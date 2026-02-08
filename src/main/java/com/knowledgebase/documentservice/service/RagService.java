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
            InputStream pdfStream = s3Service.downloadFile(doc.getS3Key());
            InputStreamResource resource = new InputStreamResource(pdfStream);
            
            DocumentReader reader = new PagePdfDocumentReader(resource);
            List<org.springframework.ai.document.Document> documents = reader.get();
            
            log.info("Read {} pages from PDF", documents.size());
            
            // Standard splitter for OpenAI
            TokenTextSplitter splitter = new TokenTextSplitter();
            List<org.springframework.ai.document.Document> chunks = splitter.apply(documents);
            
            log.info("Split into {} chunks", chunks.size());
            
            chunks.forEach(chunk -> {
                Map<String, Object> metadata = new HashMap<>(chunk.getMetadata());
                metadata.put("document_id", documentId.toString());
                metadata.put("file_name", doc.getFileName());
                metadata.put("uploaded_by", doc.getUploadedBy());
                chunk.getMetadata().putAll(metadata);
            });
            
            vectorStore.add(chunks);
            
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
            // UPDATED: Lower threshold to 0.5 to find more matches
            SearchRequest searchRequest = SearchRequest.query(question)
                    .withTopK(5)
                    .withSimilarityThreshold(0.5); 
            
            List<org.springframework.ai.document.Document> relevantDocs = 
                    vectorStore.similaritySearch(searchRequest);
            
            // DEBUG LOG: See if Pinecone is actually returning anything
            log.info("Pinecone found {} relevant chunks for question: '{}'", relevantDocs.size(), question);
            
            if (relevantDocs.isEmpty()) {
                return "I searched your documents but couldn't find relevant information matching your question.";
            }
            
            String context = relevantDocs.stream()
                    .map(org.springframework.ai.document.Document::getContent)
                    .collect(Collectors.joining("\n\n"));
            
            String promptText = String.format("""
                You are a helpful AI assistant.
                Answer the question based ONLY on the provided context.
                If the answer isn't there, say "I don't have that information."
                
                Context:
                %s
                
                Question: %s
                """, context, question);
            
            ChatClient chatClient = chatClientBuilder.build();
            return chatClient.prompt().user(promptText).call().content();
            
        } catch (Exception e) {
            log.error("Error in askQuestion: {}", e.getMessage(), e);
            return "Sorry, I encountered an error processing your request.";
        }
    }
    
    /**
     * Ask question filtered by specific document
     */
    public String askQuestionByDocument(String question, Long documentId) {
        log.info("Received question for document {}: {}", documentId, question);
        
        try {
            documentRepository.findById(documentId)
                    .orElseThrow(() -> new RuntimeException("Document not found: " + documentId));
            
            // UPDATED: Lower threshold to 0.5
            SearchRequest searchRequest = SearchRequest.query(question)
                    .withTopK(10)
                    .withSimilarityThreshold(0.5);
            
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
            
            log.info("Pinecone found {} relevant chunks for document ID {}", filteredDocs.size(), documentId);
            
            if (filteredDocs.isEmpty()) {
                return "I couldn't find relevant information in this specific document.";
            }
            
            String context = filteredDocs.stream()
                    .map(org.springframework.ai.document.Document::getContent)
                    .collect(Collectors.joining("\n\n"));
            
            String promptText = String.format("""
                Answer the question based ONLY on this document content.
                
                Document content:
                %s
                
                Question: %s
                """, context, question);
            
            ChatClient chatClient = chatClientBuilder.build();
            return chatClient.prompt().user(promptText).call().content();
            
        } catch (Exception e) {
            log.error("Error in askQuestionByDocument: {}", e.getMessage(), e);
            return "Sorry, I encountered an error.";
        }
    }
}