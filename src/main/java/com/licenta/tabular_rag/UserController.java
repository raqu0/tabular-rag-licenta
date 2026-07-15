package com.licenta.tabular_rag;

import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.Optional;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserController(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // Endpoint-ul /me - returnează doar username-ul
    @GetMapping("/me")
    public String getCurrentUser(Principal principal) {
        if (principal == null) return "Anonim";

        Optional<User> userOpt = userRepository.findByUsername(principal.getName());

        // Returnează DOAR numele, ca un simplu text
        return userOpt.map(User::getUsername).orElse("Nu a fost găsit");
    }

    // Endpoint-ul /role - returnează rolul utilizatorului curent (USER sau ADMIN)
    @GetMapping("/role")
    public String getCurrentUserRole(Principal principal) {
        if (principal == null) return "ANONIM";

        Optional<User> userOpt = userRepository.findByUsername(principal.getName());
        return userOpt.map(u -> u.getRole() != null ? u.getRole() : "USER").orElse("USER");
    }

    // Endpoint-ul pentru update
    @PostMapping("/update")
    public String updateUser(Principal principal,
                             @RequestParam(required = false) String newUsername,
                             @RequestParam(required = false) String newEmail,
                             @RequestParam(required = false) String newPassword) {

        if (principal == null) return "Eroare: Sesiune invalidă!";

        Optional<User> userOpt = userRepository.findByUsername(principal.getName());
        if (userOpt.isEmpty()) return "Eroare: Utilizatorul nu a fost găsit!";

        User user = userOpt.get();

        if (newUsername != null && !newUsername.isBlank() && !newUsername.equals(user.getUsername())) {
            if (userRepository.findByUsername(newUsername).isPresent()) {
                return "Eroare: Acest nume de utilizator este deja folosit!";
            }
            user.setUsername(newUsername.trim());
        }

        if (newEmail != null && !newEmail.isBlank() && !newEmail.equals(user.getEmail())) {
            if (userRepository.findByEmail(newEmail).isPresent()) {
                return "Eroare: Această adresă de email este deja asociată altui cont!";
            }
            user.setEmail(newEmail.trim());
        }

        if (newPassword != null && !newPassword.isBlank()) {
            user.setPassword(passwordEncoder.encode(newPassword));
        }

        userRepository.save(user);
        return "Profil actualizat cu succes!";
    }
}