package org.example.basicapp;

import java.io.File;
import java.io.FileWriter;
import java.util.List;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.rekognition.AmazonRekognition;
import com.amazonaws.services.rekognition.AmazonRekognitionClientBuilder;
import com.amazonaws.services.rekognition.model.AmazonRekognitionException;
import com.amazonaws.services.rekognition.model.DetectTextRequest;
import com.amazonaws.services.rekognition.model.DetectTextResult;
import com.amazonaws.services.rekognition.model.Image;
import com.amazonaws.services.rekognition.model.TextDetection;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.DeleteMessageRequest;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;

public class TextRecognition {

    public static void main(String[] args) throws Exception {

        String bucket = "njit-cs-643"; 
        String region = "us-east-1";   
        String sqsQueueUrl = "https://sqs.us-east-1.amazonaws.com/079478445145/car-image-index-queue";  
        String outFilePath = "src/main/java/org/example/basicapp/output.txt"; 

        // Create Amazon S3 client, Amazon Rekognition client. and Amazon SQS client
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

        // Create a file writer to store the results
        File outFile = new File(outFilePath);
        FileWriter writer = new FileWriter(outFile);

        boolean stop = false;

        while (!stop) {
            // Poll SQS queue for new messages
            ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest()
                    .withQueueUrl(sqsQueueUrl)
                    .withMaxNumberOfMessages(1) // Process one message at a time
                    .withWaitTimeSeconds(10);
            List<Message> messages = sqsClient.receiveMessage(receiveMessageRequest).getMessages();

            for (Message message : messages) {
                String imageIndex = message.getBody();

                // If index is -1, stop processing
                if (imageIndex.equals("-1")) {
                    stop = true;
                    System.out.println("Received termination signal (-1). Stopping...");
                    break;
                }

                System.out.println("Processing image: " + imageIndex);

                // Download the image from S3
                File localImageFile = new File("/tmp/" + imageIndex); // Temp file to store downloaded image
                s3Client.getObject(new GetObjectRequest(bucket, imageIndex), localImageFile);

                // Perform text detection using Rekognition
                DetectTextRequest detectTextRequest = new DetectTextRequest()
                        .withImage(new Image().withS3Object(new com.amazonaws.services.rekognition.model.S3Object()
                                .withName(imageIndex)
                                .withBucket(bucket)));

                try {
                    DetectTextResult detectTextResult = rekognitionClient.detectText(detectTextRequest);
                    List<TextDetection> textDetections = detectTextResult.getTextDetections();

                    // If text is found, write to file
                    if (!textDetections.isEmpty()) {
                        writer.write("Image index: " + imageIndex + "\n");
                        for (TextDetection text : textDetections) {
                            writer.write("Detected text: " + text.getDetectedText() + "\n");
                        }
                        writer.write("\n");
                        writer.flush(); // Ensure the content is saved to disk
                    } else {
                        System.out.println("No text detected in image " + imageIndex);
                    }

                } catch (AmazonRekognitionException e) {
                    e.printStackTrace();
                }

                // Delete the processed message from the SQS queue
                sqsClient.deleteMessage(new DeleteMessageRequest(sqsQueueUrl, message.getReceiptHandle()));
            }
        }

        writer.close();
    }
}
