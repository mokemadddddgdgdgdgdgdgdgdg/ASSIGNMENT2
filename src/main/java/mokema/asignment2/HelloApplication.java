package mokema.asignment2;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import javafx.embed.swing.SwingFXUtils;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Stack;

public class HelloApplication extends Application {

    private Canvas canvas;
    private GraphicsContext gc;
    private Stack<Image> undoStack = new Stack<>();
    private Stack<Image> redoStack = new Stack<>();
    private ColorPicker colorPicker = new ColorPicker(Color.BLACK);
    private Slider brushSizeSlider = new Slider(1, 10, 2);
    private ComboBox<String> toolSelector = new ComboBox<>();
    private TextField textInput = new TextField("Sample Text");

    // Variables for image handling
    private Image insertedImage;
    private double imageX = 100, imageY = 100, imageWidth = 200, imageHeight = 200;
    private boolean dragging = false;
    private boolean resizingN = false, resizingS = false, resizingE = false, resizingW = false;
    private double mouseX, mouseY;
    private final int RESIZE_BORDER = 8; // Border width for resize handles

    // Variables for media handling
    private MediaPlayer mediaPlayer;
    private MediaView mediaView;
    private Stage mediaStage;
    private double mediaX = 150, mediaY = 150;
    private boolean mediaActive = false;

    @Override
    public void start(Stage primaryStage) {
        try {
            BorderPane root = new BorderPane();
            Scene scene = new Scene(root, 1000, 700);

            // Try to load stylesheet
            try {
                scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
            } catch (Exception e) {
                System.out.println("Warning: Could not load stylesheet: " + e.getMessage());
            }

            // Initialize Canvas
            canvas = new Canvas(900, 600);
            gc = canvas.getGraphicsContext2D();
            root.setCenter(canvas);

            // Set default drawing settings
            gc.setStroke(Color.BLACK);
            gc.setLineWidth(2);

            // Tool Selector
            toolSelector.getItems().addAll("Draw", "Rectangle", "Circle", "Eraser", "Text", "Image", "Audio", "Video");
            toolSelector.setValue("Draw");

            // Buttons
            Button undoButton = new Button("Undo");
            undoButton.setOnAction(e -> undo());

            Button redoButton = new Button("Redo");
            redoButton.setOnAction(e -> redo());

            Button clearButton = new Button("Clear");
            clearButton.setOnAction(e -> clearCanvas());

            Button saveButton = new Button("Save");
            saveButton.setOnAction(e -> saveCanvas());

            Button brushButton = new Button("Set Brush Size");
            brushButton.setOnAction(e -> gc.setLineWidth(brushSizeSlider.getValue()));

            // Toolbar
            HBox toolbar = new HBox(10, toolSelector, colorPicker, brushSizeSlider, brushButton, undoButton, redoButton, clearButton, saveButton, textInput);
            root.setTop(toolbar);

            // Event Handlers
            setupMouseHandlers();

            // Show the stage
            primaryStage.setTitle("Interactive Digital Whiteboard");
            primaryStage.setScene(scene);
            primaryStage.show();

            // Save initial state
            saveState();

            System.out.println("Application started successfully!");
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Error starting application: " + e.getMessage());
        }
    }

    private void setupMouseHandlers() {
        canvas.setOnMousePressed(e -> {
            double x = e.getX();
            double y = e.getY();
            String tool = toolSelector.getValue();

            gc.setStroke(colorPicker.getValue());
            gc.setFill(colorPicker.getValue());

            switch (tool) {
                case "Draw":
                    gc.beginPath();
                    gc.moveTo(x, y);
                    saveState();
                    break;
                case "Rectangle":
                    gc.strokeRect(x, y, 100, 50);
                    saveState();
                    break;
                case "Circle":
                    gc.strokeOval(x, y, 50, 50);
                    saveState();
                    break;
                case "Eraser":
                    erase(x, y, 20);
                    saveState();
                    break;
                case "Text":
                    drawText(textInput.getText(), x, y);
                    saveState();
                    break;
                case "Image":
                    if (insertedImage == null) {
                        addImage();
                    } else {
                        // Check if clicking on the image for dragging
                        boolean isInsideImage = x >= imageX && x <= imageX + imageWidth &&
                                y >= imageY && y <= imageY + imageHeight;

                        if (isInsideImage) {
                            // Check for resize handles first
                            boolean isNearTop = Math.abs(y - imageY) <= RESIZE_BORDER;
                            boolean isNearBottom = Math.abs(y - (imageY + imageHeight)) <= RESIZE_BORDER;
                            boolean isNearLeft = Math.abs(x - imageX) <= RESIZE_BORDER;
                            boolean isNearRight = Math.abs(x - (imageX + imageWidth)) <= RESIZE_BORDER;

                            // Set appropriate resizing flags
                            resizingN = isNearTop && !isNearLeft && !isNearRight;
                            resizingS = isNearBottom && !isNearLeft && !isNearRight;
                            resizingW = isNearLeft && !isNearTop && !isNearBottom;
                            resizingE = isNearRight && !isNearTop && !isNearBottom;

                            // Handle corners
                            if (isNearTop && isNearLeft) { // Northwest
                                resizingN = true;
                                resizingW = true;
                            } else if (isNearTop && isNearRight) { // Northeast
                                resizingN = true;
                                resizingE = true;
                            } else if (isNearBottom && isNearLeft) { // Southwest
                                resizingS = true;
                                resizingW = true;
                            } else if (isNearBottom && isNearRight) { // Southeast
                                resizingS = true;
                                resizingE = true;
                            }

                            // If not resizing, then dragging
                            if (!(resizingN || resizingS || resizingE || resizingW)) {
                                dragging = true;
                                mouseX = x - imageX;
                                mouseY = y - imageY;
                            } else {
                                // Save original position and dimensions for resizing
                                mouseX = x;
                                mouseY = y;
                            }
                        }
                    }
                    break;
                case "Audio":
                    addAudio();
                    break;
                case "Video":
                    addVideo();
                    break;
            }
        });

        canvas.setOnMouseDragged(e -> {
            if (toolSelector.getValue().equals("Draw")) {
                gc.lineTo(e.getX(), e.getY());
                gc.stroke();
            } else if (resizingN || resizingS || resizingE || resizingW) {
                // Handle resizing from different directions
                double dx = e.getX() - mouseX;
                double dy = e.getY() - mouseY;
                mouseX = e.getX();
                mouseY = e.getY();

                // Resize from north (top)
                if (resizingN) {
                    double newY = imageY + dy;
                    double newHeight = imageHeight - dy;
                    if (newHeight >= 50) {
                        imageY = newY;
                        imageHeight = newHeight;
                    }
                }

                // Resize from south (bottom)
                if (resizingS) {
                    imageHeight = Math.max(50, imageHeight + dy);
                }

                // Resize from west (left)
                if (resizingW) {
                    double newX = imageX + dx;
                    double newWidth = imageWidth - dx;
                    if (newWidth >= 50) {
                        imageX = newX;
                        imageWidth = newWidth;
                    }
                }

                // Resize from east (right)
                if (resizingE) {
                    imageWidth = Math.max(50, imageWidth + dx);
                }

                redrawCanvas();
            } else if (dragging) {
                imageX = e.getX() - mouseX;
                imageY = e.getY() - mouseY;
                redrawCanvas();
            }
        });

        canvas.setOnMouseReleased(e -> {
            if (toolSelector.getValue().equals("Draw")) {
                saveState();
            }
            if (resizingN || resizingS || resizingE || resizingW || dragging) {
                saveState(); // Save state after modifying image
            }
            resizingN = false;
            resizingS = false;
            resizingE = false;
            resizingW = false;
            dragging = false;
        });
    }

    private void saveState() {
        Image snapshot = canvas.snapshot(null, null);
        undoStack.push(snapshot);
        redoStack.clear();
    }

    private void undo() {
        if (undoStack.size() > 1) {
            redoStack.push(undoStack.pop());
            Image lastState = undoStack.peek();
            gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
            gc.drawImage(lastState, 0, 0);
        }
    }

    private void redo() {
        if (!redoStack.isEmpty()) {
            Image nextState = redoStack.pop();
            undoStack.push(nextState);
            gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
            gc.drawImage(nextState, 0, 0);
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
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("PNG Image", "*.png"),
                new FileChooser.ExtensionFilter("JPEG Image", "*.jpg")
        );
        File file = fileChooser.showSaveDialog(null);
        if (file != null) {
            try {
                Image snapshot = canvas.snapshot(null, null);
                ImageIO.write(SwingFXUtils.fromFXImage(snapshot, null), "png", file);
                System.out.println("Canvas saved to: " + file.getAbsolutePath());
            } catch (IOException e) {
                e.printStackTrace();
                System.out.println("Error saving canvas: " + e.getMessage());
            }
        }
    }

    private void erase(double x, double y, double size) {
        gc.clearRect(x - size / 2, y - size / 2, size, size);
    }

    private void drawText(String text, double x, double y) {
        gc.setFont(new Font("Arial", 16));
        gc.fillText(text, x, y);
    }

    private void addImage() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Image File");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg"));
        File file = fileChooser.showOpenDialog(null);

        if (file != null) {
            try {
                insertedImage = new Image(file.toURI().toString());
                redrawCanvas();
                saveState();
                System.out.println("Image added successfully");
            } catch (Exception e) {
                System.out.println("Error loading image: " + e.getMessage());
            }
        }
    }

    private void redrawCanvas() {
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        if (!undoStack.isEmpty()) {
            gc.drawImage(undoStack.peek(), 0, 0);
        }
        if (insertedImage != null) {
            gc.drawImage(insertedImage, imageX, imageY, imageWidth, imageHeight);

            // Draw resize handles
            if (toolSelector.getValue().equals("Image")) {
                gc.setStroke(Color.BLUE);
                gc.setLineWidth(1);
                // Draw border around image
                gc.strokeRect(imageX, imageY, imageWidth, imageHeight);

                // Draw corner handles
                double handleSize = RESIZE_BORDER;
                gc.setFill(Color.LIGHTBLUE);
                // Top-left
                gc.fillRect(imageX - handleSize / 2, imageY - handleSize / 2, handleSize, handleSize);
                // Top-right
                gc.fillRect(imageX + imageWidth - handleSize / 2, imageY - handleSize / 2, handleSize, handleSize);
                // Bottom-left
                gc.fillRect(imageX - handleSize / 2, imageY + imageHeight - handleSize / 2, handleSize, handleSize);
                // Bottom-right
                gc.fillRect(imageX + imageWidth - handleSize / 2, imageY + imageHeight - handleSize / 2, handleSize, handleSize);

                // Draw edge handles
                // Top middle
                gc.fillRect(imageX + imageWidth / 2 - handleSize / 2, imageY - handleSize / 2, handleSize, handleSize);
                // Bottom middle
                gc.fillRect(imageX + imageWidth / 2 - handleSize / 2, imageY + imageHeight - handleSize / 2, handleSize, handleSize);
                // Left middle
                gc.fillRect(imageX - handleSize / 2, imageY + imageHeight / 2 - handleSize / 2, handleSize, handleSize);
                // Right middle
                gc.fillRect(imageX + imageWidth - handleSize / 2, imageY + imageHeight / 2 - handleSize / 2, handleSize, handleSize);

                // Reset stroke
                gc.setStroke(colorPicker.getValue());
            }
        }
    }

    /**
     * Add audio to the whiteboard with a player interface
     */
    private void addAudio() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Audio File");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Audio Files", "*.mp3", "*.wav"));
        File file = fileChooser.showOpenDialog(null);

        if (file != null) {
            try {
                // Create a media player in a separate window
                createMediaPlayer(file, "Audio Player");
                System.out.println("Audio added successfully");
            } catch (Exception e) {
                System.out.println("Error loading audio: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * Add video to the whiteboard with a player interface
     */
    private void addVideo() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Video File");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Video Files", "*.mp4", "*.avi", "*.mov"));
        File file = fileChooser.showOpenDialog(null);

        if (file != null) {
            try {
                // Create a media player in a separate window
                createMediaPlayer(file, "Video Player");
                System.out.println("Video added successfully");
            } catch (Exception e) {
                System.out.println("Error loading video: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * Creates a media player window with VLC-like controls
     */
    private void createMediaPlayer(File mediaFile, String title) {
        try {
            // Create a new stage for the media player
            Stage mediaStage = new Stage();
            mediaStage.setTitle(title);

            // Create the media player
            Media media = new Media(mediaFile.toURI().toString());
            MediaPlayer mediaPlayer = new MediaPlayer(media);
            MediaView mediaView = new MediaView(mediaPlayer);

            // Create control buttons
            Button playButton = new Button("Play");
            Button pauseButton = new Button("Pause");
            Button stopButton = new Button("Stop");
            Button muteButton = new Button("Mute");
            Slider volumeSlider = new Slider(0, 1, 0.5);
            Slider timeSlider = new Slider();
            Label timeLabel = new Label("00:00 / 00:00");

            // Configure view
            mediaView.setFitWidth(640);
            mediaView.setFitHeight(480);
            mediaView.setPreserveRatio(true);

            // Set button actions
            playButton.setOnAction(e -> mediaPlayer.play());
            pauseButton.setOnAction(e -> mediaPlayer.pause());
            stopButton.setOnAction(e -> {
                mediaPlayer.stop();
                mediaPlayer.seek(Duration.ZERO);
            });
            muteButton.setOnAction(e -> {
                mediaPlayer.setMute(!mediaPlayer.isMute());
                muteButton.setText(mediaPlayer.isMute() ? "Unmute" : "Mute");
            });

            // Volume control
            volumeSlider.valueProperty().addListener((obs, oldVal, newVal) ->
                    mediaPlayer.setVolume(newVal.doubleValue()));

            // Media duration and time tracking
            mediaPlayer.setOnReady(() -> {
                Duration total = media.getDuration();
                timeSlider.setMax(total.toSeconds());
                updateTimeLabel(mediaPlayer, timeLabel);
            });

            // Update time slider
            mediaPlayer.currentTimeProperty().addListener((obs, oldVal, newVal) -> {
                if (!timeSlider.isValueChanging()) {
                    timeSlider.setValue(newVal.toSeconds());
                    updateTimeLabel(mediaPlayer, timeLabel);
                }
            });

            // Seek when slider is moved
            timeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
                if (timeSlider.isValueChanging()) {
                    mediaPlayer.seek(Duration.seconds(newVal.doubleValue()));
                }
            });

            // Create controls layout
            HBox controls = new HBox(10, playButton, pauseButton, stopButton, muteButton,
                    volumeSlider, timeSlider, timeLabel);
            controls.setAlignment(Pos.CENTER);
            controls.setPadding(new Insets(10));

            // Create main layout
            BorderPane root = new BorderPane();
            root.setCenter(mediaView);
            root.setBottom(controls);

            // Set up the scene
            Scene scene = new Scene(root, 800, 600);
            mediaStage.setScene(scene);
            mediaStage.show();

            // Clean up resources when window is closed
            mediaStage.setOnCloseRequest(e -> {
                mediaPlayer.stop();
                mediaPlayer.dispose();
            });

        } catch (Exception e) {
            System.out.println("Error creating media player: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void updateTimeLabel(MediaPlayer mediaPlayer, Label timeLabel) {
        Duration currentTime = mediaPlayer.getCurrentTime();
        Duration totalDuration = mediaPlayer.getTotalDuration();

        if (totalDuration != null) {
            String formattedTime = formatTime(currentTime) + " / " + formatTime(totalDuration);
            timeLabel.setText(formattedTime);
        }
    }

    private String formatTime(Duration duration) {
        int minutes = (int) Math.floor(duration.toMinutes());
        int seconds = (int) Math.floor(duration.toSeconds() % 60);
        return String.format("%02d:%02d", minutes, seconds);
    }

    public static void main(String[] args) {


        launch(args);

    }
}