package com.licenta.tabular_rag;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api")
public class ChatController {

    private final ChatLanguageModel chatModel;
    private final CsvService csvService;
    private final EmbeddingModel embeddingModel;

    // Conexiunea la baza de date H2
    private Connection conn;

    // =========================================================================================
    // MULTI-TENANCY: "Dulăpioarele" separate pentru fiecare utilizator
    // =========================================================================================
    private Map<String, EmbeddingStore<TextSegment>> userVectorStores = new ConcurrentHashMap<>();
    private Map<String, ChatMemory> userMemories = new ConcurrentHashMap<>();
    private Map<String, List<String>> userTableColumns = new ConcurrentHashMap<>();

    public ChatController(CsvService csvService) {
        this.csvService = csvService;

        this.chatModel = OllamaChatModel.builder()
                .baseUrl("http://localhost:11434")
                .modelName("llama3.1")
                .temperature(0.0)
                .timeout(Duration.ofMinutes(5))
                .build();

        this.embeddingModel = new AllMiniLmL6V2EmbeddingModel();

        try {
            Class.forName("org.h2.Driver");
            this.conn = DriverManager.getConnection("jdbc:h2:mem:tabular_db;DB_CLOSE_DELAY=-1", "sa", "");
            System.out.println("Baza de date relațională H2 a fost inițializată cu succes!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Endpoint nou pentru a trimite numele utilizatorului logat securizat către frontend
    @GetMapping("/user/me")
    public String getLoggedInUser(java.security.Principal principal) {
        return principal != null ? principal.getName() : "anonim";
    }

    // NOU: Endpoint-ul cere acum un parametru "username"
    @PostMapping("/upload/{username}")
    public String uploadCsv(@PathVariable String username, @RequestParam("file") MultipartFile file) {
        try {
            String continutFisier = new String(file.getBytes(), StandardCharsets.UTF_8);
            continutFisier = continutFisier.replace("\uFEFF", ""); // Curățăm BOM-ul invizibil
            String[] randuriFisier = continutFisier.split("\\r?\\n");

            if (randuriFisier.length < 2) return "Fișierul CSV este gol!";

            // Pregătim mediul strict pentru ACEST utilizator
            userVectorStores.put(username, new InMemoryEmbeddingStore<>());
            userMemories.put(username, MessageWindowChatMemory.withMaxMessages(10));
            List<String> coloane = new ArrayList<>();

            String[] rawHeaders = randuriFisier[0].split(",");
            for (int i = 0; i < rawHeaders.length; i++) {
                String cleanHeader = rawHeaders[i].trim().replaceAll("[^a-zA-Z0-9_]", "_");
                cleanHeader = cleanHeader.replaceAll("^_+", "");
                if (cleanHeader.isEmpty()) cleanHeader = "coloana_" + i;
                coloane.add(cleanHeader);
            }
            userTableColumns.put(username, coloane);

            // Numele tabelului SQL va fi unic per utilizator
            String tableName = "tabel_" + username;

            Statement stmt = conn.createStatement();
            stmt.execute("DROP TABLE IF EXISTS " + tableName);

            StringBuilder createSql = new StringBuilder("CREATE TABLE ").append(tableName).append(" (");
            for (int i = 0; i < coloane.size(); i++) {
                createSql.append(coloane.get(i)).append(" VARCHAR(255)");
                if (i < coloane.size() - 1) createSql.append(", ");
            }
            createSql.append(")");
            stmt.execute(createSql.toString());

            StringBuilder insertSql = new StringBuilder("INSERT INTO ").append(tableName).append(" VALUES (");
            for (int i = 0; i < coloane.size(); i++) {
                insertSql.append("?");
                if (i < coloane.size() - 1) insertSql.append(", ");
            }
            insertSql.append(")");
            PreparedStatement pstmt = conn.prepareStatement(insertSql.toString());

            System.out.println("Incepe indexarea SQL pentru userul: " + username);
            for (int r = 1; r < randuriFisier.length; r++) {
                String[] valori = randuriFisier[r].split(",");
                for (int i = 0; i < coloane.size(); i++) {
                    if (i < valori.length) pstmt.setString(i + 1, valori[i].trim());
                    else pstmt.setString(i + 1, "");
                }
                pstmt.addBatch();
            }
            pstmt.executeBatch();

            System.out.println("Incepe indexarea Vectoriala pentru userul: " + username);
            List<String> dateVectoriale = csvService.proceseazaCsv(file);
            EmbeddingStore<TextSegment> store = userVectorStores.get(username);
            for (String rand : dateVectoriale) {
                TextSegment segment = TextSegment.from(rand);
                store.add(embeddingModel.embed(segment).content(), segment);
            }

            return "Fișier procesat cu succes pentru utilizatorul: " + username;

        } catch (Exception e) {
            e.printStackTrace();
            return "Eroare la procesarea hibridă: " + e.getMessage();
        }
    }

    // NOU: Chat-ul cere acum parametrul "username"
    @GetMapping("/chat/{username}")
    public String chat(@PathVariable String username, @RequestParam String intrebare) {
        try {
            // Verificăm dacă utilizatorul a încărcat vreun fișier
            if (!userMemories.containsKey(username)) {
                return "Te rog să încarci un fișier CSV mai întâi!";
            }

            ChatMemory memory = userMemories.get(username);
            List<String> coloane = userTableColumns.get(username);
            EmbeddingStore<TextSegment> store = userVectorStores.get(username);
            String tableName = "tabel_" + username;

            String promptRescriere = "Analizează acest istoric:\n" + memory.messages().toString() +
                    "\nNoua întrebare este: \"" + intrebare + "\"\n" +
                    "Dacă depinde de context, rescrie-o. Altfel, las-o. Returnează STRICT întrebarea finală.";
            String intrebareClara = chatModel.generate(promptRescriere).trim();
            if (intrebareClara.toLowerCase().startsWith("noua întrebare")) intrebareClara = intrebare;

            String promptDecizie = "Ești expert în baze de date. Avem tabelul '" + tableName + "' cu coloanele: " + String.join(", ", coloane) + "\n\n" +
                    "Analizează întrebarea: \"" + intrebareClara + "\"\n" +
                    "Sarcina ta:\n" +
                    "1. Dacă e întrebare matematică/numărătoare/extragere exactă, scrie cod SQL valid pentru H2 pe acest tabel.\n" +
                    "   - Tradu conceptele din română în engleză la singular pentru WHERE.\n" +
                    "   - Returnează STRICT codul SQL (fără ```sql).\n" +
                    "2. Altfel, răspunde STRICT cu: VECTOR_SEARCH";

            String raspunsDecizie = chatModel.generate(promptDecizie).trim();
            if (raspunsDecizie.contains("```sql")) raspunsDecizie = raspunsDecizie.substring(raspunsDecizie.indexOf("```sql") + 6).replace("```", "").trim();

            System.out.println("User [" + username + "] a întrebat: " + intrebareClara);
            System.out.println("Decizie ruta: " + raspunsDecizie);

            if (raspunsDecizie.toUpperCase().startsWith("SELECT")) {
                try {
                    Statement queryStmt = conn.createStatement();
                    ResultSet rs = queryStmt.executeQuery(raspunsDecizie);
                    ResultSetMetaData meta = rs.getMetaData();
                    int colCount = meta.getColumnCount();

                    StringBuilder rezultateSql = new StringBuilder();
                    int randuri = 0;
                    while (rs.next() && randuri < 20) {
                        for (int i = 1; i <= colCount; i++) rezultateSql.append(meta.getColumnName(i)).append(": ").append(rs.getString(i)).append(" | ");
                        rezultateSql.append("\n");
                        randuri++;
                    }

                    String promptSinteza = "Întrebare: \"" + intrebareClara + "\"\n" +
                            "Rezultate SQL brute: " + (rezultateSql.isEmpty() ? "Niciun rezultat." : rezultateSql.toString()) + "\n" +
                            "Formulează un răspuns politicos.";
                    memory.add(UserMessage.from(promptSinteza));
                    AiMessage raspunsAI = chatModel.generate(memory.messages()).content();
                    memory.add(raspunsAI);
                    return raspunsAI.text();

                } catch (Exception sqlError) {
                    System.out.println("SQL a eșuat. Fallback Vectori...");
                }
            }

            var intrebareEmbedding = embeddingModel.embed(intrebareClara).content();
            List<EmbeddingMatch<TextSegment>> matches = store.findRelevant(intrebareEmbedding, 5);

            StringBuilder context = new StringBuilder();
            for (EmbeddingMatch<TextSegment> match : matches) context.append(match.embedded().text()).append("\n");

            String mesajVectori = "Context extras:\n" + context + "\nRăspunde la: " + intrebareClara;
            memory.add(UserMessage.from(mesajVectori));
            AiMessage raspunsAI = chatModel.generate(memory.messages()).content();
            memory.add(raspunsAI);

            return raspunsAI.text();

        } catch (Exception e) {
            e.printStackTrace();
            return "Eroare: " + e.getMessage();
        }
    }
}