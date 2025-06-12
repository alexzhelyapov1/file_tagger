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
    private LeftToolbarController leftToolbarController;
    private TrackedFile currentlyDisplayedFile;

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

        loadLeftToolbar(); // <<--- ДОБАВИТЬ ВЫЗОВ
        loadRightToolbar();
        
        if (rightToolbarController != null) {
            rightToolbarController.setServices(this.tagService, this.trackedFileService);
        }
        if (leftToolbarController != null) { // <<--- ДОБАВИТЬ БЛОК
            leftToolbarController.setTagService(this.tagService);
            leftToolbarController.setMainViewController(this);
        }
    }

    private void loadLeftToolbar() { // <<--- ДОБАВИТЬ ЭТОТ МЕТОД
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/imagetagger/fxml/LeftToolbar.fxml"));
            Parent leftToolbarNode = loader.load();
            leftToolbarController = loader.getController();
            // leftToolbarController.setMainViewController(this); // Уже делается в initialize
            // leftToolbarController.setTagService(this.tagService); // Уже делается в initialize
            
            rootPane.setLeft(leftToolbarNode);
            logger.info("Left toolbar loaded successfully.");
        } catch (Exception e) {
            logger.error("Failed to load LeftToolbar.fxml", e);
        }
    }

    public void applyTagFilter(Set<Tag> filterTags) {
        logger.info("Tag filter received in MainViewController: {}", filterTags.stream().map(Tag::getName).collect(Collectors.toList()));
        // Здесь будет логика применения фильтра
        // 1. Сохранить текущие filterTags
        // 2. Если currentImageList не пуст (т.е. папка уже была открыта),
        //    перезагрузить или отфильтровать currentImageList на основе новой папки И этих тегов.
        //    Для простоты, можно просто перезагрузить из текущей открытой папки с новым фильтром.
        //    Или, если у нас есть File currentSelectedDirectory, использовать его.

        // Пока что просто выведем лог и сохраним фильтр для будущего использования
        this.activeTagFilters = filterTags; // Нужно будет объявить поле private Set<Tag> activeTagFilters;

        // Если папка уже открыта, нужно перезагрузить список изображений с учетом фильтра
        if (this.currentOpenDirectory != null) { // Нужно будет объявить поле private File currentOpenDirectory;
            loadImagesFromDirectory(this.currentOpenDirectory); // Этот метод нужно будет модифицировать
        } else {
            // Если папка не открыта, фильтр применится при следующем открытии папки
            logger.debug("No directory open, filter will be applied on next folder open.");
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
        
        // Обновляем список тегов для фильтрации на левой панели
        if (leftToolbarController != null) {
            leftToolbarController.refreshAvailableTags();
        }

        // Обновляем текущий активный фильтр, если удаленный тег был в нем
        if (activeTagFilters != null && activeTagFilters.contains(deletedTag)) {
            activeTagFilters.remove(deletedTag);
            // Если папка открыта, перезагружаем/перефильтровываем список
            if (this.currentOpenDirectory != null) {
                loadImagesFromDirectory(this.currentOpenDirectory);
            }
        }
        
        // Обновляем теги текущего отображаемого файла (это уже делается в RightToolbarController,
        // но для согласованности можно и здесь)
        if (currentlyDisplayedFile != null && currentlyDisplayedFile.getTags().contains(deletedTag)) {
            currentlyDisplayedFile.removeTag(deletedTag);
             if (rightToolbarController != null) {
                 // RightToolbarController сам обновит свой список currentImageTagsList
                 // и возможно вызовет setCurrentFile, если это необходимо
                 rightToolbarController.refreshTagLists(); // Обновит и "все теги" и "теги картинки"
             }
        }
    }

    public void refreshAllTagViews() {
        if (rightToolbarController != null) {
            rightToolbarController.refreshTagLists(); // Обновляет теги на правой панели
        }
        if (leftToolbarController != null) {
            leftToolbarController.refreshAvailableTags(); // Обновляет теги на левой панели
        }
        // Если текущий файл отображается, обновить его теги (на правой панели это уже должно было произойти)
        if (currentlyDisplayedFile != null && rightToolbarController != null) {
             Optional<TrackedFile> freshFileOpt = trackedFileService.findByPathWithTags(currentlyDisplayedFile.getAbsolutePath());
             freshFileOpt.ifPresent(file -> {
                 currentlyDisplayedFile = file; 
                 rightToolbarController.setCurrentFile(currentlyDisplayedFile);
             });
        }
        // Переприменить фильтр, если он активен и открыта папка
        if (currentOpenDirectory != null && activeTagFilters != null && !activeTagFilters.isEmpty()) {
            loadImagesFromDirectory(currentOpenDirectory);
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
            this.currentOpenDirectory = selectedDirectory; // Сохраняем выбранную папку
            loadImagesFromDirectory(selectedDirectory);
        } else {
            logger.info("No folder selected.");
        }
    }

    private void loadImagesFromDirectory(File directory) {
        // Логика получения списка файлов от FileScannerService должна остаться прежней
        // FileScannerService по-прежнему возвращает ВСЕ поддерживаемые файлы из папки
        List<TrackedFile> allFilesInDirectory = fileScannerService.scanDirectoryForImages(directory);

        List<TrackedFile> filteredList;
        if (activeTagFilters == null || activeTagFilters.isEmpty()) {
            filteredList = new ArrayList<>(allFilesInDirectory); // Нет фильтра, берем все
        } else {
            // Фильтруем список allFilesInDirectory
            // Нам нужен метод в TrackedFileService для получения файлов по тегам,
            // но т.к. мы уже получили все файлы, можем отфильтровать их локально
            // или сделать более сложный запрос в DAO, если файлов очень много.
            // Пока фильтруем локально (OR-логика: файл должен иметь хотя бы один из тегов фильтра)
            filteredList = allFilesInDirectory.stream()
                .filter(trackedFile -> {
                    if (trackedFile.getTags() == null || trackedFile.getTags().isEmpty()) {
                        return false; // Если у файла нет тегов, он не пройдет фильтр с тегами
                    }
                    // Проверяем, есть ли пересечение между тегами файла и тегами фильтра
                    return trackedFile.getTags().stream().anyMatch(activeTagFilters::contains);
                })
                .collect(Collectors.toList());
            logger.info("Filtered image list. Original: {}, Filtered: {}. Filter tags: {}",
                allFilesInDirectory.size(), filteredList.size(), activeTagFilters.stream().map(Tag::getName).collect(Collectors.toList()));
        }
        
        currentImageList = filteredList; // Обновляем основной список изображений

        if (!currentImageList.isEmpty()) {
            currentImageIndex = 0;
            displayImageAtIndex(currentImageIndex);
        } else {
            currentImageIndex = -1;
            currentlyDisplayedFile = null;
            mainImageView.setImage(null);
            if (rightToolbarController != null) {
                rightToolbarController.setCurrentFile(null);
            }
            updateNavigationButtons();
            if (activeTagFilters != null && !activeTagFilters.isEmpty()) {
                 logger.info("No images found in directory {} matching the current tag filter.", directory.getAbsolutePath());
                 // Можно показать сообщение пользователю, что по фильтру ничего нет
            } else {
                 logger.info("No supported images found in directory: {}", directory.getAbsolutePath());
            }
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