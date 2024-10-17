package org.example.basicapp;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.rekognition.AmazonRekognition;
import com.amazonaws.services.rekognition.AmazonRekognitionClientBuilder;
import com.amazonaws.services.rekognition.model.AmazonRekognitionException;
import com.amazonaws.services.rekognition.model.DetectLabelsRequest;
import com.amazonaws.services.rekognition.model.DetectLabelsResult;
import com.amazonaws.services.rekognition.model.Image;
import com.amazonaws.services.rekognition.model.Label;
import com.amazonaws.services.rekognition.model.S3Object;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.SendMessageRequest;

import java.util.List;

public class CarDetector {

    public static void main(String[] args) throws Exception {

        String bucket = "njit-cs-643";  
        String region = "us-east-1";    
                                        
        String sqsQueueUrl = "https://sqs.us-east-1.amazonaws.com/079478445145/car-image-index-queue"; 


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

        // List objects in the S3 bucket
        ObjectListing objectListing = s3Client.listObjects(bucket);
        List<S3ObjectSummary> s3ObjectSummaries = objectListing.getObjectSummaries();

        
        int imageCount = 0;
        for (S3ObjectSummary objectSummary : s3ObjectSummaries) {
            if (imageCount >= 10) { 
                break; // Stop after 10 images
            }

            String photo = objectSummary.getKey();

            // Create request for Rekognition
            DetectLabelsRequest request = new DetectLabelsRequest()
                    .withImage(new Image()
                            .withS3Object(new S3Object()
                                    .withName(photo)
                                    .withBucket(bucket)))
                    .withMaxLabels(10)
                    .withMinConfidence(75F);

            try {
                // Detect labels in the image
                DetectLabelsResult result = rekognitionClient.detectLabels(request);
                List<Label> labels = result.getLabels();

                // Check if the label 'Car' is detected with conf >90%
                boolean carDetected = false;
                for (Label label : labels) {
                    if (label.getName().equalsIgnoreCase("Car") && label.getConfidence() > 90.0F) {
                        carDetected = true;
                        break;
                    }
                }

                // If a car is detected, send the image index to SQS
                if (carDetected) {
                    SendMessageRequest sendMsgRequest = new SendMessageRequest()
                            .withQueueUrl(sqsQueueUrl)
                            .withMessageBody(photo);
                    sqsClient.sendMessage(sendMsgRequest);

                    System.out.println("Image index " + photo + " sent to SQS.");
                } else {
                    System.out.println("No car detected with sufficient confidence in image: " + photo);
                }

            } catch (AmazonRekognitionException e) {
                e.printStackTrace();
            }

            imageCount++;
        }
    }
}
