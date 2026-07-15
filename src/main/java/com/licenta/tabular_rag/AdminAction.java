package com.licenta.tabular_rag;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "admin_actions")
public class AdminAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Cine a făcut acțiunea (username-ul adminului)
    private String adminUsername;

    // Ce a făcut (descriere text)
    @Column(length = 500)
    private String action;

    private LocalDateTime createdAt;

    public AdminAction() {
    }

    public AdminAction(String adminUsername, String action) {
        this.adminUsername = adminUsername;
        this.action = action;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getAdminUsername() { return adminUsername; }
    public void setAdminUsername(String adminUsername) { this.adminUsername = adminUsername; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}