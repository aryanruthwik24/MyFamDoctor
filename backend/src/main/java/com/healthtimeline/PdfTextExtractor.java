package com.healthtimeline;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
public class PdfTextExtractor {
    private static final Logger logger = LoggerFactory.getLogger(PdfTextExtractor.class);
    private static final String TESSERACT_MISSING_MARKER = "__TESSERACT_MISSING__";

    public String extract(Path pdfPath) throws IOException {
        try (PDDocument document = PDDocument.load(pdfPath.toFile())) {
            if (document.isEncrypted()) {
                throw new IOException("Encrypted PDFs are not supported.");
            }
            PDFTextStripper textStripper = new PDFTextStripper();
            textStripper.setSortByPosition(true);
            String extracted = textStripper.getText(document).trim();
            if (!extracted.isBlank()) {
                return extracted;
            }
            return ocrFallback(document, pdfPath);
        }
    }

    private String ocrFallback(PDDocument document, Path pdfPath) throws IOException {
        Path tempDir = Files.createTempDirectory("healthtimeline-ocr-");
        List<String> pageTexts = new ArrayList<>();
        boolean tesseractMissing = false;
        try {
            PDFRenderer renderer = new PDFRenderer(document);
            int pageCount = document.getNumberOfPages();
            for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
                BufferedImage image = renderer.renderImageWithDPI(pageIndex, 300);
                Path imagePath = tempDir.resolve("page-" + (pageIndex + 1) + ".png");
                ImageIO.write(image, "png", imagePath.toFile());
                String pageText = runTesseract(imagePath);
                if (TESSERACT_MISSING_MARKER.equals(pageText)) {
                    tesseractMissing = true;
                } else if (!pageText.isBlank()) {
                    pageTexts.add(pageText.trim());
                }
            }
        } finally {
            deleteRecursively(tempDir);
        }

        if (pageTexts.isEmpty() && tesseractMissing) {
            logger.warn("OCR attempted but tesseract executable was not found on PATH.");
            return "[No readable text was extracted. OCR unavailable: tesseract is not installed on the server PATH.]";
        }

        return String.join("\n\n", pageTexts).trim();
    }

    private String runTesseract(Path imagePath) {
        try {
            Process process = new ProcessBuilder(
                "tesseract",
                imagePath.toString(),
                "stdout",
                "--psm",
                "6"
            ).redirectErrorStream(true).start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append(System.lineSeparator());
                }
            }
            int exitCode = process.waitFor();
            return exitCode == 0 ? output.toString() : "";
        } catch (IOException ioe) {
            logger.warn("Failed to start tesseract process: {}", ioe.getMessage());
            return TESSERACT_MISSING_MARKER;
        } catch (Exception e) {
            logger.warn("Tesseract OCR error: {}", e.getMessage());
            return "";
        }
    }

    private void deleteRecursively(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (var walk = Files.walk(path)) {
            walk.sorted((left, right) -> right.getNameCount() - left.getNameCount())
                .forEach(currentPath -> {
                    try {
                        Files.deleteIfExists(currentPath);
                    } catch (IOException ignored) {
                    }
                });
        }
    }
}
