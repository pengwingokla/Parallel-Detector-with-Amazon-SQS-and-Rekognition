package org.example.basicapp.util;

import java.util.concurrent.atomic.AtomicInteger;

public class MetricsCollector {
    private static MetricsCollector instance;
    private AtomicInteger imagesProcessed = new AtomicInteger(0);
    private AtomicInteger carsDetected = new AtomicInteger(0);
    private AtomicInteger imagesWithText = new AtomicInteger(0);
    private AtomicInteger messagesSent = new AtomicInteger(0);
    private AtomicInteger messagesProcessed = new AtomicInteger(0);

    private MetricsCollector() {}

    public static synchronized MetricsCollector getInstance() {
        if (instance == null) {
            instance = new MetricsCollector();
        }
        return instance;
    }

    public void incrementImagesProcessed() {
        imagesProcessed.incrementAndGet();
    }

    public void incrementCarsDetected() {
        carsDetected.incrementAndGet();
    }

    public void incrementImagesWithText() {
        imagesWithText.incrementAndGet();
    }

    public void incrementMessagesSent() {
        messagesSent.incrementAndGet();
    }

    public void incrementMessagesProcessed() {
        messagesProcessed.incrementAndGet();
    }

    public int getImagesProcessed() {
        return imagesProcessed.get();
    }

    public int getCarsDetected() {
        return carsDetected.get();
    }

    public int getImagesWithText() {
        return imagesWithText.get();
    }

    public int getMessagesSent() {
        return messagesSent.get();
    }

    public int getMessagesProcessed() {
        return messagesProcessed.get();
    }

    public void printSummary() {
        System.out.println("\n========== Processing Summary ==========");
        System.out.println("Images Processed: " + imagesProcessed.get());
        System.out.println("Cars Detected: " + carsDetected.get());
        System.out.println("Messages Sent to SQS: " + messagesSent.get());
        System.out.println("Messages Processed: " + messagesProcessed.get());
        System.out.println("Images with Text Detected: " + imagesWithText.get());
        System.out.println("========================================\n");
    }
}

