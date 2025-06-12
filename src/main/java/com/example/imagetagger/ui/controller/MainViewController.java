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
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javafx.fxml.FXMLLoader; // Добавить этот импорт
import javafx.scene.Parent;    // Добавить этот импорт
import javafx.concurrent.Task; // Добавить этот импорт
import javafx.scene.control.Label; // Добавить этот импорт
import javafx.scene.control.Menu;    // Добавить этот импорт
import javafx.scene.control.ProgressIndicator; // Добавить этот импорт
import com.example.imagetagger.ui.task.ScanDirectoryTask; // Добавить импорт
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class MainViewController {

    private static final Logger logger = LoggerFactory.getLogger(MainViewController.class);

    @FXML private BorderPane rootPane;
    @FXML private ImageView mainImageView;
    @FXML private StackPane imageViewHolder; 
    @FXML private Button previousImageButton;
    @FXML private Button nextImageButton;
    @FXML private MenuItem openFolderMenuItem; 
    @FXML private Label statusBarLabel; 
    @FXML private ProgressIndicator scanProgressIndicator; 
    @FXML private Menu fileMenu;

    private FileScannerService fileScannerService;
    private TrackedFileService trackedFileService; // Добавить это поле
    private TagService tagService;
    private final ObservableList<TrackedFile> currentImageList = FXCollections.observableArrayList();
    private RightToolbarController rightToolbarController;
    private LeftToolbarController leftToolbarController;
    private TrackedFile currentlyDisplayedFile;
    private final javafx.beans.property.IntegerProperty currentImageIndexProperty = new javafx.beans.property.SimpleIntegerProperty(-1);

    private File currentOpenDirectory;
    // Добавить поле для хранения активных тегов фильтрации
    private Set<Tag> activeTagFilters = new HashSet<>();

    @FXML
    public void initialize() {
        logger.info("MainViewController initialized.");

        TrackedFileDAO trackedFileDAO = new TrackedFileDAO();
        TagDAO tagDAO = new TagDAO();

        this.tagService = new TagService(tagDAO);
        FileTagLinkDAO fileTagLinkDAO = new FileTagLinkDAO(tagDAO);
        this.trackedFileService = new TrackedFileService(trackedFileDAO, fileTagLinkDAO, tagDAO);
        this.fileScannerService = new FileScannerService(this.trackedFileService);

        mainImageView.fitWidthProperty().bind(imageViewHolder.widthProperty());
        mainImageView.fitHeightProperty().bind(imageViewHolder.heightProperty());

        loadLeftToolbar();
        loadRightToolbar();
        
        if (rightToolbarController != null) {
            rightToolbarController.setServices(this.tagService, this.trackedFileService);
        }
        if (leftToolbarController != null) {
            leftToolbarController.setTagService(this.tagService);
            leftToolbarController.setMainViewController(this);
        }
        // Изначально currentImageList пуст, currentImageIndexProperty.get() будет -1
        updateStatusBar("Ready. " + currentImageList.size() + " images loaded.");
    }

    private void updateStatusBar(String message) {
        if (statusBarLabel != null) {
            Platform.runLater(() -> statusBarLabel.setText(message));
        }
    }

    private void loadLeftToolbar() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/imagetagger/fxml/LeftToolbar.fxml"));
            Parent leftToolbarNode = loader.load();
            leftToolbarController = loader.getController();
            rootPane.setLeft(leftToolbarNode);
            logger.info("Left toolbar loaded successfully.");
        } catch (Exception e) {
            logger.error("Failed to load LeftToolbar.fxml", e);
        }
    }

    public void applyTagFilter(Set<Tag> filterTags) {
        logger.info("Tag filter received in MainViewController: {}", filterTags.stream().map(Tag::getName).collect(Collectors.toList()));
        this.activeTagFilters = filterTags;

        if (this.currentOpenDirectory != null) {
            loadImagesFromDirectory(this.currentOpenDirectory); 
        } else {
            logger.debug("No directory open, filter will be applied on next folder open.");
            updateStatusBar("Filter set. Open a folder to apply.");
        }
    }

    private void loadRightToolbar() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/imagetagger/fxml/RightToolbar.fxml"));
            Parent rightToolbarNode = loader.load();
            rightToolbarController = loader.getController(); 
            rightToolbarController.setMainViewController(this); 
            rootPane.setRight(rightToolbarNode); 
            logger.info("Right toolbar loaded successfully.");
        } catch (Exception e) {
            logger.error("Failed to load RightToolbar.fxml", e);
        }
    }

    public void refreshTagRelatedViews() { // Переименован из refreshAllTagViews для единообразия
        if (rightToolbarController != null) {
            rightToolbarController.refreshTagLists(); 
        }
        if (leftToolbarController != null) {
            leftToolbarController.refreshAvailableTags(); 
        }
        if (currentlyDisplayedFile != null && rightToolbarController != null) {
             Optional<TrackedFile> freshFileOpt = trackedFileService.findByPathWithTags(currentlyDisplayedFile.getAbsolutePath());
             freshFileOpt.ifPresent(file -> {
                 currentlyDisplayedFile = file; 
                 rightToolbarController.setCurrentFile(currentlyDisplayedFile);
             });
        }
        if (currentOpenDirectory != null && (activeTagFilters != null && !activeTagFilters.isEmpty())) { // Проверка, что фильтр не пуст
            loadImagesFromDirectory(currentOpenDirectory);
        } else if (currentOpenDirectory != null) { // Если фильтр пуст, но папка открыта, тоже перезагружаем (на случай если файлы изменились)
            loadImagesFromDirectory(currentOpenDirectory);
        }
    }

    public void handleGlobalTagDeleted(Tag deletedTag) {
        logger.info("MainViewController notified of global tag deletion: {}", deletedTag.getName());
        
        if (leftToolbarController != null) {
            leftToolbarController.refreshAvailableTags();
        }

        if (activeTagFilters != null && activeTagFilters.contains(deletedTag)) {
            activeTagFilters.remove(deletedTag);
            if (this.currentOpenDirectory != null) {
                loadImagesFromDirectory(this.currentOpenDirectory);
            }
        }
        
        if (currentlyDisplayedFile != null && currentlyDisplayedFile.getTags().contains(deletedTag)) {
            currentlyDisplayedFile.removeTag(deletedTag);
             if (rightToolbarController != null) {
                 rightToolbarController.refreshTagLists(); 
             }
        }
    }


    @FXML
    private void handleOpenFolder() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Open Image Folder");
        Stage stage = (Stage) rootPane.getScene().getWindow();
        File selectedDirectory = directoryChooser.showDialog(stage);

        if (selectedDirectory != null) {
            logger.info("Folder selected: {}", selectedDirectory.getAbsolutePath());
            loadImagesFromDirectory(selectedDirectory); // currentOpenDirectory будет установлен внутри
        } else {
            logger.info("No folder selected.");
        }
    }

    private void loadImagesFromDirectory(File directory) {
        if (directory == null || !directory.isDirectory()) {
            logger.warn("Cannot load images, invalid directory provided: {}", directory);
            updateStatusBar("Invalid directory selected.");
            return;
        }
        this.currentOpenDirectory = directory; 

        ScanDirectoryTask scanTask = new ScanDirectoryTask(fileScannerService, directory);

        scanTask.messageProperty().addListener((obs, oldMsg, newMsg) -> updateStatusBar(newMsg));
        
        scanProgressIndicator.visibleProperty().bind(scanTask.runningProperty());
        openFolderMenuItem.disableProperty().bind(scanTask.runningProperty());
        fileMenu.disableProperty().bind(scanTask.runningProperty()); 
        
        // Отвязываем старые биндинги перед установкой новых, чтобы избежать конфликтов
        previousImageButton.disableProperty().unbind();
        nextImageButton.disableProperty().unbind();

        // Новые биндинги, учитывающие состояние scanTask
        previousImageButton.disableProperty().bind(scanTask.runningProperty().or(
            Bindings.createBooleanBinding(() -> { // Лямбда теперь явно возвращает boolean
                        return currentImageList.isEmpty() || currentImageIndexProperty.get() <= 0;
                    }, currentImageList, currentImageIndexProperty // currentImageList теперь ObservableList
            )
        ));
        nextImageButton.disableProperty().bind(scanTask.runningProperty().or(
            Bindings.createBooleanBinding(() -> { // Лямбда теперь явно возвращает boolean
                        return currentImageList.isEmpty() || currentImageIndexProperty.get() >= currentImageList.size() - 1;
                    }, currentImageList, currentImageIndexProperty // currentImageList теперь ObservableList
            )
        ));


        scanTask.setOnSucceeded(event -> {
            List<TrackedFile> allFilesInDirectory = scanTask.getValue();
            processScannedFiles(allFilesInDirectory); 
            updateStatusBar(currentImageList.size() + " images loaded. " + (activeTagFilters.isEmpty() ? "" : "Filter active."));
            // updateNavigationButtons() вызовется в конце processScannedFiles
        });

        scanTask.setOnFailed(event -> {
            Throwable e = scanTask.getException();
            logger.error("Failed to scan directory: {}", directory.getAbsolutePath(), e);
            updateStatusBar("Error scanning directory: " + e.getMessage());
            currentImageList.clear();
            currentImageIndexProperty.set(-1); 
            // this.currentImageIndex = -1; // Синхронизируется из property
            currentlyDisplayedFile = null;
            mainImageView.setImage(null);
            if (rightToolbarController != null) {
                rightToolbarController.setCurrentFile(null);
            }
            updateNavigationButtons();
            // Сброс UI после ошибки - важно отвязать свойства, которые были привязаны к scanTask.runningProperty()
            scanProgressIndicator.visibleProperty().unbind();
            scanProgressIndicator.setVisible(false);
            openFolderMenuItem.disableProperty().unbind();
            openFolderMenuItem.setDisable(false);
            fileMenu.disableProperty().unbind();
            fileMenu.setDisable(false);
            // Также отвязываем кнопки навигации, чтобы updateNavigationButtons мог ими управлять
            previousImageButton.disableProperty().unbind();
            nextImageButton.disableProperty().unbind();
            updateNavigationButtons(); // Устанавливаем их состояние на основе текущих данных
        });

        new Thread(scanTask).start();
    }

    private void processScannedFiles(List<TrackedFile> allFilesInDirectory) {
        List<TrackedFile> filteredList;
        if (activeTagFilters == null || activeTagFilters.isEmpty()) {
            filteredList = new ArrayList<>(allFilesInDirectory);
        } else {
            filteredList = allFilesInDirectory.stream()
                .filter(trackedFile -> {
                    if (trackedFile.getTags() == null || trackedFile.getTags().isEmpty()) {
                        return false;
                    }
                    return trackedFile.getTags().stream().anyMatch(activeTagFilters::contains);
                })
                .collect(Collectors.toList());
            logger.info("Filtered image list. Original: {}, Filtered: {}. Filter tags: {}",
                allFilesInDirectory.size(), filteredList.size(), activeTagFilters.stream().map(Tag::getName).collect(Collectors.toList()));
        }
        
        currentImageList.clear(); 
        currentImageList.addAll(filteredList); 

        currentImageIndexProperty.set(currentImageList.isEmpty() ? -1 : 0); 
        // this.currentImageIndex = currentImageIndexProperty.get(); // Синхронизируется

        if (!currentImageList.isEmpty()) {
            displayImageAtIndex(currentImageIndexProperty.get());
        } else {
            currentlyDisplayedFile = null;
            mainImageView.setImage(null);
            if (rightToolbarController != null) {
                rightToolbarController.setCurrentFile(null);
            }
            if (activeTagFilters != null && !activeTagFilters.isEmpty()) {
                 logger.info("No images found matching the current tag filter.");
            } else {
                 logger.info("No supported images found in directory.");
            }
        }
        updateNavigationButtons(); 
    }

    private void displayImageAtIndex(int index) {
        currentImageIndexProperty.set(index); 
        // this.currentImageIndex = index; // Синхронизируется

        if (index >= 0 && index < currentImageList.size()) {
            TrackedFile trackedFileToShow = currentImageList.get(index);
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
        
        // Обновление статус-бара
        if (currentlyDisplayedFile != null) {
            updateStatusBar("Displaying: " + currentlyDisplayedFile.getName() + " (" + (currentImageIndexProperty.get() + 1) + "/" + currentImageList.size() + ")");
        } else if (!currentImageList.isEmpty()){
             updateStatusBar(currentImageList.size() + " images loaded. Select an image.");
        } else if (currentOpenDirectory != null) {
            if (activeTagFilters.isEmpty()) {
                updateStatusBar("No images found in " + currentOpenDirectory.getName());
            } else {
                updateStatusBar("No images found in " + currentOpenDirectory.getName() + " matching filter.");
            }
        } else {
             updateStatusBar("Ready. Open a folder.");
        }
    }


    @FXML
    private void handlePreviousImage() {
        if (currentImageIndexProperty.get() > 0) {
            displayImageAtIndex(currentImageIndexProperty.get() - 1);
        }
    }

    @FXML
    private void handleNextImage() {
        if (currentImageIndexProperty.get() < currentImageList.size() - 1) {
            displayImageAtIndex(currentImageIndexProperty.get() + 1);
        }
    }

    private void updateNavigationButtons() {
        Platform.runLater(() -> {
            boolean isEmpty = currentImageList.isEmpty();
            int currentIndex = currentImageIndexProperty.get(); 

            // Если scanProgressIndicator НЕ видим (т.е. задача не запущена),
            // то состояние кнопок зависит от списка и индекса.
            // Если scanProgressIndicator ВИДИМ, то кнопки уже должны быть заблокированы
            // через биндинг к scanTask.runningProperty().or(...)
            // Поэтому дополнительная проверка scanProgressIndicator.isVisible() здесь не всегда нужна,
            // если биндинги установлены правильно и не отвязываются без необходимости.
            // Но для большей надежности, особенно после отвязки в setOnFailed, можно оставить:
            if (scanProgressIndicator.isVisible()) {
                previousImageButton.setDisable(true);
                nextImageButton.setDisable(true);
            } else {
                previousImageButton.setDisable(isEmpty || currentIndex <= 0);
                nextImageButton.setDisable(isEmpty || currentIndex >= currentImageList.size() - 1);
            }
        });
    }

    @FXML
    private void handleExit() {
        Platform.exit();
    }
}