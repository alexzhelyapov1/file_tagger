package com.example.imagetagger.persistence.dao;

import com.example.imagetagger.core.model.Tag;
import com.example.imagetagger.core.model.TrackedFile;
import com.example.imagetagger.persistence.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

public class FileTagLinkDAO {
    private static final Logger logger = LoggerFactory.getLogger(FileTagLinkDAO.class);
    private final TagDAO tagDAO; // Нужен для получения объектов Tag по ID

    public FileTagLinkDAO() {
        this.tagDAO = new TagDAO(); // Или инъекция зависимости
    }
    
    public FileTagLinkDAO(TagDAO tagDAO) {
        this.tagDAO = tagDAO;
    }


    public boolean linkTagToFile(long fileId, long tagId) {
        String sql = "INSERT OR IGNORE INTO file_tag_links(file_id, tag_id) VALUES(?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, fileId);
            pstmt.setLong(2, tagId);
            int affectedRows = pstmt.executeUpdate();
            if (affectedRows > 0) {
                logger.info("Linked fileId {} to tagId {}", fileId, tagId);
                return true;
            } else {
                // Это может произойти, если связь уже существует (из-за INSERT OR IGNORE)
                logger.debug("Link between fileId {} and tagId {} already exists or failed.", fileId, tagId);
                return false; // или true, если "уже существует" считается успехом
            }
        } catch (SQLException e) {
            logger.error("Error linking fileId {} to tagId {}", fileId, tagId, e);
            return false;
        }
    }

    public boolean unlinkTagFromFile(long fileId, long tagId) {
        String sql = "DELETE FROM file_tag_links WHERE file_id = ? AND tag_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, fileId);
            pstmt.setLong(2, tagId);
            int affectedRows = pstmt.executeUpdate();
            if (affectedRows > 0) {
                logger.info("Unlinked fileId {} from tagId {}", fileId, tagId);
                return true;
            }
        } catch (SQLException e) {
            logger.error("Error unlinking fileId {} from tagId {}", fileId, tagId, e);
        }
        return false;
    }

    public Set<Tag> getTagsForFile(long fileId) {
        Set<Tag> tags = new HashSet<>();
        // Используем JOIN для получения имен тегов сразу, но можно и просто tag_id, а потом дергать TagDAO
        String sql = "SELECT t.id, t.name FROM tags t " +
                     "JOIN file_tag_links ftl ON t.id = ftl.tag_id " +
                     "WHERE ftl.file_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, fileId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                tags.add(new Tag(rs.getLong("id"), rs.getString("name")));
            }
        } catch (SQLException e) {
            logger.error("Error fetching tags for fileId {}", fileId, e);
        }
        return tags;
    }

    public Set<TrackedFile> getFilesForTag(long tagId) {
        Set<TrackedFile> files = new HashSet<>();
        // Для этого метода нам понадобится TrackedFileDAO, чтобы сконструировать объекты TrackedFile.
        // Пока оставим заглушку или простой вариант, если TrackedFileDAO будет доступен.
        // Проще всего вернуть Set<Long> fileIds, а TrackedFileService сам получит объекты.
        // Либо, если мы передадим сюда TrackedFileDAO:
        // String sql = "SELECT tf.* FROM tracked_files tf " +
        //              "JOIN file_tag_links ftl ON tf.id = ftl.file_id " +
        //              "WHERE ftl.tag_id = ?";
        // ... и затем использовать mapRowToTrackedFile из TrackedFileDAO (если сделать его public static или передать DAO)
        logger.warn("getFilesForTag(long tagId) not fully implemented yet in FileTagLinkDAO.");
        // Пример реализации, если бы TrackedFileDAO был доступен и имел public mapRowToTrackedFile
        /*
        TrackedFileDAO trackedFileDAO = new TrackedFileDAO(); // Не очень хорошо, лучше DI
        String sql = "SELECT tf.* FROM tracked_files tf " +
                     "JOIN file_tag_links ftl ON tf.id = ftl.file_id " +
                     "WHERE ftl.tag_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, tagId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                // Предполагая, что mapRowToTrackedFile статический или DAO инжектирован
                // files.add(TrackedFileDAO.mapRowToTrackedFile(rs)); // Пример
            }
        } catch (SQLException e) {
            logger.error("Error fetching files for tagId {}", tagId, e);
        }
        */
        return files;
    }

    /**
     * Заменяет все текущие теги для файла на новый набор тегов.
     * Это делается в транзакции: сначала удаляются все старые связи, потом добавляются новые.
     * @param fileId ID файла
     * @param tags Новый набор тегов для файла.
     */
    public void replaceTagsForFile(long fileId, Set<Tag> tags) {
        String deleteAllLinksSql = "DELETE FROM file_tag_links WHERE file_id = ?";
        String insertLinkSql = "INSERT INTO file_tag_links(file_id, tag_id) VALUES(?, ?)";

        Connection conn = null;
        try {
            conn = DatabaseManager.getConnection();
            conn.setAutoCommit(false); // Начинаем транзакцию

            // 1. Удаляем все существующие связи для этого файла
            try (PreparedStatement deleteStmt = conn.prepareStatement(deleteAllLinksSql)) {
                deleteStmt.setLong(1, fileId);
                deleteStmt.executeUpdate();
            }

            // 2. Добавляем новые связи
            if (tags != null && !tags.isEmpty()) {
                try (PreparedStatement insertStmt = conn.prepareStatement(insertLinkSql)) {
                    for (Tag tag : tags) {
                        if (tag.getId() <= 0) { // Убедимся, что тег имеет ID (т.е. сохранен в БД)
                            logger.warn("Attempted to link unsaved tag (ID <=0): {} to fileId: {}", tag.getName(), fileId);
                            continue;
                        }
                        insertStmt.setLong(1, fileId);
                        insertStmt.setLong(2, tag.getId());
                        insertStmt.addBatch();
                    }
                    insertStmt.executeBatch();
                }
            }
            conn.commit(); // Завершаем транзакцию успешно
            logger.info("Replaced tags for fileId {}. New tag count: {}", fileId, tags != null ? tags.size() : 0);

        } catch (SQLException e) {
            logger.error("Error replacing tags for fileId {}", fileId, e);
            if (conn != null) {
                try {
                    conn.rollback(); // Откатываем транзакцию в случае ошибки
                } catch (SQLException ex) {
                    logger.error("Error rolling back transaction for fileId {}", fileId, ex);
                }
            }
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true); // Возвращаем режим автокоммита
                    conn.close();
                } catch (SQLException e) {
                    logger.error("Error closing connection after replacing tags for fileId {}", fileId, e);
                }
            }
        }
    }
}