package com.example.imagetagger.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);

    // Используем System.getProperty("user.home") для создания папки в домашней директории пользователя
    // Это более кроссплатформенно, чем %APPDATA% или ~/.ImageTagger напрямую
    private static final String APP_DATA_FOLDER_NAME = ".ImageTagger"; // С точкой для скрытия в Unix-like системах
    private static final String DB_FILE_NAME = "image_tagger_data.sqlite";

    private static String getDbFolderPath() {
        String homeDir = System.getProperty("user.home");
        return homeDir + File.separator + APP_DATA_FOLDER_NAME;
    }

    private static String getDbUrl() {
        return "jdbc:sqlite:" + getDbFolderPath() + File.separator + DB_FILE_NAME;
    }


    public static Connection getConnection() throws SQLException {
        File dbFolder = new File(getDbFolderPath());
        if (!dbFolder.exists()) {
            if (dbFolder.mkdirs()) {
                logger.info("Application data folder created: {}", dbFolder.getAbsolutePath());
            } else {
                logger.error("Failed to create application data folder: {}", dbFolder.getAbsolutePath());
                // Можно выбросить исключение или обработать ошибку иначе
            }
        }
        logger.debug("Attempting to connect to database at: {}", getDbUrl());
        return DriverManager.getConnection(getDbUrl());
    }

    public static void initializeDatabase() {
        String createTagsTable = "CREATE TABLE IF NOT EXISTS tags ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "name TEXT UNIQUE NOT NULL"
                + ");";

        String createTrackedFilesTable = "CREATE TABLE IF NOT EXISTS tracked_files ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "absolute_path TEXT UNIQUE NOT NULL,"
                + "content_hash TEXT NOT NULL,"
                + "size_bytes INTEGER NOT NULL,"
                + "modified_date INTEGER NOT NULL," // Храним как Unix timestamp (long)
                + "last_seen_date INTEGER NOT NULL" // Храним как Unix timestamp (long)
                + ");";

        String createFileTagLinksTable = "CREATE TABLE IF NOT EXISTS file_tag_links ("
                + "file_id INTEGER NOT NULL,"
                + "tag_id INTEGER NOT NULL,"
                + "PRIMARY KEY (file_id, tag_id),"
                + "FOREIGN KEY (file_id) REFERENCES tracked_files(id) ON DELETE CASCADE,"
                + "FOREIGN KEY (tag_id) REFERENCES tags(id) ON DELETE CASCADE"
                + ");";

        String createIndexPathOnTrackedFiles = "CREATE INDEX IF NOT EXISTS idx_tracked_files_path ON tracked_files (absolute_path);";
        String createIndexHashOnTrackedFiles = "CREATE INDEX IF NOT EXISTS idx_tracked_files_hash ON tracked_files (content_hash);";
        String createIndexTagName = "CREATE INDEX IF NOT EXISTS idx_tags_name ON tags (name);";


        try (Connection conn = getConnection(); // getConnection() теперь создает папку, если нужно
             Statement stmt = conn.createStatement()) {
            logger.info("Initializing database schema...");
            stmt.execute(createTagsTable);
            logger.debug("Table 'tags' ensured.");
            stmt.execute(createTrackedFilesTable);
            logger.debug("Table 'tracked_files' ensured.");
            stmt.execute(createFileTagLinksTable);
            logger.debug("Table 'file_tag_links' ensured.");
            stmt.execute(createIndexPathOnTrackedFiles);
            logger.debug("Index 'idx_tracked_files_path' ensured.");
            stmt.execute(createIndexHashOnTrackedFiles);
            logger.debug("Index 'idx_tracked_files_hash' ensured.");
            stmt.execute(createIndexTagName);
            logger.debug("Index 'idx_tags_name' ensured.");
            logger.info("Database schema initialization complete.");
        } catch (SQLException e) {
            logger.error("Failed to initialize database schema.", e);
            // Перебрасываем как RuntimeException, т.к. без БД приложение не может работать корректно
            throw new RuntimeException("Failed to initialize database schema", e);
        }
    }
}