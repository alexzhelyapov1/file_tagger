package com.example.imagetagger.core.service;

import com.example.imagetagger.core.model.Tag;
import com.example.imagetagger.core.model.TrackedFile;
import com.example.imagetagger.persistence.dao.FileTagLinkDAO;
import com.example.imagetagger.persistence.dao.TagDAO;
import com.example.imagetagger.persistence.dao.TrackedFileDAO;
import com.example.imagetagger.util.FileHasher; // Убедитесь, что FileHasher создан и импортирован
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Optional;
import java.util.Set;

public class TrackedFileService {
    private static final Logger logger = LoggerFactory.getLogger(TrackedFileService.class);

    private final TrackedFileDAO trackedFileDAO;
    private final FileTagLinkDAO fileTagLinkDAO;
    private final TagDAO tagDAO; // Нужен для создания/получения тегов по имени

    public TrackedFileService() {
        // В реальном приложении здесь была бы инъекция зависимостей
        this.trackedFileDAO = new TrackedFileDAO();
        this.tagDAO = new TagDAO();
        this.fileTagLinkDAO = new FileTagLinkDAO(this.tagDAO); // FileTagLinkDAO может зависеть от TagDAO
    }

    // Конструктор для DI (например, для тестов)
    public TrackedFileService(TrackedFileDAO trackedFileDAO, FileTagLinkDAO fileTagLinkDAO, TagDAO tagDAO) {
        this.trackedFileDAO = trackedFileDAO;
        this.fileTagLinkDAO = fileTagLinkDAO;
        this.tagDAO = tagDAO;
    }

    /**
     * Получает или создает TrackedFile для указанного файла на диске.
     * Если файл уже есть в БД по пути, он загружается.
     * Если файла нет, он создается, вычисляется хэш, и файл сохраняется в БД.
     * Теги для файла также загружаются.
     *
     * @param diskFile Файл на диске.
     * @return Optional с TrackedFile, или Optional.empty() если произошла ошибка.
     */
    public Optional<TrackedFile> getOrCreateTrackedFile(File diskFile) {
        if (diskFile == null || !diskFile.exists() || !diskFile.isFile()) {
            logger.warn("Invalid file provided to getOrCreateTrackedFile: {}", diskFile);
            return Optional.empty();
        }

        String absolutePath = diskFile.getAbsolutePath();

        // 1. Попытка найти файл в БД по пути
        Optional<TrackedFile> existingFileOpt = trackedFileDAO.getByPath(absolutePath);

        if (existingFileOpt.isPresent()) {
            TrackedFile trackedFile = existingFileOpt.get();
            // Проверка, не изменился ли файл (размер, дата модификации)
            // (Эту логику можно усложнить, как мы обсуждали ранее, с хэшами и т.д.)
            try {
                BasicFileAttributes attrs = Files.readAttributes(diskFile.toPath(), BasicFileAttributes.class);
                long currentSize = attrs.size();
                long currentModDate = attrs.lastModifiedTime().toMillis();

                boolean needsUpdate = false;
                if (trackedFile.getSizeBytes() != currentSize || trackedFile.getModifiedDate() != currentModDate) {
                    logger.info("File {} has changed on disk. Updating metadata.", absolutePath);
                    trackedFile.setSizeBytes(currentSize);
                    trackedFile.setModifiedDate(currentModDate);
                    // Пересчитываем хэш, если файл изменился
                    FileHasher.calculateSHA256(diskFile).ifPresent(trackedFile::setContentHash);
                    needsUpdate = true;
                }
                trackedFile.setLastSeenDate(System.currentTimeMillis());
                if (needsUpdate) {
                    trackedFileDAO.update(trackedFile);
                }
            } catch (IOException e) {
                logger.error("Could not read file attributes for {}: {}", absolutePath, e.getMessage());
                // Продолжаем с тем, что есть в БД, или можно вернуть empty
            }

            // Загружаем теги для существующего файла
            trackedFile.setTags(fileTagLinkDAO.getTagsForFile(trackedFile.getId()));
            logger.debug("Found existing TrackedFile: {} with {} tags", trackedFile, trackedFile.getTags().size());
            return Optional.of(trackedFile);
        } else {
            // 2. Файла нет в БД по этому пути, создаем новый
            logger.info("File {} not found in DB by path. Creating new entry.", absolutePath);
            Optional<String> hashOpt = FileHasher.calculateSHA256(diskFile);
            if (hashOpt.isEmpty()) {
                logger.error("Could not calculate hash for new file: {}", absolutePath);
                return Optional.empty(); // Не можем создать файл без хэша
            }

            try {
                BasicFileAttributes attrs = Files.readAttributes(diskFile.toPath(), BasicFileAttributes.class);
                TrackedFile newTrackedFile = new TrackedFile(
                        absolutePath,
                        hashOpt.get(),
                        attrs.size(),
                        attrs.lastModifiedTime().toMillis()
                );
                // Попытка сохранить в БД
                Optional<TrackedFile> createdFileOpt = trackedFileDAO.create(newTrackedFile);
                if (createdFileOpt.isPresent()) {
                    TrackedFile createdFile = createdFileOpt.get();
                    // Новый файл по определению не имеет тегов, так что getTagsForFile не нужен
                    logger.info("Created new TrackedFile: {}", createdFile);
                    return Optional.of(createdFile);
                } else {
                    // Это может случиться, если при создании возникла гонка или другая ошибка
                    // Попробуем еще раз найти по пути, вдруг его кто-то создал параллельно
                    return trackedFileDAO.getByPath(absolutePath);
                }
            } catch (IOException e) {
                logger.error("Could not read file attributes for new file {}: {}", absolutePath, e.getMessage());
                return Optional.empty();
            }
        }
    }

    public void addTagToFile(TrackedFile file, Tag tag) {
        if (file == null || tag == null || file.getId() <= 0 || tag.getId() <= 0) {
            logger.warn("Invalid file or tag provided for linking. File: {}, Tag: {}", file, tag);
            return;
        }
        if (fileTagLinkDAO.linkTagToFile(file.getId(), tag.getId())) {
            file.addTag(tag); // Обновляем объект в памяти
            logger.info("Tag '{}' added to file '{}'", tag.getName(), file.getAbsolutePath());
        }
    }

    public void removeTagFromFile(TrackedFile file, Tag tag) {
        if (file == null || tag == null || file.getId() <= 0 || tag.getId() <= 0) {
            logger.warn("Invalid file or tag provided for unlinking. File: {}, Tag: {}", file, tag);
            return;
        }
        if (fileTagLinkDAO.unlinkTagFromFile(file.getId(), tag.getId())) {
            file.removeTag(tag); // Обновляем объект в памяти
            logger.info("Tag '{}' removed from file '{}'", tag.getName(), file.getAbsolutePath());
        }
    }

    /**
     * Устанавливает (заменяет) набор тегов для файла.
     * @param file Файл, которому присваиваются теги.
     * @param tags Набор тегов. Убедитесь, что все теги в наборе имеют корректный ID (т.е. сохранены в БД).
     */
    public void setTagsForFile(TrackedFile file, Set<Tag> tags) {
        if (file == null || file.getId() <= 0) {
            logger.warn("Cannot set tags for null or unsaved file: {}", file);
            return;
        }
        // Убедимся, что все теги имеют ID. Если нет, их нужно сначала сохранить.
        // Для простоты, здесь предполагаем, что TagService уже позаботился об этом.
        for (Tag tag : tags) {
            if (tag.getId() <= 0) {
                Optional<Tag> persistedTag = tagDAO.getByName(tag.getName()); // или createOrGetTag
                if (persistedTag.isPresent()) {
                    tag.setId(persistedTag.get().getId()); // Обновляем ID в объекте
                } else {
                     logger.error("Cannot set unsaved tag '{}' (ID missing) for file {}", tag.getName(), file.getAbsolutePath());
                     // Возможно, стоит выбросить исключение или пропустить этот тег.
                     return; // Прерываем операцию, если тег не может быть сохранен/найден.
                }
            }
        }

        fileTagLinkDAO.replaceTagsForFile(file.getId(), tags);
        file.setTags(tags); // Обновляем объект в памяти
        logger.info("Set {} tags for file '{}'", tags.size(), file.getAbsolutePath());
    }

    public Optional<TrackedFile> findByPathWithTags(String absolutePath) {
        Optional<TrackedFile> fileOpt = trackedFileDAO.getByPath(absolutePath);
        fileOpt.ifPresent(tf -> tf.setTags(fileTagLinkDAO.getTagsForFile(tf.getId())));
        return fileOpt;
    }
    
    public void updateLastSeen(TrackedFile file) {
        if (file != null && file.getId() > 0) {
            file.setLastSeenDate(System.currentTimeMillis());
            trackedFileDAO.update(file); // Обновляем только last_seen_date (и другие поля, если они изменились)
        }
    }
}