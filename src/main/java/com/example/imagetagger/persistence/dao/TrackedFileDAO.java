package com.example.imagetagger.persistence.dao;

import com.example.imagetagger.core.model.TrackedFile;
import com.example.imagetagger.persistence.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class TrackedFileDAO {
    private static final Logger logger = LoggerFactory.getLogger(TrackedFileDAO.class);

    public Optional<TrackedFile> create(TrackedFile file) {
        String sql = "INSERT INTO tracked_files(absolute_path, content_hash, size_bytes, modified_date, last_seen_date) " +
                     "VALUES(?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setString(1, file.getAbsolutePath());
            pstmt.setString(2, file.getContentHash());
            pstmt.setLong(3, file.getSizeBytes());
            pstmt.setLong(4, file.getModifiedDate());
            pstmt.setLong(5, file.getLastSeenDate());

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                logger.warn("Creating tracked file failed, no rows affected for path: {}", file.getAbsolutePath());
                return Optional.empty();
            }

            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    file.setId(generatedKeys.getLong(1));
                    logger.info("TrackedFile created: {}", file);
                    return Optional.of(file);
                } else {
                    logger.warn("Creating tracked file failed, no ID obtained for path: {}", file.getAbsolutePath());
                    return Optional.empty();
                }
            }
        } catch (SQLException e) {
            // SQLITE_CONSTRAINT_UNIQUE (код 19) если absolute_path уже существует
            if (e.getErrorCode() == 19 && e.getMessage().contains("UNIQUE constraint failed: tracked_files.absolute_path")) {
                 logger.warn("TrackedFile with path '{}' already exists in DB.", file.getAbsolutePath());
            } else {
                logger.error("Error creating tracked file with path: {}", file.getAbsolutePath(), e);
            }
            return Optional.empty();
        }
    }

    public boolean update(TrackedFile file) {
        String sql = "UPDATE tracked_files SET absolute_path = ?, content_hash = ?, size_bytes = ?, " +
                     "modified_date = ?, last_seen_date = ? WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, file.getAbsolutePath());
            pstmt.setString(2, file.getContentHash());
            pstmt.setLong(3, file.getSizeBytes());
            pstmt.setLong(4, file.getModifiedDate());
            pstmt.setLong(5, file.getLastSeenDate());
            pstmt.setLong(6, file.getId());

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows > 0) {
                logger.info("TrackedFile updated: {}", file);
                return true;
            }
        } catch (SQLException e) {
             // SQLITE_CONSTRAINT_UNIQUE (код 19) если absolute_path уже существует и это не этот же файл
            if (e.getErrorCode() == 19 && e.getMessage().contains("UNIQUE constraint failed: tracked_files.absolute_path")) {
                 logger.warn("Failed to update TrackedFile. Path '{}' might already exist for another entry.", file.getAbsolutePath());
            } else {
                logger.error("Error updating tracked file: {}", file, e);
            }
        }
        return false;
    }

    public Optional<TrackedFile> getById(long id) {
        String sql = "SELECT * FROM tracked_files WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, id);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return Optional.of(mapRowToTrackedFile(rs));
            }
        } catch (SQLException e) {
            logger.error("Error fetching tracked file by id: {}", id, e);
        }
        return Optional.empty();
    }

    public Optional<TrackedFile> getByPath(String absolutePath) {
        String sql = "SELECT * FROM tracked_files WHERE absolute_path = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, absolutePath);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return Optional.of(mapRowToTrackedFile(rs));
            }
        } catch (SQLException e) {
            logger.error("Error fetching tracked file by path: {}", absolutePath, e);
        }
        return Optional.empty();
    }
    
    public List<TrackedFile> getByContentHash(String contentHash) {
        List<TrackedFile> files = new ArrayList<>();
        String sql = "SELECT * FROM tracked_files WHERE content_hash = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, contentHash);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                files.add(mapRowToTrackedFile(rs));
            }
        } catch (SQLException e) {
            logger.error("Error fetching tracked files by content hash: {}", contentHash, e);
        }
        return files;
    }

    public List<TrackedFile> getAll() {
        List<TrackedFile> files = new ArrayList<>();
        String sql = "SELECT * FROM tracked_files ORDER BY absolute_path";
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                files.add(mapRowToTrackedFile(rs));
            }
        } catch (SQLException e) {
            logger.error("Error fetching all tracked files", e);
        }
        return files;
    }

    public boolean delete(long id) {
        String sql = "DELETE FROM tracked_files WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, id);
            int affectedRows = pstmt.executeUpdate();
             if (affectedRows > 0) {
                logger.info("TrackedFile deleted with id: {}", id);
                return true;
            }
        } catch (SQLException e) {
            logger.error("Error deleting tracked file with id: {}", id, e);
        }
        return false;
    }

    private TrackedFile mapRowToTrackedFile(ResultSet rs) throws SQLException {
        return new TrackedFile(
                rs.getLong("id"),
                rs.getString("absolute_path"),
                rs.getString("content_hash"),
                rs.getLong("size_bytes"),
                rs.getLong("modified_date"),
                rs.getLong("last_seen_date")
        );
    }
}