package com.example.sydneyinfo.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * Makes PostgreSQL persistence work with Railway (and Heroku-style) deployments
 * with zero fuss, and keeps the app bootable when no database is configured.
 *
 * <p>Railway's Postgres plugin exposes a {@code DATABASE_URL} of the form
 * {@code postgresql://user:password@host:port/dbname}. Spring Boot's datasource
 * needs a JDBC URL ({@code jdbc:postgresql://host:port/dbname}) plus separate
 * username/password. This processor detects such a URL (from {@code DATABASE_URL}
 * or {@code SPRING_DATASOURCE_URL}) and rewrites it into the three
 * {@code spring.datasource.*} properties before the context starts.
 *
 * <p>When a usable database URL is found it sets {@code app.persistence.enabled=true}.
 * When none is found it sets the flag to {@code false} and excludes the JPA/DataSource
 * auto-configuration, so local runs without a database still start normally — price
 * recording simply becomes a no-op (see {@code PriceRecorder}).
 */
public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String[] EXCLUDED_AUTOCONFIG = {
        "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
        "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration",
        "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
        "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration"
    };

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication app) {
        Map<String, Object> props = new HashMap<>();

        String jdbcUrl = firstNonBlank(
            env.getProperty("SPRING_DATASOURCE_URL"),
            env.getProperty("spring.datasource.url"));
        String username = firstNonBlank(
            env.getProperty("SPRING_DATASOURCE_USERNAME"),
            env.getProperty("spring.datasource.username"));
        String password = firstNonBlank(
            env.getProperty("SPRING_DATASOURCE_PASSWORD"),
            env.getProperty("spring.datasource.password"));

        // If an explicit JDBC URL wasn't supplied, try the Railway/Heroku DATABASE_URL.
        if (isBlank(jdbcUrl)) {
            String raw = firstNonBlank(
                env.getProperty("DATABASE_URL"),
                env.getProperty("SPRING_DATASOURCE_URL"));
            Parsed parsed = parsePostgresUrl(raw);
            if (parsed != null) {
                jdbcUrl = parsed.jdbcUrl;
                if (isBlank(username)) username = parsed.username;
                if (isBlank(password)) password = parsed.password;
            }
        }

        if (!isBlank(jdbcUrl)) {
            props.put("spring.datasource.url", jdbcUrl);
            if (!isBlank(username)) props.put("spring.datasource.username", username);
            if (!isBlank(password)) props.put("spring.datasource.password", password);
            props.put("app.persistence.enabled", "true");
        } else {
            props.put("app.persistence.enabled", "false");
            // No database configured — keep the app bootable by turning off JPA autoconfig.
            props.put("spring.autoconfigure.exclude", String.join(",", EXCLUDED_AUTOCONFIG));
        }

        env.getPropertySources().addFirst(new MapPropertySource("railwayDatabaseUrl", props));
    }

    private Parsed parsePostgresUrl(String raw) {
        if (isBlank(raw)) return null;
        String trimmed = raw.trim();
        if (trimmed.startsWith("jdbc:")) return null; // already a JDBC URL, nothing to do
        if (!trimmed.startsWith("postgres://") && !trimmed.startsWith("postgresql://")) return null;
        try {
            URI uri = new URI(trimmed);
            String user = null, pass = null;
            String userInfo = uri.getUserInfo();
            if (userInfo != null && !userInfo.isEmpty()) {
                int idx = userInfo.indexOf(':');
                if (idx >= 0) {
                    user = userInfo.substring(0, idx);
                    pass = userInfo.substring(idx + 1);
                } else {
                    user = userInfo;
                }
            }
            int port = uri.getPort() > 0 ? uri.getPort() : 5432;
            String path = uri.getPath() == null ? "" : uri.getPath();
            StringBuilder jdbc = new StringBuilder("jdbc:postgresql://")
                .append(uri.getHost()).append(':').append(port).append(path);
            if (uri.getQuery() != null && !uri.getQuery().isEmpty()) {
                jdbc.append('?').append(uri.getQuery());
            }
            return new Parsed(jdbc.toString(), user, pass);
        } catch (Exception e) {
            return null; // malformed — fall through to "no database" behaviour
        }
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (!isBlank(v)) return v;
        }
        return null;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static final class Parsed {
        final String jdbcUrl;
        final String username;
        final String password;
        Parsed(String jdbcUrl, String username, String password) {
            this.jdbcUrl = jdbcUrl;
            this.username = username;
            this.password = password;
        }
    }
}
