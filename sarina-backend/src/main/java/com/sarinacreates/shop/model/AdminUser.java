package com.sarinacreates.shop.model;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "admin_users")
public class AdminUser {

    @Id
    private String id;

    private String email;
    private String passwordHash;

    @Column(name = "sessions", columnDefinition = "TEXT")
    private String sessionsStr = "";

    public AdminUser() {}

    public AdminUser(String id, String email, String passwordHash, List<String> sessions) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        setSessions(sessions);
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public List<String> getSessions() {
        if (sessionsStr == null || sessionsStr.isBlank()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(List.of(sessionsStr.split(",")));
    }

    public void setSessions(List<String> sessions) {
        if (sessions == null || sessions.isEmpty()) {
            this.sessionsStr = "";
        } else {
            this.sessionsStr = String.join(",", sessions);
        }
    }
}
