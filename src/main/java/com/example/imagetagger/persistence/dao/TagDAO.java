package com.example.imagetagger.persistence.dao;

import com.example.imagetagger.core.model.Tag;
import com.example.imagetagger.persistence.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class TagDAO {
    private static final Logger logger = LoggerFactory.getLogger(TagDAO.class);

    public Optional<Tag> create(String name) {
        String sql = "INSERT INTO tags(name) VALUES(?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, name);
            int affectedRows = pstmt.executeUpdate();

            if (affectedRows == 0) {
                logger.warn("Creating tag failed, no rows affected for name: {}", name);
                return Optional.empty();
            }

            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    Tag newTag = new Tag(generatedKeys.getLong(1), name);
                    logger.info("Tag created: {}", newTag);
                    return Optional.of(newTag);
                } else {
                    logger.warn("Creating tag failed, no ID obtained for name: {}", name);
                    return Optional.empty();
                }
            }
        } catch (SQLException e) {
            // SQLITE_CONSTRAINT_UNIQUE (код 19) если имя уже существует
            if (e.getErrorCode() == 19 && e.getMessage().contains("UNIQUE constraint failed: tags.name")) {
                logger.warn("Tag with name '{}' already exists.", name);
            } else {
                logger.error("Error creating tag with name: {}", name, e);
            }
            return Optional.empty();
        }
    }

    public Optional<Tag> getById(long id) {
        String sql = "SELECT id, name FROM tags WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, id);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return Optional.of(new Tag(rs.getLong("id"), rs.getString("name")));
            }
        } catch (SQLException e) {
            logger.error("Error fetching tag by id: {}", id, e);
        }
        return Optional.empty();
    }

    public Optional<Tag> getByName(String name) {
        // Поиск регистронезависимый благодаря COLLATE NOCASE
        String sql = "SELECT id, name FROM tags WHERE name = ? COLLATE NOCASE";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, name);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                // Возвращаем тег с именем, как оно хранится в БД (для сохранения регистра)
                return Optional.of(new Tag(rs.getLong("id"), rs.getString("name")));
            }
        } catch (SQLException e) {
            logger.error("Error fetching tag by name: {}", name, e);
        }
        return Optional.empty();
    }

    public List<Tag> getAll() {
        List<Tag> tags = new ArrayList<>();
        String sql = "SELECT id, name FROM tags ORDER BY name COLLATE NOCASE"; // Сортируем для удобства
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                tags.add(new Tag(rs.getLong("id"), rs.getString("name")));
            }
        } catch (SQLException e) {
            logger.error("Error fetching all tags", e);
        }
        return tags;
    }

    // Обновление не очень актуально для тегов, т.к. обычно меняется только связь с файлом.
    // Если нужно будет переименовывать теги, тогда добавить. Пока пропустим.
    // public boolean update(Tag tag) { ... }

    public boolean delete(long id) {
        // При удалении тега, связи в file_tag_links удалятся автоматически благодаря ON DELETE CASCADE
        String sql = "DELETE FROM tags WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, id);
            int affectedRows = pstmt.executeUpdate();
            if (affectedRows > 0) {
                logger.info("Tag deleted with id: {}", id);
                return true;
            }
        } catch (SQLException e) {
            logger.error("Error deleting tag with id: {}", id, e);
        }
        return false;
    }
}