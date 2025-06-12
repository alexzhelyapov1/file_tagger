package com.example.imagetagger.ui.controller;

import com.example.imagetagger.core.model.TrackedFile;
import com.example.imagetagger.core.model.Tag;
import com.example.imagetagger.core.service.FileScannerService;
import com.example.imagetagger.core.service.TagService;
import com.example.imagetagger.core.service.TrackedFileService;
import com.example.imagetagger.persistence.dao.FileTagLinkDAO;
import com.example.imagetagger.persistence.dao.TagDAO;
import com.example.imagetagger.persistence.dao.TrackedFileDAO;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.MenuItem;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javafx.fxml.FXMLLoader; // Добавить этот импорт
import javafx.scene.Parent;    // Добавить этот импорт

public class MainViewController {

    private static final Logger logger = LoggerFactory.getLogger(MainViewController.class);

    @FXML private BorderPane rootPane;
    @FXML private ImageView mainImageView;
    @FXML private StackPane imageViewHolder; // Родительский контейнер для ImageView
    @FXML private Button previousImageButton;
    @FXML private Button nextImageButton;
    @FXML private MenuItem openFolderMenuItem; // Если нужно будет к нему обращаться

    private FileScannerService fileScannerService;
    private TrackedFileService trackedFileService; // Добавить это поле
    private TagService tagService;
    private List<TrackedFile> currentImageList = new ArrayList<>();
    private int currentImageIndex = -1;
    private RightToolbarController rightToolbarController;
    private TrackedFile currentlyDisplayedFile;

    @FXML
    public void initialize() {
        logger.info("MainViewController initialized.");

        // --- Начало исправленного блока инициализации сервисов ---
        // Создаем DAO один раз
        TrackedFileDAO trackedFileDAO = new TrackedFileDAO();
        TagDAO tagDAO = new TagDAO(); // Создаем экземпляр TagDAO

        // Инициализируем сервисы, передавая им созданные DAO
        this.tagService = new TagService(tagDAO); // TagService принимает TagDAO
        FileTagLinkDAO fileTagLinkDAO = new FileTagLinkDAO(tagDAO); // FileTagLinkDAO принимает TagDAO

        // TrackedFileService принимает TrackedFileDAO, FileTagLinkDAO и TagDAO
        this.trackedFileService = new TrackedFileService(trackedFileDAO, fileTagLinkDAO, tagDAO);
        
        this.fileScannerService = new FileScannerService(this.trackedFileService);
        // --- Конец исправленного блока инициализации сервисов ---

        mainImageView.fitWidthProperty().bind(imageViewHolder.widthProperty());
        mainImageView.fitHeightProperty().bind(imageViewHolder.heightProperty());

        loadRightToolbar();
        
        if (rightToolbarController != null) {
            // Передаем корректно инициализированные сервисы
            rightToolbarController.setServices(this.tagService, this.trackedFileService);
        }
    }

    private void loadRightToolbar() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/imagetagger/fxml/RightToolbar.fxml"));
            Parent rightToolbarNode = loader.load();
            rightToolbarController = loader.getController(); // Получаем контроллер правой панели
            rightToolbarController.setMainViewController(this); // Передаем ссылку на себя

            rootPane.setRight(rightToolbarNode); // Устанавливаем в правую часть BorderPane
            logger.info("Right toolbar loaded successfully.");
        } catch (Exception e) {
            logger.error("Failed to load RightToolbar.fxml", e);
        }
    }

    public void refreshTagRelatedViews() {
        if (rightToolbarController != null) {
            rightToolbarController.refreshTagLists();
        }
        // Если текущий файл отображается, обновить его теги
        if (currentlyDisplayedFile != null && rightToolbarController != null) {
             Optional<TrackedFile> freshFileOpt = trackedFileService.findByPathWithTags(currentlyDisplayedFile.getAbsolutePath());
             freshFileOpt.ifPresent(file -> {
                 currentlyDisplayedFile = file; // Обновляем локальную копию
                 rightToolbarController.setCurrentFile(currentlyDisplayedFile);
             });
        }
    }

    public void handleGlobalTagDeleted(Tag deletedTag) {
        logger.info("MainViewController notified of global tag deletion: {}", deletedTag.getName());
        // Если текущий файл содержал этот тег, его список тегов в RightToolbarController
        // уже должен был обновиться.
        // Здесь мы могли бы обновить, например, фильтры, если бы они были.
        // Или, если currentlyDisplayedFile хранил этот тег, его нужно обновить:
        if (currentlyDisplayedFile != null && currentlyDisplayedFile.getTags().contains(deletedTag)) {
            currentlyDisplayedFile.removeTag(deletedTag); // Обновляем объект в памяти MainViewController
            // RightToolbarController.setCurrentFile уже был вызван изнутри RightToolbarController
            // при удалении тега из currentImageTagsObservableList, но для надежности можно:
             if (rightToolbarController != null) {
                 rightToolbarController.setCurrentFile(currentlyDisplayedFile);
             }
        }
    }

    @FXML
    private void handleOpenFolder() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Open Image Folder");
        // Пытаемся получить текущее окно для DirectoryChooser
        Stage stage = (Stage) rootPane.getScene().getWindow();
        File selectedDirectory = directoryChooser.showDialog(stage);

        if (selectedDirectory != null) {
            logger.info("Folder selected: {}", selectedDirectory.getAbsolutePath());
            loadImagesFromDirectory(selectedDirectory);
        } else {
            logger.info("No folder selected.");
        }
    }

    private void loadImagesFromDirectory(File directory) {
        currentImageList = fileScannerService.scanDirectoryForImages(directory);
        if (!currentImageList.isEmpty()) {
            currentImageIndex = 0;
            displayImageAtIndex(currentImageIndex);
            // updateNavigationButtons(); // Уже вызывается в displayImageAtIndex
        } else {
            currentImageIndex = -1;
            currentlyDisplayedFile = null; // Очищаем текущий файл
            mainImageView.setImage(null);
            if (rightToolbarController != null) {
                rightToolbarController.setCurrentFile(null); // Уведомляем тулбар
            }
            updateNavigationButtons();
            logger.info("No supported images found in directory: {}", directory.getAbsolutePath());
        }
    }

    private void displayImageAtIndex(int index) {
        if (index >= 0 && index < currentImageList.size()) {
            TrackedFile trackedFileToShow = currentImageList.get(index);

            // Получаем актуальную версию файла с тегами из сервиса
            // Это важно, так как FileScannerService мог вернуть объект без тегов
            // или с неактуальными тегами, если мы не хотим грузить их все при сканировании.
            // Но наш getOrCreateTrackedFile УЖЕ загружает теги.
            // Для чистоты, можно всегда получать актуальный объект перед отображением.
            Optional<TrackedFile> freshFileOpt = trackedFileService.findByPathWithTags(trackedFileToShow.getAbsolutePath());

            if (freshFileOpt.isPresent()) {
                currentlyDisplayedFile = freshFileOpt.get();
                logger.info("Displaying image: {} (ID: {})", currentlyDisplayedFile.getAbsolutePath(), currentlyDisplayedFile.getId());
                try (FileInputStream fis = new FileInputStream(currentlyDisplayedFile.getFile())) {
                    Image image = new Image(fis);
                    if (image.isError()) {
                        logger.error("Error loading image: {}. Exception: {}", currentlyDisplayedFile.getAbsolutePath(), image.getException());
                        mainImageView.setImage(null);
                    } else {
                        mainImageView.setImage(image);
                    }
                } catch (FileNotFoundException e) {
                    logger.error("Image file not found: {}", currentlyDisplayedFile.getAbsolutePath(), e);
                    mainImageView.setImage(null);
                } catch (Exception e) {
                    logger.error("Failed to load image: {}", currentlyDisplayedFile.getAbsolutePath(), e);
                    mainImageView.setImage(null);
                }

                // Уведомляем RightToolbarController о смене файла
                if (rightToolbarController != null) {
                    rightToolbarController.setCurrentFile(currentlyDisplayedFile);
                }
            } else {
                logger.error("Could not retrieve tracked file data for path: {}", trackedFileToShow.getAbsolutePath());
                mainImageView.setImage(null);
                currentlyDisplayedFile = null;
                if (rightToolbarController != null) {
                    rightToolbarController.setCurrentFile(null);
                }
            }
        } else {
            logger.warn("Attempted to display image at invalid index: {}", index);
            mainImageView.setImage(null);
            currentlyDisplayedFile = null;
            if (rightToolbarController != null) {
                rightToolbarController.setCurrentFile(null);
            }
        }
        updateNavigationButtons();
    }


    @FXML
    private void handlePreviousImage() {
        if (currentImageIndex > 0) {
            currentImageIndex--;
            displayImageAtIndex(currentImageIndex);
        }
    }

    @FXML
    private void handleNextImage() {
        if (currentImageIndex < currentImageList.size() - 1) {
            currentImageIndex++;
            displayImageAtIndex(currentImageIndex);
        }
    }

    private void updateNavigationButtons() {
        previousImageButton.setDisable(currentImageList.isEmpty() || currentImageIndex <= 0);
        nextImageButton.setDisable(currentImageList.isEmpty() || currentImageIndex >= currentImageList.size() - 1);
    }

    @FXML
    private void handleExit() {
        Platform.exit();
    }
}