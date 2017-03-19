package ch.uzh.model;

import ch.uzh.controller.*;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import java.io.IOException;

/**
 * Created by jesus on 11.03.2017.
 */
public class MainWindow {
    private Stage stage;
    private int id;
    private String ip;
    private String username;
    private String password;
    private boolean bootstrapNode;

    private MainWindowController mainWindowController;
    private MsgWindowController msgWindowController;
    private FriendListController friendListController;
    private MenuOverlayController menuOverlayController;
    private CallWindowController callWindowController;


    private AnchorPane mainPane;
    private AnchorPane msgWindowPane;
    private AnchorPane friendListPane;
    private AnchorPane menuOverlay;
    private AnchorPane callWindow;



    public void draw(Stage stage, int id, String ip, String username, String password,
                     boolean bootstrapNode) throws Exception {
        this.stage = stage;
        this.id = id;
        this.ip = ip;
        this.username = username;
        this.password = password;
        this.bootstrapNode = bootstrapNode;
        drawMainWindow();
    }

    private void drawMainWindow() throws Exception { //TODO: exception handling
        System.err.println("2222222~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
        System.err.println("FXML resource: " + getClass().getResource("ch/uzh/csg/p2p/screens/MainWindow.fxml"));
        System.err.println("FXML resource: " + getClass().getResource("/MainWindow.fxml"));
        System.err.println("FXML resource: " + getClass().getResource("MainWindow.fxml"));
        System.err.println("FXML resource: " + getClass().getResource("/view/MainWindow.fxml"));
        System.err.println("2222222~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/MainWindow.fxml"));
        mainWindowController = new MainWindowController(stage);

        msgWindowController = new MsgWindowController(mainWindowController);  // <--- THIS
        mainWindowController.setMsgWindowController(msgWindowController);

        friendListController = new FriendListController(mainWindowController);   // <--- THIS
        mainWindowController.setFriendListController(friendListController);

        menuOverlayController = new MenuOverlayController(mainWindowController);   // <--- THIS
        mainWindowController.setMenuOverlayController(menuOverlayController);

        callWindowController = new CallWindowController(mainWindowController);
        mainWindowController.setCallWindowController(callWindowController);


        loader.setController(mainWindowController);


        //mainWindowController.setMainWindow(this);

        mainPane = loader.load();

        drawFriendList();
        drawMsgWindow();
        drawmenuOverlay();
        drawCallWindow();

        mainWindowController.setFriendlistPane(friendListPane);
        mainWindowController.setMsgWindowPane(msgWindowPane);
        mainWindowController.setCallWindowPane(callWindow);
        mainWindowController.setMenuOverlayPane(menuOverlay);


        mainWindowController.setLeftPane(friendListPane);

        mainWindowController.drawMsgPane();

        Scene scene = new Scene(mainPane);

        scene.getStylesheets().add("/css/MainWindow.css");


        stage.getIcons().add(new Image(getClass().getResourceAsStream("/img/favicon.png")));
        stage.setTitle("Misaka - Main");
        stage.setScene(scene);

        stage.setMinWidth(800);
        stage.setMinHeight(480);
       // stage.centerOnScreen();

        stage.show();

        stage.setOnCloseRequest(new EventHandler<WindowEvent>() {

            public void handle(WindowEvent event) {
                stage.close();
                System.exit(0);
            }
        });


    }

    private void drawFriendList() throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/FriendList.fxml"));
        loader.setController(friendListController);
        friendListPane = loader.load();
    }

    private void drawMsgWindow() throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/MsgWindow.fxml"));
        loader.setController(msgWindowController);
        msgWindowPane = loader.load();
    }

    private void drawCallWindow() throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/CallWindow.fxml"));
        loader.setController(callWindowController);
        callWindow = loader.load();
    }

    private void drawmenuOverlay() throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/MenuOverlay.fxml"));
        loader.setController(menuOverlayController);
        menuOverlay = loader.load();
    }

}
