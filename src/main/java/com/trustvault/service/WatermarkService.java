package com.trustvault.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.util.Matrix;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class WatermarkService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public byte[] applyWatermarkToPdf(InputStream inputStream, String username, String ipAddress) {
        try (PDDocument document = Loader.loadPDF(inputStream.readAllBytes())) {
            String timestamp = LocalDateTime.now().format(FORMATTER);
            String watermarkText = String.format("CONFIDENTIAL - TRUSTVAULT | USER: %s | %s | IP: %s",
                    username, timestamp, ipAddress != null ? ipAddress : "UNKNOWN");

            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

            for (PDPage page : document.getPages()) {
                float pageWidth = page.getMediaBox().getWidth();
                float pageHeight = page.getMediaBox().getHeight();

                PDExtendedGraphicsState graphicsState = new PDExtendedGraphicsState();
                graphicsState.setNonStrokingAlphaConstant(0.22f); // Semi-transparent watermark

                try (PDPageContentStream contentStream = new PDPageContentStream(
                        document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                    contentStream.setGraphicsStateParameters(graphicsState);
                    contentStream.setFont(font, 14);
                    contentStream.setNonStrokingColor(180, 0, 0); // Subtle red security tint

                    // Diagonal placement across center
                    contentStream.beginText();
                    Matrix matrix = Matrix.getRotateInstance(Math.toRadians(45), pageWidth / 4, pageHeight / 3);
                    contentStream.setTextMatrix(matrix);
                    contentStream.showText(watermarkText);
                    contentStream.endText();

                    // Top and bottom banner watermarks
                    contentStream.beginText();
                    contentStream.setTextMatrix(Matrix.getTranslateInstance(40, pageHeight - 25));
                    contentStream.setFont(font, 9);
                    contentStream.showText("RESTRICTED - DO NOT DISTRIBUTE | TRUSTVAULT AUDITED ACCESS | " + username);
                    contentStream.endText();

                    contentStream.beginText();
                    contentStream.setTextMatrix(Matrix.getTranslateInstance(40, 20));
                    contentStream.setFont(font, 9);
                    contentStream.showText("SECURITY STAMP: " + watermarkText);
                    contentStream.endText();
                }
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            document.save(outputStream);
            return outputStream.toByteArray();
        } catch (Exception e) {
            // If watermarking fails or file is not valid PDF, throw runtime exception
            throw new RuntimeException("Error applying dynamic security watermark to document: " + e.getMessage(), e);
        }
    }
}
