package JarvisAI.ui;

import JarvisAI.commands.TimerCommand;
import JarvisAI.core.JarvisCore;
import JarvisAI.database.DatabaseManager;
import JarvisAI.vision.FocusTracker;
import JarvisAI.voice.SpeechRecognizer;
import JarvisAI.voice.TextToSpeech;

import javafx.animation.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.io.File;
import java.io.FileInputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

public class JarvisUI extends Application {

    private VBox chatBox;
    private ScrollPane scrollPane;
    private TextField inputField;
    private Label statusLabel;
    private Label focusScoreLabel;
    private Label focusBarLabel;
    private Label clockLabel;
    private Label cpuLabel;
    private Label ramLabel;
    private Label dateLabel;
    private Label gestureLabel;
    private Label objectLabel;
    private Button micBtn;
    private Button fullscreenBtn;
    private ImageView webcamView;
    private FocusTracker focusTracker;
    private Stage primaryStage;
    private HBox typingBubble;
    private Timeline typingDots;
    private boolean isListening  = false;
    private boolean wakeWordActive = true;
    private boolean isFullscreen  = false;
    private double xOffset = 0, yOffset = 0;

    private static final String ICON_PATH = "jarvis_icon.png";

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;

        try {
            File f = new File(ICON_PATH);
            if (f.exists()) stage.getIcons().add(new Image(new FileInputStream(f)));
        } catch (Exception ignored) {}

        TimerCommand.setUICallback(msg -> addJarvisMessage(msg));

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color:#020b18;-fx-border-color:#00d4ff;-fx-border-width:1px;");
        root.setTop(buildHeader(stage));

        HBox mainArea = new HBox(0, buildChatArea(), buildRightPanel());
        HBox.setHgrow(mainArea.getChildren().get(0), Priority.ALWAYS);
        root.setCenter(mainArea);
        root.setBottom(buildBottom());

        typingBubble = buildTypingBubble();

        Scene scene = new Scene(root, 1100, 680);
        scene.setFill(Color.TRANSPARENT);
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setScene(scene);
        stage.setTitle("J.A.R.V.I.S");
        stage.show();

        inputField.requestFocus();
        startFocusTracker();
        startClock();
        startSystemStats();
        startWakeWordListener();
    }

    // ── WAKE WORD ─────────────────────────────────────────────────
    private void startWakeWordListener() {
        Thread t = new Thread(() -> {
            System.out.println("Wake word listener active. Say 'Hey Jarvis'.");
            while (wakeWordActive) {
                try {
                    if (!isListening) {
                        String heard = SpeechRecognizer.listenShort();
                        if (heard != null && !heard.isEmpty()) {
                            String l = heard.toLowerCase();
                            if (l.contains("hey jarvis") || l.contains("jarvis")) {
                                Platform.runLater(() -> {
                                    setStatus("● WAKE WORD DETECTED!", "#ff44ff");
                                    addJarvisMessage("Yes? I'm listening...");
                                    TextToSpeech.speak("Yes, I'm listening.");
                                    activateVoiceInput();
                                });
                            }
                        }
                    }
                    Thread.sleep(500);
                } catch (Exception e) {
                    try { Thread.sleep(1000); } catch (Exception ignored) {}
                }
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private void activateVoiceInput() {
        if (isListening) return;
        isListening = true;
        micBtn.setStyle(micActiveStyle());
        setStatus("● LISTENING...", "#ff4488");
        new Thread(() -> {
            String heard = SpeechRecognizer.listen();
            Platform.runLater(() -> {
                isListening = false;
                micBtn.setStyle(micIdleStyle());
                if (!heard.isEmpty()) {
                    inputField.setText(heard);
                    setStatus("● HEARD: " + heard, "#ffaa00");
                    send();
                } else {
                    setStatus("● NOTHING HEARD — TRY AGAIN", "#ff4444");
                    new Thread(() -> {
                        try { Thread.sleep(2000); } catch (Exception ignored) {}
                        Platform.runLater(() -> setStatus("● READY  |  Say 'Hey Jarvis'", "#00ff88"));
                    }).start();
                }
            });
        }).start();
    }

    // ── FULLSCREEN ────────────────────────────────────────────────
    private void toggleFullscreen() {
        isFullscreen = !isFullscreen;
        primaryStage.setFullScreen(isFullscreen);
        fullscreenBtn.setText(isFullscreen ? "⊠" : "⛶");
    }

    // ── LIVE CLOCK ────────────────────────────────────────────────
    private void startClock() {
        Timeline clock = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            clockLabel.setText(new SimpleDateFormat("HH:mm:ss").format(new Date()));
            dateLabel.setText(new SimpleDateFormat("EEE, MMM dd yyyy").format(new Date()));
        }));
        clock.setCycleCount(Animation.INDEFINITE);
        clock.play();
    }

    // ── SYSTEM STATS ──────────────────────────────────────────────
    private void startSystemStats() {
        Timeline stats = new Timeline(new KeyFrame(Duration.seconds(3), e -> {
            new Thread(() -> {
                Runtime rt = Runtime.getRuntime();
                long usedMem = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
                int cpus = rt.availableProcessors();
                Platform.runLater(() -> {
                    cpuLabel.setText("CPU CORES: " + cpus);
                    ramLabel.setText("JVM RAM: " + usedMem + " MB");
                });
            }).start();
        }));
        stats.setCycleCount(Animation.INDEFINITE);
        stats.play();
    }

    // ── TYPING BUBBLE ─────────────────────────────────────────────
    private HBox buildTypingBubble() {
        Label d1 = dot(), d2 = dot(), d3 = dot();
        HBox dots = new HBox(5, d1, d2, d3);
        dots.setAlignment(Pos.CENTER_LEFT);
        Label prefix = new Label("J.A.R.V.I.S  is thinking...");
        prefix.setStyle("-fx-font-family:'Courier New';-fx-font-size:10px;-fx-text-fill:#00d4ff;-fx-font-weight:bold;");
        VBox bubble = new VBox(4, prefix, dots);
        bubble.setStyle("-fx-background-color:#031525;-fx-padding:10 14 10 14;" +
                        "-fx-border-color:#00d4ff transparent transparent transparent;" +
                        "-fx-border-width:1 0 0 0;-fx-background-radius:3;");
        HBox row = new HBox(bubble);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(2, 40, 2, 0));
        row.setVisible(false); row.setManaged(false);

        typingDots = new Timeline(
            new KeyFrame(Duration.ZERO,        e -> { d1.setOpacity(1); d2.setOpacity(0.3); d3.setOpacity(0.3); }),
            new KeyFrame(Duration.millis(300), e -> { d1.setOpacity(0.3); d2.setOpacity(1); d3.setOpacity(0.3); }),
            new KeyFrame(Duration.millis(600), e -> { d1.setOpacity(0.3); d2.setOpacity(0.3); d3.setOpacity(1); }),
            new KeyFrame(Duration.millis(900))
        );
        typingDots.setCycleCount(Animation.INDEFINITE);
        return row;
    }

    private Label dot() {
        Label l = new Label("●");
        l.setStyle("-fx-font-size:10px;-fx-text-fill:#00d4ff;");
        return l;
    }

    private void showTyping() {
        typingBubble.setVisible(true); typingBubble.setManaged(true);
        if (!chatBox.getChildren().contains(typingBubble)) chatBox.getChildren().add(typingBubble);
        typingDots.play(); scrollToBottom();
    }

    private void hideTyping() {
        typingDots.stop(); typingBubble.setVisible(false); typingBubble.setManaged(false);
        chatBox.getChildren().remove(typingBubble);
    }

    // ── HEADER ────────────────────────────────────────────────────
    private HBox buildHeader(Stage stage) {
        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(10, 20, 10, 20));
        header.setStyle("-fx-background-color:linear-gradient(to right,#020b18,#041a2e);" +
                        "-fx-border-color:transparent transparent #00d4ff transparent;-fx-border-width:0 0 1 0;");

        ImageView iconView = new ImageView();
        try {
            File f = new File(ICON_PATH);
            if (f.exists()) iconView.setImage(new Image(new FileInputStream(f)));
        } catch (Exception ignored) {}
        iconView.setFitWidth(28); iconView.setFitHeight(28); iconView.setPreserveRatio(true);
        ScaleTransition pa = new ScaleTransition(Duration.millis(1500), iconView);
        pa.setFromX(1.0); pa.setToX(1.06); pa.setFromY(1.0); pa.setToY(1.06);
        pa.setAutoReverse(true); pa.setCycleCount(Animation.INDEFINITE); pa.play();

        Label title = new Label("  J.A.R.V.I.S");
        title.setStyle("-fx-font-family:'Courier New';-fx-font-size:18px;-fx-font-weight:bold;" +
                       "-fx-text-fill:#00d4ff;-fx-effect:dropshadow(gaussian,#00d4ff,10,0.5,0,0);");
        Label subtitle = new Label("  ADVANCED AI SYSTEM  v3.0");
        subtitle.setStyle("-fx-font-family:'Courier New';-fx-font-size:9px;-fx-text-fill:#4a9eba;");
        VBox titleBox = new VBox(1, title, subtitle);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        clockLabel = new Label("00:00:00");
        clockLabel.setStyle("-fx-font-family:'Courier New';-fx-font-size:20px;-fx-font-weight:bold;" +
                            "-fx-text-fill:#00d4ff;-fx-effect:dropshadow(gaussian,#00d4ff,8,0.4,0,0);");
        dateLabel = new Label("");
        dateLabel.setStyle("-fx-font-family:'Courier New';-fx-font-size:9px;-fx-text-fill:#4a9eba;");
        VBox clockBox = new VBox(1, clockLabel, dateLabel);
        clockBox.setAlignment(Pos.CENTER_RIGHT);

        Region sp2 = new Region(); sp2.setMinWidth(16);

        Button minBtn = new Button("─");
        minBtn.setStyle(btnStyle("#4a9eba", "transparent"));
        minBtn.setOnAction(e -> stage.setIconified(true));

        fullscreenBtn = new Button("⛶");
        fullscreenBtn.setStyle(btnStyle("#4a9eba", "transparent"));
        fullscreenBtn.setOnMouseEntered(e -> fullscreenBtn.setStyle(btnStyle("#00d4ff", "transparent")));
        fullscreenBtn.setOnMouseExited(e -> fullscreenBtn.setStyle(btnStyle("#4a9eba", "transparent")));
        fullscreenBtn.setOnAction(e -> toggleFullscreen());

        Button closeBtn = new Button("✕");
        closeBtn.setStyle(btnStyle("#4a9eba", "transparent"));
        closeBtn.setOnMouseEntered(e -> closeBtn.setStyle(btnStyle("white", "#ff3b5c")));
        closeBtn.setOnMouseExited(e -> closeBtn.setStyle(btnStyle("#4a9eba", "transparent")));
        closeBtn.setOnAction(e -> { wakeWordActive = false; if (focusTracker != null) focusTracker.stop(); Platform.exit(); });

        header.getChildren().addAll(iconView, titleBox, spacer, clockBox, sp2, minBtn, fullscreenBtn, closeBtn);
        header.setOnMousePressed(e -> { xOffset = e.getSceneX(); yOffset = e.getSceneY(); });
        header.setOnMouseDragged(e -> {
            if (!isFullscreen) { stage.setX(e.getScreenX() - xOffset); stage.setY(e.getScreenY() - yOffset); }
        });
        return header;
    }

    // ── CHAT AREA ─────────────────────────────────────────────────
    private ScrollPane buildChatArea() {
        chatBox = new VBox(12);
        chatBox.setPadding(new Insets(16));
        chatBox.setStyle("-fx-background-color:transparent;");

        scrollPane = new ScrollPane(chatBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background:transparent;-fx-background-color:transparent;-fx-border-color:transparent;");
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        addJarvisMessage("SYSTEM ONLINE. All neural networks operational. v3.0 ready.");
        addJarvisMessage("NEW: Multi-task commands! Try: 'open Chrome and then open YouTube in it'");
        addJarvisMessage("NEW: Fullscreen mode (⛶), gesture detection, object recognition active.");
        addJarvisMessage("Say 'Hey Jarvis' to activate voice — or use the 🎤 button.");
        return scrollPane;
    }

    // ── RIGHT PANEL ───────────────────────────────────────────────
    private VBox buildRightPanel() {
        Label focusTitle = new Label("[ FOCUS TRACKER ]");
        focusTitle.setStyle(panelTitleStyle());

        webcamView = new ImageView();
        webcamView.setFitWidth(220); webcamView.setFitHeight(165);
        webcamView.setPreserveRatio(true);
        Label camPlaceholder = new Label("[ NO SIGNAL ]");
        camPlaceholder.setStyle("-fx-font-family:'Courier New';-fx-font-size:11px;-fx-text-fill:#2a5f7a;");
        StackPane camBox = new StackPane(webcamView, camPlaceholder);
        camBox.setStyle("-fx-background-color:#010d18;-fx-border-color:#00d4ff;-fx-border-width:1;" +
                        "-fx-min-width:220;-fx-min-height:165;");

        focusScoreLabel = new Label("FOCUS: ---%");
        focusScoreLabel.setStyle("-fx-font-family:'Courier New';-fx-font-size:15px;-fx-font-weight:bold;-fx-text-fill:#00ff88;");
        focusBarLabel = new Label("▓▓▓▓▓▓▓▓▓▓ 100%");
        focusBarLabel.setStyle("-fx-font-family:'Courier New';-fx-font-size:9px;-fx-text-fill:#00ff88;");

        gestureLabel = new Label("GESTURE: ---");
        gestureLabel.setStyle("-fx-font-family:'Courier New';-fx-font-size:9px;-fx-text-fill:#ff44ff;");

        objectLabel = new Label("OBJECTS: ---");
        objectLabel.setStyle("-fx-font-family:'Courier New';-fx-font-size:9px;-fx-text-fill:#ffaa00;");

        Label statsTitle = new Label("[ SYSTEM STATUS ]");
        statsTitle.setStyle(panelTitleStyle());
        statsTitle.setPadding(new Insets(10, 0, 2, 0));

        cpuLabel = new Label("CPU CORES: --");
        cpuLabel.setStyle(statStyle());
        ramLabel = new Label("JVM RAM: -- MB");
        ramLabel.setStyle(statStyle());

        Label statusIndicators = new Label(
            "◉ FACE DETECTION: ACTIVE\n◉ EYE TRACKING: ACTIVE\n◉ GESTURE DETECT: ACTIVE\n◉ OBJECT RECOG: ACTIVE\n◉ WAKE WORD: ACTIVE"
        );
        statusIndicators.setStyle("-fx-font-family:'Courier New';-fx-font-size:8px;-fx-text-fill:#4a9eba;");
        statusIndicators.setPadding(new Insets(4, 0, 0, 0));

        Label tipsTitle = new Label("[ QUICK COMMANDS ]");
        tipsTitle.setStyle(panelTitleStyle());
        tipsTitle.setPadding(new Insets(10, 0, 2, 0));

        String[] tips = {
            "\"open chrome + youtube\"",
            "\"set timer 25 min\"",
            "\"set volume to 60\"",
            "\"search for...\"",
            "\"take screenshot\"",
            "\"remember that...\"",
            "\"system stats\"",
            "\"what's in clipboard\""
        };
        VBox tipsBox = new VBox(2);
        for (String tip : tips) {
            Label t = new Label("▸ " + tip);
            t.setStyle("-fx-font-family:'Courier New';-fx-font-size:7px;-fx-text-fill:#3a7a9a;");
            tipsBox.getChildren().add(t);
        }

        VBox panel = new VBox(5,
            focusTitle, camBox, focusScoreLabel, focusBarLabel, gestureLabel, objectLabel,
            statsTitle, cpuLabel, ramLabel, statusIndicators,
            tipsTitle, tipsBox
        );
        panel.setPadding(new Insets(14));
        panel.setAlignment(Pos.TOP_LEFT);
        panel.setStyle("-fx-background-color:#010d18;" +
                       "-fx-border-color:transparent transparent transparent #00d4ff;" +
                       "-fx-border-width:0 0 0 1;-fx-min-width:255;-fx-max-width:255;");
        return panel;
    }

    // ── BOTTOM ────────────────────────────────────────────────────
    private VBox buildBottom() {
        statusLabel = new Label("● READY  |  Say 'Hey Jarvis' to activate");
        statusLabel.setStyle("-fx-font-family:'Courier New';-fx-font-size:10px;-fx-text-fill:#00ff88;");
        HBox statusBar = new HBox(statusLabel);
        statusBar.setPadding(new Insets(4, 16, 4, 16));
        statusBar.setStyle("-fx-background-color:#020b18;");

        inputField = new TextField();
        inputField.setPromptText("Type a command, ask anything, or say 'Hey Jarvis'...");
        inputField.setStyle(
            "-fx-background-color:#041a2e;-fx-text-fill:#00d4ff;-fx-prompt-text-fill:#2a5f7a;" +
            "-fx-font-family:'Courier New';-fx-font-size:13px;" +
            "-fx-border-color:#00d4ff;-fx-border-width:1;-fx-border-radius:3;" +
            "-fx-background-radius:3;-fx-padding:10 14 10 14;"
        );
        inputField.setOnAction(e -> send());
        HBox.setHgrow(inputField, Priority.ALWAYS);

        micBtn = new Button("🎤");
        micBtn.setStyle(micIdleStyle());
        micBtn.setOnMouseEntered(e -> { if (!isListening) micBtn.setStyle(micHoverStyle()); });
        micBtn.setOnMouseExited(e -> { if (!isListening) micBtn.setStyle(micIdleStyle()); });
        micBtn.setOnAction(e -> activateVoiceInput());

        Button imgBtn = new Button("🖼");
        imgBtn.setStyle(micIdleStyle());
        imgBtn.setOnMouseEntered(e -> imgBtn.setStyle(micHoverStyle()));
        imgBtn.setOnMouseExited(e -> imgBtn.setStyle(micIdleStyle()));
        imgBtn.setOnAction(e -> openImagePicker());

        Button sendBtn = new Button("SEND ▶");
        sendBtn.setStyle(sendIdleStyle());
        sendBtn.setOnMouseEntered(e -> sendBtn.setStyle(sendHoverStyle()));
        sendBtn.setOnMouseExited(e -> sendBtn.setStyle(sendIdleStyle()));
        sendBtn.setOnAction(e -> send());

        HBox inputRow = new HBox(10, inputField, micBtn, imgBtn, sendBtn);
        inputRow.setPadding(new Insets(10, 16, 14, 16));
        inputRow.setAlignment(Pos.CENTER);
        inputRow.setStyle("-fx-background-color:#020b18;" +
                          "-fx-border-color:#00d4ff transparent transparent transparent;-fx-border-width:1 0 0 0;");
        return new VBox(0, statusBar, inputRow);
    }

    // ── IMAGE PICKER ──────────────────────────────────────────────
    private void openImagePicker() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Select Image");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.png","*.jpg","*.jpeg","*.bmp","*.webp"));
        File file = fc.showOpenDialog(primaryStage);
        if (file != null) {
            addUserMessage("📎 Analyzing: " + file.getName());
            showTyping();
            setStatus("● ANALYZING IMAGE...", "#ffaa00");
            new Thread(() -> {
                String response = JarvisCore.process("analyze image " + file.getAbsolutePath());
                Platform.runLater(() -> {
                    hideTyping();
                    addJarvisMessage(response);
                    TextToSpeech.speak("Image analysis complete.");
                    DatabaseManager.saveConversation("image: " + file.getName(), response);
                    setStatus("● READY  |  Say 'Hey Jarvis'", "#00ff88");
                });
            }).start();
        }
    }

    // ── FOCUS TRACKER ─────────────────────────────────────────────
    private void startFocusTracker() {
        focusTracker = new FocusTracker(
            score -> Platform.runLater(() -> updateFocusUI(score)),
            () -> Platform.runLater(() -> {
                String alert = "Hey! You've been away. Stay focused!";
                addJarvisMessage("⚠ " + alert);
                TextToSpeech.speak(alert);
            }),
            jpegBytes -> Platform.runLater(() -> {
                try { webcamView.setImage(new Image(new java.io.ByteArrayInputStream(jpegBytes))); }
                catch (Exception ignored) {}
            }),
            gesture -> Platform.runLater(() -> {
                gestureLabel.setText("GESTURE: " + gesture);
                // Gesture commands
                switch (gesture) {
                    case "OPEN HAND": addJarvisMessage("✋ Open hand detected — Pausing."); break;
                    case "FIST":      addJarvisMessage("✊ Fist detected — Resuming."); break;
                    case "PEACE":     addJarvisMessage("✌ Peace sign — Hello!"); break;
                    case "POINT":     addJarvisMessage("☝ Pointing detected."); break;
                }
            }),
            objects -> Platform.runLater(() -> {
                if (objects != null && !objects.isEmpty()) {
                    objectLabel.setText(objects.length() > 30 ? objects.substring(0, 30) + "..." : objects);
                }
            })
        );
        new Thread(() -> {
            boolean ok = focusTracker.initialize();
            Platform.runLater(() -> {
                if (ok) addJarvisMessage("Vision system online — focus tracking, gesture detection, and object recognition active.");
                else    addJarvisMessage("Webcam unavailable. Vision system disabled.");
            });
            if (ok) focusTracker.start();
        }).start();
    }

    private void updateFocusUI(int score) {
        focusScoreLabel.setText("FOCUS: " + score + "%");
        String color = score > 60 ? "#00ff88" : score > 30 ? "#ffaa00" : "#ff3b5c";
        focusScoreLabel.setStyle("-fx-font-family:'Courier New';-fx-font-size:15px;-fx-font-weight:bold;" +
                                 "-fx-text-fill:" + color + ";-fx-effect:dropshadow(gaussian," + color + ",8,0.4,0,0);");
        int filled = score / 10;
        focusBarLabel.setText("▓".repeat(filled) + "░".repeat(10 - filled) + " " + score + "%");
        focusBarLabel.setStyle("-fx-font-family:'Courier New';-fx-font-size:9px;-fx-text-fill:" + color + ";");
    }

    // ── SEND ──────────────────────────────────────────────────────
    private void send() {
        String user = inputField.getText().trim();
        if (user.isEmpty()) return;
        inputField.clear();
        addUserMessage(user);
        showTyping();
        setStatus("● PROCESSING...", "#ffaa00");
        new Thread(() -> {
            String response = JarvisCore.process(user);
            Platform.runLater(() -> {
                hideTyping();
                addJarvisMessage(response);
                TextToSpeech.speak(response);
                DatabaseManager.saveConversation(user, response);
                setStatus("● READY  |  Say 'Hey Jarvis'", "#00ff88");
            });
        }).start();
    }

    // ── CHAT BUBBLES ──────────────────────────────────────────────
    private void addUserMessage(String text) {
        Label msg = new Label("YOU  ›  " + text);
        msg.setWrapText(true); msg.setMaxWidth(480);
        msg.setStyle("-fx-background-color:#0a2a3a;-fx-text-fill:#a0e8ff;" +
                     "-fx-font-family:'Courier New';-fx-font-size:12px;-fx-padding:10 14 10 14;" +
                     "-fx-border-color:#00d4ff transparent transparent transparent;" +
                     "-fx-border-width:1 0 0 0;-fx-background-radius:3;");
        HBox row = new HBox(msg);
        row.setAlignment(Pos.CENTER_RIGHT);
        row.setPadding(new Insets(2, 0, 2, 40));
        animateIn(row);
        chatBox.getChildren().add(row);
        scrollToBottom();
    }

    public void addJarvisMessage(String text) {
        Label prefix = new Label("J.A.R.V.I.S");
        prefix.setStyle("-fx-font-family:'Courier New';-fx-font-size:9px;-fx-text-fill:#00d4ff;" +
                        "-fx-font-weight:bold;-fx-effect:dropshadow(gaussian,#00d4ff,6,0.5,0,0);");
        Label msg = new Label(text);
        msg.setWrapText(true); msg.setMaxWidth(480);
        msg.setStyle("-fx-text-fill:#e0f8ff;-fx-font-family:'Courier New';-fx-font-size:12px;-fx-padding:3 0 0 0;");
        VBox bubble = new VBox(3, prefix, msg);
        bubble.setStyle("-fx-background-color:#031525;-fx-padding:10 14 10 14;" +
                        "-fx-border-color:#00d4ff transparent transparent transparent;" +
                        "-fx-border-width:1 0 0 0;-fx-background-radius:3;");
        bubble.setMaxWidth(480);
        HBox row = new HBox(bubble);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(2, 40, 2, 0));
        animateIn(row);
        chatBox.getChildren().add(row);
        scrollToBottom();
    }

    private void animateIn(javafx.scene.Node node) {
        node.setOpacity(0); node.setTranslateY(10);
        FadeTransition f = new FadeTransition(Duration.millis(250), node);
        f.setFromValue(0); f.setToValue(1);
        TranslateTransition s = new TranslateTransition(Duration.millis(250), node);
        s.setFromY(10); s.setToY(0);
        new ParallelTransition(f, s).play();
    }

    private void setStatus(String text, String color) {
        statusLabel.setText(text);
        statusLabel.setStyle("-fx-font-family:'Courier New';-fx-font-size:10px;-fx-text-fill:" + color + ";");
    }

    private void scrollToBottom() { Platform.runLater(() -> scrollPane.setVvalue(1.0)); }

    // ── STYLES ────────────────────────────────────────────────────
    private String panelTitleStyle() {
        return "-fx-font-family:'Courier New';-fx-font-size:9px;-fx-font-weight:bold;" +
               "-fx-text-fill:#00d4ff;-fx-effect:dropshadow(gaussian,#00d4ff,6,0.4,0,0);";
    }
    private String statStyle() { return "-fx-font-family:'Courier New';-fx-font-size:9px;-fx-text-fill:#4a9eba;"; }
    private String btnStyle(String fg, String bg) {
        return "-fx-background-color:" + bg + ";-fx-text-fill:" + fg + ";-fx-font-size:13px;" +
               "-fx-cursor:hand;-fx-border-color:" + fg + ";-fx-border-width:1;-fx-border-radius:3;-fx-padding:2 7 2 7;";
    }
    private String micIdleStyle() {
        return "-fx-background-color:#041a2e;-fx-text-fill:#00d4ff;-fx-font-size:14px;" +
               "-fx-cursor:hand;-fx-border-color:#00d4ff;-fx-border-width:1;" +
               "-fx-border-radius:3;-fx-background-radius:3;-fx-padding:10 12 10 12;";
    }
    private String micHoverStyle() {
        return "-fx-background-color:#0a2a3a;-fx-text-fill:#00ffff;-fx-font-size:14px;" +
               "-fx-cursor:hand;-fx-border-color:#00ffff;-fx-border-width:1;" +
               "-fx-border-radius:3;-fx-background-radius:3;-fx-padding:10 12 10 12;" +
               "-fx-effect:dropshadow(gaussian,#00ffff,8,0.4,0,0);";
    }
    private String micActiveStyle() {
        return "-fx-background-color:#ff0055;-fx-text-fill:white;-fx-font-size:14px;" +
               "-fx-cursor:hand;-fx-border-color:#ff0055;-fx-border-width:1;" +
               "-fx-border-radius:3;-fx-background-radius:3;-fx-padding:10 12 10 12;" +
               "-fx-effect:dropshadow(gaussian,#ff0055,12,0.6,0,0);";
    }
    private String sendIdleStyle() {
        return "-fx-background-color:#00d4ff;-fx-text-fill:#020b18;" +
               "-fx-font-family:'Courier New';-fx-font-weight:bold;-fx-font-size:12px;" +
               "-fx-cursor:hand;-fx-border-radius:3;-fx-background-radius:3;" +
               "-fx-padding:10 18 10 18;-fx-effect:dropshadow(gaussian,#00d4ff,8,0.4,0,0);";
    }
    private String sendHoverStyle() {
        return "-fx-background-color:#00ffff;-fx-text-fill:#020b18;" +
               "-fx-font-family:'Courier New';-fx-font-weight:bold;-fx-font-size:12px;" +
               "-fx-cursor:hand;-fx-border-radius:3;-fx-background-radius:3;" +
               "-fx-padding:10 18 10 18;-fx-effect:dropshadow(gaussian,#00ffff,14,0.6,0,0);";
    }
}