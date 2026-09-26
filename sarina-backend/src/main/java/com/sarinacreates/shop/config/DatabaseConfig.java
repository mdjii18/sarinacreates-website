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
                String jdbcUrl;
                String username = "";
                String password = "";

                if (cleanUrl.startsWith("jdbc:postgresql://")) {
                    jdbcUrl = cleanUrl;
                } else {
                    if (cleanUrl.startsWith("postgres://")) {
                        cleanUrl = "postgresql://" + cleanUrl.substring("postgres://".length());
                    }
                    URI dbUri = new URI(cleanUrl);
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

                    jdbcUrl = "jdbc:postgresql://" + host + ":" + port + path;
                    if (query != null && !query.isBlank()) {
                        jdbcUrl += "?" + query;
                    } else if (host != null && host.contains("neon.tech")) {
                        // Automatically append SSL for Neon PostgreSQL hosted database
                        jdbcUrl += "?sslmode=require";
                    }
                }

                HikariConfig hikariConfig = new HikariConfig();
                hikariConfig.setDriverClassName("org.postgresql.Driver");
                hikariConfig.setJdbcUrl(jdbcUrl);

                if (!username.isEmpty()) {
                    hikariConfig.setUsername(username);
                    hikariConfig.setPassword(password);
                }

                // Serverless PostgreSQL (Neon.tech) Free Tier Optimizations:
                // Allows Neon DB to auto-suspend after 5 mins of inactivity to stay 100% FREE
                hikariConfig.setMinimumIdle(0);          // Allow pool to drop to 0 idle connections
                hikariConfig.setMaximumPoolSize(10);     // Max concurrent connections
                hikariConfig.setIdleTimeout(240000);     // 4 minutes (closes idle connections before Neon's 5m sleep)
                hikariConfig.setMaxLifetime(600000);     // 10 minutes max connection lifetime
                hikariConfig.setConnectionTimeout(30000);// 30s timeout (allows Neon DB ~1s cold-start wakeup time)
                hikariConfig.setKeepaliveTime(0);        // Disabled keepalive so it doesn't drain free compute hours!

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
