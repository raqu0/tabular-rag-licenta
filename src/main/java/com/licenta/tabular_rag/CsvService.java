package com.licenta.tabular_rag;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;

import java.io.FileReader;
import java.io.Reader;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CsvService {

    public String extractCsvSchemaForLLM(String filePath) {
        try (Reader reader = new FileReader(filePath);
             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader().withTrim())) {

            List<String> columnNames = csvParser.getHeaderNames();

            String exampleRow = "";
            for (CSVRecord record : csvParser) {
                exampleRow = record.toList().stream().collect(Collectors.joining(", "));
                break;
            }

            StringBuilder schemaContext = new StringBuilder();
            schemaContext.append("Baza de date conține un tabel cu următoarele coloane:\n");

            for (String col : columnNames) {
                schemaContext.append("- ").append(col).append("\n");
            }

            schemaContext.append("\nUn exemplu de rând din acest tabel arată așa pentru a înțelege formatul datelor:\n");
            schemaContext.append(exampleRow).append("\n");
            schemaContext.append("\nFolosește exclusiv aceste coloane pentru a genera interogări SQL valide.");

            return schemaContext.toString();

        } catch (Exception e) {
            e.printStackTrace();
            return "Eroare: Nu s-a putut citi schema CSV-ului.";
        }
    }
}