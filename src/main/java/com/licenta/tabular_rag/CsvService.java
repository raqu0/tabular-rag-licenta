package com.licenta.tabular_rag;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class CsvService {

    public List<String> proceseazaCsv(MultipartFile file) throws Exception {
        List<String> randuriProcesate = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader().withIgnoreHeaderCase().withTrim())) {

            // Luăm numele coloanelor
            List<String> headers = csvParser.getHeaderNames();

            for (CSVRecord record : csvParser) {
                StringBuilder sb = new StringBuilder();
                sb.append("Informații rând: ");
                for (String header : headers) {
                    sb.append(header).append(" este ").append(record.get(header)).append(", ");
                }
                randuriProcesate.add(sb.toString());
            }
        }
        return randuriProcesate;
    }
}