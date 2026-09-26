package com.sarinacreates.shop.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.net.URI;
import java.sql.Connection;

@Configuration
public class DatabaseConfig {

    @Bean
    public DataSource dataSource() {
        String databaseUrl = System.getenv("DATABASE_URL");
        String internalUrl = System.getenv("INTERNAL_DATABASE_URL");
        String targetUrl = databaseUrl != null && !databaseUrl.isBlank() ? databaseUrl : internalUrl;

        if (targetUrl != null && !targetUrl.isBlank()) {
            try {
                String cleanUrl = targetUrl.trim();
                String parseUrl = cleanUrl;
                if (parseUrl.startsWith("jdbc:")) {
                    parseUrl = parseUrl.substring("jdbc:".length());
                }
                if (parseUrl.startsWith("postgres://")) {
                    parseUrl = "postgresql://" + parseUrl.substring("postgres://".length());
                }

                String jdbcUrl;
                String username = "";
                String password = "";

                try {
                    URI dbUri = new URI(parseUrl);
                    String userInfo = dbUri.getUserInfo();

                    if (userInfo != null && userInfo.contains(":")) {
                        String[] parts = userInfo.split(":", 2);
                        username = parts[0];
                        password = parts[1];
                    }

                    String host = dbUri.getHost();
                    int port = dbUri.getPort() == -1 ? 5432 : dbUri.getPort();
                    String path = dbUri.getPath();
                    String query = dbUri.getQuery();

                    // Parse user and password from query parameters if not present in userInfo
                    if (query != null && !query.isBlank()) {
                        for (String param : query.split("&")) {
                            String[] kv = param.split("=", 2);
                            if (kv.length == 2) {
                                String key = kv[0].trim();
                                String val = kv[1].trim();
                                if ((key.equalsIgnoreCase("user") || key.equalsIgnoreCase("username")) && username.isEmpty()) {
                                    username = val;
                                } else if (key.equalsIgnoreCase("password") && password.isEmpty()) {
                                    password = val;
                                }
                            }
                        }
                    }

                    if (host != null) {
                        jdbcUrl = "jdbc:postgresql://" + host + ":" + port + path;
                        if (query != null && !query.isBlank()) {
                            // Strip channelBinding / channel_binding from query string for PgBouncer compatibility
                            String cleanQuery = query.replaceAll("(?i)&?channelBinding=[^&]*", "")
                                                     .replaceAll("(?i)&?channel_binding=[^&]*", "");
                            if (cleanQuery.startsWith("&")) {
                                cleanQuery = cleanQuery.substring(1);
                            }
                            if (!cleanQuery.contains("sslmode") && !cleanQuery.contains("ssl")) {
                                jdbcUrl += "?" + cleanQuery + (cleanQuery.isBlank() ? "sslmode=require" : "&sslmode=require");
                            } else {
                                jdbcUrl += (cleanQuery.isBlank() ? "" : "?" + cleanQuery);
                            }
                        } else {
                            jdbcUrl += "?sslmode=require";
                        }
                    } else {
                        jdbcUrl = cleanUrl.startsWith("jdbc:") ? cleanUrl : "jdbc:" + cleanUrl;
                    }
                } catch (Exception e) {
                    jdbcUrl = cleanUrl.startsWith("jdbc:") ? cleanUrl : "jdbc:" + cleanUrl;
                }

                // Strip channelBinding if present in raw string fallback
                jdbcUrl = jdbcUrl.replaceAll("(?i)&?channelBinding=[^&]*", "")
                                 .replaceAll("(?i)&?channel_binding=[^&]*", "");

                HikariConfig hikariConfig = new HikariConfig();
                hikariConfig.setDriverClassName("org.postgresql.Driver");
                hikariConfig.setJdbcUrl(jdbcUrl);

                if (username != null && !username.isEmpty()) {
                    hikariConfig.setUsername(username);
                    hikariConfig.setPassword(password);
                }

                // Serverless PostgreSQL (Neon.tech) Optimized Pooling
                hikariConfig.setMinimumIdle(1);          // Keep 1 active connection ready
                hikariConfig.setMaximumPoolSize(10);     // Max concurrent connections
                hikariConfig.setIdleTimeout(300000);     // 5 minutes idle timeout
                hikariConfig.setMaxLifetime(600000);     // 10 minutes max connection lifetime
                hikariConfig.setConnectionTimeout(30000);// 30s connection timeout
                hikariConfig.setValidationTimeout(5000); // 5s validation query timeout
                hikariConfig.setKeepaliveTime(45000);    // 45s keepalive ping

                String safeUrl = jdbcUrl.replaceAll(":[^/@]+@", ":***@").replaceAll("password=[^&]*", "password=***");
                System.out.println("=== CONFIGURING POSTGRESQL DATA SOURCE ===");
                System.out.println("JDBC URL: " + safeUrl);
                System.out.println("DB User : " + username);

                HikariDataSource ds = new HikariDataSource(hikariConfig);

                // Quick diagnostics probe to log exact exception on startup
                try (Connection conn = ds.getConnection()) {
                    System.out.println("=== POSTGRESQL CONNECTION SUCCESSFUL! ===");
                } catch (Exception diagEx) {
                    System.err.println("=== POSTGRESQL CONNECTION DIAGNOSTIC ERROR ===");
                    diagEx.printStackTrace();
                }

                return ds;
            } catch (Exception e) {
                System.err.println("Failed to parse DATABASE_URL: " + e.getMessage());
            }
        }

        System.out.println("No valid DATABASE_URL environment variable set. Initializing H2 in-memory database...");
        HikariConfig h2Config = new HikariConfig();
        h2Config.setDriverClassName("org.h2.Driver");
        h2Config.setJdbcUrl("jdbc:h2:mem:sarinacreates;DB_CLOSE_DELAY=-1;MODE=PostgreSQL");
        h2Config.setUsername("sa");
        h2Config.setPassword("");
        return new HikariDataSource(h2Config);
    }
}
