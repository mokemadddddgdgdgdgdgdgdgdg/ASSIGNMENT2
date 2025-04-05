// Main package declaration
package mokema.asignment2;

// JavaFX and other necessary imports
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

//Main application class for Digital Whiteboard


public class HelloApplication extends Application {

    // Constants for canvas dimensions
    private static final int CANVAS_WIDTH = 900;
    private static final int CANVAS_HEIGHT = 600;

    // Core application components
    private DrawingCanvas drawingCanvas;       // Handles drawing operations
    private MediaHandler mediaHandler;        // Manages audio/video playback
    private ToolbarManager toolbarManager;    // Manages UI toolbar

    // Main entry point for the application
    public static void main(String[] args) {
        launch(args);  // Standard JavaFX entry point
    }


    // JavaFX start method - initializes and shows the application
    @Override
    public void start(Stage primaryStage) {
        initializeComponents();    // Create application components
        setupMainLayout(primaryStage);  // Set up the UI layout
    }

    // Initialize the main application components
    private void initializeComponents() {
        drawingCanvas = new DrawingCanvas(CANVAS_WIDTH, CANVAS_HEIGHT);
        mediaHandler = new MediaHandler();
        toolbarManager = new ToolbarManager(drawingCanvas, mediaHandler);
    }

    // Sets up the main application layout
    // primaryStage The main application window

    private void setupMainLayout(Stage primaryStage) {
        BorderPane root = new BorderPane();  // Main layout container

        // Set up the layout hierarchy
        root.setCenter(drawingCanvas.getCanvas());  // Drawing area in center
        root.setTop(toolbarManager.createToolbar());  // Toolbar at top

        // Create scene with optional CSS styling
        Scene scene = new Scene(root, 1000, 700);
        try {
            scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
        } catch (Exception e) {
            System.out.println("Stylesheet not found, using default styling");
        }

        // Configure and show the primary stage
        primaryStage.setTitle("Digital Whiteboard");
        primaryStage.setScene(scene);
        primaryStage.show();
    }


    // class representing the drawing canvas and its functionality
    private static class DrawingCanvas {
        // Core drawing components
        private final Canvas canvas;          // The actual drawing surface
        private final GraphicsContext gc;     // Drawing context for the canvas

        // Undo/redo functionality stacks
        private final Stack<Image> undoStack = new Stack<>();
        private final Stack<Image> redoStack = new Stack<>();

        // UI controls for drawing properties
        private final ColorPicker strokeColorPicker = new ColorPicker(Color.BLACK);
        private final ColorPicker fillColorPicker = new ColorPicker(Color.TRANSPARENT);
        private final ComboBox<String> toolSelector = new ComboBox<>();
        private final ComboBox<String> fontSelector = new ComboBox<>();
        private final Slider sizeSlider = new Slider(1, 50, 5);
        private final TextField textInput = new TextField("NGOLA");

        // Drawing state tracking variables
        private String currentTool = "Draw";  // Currently selected tool
        private double startX, startY;        // Starting coordinates for shapes
        private boolean isDrawing = false;    // Flag for drawing in progress

        // Image manipulation state
        private Image currentImage;           // Currently loaded image
        private double imageX, imageY;        // Image position
        private double imageWidth, imageHeight; // Image dimensions
        private boolean isDraggingImage = false; // Image dragging flag
        private boolean isResizingImage = false; // Image resizing flag
        private double dragStartX, dragStartY; // Drag starting position
        private int resizeDirection = 0;      // Direction for resizing (0=none, 1-8 for different edges/corners)

        // Constants
        private static final String[] FONT_FAMILIES = {"Arial", "Verdana", "Times New Roman", "Courier New"};
        private static final double RESIZE_HANDLE_SIZE = 8; // Size of image resize handles
        private static final double MIN_IMAGE_SIZE = 20;    // Minimum size for images

        //Constructor - creates a new drawing canvas
        public DrawingCanvas(int width, int height) {
            canvas = new Canvas(width, height);
            gc = canvas.getGraphicsContext2D();
            initialize();  // Set up initial canvas state
        }

        // Initialize canvas properties and event handlers
        private void initialize() {
            clearCanvas();         // Start with blank canvas
            setupMouseHandlers();  // Set up mouse event handlers
            setupToolSelector();   // Configure tool selection dropdown
            setupFontSelector();   // Configure font selection dropdown
            setupSizeSlider();     // Configure line size slider
        }

        // Getter methods for UI components
        public Canvas getCanvas() { return canvas; }
        public GraphicsContext getGc() { return gc; }
        public ColorPicker getStrokeColorPicker() { return strokeColorPicker; }
        public ColorPicker getFillColorPicker() { return fillColorPicker; }
        public ComboBox<String> getToolSelector() { return toolSelector; }
        public ComboBox<String> getFontSelector() { return fontSelector; }
        public Slider getSizeSlider() { return sizeSlider; }
        public TextField getTextInput() { return textInput; }

        // Set up the tool selection dropdown
        private void setupToolSelector() {
            toolSelector.getItems().addAll(
                    "Draw", "Line", "Rectangle", "Circle",
                    "Text", "Image", "Eraser"
            );
            toolSelector.setValue("Draw");
            toolSelector.setOnAction(e -> currentTool = toolSelector.getValue());
        }

        // Set up the font selection dropdown
        private void setupFontSelector() {
            fontSelector.getItems().addAll(FONT_FAMILIES);
            fontSelector.setValue("Arial");
        }

        // Configure the line width slider
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


        // Handles mouse press events on the canvas

        private void handleMousePressed(MouseEvent e) {
            saveState();  // Save current state for undo/redo

            // Record starting position
            startX = e.getX();
            startY = e.getY();

            // Handle image interaction if needed
            if (handleImageInteraction(e)) {
                return;
            }

            // Set drawing properties from UI controls
            gc.setStroke(strokeColorPicker.getValue());
            gc.setFill(fillColorPicker.getValue());
            gc.setLineWidth(sizeSlider.getValue());

            // Tool-specific press handling
            handleToolSpecificPress();
        }


        // Handles image interaction (dragging/resizing)
        //return true if image interaction was handled
        private boolean handleImageInteraction(MouseEvent e) {
            if (currentTool.equals("Image") && currentImage != null) {
                double mouseX = e.getX();
                double mouseY = e.getY();

                // Check if click was within image bounds
                if (mouseX >= imageX && mouseX <= imageX + imageWidth &&
                        mouseY >= imageY && mouseY <= imageY + imageHeight) {

                    // Determine if user clicked on a resize handle or the image body
                    resizeDirection = getResizeDirection(mouseX, mouseY);

                    if (resizeDirection == 0) {
                        // Dragging the image
                        isDraggingImage = true;
                        dragStartX = mouseX - imageX;
                        dragStartY = mouseY - imageY;
                    } else {
                        // Resizing the image
                        isResizingImage = true;
                        dragStartX = mouseX;
                        dragStartY = mouseY;
                    }
                    e.consume();
                    return true;
                }
            }
            return false;
        }

        // Determines which edge/corner of the image was clicked for resizing
        //@return Direction code (0=none, 1-8 for different edges/corners)
        private int getResizeDirection(double mouseX, double mouseY) {
            // Check proximity to each edge/corner
            boolean nearLeft = Math.abs(mouseX - imageX) < RESIZE_HANDLE_SIZE;
            boolean nearRight = Math.abs(mouseX - (imageX + imageWidth)) < RESIZE_HANDLE_SIZE;
            boolean nearTop = Math.abs(mouseY - imageY) < RESIZE_HANDLE_SIZE;
            boolean nearBottom = Math.abs(mouseY - (imageY + imageHeight)) < RESIZE_HANDLE_SIZE;

            // Return direction code based on which edges are near
            if (nearTop && nearLeft) return 5;    // Top-left corner
            if (nearTop && nearRight) return 6;   // Top-right corner
            if (nearBottom && nearRight) return 7;// Bottom-right corner
            if (nearBottom && nearLeft) return 8;// Bottom-left corner
            if (nearTop) return 1;               // Top edge
            if (nearRight) return 2;             // Right edge
            if (nearBottom) return 3;            // Bottom edge
            if (nearLeft) return 4;             // Left edge

            return 0;  // No edge/corner nearby
        }

        // Handle tool-specific behavior when mouse is pressed
        private void handleToolSpecificPress() {
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


        // Handles mouse drag events on the canvas


        private void handleMouseDragged(MouseEvent e) {
            double x = e.getX();
            double y = e.getY();

            // Handle image dragging/resizing if needed
            if (handleImageDragAndResize(x, y)) {
                e.consume();
                return;
            }

            // Tool-specific drag handling
            handleToolSpecificDrag(x, y);
        }


        //Handles image dragging and resizing during mouse drag
        // return true if image interaction was handled

        private boolean handleImageDragAndResize(double x, double y) {
            if (currentTool.equals("Image") && currentImage != null) {
                if (isDraggingImage) {
                    // Update image position based on drag
                    imageX = x - dragStartX;
                    imageY = y - dragStartY;
                } else if (isResizingImage) {
                    // Resize the image based on drag direction
                    resizeImage(x, y);
                }
                redrawCanvas();  // Refresh the canvas
                return true;
            }
            return false;
        }


        //Resizes the image based on mouse movement and resize direction

        private void resizeImage(double x, double y) {
            double deltaX = x - dragStartX;
            double deltaY = y - dragStartY;

            // Adjust image dimensions based on which edge/corner is being dragged
            switch (resizeDirection) {
                case 1:  // Top edge
                    imageHeight -= deltaY;
                    imageY += deltaY;
                    break;
                case 2:  // Right edge
                    imageWidth += deltaX;
                    break;
                case 3:  // Bottom edge
                    imageHeight += deltaY;
                    break;
                case 4:  // Left edge
                    imageWidth -= deltaX;
                    imageX += deltaX;
                    break;
                case 5:  // Top-left corner
                    imageWidth -= deltaX;
                    imageX += deltaX;
                    imageHeight -= deltaY;
                    imageY += deltaY;
                    break;
                case 6:  // Top-right corner
                    imageWidth += deltaX;
                    imageHeight -= deltaY;
                    imageY += deltaY;
                    break;
                case 7:  // Bottom-right corner
                    imageWidth += deltaX;
                    imageHeight += deltaY;
                    break;
                case 8:  // Bottom-left corner
                    imageWidth -= deltaX;
                    imageX += deltaX;
                    imageHeight += deltaY;
                    break;
            }

            enforceMinimumImageSize();  // Prevent image from becoming too small
            dragStartX = x;  // Update drag starting position
            dragStartY = y;
        }

        // Ensures image doesn't get smaller than minimum size
        private void enforceMinimumImageSize() {
            if (imageWidth < MIN_IMAGE_SIZE) imageWidth = MIN_IMAGE_SIZE;
            if (imageHeight < MIN_IMAGE_SIZE) imageHeight = MIN_IMAGE_SIZE;
        }


        // Handles toolspecific behavior during mouse drag

        private void handleToolSpecificDrag(double x, double y) {
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


        // Handles mouse release events on the canvas
        private void handleMouseReleased(MouseEvent e) {
            if (currentTool.equals("Image") && currentImage != null) {
                // Finish image manipulation
                isDraggingImage = false;
                isResizingImage = false;
                saveState();  // Save final state
                return;
            }

            double x = e.getX();
            double y = e.getY();

            // Tool-specific release handling
            handleToolSpecificRelease(x, y);

            if (isDrawing) {
                saveState();  // Save final state
                isDrawing = false;
            }
        }


        //Handles tool-specific behavior when mouse is released

        private void handleToolSpecificRelease(double x, double y) {
            switch (currentTool) {
                case "Text":
                    drawText(textInput.getText(), x, y);
                    break;
                case "Image":
                    addImage();
                    break;
                case "Audio":
                    // Handled by MediaHandler
                    break;
                case "Video":
                    // Handled by MediaHandler
                    break;
            }
        }


        //Draws a rectangle between two points

        private void drawRectangle(double x1, double y1, double x2, double y2) {
            double width = x2 - x1;
            double height = y2 - y1;

            if (fillColorPicker.getValue() != Color.TRANSPARENT) {
                gc.fillRect(x1, y1, width, height);  // Fill if color is not transparent
            }
            gc.strokeRect(x1, y1, width, height);    // Always draw outline
        }


        //Draws a circle between two points (center to edge)

        private void drawCircle(double x1, double y1, double x2, double y2) {
            double radius = Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));

            if (fillColorPicker.getValue() != Color.TRANSPARENT) {
                gc.fillOval(x1 - radius, y1 - radius, radius * 2, radius * 2);  // Fill if color is not transparent
            }
            gc.strokeOval(x1 - radius, y1 - radius, radius * 2, radius * 2);    // Always draw outline
        }

        //Draws text at specified position
        private void drawText(String text, double x, double y) {
            gc.setFont(Font.font(fontSelector.getValue(), sizeSlider.getValue() * 3));
            gc.fillText(text, x, y);
        }


        //Erases at specified position


        private void eraseAt(double x, double y) {
            double size = sizeSlider.getValue() * 2;
            gc.clearRect(x - size / 2, y - size / 2, size, size);
        }


        // Opens file chooser to add an image to the canvas

        private void addImage() {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Select Image");
            fileChooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp")
            );
            File file = fileChooser.showOpenDialog(null);

            if (file != null) {
                currentImage = new Image(file.toURI().toString());
                scaleAndPositionImage();  // Scale and position the new image
                redrawCanvas();          // Refresh the canvas
                saveState();             // Save state for undo/redo
            }
        }


        // Scales and positions a newly loaded image

        private void scaleAndPositionImage() {
            double scaleFactor = 0.25;  // Default scaling factor
            imageWidth = Math.min(currentImage.getWidth() * scaleFactor, 200);
            imageHeight = currentImage.getHeight() * (imageWidth / currentImage.getWidth());

            // Position image - centered if no specific position set
            if (startX == 0 && startY == 0) {
                imageX = (canvas.getWidth() - imageWidth) / 2;
                imageY = (canvas.getHeight() - imageHeight) / 2;
            } else {
                imageX = startX - imageWidth / 2;
                imageY = startY - imageHeight / 2;
            }
        }


        //Redraws the entire canvas (used during image manipulation)

        private void redrawCanvas() {
            // Clear and redraw everything
            gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

            // Redraw previous state from undo stack if available
            if (!undoStack.isEmpty()) {
                gc.drawImage(undoStack.peek(), 0, 0);
            }

            // Redraw current image if it exists
            if (currentImage != null) {
                gc.drawImage(currentImage, imageX, imageY, imageWidth, imageHeight);

                // Draw resize handles if image tool is active
                if (currentTool.equals("Image")) {
                    drawImageHandles();
                }
            }
        }


        //Draws resize handles around the current image

        private void drawImageHandles() {
            // Draw border around image
            gc.setStroke(Color.BLUE);
            gc.setLineWidth(1);
            gc.strokeRect(imageX, imageY, imageWidth, imageHeight);

            // Draw resize handles at corners and edges
            gc.setFill(Color.LIGHTBLUE);

            // Corner handles
            gc.fillRect(imageX - RESIZE_HANDLE_SIZE / 2, imageY - RESIZE_HANDLE_SIZE / 2,
                    RESIZE_HANDLE_SIZE, RESIZE_HANDLE_SIZE);
            gc.fillRect(imageX + imageWidth - RESIZE_HANDLE_SIZE / 2, imageY - RESIZE_HANDLE_SIZE / 2,
                    RESIZE_HANDLE_SIZE, RESIZE_HANDLE_SIZE);
            gc.fillRect(imageX + imageWidth - RESIZE_HANDLE_SIZE / 2, imageY + imageHeight - RESIZE_HANDLE_SIZE / 2,
                    RESIZE_HANDLE_SIZE, RESIZE_HANDLE_SIZE);
            gc.fillRect(imageX - RESIZE_HANDLE_SIZE / 2, imageY + imageHeight - RESIZE_HANDLE_SIZE / 2,
                    RESIZE_HANDLE_SIZE, RESIZE_HANDLE_SIZE);

            // Edge handles
            gc.fillRect(imageX + imageWidth / 2 - RESIZE_HANDLE_SIZE / 2, imageY - RESIZE_HANDLE_SIZE / 2,
                    RESIZE_HANDLE_SIZE, RESIZE_HANDLE_SIZE);
            gc.fillRect(imageX + imageWidth - RESIZE_HANDLE_SIZE / 2, imageY + imageHeight / 2 - RESIZE_HANDLE_SIZE / 2,
                    RESIZE_HANDLE_SIZE, RESIZE_HANDLE_SIZE);
            gc.fillRect(imageX + imageWidth / 2 - RESIZE_HANDLE_SIZE / 2, imageY + imageHeight - RESIZE_HANDLE_SIZE / 2,
                    RESIZE_HANDLE_SIZE, RESIZE_HANDLE_SIZE);
            gc.fillRect(imageX - RESIZE_HANDLE_SIZE / 2, imageY + imageHeight / 2 - RESIZE_HANDLE_SIZE / 2,
                    RESIZE_HANDLE_SIZE, RESIZE_HANDLE_SIZE);
        }


        //Saves current canvas state to undo stack

        private void saveState() {
            undoStack.push(canvas.snapshot(null, null));
            redoStack.clear();  // Clear redo stack when new state is saved
        }


        // Undo the last operation

        public void undo() {
            if (undoStack.size() > 1) {
                redoStack.push(undoStack.pop());  // Move current state to redo stack
                redrawCanvas();                   // Redraw previous state
            }
        }


        //Redo the last undone operation

        public void redo() {
            if (!redoStack.isEmpty()) {
                undoStack.push(redoStack.pop());  // Move state back to undo stack
                redrawCanvas();                  // Redraw the state
            }
        }


        // Clears the canvas completely

        public void clearCanvas() {
            gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
            undoStack.clear();  // Clear history
            redoStack.clear();
            saveState();       // Save blank state
        }


        // Saves the canvas to an image file

        public void saveCanvas() {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Save Canvas");

            // Supported file formats
            String[] SAVE_FORMATS = {"PNG", "JPG", "BMP", "GIF"};
            for (String format : SAVE_FORMATS) {
                fileChooser.getExtensionFilters().add(
                        new FileChooser.ExtensionFilter(format, "*." + format.toLowerCase())
                );
            }

            File file = fileChooser.showSaveDialog(null);
            if (file != null) {
                try {
                    // Get selected format
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
    }


    //  class handling media (audio/video) playback

    private static class MediaHandler {
        private MediaPlayer mediaPlayer;  // Current media player instance
        private MediaView mediaView;      // For video display

        // Supported file formats
        private static final String[] AUDIO_FORMATS = {"*.mp3", "*.wav"};
        private static final String[] VIDEO_FORMATS = {"*.mp4", "*.avi", "*.mov"};


        // Opens file chooser to add audio to the canvas

        public void addAudio() {
            File file = showFileChooser("Select Audio", "Audio Files", AUDIO_FORMATS);
            if (file != null) {
                createMediaPlayer(file.toURI().toString(), "Audio Player", false);
            }
        }


        //Opens file chooser to add video to the canvas

        public void addVideo() {
            File file = showFileChooser("Select Video", "Video Files", VIDEO_FORMATS);
            if (file != null) {
                createMediaPlayer(file.toURI().toString(), "Video Player", true);
            }
        }


        //Selected file
        private File showFileChooser(String title, String description, String... extensions) {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle(title);
            fileChooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter(description, extensions)
            );
            return fileChooser.showOpenDialog(null);
        }


        //Creates a media player for audio or video
        private void createMediaPlayer(String mediaURI, String title, boolean isVideo) {
            try {
                Media media = new Media(mediaURI);
                mediaPlayer = new MediaPlayer(media);

                // Create media player window
                Stage mediaStage = new Stage();
                mediaStage.setTitle(title);

                // Player controls
                Button playBtn = new Button("▶");
                Button pauseBtn = new Button("⏸");
                Button stopBtn = new Button("⏹");

                // Button actions
                playBtn.setOnAction(e -> mediaPlayer.play());
                pauseBtn.setOnAction(e -> mediaPlayer.pause());
                stopBtn.setOnAction(e -> {
                    mediaPlayer.stop();
                    mediaPlayer.seek(Duration.ZERO);
                });

                // Volume control
                Slider volumeSlider = new Slider(0, 1, 0.5);
                mediaPlayer.volumeProperty().bind(volumeSlider.valueProperty());

                // Time slider and labels
                Slider timeSlider = new Slider();
                Label currentTimeLabel = new Label("00:00");
                Label totalTimeLabel = new Label("00:00");

                // Update current time during playback
                mediaPlayer.currentTimeProperty().addListener((observable, oldValue, newValue) -> {
                    if (!timeSlider.isValueChanging()) {
                        timeSlider.setValue(newValue.toSeconds());
                    }
                    currentTimeLabel.setText(formatTime(newValue));
                });

                // Set up total duration when media is ready
                mediaPlayer.setOnReady(() -> {
                    Duration totalDuration = media.getDuration();
                    timeSlider.setMax(totalDuration.toSeconds());
                    totalTimeLabel.setText(formatTime(totalDuration));
                });

                // Seek when time slider is moved
                timeSlider.valueProperty().addListener((observable, oldValue, newValue) -> {
                    if (timeSlider.isValueChanging()) {
                        mediaPlayer.seek(Duration.seconds(newValue.doubleValue()));
                    }
                });

                // Time display layout
                HBox timeBox = new HBox(5, currentTimeLabel, timeSlider, totalTimeLabel);
                timeBox.setAlignment(Pos.CENTER);

                // Control buttons layout
                HBox controls = new HBox(10, playBtn, pauseBtn, stopBtn,
                        new Label("Volume:"), volumeSlider);
                controls.setAlignment(Pos.CENTER);
                controls.setPadding(new Insets(10));

                // Main layout
                BorderPane root = new BorderPane();

                // Add video display if this is a video
                if (isVideo) {
                    mediaView = new MediaView(mediaPlayer);
                    mediaView.setFitWidth(640);
                    root.setCenter(mediaView);
                } else {
                    root.setCenter(new Label("Now Playing: " + title));
                }

                // Combine controls at bottom
                VBox bottomPanel = new VBox(10, timeBox, controls);
                bottomPanel.setPadding(new Insets(10));
                root.setBottom(bottomPanel);

                // Show the media player window
                mediaStage.setScene(new Scene(root, isVideo ? 640 : 400, isVideo ? 480 : 150));
                mediaStage.show();

                // Clean up when window is closed
                mediaStage.setOnCloseRequest(e -> mediaPlayer.dispose());
            } catch (Exception e) {
                showError("Media Error", "Could not load media: " + e.getMessage());
            }
        }


        //Formats duration as MM:SS
        private String formatTime(Duration duration) {
            int minutes = (int) duration.toMinutes();
            int seconds = (int) duration.toSeconds() % 60;
            return String.format("%02d:%02d", minutes, seconds);
        }
    }


    // class managing the toolbar UI

    private static class ToolbarManager {
        private final DrawingCanvas drawingCanvas;  // Reference to drawing canvas
        private final MediaHandler mediaHandler;    // Reference to media handler


        //constractor drawing canvas instance
        public ToolbarManager(DrawingCanvas drawingCanvas, MediaHandler mediaHandler) {
            this.drawingCanvas = drawingCanvas;
            this.mediaHandler = mediaHandler;
        }


        //creates and return Configured HBox containing toolbar controls
        public HBox createToolbar() {
            // Create toolbar buttons
            Button undoBtn = createButton("Undo", drawingCanvas::undo);
            Button redoBtn = createButton("Redo", drawingCanvas::redo);
            Button clearBtn = createButton("Clear", drawingCanvas::clearCanvas);
            Button saveBtn = createButton("Save", drawingCanvas::saveCanvas);
            Button audioBtn = createButton("Audio", mediaHandler::addAudio);
            Button videoBtn = createButton("Video", mediaHandler::addVideo);

            // Create and configure toolbar container
            HBox toolbar = new HBox(10);
            toolbar.setPadding(new Insets(10));

            // Add all controls to toolbar
            toolbar.getChildren().addAll(
                    drawingCanvas.getToolSelector(),
                    new Label("Stroke:"), drawingCanvas.getStrokeColorPicker(),
                    new Label("Fill:"), drawingCanvas.getFillColorPicker(),
                    new Label("Size:"), drawingCanvas.getSizeSlider(),
                    drawingCanvas.getFontSelector(), drawingCanvas.getTextInput(),
                    undoBtn, redoBtn, clearBtn, saveBtn, audioBtn, videoBtn
            );
            return toolbar;
        }


        //create a toolbar button
        private Button createButton(String text, Runnable action) {
            Button button = new Button(text);
            button.setOnAction(e -> action.run());
            return button;
        }
    }


    //shows an error diolog
    private static void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}