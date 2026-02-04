# Application Architecture - Mermaid Diagrams

This document contains Mermaid diagrams visualizing the architecture and flow of the parallel image processing system.

## System Overview

```mermaid
graph TB
    subgraph AWS["AWS Cloud Environment"]
        subgraph EC2A["EC2 Instance A"]
            CD[CarDetector]
        end
        
        subgraph EC2B["EC2 Instance B"]
            TR[TextRecognition]
        end
        
        S3[(Amazon S3<br/>Image Storage)]
        SQS[(SQS Queue<br/>Message Queue)]
        REK1[Amazon Rekognition<br/>Detect Labels]
        REK2[Amazon Rekognition<br/>Detect Text]
        
        CD -->|List & Read| S3
        CD -->|Detect Cars| REK1
        CD -->|Send Image Keys| SQS
        SQS -->|Poll Messages| TR
        TR -->|Detect Text| REK2
        TR -->|Read Images| S3
        TR -->|Write Results| OUTPUT[output.txt]
    end
    
    CONFIG[application.properties<br/>Configuration]
    CONFIG -.->|Load Config| CD
    CONFIG -.->|Load Config| TR
    
    style CD fill:#e1f5ff
    style TR fill:#fff4e1
    style SQS fill:#ffe1f5
    style S3 fill:#e1ffe1
    style REK1 fill:#f5e1ff
    style REK2 fill:#f5e1ff
```

## CarDetector Flow

```mermaid
car
```

## TextRecognition Flow

```mermaid
flowchart TD
    START([Start]) --> LOAD_CONFIG[Load Configuration<br/>ConfigManager]
    LOAD_CONFIG --> INIT_CLIENTS[Initialize AWS Clients<br/>S3, Rekognition, SQS]
    INIT_CLIENTS --> OPEN_FILE[Open output.txt<br/>for Writing]
    OPEN_FILE --> POLL_SQS[Poll SQS Queue<br/>Long Polling 10s]
    
    POLL_SQS --> CHECK_MSG{Messages<br/>Received?}
    CHECK_MSG -->|No| POLL_SQS
    CHECK_MSG -->|Yes| GET_MSG[Get Message]
    
    GET_MSG --> CHECK_TERM{Message Body<br/>= -1?}
    CHECK_TERM -->|Yes| STOP[Stop Processing]
    CHECK_TERM -->|No| DETECT_TEXT[Call Rekognition<br/>Detect Text from S3]
    
    DETECT_TEXT --> FILTER[Filter Duplicate<br/>Text Detections]
    FILTER --> CHECK_TEXT{Text<br/>Found?}
    
    CHECK_TEXT -->|Yes| WRITE_FILE[Write to output.txt<br/>Image Index + Text]
    CHECK_TEXT -->|No| LOG_NO_TEXT[Log: No Text Detected]
    
    WRITE_FILE --> UPDATE_METRICS1[Update Metrics<br/>imagesWithText++]
    LOG_NO_TEXT --> UPDATE_METRICS2[Update Metrics<br/>messagesProcessed++]
    UPDATE_METRICS1 --> DELETE_MSG[Delete Message<br/>from SQS Queue]
    UPDATE_METRICS2 --> DELETE_MSG
    
    DELETE_MSG --> POLL_SQS
    STOP --> PRINT_METRICS[Print Metrics Summary]
    PRINT_METRICS --> CLOSE_FILE[Close output.txt]
    CLOSE_FILE --> END([End])
    
    style START fill:#90EE90
    style END fill:#FFB6C1
    style DETECT_TEXT fill:#87CEEB
    style STOP fill:#FFA500
```

## Sequence Diagram - Complete Flow

```mermaid
sequenceDiagram
    participant CD as CarDetector<br/>(Instance A)
    participant S3 as Amazon S3
    participant REK1 as Rekognition<br/>(Labels)
    participant SQS as SQS Queue
    participant TR as TextRecognition<br/>(Instance B)
    participant REK2 as Rekognition<br/>(Text)
    participant OUT as output.txt
    
    Note over CD: Phase 1: Car Detection
    
    CD->>S3: List objects in bucket
    S3-->>CD: Return image list
    
    loop For each image (max 10)
        CD->>REK1: Detect labels (image)
        REK1-->>CD: Return labels
        
        alt Car detected (confidence > 90%)
            CD->>SQS: Send image key
            Note over CD,SQS: Message: "image1.jpg"
        else No car detected
            Note over CD: Skip image
        end
    end
    
    CD->>SQS: Send termination signal "-1"
    
    Note over TR: Phase 2: Text Recognition
    
    loop Until termination signal
        TR->>SQS: Poll queue (long polling)
        SQS-->>TR: Return message
        
        alt Message = "-1"
            Note over TR: Stop processing
        else Message = image key
            TR->>REK2: Detect text (from S3)
            REK2-->>TR: Return text detections
            TR->>TR: Filter duplicates
            TR->>OUT: Write results
            TR->>SQS: Delete message
        end
    end
    
    TR->>OUT: Close file
```

## Component Interaction Diagram

```mermaid
graph LR
    subgraph Config["Configuration"]
        PROPS[application.properties]
        CM[ConfigManager<br/>Singleton]
        PROPS --> CM
    end
    
    subgraph InstanceA["Instance A"]
        CD[CarDetector]
        CM -.->|Get Config| CD
        CD --> METRICS1[MetricsCollector]
    end
    
    subgraph InstanceB["Instance B"]
        TR[TextRecognition]
        CM -.->|Get Config| TR
        TR --> METRICS2[MetricsCollector]
    end
    
    subgraph AWS["AWS Services"]
        S3[(S3 Bucket)]
        SQS[(SQS Queue)]
        REK[Rekognition API]
    end
    
    CD -->|Read Images| S3
    CD -->|Detect Labels| REK
    CD -->|Send Messages| SQS
    
    SQS -->|Poll Messages| TR
    TR -->|Read Images| S3
    TR -->|Detect Text| REK
    
    METRICS1 -.->|Same Instance| METRICS2
    
    style CM fill:#FFE4B5
    style METRICS1 fill:#E0E0E0
    style METRICS2 fill:#E0E0E0
```

## Data Flow Diagram

```mermaid
flowchart LR
    subgraph Input["Input"]
        IMG1[image1.jpg]
        IMG2[image2.jpg]
        IMG3[image3.jpg]
    end
    
    subgraph Processing["Processing"]
        CD[CarDetector]
        SQS[(SQS Queue)]
        TR[TextRecognition]
    end
    
    subgraph Output["Output"]
        OUT[output.txt]
    end
    
    IMG1 -->|Detect Cars| CD
    IMG2 -->|Detect Cars| CD
    IMG3 -->|Detect Cars| CD
    
    CD -->|image1.jpg| SQS
    CD -->|image3.jpg| SQS
    CD -->|-1| SQS
    
    SQS -->|image1.jpg| TR
    SQS -->|image3.jpg| TR
    SQS -->|-1| TR
    
    TR -->|Results| OUT
    
    style CD fill:#87CEEB
    style TR fill:#FFE4B5
    style SQS fill:#DDA0DD
```

## State Diagram - Message Lifecycle

```mermaid
stateDiagram-v2
    [*] --> ImageInS3: Image uploaded
    
    ImageInS3 --> CarDetection: CarDetector processes
    CarDetection --> CarFound: Car detected (>90%)
    CarDetection --> CarNotFound: No car
    
    CarFound --> InQueue: Send to SQS
    CarNotFound --> [*]: Skip image
    
    InQueue --> Polled: TextRecognition polls
    Polled --> Processing: Message received
    
    Processing --> TextDetected: Text found
    Processing --> NoText: No text
    
    TextDetected --> Written: Write to output.txt
    NoText --> Deleted: Log and continue
    
    Written --> Deleted: Delete from queue
    Deleted --> [*]: Complete
    
    InQueue --> Terminated: Termination signal (-1)
    Terminated --> [*]: Stop processing
```

## Class Diagram

```mermaid
classDiagram
    class CarDetector {
        -ConfigManager config
        -MetricsCollector metrics
        -Logger logger
        +main(String[] args)
        +processImages()
        -isImageFile(String filename) boolean
    }
    
    class TextRecognition {
        -ConfigManager config
        -MetricsCollector metrics
        -Logger logger
        +main(String[] args)
        +processTextRecognition()
    }
    
    class ConfigManager {
        -Properties properties
        -static ConfigManager instance
        +getInstance() ConfigManager
        +getProperty(String key) String
        +getIntProperty(String key, int default) int
        +getFloatProperty(String key, float default) float
        -loadProperties()
    }
    
    class MetricsCollector {
        -AtomicInteger imagesProcessed
        -AtomicInteger carsDetected
        -AtomicInteger imagesWithText
        -AtomicInteger messagesSent
        -AtomicInteger messagesProcessed
        +getInstance() MetricsCollector
        +incrementImagesProcessed()
        +incrementCarsDetected()
        +printSummary()
    }
    
    CarDetector --> ConfigManager : uses
    CarDetector --> MetricsCollector : uses
    TextRecognition --> ConfigManager : uses
    TextRecognition --> MetricsCollector : uses
    
    ConfigManager ..> Properties : loads from
    MetricsCollector ..> AtomicInteger : uses
```

## Deployment Architecture

```mermaid
graph TB
    subgraph Dev["Development"]
        CODE[Source Code]
        GIT[Git Repository]
        CODE --> GIT
    end
    
    subgraph CI["CI/CD Pipeline"]
        GITHUB[GitHub Actions]
        BUILD[Maven Build]
        TEST[Run Tests]
        GIT -->|Push/PR| GITHUB
        GITHUB --> BUILD
        BUILD --> TEST
    end
    
    subgraph AWS["AWS Deployment"]
        subgraph EC2A["EC2 Instance A"]
            DOCKER1[Docker Container]
            CD_APP[CarDetector App]
            DOCKER1 --> CD_APP
        end
        
        subgraph EC2B["EC2 Instance B"]
            DOCKER2[Docker Container]
            TR_APP[TextRecognition App]
            DOCKER2 --> TR_APP
        end
        
        TEST -->|Deploy| DOCKER1
        TEST -->|Deploy| DOCKER2
    end
    
    subgraph AWS_SERVICES["AWS Services"]
        S3[(S3)]
        SQS[(SQS)]
        REK[Rekognition]
    end
    
    CD_APP --> S3
    CD_APP --> REK
    CD_APP --> SQS
    TR_APP --> SQS
    TR_APP --> S3
    TR_APP --> REK
    
    style GITHUB fill:#24292e,color:#fff
    style DOCKER1 fill:#0db7ed,color:#fff
    style DOCKER2 fill:#0db7ed,color:#fff
```

## Metrics Flow

```mermaid
graph TD
    subgraph CarDetector["CarDetector"]
        CD_PROCESS[Process Image]
        CD_DETECT[Detect Car]
        CD_SEND[Send to SQS]
    end
    
    subgraph TextRecognition["TextRecognition"]
        TR_PROCESS[Process Message]
        TR_DETECT[Detect Text]
        TR_WRITE[Write Results]
    end
    
    subgraph Metrics["MetricsCollector"]
        IMG_PROC[imagesProcessed]
        CARS[Cars Detected]
        MSG_SENT[messagesSent]
        MSG_PROC[messagesProcessed]
        IMG_TEXT[imagesWithText]
    end
    
    CD_PROCESS -->|increment| IMG_PROC
    CD_DETECT -->|increment| CARS
    CD_SEND -->|increment| MSG_SENT
    
    TR_PROCESS -->|increment| MSG_PROC
    TR_DETECT -->|increment| IMG_TEXT
    
    IMG_PROC --> SUMMARY[Print Summary]
    CARS --> SUMMARY
    MSG_SENT --> SUMMARY
    MSG_PROC --> SUMMARY
    IMG_TEXT --> SUMMARY
    
    style Metrics fill:#E8F5E9
    style SUMMARY fill:#FFF9C4
```

## Error Handling Flow

```mermaid
flowchart TD
    START([Operation Start]) --> TRY{Try Operation}
    
    TRY -->|Success| SUCCESS[Continue Processing]
    TRY -->|Exception| CATCH[Catch Exception]
    
    CATCH --> CHECK_TYPE{Exception Type?}
    
    CHECK_TYPE -->|AmazonRekognitionException| LOG_REK[Log Rekognition Error<br/>Continue to Next Image]
    CHECK_TYPE -->|IOException| LOG_IO[Log IO Error<br/>Continue Processing]
    CHECK_TYPE -->|RuntimeException| LOG_RUNTIME[Log Runtime Error<br/>Continue if Possible]
    CHECK_TYPE -->|Fatal Exception| EXIT[Log Fatal Error<br/>Exit with Code 1]
    
    LOG_REK --> CONTINUE[Continue Processing]
    LOG_IO --> CONTINUE
    LOG_RUNTIME --> CONTINUE
    
    CONTINUE --> NEXT{More Items?}
    NEXT -->|Yes| TRY
    NEXT -->|No| END([End])
    
    SUCCESS --> NEXT
    EXIT --> END
    
    style EXIT fill:#FF6B6B
    style SUCCESS fill:#51CF66
```

## Configuration Management Flow

```mermaid
flowchart TD
    START([Application Start]) --> LOAD[Load application.properties]
    
    LOAD --> CHECK_FILE{File Exists?}
    CHECK_FILE -->|No| ERROR[RuntimeException:<br/>Config file not found]
    CHECK_FILE -->|Yes| READ[Read Properties]
    
    READ --> CREATE[Create ConfigManager<br/>Singleton Instance]
    CREATE --> STORE[Store in Properties Object]
    
    STORE --> GET[Get Property Values]
    GET --> TYPE{Property Type?}
    
    TYPE -->|String| GET_STRING[getProperty]
    TYPE -->|Integer| GET_INT[getIntProperty<br/>with default]
    TYPE -->|Float| GET_FLOAT[getFloatProperty<br/>with default]
    
    GET_STRING --> USE[Use in Application]
    GET_INT --> USE
    GET_FLOAT --> USE
    
    USE --> END([Application Running])
    ERROR --> END
    
    style ERROR fill:#FF6B6B
    style USE fill:#51CF66
```

---

## How to View These Diagrams

These Mermaid diagrams can be viewed by copying the code to https://mermaid.live/