
# Franchise Application Deployment and API Testing Guide

This document provides the instructions to deploy the Franchise application on a different machine, provision the required infrastructure via CloudFormation on AWS, and run the application locally using Docker. The guide also explains how to test the endpoints via Postman.

## 1. Prerequisites
Make sure to have the following installed on the target machine:

- **AWS CLI**: Set up and configured with credentials that have the permissions to manage CloudFormation and DynamoDB.
- **Docker** and **Docker Compose**: To containerize and run the application.
- **Java 21**: Installed locally.
- **Postman**: For API testing.

---

## 2. Clone the Project
To get started, clone the project repository:

```bash
git clone <repository-url>
cd <project-directory>
```

---

## 3. Setting Up the DynamoDB Tables Using AWS CloudFormation

The application requires three DynamoDB tables: `Events`, `Idempotency`, and `MaxProductPerFranchise`. These tables are provisioned using CloudFormation YAML files, located in the `resources` directory.

### a. Provision Event Store and Idempotency Tables
Run the following command to create these tables:

```bash
aws cloudformation create-stack --stack-name EventStoreStack --template-body file://resources/event-store.yml --capabilities CAPABILITY_NAMED_IAM
```

### b. Provision MaxProductPerFranchise Table
To create the table for storing product information, run:

```bash
aws cloudformation create-stack --stack-name MaxProductProjectionStack --template-body file://resources/max-product-projection.yml --capabilities CAPABILITY_NAMED_IAM
```

### c. Validate the Templates (Optional)
Optionally, you can validate the CloudFormation templates before applying them:

```bash
aws cloudformation validate-template --template-body file://resources/event-store.yml
aws cloudformation validate-template --template-body file://resources/max-product-projection.yml
```

### d. Check Stack Status
To ensure the stacks were created successfully, check the status:

```bash
aws cloudformation describe-stacks --stack-name EventStoreStack
aws cloudformation describe-stacks --stack-name MaxProductProjectionStack
```

---

## 4. Running the Application with Docker

To run the application locally using Docker:

### a. Build and Start the Docker Containers
Ensure Docker is running, then navigate to the root directory and run:

```bash
docker-compose up --build
```

This will build the `franchises-app` image and start the application at `http://localhost:8080`.

### b. AWS DynamoDB Configuration (For Production)

If using AWS DynamoDB instead of a local instance, modify the `docker-compose.yml` file to point to your AWS region and endpoint:

```yaml
environment:
  - AWS_REGION=us-east-2
  - DYNAMODB_ENDPOINT=https://dynamodb.us-east-2.amazonaws.com
```

Then, rebuild and restart the application:

```bash
docker-compose down
docker-compose up --build
```

---

## 5. API Testing Using Postman

The provided Postman collection (`Nequi.postman_collection.json`) allows you to easily test the application’s endpoints.

### a. Import the Postman Collection
1. Open Postman and import the collection file located in the root directory of the project.
2. Use the provided requests to test the API endpoints.

### b. Example Endpoints
1. **Create Franchise**
   - Method: `POST`
   - URL: `http://localhost:8080/api/v1/franchise/create`
   - Body:
     ```json
     {
       "franchiseName": "Starbucks",
       "franchiseId": "STB12345"
     }
     ```

2. **Get Max Stock Product per Franchise**
   - Method: `GET`
   - URL: `http://localhost:8080/api/v1/franchise/max-product?franchiseId=STB12345`

---

## 6. Stopping the Application

To stop the Docker containers, run:

```bash
docker-compose down
```

---

## 7. Deleting the AWS CloudFormation Stack

To clean up the AWS resources, delete the stacks with:

```bash
aws cloudformation delete-stack --stack-name EventStoreStack
aws cloudformation delete-stack --stack-name MaxProductProjectionStack
```

---

## 8. Troubleshooting

- **DynamoDB Local Connection Issue**: Ensure Docker is running and the containers are started correctly.
- **CloudFormation Errors**: Verify the YAML template syntax and permissions.
- **Postman Issues**: Ensure the collection is imported and the application is running on the correct port.

For further assistance, contact the support team at support@example.com.
