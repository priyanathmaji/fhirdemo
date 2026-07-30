package com.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class App {
    private static final int MARGIN = 50;
    private static final int MAX_WIDTH = 512; 
    private static int yPosition = 750;

    public static void main(String[] args) {
        File inputDir = new File("D:\\08102025\\App\\fhirpdfdemo\\fhirpdfdemo\\inputJSON");
        File outputDir = new File("D:\\08102025\\App\\fhirpdfdemo\\fhirpdfdemo\\output");

        // Ensure directories exist
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        // Check if directory exists and list files
        if (inputDir.exists() && inputDir.isDirectory()) {
            File[] jsonFiles = inputDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".json"));

            if (jsonFiles != null && jsonFiles.length > 0) {
                System.out.println("Found " + jsonFiles.length + " JSON file(s) to process.");
                
                for (File file : jsonFiles) {
                    processJsonToPdf(file, outputDir);
                }
            } else {
                System.out.println("No JSON files found in the input folder: " + inputDir.getAbsolutePath());
            }
        } else {
            System.err.println("Input directory does not exist: " + inputDir.getAbsolutePath());
        }
    }

    private static void processJsonToPdf(File inputFile, File outputDir) {
        // Create an output name based on the input name (e.g., sample.json -> sample.pdf)
        String outputFileName = inputFile.getName().substring(0, inputFile.getName().lastIndexOf(".")) + ".pdf";
        File outputFile = new File(outputDir, outputFileName);

        System.out.println("Processing: " + inputFile.getName() + " -> " + outputFileName);
        yPosition = 750; // Reset page Y coordinate baseline for each new document context

        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                PDType1Font boldFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
                PDType1Font regularFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

                // Document Header Title
                contentStream.beginText();
                contentStream.setFont(boldFont, 16);
                contentStream.newLineAtOffset(MARGIN, yPosition);
                contentStream.showText("Da Vinci DTR Response PDF Export");
                yPosition -= 25;
                contentStream.endText();

                // Parse input file using Jackson
                ObjectMapper mapper = new ObjectMapper();
                JsonNode rootNode = mapper.readTree(inputFile);

                // File metadata readout properties injection framework
                String fileStatus = rootNode.has("status") ? rootNode.get("status").asText().toUpperCase() : "UNKNOWN";
                if (rootNode.has("entry") && rootNode.get("entry").isArray()) {
                    JsonNode resourceNode = rootNode.get("entry").get(0).get("resource");
                    if (resourceNode != null && resourceNode.has("status")) {
                        fileStatus = resourceNode.get("status").asText().toUpperCase();
                    }
                }

                contentStream.beginText();
                contentStream.setFont(regularFont, 10);
                contentStream.newLineAtOffset(MARGIN, yPosition);
                contentStream.showText("Source File: " + inputFile.getName() + "  |  Status: " + fileStatus);
                yPosition -= 35;
                contentStream.endText();

                // Extract QuestionnaireResponse array from Bundle or Standalone Resource
                JsonNode itemsArray = null;
                if (rootNode.has("entry") && rootNode.get("entry").isArray()) {
                    JsonNode resourceNode = rootNode.get("entry").get(0).get("resource");
                    if (resourceNode != null && resourceNode.has("item")) {
                        itemsArray = resourceNode.get("item");
                    }
                } else if (rootNode.has("item") && rootNode.get("item").isArray()) {
                    itemsArray = rootNode.get("item");
                }

                // Render questions sequentially 
                if (itemsArray != null && itemsArray.isArray()) {
                    int questionCounter = 1;
                    for (JsonNode item : itemsArray) {
                        String rawQuestion = item.has("text") ? item.get("text").asText() : "No question text provided";
                        String questionText = "Q" + questionCounter + ": " + rawQuestion;
                        
                        String answerText = "Answer: [No Answer Provided]";
                        if (item.has("answer") && item.get("answer").isArray() && item.get("answer").size() > 0) {
                            JsonNode ansNode = item.get("answer").get(0);
                            if (ansNode.has("valueBoolean")) {
                                answerText = "Answer: " + ansNode.get("valueBoolean").asBoolean();
                            } else if (ansNode.has("valueInteger")) {
                                answerText = "Answer: " + ansNode.get("valueInteger").asInt();
                            } else if (ansNode.has("valueString")) {
                                answerText = "Answer: " + ansNode.get("valueString").asText();
                            }
                        }

                        // Write out wrapped block structural layouts
                        yPosition = writeWrappedText(contentStream, questionText, boldFont, 11, yPosition, MARGIN);
                        yPosition -= 4;
                        yPosition = writeWrappedText(contentStream, answerText, regularFont, 11, yPosition, MARGIN + 15);
                        yPosition -= 20; // Paragraph breaks allocation spacing boundary markup
                        
                        questionCounter++;
                    }
                } else {
                    contentStream.beginText();
                    contentStream.setFont(regularFont, 11);
                    contentStream.newLineAtOffset(MARGIN, yPosition);
                    contentStream.showText("Error: Structural clinical nodes match patterns failed.");
                    contentStream.endText();
                }
            }

            document.save(outputFile);
            System.out.println("SUCCESS -> Exported to: " + outputFile.getAbsolutePath());

        } catch (Exception e) {
            System.err.println("Failed to export file " + inputFile.getName() + " due to: " + e.getMessage());
        }
    }

    private static int writeWrappedText(PDPageContentStream stream, String text, PDType1Font font, float fontSize, int y, int xOffset) throws IOException {
        List<String> wrappedLines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            String testLine = currentLine.length() == 0 ? word : currentLine + " " + word;
            float lineWidth = fontSize * font.getStringWidth(testLine) / 1000f;
            if (lineWidth > MAX_WIDTH - (xOffset - MARGIN)) {
                wrappedLines.add(currentLine.toString());
                currentLine = new StringBuilder(word);
            } else {
                currentLine.append(currentLine.length() == 0 ? word : " " + word);
            }
        }
        if (currentLine.length() > 0) {
            wrappedLines.add(currentLine.toString());
        }

        for (String line : wrappedLines) {
            stream.beginText();
            stream.setFont(font, fontSize);
            stream.newLineAtOffset(xOffset, y);
            stream.showText(line);
            stream.endText();
            y -= 15;
        }
        return y;
    }
}
