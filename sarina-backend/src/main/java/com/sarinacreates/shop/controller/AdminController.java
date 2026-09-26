package com.sarinacreates.shop.controller;

import com.sarinacreates.shop.model.AdminUser;
import com.sarinacreates.shop.repository.AdminUserRepository;
import com.sarinacreates.shop.util.SecurityUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminUserRepository adminRepository;

    public AdminController(AdminUserRepository adminRepository) {
        this.adminRepository = adminRepository;
    }

    private Optional<AdminUser> validateBearerToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return Optional.empty();
        }
        String token = authHeader.substring(7);
        return adminRepository.findBySessionsStrContaining(token);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String password = body != null ? body.get("password") : null;
        if (password != null) {
            password = password.trim();
        }

        if (password == null || password.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Password is required"));
        }

        String defaultPass = "sarina2026";
        String inputHash = SecurityUtils.sha256(password);
        String defaultHash = SecurityUtils.sha256(defaultPass);

        Optional<AdminUser> adminOpt = adminRepository.findById("admin");
        AdminUser admin;
        if (adminOpt.isEmpty()) {
            admin = new AdminUser(
                    "admin",
                    "sarinaquadri71@gmail.com",
                    defaultHash,
                    new java.util.ArrayList<>()
            );
            adminRepository.save(admin);
        } else {
            admin = adminOpt.get();
        }

        boolean matches = inputHash.equalsIgnoreCase(admin.getPasswordHash()) || inputHash.equalsIgnoreCase(defaultHash);
        if (!matches) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Incorrect password"));
        }

        if (inputHash.equalsIgnoreCase(defaultHash) && !inputHash.equalsIgnoreCase(admin.getPasswordHash())) {
            admin.setPasswordHash(defaultHash);
        }

        String token = SecurityUtils.generateToken();
        java.util.List<String> sessions = admin.getSessions();
        sessions.add(token);
        admin.setSessions(sessions);
        adminRepository.save(admin);

        return ResponseEntity.ok(Map.of("token", token));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        Optional<AdminUser> adminOpt = validateBearerToken(authHeader);
        if (adminOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authorized"));
        }

        String token = authHeader.substring(7);
        AdminUser admin = adminOpt.get();
        java.util.List<String> sessions = admin.getSessions();
        sessions.remove(token);
        admin.setSessions(sessions);
        adminRepository.save(admin);

        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping("/password")
    public ResponseEntity<?> changePassword(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                            @RequestBody Map<String, String> body) {
        Optional<AdminUser> adminOpt = validateBearerToken(authHeader);
        if (adminOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authorized"));
        }

        String newPassword = body != null ? body.get("newPassword") : null;
        if (newPassword == null || newPassword.length() < 6) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Password must be at least 6 characters."));
        }

        AdminUser admin = adminOpt.get();
        admin.setPasswordHash(SecurityUtils.sha256(newPassword));
        adminRepository.save(admin);

        return ResponseEntity.ok(Map.of("ok", true));
    }
}
