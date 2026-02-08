package com.knowledgebase.documentservice.controller;

import com.knowledgebase.documentservice.service.RagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Slf4j
public class ChatController {
    
    private final RagService ragService;
    
    /**
     * Ask a question across all documents
     */
    @PostMapping("/ask")
    public ResponseEntity<Map<String, String>> askQuestion(@RequestBody Map<String, String> request) {
        String question = request.get("question");
        
        if (question == null || question.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Question is required"));
        }
        
        log.info("Received chat question: {}", question);
        
        try {
            String answer = ragService.askQuestion(question);
            return ResponseEntity.ok(Map.of(
                    "question", question,
                    "answer", answer
            ));
        } catch (Exception e) {
            log.error("Error processing question: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to process question"));
        }
    }
    
    /**
     * Ask a question about a specific document
     */
    @PostMapping("/ask/{documentId}")
    public ResponseEntity<Map<String, String>> askQuestionByDocument(
            @PathVariable Long documentId,
            @RequestBody Map<String, String> request) {
        
        String question = request.get("question");
        
        if (question == null || question.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Question is required"));
        }
        
        log.info("Received question for document {}: {}", documentId, question);
        
        try {
            String answer = ragService.askQuestionByDocument(question, documentId);
            return ResponseEntity.ok(Map.of(
                    "question", question,
                    "answer", answer,
                    "documentId", documentId.toString()
            ));
        } catch (Exception e) {
            log.error("Error processing question: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * Health check for chat service
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "Chat service is running",
                "model", "Ollama Llama 3.2"
        ));
    }
}