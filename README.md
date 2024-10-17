# Parallel-Detector-with-Amazon-SQS-and-Rekognition
This project uses AWS to process images in parallel. Instance A detects cars in S3 images using Rekognition and sends image indexes to an SQS queue. Instance B reads the indexes, downloads the images, and performs text recognition. Results are stored in EBS, enabling efficient parallel processing of object and text detection.
