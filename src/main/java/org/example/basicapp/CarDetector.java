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
import org.example.basicapp.config.ConfigManager;
import org.example.basicapp.util.MetricsCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class CarDetector {
    private static final Logger logger = LoggerFactory.getLogger(CarDetector.class);
    private final ConfigManager config;
    private final MetricsCollector metrics;

    public CarDetector() {
        this.config = ConfigManager.getInstance();
        this.metrics = MetricsCollector.getInstance();
    }

    public static void main(String[] args) {
        CarDetector detector = new CarDetector();
        try {
            detector.processImages();
        } catch (Exception e) {
            logger.error("Fatal error in CarDetector", e);
            System.exit(1);
        }
    }

    public void processImages() throws Exception {
        String bucket = config.getProperty("aws.s3.bucket");
        String region = config.getProperty("aws.region");
        String sqsQueueUrl = config.getProperty("aws.sqs.queue.url");
        float minCarConfidence = config.getFloatProperty("car.detection.min.confidence", 90.0f);
        int maxLabels = config.getIntProperty("car.detection.max.labels", 10);
        float minLabelConfidence = config.getFloatProperty("car.detection.min.label.confidence", 75.0f);
        int maxImages = config.getIntProperty("car.detection.max.images", 10);
        String terminationSignal = config.getProperty("termination.signal", "-1");

        logger.info("Starting car detection process");
        logger.info("Configuration: bucket={}, region={}, maxImages={}, minCarConfidence={}%", 
                    bucket, region, maxImages, minCarConfidence);

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

        // List objects in the S3 bucket
        ObjectListing objectListing = s3Client.listObjects(bucket);
        List<S3ObjectSummary> s3ObjectSummaries = objectListing.getObjectSummaries();

        int imageCount = 0;
        int carsDetectedCount = 0;

        for (S3ObjectSummary objectSummary : s3ObjectSummaries) {
            if (imageCount >= maxImages) {
                logger.info("Reached maximum image limit: {}", maxImages);
                break;
            }

            String photo = objectSummary.getKey();
            metrics.incrementImagesProcessed();

            // Skip non-image files
            if (!isImageFile(photo)) {
                logger.debug("Skipping non-image file: {}", photo);
                continue;
            }

            try {
                // Create request for Rekognition
                DetectLabelsRequest request = new DetectLabelsRequest()
                        .withImage(new Image()
                                .withS3Object(new S3Object()
                                        .withName(photo)
                                        .withBucket(bucket)))
                        .withMaxLabels(maxLabels)
                        .withMinConfidence(minLabelConfidence);

                // Detect labels in the image
                DetectLabelsResult result = rekognitionClient.detectLabels(request);
                List<Label> labels = result.getLabels();

                // Check if the label 'Car' is detected with sufficient confidence
                boolean carDetected = false;
                for (Label label : labels) {
                    if (label.getName().equalsIgnoreCase("Car") && label.getConfidence() >= minCarConfidence) {
                        carDetected = true;
                        logger.info("Car detected in {} with confidence: {}%", photo, label.getConfidence());
                        break;
                    }
                }

                // If a car is detected, send the image index to SQS
                if (carDetected) {
                    SendMessageRequest sendMsgRequest = new SendMessageRequest()
                            .withQueueUrl(sqsQueueUrl)
                            .withMessageBody(photo);
                    sqsClient.sendMessage(sendMsgRequest);
                    metrics.incrementCarsDetected();
                    metrics.incrementMessagesSent();
                    carsDetectedCount++;
                    logger.info("Image index {} sent to SQS queue", photo);
                } else {
                    logger.debug("No car detected with sufficient confidence (>{}) in image: {}", 
                                minCarConfidence, photo);
                }

            } catch (AmazonRekognitionException e) {
                logger.error("Error processing image {}: {}", photo, e.getMessage(), e);
            }

            imageCount++;
        }

        // Send termination signal to SQS
        try {
            SendMessageRequest terminationRequest = new SendMessageRequest()
                    .withQueueUrl(sqsQueueUrl)
                    .withMessageBody(terminationSignal);
            sqsClient.sendMessage(terminationRequest);
            logger.info("Termination signal ({}) sent to SQS queue", terminationSignal);
        } catch (Exception e) {
            logger.error("Error sending termination signal", e);
        }

        logger.info("Car detection completed. Processed {} images, detected {} cars", 
                   imageCount, carsDetectedCount);
        metrics.printSummary();
    }

    private boolean isImageFile(String filename) {
        String lowerCase = filename.toLowerCase();
        return lowerCase.endsWith(".jpg") || lowerCase.endsWith(".jpeg") || 
               lowerCase.endsWith(".png") || lowerCase.endsWith(".gif");
    }
}
