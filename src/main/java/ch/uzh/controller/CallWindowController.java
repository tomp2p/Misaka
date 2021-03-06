package ch.uzh.controller;


import ch.uzh.helper.*;
import ch.uzh.model.FriendsListEntry;
import ch.uzh.model.MainWindow;
import com.github.sarxos.webcam.Webcam;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.sound.sampled.LineUnavailableException;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Level;

import static java.util.concurrent.TimeUnit.SECONDS;

public class CallWindowController {

    private static final Logger log = LoggerFactory.getLogger(CallWindowController.class);

    private MainWindow mainWindow;
    private MainWindowController mainWindowController;
    private VideoStuff videoUtils;
    private P2POverlay p2p;
    private FriendsListEntry friendsListEntry;
    private CallHandler callHandler;
    private ScheduledExecutorService scheduler;


    @FXML
    public HBox btnWrapperVideo;
    @FXML
    private Button muteMicrophoneBtn;
    @FXML
    private Button muteVideoBtn;
    @FXML
    private Label microphoneLbl;
    @FXML
    private Label cameraLbl;
    @FXML
    private HBox videoUserWrapper;
    @FXML
    private ImageView videoUser1;
    @FXML
    private ImageView meImageView;
    @FXML
    private Button hideMyselfBtn;
    @FXML
    private Button endCallBtn;


    public CallWindowController(MainWindowController mainWindowController, P2POverlay p2p, MainWindow mainWindow) {
        this.mainWindowController = mainWindowController;
        this.videoUtils = new VideoStuff();
        this.p2p = p2p;
        this.mainWindow = mainWindow;
    }

    public void setFriendsListEntry(FriendsListEntry friendsListEntry) {
        this.friendsListEntry = friendsListEntry;
    }

    @FXML
    private void initialize() {
        log.info("CallWindowController is initializing");
        muteMicrophoneBtn.setVisible(false);

        endCallBtn.setOnAction((event) -> {
                    log.info("CLICK END call btn");
                    //stop video
                    if (scheduler != null) {
                        scheduler.shutdown();
                    }
                    stopTransmitting();
            mainWindow.setStopsound(false);

            mainWindowController.drawMsgPane();
                }
        );


    }

    public static BufferedImage resize(BufferedImage img, int newW, int newH) throws IOException {
        return Thumbnails.of(img).size(newW, newH).asBufferedImage();
    }

    public void showVideo(VideoFrame vidFrame) {
        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(vidFrame.getData()));
            Image fxImage = SwingFXUtils.toFXImage(image, null);
            videoUser1.setImage(fxImage);

        } catch (Exception e) {
            e.printStackTrace();
        }


    }

    public void takePicture() {
        Webcam webcam = Webcam.getDefault().getDefault();
        webcam.open();

        BufferedImage image = null;
        // get image
        try {
            image = resize(webcam.getImage(), 200, 200);
            Image fxImage = SwingFXUtils.toFXImage(image, null);
            meImageView.setImage(fxImage);
            mainWindow.sendVideoFrame(image, mainWindow.getFriendsListEntry(mainWindow.getCurrentChatpartner()));
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            // save image to PNG file
            //ImageIO.write(image, "JPG", new File("test.jpg"));
        } catch (Exception e) {

        }
    }

    public void startVideo() {
        scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(() -> {
            takePicture();
        }, 1, 1, SECONDS);
    }

    public void showMicrophone() {
        try {
            log.info("show pic 2");
            log.info(getClass().getResource("/img/telw.png").toString());
            File file = new File("/img/andr.png");
            Image image = new Image(file.toURI().toString());
            meImageView.setImage(image);
            meImageView.setVisible(true);
            meImageView.setDisable(false);
            log.info("pic set");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void disableMicPic() {
        meImageView.setImage(null);
    }

    public void startTransmitting() {
        // Create new call
        callHandler = new CallHandler(mainWindow, p2p, getFriendsListEntry());
        try {
            callHandler.start();
        } catch (LineUnavailableException ex) {
            stopTransmitting();
            log.info("LineUnavailableException");
        }
    }

    public void stopTransmitting() {
        if (callHandler == null) {
            return;
        }
        callHandler.stop();
        try {
            Thread.sleep(100);
        } catch (InterruptedException ex) {
            java.util.logging.Logger.getLogger(CallWindowController.class.getName()).log(Level.SEVERE, null, ex);
        }
        callHandler = null;
    }

    public void handleIncomingAudioFrame(AudioFrame msg) {
        callHandler.addAudioFrame(msg.getData());
    }

    public FriendsListEntry getFriendsListEntry() {
        return friendsListEntry;
    }


}
