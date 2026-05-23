package com.licenta.tabular_rag;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // 1. Algoritmul militar de criptare a parolelor
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // 2. Regulile de acces
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // Oprim CSRF temporar ca să funcționeze upload-ul de fișiere
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/css/**", "/js/**").permitAll() // CSS-ul și JS-ul pot fi descărcate fără parolă
                        .anyRequest().authenticated() // ABSOLUT orice altceva cere parolă
                )
                .formLogin(form -> form
                        .defaultSuccessUrl("/", true) // După un login cu succes, te duce direct pe platforma de RAG
                        .permitAll()
                )
                .logout(logout -> logout.permitAll());

        return http.build();
    }

    // 3. TRUCUL DE LICENȚĂ: Populăm baza de date cu primul nostru user criptat automat
    @Bean
    public CommandLineRunner initData(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            // Ștergem userul cu parola greșită (necriptată) dacă a rămas prin baza de date
            userRepository.findByUsername("razvan").ifPresent(u -> {
                if (!u.getPassword().startsWith("$2a$")) {
                    userRepository.delete(u);
                }
            });

            // Creăm userul corect dacă nu există deja
            if (userRepository.findByUsername("razvan").isEmpty()) {
                User admin = new User();
                admin.setUsername("razvan");
                admin.setPassword(passwordEncoder.encode("parola123")); // Aici are loc MAGIA criptării
                admin.setRole("ADMIN");
                userRepository.save(admin);
            }
        };
    }
}