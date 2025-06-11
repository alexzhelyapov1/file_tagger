package com.example.imagetagger;

import com.example.imagetagger.persistence.DatabaseManager;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Objects;

public class MainApplication extends Application {

    private static final Logger logger = LoggerFactory.getLogger(MainApplication.class);

    @Override
    public void init() throws Exception {
        super.init();
        logger.info("Initializing application...");
        try {
            DatabaseManager.initializeDatabase();
            logger.info("Database initialized successfully.");
        } catch (Exception e) {
            logger.error("Failed to initialize database", e);
            // Решаем, что делать дальше: либо выбрасываем исключение, чтобы приложение не запустилось,
            // либо пытаемся работать без БД (что для нашего приложения бессмысленно).
            // Для начала, просто залогируем и продолжим, чтобы увидеть окно.
            // В продакшене лучше показать пользователю ошибку и закрыться.
        }
    }

    @Override
    public void start(Stage primaryStage) {
        logger.info("Starting JavaFX application...");
        try {
            FXMLLoader loader = new FXMLLoader(Objects.requireNonNull(getClass().getResource("/com/example/imagetagger/fxml/MainView.fxml")));
            Parent root = loader.load();

            Scene scene = new Scene(root, 1024, 768); // Зададим начальные размеры окна
            primaryStage.setTitle("Image Tagger");
            primaryStage.setScene(scene);
            primaryStage.show();
            logger.info("Application window shown.");

        } catch (IOException e) {
            logger.error("Failed to load MainView.fxml or initialize controller.", e);
            // Показать пользователю сообщение об ошибке
            // Platform.exit(); // Закрыть приложение, если основной FXML не загрузился
        } catch (NullPointerException e) {
            logger.error("Failed to find MainView.fxml. Check the path.", e);
        }
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        logger.info("Application stopping.");
        // Здесь можно добавить логику очистки ресурсов, если необходимо
    }

    public static void main(String[] args) {
        launch(args);
    }
}