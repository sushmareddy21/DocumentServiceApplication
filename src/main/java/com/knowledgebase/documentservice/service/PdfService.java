package com.knowledgebase.documentservice.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;

@Service
@Slf4j
public class PdfService {
    
    public int getPageCount(Path filePath) {
        try (PDDocument document = Loader.loadPDF(filePath.toFile())) {
            int pageCount = document.getNumberOfPages();
            log.info("PDF has {} pages", pageCount);
            return pageCount;
        } catch (IOException e) {
            log.error("Error reading PDF: {}", e.getMessage());
            return 0;
        }
    }
    
    public String extractText(Path filePath) {
        try (PDDocument document = Loader.loadPDF(filePath.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            log.info("Extracted {} characters from PDF", text.length());
            return text;
        } catch (IOException e) {
            log.error("Error extracting text from PDF: {}", e.getMessage());
            return "";
        }
    }
    
    public boolean isValidPdf(Path filePath) {
        try (PDDocument document = Loader.loadPDF(filePath.toFile())) {
            return document.getNumberOfPages() > 0;
        } catch (IOException e) {
            log.error("Invalid PDF file: {}", e.getMessage());
            return false;
        }
    }
}