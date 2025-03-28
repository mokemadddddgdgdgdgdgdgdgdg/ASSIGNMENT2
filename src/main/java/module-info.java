module mokema.asignment2 {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.desktop;
    requires javafx.swing;
    requires javafx.media;


    opens mokema.asignment2 to javafx.fxml;
    exports mokema.asignment2;
}