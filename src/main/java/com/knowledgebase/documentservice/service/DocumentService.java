package com.knowledgebase.documentservice.service;

import com.knowledgebase.documentservice.dto.DocumentDTO;
import com.knowledgebase.documentservice.entity.Document;
import com.knowledgebase.documentservice.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {
    
    private final DocumentRepository documentRepository;
    private final S3Service s3Service;
    private final PdfService pdfService;
    
    public List<DocumentDTO> getAllDocuments() {
        log.info("Fetching all documents");
        return documentRepository.findAll()
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    public DocumentDTO getDocumentById(Long id) {
        log.info("Fetching document with id: {}", id);
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Document not found with id: " + id));
        return convertToDTO(document);
    }
    
    public List<DocumentDTO> getDocumentsByUser(String username) {
        log.info("Fetching documents for user: {}", username);
        return documentRepository.findByUploadedBy(username)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    @Transactional
    public DocumentDTO uploadDocument(MultipartFile file, String uploadedBy, String description) {
        log.info("Processing file upload: {}", file.getOriginalFilename());
        
        // Validate file
        if (file.isEmpty()) {
            throw new RuntimeException("Cannot upload empty file");
        }
        
        String contentType = file.getContentType();
        if (contentType == null || !contentType.equals("application/pdf")) {
            throw new RuntimeException("Only PDF files are allowed");
        }
        
        // Upload to S3
        String s3Key = s3Service.uploadFile(file);
        
        // Download from S3 temporarily to extract PDF metadata
        File tempFile = null;
        int pageCount = 0;
        
        try {
            tempFile = File.createTempFile("temp_pdf_", ".pdf");
            InputStream s3Stream = s3Service.downloadFile(s3Key);
            
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = s3Stream.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
            }
            
            // Extract page count
            pageCount = pdfService.getPageCount(tempFile.toPath());
            
            // Validate PDF
            if (!pdfService.isValidPdf(tempFile.toPath())) {
                s3Service.deleteFile(s3Key);
                throw new RuntimeException("Invalid PDF file");
            }
            
        } catch (Exception e) {
            s3Service.deleteFile(s3Key);
            log.error("Error processing PDF: {}", e.getMessage());
            throw new RuntimeException("Failed to process PDF file", e);
        } finally {
            // Clean up temp file
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }
        
        // Create document entity
        Document document = new Document();
        document.setFileName(file.getOriginalFilename());
        document.setFileType("PDF");
        document.setFileSize(file.getSize());
        document.setS3Key(s3Key);
        document.setDescription(description != null ? description : "");
        document.setUploadedBy(uploadedBy);
        document.setPageCount(pageCount);
        document.setIsProcessed(false);
        
        // Save to database
        Document savedDocument = documentRepository.save(document);
        log.info("Document uploaded successfully to S3: {}", savedDocument.getId());
        
        return convertToDTO(savedDocument);
    }
    
    @Transactional
    public DocumentDTO saveDocument(Document document) {
        log.info("Saving document: {}", document.getFileName());
        Document savedDocument = documentRepository.save(document);
        return convertToDTO(savedDocument);
    }
    
    @Transactional
    public void deleteDocument(Long id) {
        log.info("Deleting document with id: {}", id);
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Document not found with id: " + id));
        
        // Delete file from S3
        try {
            s3Service.deleteFile(document.getS3Key());
        } catch (Exception e) {
            log.warn("Could not delete file from S3: {}", document.getS3Key());
        }
        
        // Delete from database
        documentRepository.deleteById(id);
    }
    
    @Transactional
    public DocumentDTO markAsProcessed(Long id) {
        log.info("Marking document as processed: {}", id);
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Document not found with id: " + id));
        document.setIsProcessed(true);
        return convertToDTO(documentRepository.save(document));
    }
    
    public String getDocumentUrl(Long id) {
        log.info("Getting S3 URL for document: {}", id);
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Document not found with id: " + id));
        return s3Service.getFileUrl(document.getS3Key());
    }
    
    private DocumentDTO convertToDTO(Document document) {
        DocumentDTO dto = new DocumentDTO();
        dto.setId(document.getId());
        dto.setFileName(document.getFileName());
        dto.setFileType(document.getFileType());
        dto.setFileSize(document.getFileSize());
        dto.setDescription(document.getDescription());
        dto.setUploadedAt(document.getUploadedAt());
        dto.setUploadedBy(document.getUploadedBy());
        dto.setIsProcessed(document.getIsProcessed());
        dto.setPageCount(document.getPageCount());
        return dto;
    }
}