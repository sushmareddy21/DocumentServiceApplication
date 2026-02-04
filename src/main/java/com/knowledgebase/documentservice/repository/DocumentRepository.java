

package com.knowledgebase.documentservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.knowledgebase.documentservice.entity.Document;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {
    
    // Find by file name
    Optional<Document> findByFileName(String fileName);
    
    // Find all documents by user
    List<Document> findByUploadedBy(String uploadedBy);
    
    // Find only processed documents (ready for AI)
    List<Document> findByIsProcessedTrue();
    
    // Find pending documents
    List<Document> findByIsProcessedFalse();
    
    // Check if file already exists
    boolean existsByS3Key(String s3Key);
}