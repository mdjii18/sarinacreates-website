package com.sarinacreates.shop.config;

import org.springframework.boot.jdbc.DataSourceBuilder;
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
                    } else if (host.contains("neon.tech")) {
                        // Automatically append SSL for Neon PostgreSQL hosted database
                        jdbcUrl += "?sslmode=require";
                    }
                }

                DataSourceBuilder<?> builder = DataSourceBuilder.create()
                        .driverClassName("org.postgresql.Driver")
                        .url(jdbcUrl);

                if (!username.isEmpty()) {
                    builder.username(username).password(password);
                }

                DataSource ds = builder.build();

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
            DataSource ds = DataSourceBuilder.create()
                    .driverClassName("org.postgresql.Driver")
                    .url(System.getProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/sarinacreates"))
                    .username(System.getProperty("spring.datasource.username", "postgres"))
                    .password(System.getProperty("spring.datasource.password", "postgres"))
                    .build();

            try (Connection conn = ds.getConnection()) {
                System.out.println("Successfully connected to local PostgreSQL database.");
                return ds;
            }
        } catch (Exception e) {
            System.out.println("PostgreSQL unavailable locally. Falling back to H2 in-memory database for local testing...");
        }

        // H2 embedded fallback
        return DataSourceBuilder.create()
                .driverClassName("org.h2.Driver")
                .url("jdbc:h2:mem:sarinacreates;DB_CLOSE_DELAY=-1;MODE=PostgreSQL")
                .username("sa")
                .password("")
                .build();
    }
}
