package com.licenta.tabular_rag;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

@Controller
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder, EmailService emailService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    @GetMapping("/login")
    public String loginPage() { return "forward:/login.html"; }

    @GetMapping("/register")
    public String registerPage() { return "forward:/register.html"; }

    @PostMapping("/api/register")
    @ResponseBody
    public String registerUser(@RequestParam String username, @RequestParam String email, @RequestParam String password) {
        if (userRepository.findByUsername(username).isPresent()) {
            return "Eroare: Numele de utilizator este deja folosit!";
        }
        if (userRepository.findByEmail(email).isPresent()) {
            return "Eroare: Există deja un cont cu acest email!";
        }

        User newUser = new User();
        newUser.setUsername(username);
        newUser.setEmail(email);
        newUser.setPassword(passwordEncoder.encode(password));
        newUser.setRole("USER");
        userRepository.save(newUser);
        return "Succes";
    }

    @PostMapping("/api/reset-password")
    @ResponseBody
    public String resetPassword(@RequestParam String email) {
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            return "Eroare: Nu am găsit niciun cont asociat acestui email.";
        }

        User user = userOpt.get();
        // Generăm o parolă scurtă aleatorie (primele 8 caractere dintr-un UUID)
        String tempPassword = UUID.randomUUID().toString().substring(0, 8);

        user.setPassword(passwordEncoder.encode(tempPassword));
        userRepository.save(user); // Salvăm noua parolă în baza de date

        try {
            emailService.sendPasswordRecoveryEmail(user.getEmail(), tempPassword);
            return "Succes";
        } catch (Exception e) {
            e.printStackTrace();
            return "Eroare la trimiterea emailului. Te rugăm să încerci mai târziu.";
        }
    }
}