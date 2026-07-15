package com.licenta.tabular_rag;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/upload")
public class UploadController {

    private final UserRepository userRepository;
    private final UserDocumentRepository documentRepository;

    @Autowired
    public UploadController(UserRepository userRepository, UserDocumentRepository documentRepository) {
        this.userRepository = userRepository;
        this.documentRepository = documentRepository;
    }

    @PostMapping("/{username}")
    public ResponseEntity<?> uploadCsv(
            @PathVariable String username,
            @RequestParam("file") MultipartFile file) {

        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("Eroare: Utilizatorul nu a fost găsit.");
        }
        User user = userOpt.get();

        String originalName = file.getOriginalFilename();

        try {
            Path userDir = Paths.get("uploads", username);
            Files.createDirectories(userDir);

            Path filePath = userDir.resolve(originalName);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            // Dacă userul a mai uploadat același fișier, îl ștergem din DB
            documentRepository.findByUser(user).stream()
                    .filter(doc -> doc.getFileName().equals(originalName))
                    .findFirst()
                    .ifPresent(documentRepository::delete);

            UserDocument doc = new UserDocument(originalName, filePath.toAbsolutePath().toString(), user);
            documentRepository.save(doc);

            // Returnăm ID-ul și numele fișierului ca JSON, pentru a-l reține în sesiunea din frontend
            Map<String, Object> response = new HashMap<>();
            response.put("id", doc.getId());
            response.put("fileName", originalName);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Eroare: " + e.getMessage());
        }
    }

    // Listează toate fișierele utilizatorului (id + nume)
    @GetMapping("/files/{username}")
    public ResponseEntity<List<Map<String, Object>>> listFiles(@PathVariable String username) {
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        User user = userOpt.get();

        List<Map<String, Object>> result = new ArrayList<>();
        for (UserDocument doc : documentRepository.findByUser(user)) {
            Map<String, Object> fileData = new HashMap<>();
            fileData.put("id", doc.getId());
            fileData.put("fileName", doc.getFileName());
            result.add(fileData);
        }

        return ResponseEntity.ok(result);
    }
}