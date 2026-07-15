package com.licenta.tabular_rag;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class LoginListener {

    private final LoginSessionRepository loginSessionRepository;
    private final UserRepository userRepository;

    public LoginListener(LoginSessionRepository loginSessionRepository, UserRepository userRepository) {
        this.loginSessionRepository = loginSessionRepository;
        this.userRepository = userRepository;
    }

    // Se declanșează automat când un utilizator se autentifică cu succes
    @EventListener
    public void onLoginSuccess(AuthenticationSuccessEvent event) {
        String username = event.getAuthentication().getName();

        // Aflăm rolul din baza de date
        String role = userRepository.findByUsername(username)
                .map(u -> u.getRole() != null ? u.getRole() : "USER")
                .orElse("USER");

        // Creăm o nouă sesiune deschisă (fără logout deocamdată)
        LoginSession session = new LoginSession(username, role, LocalDateTime.now());
        loginSessionRepository.save(session);

        System.out.println(">>> LOGIN: " + username + " (" + role + ") la " + session.getLoginTime());
    }
}