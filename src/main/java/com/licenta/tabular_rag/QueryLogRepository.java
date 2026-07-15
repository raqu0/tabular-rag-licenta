package com.licenta.tabular_rag;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QueryLogRepository extends JpaRepository<QueryLog, Long> {
    List<QueryLog> findByUser(User user);
    long countByUser(User user);
}