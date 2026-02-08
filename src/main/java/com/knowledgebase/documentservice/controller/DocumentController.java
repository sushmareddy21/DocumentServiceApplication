package com.knowledgebase.documentservice.controller;

import com.knowledgebase.documentservice.dto.DocumentDTO;
import com.knowledgebase.documentservice.entity.Document;
import com.knowledgebase.documentservice.service.DocumentService;
import com.knowledgebase.documentservice.service.RagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@Slf4j
public class DocumentController {
    
    private final DocumentService documentService;
    private final RagService ragService;
    
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Document Service is running!");
    }
    
    @GetMapping
    public ResponseEntity<List<DocumentDTO>> getAllDocuments() {
        log.info("GET /api/documents - Fetching all documents");
        List<DocumentDTO> documents = documentService.getAllDocuments();
        return ResponseEntity.ok(documents);
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<DocumentDTO> getDocumentById(@PathVariable Long id) {
        log.info("GET /api/documents/{} - Fetching document", id);
        DocumentDTO document = documentService.getDocumentById(id);
        return ResponseEntity.ok(document);
    }
    
    @GetMapping("/user/{username}")
    public ResponseEntity<List<DocumentDTO>> getDocumentsByUser(@PathVariable String username) {
        log.info("GET /api/documents/user/{} - Fetching documents for user", username);
        List<DocumentDTO> documents = documentService.getDocumentsByUser(username);
        return ResponseEntity.ok(documents);
    }
    
    // NEW: File Upload Endpoint
    @PostMapping("/upload")
    public ResponseEntity<DocumentDTO> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "uploadedBy", defaultValue = "anonymous") String uploadedBy,
            @RequestParam(value = "description", required = false) String description) {
        
        log.info("POST /api/documents/upload - Uploading file: {}", file.getOriginalFilename());
        
        try {
            DocumentDTO document = documentService.uploadDocument(file, uploadedBy, description);
            
            // Process document for RAG in background
            try {
                ragService.processDocument(document.getId());
                log.info("Document processed for RAG: {}", document.getId());
            } catch (Exception e) {
                log.error("Error processing document for RAG: {}", e.getMessage());
                // Don't fail the upload if RAG processing fails
            }
            
            return ResponseEntity.status(HttpStatus.CREATED).body(document);
        } catch (Exception e) {
            log.error("Error uploading document: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }
    
    // Keep old endpoint for testing
    @PostMapping
    public ResponseEntity<DocumentDTO> createDocument(@RequestBody Document document) {
        log.info("POST /api/documents - Creating document: {}", document.getFileName());
        DocumentDTO savedDocument = documentService.saveDocument(document);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedDocument);
    }
    
    @PatchMapping("/{id}/process")
    public ResponseEntity<DocumentDTO> markAsProcessed(@PathVariable Long id) {
        log.info("PATCH /api/documents/{}/process - Marking as processed", id);
        DocumentDTO document = documentService.markAsProcessed(id);
        return ResponseEntity.ok(document);
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDocument(@PathVariable Long id) {
        log.info("DELETE /api/documents/{} - Deleting document", id);
        documentService.deleteDocument(id);
        return ResponseEntity.noContent().build();
    }
    
    // Get S3 URL for document
    @GetMapping("/{id}/url")
    public ResponseEntity<String> getDocumentUrl(@PathVariable Long id) {
        log.info("GET /api/documents/{}/url - Getting S3 URL", id);
        String url = documentService.getDocumentUrl(id);
        return ResponseEntity.ok(url);
    }
}