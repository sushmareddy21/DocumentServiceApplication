package com.knowledgebase.documentservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentDTO {
    private Long id;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private String description;
    private LocalDateTime uploadedAt;
    private String uploadedBy;
    private Boolean isProcessed;
    private Integer pageCount;
    
    // Helper method to convert MB
    public String getFileSizeInMB() {
        return String.format("%.2f MB", fileSize / (1024.0 * 1024.0));
    }
}