package com.example.imagetagger.ui.controller;

import com.example.imagetagger.core.model.TrackedFile;
import com.example.imagetagger.core.service.FileScannerService;
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

public class MainViewController {

    private static final Logger logger = LoggerFactory.getLogger(MainViewController.class);

    @FXML private BorderPane rootPane;
    @FXML private ImageView mainImageView;
    @FXML private StackPane imageViewHolder; // Родительский контейнер для ImageView
    @FXML private Button previousImageButton;
    @FXML private Button nextImageButton;
    @FXML private MenuItem openFolderMenuItem; // Если нужно будет к нему обращаться

    private FileScannerService fileScannerService;
    private List<TrackedFile> currentImageList = new ArrayList<>();
    private int currentImageIndex = -1;

    @FXML
    public void initialize() {
        logger.info("MainViewController initialized.");
        this.fileScannerService = new FileScannerService();

        // Привязываем размер ImageView к размеру родительского StackPane
        // чтобы изображение корректно масштабировалось при изменении размера окна.
        mainImageView.fitWidthProperty().bind(imageViewHolder.widthProperty());
        mainImageView.fitHeightProperty().bind(imageViewHolder.heightProperty());
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
            updateNavigationButtons();
            logger.info("Loaded {} images from directory.", currentImageList.size());
        } else {
            currentImageIndex = -1;
            mainImageView.setImage(null); // Очистить изображение, если ничего не найдено
            updateNavigationButtons();
            logger.info("No supported images found in directory: {}", directory.getAbsolutePath());
            // Можно показать сообщение пользователю
        }
    }

    private void displayImageAtIndex(int index) {
        if (index >= 0 && index < currentImageList.size()) {
            TrackedFile trackedFile = currentImageList.get(index);
            logger.info("Displaying image: {}", trackedFile.getAbsolutePath());
            try (FileInputStream fis = new FileInputStream(trackedFile.getFile())) {
                Image image = new Image(fis);
                if (image.isError()) {
                    logger.error("Error loading image: {}. Exception: {}", trackedFile.getAbsolutePath(), image.getException());
                    mainImageView.setImage(null); // Показать плейсхолдер или ничего
                    // Можно также показать сообщение об ошибке пользователю
                } else {
                    mainImageView.setImage(image);
                }
            } catch (FileNotFoundException e) {
                logger.error("Image file not found: {}", trackedFile.getAbsolutePath(), e);
                mainImageView.setImage(null);
            } catch (Exception e) {
                logger.error("Failed to load image: {}", trackedFile.getAbsolutePath(), e);
                mainImageView.setImage(null);
            }
        } else {
            logger.warn("Attempted to display image at invalid index: {}", index);
            mainImageView.setImage(null);
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