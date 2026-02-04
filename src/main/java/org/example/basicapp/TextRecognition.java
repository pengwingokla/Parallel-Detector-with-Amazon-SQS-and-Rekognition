package org.example.basicapp;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.rekognition.AmazonRekognition;
import com.amazonaws.services.rekognition.AmazonRekognitionClientBuilder;
import com.amazonaws.services.rekognition.model.AmazonRekognitionException;
import com.amazonaws.services.rekognition.model.DetectTextRequest;
import com.amazonaws.services.rekognition.model.DetectTextResult;
import com.amazonaws.services.rekognition.model.Image;
import com.amazonaws.services.rekognition.model.S3Object;
import com.amazonaws.services.rekognition.model.TextDetection;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.DeleteMessageRequest;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import org.example.basicapp.config.ConfigManager;
import org.example.basicapp.util.MetricsCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class TextRecognition {
    private static final Logger logger = LoggerFactory.getLogger(TextRecognition.class);
    private final ConfigManager config;
    private final MetricsCollector metrics;

    public TextRecognition() {
        this.config = ConfigManager.getInstance();
        this.metrics = MetricsCollector.getInstance();
    }

    public static void main(String[] args) {
        TextRecognition recognizer = new TextRecognition();
        try {
            recognizer.processTextRecognition();
        } catch (Exception e) {
            logger.error("Fatal error in TextRecognition", e);
            System.exit(1);
        }
    }

    public void processTextRecognition() throws Exception {
        String bucket = config.getProperty("aws.s3.bucket");
        String region = config.getProperty("aws.region");
        String sqsQueueUrl = config.getProperty("aws.sqs.queue.url");
        String outFilePath = config.getProperty("text.recognition.output.path", "src/main/output.txt");
        String tempDir = config.getProperty("text.recognition.temp.dir", "/tmp");
        int maxMessages = config.getIntProperty("sqs.max.messages", 1);
        int waitTimeSeconds = config.getIntProperty("sqs.wait.time.seconds", 10);
        String terminationSignal = config.getProperty("termination.signal", "-1");

        logger.info("Starting text recognition process");
        logger.info("Configuration: bucket={}, region={}, outputPath={}", bucket, region, outFilePath);

        // Create AWS clients
        AmazonS3 s3Client = AmazonS3ClientBuilder.standard()
                .withRegion(region)
                .withCredentials(new DefaultAWSCredentialsProviderChain())
                .build();

        AmazonRekognition rekognitionClient = AmazonRekognitionClientBuilder.standard()
                .withRegion(region)
                .build();

        AmazonSQS sqsClient = AmazonSQSClientBuilder.standard()
                .withRegion(region)
                .withCredentials(new DefaultAWSCredentialsProviderChain())
                .build();

        // Ensure output directory exists
        File outFile = new File(outFilePath);
        outFile.getParentFile().mkdirs();

        // Ensure temp directory exists
        Files.createDirectories(Paths.get(tempDir));

        boolean stop = false;

        try (FileWriter writer = new FileWriter(outFile, true)) { // Append mode
            while (!stop) {
                // Poll SQS queue for new messages
                ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest()
                        .withQueueUrl(sqsQueueUrl)
                        .withMaxNumberOfMessages(maxMessages)
                        .withWaitTimeSeconds(waitTimeSeconds);

                List<Message> messages = sqsClient.receiveMessage(receiveMessageRequest).getMessages();

                if (messages.isEmpty()) {
                    logger.debug("No messages received, continuing to poll...");
                    continue;
                }

                for (Message message : messages) {
                    String imageIndex = message.getBody();

                    // Check for termination signal
                    if (imageIndex.equals(terminationSignal)) {
                        stop = true;
                        logger.info("Received termination signal ({}). Stopping text recognition...", terminationSignal);
                        break;
                    }

                    logger.info("Processing image: {}", imageIndex);
                    metrics.incrementMessagesProcessed();

                    try {
                        // Perform text detection using Rekognition (directly from S3, no download needed)
                        DetectTextRequest detectTextRequest = new DetectTextRequest()
                                .withImage(new Image()
                                        .withS3Object(new S3Object()
                                                .withName(imageIndex)
                                                .withBucket(bucket)));

                        DetectTextResult detectTextResult = rekognitionClient.detectText(detectTextRequest);
                        List<TextDetection> textDetections = detectTextResult.getTextDetections();

                        // Filter out duplicate text detections (Rekognition returns both word and line detections)
                        String previousText = "";
                        
                        // If text is found, write to file
                        if (!textDetections.isEmpty()) {
                            boolean hasNewText = false;
                            StringBuilder output = new StringBuilder();
                            output.append("Image index: ").append(imageIndex).append("\n");
                            
                            for (TextDetection text : textDetections) {
                                String detectedText = text.getDetectedText();
                                // Only write unique text (filter duplicates)
                                if (!detectedText.equals(previousText) && 
                                    text.getType().toString().equals("LINE")) { // Only process LINE type to avoid duplicates
                                    output.append("Detected text: ").append(detectedText).append("\n");
                                    hasNewText = true;
                                }
                                previousText = detectedText;
                            }
                            
                            if (hasNewText) {
                                output.append("\n");
                                writer.write(output.toString());
                                writer.flush();
                                metrics.incrementImagesWithText();
                                logger.info("Text detected and written for image: {}", imageIndex);
                            } else {
                                logger.debug("No unique text detected in image: {}", imageIndex);
                            }
                        } else {
                            logger.debug("No text detected in image: {}", imageIndex);
                        }

                    } catch (AmazonRekognitionException e) {
                        logger.error("Error processing text recognition for image {}: {}", 
                                    imageIndex, e.getMessage(), e);
                    } catch (IOException e) {
                        logger.error("Error writing to output file: {}", e.getMessage(), e);
                    }

                    // Delete the processed message from the SQS queue
                    try {
                        sqsClient.deleteMessage(new DeleteMessageRequest(sqsQueueUrl, message.getReceiptHandle()));
                        logger.debug("Message deleted from queue for image: {}", imageIndex);
                    } catch (Exception e) {
                        logger.error("Error deleting message from queue: {}", e.getMessage(), e);
                    }
                }
            }
        }

        logger.info("Text recognition process completed");
        metrics.printSummary();
    }
}
