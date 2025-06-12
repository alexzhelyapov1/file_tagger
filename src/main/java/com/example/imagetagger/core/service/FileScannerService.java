package com.example.imagetagger.core.service;

import com.example.imagetagger.core.model.TrackedFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FileScannerService {

    private static final Logger logger = LoggerFactory.getLogger(FileScannerService.class);
    private static final List<String> SUPPORTED_EXTENSIONS = List.of(".png"); // Пока только PNG

    private final TrackedFileService trackedFileService;

    public FileScannerService(TrackedFileService trackedFileService) {
        this.trackedFileService = trackedFileService;
    }

    public List<TrackedFile> scanDirectoryForImages(File directory) {
        if (directory == null || !directory.isDirectory()) {
            logger.warn("Provided path is not a directory or is null: {}", directory);
            return Collections.emptyList();
        }

        logger.info("Scanning directory for images: {}", directory.getAbsolutePath());
        try (Stream<Path> stream = Files.walk(directory.toPath(), 1)) { // Глубина поиска 1
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String fileName = path.getFileName().toString().toLowerCase();
                        return SUPPORTED_EXTENSIONS.stream().anyMatch(fileName::endsWith);
                    })
                    .map(path -> {
                        // Используем TrackedFileService для получения или создания TrackedFile
                        Optional<TrackedFile> trackedFileOpt = trackedFileService.getOrCreateTrackedFile(path.toFile());
                        trackedFileOpt.ifPresent(trackedFileService::updateLastSeen); // Обновляем last_seen_date
                        return trackedFileOpt.orElse(null); // Возвращаем null, если не удалось получить/создать
                    })
                    .filter(Objects::nonNull) // Отфильтровываем null значения (если были ошибки)
                    .sorted((f1, f2) -> f1.getName().compareToIgnoreCase(f2.getName())) // Сортировка по имени
                    .collect(Collectors.toList());
        } catch (IOException e) {
            logger.error("Error scanning directory: {}", directory.getAbsolutePath(), e);
            return Collections.emptyList();
        }
    }
}