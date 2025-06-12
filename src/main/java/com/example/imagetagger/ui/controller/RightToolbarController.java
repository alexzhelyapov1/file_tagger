package com.example.imagetagger.ui.controller;

import com.example.imagetagger.core.model.Tag;
import com.example.imagetagger.core.model.TrackedFile;
import com.example.imagetagger.core.service.TagService;
import com.example.imagetagger.core.service.TrackedFileService; // Добавить импорт

import javafx.beans.binding.BooleanBinding;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;

import java.util.Optional;
import java.util.stream.Collectors;

public class RightToolbarController {

    private static final Logger logger = LoggerFactory.getLogger(RightToolbarController.class);

    @FXML private ListView<Tag> currentImageTagsListView;
    @FXML private Button removeTagFromImageButton;
    @FXML private ListView<Tag> allTagsListView;
    @FXML private TextField newTagTextField;
    @FXML private Button addTagButton;
    @FXML private Button deleteTagButton;

    private TagService tagService;
    private TrackedFileService trackedFileService;

    private final ObservableList<Tag> allTagsObservableList = FXCollections.observableArrayList();
    private final ObservableList<Tag> currentImageTagsObservableList = FXCollections.observableArrayList();

    private MainViewController mainViewController;
    private TrackedFile currentTrackedFile;

    public void setMainViewController(MainViewController mainViewController) {
        this.mainViewController = mainViewController;
    }

    public void setServices(TagService tagService, TrackedFileService trackedFileService) {
        this.tagService = tagService;
        this.trackedFileService = trackedFileService;
        loadAllTags();
    }


    @FXML
    public void initialize() {
        logger.info("RightToolbarController initialized.");
        if (this.tagService == null) {
            this.tagService = new TagService();
        }
        if (this.trackedFileService == null) {
            this.trackedFileService = new TrackedFileService();
        }

        allTagsListView.setItems(allTagsObservableList);
        currentImageTagsListView.setItems(currentImageTagsObservableList);

        // Кнопка удаления глобального тега
        deleteTagButton.disableProperty().bind(allTagsListView.getSelectionModel().selectedItemProperty().isNull());

        // --- ИСПРАВЛЕННЫЙ БЛОК ---
        // Кнопка удаления тега с изображения
        // Должна быть заблокирована, если ничего не выбрано ИЛИ если список тегов изображения пуст.
        BooleanBinding noTagSelectedInImageTags = currentImageTagsListView.getSelectionModel().selectedItemProperty().isNull();
        BooleanBinding imageTagsListIsEmpty = Bindings.isEmpty(currentImageTagsObservableList);

        removeTagFromImageButton.disableProperty().bind(noTagSelectedInImageTags.or(imageTagsListIsEmpty));
        // --- КОНЕЦ ИСПРАВЛЕННОГО БЛОКА ---

        allTagsListView.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                Tag selectedTag = allTagsListView.getSelectionModel().getSelectedItem();
                if (selectedTag != null && currentTrackedFile != null) {
                    handleAddTagToCurrentImage(selectedTag);
                }
            }
        });

        newTagTextField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                handleAddGlobalTag();
            }
        });
        
        loadAllTags();
    }

    /**
     * Вызывается из MainViewController при смене текущего изображения.
     * @param trackedFile Новый текущий файл или null, если изображение очищено.
     */
    public void setCurrentFile(TrackedFile trackedFile) {
        this.currentTrackedFile = trackedFile;
        if (this.currentTrackedFile != null && this.currentTrackedFile.getId() > 0) {
            // Загружаем теги для этого файла. TrackedFileService должен уметь это делать.
            // Предположим, что TrackedFile, переданный сюда, уже содержит свои теги
            // (загруженные в TrackedFileService.getOrCreateTrackedFile)
            currentImageTagsObservableList.setAll(this.currentTrackedFile.getTags());
            logger.info("Displaying {} tags for file: {}", currentImageTagsObservableList.size(), this.currentTrackedFile.getAbsolutePath());
        } else {
            currentImageTagsObservableList.clear();
            logger.info("Current file is null or not persisted, clearing image tags list.");
        }
        // Обновляем доступность кнопки добавления тега к изображению
        // (хотя мы добавляем двойным кликом, но если бы была кнопка)
    }

    private void loadAllTags() {
        if (tagService == null) {
            logger.warn("TagService not initialized in RightToolbarController. Cannot load tags.");
            return;
        }
        allTagsObservableList.setAll(tagService.getAllTags());
        FXCollections.sort(allTagsObservableList, (t1, t2) -> t1.getName().compareToIgnoreCase(t2.getName()));
        logger.debug("Loaded {} tags into 'All Tags' list.", allTagsObservableList.size());
    }

    @FXML
    private void handleAddGlobalTag() { // Переименовали из handleAddTag
        String tagName = newTagTextField.getText().trim();
        if (tagName.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Input Error", "Tag name cannot be empty.");
            return;
        }

        Optional<Tag> createdTagOpt = tagService.createOrGetTag(tagName);
        if (createdTagOpt.isPresent()) {
            Tag tag = createdTagOpt.get();
            if (!allTagsObservableList.contains(tag)) {
                 allTagsObservableList.add(tag);
                 FXCollections.sort(allTagsObservableList, (t1, t2) -> t1.getName().compareToIgnoreCase(t2.getName()));
            }
            allTagsListView.getSelectionModel().select(tag);
            allTagsListView.scrollTo(tag); // Прокручиваем к добавленному/выбранному тегу
            newTagTextField.clear();
            logger.info("Global tag '{}' processed.", tagName);
        } else {
            showAlert(Alert.AlertType.ERROR, "Database Error", "Failed to create or retrieve global tag: " + tagName);
        }
    }

    @FXML
    private void handleDeleteSelectedTag() {
        Tag selectedTag = allTagsListView.getSelectionModel().getSelectedItem();
        if (selectedTag == null) {
            showAlert(Alert.AlertType.WARNING, "Selection Error", "No global tag selected to delete.");
            return;
        }

        // Запрос подтверждения перед удалением
        Alert confirmationDialog = new Alert(Alert.AlertType.CONFIRMATION);
        confirmationDialog.setTitle("Confirm Deletion");
        confirmationDialog.setHeaderText("Delete Tag '" + selectedTag.getName() + "'?");
        confirmationDialog.setContentText("This will remove the tag from all files and delete it permanently. Are you sure?");
        Optional<ButtonType> result = confirmationDialog.showAndWait();

        if (result.isPresent() && result.get() == ButtonType.OK) {
            boolean deleted = tagService.deleteTag(selectedTag.getId());
            if (deleted) {
                allTagsObservableList.remove(selectedTag);
                // Также нужно удалить этот тег из списка тегов текущего изображения, если он там есть
                currentImageTagsObservableList.remove(selectedTag);
                logger.info("Global tag '{}' (ID: {}) deleted.", selectedTag.getName(), selectedTag.getId());
                // Если mainViewController существует, можно уведомить его,
                // чтобы он, например, обновил фильтры, если они основаны на тегах
                if (mainViewController != null) {
                    mainViewController.handleGlobalTagDeleted(selectedTag);
                }
            } else {
                showAlert(Alert.AlertType.ERROR, "Database Error", "Failed to delete global tag: " + selectedTag.getName());
            }
        }
    }

    private void handleAddTagToCurrentImage(Tag tagToAdd) {
        if (currentTrackedFile == null || currentTrackedFile.getId() <= 0) {
            showAlert(Alert.AlertType.WARNING, "No Image", "No image is currently open or image is not saved.");
            return;
        }
        if (tagToAdd == null || tagToAdd.getId() <= 0) {
            showAlert(Alert.AlertType.ERROR, "Invalid Tag", "Cannot add an invalid or unsaved tag.");
            return;
        }

        // Проверяем, нет ли уже такого тега у файла
        if (currentTrackedFile.getTags().stream().anyMatch(t -> t.getId() == tagToAdd.getId())) {
            logger.debug("Tag '{}' already assigned to file '{}'. Skipping.", tagToAdd.getName(), currentTrackedFile.getName());
            return; // Тег уже есть
        }
        
        trackedFileService.addTagToFile(currentTrackedFile, tagToAdd);
        // currentTrackedFile.getTags() должен был обновиться внутри addTagToFile,
        // но для ObservableList нужно явное добавление, если не весь список переустанавливается.
        currentImageTagsObservableList.add(tagToAdd);
        FXCollections.sort(currentImageTagsObservableList, (t1, t2) -> t1.getName().compareToIgnoreCase(t2.getName()));
        logger.info("Tag '{}' added to current image '{}'.", tagToAdd.getName(), currentTrackedFile.getName());
    }


    @FXML
    private void handleRemoveTagFromImage() {
        Tag selectedTag = currentImageTagsListView.getSelectionModel().getSelectedItem();
        if (selectedTag == null) {
            showAlert(Alert.AlertType.WARNING, "Selection Error", "No tag selected to remove from image.");
            return;
        }
        if (currentTrackedFile == null || currentTrackedFile.getId() <= 0) {
            logger.warn("Cannot remove tag, current file is not valid.");
            return;
        }

        trackedFileService.removeTagFromFile(currentTrackedFile, selectedTag);
        // currentTrackedFile.getTags() должен был обновиться внутри removeTagFromFile.
        currentImageTagsObservableList.remove(selectedTag); // Обновляем UI
        logger.info("Tag '{}' removed from current image '{}'.", selectedTag.getName(), currentTrackedFile.getName());
    }


    private void showAlert(Alert.AlertType alertType, String title, String message) {
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public void refreshTagLists() {
        loadAllTags();
        // Перезагрузка тегов для текущего файла, если он есть
        if (currentTrackedFile != null && currentTrackedFile.getId() > 0) {
            // Может понадобиться перезагрузить сам объект currentTrackedFile из сервиса,
            // чтобы получить актуальные теги, если они изменились где-то еще.
            // Но обычно RightToolbarController сам управляет тегами текущего файла.
             Optional<TrackedFile> updatedFileOpt = trackedFileService.findByPathWithTags(currentTrackedFile.getAbsolutePath());
             updatedFileOpt.ifPresent(this::setCurrentFile);
        } else {
             currentImageTagsObservableList.clear();
        }
    }
}