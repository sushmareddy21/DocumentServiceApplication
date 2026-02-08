📚 Knowledge Base AI - Backend

AI-powered document question-answering system using RAG (Retrieval Augmented Generation) architecture.

This backend service ingests PDF documents, processes them using vector embeddings, and enables intelligent conversations via OpenAI's GPT models.

🚀 Live Demo

Backend API: documentserviceapplication-production.up.railway.app

Frontend App: https://knowledge-base-frontend-five.vercel.app/

🛠️ Tech Stack

Framework: Java 17 + Spring Boot 3.3.5

AI Integration: Spring AI (Milestone 5)

Database (Metadata): PostgreSQL

Vector Database: Pinecone (Serverless)

AI Model: OpenAI GPT-4o-mini (Chat) + text-embedding-3-small (Embeddings)

File Storage: AWS S3

Deployment: Railway

✨ Features

✅ Secure Uploads: PDF document upload directly to AWS S3.

✅ RAG Pipeline: Automatic text extraction, chunking, and vectorization upon upload.

✅ Semantic Search: Uses Pinecone to find relevant context based on meaning, not just keywords.

✅ Smart Chat: Answers questions based only on the uploaded document context (No hallucinations).

✅ Filter capabilities: Chat with all documents or drill down to a specific file.

🏗️ Architecture

graph TD
    Client[React Frontend] -->|REST API| API[Spring Boot Backend]
    API -->|Store File| S3[AWS S3]
    API -->|Save Metadata| DB[PostgreSQL]
    API -->|Extract Text| PDF[Apache PDFBox]
    
    subgraph "RAG Pipeline"
    PDF -->|Raw Text| Splitter[Token Splitter]
    Splitter -->|Chunks| OpenAI[OpenAI Embeddings]
    OpenAI -->|Vectors| Pinecone[Pinecone Vector DB]
    end
    
    subgraph "Chat Flow"
    Client -->|Question| API
    API -->|Search| Pinecone
    Pinecone -->|Context| API
    API -->|Context + Question| GPT[OpenAI GPT-4o-mini]
    GPT -->|Answer| Client
    end


📡 API Endpoints

📄 Documents

Method

Endpoint

Description

POST

/api/documents/upload

Upload a PDF file (Form Data: file, uploadedBy)

GET

/api/documents

List all uploaded documents

GET

/api/documents/{id}

Get metadata for a specific document

DELETE

/api/documents/{id}

Delete a document (from DB, S3, and Pinecone)

GET

/api/documents/{id}/url

Get a temporary S3 download URL

💬 Chat

Method

Endpoint

Description

POST

/api/chat/ask

Ask a question across all documents

POST

/api/chat/ask/{id}

Ask a question about a specific document

GET

/api/chat/health

Check if AI service is online

🔧 Local Development

Prerequisites

Java 17+

Maven

PostgreSQL (Local or Docker)

Environment Variables

Create a src/main/resources/application.properties file or set these in your IDE:

# Database
spring.datasource.url=jdbc:postgresql://localhost:5432/knowledge_base
spring.datasource.username=your_user
spring.datasource.password=your_password

# AWS S3
aws.s3.bucket-name=your-bucket-name
aws.s3.region=us-east-1
aws.access-key-id=your-aws-key
aws.secret-access-key=your-aws-secret

# AI Keys
spring.ai.openai.api-key=sk-proj-...
spring.ai.vectorstore.pinecone.apiKey=pc-sk-...
spring.ai.vectorstore.pinecone.environment=us-east-1-aws
spring.ai.vectorstore.pinecone.project-id=your-project-id
spring.ai.vectorstore.pinecone.index-name=knowledge-base


Run Locally

./mvnw spring-boot:run


📦 Deployment

This project is configured for Railway.

Connect GitHub Repository.

Set Environment Variables in the Railway Dashboard.

Deploy!

👨‍💻 Author

Sushma Reddy
