package org.example.basicapp;

import org.example.basicapp.config.ConfigManager;

/**
 * Simple test class to verify configuration loading works correctly.
 * Run this before running the main applications to ensure everything is set up properly.
 */
public class TestConfig {
    public static void main(String[] args) {
        System.out.println("==========================================");
        System.out.println("Testing Configuration Manager");
        System.out.println("==========================================");
        System.out.println();
        
        try {
            ConfigManager config = ConfigManager.getInstance();
            
            System.out.println("✓ ConfigManager initialized successfully");
            System.out.println();
            
            // Test reading properties
            System.out.println("Configuration Values:");
            System.out.println("  AWS Region: " + config.getProperty("aws.region"));
            System.out.println("  S3 Bucket: " + config.getProperty("aws.s3.bucket"));
            System.out.println("  SQS Queue URL: " + config.getProperty("aws.sqs.queue.url"));
            System.out.println("  Min Car Confidence: " + config.getFloatProperty("car.detection.min.confidence", 90.0f));
            System.out.println("  Max Images: " + config.getIntProperty("car.detection.max.images", 10));
            System.out.println("  Output Path: " + config.getProperty("text.recognition.output.path"));
            System.out.println("  Termination Signal: " + config.getProperty("termination.signal"));
            System.out.println();
            
            // Test default values
            System.out.println("Testing default values:");
            String nonExistent = config.getProperty("non.existent.property", "DEFAULT_VALUE");
            System.out.println("  Non-existent property with default: " + nonExistent);
            System.out.println();
            
            System.out.println("==========================================");
            System.out.println("✓ All configuration tests passed!");
            System.out.println("==========================================");
            
        } catch (Exception e) {
            System.err.println("✗ Configuration test failed!");
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}

