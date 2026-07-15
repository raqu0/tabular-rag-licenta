package com.licenta.tabular_rag;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class AiService {

    private final CsvService csvService;

    private final ChatLanguageModel qwenCoderModel;
    private final ChatLanguageModel llamaModel;

    // Reținem ultimul rezultat structurat (lista de rânduri brute) pentru export Excel/PDF
    private List<Map<String, Object>> lastQueryResultList = List.of();

    // Reținem SQL-ul final care a funcționat (după eventualele corectări), pentru afișare
    private String lastWorkingSql = "";

    // Numărul maxim de încercări de auto-corectare
    private static final int MAX_RETRIES = 3;

    @Autowired
    public AiService(CsvService csvService) {
        this.csvService = csvService;

        this.qwenCoderModel = OllamaChatModel.builder()
                .baseUrl("http://localhost:11434")
                .modelName("qwen2.5-coder:7b")
                .temperature(0.0)
                .build();

        this.llamaModel = OllamaChatModel.builder()
                .baseUrl("http://localhost:11434")
                .modelName("llama3")
                .temperature(0.1)
                .build();
    }

    /**
     * VERIFICARE LA ÎNCEPUT: stabilește dacă întrebarea poate primi răspuns
     * pe baza coloanelor disponibile în CSV. Returnează true dacă se poate, false dacă nu.
     */
    public boolean isQuestionAnswerable(String question, String csvFilePath) {
        String schemaContext = csvService.extractCsvSchemaForLLM(csvFilePath);

        String checkPrompt = """
                Ești un sistem de validare pentru interogări pe date tabulare.
                Sarcina ta: stabilește dacă întrebarea utilizatorului POATE primi răspuns folosind STRICT coloanele disponibile mai jos.
                
                REGULI:
                1. Răspunde cu UN SINGUR cuvânt: DA sau NU.
                2. Răspunde DA dacă informația cerută există în coloanele disponibile.
                3. Răspunde DA și pentru întrebări generale despre rânduri/înregistrări (ex: "primii 5 studenți", "câte înregistrări sunt"), deoarece acestea se pot rezolva folosind coloanele existente.
                4. Răspunde NU DOAR dacă întrebarea cere clar informații care nu se regăsesc în nicio coloană (ex: dacă se cer mașini, dar nu există nicio coloană despre mașini).
                5. Nu inventa coloane. Bazează-te exclusiv pe lista de coloane.
                
                COLOANE DISPONIBILE:
                """ + schemaContext + "\n\nÎntrebare utilizator: " + question + "\n\nRăspuns (DA sau NU):";

        String response = qwenCoderModel.generate(checkPrompt).trim().toUpperCase();
        System.out.println(">>> VERIFICARE RELEVANȚĂ: " + response);

        // Luăm doar caracterele alfabetice ca să evităm interpretări greșite.
        // Considerăm întrebarea ca NEvalidă DOAR dacă răspunsul începe clar cu NU.
        String cleaned = response.replaceAll("[^A-Z ]", "").trim();
        if (cleaned.startsWith("NU")) {
            return false;
        }
        // În orice alt caz (DA, sau răspuns ambiguu), lăsăm întrebarea să treacă mai departe
        return true;
    }

    public String generateSql(String question, String csvFilePath) {
        String schemaContext = csvService.extractCsvSchemaForLLM(csvFilePath);

        String fullPromptText = """
                Ești un expert în baze de date și interogări SQL. Sarcina ta este să generezi o singură interogare SQL validă (sintaxă standard ANSI/H2) bazată strict pe întrebarea utilizatorului și structura tabelului furnizat mai jos.
                
                REGULI OBLIGATORII:
                1. Returnează DOAR codul SQL pur, ca text simplu.
                2. NU pune text explicativ, nu pune introduceri și NU folosi blocuri de markdown (fără ```sql sau ```).
                3. Numele tabelului pe care îl folosești în interogare TREBUIE să fie cuvântul generic: DATE_TABEL.
                4. Potrivește cu atenție denumirile coloanelor furnizate în context.
                
                IMPORTANT - TIPURI DE DATE: Toate coloanele citite din CSV sunt de tip text (VARCHAR). Pentru ORICE operație numerică (AVG, SUM, MIN, MAX, comparații >, <, ordonare numerică) TREBUIE să convertești coloana cu CAST. Exemplu: folosește AVG(CAST(nume_coloana AS DOUBLE)) în loc de AVG(nume_coloana), și CAST(nume_coloana AS DOUBLE) > 5 în loc de nume_coloana > 5.
                CONTEXT STRUCTURĂ:
                """ + schemaContext + "\n\nÎntrebare utilizator: " + question + "\nSQL:";

        return qwenCoderModel.generate(fullPromptText).trim();
    }

    /**
     * Generează SQL cu JOIN pentru DOUĂ fișiere. Tabelele se numesc DATE_TABEL_1 și DATE_TABEL_2.
     */
    public String generateSqlTwoFiles(String question, String path1, String path2) {
        String schema1 = csvService.extractCsvSchemaForLLM(path1);
        String schema2 = csvService.extractCsvSchemaForLLM(path2);

        String fullPromptText = """
                Ești un expert în baze de date și interogări SQL (sintaxă H2). Ai la dispoziție DOUĂ tabele și trebuie să generezi o singură interogare SQL validă pentru întrebarea utilizatorului.
                
                REGULI OBLIGATORII:
                1. Returnează DOAR codul SQL pur, ca text simplu. Fără explicații, fără markdown (fără ```sql).
                2. Primul tabel se numește OBLIGATORIU: DATE_TABEL_1
                3. Al doilea tabel se numește OBLIGATORIU: DATE_TABEL_2
                4. Dacă întrebarea necesită combinarea datelor din ambele tabele, folosește un JOIN pe coloana comună (de obicei un ID prezent în ambele).
                5. Folosește alias-uri pentru tabele (ex: t1, t2) ca să eviți ambiguitatea coloanelor.
                6. Potrivește cu atenție denumirile exacte ale coloanelor (litere mari/mici contează).
                
                IMPORTANT - TIPURI DE DATE: Toate coloanele citite din CSV sunt de tip text (VARCHAR). Pentru ORICE operație numerică (AVG, SUM, MIN, MAX, comparații >, <, ordonare numerică) TREBUIE să convertești coloana cu CAST. Exemplu: folosește AVG(CAST(nume_coloana AS DOUBLE)) în loc de AVG(nume_coloana), și CAST(nume_coloana AS DOUBLE) > 5 în loc de nume_coloana > 5.
                STRUCTURĂ TABEL 1 (DATE_TABEL_1):
                """ + schema1 + """
                
                STRUCTURĂ TABEL 2 (DATE_TABEL_2):
                """ + schema2 + "\n\nÎntrebare utilizator: " + question + "\nSQL:";

        return qwenCoderModel.generate(fullPromptText).trim();
    }

    /**
     * Cere lui Qwen să corecteze un SQL care a eșuat, trimițându-i eroarea ca feedback.
     */
    public String fixSql(String question, String brokenSql, String errorMessage, String csvFilePath) {
        String schemaContext = csvService.extractCsvSchemaForLLM(csvFilePath);

        String fixPromptText = """
                Ești un expert în baze de date H2/SQL. O interogare SQL generată anterior a eșuat la execuție.
                Sarcina ta este să o corectezi, folosind mesajul de eroare și structura tabelului.
                
                REGULI OBLIGATORII:
                1. Returnează DOAR codul SQL corectat, ca text simplu.
                2. NU pune explicații, NU pune markdown (fără ```sql).
                3. Numele tabelului TREBUIE să rămână cuvântul generic: DATE_TABEL.
                4. Verifică cu ATENȚIE denumirile exacte ale coloanelor din structura de mai jos (litere mari/mici contează).
                5. Folosește doar funcții SQL compatibile cu motorul H2.
                
                IMPORTANT - TIPURI DE DATE: Toate coloanele citite din CSV sunt de tip text (VARCHAR). Pentru ORICE operație numerică (AVG, SUM, MIN, MAX, comparații >, <, ordonare numerică) TREBUIE să convertești coloana cu CAST. Exemplu: folosește AVG(CAST(nume_coloana AS DOUBLE)) în loc de AVG(nume_coloana), și CAST(nume_coloana AS DOUBLE) > 5 în loc de nume_coloana > 5.
                CONTEXT STRUCTURĂ:
                """ + schemaContext
                + "\n\nÎntrebarea originală a utilizatorului: " + question
                + "\n\nSQL-ul care a eșuat:\n" + brokenSql
                + "\n\nMesajul de eroare primit:\n" + errorMessage
                + "\n\nSQL corectat:";

        return qwenCoderModel.generate(fixPromptText).trim();
    }

    /**
     * Corectare SQL pentru DOUĂ fișiere (păstrează numele DATE_TABEL_1 și DATE_TABEL_2).
     */
    public String fixSqlTwoFiles(String question, String brokenSql, String errorMessage, String path1, String path2) {
        String schema1 = csvService.extractCsvSchemaForLLM(path1);
        String schema2 = csvService.extractCsvSchemaForLLM(path2);

        String fixPromptText = """
                Ești un expert în baze de date H2/SQL. O interogare SQL cu JOIN între două tabele a eșuat la execuție.
                Corecteaz-o folosind mesajul de eroare și structurile de mai jos.
                
                REGULI OBLIGATORII:
                1. Returnează DOAR codul SQL corectat, fără explicații, fără markdown.
                2. Primul tabel rămâne DATE_TABEL_1, al doilea rămâne DATE_TABEL_2.
                3. Verifică denumirile exacte ale coloanelor (litere mari/mici contează).
                4. Folosește doar funcții compatibile cu H2.
                
                IMPORTANT - TIPURI DE DATE: Toate coloanele citite din CSV sunt de tip text (VARCHAR). Pentru ORICE operație numerică (AVG, SUM, MIN, MAX, comparații >, <, ordonare numerică) TREBUIE să convertești coloana cu CAST. Exemplu: folosește AVG(CAST(nume_coloana AS DOUBLE)) în loc de AVG(nume_coloana), și CAST(nume_coloana AS DOUBLE) > 5 în loc de nume_coloana > 5.
                STRUCTURĂ TABEL 1 (DATE_TABEL_1):
                """ + schema1 + """
                
                STRUCTURĂ TABEL 2 (DATE_TABEL_2):
                """ + schema2
                + "\n\nÎntrebarea originală: " + question
                + "\n\nSQL-ul care a eșuat:\n" + brokenSql
                + "\n\nMesajul de eroare:\n" + errorMessage
                + "\n\nSQL corectat:";

        return qwenCoderModel.generate(fixPromptText).trim();
    }

    /**
     * Generează și execută SQL cu auto-corectare (self-healing) pentru UN fișier.
     */
    public String runQueryWithRetry(String question, String csvFilePath) {
        String currentSql = generateSql(question, csvFilePath);
        String lastError = "";

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            System.out.println(">>> ÎNCERCAREA " + attempt + " - SQL: " + currentSql);
            try {
                String formattedTable = executeSqlInternal(currentSql, csvFilePath);
                this.lastWorkingSql = cleanSql(currentSql);
                return formattedTable;
            } catch (Exception e) {
                Throwable root = e;
                while (root.getCause() != null && root.getCause() != root) {
                    root = root.getCause();
                }
                lastError = root.getClass().getSimpleName() + " - " + root.getMessage();
                System.out.println(">>> EROARE la încercarea " + attempt + ": " + lastError);

                if (attempt < MAX_RETRIES) {
                    currentSql = fixSql(question, currentSql, lastError, csvFilePath);
                }
            }
        }

        this.lastQueryResultList = List.of();
        this.lastWorkingSql = cleanSql(currentSql);
        return "Nu am reușit să generez o interogare validă după " + MAX_RETRIES + " încercări. Ultima eroare: " + lastError;
    }

    /**
     * Generează și execută SQL cu JOIN pentru DOUĂ fișiere, cu auto-corectare.
     */
    public String runQueryWithRetryTwoFiles(String question, String path1, String path2) {
        String currentSql = generateSqlTwoFiles(question, path1, path2);
        String lastError = "";

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            System.out.println(">>> [2 FIȘIERE] ÎNCERCAREA " + attempt + " - SQL: " + currentSql);
            try {
                String formattedTable = executeSqlInternalTwoFiles(currentSql, path1, path2);
                this.lastWorkingSql = cleanSql(currentSql);
                return formattedTable;
            } catch (Exception e) {
                Throwable root = e;
                while (root.getCause() != null && root.getCause() != root) {
                    root = root.getCause();
                }
                lastError = root.getClass().getSimpleName() + " - " + root.getMessage();
                System.out.println(">>> [2 FIȘIERE] EROARE la încercarea " + attempt + ": " + lastError);

                if (attempt < MAX_RETRIES) {
                    currentSql = fixSqlTwoFiles(question, currentSql, lastError, path1, path2);
                }
            }
        }

        this.lastQueryResultList = List.of();
        this.lastWorkingSql = cleanSql(currentSql);
        return "Nu am reușit să generez o interogare validă (JOIN) după " + MAX_RETRIES + " încercări. Ultima eroare: " + lastError;
    }

    /**
     * Curăță SQL-ul de markdown și punct-virgulă final.
     */
    private String cleanSql(String generatedSql) {
        String cleanSql = generatedSql.replace("```sql", "").replace("```", "").trim();
        if (cleanSql.endsWith(";")) {
            cleanSql = cleanSql.substring(0, cleanSql.length() - 1);
        }
        return cleanSql;
    }

    /**
     * Execuția propriu-zisă pentru UN fișier. Aruncă excepție dacă SQL-ul e invalid.
     */
    private String executeSqlInternal(String generatedSql, String csvFilePath) throws Exception {
        String cleanSql = cleanSql(generatedSql);

        String safePath = csvFilePath.replace("\\", "/");
        String h2ExecutionSql = cleanSql.replaceAll("(?i)DATE_TABEL", "(SELECT * FROM CSVREAD('" + safePath + "'))");

        System.out.println(">>> SQL FINAL H2: " + h2ExecutionSql);

        DriverManagerDataSource h2DataSource = new DriverManagerDataSource();
        h2DataSource.setDriverClassName("org.h2.Driver");
        h2DataSource.setUrl("jdbc:h2:mem:csvdb;DB_CLOSE_DELAY=-1");
        h2DataSource.setUsername("sa");
        h2DataSource.setPassword("");

        JdbcTemplate h2Template = new JdbcTemplate(h2DataSource);
        List<Map<String, Object>> queryResult = h2Template.queryForList(h2ExecutionSql);

        this.lastQueryResultList = queryResult;

        if (queryResult.isEmpty()) {
            return "Nu s-au găsit înregistrări care să corespundă cerinței.";
        }

        return formatTable(queryResult);
    }

    /**
     * Execuția pentru DOUĂ fișiere. Înlocuiește DATE_TABEL_1 și DATE_TABEL_2 cu CSVREAD-urile respective.
     * IMPORTANT: înlocuim întâi _2 apoi _1, ca să nu strice "DATE_TABEL_1" devenind prefix al lui "DATE_TABEL_12".
     */
    private String executeSqlInternalTwoFiles(String generatedSql, String path1, String path2) throws Exception {
        String cleanSql = cleanSql(generatedSql);

        String safePath1 = path1.replace("\\", "/");
        String safePath2 = path2.replace("\\", "/");

        String h2ExecutionSql = cleanSql
                .replaceAll("(?i)DATE_TABEL_2", "(SELECT * FROM CSVREAD('" + safePath2 + "'))")
                .replaceAll("(?i)DATE_TABEL_1", "(SELECT * FROM CSVREAD('" + safePath1 + "'))");

        System.out.println(">>> SQL FINAL H2 (JOIN): " + h2ExecutionSql);

        DriverManagerDataSource h2DataSource = new DriverManagerDataSource();
        h2DataSource.setDriverClassName("org.h2.Driver");
        h2DataSource.setUrl("jdbc:h2:mem:csvdb;DB_CLOSE_DELAY=-1");
        h2DataSource.setUsername("sa");
        h2DataSource.setPassword("");

        JdbcTemplate h2Template = new JdbcTemplate(h2DataSource);
        List<Map<String, Object>> queryResult = h2Template.queryForList(h2ExecutionSql);

        this.lastQueryResultList = queryResult;

        if (queryResult.isEmpty()) {
            return "Nu s-au găsit înregistrări care să corespundă cerinței.";
        }

        return formatTable(queryResult);
    }

    /**
     * Formatează rezultatul ca tabel text (extras ca metodă comună).
     */
    private String formatTable(List<Map<String, Object>> queryResult) {
        StringBuilder table = new StringBuilder();
        Map<String, Object> firstRow = queryResult.get(0);
        table.append(String.join(" | ", firstRow.keySet())).append("\n");
        table.append("-".repeat(40)).append("\n");
        for (Map<String, Object> row : queryResult) {
            table.append(String.join(" | ", row.values().stream()
                    .map(v -> v == null ? "N/A" : v.toString())
                    .toList())).append("\n");
        }
        table.append("-".repeat(40)).append("\n");
        table.append("Total înregistrări: ").append(queryResult.size());
        return table.toString();
    }

    public String executeSqlOnCsv(String generatedSql, String csvFilePath) {
        try {
            String cleanSql = generatedSql.replace("```sql", "").replace("```", "").trim();
            if (cleanSql.endsWith(";")) {
                cleanSql = cleanSql.substring(0, cleanSql.length() - 1);
            }

            String safePath = csvFilePath.replace("\\", "/");
            String h2ExecutionSql = cleanSql.replaceAll("(?i)DATE_TABEL", "(SELECT * FROM CSVREAD('" + safePath + "'))");

            System.out.println(">>> SQL FINAL H2: " + h2ExecutionSql);

            DriverManagerDataSource h2DataSource = new DriverManagerDataSource();
            h2DataSource.setDriverClassName("org.h2.Driver");
            h2DataSource.setUrl("jdbc:h2:mem:csvdb;DB_CLOSE_DELAY=-1");
            h2DataSource.setUsername("sa");
            h2DataSource.setPassword("");

            JdbcTemplate h2Template = new JdbcTemplate(h2DataSource);
            List<Map<String, Object>> queryResult = h2Template.queryForList(h2ExecutionSql);

            this.lastQueryResultList = queryResult;

            if (queryResult.isEmpty()) {
                return "Nu s-au găsit înregistrări care să corespundă cerinței.";
            }

            return formatTable(queryResult);

        } catch (Exception e) {
            e.printStackTrace();
            this.lastQueryResultList = List.of();
            return "Eroare la execuția interogării SQL pe fișier: " + e.getClass().getSimpleName() + " - " + e.getMessage();
        }
    }

    public String generateFinalAnswer(String question, String sqlResult, String formattedTable) {
        String fullPromptText = """
            INSTRUCȚIUNE CRITICĂ: Răspunde EXCLUSIV în limba română. Nicio propoziție în engleză.
            
            Ești un Analist de Date Senior. Datele complete au fost deja extrase și afișate utilizatorului într-un tabel.
            Sarcina ta este să scrii DOAR o concluzie generală de 1-2 propoziții despre ce reprezintă aceste rezultate.
            
            REGULĂ DE ANCORARE (FOARTE IMPORTANT):
            - Bazează-te EXCLUSIV pe denumirile coloanelor și valorile din datele furnizate.
            - NU inventa subiecte care nu apar în date. Dacă datele conțin nume de instrumente, vorbește despre instrumente, NU despre alte lucruri.
            - NU presupune nimic dincolo de ce arată coloanele. Dacă întrebarea cere ceva ce nu apare în coloane, spune clar că datele nu conțin acea informație.
            
            INTERDICȚII ABSOLUTE:
            - NU menționa niciun ID, nicio vârstă, nicio valoare numerică concretă din date.
            - NU enumera înregistrările.
            - NU repeta informațiile din tabel.
            - Vorbește doar la modul general despre ce înseamnă rezultatele.
            - Răspunde DOAR în limba română.
            """ + "\n\nÎntrebarea utilizatorului: " + question
                + "\n\nDenumirile coloanelor și datele extrase:\n" + formattedTable
                + "\n\nConcluzie generală în română (ancorată strict în date, fără valori concrete):";

        return llamaModel.generate(fullPromptText).trim();
    }

    public List<Map<String, Object>> getLastQueryResultList() {
        return this.lastQueryResultList;
    }

    public String getLastWorkingSql() {
        return this.lastWorkingSql;
    }
}