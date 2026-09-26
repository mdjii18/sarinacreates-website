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

                    if (host != null) {
                        jdbcUrl = "jdbc:postgresql://" + host + ":" + port + path;
                        if (query != null && !query.isBlank()) {
                            jdbcUrl += "?" + query;
                        } else if (host.contains("neon.tech")) {
                            jdbcUrl += "?sslmode=require";
                        }
                    } else {
                        jdbcUrl = cleanUrl.startsWith("jdbc:") ? cleanUrl : "jdbc:" + cleanUrl;
                    }
                } catch (Exception e) {
                    jdbcUrl = cleanUrl.startsWith("jdbc:") ? cleanUrl : "jdbc:" + cleanUrl;
                }

                HikariConfig hikariConfig = new HikariConfig();
                hikariConfig.setDriverClassName("org.postgresql.Driver");
                hikariConfig.setJdbcUrl(jdbcUrl);

                if (username != null && !username.isEmpty()) {
                    hikariConfig.setUsername(username);
                    hikariConfig.setPassword(password);
                }

                // Serverless PostgreSQL (Neon.tech) Optimized Pooling:
                hikariConfig.setMinimumIdle(1);          // Keep 1 active connection ready
                hikariConfig.setMaximumPoolSize(10);     // Max concurrent connections
                hikariConfig.setIdleTimeout(300000);     // 5 minutes idle timeout
                hikariConfig.setMaxLifetime(600000);     // 10 minutes max connection lifetime
                hikariConfig.setConnectionTimeout(15000);// 15s connection timeout (never hangs for 4 minutes!)
                hikariConfig.setValidationTimeout(5000); // 5s validation query timeout
                hikariConfig.setKeepaliveTime(45000);    // 45s keepalive ping

                HikariDataSource ds = new HikariDataSource(hikariConfig);

                // Test connection
                try (Connection conn = ds.getConnection()) {
                    System.out.println("Successfully connected to PostgreSQL database via DATABASE_URL.");
                    return ds;
                }
            } catch (Exception e) {
                System.err.println("DATABASE_URL connection test failed: " + e.getMessage());
            }
        }

        // Try local PostgreSQL configuration
        try {
            HikariConfig localConfig = new HikariConfig();
            localConfig.setDriverClassName("org.postgresql.Driver");
            localConfig.setJdbcUrl(System.getProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/sarinacreates"));
            localConfig.setUsername(System.getProperty("spring.datasource.username", "postgres"));
            localConfig.setPassword(System.getProperty("spring.datasource.password", "postgres"));
            localConfig.setConnectionTimeout(5000);
            HikariDataSource ds = new HikariDataSource(localConfig);

            try (Connection conn = ds.getConnection()) {
                System.out.println("Successfully connected to local PostgreSQL database.");
                return ds;
            }
        } catch (Exception e) {
            System.out.println("PostgreSQL unavailable locally. Falling back to H2 in-memory database for local testing...");
        }

        // H2 embedded fallback
        HikariConfig h2Config = new HikariConfig();
        h2Config.setDriverClassName("org.h2.Driver");
        h2Config.setJdbcUrl("jdbc:h2:mem:sarinacreates;DB_CLOSE_DELAY=-1;MODE=PostgreSQL");
        h2Config.setUsername("sa");
        h2Config.setPassword("");
        return new HikariDataSource(h2Config);
    }
}
