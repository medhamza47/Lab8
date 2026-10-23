package com.example.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MigrationExecutor {
    private static final Logger LOGGER = Logger.getLogger(MigrationExecutor.class.getName());
    private final String jdbcUrl;
    private final String username;
    private final String password;

    public MigrationExecutor(String jdbcUrl, String username, String password) {
        if (jdbcUrl == null || jdbcUrl.isBlank() || username == null || password == null) {
            throw new IllegalArgumentException("La configuration JDBC est incomplete");
        }
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
    }

    public static MigrationExecutor fromEnvironment() {
        String url = firstNonBlank(System.getProperty("hibernate.connection.url"), System.getenv("DB_URL"),
                "jdbc:mysql://localhost:3306/reservation?useSSL=false&serverTimezone=UTC");
        String user = firstNonBlank(System.getProperty("hibernate.connection.username"), System.getenv("DB_USER"),
                "root");
        String password = firstNonBlank(System.getProperty("hibernate.connection.password"),
                System.getenv("DB_PASSWORD"), "");
        return new MigrationExecutor(url, user, password);
    }

    public void executeMigration(String scriptPath) {
        List<String> statements = readStatements(scriptPath);
        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password)) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                createHistoryTable(connection);
                if (alreadyApplied(connection, scriptPath)) {
                    connection.commit();
                    LOGGER.info(() -> "Migration already applied: " + scriptPath);
                    return;
                }
                for (String sql : statements) {
                    if (alreadyPresent(connection, sql)) {
                        LOGGER.info(() -> "Migration change already exists: " + sql);
                        continue;
                    }
                    try (Statement statement = connection.createStatement()) {
                        statement.execute(sql);
                    }
                }
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO schema_migrations (version) VALUES (?)")) {
                    insert.setString(1, scriptPath);
                    insert.executeUpdate();
                }
                connection.commit();
                LOGGER.info(() -> "Migration applied: " + scriptPath);
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        } catch (SQLException exception) {
            LOGGER.log(Level.SEVERE, "Database migration failed: " + scriptPath, exception);
            throw new IllegalStateException("Database migration failed: " + scriptPath, exception);
        }
    }

    private List<String> readStatements(String scriptPath) {
        if (scriptPath == null || scriptPath.isBlank()) {
            throw new IllegalArgumentException("Le chemin du script est obligatoire");
        }
        InputStream input = getClass().getClassLoader().getResourceAsStream(scriptPath);
        if (input == null) {
            throw new IllegalArgumentException("Script de migration introuvable : " + scriptPath);
        }
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                    continue;
                }
                current.append(line).append('\n');
                int delimiter = current.indexOf(";");
                while (delimiter >= 0) {
                    String sql = current.substring(0, delimiter).trim();
                    if (!sql.isEmpty()) {
                        statements.add(sql);
                    }
                    current.delete(0, delimiter + 1);
                    delimiter = current.indexOf(";");
                }
            }
            String finalStatement = current.toString().trim();
            if (!finalStatement.isEmpty()) {
                statements.add(finalStatement);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Impossible de lire le script : " + scriptPath, exception);
        }
        if (statements.isEmpty()) {
            throw new IllegalArgumentException("Le script ne contient aucune instruction SQL : " + scriptPath);
        }
        return statements;
    }

    private static void createHistoryTable(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS schema_migrations " +
                    "(version VARCHAR(255) PRIMARY KEY, applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        }
    }

    private static boolean alreadyApplied(Connection connection, String version) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT version FROM schema_migrations WHERE version = ?")) {
            query.setString(1, version);
            try (ResultSet result = query.executeQuery()) {
                return result.next();
            }
        }
    }

    private static boolean alreadyPresent(Connection connection, String sql) throws SQLException {
        Matcher column = Pattern.compile(
                "(?i)^ALTER\\s+TABLE\\s+([\\w.]+)\\s+ADD\\s+COLUMN\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?([\\w]+)")
                .matcher(sql.trim());
        if (column.find()) {
            try (ResultSet columns = connection.getMetaData().getColumns(
                    connection.getCatalog(), null,
                    normalizeIdentifier(connection, unqualify(column.group(1))),
                    normalizeIdentifier(connection, column.group(2)))) {
                return columns.next();
            }
        }

        Matcher index = Pattern.compile(
                "(?i)^CREATE\\s+(?:UNIQUE\\s+)?INDEX\\s+([\\w]+)\\s+ON\\s+([\\w.]+)")
                .matcher(sql.trim());
        if (index.find()) {
            try (ResultSet indexes = connection.getMetaData().getIndexInfo(
                    connection.getCatalog(), null,
                    normalizeIdentifier(connection, unqualify(index.group(2))),
                    false, false)) {
                while (indexes.next()) {
                    if (index.group(1).equalsIgnoreCase(indexes.getString("INDEX_NAME"))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static String unqualify(String tableName) {
        int separator = tableName.lastIndexOf('.');
        return separator < 0 ? tableName : tableName.substring(separator + 1);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String normalizeIdentifier(Connection connection, String... identifiers) throws SQLException {
        if (connection.getMetaData().storesUpperCaseIdentifiers()) {
            return identifiers[0].toUpperCase(java.util.Locale.ROOT);
        }
        if (connection.getMetaData().storesLowerCaseIdentifiers()) {
            return identifiers[0].toLowerCase(java.util.Locale.ROOT);
        }
        return identifiers[0];
    }
}
