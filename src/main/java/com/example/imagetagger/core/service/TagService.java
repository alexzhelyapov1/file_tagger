package com.example.imagetagger.core.service;

import com.example.imagetagger.core.model.Tag;
import com.example.imagetagger.persistence.dao.TagDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class TagService {
    private static final Logger logger = LoggerFactory.getLogger(TagService.class);
    private final TagDAO tagDAO;

    public TagService() {
        this.tagDAO = new TagDAO(); // В более сложных приложениях можно использовать DI
    }

    // Для тестов можно передавать mock DAO
    public TagService(TagDAO tagDAO) {
        this.tagDAO = tagDAO;
    }

    /**
     * Создает новый тег. Если тег с таким именем (регистронезависимо) уже существует,
     * возвращает существующий тег.
     *
     * @param name Имя тега.
     * @return Optional с созданным или существующим тегом, или Optional.empty() если произошла ошибка.
     */
    public Optional<Tag> createOrGetTag(String name) {
        if (name == null || name.trim().isEmpty()) {
            logger.warn("Attempted to create a tag with empty or null name.");
            return Optional.empty();
        }
        String trimmedName = name.trim();

        // Проверяем, существует ли тег с таким именем (регистронезависимо)
        Optional<Tag> existingTag = tagDAO.getByName(trimmedName);
        if (existingTag.isPresent()) {
            logger.info("Tag '{}' already exists, returning existing one.", trimmedName);
            return existingTag;
        }

        // Если не существует, создаем новый
        return tagDAO.create(trimmedName);
    }

    public List<Tag> getAllTags() {
        try {
            return tagDAO.getAll();
        } catch (Exception e) {
            logger.error("Failed to retrieve all tags.", e);
            return Collections.emptyList();
        }
    }

    public boolean deleteTag(long tagId) {
        if (tagId <= 0) {
            logger.warn("Attempted to delete tag with invalid id: {}", tagId);
            return false;
        }
        // Дополнительная логика (например, проверка, используется ли тег) может быть здесь
        // Но так как у нас ON DELETE CASCADE, это не так критично для целостности данных.
        return tagDAO.delete(tagId);
    }

    public Optional<Tag> findTagByName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return Optional.empty();
        }
        return tagDAO.getByName(name.trim());
    }
}