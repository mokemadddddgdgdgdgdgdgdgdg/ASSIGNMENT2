// Package declaration for the application
package mokema.asignment2;

// Import necessary JavaFX and other libraries
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
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.embed.swing.SwingFXUtils;
import javafx.util.Duration;
import javax.imageio.ImageIO;
import java.io.File;
import java.util.Stack;

// Main application class extending JavaFX Application
public class HelloApplication extends Application {

    // Constants for canvas dimensions and file formats
    private static final int CANVAS_WIDTH = 900;
    private static final int CANVAS_HEIGHT = 600;
    private static final String[] SAVE_FORMATS = {"PNG", "JPG", "BMP", "GIF"};
    private static final String[] AUDIO_FORMATS = {"*.mp3", "*.wav"};
    private static final String[] VIDEO_FORMATS = {"*.mp4", "*.avi", "*.mov"};
    private static final String[] FONT_FAMILIES = {"Arial", "Verdana", "Times New Roman", "Courier New"};
    private static final double RESIZE_HANDLE_SIZE = 8;
    private static final double MIN_IMAGE_SIZE = 20;

    // Drawing components
    private Canvas canvas;
    private GraphicsContext gc;
    private Stack<Image> undoStack = new Stack<>();  // Stores states for undo functionality
    private Stack<Image> redoStack = new Stack<>();  // Stores states for redo functionality

    // UI controls
    private ColorPicker strokeColorPicker = new ColorPicker(Color.BLACK);
    private ColorPicker fillColorPicker = new ColorPicker(Color.TRANSPARENT);
    private ComboBox<String> toolSelector = new ComboBox<>();
    private ComboBox<String> fontSelector = new ComboBox<>();
    private Slider sizeSlider = new Slider(1, 50, 5);
    private TextField textInput = new TextField("Type here");

    // Media playback components
    private MediaPlayer mediaPlayer;
    private MediaView mediaView;

    // Drawing state tracking
    private String currentTool = "Draw";
    private double startX, startY;
    private boolean isDrawing = false;

    // Image manipulation state
    private Image currentImage;
    private double imageX, imageY;
    private double imageWidth, imageHeight;
    private boolean isDraggingImage = false;
    private boolean isResizingImage = false;
    private double dragStartX, dragStartY;
    private int resizeDirection = 0; // 0=none, 1=top, 2=right, 3=bottom, 4=left,
    // 5=top-left, 6=top-right, 7=bottom-right, 8=bottom-left

    // Main method to launch the application
    public static void main(String[] args) {
        launch(args);
    }

    // JavaFX start method - main entry point
    @Override
    public void start(Stage primaryStage) {
        initializeCanvas();
        setupToolSelector();
        setupFontSelector();
        setupSizeSlider();

        // Create main layout
        BorderPane root = new BorderPane();
        root.setCenter(canvas);
        root.setTop(createToolbar());

        // Set up scene with optional CSS styling
        Scene scene = new Scene(root, 1000, 700);
        try {
            scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
        } catch (Exception e) {
            System.out.println("Stylesheet not found, using default styling");
        }

        // Configure and show primary stage
        primaryStage.setTitle("Complete Digital Whiteboard");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    // Initialize the drawing canvas
    private void initializeCanvas() {
        canvas = new Canvas(CANVAS_WIDTH, CANVAS_HEIGHT);
        gc = canvas.getGraphicsContext2D();
        clearCanvas();
        setupMouseHandlers();
    }

    // Create the toolbar with drawing controls
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

    // Helper method to create styled buttons
    private Button createButton(String text, Runnable action) {
        Button button = new Button(text);
        button.setOnAction(e -> action.run());
        return button;
    }

    // Set up the tool selection dropdown
    private void setupToolSelector() {
        toolSelector.getItems().addAll(
                "Draw", "Line", "Rectangle", "Circle",
                "Text", "Image", "Audio", "Video", "Eraser"
        );
        toolSelector.setValue("Draw");
        toolSelector.setOnAction(e -> currentTool = toolSelector.getValue());
    }

    // Set up the font selection dropdown
    private void setupFontSelector() {
        fontSelector.getItems().addAll(FONT_FAMILIES);
        fontSelector.setValue("Arial");
    }

    // Configure the brush size slider
    private void setupSizeSlider() {
        sizeSlider.setShowTickLabels(true);
        sizeSlider.setShowTickMarks(true);
    }

    // Set up mouse event handlers for the canvas
    private void setupMouseHandlers() {
        canvas.setOnMousePressed(this::handleMousePressed);
        canvas.setOnMouseDragged(this::handleMouseDragged);
        canvas.setOnMouseReleased(this::handleMouseReleased);
    }

    // Handle mouse press events
    private void handleMousePressed(MouseEvent e) {
        saveState();
        startX = e.getX();
        startY = e.getY();

        // Special handling for image manipulation
        if (currentTool.equals("Image") && currentImage != null) {
            double mouseX = e.getX();
            double mouseY = e.getY();

            if (mouseX >= imageX && mouseX <= imageX + imageWidth &&
                    mouseY >= imageY && mouseY <= imageY + imageHeight) {

                resizeDirection = getResizeDirection(mouseX, mouseY);

                if (resizeDirection == 0) {
                    isDraggingImage = true;
                    dragStartX = mouseX - imageX;
                    dragStartY = mouseY - imageY;
                } else {
                    isResizingImage = true;
                    dragStartX = mouseX;
                    dragStartY = mouseY;
                }
                e.consume();
                return;
            }
        }

        // Set drawing properties based on current selections
        gc.setStroke(strokeColorPicker.getValue());
        gc.setFill(fillColorPicker.getValue());
        gc.setLineWidth(sizeSlider.getValue());

        // Tool-specific press handling
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

    // Determine which part of an image is being interacted with
    private int getResizeDirection(double mouseX, double mouseY) {
        boolean nearLeft = Math.abs(mouseX - imageX) < RESIZE_HANDLE_SIZE;
        boolean nearRight = Math.abs(mouseX - (imageX + imageWidth)) < RESIZE_HANDLE_SIZE;
        boolean nearTop = Math.abs(mouseY - imageY) < RESIZE_HANDLE_SIZE;
        boolean nearBottom = Math.abs(mouseY - (imageY + imageHeight)) < RESIZE_HANDLE_SIZE;

        if (nearTop && nearLeft) return 5;
        if (nearTop && nearRight) return 6;
        if (nearBottom && nearRight) return 7;
        if (nearBottom && nearLeft) return 8;
        if (nearTop) return 1;
        if (nearRight) return 2;
        if (nearBottom) return 3;
        if (nearLeft) return 4;

        return 0;
    }

    // Handle mouse drag events
    private void handleMouseDragged(MouseEvent e) {
        double x = e.getX();
        double y = e.getY();

        // Image manipulation handling
        if (currentTool.equals("Image") && currentImage != null) {
            if (isDraggingImage) {
                imageX = x - dragStartX;
                imageY = y - dragStartY;
            }
            else if (isResizingImage) {
                double deltaX = x - dragStartX;
                double deltaY = y - dragStartY;

                // Handle resizing from different directions
                switch (resizeDirection) {
                    case 1: // Top
                        imageHeight -= deltaY;
                        imageY += deltaY;
                        break;
                    case 2: // Right
                        imageWidth += deltaX;
                        break;
                    case 3: // Bottom
                        imageHeight += deltaY;
                        break;
                    case 4: // Left
                        imageWidth -= deltaX;
                        imageX += deltaX;
                        break;
                    case 5: // Top-left
                        imageWidth -= deltaX;
                        imageX += deltaX;
                        imageHeight -= deltaY;
                        imageY += deltaY;
                        break;
                    case 6: // Top-right
                        imageWidth += deltaX;
                        imageHeight -= deltaY;
                        imageY += deltaY;
                        break;
                    case 7: // Bottom-right
                        imageWidth += deltaX;
                        imageHeight += deltaY;
                        break;
                    case 8: // Bottom-left
                        imageWidth -= deltaX;
                        imageX += deltaX;
                        imageHeight += deltaY;
                        break;
                }

                // Enforce minimum size constraints
                if (imageWidth < MIN_IMAGE_SIZE) imageWidth = MIN_IMAGE_SIZE;
                if (imageHeight < MIN_IMAGE_SIZE) imageHeight = MIN_IMAGE_SIZE;

                dragStartX = x;
                dragStartY = y;
            }
            // Redraw canvas with updated image
            redrawCanvas();
            e.consume();
            return;
        }

        // Tool-specific drag handling
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

    // Handle mouse release events
    private void handleMouseReleased(MouseEvent e) {
        if (currentTool.equals("Image") && currentImage != null) {
            isDraggingImage = false;
            isResizingImage = false;
            saveState();
            return;
        }

        double x = e.getX();
        double y = e.getY();

        // Tool-specific release handling
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

    // Draw a rectangle between two points
    private void drawRectangle(double x1, double y1, double x2, double y2) {
        double width = x2 - x1;
        double height = y2 - y1;

        if (fillColorPicker.getValue() != Color.TRANSPARENT) {
            gc.fillRect(x1, y1, width, height);
        }
        gc.strokeRect(x1, y1, width, height);
    }

    // Draw a circle between two points (center to edge)
    private void drawCircle(double x1, double y1, double x2, double y2) {
        double radius = Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));

        if (fillColorPicker.getValue() != Color.TRANSPARENT) {
            gc.fillOval(x1 - radius, y1 - radius, radius * 2, radius * 2);
        }
        gc.strokeOval(x1 - radius, y1 - radius, radius * 2, radius * 2);
    }

    // Draw text at specified position
    private void drawText(String text, double x, double y) {
        gc.setFont(Font.font(fontSelector.getValue(), sizeSlider.getValue() * 3));
        gc.fillText(text, x, y);
    }

    // Erase at specified position
    private void eraseAt(double x, double y) {
        double size = sizeSlider.getValue() * 2;
        gc.clearRect(x - size/2, y - size/2, size, size);
    }

    // Add an image to the canvas
    private void addImage() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Image");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp")
        );
        File file = fileChooser.showOpenDialog(null);

        if (file != null) {
            currentImage = new Image(file.toURI().toString());

            // Scale image appropriately
            double scaleFactor = 0.25;
            imageWidth = Math.min(currentImage.getWidth() * scaleFactor, 200);
            imageHeight = currentImage.getHeight() * (imageWidth / currentImage.getWidth());

            // Position image
            if (startX == 0 && startY == 0) {
                imageX = (canvas.getWidth() - imageWidth) / 2;
                imageY = (canvas.getHeight() - imageHeight) / 2;
            } else {
                imageX = startX - imageWidth/2;
                imageY = startY - imageHeight/2;
            }

            redrawCanvas();
            saveState();
        }
    }

    // Redraw the entire canvas
    private void redrawCanvas() {
        // Clear canvas
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

        // Redraw previous state if available
        if (!undoStack.isEmpty()) {
            gc.drawImage(undoStack.peek(), 0, 0);
        }

        // Draw current image with handles if selected
        if (currentImage != null) {
            gc.drawImage(currentImage, imageX, imageY, imageWidth, imageHeight);

            if (currentTool.equals("Image")) {
                drawImageHandles();
            }
        }
    }

    // Draw resize handles around an image
    private void drawImageHandles() {
        gc.setStroke(Color.BLUE);
        gc.setLineWidth(1);
        gc.strokeRect(imageX, imageY, imageWidth, imageHeight);

        gc.setFill(Color.LIGHTBLUE);

        // Draw corner handles
        gc.fillRect(imageX - RESIZE_HANDLE_SIZE/2, imageY - RESIZE_HANDLE_SIZE/2,
                RESIZE_HANDLE_SIZE, RESIZE_HANDLE_SIZE);
        gc.fillRect(imageX + imageWidth - RESIZE_HANDLE_SIZE/2, imageY - RESIZE_HANDLE_SIZE/2,
                RESIZE_HANDLE_SIZE, RESIZE_HANDLE_SIZE);
        gc.fillRect(imageX + imageWidth - RESIZE_HANDLE_SIZE/2, imageY + imageHeight - RESIZE_HANDLE_SIZE/2,
                RESIZE_HANDLE_SIZE, RESIZE_HANDLE_SIZE);
        gc.fillRect(imageX - RESIZE_HANDLE_SIZE/2, imageY + imageHeight - RESIZE_HANDLE_SIZE/2,
                RESIZE_HANDLE_SIZE, RESIZE_HANDLE_SIZE);

        // Draw edge handles
        gc.fillRect(imageX + imageWidth/2 - RESIZE_HANDLE_SIZE/2, imageY - RESIZE_HANDLE_SIZE/2,
                RESIZE_HANDLE_SIZE, RESIZE_HANDLE_SIZE);
        gc.fillRect(imageX + imageWidth - RESIZE_HANDLE_SIZE/2, imageY + imageHeight/2 - RESIZE_HANDLE_SIZE/2,
                RESIZE_HANDLE_SIZE, RESIZE_HANDLE_SIZE);
        gc.fillRect(imageX + imageWidth/2 - RESIZE_HANDLE_SIZE/2, imageY + imageHeight - RESIZE_HANDLE_SIZE/2,
                RESIZE_HANDLE_SIZE, RESIZE_HANDLE_SIZE);
        gc.fillRect(imageX - RESIZE_HANDLE_SIZE/2, imageY + imageHeight/2 - RESIZE_HANDLE_SIZE/2,
                RESIZE_HANDLE_SIZE, RESIZE_HANDLE_SIZE);
    }

    // Add audio media to the canvas
    private void addAudio() {
        File file = showFileChooser("Select Audio", "Audio Files", AUDIO_FORMATS);
        if (file != null) {
            createMediaPlayer(file.toURI().toString(), "Audio Player", false);
        }
    }

    // Add video media to the canvas
    private void addVideo() {
        File file = showFileChooser("Select Video", "Video Files", VIDEO_FORMATS);
        if (file != null) {
            createMediaPlayer(file.toURI().toString(), "Video Player", true);
        }
    }

    // Show file chooser dialog
    private File showFileChooser(String title, String description, String... extensions) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(title);
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(description, extensions)
        );
        return fileChooser.showOpenDialog(null);
    }

    // Create a media player for audio/video
    private void createMediaPlayer(String mediaURI, String title, boolean isVideo) {
        try {
            Media media = new Media(mediaURI);
            mediaPlayer = new MediaPlayer(media);

            // Create media player UI
            Stage mediaStage = new Stage();
            mediaStage.setTitle(title);

            // Playback controls
            Button playBtn = new Button("▶");
            Button pauseBtn = new Button("⏸");
            Button stopBtn = new Button("⏹");

            playBtn.setOnAction(e -> mediaPlayer.play());
            pauseBtn.setOnAction(e -> mediaPlayer.pause());
            stopBtn.setOnAction(e -> {
                mediaPlayer.stop();
                mediaPlayer.seek(Duration.ZERO);
            });

            // Volume control
            Slider volumeSlider = new Slider(0, 1, 0.5);
            mediaPlayer.volumeProperty().bind(volumeSlider.valueProperty());

            // Timeline controls
            Slider timeSlider = new Slider();
            Label currentTimeLabel = new Label("00:00");
            Label totalTimeLabel = new Label("00:00");

            // Update time display during playback
            mediaPlayer.currentTimeProperty().addListener((observable, oldValue, newValue) -> {
                if (!timeSlider.isValueChanging()) {
                    timeSlider.setValue(newValue.toSeconds());
                }
                currentTimeLabel.setText(formatTime(newValue));
            });

            // Initialize duration display
            mediaPlayer.setOnReady(() -> {
                Duration totalDuration = media.getDuration();
                timeSlider.setMax(totalDuration.toSeconds());
                totalTimeLabel.setText(formatTime(totalDuration));
            });

            // Seek functionality
            timeSlider.valueProperty().addListener((observable, oldValue, newValue) -> {
                if (timeSlider.isValueChanging()) {
                    mediaPlayer.seek(Duration.seconds(newValue.doubleValue()));
                }
            });

            // Layout controls
            HBox timeBox = new HBox(5, currentTimeLabel, timeSlider, totalTimeLabel);
            timeBox.setAlignment(Pos.CENTER);

            HBox controls = new HBox(10, playBtn, pauseBtn, stopBtn,
                    new Label("Volume:"), volumeSlider);
            controls.setAlignment(Pos.CENTER);
            controls.setPadding(new Insets(10));

            BorderPane root = new BorderPane();

            // Add video display if video media
            if (isVideo) {
                mediaView = new MediaView(mediaPlayer);
                mediaView.setFitWidth(640);
                root.setCenter(mediaView);
            } else {
                root.setCenter(new Label("Now Playing: " + title));
            }

            // Combine all UI elements
            VBox bottomPanel = new VBox(10, timeBox, controls);
            bottomPanel.setPadding(new Insets(10));
            root.setBottom(bottomPanel);

            // Show media player window
            mediaStage.setScene(new Scene(root, isVideo ? 640 : 400, isVideo ? 480 : 150));
            mediaStage.show();

            // Clean up on window close
            mediaStage.setOnCloseRequest(e -> mediaPlayer.dispose());
        } catch (Exception e) {
            showError("Media Error", "Could not load media: " + e.getMessage());
        }
    }

    // Format time duration as MM:SS
    private String formatTime(Duration duration) {
        int minutes = (int) duration.toMinutes();
        int seconds = (int) duration.toSeconds() % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    // Save current canvas state for undo/redo
    private void saveState() {
        undoStack.push(canvas.snapshot(null, null));
        redoStack.clear();
    }

    // Undo the last action
    private void undo() {
        if (undoStack.size() > 1) {
            redoStack.push(undoStack.pop());
            redrawCanvas();
        }
    }

    // Redo the last undone action
    private void redo() {
        if (!redoStack.isEmpty()) {
            undoStack.push(redoStack.pop());
            redrawCanvas();
        }
    }

    // Clear the entire canvas
    private void clearCanvas() {
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        undoStack.clear();
        redoStack.clear();
        saveState();
    }

    // Save canvas to an image file
    private void saveCanvas() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Canvas");

        // Add supported format filters
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

                // Save canvas snapshot to file
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

    // Show error dialog
    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}