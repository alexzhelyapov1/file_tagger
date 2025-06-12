package com.example.imagetagger.ui.controller;

import com.example.imagetagger.core.model.Tag;
import com.example.imagetagger.core.service.TagService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class LeftToolbarController {

    private static final Logger logger = LoggerFactory.getLogger(LeftToolbarController.class);

    @FXML private ListView<Tag> tagFilterListView;
    @FXML private Button applyFilterButton;
    @FXML private Button clearFilterButton;

    private TagService tagService;
    private MainViewController mainViewController; // Ссылка на главный контроллер

    private final ObservableList<Tag> allTagsForFiltering = FXCollections.observableArrayList();

    public void setMainViewController(MainViewController mainViewController) {
        this.mainViewController = mainViewController;
    }

    public void setTagService(TagService tagService) {
        this.tagService = tagService;
        loadTagsForFiltering(); // Загружаем теги после установки сервиса
    }

    @FXML
    public void initialize() {
        logger.info("LeftToolbarController initialized.");

        // Устанавливаем режим множественного выбора для ListView
        tagFilterListView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        tagFilterListView.setItems(allTagsForFiltering);

        // Инициализация сервиса (если не будет установлен извне через setTagService)
        if (this.tagService == null) {
            this.tagService = new TagService();
            loadTagsForFiltering();
        }
        
        // Кнопка "Применить фильтр" изначально может быть заблокирована или всегда активна
        // applyFilterButton.disableProperty().bind(Bindings.isEmpty(tagFilterListView.getSelectionModel().getSelectedItems()));
    }

    private void loadTagsForFiltering() {
        if (tagService != null) {
            List<Tag> tags = tagService.getAllTags();
            allTagsForFiltering.setAll(tags);
            FXCollections.sort(allTagsForFiltering, (t1, t2) -> t1.getName().compareToIgnoreCase(t2.getName()));
            logger.debug("Loaded {} tags into filter list.", allTagsForFiltering.size());
        } else {
            logger.warn("TagService not available in LeftToolbarController. Cannot load tags for filtering.");
        }
    }

    @FXML
    private void handleApplyFilter() {
        ObservableList<Tag> selectedTags = tagFilterListView.getSelectionModel().getSelectedItems();
        Set<Tag> filterTags = new HashSet<>(selectedTags);

        if (mainViewController != null) {
            logger.info("Applying filter with tags: {}", filterTags.stream().map(Tag::getName).collect(Collectors.toList()));
            mainViewController.applyTagFilter(filterTags);
        } else {
            logger.warn("MainViewController is null. Cannot apply filter.");
        }
    }

    @FXML
    private void handleClearFilter() {
        tagFilterListView.getSelectionModel().clearSelection();
        if (mainViewController != null) {
            logger.info("Clearing tag filter.");
            mainViewController.applyTagFilter(new HashSet<>()); // Передаем пустой сет для сброса фильтра
        } else {
            logger.warn("MainViewController is null. Cannot clear filter.");
        }
    }

    /**
     * Метод для обновления списка тегов, доступных для фильтрации.
     * Может вызываться, например, из MainViewController, если глобальный список тегов изменился.
     */
    public void refreshAvailableTags() {
        Set<Tag> previouslySelectedTags = new HashSet<>(tagFilterListView.getSelectionModel().getSelectedItems());
        loadTagsForFiltering();
        // Попытка восстановить выбор, если теги все еще существуют
        for (Tag tag : previouslySelectedTags) {
            if (allTagsForFiltering.contains(tag)) {
                tagFilterListView.getSelectionModel().select(tag);
            }
        }
    }
}