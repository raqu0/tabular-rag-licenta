package com.licenta.tabular_rag;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api")
public class ChatController {

    private final AiService aiService;
    private final UserRepository userRepository;
    private final UserDocumentRepository documentRepository;
    private final QueryLogRepository queryLogRepository;

    // Reținem în memorie ultimul rezultat SQL brut al fiecărui utilizator, pentru export
    public static final Map<String, List<Map<String, Object>>> lastResultsByUser = new HashMap<>();
    public static final Map<String, String> lastQuestionByUser = new HashMap<>();

    @Autowired
    public ChatController(AiService aiService, UserRepository userRepository,
                          UserDocumentRepository documentRepository, QueryLogRepository queryLogRepository) {
        this.aiService = aiService;
        this.userRepository = userRepository;
        this.documentRepository = documentRepository;
        this.queryLogRepository = queryLogRepository;
    }

    @GetMapping("/chat/{username}")
    public ResponseEntity<String> chatWithCsv(
            @PathVariable String username,
            @RequestParam("intrebare") String intrebare,
            @RequestParam(value = "fileIds", required = false) List<Long> fileIds) {

        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("Eroare: Utilizatorul nu a fost găsit în sistem.");
        }

        User user = userOpt.get();

        List<UserDocument> userDocs = documentRepository.findByUser(user);
        if (userDocs.isEmpty()) {
            return ResponseEntity.ok("Nu ai încărcat niciun fișier CSV încă. Te rog să tragi un fișier " +
                    "în panoul din stânga pentru a putea începe analiza hibridă.");
        }

        try {
            // Determinăm ce fișiere folosim: cele bifate (fileIds) sau, dacă nu s-a bifat nimic, ultimul fișier
            List<UserDocument> selectedDocs = new java.util.ArrayList<>();
            if (fileIds != null && !fileIds.isEmpty()) {
                for (Long id : fileIds) {
                    documentRepository.findById(id)
                            .filter(doc -> doc.getUser() != null && doc.getUser().getId().equals(user.getId()))
                            .ifPresent(selectedDocs::add);
                }
            }
            // Dacă nu s-a selectat nimic valid, folosim ultimul fișier (comportamentul vechi)
            if (selectedDocs.isEmpty()) {
                selectedDocs.add(userDocs.get(userDocs.size() - 1));
            }
            // Limităm la maxim 2 fișiere
            if (selectedDocs.size() > 2) {
                selectedDocs = selectedDocs.subList(0, 2);
            }

            String sqlResult;
            String fileLabel;

            if (selectedDocs.size() == 2) {
                // DOUĂ FIȘIERE → JOIN
                String path1 = selectedDocs.get(0).getFilePath();
                String path2 = selectedDocs.get(1).getFilePath();
                sqlResult = aiService.runQueryWithRetryTwoFiles(intrebare, path1, path2);
                fileLabel = selectedDocs.get(0).getFileName() + " + " + selectedDocs.get(1).getFileName() + " (JOIN)";
            } else {
                // UN FIȘIER → flux normal
                String csvFilePath = selectedDocs.get(0).getFilePath();
                sqlResult = aiService.runQueryWithRetry(intrebare, csvFilePath);
                fileLabel = selectedDocs.get(0).getFileName();
            }

            // SQL-ul final care a funcționat (după eventualele corectări)
            String generatedSql = aiService.getLastWorkingSql();

            String finalAnswer = aiService.generateFinalAnswer(intrebare, sqlResult, sqlResult);

            // Salvăm rezultatul structurat (lista de rânduri) pentru export Excel/PDF
            List<Map<String, Object>> structuredResult = aiService.getLastQueryResultList();
            lastResultsByUser.put(username, structuredResult);
            lastQuestionByUser.put(username, intrebare);

            // Înregistrăm interogarea în log (pentru statistici admin)
            queryLogRepository.save(new QueryLog(user, intrebare));

            StringBuilder fullResponse = new StringBuilder();
            fullResponse.append("📂 Fișier analizat activ: ").append(fileLabel).append("\n");
            fullResponse.append("──────────────────────────────────\n");
            fullResponse.append("🔍 1. SQL Generat (Qwen-Coder):\n").append(generatedSql).append("\n\n");
            fullResponse.append("💾 2. Date Extrase:\n").append(sqlResult).append("\n\n");
            fullResponse.append("🤖 3. Concluzie (Llama3):\n").append(finalAnswer);

            return ResponseEntity.ok(fullResponse.toString());

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("A apărut o eroare în timpul procesării datelor: " + e.getMessage());
        }
    }
}