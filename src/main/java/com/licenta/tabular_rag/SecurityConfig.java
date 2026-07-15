package com.licenta.tabular_rag;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.LogoutHandler;

import java.time.Duration;
import java.time.LocalDateTime;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final LoginSessionRepository loginSessionRepository;

    public SecurityConfig(LoginSessionRepository loginSessionRepository) {
        this.loginSessionRepository = loginSessionRepository;
    }

    // 1. Algoritmul militar de criptare a parolelor
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Handler care se execută la logout: închide sesiunea și calculează durata
    private LogoutHandler sessionLogoutHandler() {
        return (request, response, authentication) -> {
            if (authentication != null) {
                String username = authentication.getName();
                loginSessionRepository
                        .findFirstByUsernameAndLogoutTimeIsNullOrderByLoginTimeDesc(username)
                        .ifPresent(session -> {
                            LocalDateTime now = LocalDateTime.now();
                            session.setLogoutTime(now);
                            long seconds = Duration.between(session.getLoginTime(), now).getSeconds();
                            session.setDurationSeconds(seconds);
                            loginSessionRepository.save(session);
                            System.out.println(">>> LOGOUT: " + username + " - durata: " + seconds + " secunde");
                        });
            }
        };
    }

    // 2. Regulile de acces
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        // Rute publice
                        .requestMatchers("/login", "/register", "/login.html", "/register.html",
                                "/css/**", "/js/**", "/images/**", "/api/register",
                                "/api/reset-password", "/error").permitAll()

                        // Rute exclusiv pentru ADMIN
                        .requestMatchers("/admin", "/admin.html", "/api/admin/**").hasRole("ADMIN")
                        // Orice altceva necesită autentificare
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login") // Pagina noastră custom
                        .defaultSuccessUrl("/", true)
                        .permitAll()
                )
                .logout(logout -> logout
                        .addLogoutHandler(sessionLogoutHandler()) // Închidem sesiunea de login
                        .logoutSuccessUrl("/login?logout")
                        .permitAll());

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
                // Adăugăm și un email default ca să nu dea eroare (fiindcă coloana e setată pe NOT NULL)
                admin.setEmail("razvan@licenta.ro");
                admin.setPassword(passwordEncoder.encode("parola123")); // Aici are loc MAGIA criptării
                admin.setRole("ADMIN");
                userRepository.save(admin);
            }
        };
    }
}