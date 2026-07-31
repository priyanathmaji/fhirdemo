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
    private static final int BOTTOM_MARGIN = 60;
    
    // Core fonts reused across the application layout context
    private static PDType1Font boldFont;
    private static PDType1Font regularFont;

    // Helper context object to carry page states safely without global statics
    private static class RenderState {
        PDDocument doc;
        PDPageContentStream stream;
        int y;

        RenderState(PDDocument doc) throws IOException {
            this.doc = doc;
            this.y = 740;
            PDPage firstPage = new PDPage();
            this.doc.addPage(firstPage);
            this.stream = new PDPageContentStream(doc, firstPage);
        }

        void checkAndTriggerPageBreak(float lineSpacing) throws IOException {
            if (this.y < BOTTOM_MARGIN) {
                this.stream.close(); // Close active writing frame context cleanly
                PDPage nextPage = new PDPage();
                this.doc.addPage(nextPage);
                this.stream = new PDPageContentStream(this.doc, nextPage);
                this.y = 740; // Reset upper structural limits
            }
        }
    }

    public static void main(String[] args) {
        File inputDir = new File("inputJSON");
        File outputDir = new File("output");

        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        boldFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        regularFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

        if (inputDir.exists() && inputDir.isDirectory()) {
            File[] jsonFiles = inputDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".json"));

            if (jsonFiles != null && jsonFiles.length > 0) {
                System.out.println("Discovered " + jsonFiles.length + " file(s) in local workspace.");
                for (File file : jsonFiles) {
                    processJsonToPdf(file, outputDir);
                }
            } else {
                System.out.println("No JSON files found in inputJSON folder.");
            }
        } else {
            System.err.println("Input directory 'inputJSON' not found at project root.");
        }
    }

    private static void processJsonToPdf(File inputFile, File outputDir) {
        String outputFileName = inputFile.getName().substring(0, inputFile.getName().lastIndexOf(".")) + ".pdf";
        File outputFile = new File(outputDir, outputFileName);

        System.out.println("Generating document context: " + outputFile.getName());

        // Use modern try-with-resources to safeguard file locking overrides
        try (PDDocument document = new PDDocument()) {
            RenderState state = new RenderState(document);

            // Print Document Header Title Block Banner
            writeLine("Da Vinci DTR Response Export", boldFont, 16, MARGIN, state);
            state.y -= 15;

            // Load and read files using Jackson
            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(inputFile);

            // Locate base entries array
            JsonNode baseItems = null;
            if (rootNode.has("entry") && rootNode.get("entry").isArray()) {
                JsonNode resource = rootNode.get("entry").get(0).get("resource");
                if (resource != null && resource.has("item")) {
                    baseItems = resource.get("item");
                }
            } else if (rootNode.has("item") && rootNode.get("item").isArray()) {
                baseItems = rootNode.get("item");
            }

            // Execute tree traversal safely passing state objects
            if (baseItems != null && baseItems.isArray()) {
                for (JsonNode structuralItem : baseItems) {
                    parseItemRecursive(structuralItem, 0, state);
                }
            } else {
                writeLine("Error: No structural question item blocks detected in data stream payload.", regularFont, 11, MARGIN, state);
            }

            // Cleanly wind down structural elements row updates
            if (state.stream != null) {
                state.stream.close();
            }
            
            document.save(outputFile);
            System.out.println("SUCCESS -> Processed and saved to: " + outputFile.getAbsolutePath());

        } catch (Exception e) {
            System.err.println("Critical processing exception encountered: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void parseItemRecursive(JsonNode item, int depth, RenderState state) throws IOException {
        int currentIndent = MARGIN + (depth * 15);
        
        if (item.has("text")) {
            String questionPrefix = item.has("answer") ? " - " : "";
            String questionText = questionPrefix + item.get("text").asText();
            
            PDType1Font fontToUse = (depth == 0) ? boldFont : regularFont;
            float sizeToUse = (depth == 0) ? 12 : 10.5f;
            
            if (depth == 0) state.y -= 10;
            writeLine(questionText, fontToUse, sizeToUse, currentIndent, state);
        }

        if (item.has("answer") && item.get("answer").isArray()) {
            for (JsonNode ans : item.get("answer")) {
                String extractedAnswerValue = "";

                if (ans.has("valueBoolean")) extractedAnswerValue = String.valueOf(ans.get("valueBoolean").asBoolean());
                else if (ans.has("valueInteger")) extractedAnswerValue = String.valueOf(ans.get("valueInteger").asInt());
                else if (ans.has("valueString")) extractedAnswerValue = ans.get("valueString").asText();
                else if (ans.has("valueDate")) extractedAnswerValue = ans.get("valueDate").asText();
                else if (ans.has("valueCoding") && ans.get("valueCoding").has("display")) {
                    extractedAnswerValue = ans.get("valueCoding").get("display").asText();
                }

                if (!extractedAnswerValue.isEmpty()) {
                    String formattedAnswer = "  -> Answer: " + extractedAnswerValue;
                    writeLine(formattedAnswer, regularFont, 10.5f, currentIndent + 12, state);
                }
            }
            state.y -= 5;
        }

        if (item.has("item") && item.get("item").isArray()) {
            for (JsonNode childItem : item.get("item")) {
                parseItemRecursive(childItem, depth + 1, state);
            }
        }
    }

    private static void writeLine(String text, PDType1Font font, float fontSize, int xOffset, RenderState state) throws IOException {
        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder activeLine = new StringBuilder();

        int availableWidth = MAX_WIDTH - (xOffset - MARGIN);

        for (String word : words) {
            String evaluationString = activeLine.length() == 0 ? word : activeLine + " " + word;
            float evaluatedLineWidth = fontSize * font.getStringWidth(evaluationString) / 1000f;
            
            if (evaluatedLineWidth > availableWidth) {
                lines.add(activeLine.toString());
                activeLine = new StringBuilder(word);
            } else {
                activeLine.append(activeLine.length() == 0 ? word : " " + word);
            }
        }
        if (activeLine.length() > 0) {
            lines.add(activeLine.toString());
        }

        for (String lineText : lines) {
            state.checkAndTriggerPageBreak(16);

            state.stream.beginText();
            state.stream.setFont(font, fontSize);
            state.stream.newLineAtOffset(xOffset, state.y);
            state.stream.showText(lineText);
            state.stream.endText();
            
            state.y -= 16; 
        }
    }
}
