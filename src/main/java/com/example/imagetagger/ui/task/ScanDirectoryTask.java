    package com.example.imagetagger.ui.task;

import com.example.imagetagger.core.model.TrackedFile;
import com.example.imagetagger.core.service.FileScannerService;
import javafx.concurrent.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;
import java.util.Set; // Если будем передавать фильтры

public class ScanDirectoryTask extends Task<List<TrackedFile>> {

    private static final Logger logger = LoggerFactory.getLogger(ScanDirectoryTask.class);

    private final FileScannerService fileScannerService;
    private final File directory;
    // private final Set<Tag> activeTagFilters; // Если нужно передавать фильтры в сам таск

    public ScanDirectoryTask(FileScannerService fileScannerService, File directory /*, Set<Tag> activeTagFilters */) {
        this.fileScannerService = fileScannerService;
        this.directory = directory;
        // this.activeTagFilters = activeTagFilters;
    }

    @Override
    protected List<TrackedFile> call() throws Exception {
        logger.info("ScanDirectoryTask started for directory: {}", directory.getAbsolutePath());
        updateMessage("Scanning directory: " + directory.getName() + "..."); // Обновляем сообщение для UI

        // Здесь происходит основная работа
        List<TrackedFile> allFilesInDirectory = fileScannerService.scanDirectoryForImages(directory);
        
        // Фильтрация может происходить здесь или после получения результата в MainViewController
        // Если фильтровать здесь:
        /*
        if (activeTagFilters == null || activeTagFilters.isEmpty()) {
            updateMessage("Found " + allFilesInDirectory.size() + " images.");
            return allFilesInDirectory;
        } else {
            updateMessage("Filtering " + allFilesInDirectory.size() + " images by tags...");
            List<TrackedFile> filteredList = allFilesInDirectory.stream()
                .filter(trackedFile -> {
                    if (trackedFile.getTags() == null || trackedFile.getTags().isEmpty()) {
                        return false;
                    }
                    return trackedFile.getTags().stream().anyMatch(activeTagFilters::contains);
                })
                .collect(Collectors.toList());
            updateMessage("Found " + filteredList.size() + " images matching filter.");
            return filteredList;
        }
        */
        updateMessage("Found " + allFilesInDirectory.size() + " images in " + directory.getName());
        logger.info("ScanDirectoryTask finished. Found {} images.", allFilesInDirectory.size());
        return allFilesInDirectory; // Пока возвращаем все, фильтрация в MainViewController
    }
}