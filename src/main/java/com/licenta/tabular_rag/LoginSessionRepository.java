package com.licenta.tabular_rag;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LoginSessionRepository extends JpaRepository<LoginSession, Long> {
    // Ultima sesiune deschisă (fără logout) a unui user - ca să o închidem la logout
    Optional<LoginSession> findFirstByUsernameAndLogoutTimeIsNullOrderByLoginTimeDesc(String username);
    List<LoginSession> findAllByOrderByLoginTimeDesc();
}