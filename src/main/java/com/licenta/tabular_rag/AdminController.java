package com.licenta.tabular_rag;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.io.File;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final UserRepository userRepository;
    private final UserDocumentRepository documentRepository;
    private final QueryLogRepository queryLogRepository;
    private final AdminActionRepository adminActionRepository;
    private final LoginSessionRepository loginSessionRepository;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    @Autowired
    public AdminController(UserRepository userRepository,
                           UserDocumentRepository documentRepository,
                           QueryLogRepository queryLogRepository,
                           AdminActionRepository adminActionRepository,
                           LoginSessionRepository loginSessionRepository) {
        this.userRepository = userRepository;
        this.documentRepository = documentRepository;
        this.queryLogRepository = queryLogRepository;
        this.adminActionRepository = adminActionRepository;
        this.loginSessionRepository = loginSessionRepository;
    }

    /**
     * Lista tuturor utilizatorilor cu detalii + statistici per user (fișiere, interogări).
     */
    @GetMapping("/users")
    public ResponseEntity<List<Map<String, Object>>> getAllUsers() {
        List<Map<String, Object>> result = new ArrayList<>();

        for (User u : userRepository.findAll()) {
            Map<String, Object> userData = new HashMap<>();
            userData.put("id", u.getId());
            userData.put("username", u.getUsername());
            userData.put("email", u.getEmail());
            userData.put("role", u.getRole());
            userData.put("nrFisiere", documentRepository.findByUser(u).size());
            userData.put("nrInterogari", queryLogRepository.countByUser(u));
            result.add(userData);
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Lista tuturor fișierelor CSV încărcate de toți utilizatorii.
     */
    @GetMapping("/files")
    public ResponseEntity<List<Map<String, Object>>> getAllFiles() {
        List<Map<String, Object>> result = new ArrayList<>();

        for (UserDocument doc : documentRepository.findAll()) {
            Map<String, Object> fileData = new HashMap<>();
            fileData.put("id", doc.getId());
            fileData.put("fileName", doc.getFileName());
            fileData.put("filePath", doc.getFilePath());
            fileData.put("owner", doc.getUser() != null ? doc.getUser().getUsername() : "necunoscut");
            result.add(fileData);
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Statistici globale: nr useri, nr fișiere, nr total interogări.
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getGlobalStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalUseri", userRepository.count());
        stats.put("totalFisiere", documentRepository.count());
        stats.put("totalInterogari", queryLogRepository.count());
        return ResponseEntity.ok(stats);
    }

    /**
     * Istoricul tuturor întrebărilor puse de toți utilizatorii.
     */
    @GetMapping("/queries")
    public ResponseEntity<List<Map<String, Object>>> getAllQueries() {
        List<Map<String, Object>> result = new ArrayList<>();

        // Sortăm descrescător după dată (cele mai noi primele)
        List<QueryLog> logs = queryLogRepository.findAll();
        logs.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));

        for (QueryLog log : logs) {
            Map<String, Object> data = new HashMap<>();
            data.put("username", log.getUser() != null ? log.getUser().getUsername() : "necunoscut");
            data.put("question", log.getQuestion());
            data.put("createdAt", log.getCreatedAt() != null ? log.getCreatedAt().format(FMT) : "-");
            result.add(data);
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Istoricul acțiunilor făcute de admini (ștergeri etc).
     */
    @GetMapping("/actions")
    public ResponseEntity<List<Map<String, Object>>> getAllAdminActions() {
        List<Map<String, Object>> result = new ArrayList<>();

        List<AdminAction> actions = adminActionRepository.findAll();
        actions.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));

        for (AdminAction a : actions) {
            Map<String, Object> data = new HashMap<>();
            data.put("admin", a.getAdminUsername());
            data.put("action", a.getAction());
            data.put("createdAt", a.getCreatedAt() != null ? a.getCreatedAt().format(FMT) : "-");
            result.add(data);
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Istoricul conectărilor (login/logout + durată).
     */
    @GetMapping("/sessions")
    public ResponseEntity<List<Map<String, Object>>> getAllSessions() {
        List<Map<String, Object>> result = new ArrayList<>();

        for (LoginSession s : loginSessionRepository.findAllByOrderByLoginTimeDesc()) {
            Map<String, Object> data = new HashMap<>();
            data.put("username", s.getUsername());
            data.put("role", s.getRole());
            data.put("loginTime", s.getLoginTime() != null ? s.getLoginTime().format(FMT) : "-");
            data.put("logoutTime", s.getLogoutTime() != null ? s.getLogoutTime().format(FMT) : "Sesiune activă");
            data.put("durata", formatDuration(s.getDurationSeconds()));
            result.add(data);
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Transformă secundele într-un format citibil (ex: 1h 23m 45s).
     */
    private String formatDuration(Long seconds) {
        if (seconds == null) return "în desfășurare";
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        StringBuilder sb = new StringBuilder();
        if (h > 0) sb.append(h).append("h ");
        if (m > 0) sb.append(m).append("m ");
        sb.append(s).append("s");
        return sb.toString();
    }

    /**
     * Șterge un utilizator după ID (plus fișierele și log-urile lui).
     */
    @DeleteMapping("/users/{id}")
    public ResponseEntity<String> deleteUser(@PathVariable Long id, Principal principal) {
        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("Utilizatorul nu există.");
        }
        User user = userOpt.get();
        String deletedUsername = user.getUsername();

        // Ștergem mai întâi log-urile și documentele asociate (altfel dă eroare de cheie străină)
        for (QueryLog log : queryLogRepository.findByUser(user)) {
            queryLogRepository.delete(log);
        }
        for (UserDocument doc : documentRepository.findByUser(user)) {
            try {
                File f = new File(doc.getFilePath());
                if (f.exists()) f.delete();
            } catch (Exception ignored) {}
            documentRepository.delete(doc);
        }

        userRepository.delete(user);

        // Înregistrăm acțiunea în audit-ul de admin
        String adminName = principal != null ? principal.getName() : "necunoscut";
        adminActionRepository.save(new AdminAction(adminName, "A șters utilizatorul: " + deletedUsername));

        return ResponseEntity.ok("Utilizatorul a fost șters.");
    }

    /**
     * Șterge un fișier după ID (din DB + de pe disc).
     */
    @DeleteMapping("/files/{id}")
    public ResponseEntity<String> deleteFile(@PathVariable Long id, Principal principal) {
        Optional<UserDocument> docOpt = documentRepository.findById(id);
        if (docOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("Fișierul nu există.");
        }
        UserDocument doc = docOpt.get();
        String deletedFile = doc.getFileName();
        String owner = doc.getUser() != null ? doc.getUser().getUsername() : "necunoscut";

        try {
            File f = new File(doc.getFilePath());
            if (f.exists()) f.delete();
        } catch (Exception ignored) {}

        documentRepository.delete(doc);

        // Înregistrăm acțiunea în audit-ul de admin
        String adminName = principal != null ? principal.getName() : "necunoscut";
        adminActionRepository.save(new AdminAction(adminName, "A șters fișierul: " + deletedFile + " (proprietar: " + owner + ")"));

        return ResponseEntity.ok("Fișierul a fost șters.");
    }
}