package com.licenta.tabular_rag;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.itextpdf.text.Document;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.BaseColor;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/export")
public class ExportController {

    private final AiService aiService;

    @Autowired
    public ExportController(AiService aiService) {
        this.aiService = aiService;
    }

    @GetMapping("/excel/{username}")
    public ResponseEntity<ByteArrayResource> exportExcel(@PathVariable String username) throws Exception {
        List<Map<String, Object>> data = ChatController.lastResultsByUser.get(username);

        if (data == null || data.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Rezultate");

            // Header
            Row headerRow = sheet.createRow(0);
            Map<String, Object> firstRow = data.get(0);
            int colIndex = 0;
            CellStyle headerStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            for (String column : firstRow.keySet()) {
                Cell cell = headerRow.createCell(colIndex++);
                cell.setCellValue(column);
                cell.setCellStyle(headerStyle);
            }

            // Date
            int rowIndex = 1;
            for (Map<String, Object> rowData : data) {
                Row row = sheet.createRow(rowIndex++);
                colIndex = 0;
                for (Object value : rowData.values()) {
                    Cell cell = row.createCell(colIndex++);
                    cell.setCellValue(value == null ? "" : value.toString());
                }
            }

            // Auto-resize coloane
            for (int i = 0; i < firstRow.keySet().size(); i++) {
                sheet.autoSizeColumn(i);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);

            ByteArrayResource resource = new ByteArrayResource(out.toByteArray());

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=export_rezultate.xlsx")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .contentLength(resource.contentLength())
                    .body(resource);
        }
    }

    @GetMapping("/data/{username}")
    public ResponseEntity<List<Map<String, Object>>> getChartData(@PathVariable String username) {
        List<Map<String, Object>> data = ChatController.lastResultsByUser.get(username);
        if (data == null || data.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(data);
    }

    @GetMapping("/pdf/{username}")
    public ResponseEntity<ByteArrayResource> exportPdf(@PathVariable String username) throws Exception {
        List<Map<String, Object>> data = ChatController.lastResultsByUser.get(username);
        String question = ChatController.lastQuestionByUser.getOrDefault(username, "");

        if (data == null || data.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4);
        PdfWriter.getInstance(document, out);
        document.open();

        com.itextpdf.text.Font titleFont = new com.itextpdf.text.Font(com.itextpdf.text.Font.FontFamily.HELVETICA, 16, com.itextpdf.text.Font.BOLD);
        com.itextpdf.text.Font normalFont = new com.itextpdf.text.Font(com.itextpdf.text.Font.FontFamily.HELVETICA, 11, com.itextpdf.text.Font.NORMAL);
        com.itextpdf.text.Font headerFont = new com.itextpdf.text.Font(com.itextpdf.text.Font.FontFamily.HELVETICA, 11, com.itextpdf.text.Font.BOLD, BaseColor.WHITE);

        Paragraph title = new Paragraph("Nexus RAG - Export Rezultate", titleFont);
        title.setSpacingAfter(10);
        document.add(title);

        if (!question.isEmpty()) {
            Paragraph questionPar = new Paragraph("Întrebare: " + question, normalFont);
            questionPar.setSpacingAfter(15);
            document.add(questionPar);
        }

        Map<String, Object> firstRow = data.get(0);
        int numColumns = firstRow.keySet().size();

        PdfPTable table = new PdfPTable(numColumns);
        table.setWidthPercentage(100);

        for (String column : firstRow.keySet()) {
            PdfPCell cell = new PdfPCell(new Phrase(column, headerFont));
            cell.setBackgroundColor(new BaseColor(79, 70, 229)); // indigo
            cell.setPadding(6);
            table.addCell(cell);
        }

        for (Map<String, Object> rowData : data) {
            for (Object value : rowData.values()) {
                PdfPCell cell = new PdfPCell(new Phrase(value == null ? "" : value.toString(), normalFont));
                cell.setPadding(5);
                table.addCell(cell);
            }
        }

        document.add(table);

        Paragraph footer = new Paragraph("\nTotal înregistrări: " + data.size(), normalFont);
        document.add(footer);

        document.close();

        ByteArrayResource resource = new ByteArrayResource(out.toByteArray());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=export_rezultate.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(resource.contentLength())
                .body(resource);
    }
}