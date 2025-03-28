package mokema.asignment2;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.embed.swing.SwingFXUtils;
import javax.imageio.ImageIO;
import java.io.File;
import java.util.Stack;
import javafx.util.Duration;
import javafx.scene.layout.VBox;
import javafx.geometry.Insets;

public class HelloApplication extends Application {

    // Constants
    private static final int CANVAS_WIDTH = 900;
    private static final int CANVAS_HEIGHT = 600;
    private static final String[] SAVE_FORMATS = {"PNG", "JPG", "BMP", "GIF"};
    private static final String[] AUDIO_FORMATS = {"*.mp3", "*.wav"};
    private static final String[] VIDEO_FORMATS = {"*.mp4", "*.avi", "*.mov"};
    private static final String[] FONT_FAMILIES = {"Arial", "Verdana", "Times New Roman", "Courier New"};

    // Drawing Tools
    private Canvas canvas;
    private GraphicsContext gc;
    private Stack<Image> undoStack = new Stack<>();
    private Stack<Image> redoStack = new Stack<>();

    // UI Components
    private ColorPicker strokeColorPicker = new ColorPicker(Color.BLACK);
    private ColorPicker fillColorPicker = new ColorPicker(Color.TRANSPARENT);
    private ComboBox<String> toolSelector = new ComboBox<>();
    private ComboBox<String> fontSelector = new ComboBox<>();
    private Slider sizeSlider = new Slider(1, 50, 5);
    private TextField textInput = new TextField("Type here");

    // Media Components
    private MediaPlayer mediaPlayer;
    private MediaView mediaView;

    // Drawing State
    private String currentTool = "Draw";
    private double startX, startY;
    private boolean isDrawing = false;
    private Image currentImage;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        initializeCanvas();
        setupToolSelector();
        setupFontSelector();
        setupSizeSlider();

        BorderPane root = new BorderPane();
        root.setCenter(canvas);
        root.setTop(createToolbar());

        Scene scene = new Scene(root, 1000, 700);
        try {
            scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
        } catch (Exception e) {
            System.out.println("Stylesheet not found, using default styling");
        }

        primaryStage.setTitle("Complete Digital Whiteboard");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void initializeCanvas() {
        canvas = new Canvas(CANVAS_WIDTH, CANVAS_HEIGHT);
        gc = canvas.getGraphicsContext2D();
        clearCanvas();
        setupMouseHandlers();
    }

    private HBox createToolbar() {
        Button undoBtn = createButton("Undo", this::undo);
        Button redoBtn = createButton("Redo", this::redo);
        Button clearBtn = createButton("Clear", this::clearCanvas);
        Button saveBtn = createButton("Save", this::saveCanvas);

        HBox toolbar = new HBox(10);
        toolbar.setPadding(new Insets(10));
        toolbar.getChildren().addAll(
                toolSelector,
                new Label("Stroke:"), strokeColorPicker,
                new Label("Fill:"), fillColorPicker,
                new Label("Size:"), sizeSlider,
                fontSelector, textInput,
                undoBtn, redoBtn, clearBtn, saveBtn
        );
        return toolbar;
    }

    private Button createButton(String text, Runnable action) {
        Button button = new Button(text);
        button.setOnAction(e -> action.run());
        return button;
    }

    private void setupToolSelector() {
        toolSelector.getItems().addAll(
                "Draw", "Line", "Rectangle", "Circle",
                "Text", "Image", "Audio", "Video", "Eraser"
        );
        toolSelector.setValue("Draw");
        toolSelector.setOnAction(e -> currentTool = toolSelector.getValue());
    }

    private void setupFontSelector() {
        fontSelector.getItems().addAll(FONT_FAMILIES);
        fontSelector.setValue("Arial");
    }

    private void setupSizeSlider() {
        sizeSlider.setShowTickLabels(true);
        sizeSlider.setShowTickMarks(true);
    }

    private void setupMouseHandlers() {
        canvas.setOnMousePressed(this::handleMousePressed);
        canvas.setOnMouseDragged(this::handleMouseDragged);
        canvas.setOnMouseReleased(this::handleMouseReleased);
    }

    private void handleMousePressed(MouseEvent e) {
        saveState();
        startX = e.getX();
        startY = e.getY();

        gc.setStroke(strokeColorPicker.getValue());
        gc.setFill(fillColorPicker.getValue());
        gc.setLineWidth(sizeSlider.getValue());

        switch (currentTool) {
            case "Draw":
                gc.beginPath();
                gc.moveTo(startX, startY);
                isDrawing = true;
                break;
            case "Eraser":
                eraseAt(startX, startY);
                break;
        }
    }

    private void handleMouseDragged(MouseEvent e) {
        double x = e.getX();
        double y = e.getY();

        switch (currentTool) {
            case "Draw":
                gc.lineTo(x, y);
                gc.stroke();
                break;
            case "Line":
                redrawCanvas();
                gc.strokeLine(startX, startY, x, y);
                break;
            case "Rectangle":
                redrawCanvas();
                drawRectangle(startX, startY, x, y);
                break;
            case "Circle":
                redrawCanvas();
                drawCircle(startX, startY, x, y);
                break;
            case "Eraser":
                eraseAt(x, y);
                break;
        }
    }

    private void handleMouseReleased(MouseEvent e) {
        double x = e.getX();
        double y = e.getY();

        switch (currentTool) {
            case "Text":
                drawText(textInput.getText(), x, y);
                break;
            case "Image":
                addImage();
                break;
            case "Audio":
                addAudio();
                break;
            case "Video":
                addVideo();
                break;
        }

        if (isDrawing) {
            saveState();
            isDrawing = false;
        }
    }

    private void drawRectangle(double x1, double y1, double x2, double y2) {
        double width = x2 - x1;
        double height = y2 - y1;

        if (fillColorPicker.getValue() != Color.TRANSPARENT) {
            gc.fillRect(x1, y1, width, height);
        }
        gc.strokeRect(x1, y1, width, height);
    }

    private void drawCircle(double x1, double y1, double x2, double y2) {
        double radius = Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));

        if (fillColorPicker.getValue() != Color.TRANSPARENT) {
            gc.fillOval(x1 - radius, y1 - radius, radius * 2, radius * 2);
        }
        gc.strokeOval(x1 - radius, y1 - radius, radius * 2, radius * 2);
    }

    private void drawText(String text, double x, double y) {
        gc.setFont(Font.font(fontSelector.getValue(), sizeSlider.getValue() * 3));
        gc.fillText(text, x, y);
    }

    private void eraseAt(double x, double y) {
        double size = sizeSlider.getValue() * 2;
        gc.clearRect(x - size/2, y - size/2, size, size);
    }

    private void addImage() {
        File file = showFileChooser("Select Image", "Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp");
        if (file != null) {
            currentImage = new Image(file.toURI().toString());
            gc.drawImage(currentImage, startX, startY,
                    currentImage.getWidth(), currentImage.getHeight());
            saveState();
        }
    }

    private void addAudio() {
        File file = showFileChooser("Select Audio", "Audio Files", AUDIO_FORMATS);
        if (file != null) {
            createMediaPlayer(file.toURI().toString(), "Audio Player", false);
        }
    }

    private void addVideo() {
        File file = showFileChooser("Select Video", "Video Files", VIDEO_FORMATS);
        if (file != null) {
            createMediaPlayer(file.toURI().toString(), "Video Player", true);
        }
    }

    private File showFileChooser(String title, String description, String... extensions) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(title);
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(description, extensions)
        );
        return fileChooser.showOpenDialog(null);
    }

    private void createMediaPlayer(String mediaURI, String title, boolean isVideo) {
        try {
            Media media = new Media(mediaURI);
            mediaPlayer = new MediaPlayer(media);

            Stage mediaStage = new Stage();
            mediaStage.setTitle(title);

            // Create control buttons
            Button playBtn = createMediaButton("▶", () -> mediaPlayer.play());
            Button pauseBtn = createMediaButton("⏸", () -> mediaPlayer.pause());
            Button stopBtn = createMediaButton("⏹", () -> {
                mediaPlayer.stop();
                mediaPlayer.seek(mediaPlayer.getStartTime());
            });

            // Volume control
            Slider volumeSlider = new Slider(0, 1, 0.5);
            volumeSlider.valueProperty().bindBidirectional(mediaPlayer.volumeProperty());

            // Time slider and labels
            Slider timeSlider = new Slider();
            Label timeLabel = new Label("00:00 / 00:00");
            Label currentTimeLabel = new Label("00:00");
            Label totalTimeLabel = new Label("00:00");

            // Set up time slider behavior
            mediaPlayer.currentTimeProperty().addListener((obs, oldVal, newVal) -> {
                if (!timeSlider.isValueChanging()) {
                    timeSlider.setValue(newVal.toSeconds());
                }
                currentTimeLabel.setText(formatTime(newVal));
            });

            mediaPlayer.setOnReady(() -> {
                Duration totalDuration = media.getDuration();
                timeSlider.setMax(totalDuration.toSeconds());
                totalTimeLabel.setText(formatTime(totalDuration));
            });

            timeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
                if (timeSlider.isValueChanging()) {
                    mediaPlayer.seek(Duration.seconds(newVal.doubleValue()));
                }
            });

            // Layout for time controls
            HBox timeBox = new HBox(5, currentTimeLabel, timeSlider, totalTimeLabel);
            timeBox.setAlignment(Pos.CENTER);

            // Main controls layout
            HBox controls = new HBox(10, playBtn, pauseBtn, stopBtn,
                    new Label("Volume:"), volumeSlider);
            controls.setAlignment(Pos.CENTER);
            controls.setPadding(new Insets(10));

            // Main container
            BorderPane root = new BorderPane();

            if (isVideo) {
                mediaView = new MediaView(mediaPlayer);
                mediaView.setFitWidth(640);
                root.setCenter(mediaView);
            } else {
                // For audio, show a visualization or just the controls
                root.setCenter(new Label("Now Playing: " + title));
            }

            VBox bottomPanel = new VBox(10, timeBox, controls);
            bottomPanel.setPadding(new Insets(10));
            root.setBottom(bottomPanel);

            mediaStage.setScene(new Scene(root, isVideo ? 640 : 400, isVideo ? 480 : 150));
            mediaStage.show();

            mediaStage.setOnCloseRequest(e -> mediaPlayer.dispose());
        } catch (Exception e) {
            showError("Media Error", "Could not load media: " + e.getMessage());
        }
    }

    private String formatTime(Duration duration) {
        int minutes = (int) duration.toMinutes();
        int seconds = (int) duration.toSeconds() % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private Button createMediaButton(String text, Runnable action) {
        Button button = new Button(text);
        button.setOnAction(e -> action.run());
        return button;
    }

    private void saveState() {
        undoStack.push(canvas.snapshot(null, null));
        redoStack.clear();
    }

    private void undo() {
        if (undoStack.size() > 1) {
            redoStack.push(undoStack.pop());
            redrawCanvas();
        }
    }

    private void redo() {
        if (!redoStack.isEmpty()) {
            undoStack.push(redoStack.pop());
            redrawCanvas();
        }
    }

    private void redrawCanvas() {
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        if (!undoStack.isEmpty()) {
            gc.drawImage(undoStack.peek(), 0, 0);
        }
    }

    private void clearCanvas() {
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        undoStack.clear();
        redoStack.clear();
        saveState();
    }

    private void saveCanvas() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Canvas");

        for (String format : SAVE_FORMATS) {
            fileChooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter(format, "*." + format.toLowerCase())
            );
        }

        File file = fileChooser.showSaveDialog(null);
        if (file != null) {
            try {
                String extension = fileChooser.getSelectedExtensionFilter()
                        .getDescription().split("\\(")[0].trim();

                ImageIO.write(
                        SwingFXUtils.fromFXImage(canvas.snapshot(null, null), null),
                        extension,
                        file
                );
            } catch (Exception e) {
                showError("Save Error", "Could not save file: " + e.getMessage());
            }
        }
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}