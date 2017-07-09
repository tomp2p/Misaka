package ch.uzh.model;

import ch.uzh.controller.*;
import ch.uzh.helper.*;
import com.google.gson.Gson;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.util.Pair;
import net.tomp2p.dht.FutureGet;
import net.tomp2p.futures.BaseFutureAdapter;
import net.tomp2p.peers.PeerAddress;
import org.mindrot.jbcrypt.BCrypt;

import javax.crypto.NoSuchPaddingException;
import java.io.*;
import java.math.BigInteger;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Created by jesus on 11.03.2017.
 */
public class MainWindow /*implements CallBack*/{
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

    public static boolean futurputSuccess = false;


    private AnchorPane mainPane;
    private AnchorPane msgWindowPane;
    private AnchorPane friendListPane;
    private AnchorPane menuOverlay;
    private AnchorPane callWindow;

    private P2POverlay p2p;
    private List<FriendsListEntry> friendsList;
    private ObservableList<FriendRequestMessage> friendRequestsList;
    private ScheduledExecutorService scheduler;

    private String currentChatPartner;

    Key publicKey;
    Key privateKey;

    byte[] publicKeySerialized;
    byte[] privateKeySerialized;


    public PrivateUserProfile getUserProfile() {
        return userProfile;
    }

    private PrivateUserProfile userProfile;
    private EncryptedPrivateUserProfile encrypteduserProfile;

    public String getCurrentChatpartner(){
        return currentChatPartner;
    }

    public void setCurrentChatpartner(String userID){
        currentChatPartner = userID;
    }




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

    public MainWindow( P2POverlay p2p){
        this.p2p = p2p;
        //p2p.addListener(this);
    }


    private void drawMainWindow() throws Exception { //TODO: exception handling
        stage.setOnCloseRequest(e -> {
            shutdown();
        });
        System.err.println("2222222~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
        System.err.println("FXML resource: " + getClass().getResource("ch/uzh/csg/p2p/screens/MainWindow.fxml"));
        System.err.println("FXML resource: " + getClass().getResource("/MainWindow.fxml"));
        System.err.println("FXML resource: " + getClass().getResource("MainWindow.fxml"));
        System.err.println("FXML resource: " + getClass().getResource("/view/MainWindow.fxml"));
        System.err.println("2222222~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/MainWindow.fxml"));
        mainWindowController = new MainWindowController(stage, p2p);

        msgWindowController = new MsgWindowController(mainWindowController, this, p2p);  // <--- THIS
        mainWindowController.setMsgWindowController(msgWindowController);

        friendListController = new FriendListController(mainWindowController, p2p, this);   // <--- THIS
        mainWindowController.setFriendListController(friendListController);

        menuOverlayController = new MenuOverlayController(mainWindowController, p2p, this);   // <--- THIS
        mainWindowController.setMenuOverlayController(menuOverlayController);

        callWindowController = new CallWindowController(mainWindowController, p2p, this);
        mainWindowController.setCallWindowController(callWindowController);


        loader.setController(mainWindowController);


        mainWindowController.setMainWindow(this);

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

    public void acceptFriendRequest(FriendRequestMessage message) {
        // Add user
        addFriend(message.getSenderUserID());

        // Remove friend request
        friendRequestsList.remove(message);

        // Save User Profile
        savePrivateUserProfileNonBlocking();
        System.err.println("userid: " + message.getSenderUserID() + "messagetxt: " +  message.getMessageText() + "peeraddress: " + message.getSenderPeerAddress());
        FriendsListEntry newFriend = new FriendsListEntry(message.getSenderUserID());
        newFriend.setPeerAddress(message.getSenderPeerAddress());
        friendListController.updateFriends();
       /* Pair<Boolean, String> result = sendFriendRequest(message.getSenderUserID(), "hi, pls accept 2");

        if (result.getKey() == true) {
            System.err.println("response friend request sent");
        } else {
            System.err.println("response friend request ERROR");
        }*/
    }

    public void declineFriendRequest(FriendRequestMessage message) {
        // Remove friend request
        friendRequestsList.remove(message);

        // Save User Profile
        savePrivateUserProfileNonBlocking();
    }

    public void handleIncomingFriendRequest(FriendRequestMessage requestMessage) {
        // Ignore requests from users already in the list
        if (userProfile.isFriendsWith(requestMessage.getSenderUserID())) {
            return;
        }



        // Ignore multiple requests
/*        if (userProfile.hasFriendRequestFromUser(requestMessage.getSenderUserID())) {
            return;
        }*/

        // Add friend request
        friendRequestsList.add(requestMessage);

        // Save the change
        boolean isSaved = savePrivateUserProfileNonBlocking();
        if (isSaved == true){
            System.err.println("saved succesfully");
        } else{
            System.err.println("saved UNsuccesfully");

        }

        // Show visual message
        int i = 0;
        int i2 = 0;
        System.err.println("friendlistsize: " + friendsList.size());
        for(FriendsListEntry e : friendsList){
            System.err.println("i: " + i++);
            System.err.println(e.toString());
        }
        System.err.println(" BEFORE, AFTER");
        FriendListController.showIncomingFriendRequest(requestMessage);
        acceptFriendRequest(requestMessage);
        System.err.println("friendlistsize: " + friendsList.size());
        for(FriendsListEntry e : friendsList){
            System.err.println("i2: " + i2++);
            System.err.println("friendlistitem: " + e.getUserID());
        }

    }

    public Pair<Boolean, String> sendFriendRequest(String userID, String messageText) {
        // Check if user already exists in friends list
        if (getFriendsListEntry(userID) != null) {
            System.err.println("User already in friendslist");
            return new Pair<>(false, "User already in friendslist");
        }

        // Check if user exists in the network
        if (!existsUser(userID)) {
            System.err.println("User was not found");
            return new Pair<>(false, "User was not found");
        }

        // Get public profile of friend we want to add
        String jsonFriendProfile = (String) p2p.getBlocking(userID);
        Gson gson = new Gson();
        PublicUserProfile friendProfile = gson.fromJson(jsonFriendProfile, PublicUserProfile.class);
        // Create friend request message
        FriendRequestMessage friendRequestMessage = new FriendRequestMessage("FriendRequestMessage", p2p.getPeerAddress(), userProfile.getUserID(), messageText);

        Gson gsonFriendRequest = new Gson();
        String jsonFriendRequest = gsonFriendRequest.toJson(friendRequestMessage);

        // Try to send direct friend request first, (in case user is online)
        boolean sendDirect = false;
        if (friendProfile.getPeerAddress() != null) {
            sendDirect = p2p.sendBlocking(friendProfile.getPeerAddress(), jsonFriendRequest);
        }

        // If that failed, or other has no peer address, append to pub profile of other friend
        if (sendDirect == false) {
            // Friend is not online, append to public profile
            friendProfile.getPendingFriendRequests().add(jsonFriendRequest);
            boolean now = p2p.putNonBlocking(userID, friendProfile);

            while(!futurputSuccess){
                donothing();
            }
            futurputSuccess = false;

            if (now == false) {
                return new Pair<>(false, "Error sending friend request");
            }
        }

        System.err.println("HEX HEX ~~~~~~~~~~~~~~~SENDING FRIEND REQ MYSLF~~~~~~~~~~~~~ HEX HEX");
        System.err.println("HEX misaka:" + toHex(userProfile.getUserID()));


        // Addd as friend
        if (addFriend(userID) == false) {
            return new Pair<>(false, "Error, adding the friend");
        }

        return new Pair<>(true, "Friend request to " + userID + " was sent");
    }

    public void donothing(){
        System.err.println("nothing");
    }



    public FriendsListEntry getFriendsListEntry(String userID) {
        if(friendsList == null){
            return null;
        }
        for (FriendsListEntry e : friendsList) {
            if (e.getUserID().equals(userID)) {
                return e;
            }
        }
        return null;
    }

    public List<FriendsListEntry> getFriendsList() {
        if(friendsList == null){
            return null;
        }else{
            return friendsList;
        }
    }

    public boolean addFriend(String userID) {
        // Add to list
        System.err.println("ADD THIS BFF: " + userID);
        friendsList.add(new FriendsListEntry(userID));
        //friendsList.sort(new FriendsListComparator());
        //mainController.sortFriendsListView();

        // Send ping
        pingUser(userID, true, true);

        friendListController.updateFriends();

        // Save profile
        return savePrivateUserProfileNonBlocking();
    }

    private void pingUser(String userID, boolean onlineStatus, boolean replyPongExpected) {
        p2p.getNonBLocking(userID, new BaseFutureAdapter<FutureGet>() {
            @Override
            public void operationComplete(FutureGet f) throws Exception {
                FriendsListEntry friendsListEntry = getFriendsListEntry(userID);
                assert (friendsListEntry != null);
                if (f.isSuccess()) {
                    Gson publicUserProfileGson = new Gson();
                    String publicUserProfileJson = (String) f.data().object();

                    PublicUserProfile publicUserProfile = publicUserProfileGson.fromJson(publicUserProfileJson, PublicUserProfile.class);
                    // Set peer address in friendslist
                    PeerAddress peerAddress = publicUserProfile.getPeerAddress();
                    friendsListEntry.setPeerAddress(peerAddress);

                    // Send ping
                    if (peerAddress != null) {
                        pingAddress(publicUserProfile.getPeerAddress(), onlineStatus, replyPongExpected);
                    }
                } else {
                    // Can't find other peer, maybe he deleted his account? -> show offline
                    System.out.println("User " + userID + " doesnt seem to exist anymore");
                    friendsListEntry.setOnline(false);
                    friendsListEntry.setPeerAddress(null);
                }
            }
        });
    }

    public void pingAddress(PeerAddress pa, boolean onlineStatus, boolean replyPongExpected) {
        // Send ping
        OnlineStatusMessage msg = new OnlineStatusMessage("OnlineStatusMessage", p2p.getPeerAddress(), userProfile.getUserID(), onlineStatus, replyPongExpected);
        Gson onlineStatusMessageGson = new Gson();
        String onlineStatusMessageJson = onlineStatusMessageGson.toJson(msg);

        p2p.sendNonBlocking(pa, onlineStatusMessageJson, false);
    }

    public void logout() {


        // Tell "friends" that i'm going offline
        pingAllFriends(false);

        // Set PeerAddress in public Profile to null
        Object objectPublicUserProfile = p2p.getBlocking(userProfile.getUserID());
        if (objectPublicUserProfile == null) {
            System.out.println("Could not retrieve public userprofile");
            return;
        }

        Gson publicUserprofileGson = new Gson();
        String publicUserProfileJson = (String) objectPublicUserProfile;


        PublicUserProfile publicUserProfile = publicUserprofileGson.fromJson(publicUserProfileJson, PublicUserProfile.class);
        publicUserProfile.setPeerAddress(null);

        String newPublicUserProfileJson = publicUserprofileGson.toJson(publicUserProfile);


        boolean now = p2p.putNonBlocking(userProfile.getUserID(), newPublicUserProfileJson);

        while(!futurputSuccess){
            donothing();
        }
        futurputSuccess = false;

        if (now == false) {
            System.out.println("Could not update peer address in public user profile");
            return;
        }

        savePrivateUserProfileNonBlocking();

        p2p.setObjectDataReply(null);

        userProfile = null;
        friendsList = null;
        shutdown();


    }

    private void pingAllFriends(boolean onlineStatus) {
        if (friendsList == null){
            return;
        }
        for (FriendsListEntry entry : friendsList) {
            String userID = entry.getUserID();

            // For friends that are online, send direct to their PeerAddress
            if (entry.isOnline()) {
                OnlineStatusMessage ping = new OnlineStatusMessage("OnlineStatusMessage", p2p.getPeerAddress(), userProfile.getUserID(),
                        onlineStatus, onlineStatus);
                Gson pingAllFriendsGson = new Gson();
                String pingAllFriendsJson = pingAllFriendsGson.toJson(ping);

                p2p.sendNonBlocking(entry.getPeerAddress(), pingAllFriendsJson, false);
            } // For friends that are offline and in the case that we want to tell
            // them we're coming online, use pingUser method to first check for
            // their peerAddress (if any).
            else if (!entry.isOnline() && onlineStatus == true) {
                pingUser(userID, onlineStatus, onlineStatus);

            }
        }
    }

    public void shutdown() {
        if (userProfile != null) {
            logout();
        }
        // Shutdown Tom P2P stuff
        p2p.shutdown();
        Platform.exit();
        System.exit(0);
    }

    public void handleIncomingOnlineStatus(OnlineStatusMessage msg) {
        synchronized (this) {
            FriendsListEntry e = getFriendsListEntry(msg.getSenderUserID());

            // If friend is in friendslist
            if (e != null) {

                // Show notification if user came online
/*                if (msg.isOnline() && !e.isOnline()) {
                    Notifications.create().text("User " + msg.getSenderUserID() + " just came online")
                            .showInformation();
                }*/

                // Set online/offline
                e.setOnline(msg.isOnline());
                e.setPeerAddress(msg.getSenderPeerAddress());
                e.setWaitingForHeartbeat(false);

                //sortFriendsListView();

                // Send pong back if wanted
                if (msg.isReplyPongExpected()) {
                    pingAddress(msg.getSenderPeerAddress(), true, false);
                }
            }
        }
    }

    public String getUserID() {
        return (userProfile != null) ? userProfile.getUserID() : "error";
    }

    public void sendChatMessage(String text, FriendsListEntry friendsListEntry) {
        ChatMessage chatMessage = new ChatMessage("ChatMessage", p2p.getPeerAddress(), userProfile.getUserID(), text);
        System.err.println("SENDING THIS: peeraddress: " + p2p.getPeerAddress() + " userID: " + userProfile.getUserID() + " text: " + text);
        System.err.println("SENDING TO: peeraddress: " + friendsListEntry.getPeerAddress() + " userID: " + friendsListEntry.getUserID() + " text: " + text);

        Gson chatMessageGson = new Gson();
        String ChatMessageJson = chatMessageGson.toJson(chatMessage);


        p2p.sendNonBlocking(friendsListEntry.getPeerAddress(), ChatMessageJson, false);
    }

    public void handleIncomingChatMessage(ChatMessage msg) {
            FriendsListEntry e = getFriendsListEntry(msg.getSenderUserID());

            // If friend is in friendslist
            if (e != null) {
                System.err.println("Message received from: " + msg.getSenderUserID() + " Messagetext: " + msg.getMessageText());
                //openChat.showIncomingChatMessage(msg.getSenderUserID(), msg.getMessageText());
                msgWindowController.addChatBubble(msg.getMessageText(), msg.getSenderUserID(), false);
            }
            else{
                System.err.println("That's my purse, i don't know you!");
            }
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
    public String toHex(String arg) {
        return String.format("%x", new BigInteger(1, arg.getBytes(/*YOUR_CHARSET?*/)));
    }


    public Pair<Boolean, String> createAccount(String userID, String password) {
        // Check if the user is already in the friendslist

        // Check if account exists
        if (p2p.getBlocking(userID) != null) {
            System.err.println("Could not create user account. UserID already taken.");
            return new Pair<>(false, "Could not create user account. UserID already taken."); //TODO: LOGIN NOW

        }

        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            KeyPair kp = kpg.genKeyPair();
            publicKeySerialized = kp.getPublic().getEncoded();
            privateKeySerialized = kp.getPrivate().getEncoded();
            publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(publicKeySerialized));
            privateKey = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(privateKeySerialized));
        } catch (InvalidKeySpecException | NoSuchAlgorithmException e){
            e.printStackTrace();

        }

        // Create private UserProfile
        userProfile = new PrivateUserProfile(userID, password, privateKeySerialized);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            Encryption.encrypt(password, (Serializable) userProfile, baos);
            byte[] encryptedArray = baos.toByteArray();
            encrypteduserProfile = new EncryptedPrivateUserProfile(encryptedArray);
            baos.close();
        } catch(IOException | NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException e){
            e.printStackTrace();
        }


        // TODO: Encrypt it with password
        boolean saving = savePrivateUserProfileNonBlocking();



        System.err.println("saving is: " + saving);




        // Create public UserProfile
        PublicUserProfile publicUserProfile;
        publicUserProfile = new PublicUserProfile(userID,    null, publicKeySerialized);
        Gson gson = new Gson();
        String jsonPublic = gson.toJson(publicUserProfile);


        boolean now = p2p.putNonBlocking(userID, jsonPublic);

        while(!futurputSuccess){
            donothing();
        }
        futurputSuccess = false;


        if (now) {
            login(userID, password);
        } else {
            System.err.println("Network DHT error. Could not save public UserProfile");
        }

        return new Pair<>(true, "ok for now");
    }

/*    public void futurePutIsASuccess(){
        System.out.println("Called me successfully!");
        // Create public UserProfile
        PublicUserProfile publicUserProfile;


        publicUserProfile = new PublicUserProfile(userProfile.getUserID(),    null, publicKeySerialized);
        Gson gson = new Gson();
        String jsonPublic = gson.toJson(publicUserProfile);


        boolean now = p2p.putNonBlocking(userProfile.getUserID(), jsonPublic);

        try{
            TimeUnit.SECONDS.sleep(10);}
        catch(Exception e){

        }

        if (now) {

            login(userProfile.getUserID(), password);
        } else {
            System.err.println("Network DHT error. Could not save public UserProfile");
        }

    }*/


    private void pingAllOnlineFriends() {
        for (FriendsListEntry entry : friendsList) {
            if (entry.isOnline()) {
                // If friend din't reply since last call, set him offline
                if (entry.isWaitingForHeartbeat()) {
                    entry.setOnline(false);
                   // sortFriendsListView();
                }

                // Flag friend until he replies
                entry.setWaitingForHeartbeat(true);

                OnlineStatusMessage ping = new OnlineStatusMessage("OnlineStatusMessage", p2p.getPeerAddress(), userProfile.getUserID(),
                        true, true);
                Gson pingAllOnlineFriendsGson = new Gson();
                String pingAllOnlineFriendsJson = pingAllOnlineFriendsGson.toJson(ping);

                p2p.sendNonBlocking(entry.getPeerAddress(), pingAllOnlineFriendsJson, false);
            }
        }

    }

    public Pair<Boolean, String> login(String userID, String password) {


/*        if (BCrypt.checkpw(insecurePassword, hashed))
            System.out.println("It matches");
        else
            System.out.println("It does not match");

*/

        // Get userprofile if password and username are correct
        Object getResult = p2p.getBlocking(userID + password);
        if (getResult == null) {
            return new Pair<>(false, "Login data not valid, Wrong UserID/password?");
        }

        System.err.println("Whatdo we have here?");
        System.err.println(getResult);
        Gson gson = new Gson();
        encrypteduserProfile = gson.fromJson((String) getResult, EncryptedPrivateUserProfile.class);
        System.err.println("Whatdo we have here FO REAL?");
        System.err.println(encrypteduserProfile.getEncryptedProfile().toString());
        ByteArrayInputStream bais = new ByteArrayInputStream(encrypteduserProfile.getEncryptedProfile());
        try {
            userProfile = (PrivateUserProfile) Encryption.decrypt(password, bais);
        }catch(IOException | NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException e){
            e.printStackTrace();
        }
        System.err.println("userID: " + userProfile.getUserID());
        System.err.println("pw: " + userProfile.getPassword());
        if (!userProfile.getFriendsList().isEmpty()) {
            System.err.println("friends: " + userProfile.getFriendsList().get(0));
        }
        //userProfile = (PrivateUserProfile) getResult;


        // Get public user profile
        Object objectPublicUserProfile = p2p.getBlocking(userID);
        if (objectPublicUserProfile == null) {
            System.err.println("Could not retrieve public userprofile");
            return new Pair<>(false, "Could not retrieve public userprofile");
        }

        PublicUserProfile publicUserProfile = gson.fromJson((String) objectPublicUserProfile, PublicUserProfile.class);
        //PublicUserProfile publicUserProfile = (PublicUserProfile) objectPublicUserProfile;

        // **** FRIENDS LIST ****
        // Reset all friends list entries to offline and unkown peer address
        for (FriendsListEntry e : userProfile.getFriendsList()) {
            e.setOnline(false);
            e.setPeerAddress(null);
            e.setWaitingForHeartbeat(false);
        }
        System.err.println("here is the sneqky error");

        friendsList = new ArrayList<>();
        friendsList = userProfile.getFriendsList();

        friendRequestsList = FXCollections.observableList(userProfile.getFriendRequestsList());






        // Set current IP address in public user profile
        publicUserProfile.setPeerAddress(p2p.getPeerAddress());
        String jsonPublic = gson.toJson(publicUserProfile);

        // Save public user profile
        boolean now = p2p.putNonBlocking(userID, jsonPublic);

        while(!futurputSuccess){
            donothing();
        }
        futurputSuccess = false;

        if (now == false) {
            System.err.println("Could not update public user profile");
            return new Pair<>(false, "Could not update public user profile");
        }

        // Set reply handler
        p2p.setObjectDataReply(new ObjectReplyHandler(this));

        // Send out online status to all friends
        pingAllFriends(true);

        // Schedule new thread to check periodically if friends are still online
        scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(() -> {
            pingAllOnlineFriends();
        }, 10, 10, SECONDS);

        System.err.println("Login successful");
        return new Pair<>(true, "Login successful");
    }

    private boolean savePrivateUserProfile() {
        Gson gson = new Gson();
        String json = gson.toJson(encrypteduserProfile);
        System.err.println("IMMA GONNA PRINT MY JSON");
        System.err.println(json);
        System.err.println("PRINTED MY JSON");

        // TODO: encrypt before saving

        boolean now = p2p.putNonBlocking(userProfile.getUserID() + userProfile.getPassword(), json);

        while(!futurputSuccess){
            donothing();
        }
        futurputSuccess = false;

        return now;
    }

    public boolean savePrivateUserProfileNonBlocking() {
        Gson gson = new Gson();
        String json = gson.toJson(encrypteduserProfile);
        System.err.println("IMMA GONNA PRINT MY JSON NON BLOCKING");
        System.err.println(json);
        System.err.println("PRINTED MY JSON");

        boolean now = p2p.putNonBlocking(userProfile.getUserID() + userProfile.getPassword(), json);

        while(!futurputSuccess){
            donothing();
        }
        futurputSuccess = false;

        return now;
    }

    private boolean savePrivateUserProfileNonBlockingReg() {
        Gson gson = new Gson();
        String json = gson.toJson(encrypteduserProfile);
        System.err.println("IMMA GONNA PRINT MY JSON NON BLOCKING");
        System.err.println(json);
        System.err.println("PRINTED MY JSON");

        return p2p.putNonBlockingReg(userProfile.getUserID() + userProfile.getPassword(), json);
    }

    public void handleIncomingAudioFrame(AudioFrame frame) {
        if (true) {
            callWindowController.handleIncomingAudioFrame(frame);
        }
    }

    /**
     *
     * @param userID
     * @return
     */
    public boolean existsUser(String userID) {
        return (p2p.getBlocking(userID) != null);
    }

/*    public boolean addFriend(String userID) {
        // Add to list
        friendsList.add(new FriendsListEntry(userID));
        friendsList.sort(new FriendsListComparator());
        mainController.sortFriendsListView();

        // Send ping
        pingUser(userID, true, true);

        // Save profile
        return savePrivateUserProfileNonBlocking();
    }*/

}
