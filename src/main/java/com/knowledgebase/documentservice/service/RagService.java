package com.knowledgebase.documentservice.service;

import com.knowledgebase.documentservice.entity.Document;
import com.knowledgebase.documentservice.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;
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
     * Process document and store in vector database
     */
    public void processDocument(Long documentId) {
        log.info("Processing document for RAG: {}", documentId);
        
        // Get document from database
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found: " + documentId));
        
        try {
            // Download PDF from S3
            InputStream pdfStream = s3Service.downloadFile(doc.getS3Key());
            InputStreamResource resource = new InputStreamResource(pdfStream);
            
            // Read PDF and convert to Spring AI documents
            DocumentReader reader = new PagePdfDocumentReader(resource);
            List<org.springframework.ai.document.Document> documents = reader.get();
            
            log.info("Read {} pages from PDF", documents.size());
            
            // Split documents into chunks for better retrieval
            // We use specific values here optimized for Ollama/Llama 3.2 (Smaller chunks = faster/better accuracy)
            // (chunkSize=500, minChars=350, minEmbed=5, maxChunks=2000, keepSep=true)
            TokenTextSplitter splitter = new TokenTextSplitter(500, 350, 5, 2000, true);
            List<org.springframework.ai.document.Document> chunks = splitter.apply(documents);
            
            log.info("Split into {} chunks", chunks.size());
            
            // Add metadata to each chunk
            chunks.forEach(chunk -> {
                chunk.getMetadata().put("document_id", documentId);
                chunk.getMetadata().put("file_name", doc.getFileName());
                chunk.getMetadata().put("uploaded_by", doc.getUploadedBy());
            });
            
            // Store in vector database
            vectorStore.add(chunks);
            
            // Mark document as processed
            doc.setIsProcessed(true);
            documentRepository.save(doc);
            
            log.info("Document processed successfully: {}", documentId);
            
        } catch (Exception e) {
            log.error("Error processing document: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process document", e);
        }
    }
    
    /**
     * Ask a question about the documents
     */
    public String askQuestion(String question) {
        log.info("Received question: {}", question);
        
        // Search for relevant document chunks
        List<org.springframework.ai.document.Document> relevantDocs = vectorStore
                .similaritySearch(question);
        
        if (relevantDocs.isEmpty()) {
            return "I don't have enough information to answer that question. Please upload relevant documents first.";
        }
        
        log.info("Found {} relevant chunks", relevantDocs.size());
        
        // Build context from relevant documents
        String context = relevantDocs.stream()
                .map(org.springframework.ai.document.Document::getContent)
                .collect(Collectors.joining("\n\n"));
        
        // Create prompt with context
        String promptText = """
                You are a helpful assistant that answers questions based on the provided context.
                Use only the information from the context to answer the question.
                If the answer is not in the context, say "I don't have that information."
                
                Context:
                %s
                
                Question: %s
                
                Answer:
                """.formatted(context, question);
        
        // Get response from Ollama
        ChatClient chatClient = chatClientBuilder.build();
        ChatResponse response = chatClient.prompt(new Prompt(promptText)).call().chatResponse();
        
        String answer = response.getResult().getOutput().getContent();
        log.info("Generated answer: {}", answer);
        
        return answer;
    }
    
    /**
     * Ask question with document filtering
     */
    public String askQuestionByDocument(String question, Long documentId) {
        log.info("Received question for document {}: {}", documentId, question);
        
        // Verify document exists
        documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found: " + documentId));
        
        // Search with metadata filter
        List<org.springframework.ai.document.Document> relevantDocs = vectorStore
                .similaritySearch(question);
        
        // Filter by document ID (Manual filtering for SimpleVectorStore/PGVector compatibility)
        List<org.springframework.ai.document.Document> filteredDocs = relevantDocs.stream()
                .filter(doc -> {
                    Object docId = doc.getMetadata().get("document_id");
                    return docId != null && docId.toString().equals(documentId.toString());
                })
                .collect(Collectors.toList());
        
        if (filteredDocs.isEmpty()) {
            return "I couldn't find relevant information in this specific document.";
        }
        
        // Build context
        String context = filteredDocs.stream()
                .map(org.springframework.ai.document.Document::getContent)
                .collect(Collectors.joining("\n\n"));
        
        // Create prompt
        String promptText = """
                Answer the question based only on this document content.
                
                Context:
                %s
                
                Question: %s
                
                Answer:
                """.formatted(context, question);
        
        // Get response
        ChatClient chatClient = chatClientBuilder.build();
        ChatResponse response = chatClient.prompt(new Prompt(promptText)).call().chatResponse();
        
        return response.getResult().getOutput().getContent();
    }
}