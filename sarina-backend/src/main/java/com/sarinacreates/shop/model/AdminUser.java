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

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "admin_sessions", joinColumns = @JoinColumn(name = "admin_id"))
    private List<String> sessions = new ArrayList<>();

    public AdminUser() {}

    public AdminUser(String id, String email, String passwordHash, List<String> sessions) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.sessions = sessions != null ? sessions : new ArrayList<>();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public List<String> getSessions() { return sessions; }
    public void setSessions(List<String> sessions) { this.sessions = sessions; }
}
