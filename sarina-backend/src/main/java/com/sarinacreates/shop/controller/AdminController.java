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
        return adminRepository.findBySessionsContaining(token);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String password = body != null ? body.get("password") : null;
        Optional<AdminUser> adminOpt = adminRepository.findById("admin");
        if (adminOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Incorrect password"));
        }

        AdminUser admin = adminOpt.get();
        if (password == null || !SecurityUtils.sha256(password).equalsIgnoreCase(admin.getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Incorrect password"));
        }

        String token = SecurityUtils.generateToken();
        admin.getSessions().add(token);
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
        admin.getSessions().remove(token);
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
