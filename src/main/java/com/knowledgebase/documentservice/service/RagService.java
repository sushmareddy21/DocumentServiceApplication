package com.knowledgebase.documentservice.service;

import com.knowledgebase.documentservice.entity.Document;
import com.knowledgebase.documentservice.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RagService {
    
    private final VectorStore vectorStore;
    private final ChatClient.Builder chatClientBuilder;
    private final DocumentRepository documentRepository;
    private final S3Service s3Service;
    
    /**
     * Process document and store in Pinecone vector database
     */
    public void processDocument(Long documentId) {
        log.info("Processing document for RAG: {}", documentId);
        
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found: " + documentId));
        
        try {
            // Download PDF from S3
            InputStream pdfStream = s3Service.downloadFile(doc.getS3Key());
            InputStreamResource resource = new InputStreamResource(pdfStream);
            
            // Read PDF
            DocumentReader reader = new PagePdfDocumentReader(resource);
            List<org.springframework.ai.document.Document> documents = reader.get();
            
            log.info("Read {} pages from PDF", documents.size());
            
            // Split into chunks (OpenAI has token limits)
            TokenTextSplitter splitter = new TokenTextSplitter(500, 100, 5, 1000, true);
            List<org.springframework.ai.document.Document> chunks = splitter.apply(documents);
            
            log.info("Split into {} chunks", chunks.size());
            
            // Add metadata to each chunk
            chunks.forEach(chunk -> {
                Map<String, Object> metadata = new HashMap<>(chunk.getMetadata());
                metadata.put("document_id", documentId.toString());
                metadata.put("file_name", doc.getFileName());
                metadata.put("uploaded_by", doc.getUploadedBy());
                chunk.getMetadata().putAll(metadata);
            });
            
            // Store in Pinecone
            vectorStore.add(chunks);
            
            // Mark as processed
            doc.setIsProcessed(true);
            documentRepository.save(doc);
            
            log.info("Document processed and stored in Pinecone: {}", documentId);
            
        } catch (Exception e) {
            log.error("Error processing document: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process document", e);
        }
    }
    
    /**
     * Ask a question using OpenAI with RAG
     */
    public String askQuestion(String question) {
        log.info("Received question: {}", question);
        
        try {
            // Search Pinecone for relevant chunks
            SearchRequest searchRequest = SearchRequest.query(question)
                    .withTopK(5)
                    .withSimilarityThreshold(0.7);
            
            List<org.springframework.ai.document.Document> relevantDocs = 
                    vectorStore.similaritySearch(searchRequest);
            
            if (relevantDocs.isEmpty()) {
                return "I don't have enough information to answer that question. Please upload relevant documents first.";
            }
            
            log.info("Found {} relevant chunks from Pinecone", relevantDocs.size());
            
            // Build context from relevant documents
            String context = relevantDocs.stream()
                    .map(org.springframework.ai.document.Document::getContent)
                    .collect(Collectors.joining("\n\n"));
            
            // Create prompt for OpenAI
            String promptText = String.format("""
                You are a helpful AI assistant that answers questions based on the provided context.
                Use ONLY the information from the context to answer the question.
                If the answer is not in the context, say "I don't have that information in the uploaded documents."
                Be concise and accurate.
                
                Context from documents:
                %s
                
                Question: %s
                
                Answer:
                """, context, question);
            
            // Get response from OpenAI
            ChatClient chatClient = chatClientBuilder.build();
            String answer = chatClient.prompt()
                    .user(promptText)
                    .call()
                    .content();
            
            log.info("Generated answer from OpenAI");
            return answer;
            
        } catch (Exception e) {
            log.error("Error in askQuestion: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process question", e);
        }
    }
    
    /**
     * Ask question filtered by specific document
     */
    public String askQuestionByDocument(String question, Long documentId) {
        log.info("Received question for document {}: {}", documentId, question);
        
        try {
            // Verify document exists
            documentRepository.findById(documentId)
                    .orElseThrow(() -> new RuntimeException("Document not found: " + documentId));
            
            // Search with similarity
            SearchRequest searchRequest = SearchRequest.query(question)
                    .withTopK(10)
                    .withSimilarityThreshold(0.6);
            
            List<org.springframework.ai.document.Document> relevantDocs = 
                    vectorStore.similaritySearch(searchRequest);
            
            // Filter by document ID
            List<org.springframework.ai.document.Document> filteredDocs = relevantDocs.stream()
                    .filter(doc -> {
                        Object docId = doc.getMetadata().get("document_id");
                        return docId != null && docId.toString().equals(documentId.toString());
                    })
                    .limit(5)
                    .collect(Collectors.toList());
            
            if (filteredDocs.isEmpty()) {
                return "I couldn't find relevant information in this specific document to answer your question.";
            }
            
            log.info("Found {} relevant chunks from specific document", filteredDocs.size());
            
            // Build context
            String context = filteredDocs.stream()
                    .map(org.springframework.ai.document.Document::getContent)
                    .collect(Collectors.joining("\n\n"));
            
            // Create prompt
            String promptText = String.format("""
                Answer the question based ONLY on this document content.
                Be specific and cite information from the document.
                
                Document content:
                %s
                
                Question: %s
                
                Answer:
                """, context, question);
            
            // Get response from OpenAI
            ChatClient chatClient = chatClientBuilder.build();
            String answer = chatClient.prompt()
                    .user(promptText)
                    .call()
                    .content();
            
            return answer;
            
        } catch (Exception e) {
            log.error("Error in askQuestionByDocument: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process question", e);
        }
    }
}