package com.knowledgebase.documentservice.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class S3Service {
    
    private final AmazonS3 amazonS3;
    
    @Value("${aws.s3.bucket-name}")
    private String bucketName;
    
    /**
     * Upload file to S3 bucket
     * @param file MultipartFile to upload
     * @return S3 key (unique identifier for the file)
     */
    public String uploadFile(MultipartFile file) {
        String originalFileName = file.getOriginalFilename();
        String s3Key = generateS3Key(originalFileName);
        
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType(file.getContentType());
            metadata.setContentLength(file.getSize());
            
            InputStream inputStream = file.getInputStream();
            
            amazonS3.putObject(new PutObjectRequest(bucketName, s3Key, inputStream, metadata));
            
            log.info("File uploaded to S3: {}", s3Key);
            return s3Key;
            
        } catch (IOException e) {
            log.error("Error uploading file to S3: {}", e.getMessage());
            throw new RuntimeException("Failed to upload file to S3", e);
        }
    }
    
    /**
     * Download file from S3
     * @param s3Key S3 key of the file
     * @return InputStream of the file
     */
    public InputStream downloadFile(String s3Key) {
        try {
            S3Object s3Object = amazonS3.getObject(bucketName, s3Key);
            log.info("File downloaded from S3: {}", s3Key);
            return s3Object.getObjectContent();
        } catch (Exception e) {
            log.error("Error downloading file from S3: {}", e.getMessage());
            throw new RuntimeException("Failed to download file from S3", e);
        }
    }
    
    /**
     * Delete file from S3
     * @param s3Key S3 key of the file to delete
     */
    public void deleteFile(String s3Key) {
        try {
            amazonS3.deleteObject(bucketName, s3Key);
            log.info("File deleted from S3: {}", s3Key);
        } catch (Exception e) {
            log.error("Error deleting file from S3: {}", e.getMessage());
            throw new RuntimeException("Failed to delete file from S3", e);
        }
    }
    
    /**
     * Check if file exists in S3
     * @param s3Key S3 key of the file
     * @return true if exists, false otherwise
     */
    public boolean fileExists(String s3Key) {
        try {
            return amazonS3.doesObjectExist(bucketName, s3Key);
        } catch (Exception e) {
            log.error("Error checking file existence in S3: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Get file URL from S3
     * @param s3Key S3 key of the file
     * @return URL of the file
     */
    public String getFileUrl(String s3Key) {
        return amazonS3.getUrl(bucketName, s3Key).toString();
    }
    
    /**
     * Generate unique S3 key for file
     * @param originalFileName Original filename
     * @return Unique S3 key
     */
    private String generateS3Key(String originalFileName) {
        String uuid = UUID.randomUUID().toString();
        String extension = getFileExtension(originalFileName);
        return String.format("documents/%s_%s.%s", uuid, System.currentTimeMillis(), extension);
    }
    
    /**
     * Extract file extension from filename
     * @param fileName Filename
     * @return File extension
     */
    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1);
    }
}