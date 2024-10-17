# Parallel-Detector-with-Amazon-SQS-and-Rekognition
This project uses AWS to process images in parallel. Instance A detects cars in S3 images using Rekognition and sends image indexes to an SQS queue. Instance B reads the indexes, downloads the images, and performs text recognition. Results are stored in EBS, enabling efficient parallel processing of object and text detection.


## Cloud Application Setup and Execution Guide
This guide explains how to set up a cloud environment using two EC2 instances that run in parallel to detect cars in images and perform text recognition using Amazon S3, SQS, and Rekognition.

### Prerequisites
* AWS Account
* AWS CLI installed and configured on your local machine or instances
* Java 8 (or higher) installed on both instances
* IAM role that allow access to S3, SQS, and Rekognition.
`AmazonS3FullAccess`
`AmazonRekognitionFullAccess`
`AmazonSQSFullAccess`

### Steps to Set Up the Cloud Environment
#### Step 1: Set Up SQS Queue
- In the AWS Management Console, navigate to SQS.
- Click Create Queue.
- Name the queue (e.g., car-index-queue), select Standard Queue, and click Create.
- Save queue URL somewhere (e.g., https://sqs.us-east-1.amazonaws.com/123456789012/car-index-queue).

#### Step 2: Launch EC2 Instances
Launch Instance A and Instance B using the steps below:
- Go to EC2 > Launch Instance.
- Choose an Amazon Machine Image (AMI).
- Choose the instance type (e.g., `t2.micro`).
- Select `LabInstanceProfile` for IAM Role. This should automatically have access to S3, SQS, and Rekognition.
- Launch the instance.
- SSH into the instance using:
<br> `ssh -i your-key.pem ec2-user@<Instance-PublicIP>`
- Configure SSO by using:
<br> `aws configure` and `aws configure sso`
<br> Copy and paste the credentials shown in AWS Details in AWS Academy Learner Lab
- After launching, SSH into the instance and install Java 
<br> `sudo yum install java-1.8.0-amazon-corretto`

#### Step 3: Ensure access to provided S3 Bucket
On each instance CLI use this code to see if there is access:
<br> `aws s3 ls s3://njit-cs-643`

### Create new project using the SDK with Apache Maven
On the terminal of each instance use:
<br> `mvn -B archetype:generate \
  -DarchetypeGroupId=org.apache.maven.archetypes \
  -DgroupId=org.example.basicapp \
  -DartifactId=myapp`

### Build Application on Each Instance
- On instance A, use the `CarDetector.java` file. 
- On instance B, use the `TextRecognition.java` file. 
- Use the same `pom.xml` file for the dependencies set up of the application on both instances.