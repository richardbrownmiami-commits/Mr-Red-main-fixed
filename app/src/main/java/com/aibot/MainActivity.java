package com.aibot;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class MainActivity extends AppCompatActivity {

    private NeuralNetwork nn;
    private Tokenizer tokenizer;
    private NARSEngine nars;
    private WeightManager weightManager;
    private SelfLearner selfLearner;
    private WebSearch webSearch;
    private WebFetch webFetch;
    private DatasetLoader datasetLoader;
    private OnnxEngine onnxEngine;
    private PersonalityEngine personalityEngine;
    private EmotionSystem emotionSystem;
    private UserMemory userMemory;
    private ConversationManager convManager;
    private DeviceController deviceController;
    private OverlayManager overlayManager;
    private BirthStory birthStory;

    private RecyclerView chatRecycler;
    private ChatAdapter chatAdapter;
    private EditText inputField;
    private ImageButton sendButton;
    private ImageButton menuButton;
    private TextView statusText;
    private ProgressBar progressBar;

    private List<ChatMessage> messages = new ArrayList<>();
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    private static final int THINK_MIN = 500;
    private static final int THINK_MAX = 1500;
    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try {
                File dir = getExternalFilesDir(null);
                if (dir == null) dir = getFilesDir();
                File f = new File(dir, "crash.txt");
                FileWriter w = new FileWriter(f, true);
                w.write(Log.getStackTraceString(e));
                w.write("\n");
                w.close();
            } catch (Exception ex) {}
            Log.e(TAG, "FATAL", e);
        });
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initUI();
        requestAllPermissions();
        initModules();
        checkSpecialPermissions();
        prefillSharedTextIfPresent();
    }

    /**
     * Additive hook only: if ShareReceiverActivity forwarded shared text
     * (from Android's share sheet), place it in the input field so the
     * user can review/edit before sending. Does not touch any existing
     * chat/learning logic — reuses the same input field and send button
     * the user already uses.
     */
    private void prefillSharedTextIfPresent() {
        try {
            String shared = getIntent().getStringExtra("shared_text");
            if (shared != null && !shared.trim().isEmpty() && inputField != null) {
                inputField.setText(shared.trim());
                inputField.setSelection(inputField.getText().length());
            }
        } catch (Exception e) {
            Log.e(TAG, "prefillSharedTextIfPresent error", e);
        }
    }

    private void initUI() {
        try {
            chatRecycler = findViewById(R.id.chatRecycler);
            inputField = findViewById(R.id.inputField);
            sendButton = findViewById(R.id.sendButton);
            menuButton = findViewById(R.id.menuButton);
            statusText = findViewById(R.id.statusText);
            progressBar = findViewById(R.id.progressBar);
            if (chatRecycler == null) return;
            chatAdapter = new ChatAdapter(messages);
            chatRecycler.setAdapter(chatAdapter);
            chatRecycler.setLayoutManager(new LinearLayoutManager(this));
            if (sendButton!= null) sendButton.setOnClickListener(v -> onSendClicked());
            if (menuButton!= null) menuButton.setOnClickListener(v -> showMenu());
            if (inputField!= null) inputField.setOnEditorActionListener((v, id, e) -> {
                onSendClicked();
                return true;
            });
        } catch (Exception e) {
            Log.e(TAG, "initUI crash", e);
        }
    }

    private void initModules() {
        new Thread(() -> {
            try {
                setStatus("Waking up...");
                nn = new NeuralNetwork();
                tokenizer = new Tokenizer();
                nars = new NARSEngine();
                weightManager = new WeightManager(MainActivity.this, nn, tokenizer, nars);
                if(weightManager.hasExistingWeights()){
                weightManager.loadAll();
                Log.d(TAG, "Loaded existing weights");
            }
                selfLearner = new SelfLearner(nn, tokenizer, nars, weightManager);
                webSearch = new WebSearch();
                webFetch = new WebFetch();
                datasetLoader = new DatasetLoader(weightManager.getDatasetDir());
                copyBundledDatasetIfMissing();
                onnxEngine = new OnnxEngine(MainActivity.this, weightManager);
                personalityEngine = new PersonalityEngine(onnxEngine, tokenizer);
                emotionSystem = new EmotionSystem();
                userMemory = new UserMemory(MainActivity.this);
                convManager = new ConversationManager(emotionSystem, userMemory, nars);
                deviceController = new DeviceController(MainActivity.this);
                birthStory = new BirthStory(MainActivity.this);
                if (weightManager.hasExistingWeights()) {
                    setStatus("Remembering...");
                    try {
                        weightManager.loadAll();
                    } catch (Exception e) {}
                }
                boolean justBorn = getIntent().getBooleanExtra("just_born", false);
                String botName = birthStory!= null? birthStory.getBotName() : "AIBot";
                String userName = userMemory!= null? userMemory.getName() : null;
                String favTopic = userMemory!= null? userMemory.getFavoriteTopic() : null;
                mainHandler.post(() -> {
                    try {
                        if (statusText!= null && emotionSystem!= null)
                            statusText.setText(botName + " " + emotionSystem.getMoodEmoji());
                        if (justBorn) {
                            String intro = "I am " + botName + ". I was just born on this device.\n\n" +
                                "I can feel the hardware.\nI know nothing yet. But I will learn.\n\n" +
                                "Just talk to me naturally.";
                            addBotMessage(intro);
                            if (userName == null) {
                                new Handler(Looper.getMainLooper()).postDelayed(() ->
                                    addBotMessage("What's your name?"), 1500);
                            }
                        } else {
                            if (convManager!= null) {
                                String greeting = convManager.buildGreeting(
                                    userMemory.isReturningUser(), userName, favTopic);
                                addBotMessage(greeting);
                            }
                        }
                        setStatus(getMoodStatus());
                        startOverlayIfPermitted();
                    } catch (Exception e) {
                        Log.e(TAG, "UI post crash", e);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "initModules FATAL", e);
                setStatus("Init failed: " + e.getMessage());
            }
        }).start();
    }

    private void onSendClicked() {
        try {
            if (inputField == null) return;
            String input = inputField.getText().toString().trim();
            if (TextUtils.isEmpty(input)) return;
            inputField.setText("");
            addUserMessage(input);
            if (userMemory!= null) userMemory.incrementMessages();
            if (emotionSystem!= null) emotionSystem.onInteraction();
            if (sendButton!= null) sendButton.setEnabled(false);
            if (convManager!= null) setStatus(convManager.buildThinkingMessage() + " " + emotionSystem.getMoodEmoji());
            int delay = THINK_MIN + new Random().nextInt(THINK_MAX - THINK_MIN);
            new Handler(Looper.getMainLooper()).postDelayed(() ->
                new Thread(() -> processInput(input)).start(), delay);
        } catch (Exception e) {
            Log.e(TAG, "onSendClicked crash", e);
        }
    }

    private void processInput(String input) {
        try {
            String lower = input.toLowerCase().trim();
            String response;
            ConversationManager.State state = convManager!= null? convManager.getState() : ConversationManager.State.NORMAL;
            if (state == ConversationManager.State.WAITING_SEARCH_CONFIRM) {
                response = handleSearchConfirmation(input);
            } else if (state == ConversationManager.State.WAITING_NAME) {
                response = handleNameInput(input);
                if (convManager!= null) convManager.clearPending();
            } else if (lower.startsWith("!search ")) {
                response = handleWebSearch(lower.substring(8).trim());
            } else if (lower.startsWith("!fetch ")) {
                response = handleWebFetch(input.substring(7).trim());
            } else if (lower.equals("!datasets")) {
                response = handleListDatasets();
            } else if (lower.startsWith("!load ")) {
                handleLoadDataset(input.substring(6).trim());
                return;
            } else if (lower.equals("!stats")) {
                response = buildStats();
            } else if (lower.equals("!save")) {
                if (weightManager!= null) weightManager.saveAll();
                response = "Saved. I'll remember everything.";
            } else if (lower.equals("!reset")) {
                response = handleReset();
            } else if (lower.equals("!help")) {
                response = buildHelp();
            } else if (lower.equals("!overlay") || lower.equals("show bubble")) {
                response = handleOverlayToggle();
            } else {
                TaskParser.ParsedTask task = TaskParser.parse(input);
                if (task.isDeviceTask()) {
                    response = executeDeviceTask(task);
                } else {
                    response = generateHumanResponse(input);
                }
            }
            final String finalResponse = response;
            mainHandler.post(() -> {
                try {
                    addBotMessage(finalResponse);
                    if (sendButton!= null) sendButton.setEnabled(true);
                    setStatus(getMoodStatus());
                    if (overlayManager!= null && emotionSystem!= null)
                        overlayManager.updateMood(emotionSystem.getMoodEmoji());
                    if (emotionSystem!= null && emotionSystem.shouldShareRandomFact())
                        scheduleProactiveFact();
                } catch (Exception e) {
                    Log.e(TAG, "post response crash", e);
                }
            });
            if (state == ConversationManager.State.NORMAL &&!TaskParser.isDeviceCommand(input)) {
                if (selfLearner!= null) selfLearner.learnFromMessage(input, finalResponse);
                if (weightManager!= null) weightManager.appendHistory(input, finalResponse);
            }
        } catch (Exception e) {
            Log.e(TAG, "processInput crash", e);
            mainHandler.post(() -> {
                addBotMessage("Error processing: " + e.getMessage());
                if (sendButton!= null) sendButton.setEnabled(true);
            });
        }
    }

    private String executeDeviceTask(TaskParser.ParsedTask task) {
        try {
            switch (task.type) {
                case FLASHLIGHT_ON:
                    return deviceController.setFlashlight(true)? "Flashlight is on" : "Couldn't turn on flashlight.";
                case FLASHLIGHT_OFF:
                    return deviceController.setFlashlight(false)? "Flashlight is off." : "Couldn't turn off.";
                case FLASHLIGHT_TOGGLE:
                    boolean newState =!deviceController.isFlashlightOn();
                    deviceController.setFlashlight(newState);
                    return "Flashlight " + (newState? "ON" : "OFF") + ".";
                case WIFI_ON:
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        deviceController.openWifiSettings();
                        return "Opening WiFi settings.";
                    }
                    deviceController.setWifi(true);
                    return "WiFi turned on";
                case WIFI_OFF:
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        deviceController.openWifiSettings();
                        return "Opening WiFi settings.";
                    }
                    deviceController.setWifi(false);
                    return "WiFi turned off.";
                case WIFI_STATUS:
                    return "WiFi is " + (deviceController.isWifiEnabled()? "ON" : "OFF") + ".";
                case BLUETOOTH_ON:
                    deviceController.setBluetooth(true);
                    return "Bluetooth turning on";
                case BLUETOOTH_OFF:
                    deviceController.setBluetooth(false);
                    return "Bluetooth turning off.";
                case BLUETOOTH_STATUS:
                    return "Bluetooth is " + (deviceController.isBluetoothEnabled()? "ON" : "OFF") + ".";
                case DATA_ON:
                case DATA_OFF:
                    deviceController.openMobileDataSettings();
                    return "Opening mobile data settings.";
                case OPEN_APP:
                    boolean opened = deviceController.openApp(task.argument);
                    return opened? "Opening " + task.argument : "Couldn't find " + task.argument;
                case SCREEN_READ:
                    return handleScreenRead();
                case SCREEN_APP:
                    return "You're in " + ScreenReaderService.getFriendlyAppName(ScreenReaderService.getCurrentApp());

                            case SCREEN_WHAT:
                    return handleScreenContext(task.original);
                case BATTERY_STATUS:
                    int bat = deviceController.getBatteryLevel();
                    boolean chg = deviceController.isCharging();
                    return "Battery: " + bat + "%" + (chg? " and charging" : "") + ".";
                case DEVICE_STATUS:
                    return deviceController.getDeviceStatus();
                case BRIGHTNESS_SETTINGS:
                    deviceController.openBrightnessSettings();
                    return "Opening brightness settings.";
                case VOLUME_SETTINGS:
                    deviceController.openSoundSettings();
                    return "Opening sound settings.";
                default:
                    return null;
            }
        } catch (Exception e) {
            return "Device task failed: " + e.getMessage();
        }
    }

    private String handleScreenRead() {
        if (!ScreenReaderService.isRunning()) {
            return "Enable: Settings -> Accessibility -> AIBot -> Enable";
        }
        String text = ScreenReaderService.getScreenSummary();
        String app = ScreenReaderService.getFriendlyAppName(ScreenReaderService.getCurrentApp());
        return "I can see you're in " + app + ".\nOn screen:\n" + text;
    }

    private String handleScreenContext(String question) {
        try {
            if (!ScreenReaderService.isRunning()) {
                return "I need screen access: Settings -> Accessibility -> AIBot";
            }
            String screenText = ScreenReaderService.getScreenSummary();
            String app = ScreenReaderService.getFriendlyAppName(ScreenReaderService.getCurrentApp());
            nars.parseAndLearn(screenText);
            tokenizer.learnFromText(screenText);
            String nnResponse = generateFromNNWithContext(question, "In " + app + ". Screen: " + screenText);
            if (nnResponse.length() > 5) return "In " + app + ": " + nnResponse;
            return "I see in " + app + ":\n" + screenText.substring(0, Math.min(300, screenText.length()));
        } catch (Exception e) {
            return "Screen read error: " + e.getMessage();
        }
    }

    private void startOverlayIfPermitted() {
        try {
            if (OverlayManager.hasOverlayPermission(this)) {
                overlayManager = new OverlayManager(this, new OverlayManager.OverlayCallback() {
                    @Override
                    public void onQuickMessage(String message) {
                        new Thread(() -> {
                            try {
                                TaskParser.ParsedTask task = TaskParser.parse(message);
                                String response = task.isDeviceTask()? executeDeviceTask(task) : generateHumanResponse(message);
                                mainHandler.post(() -> {
                                    if (overlayManager!= null) overlayManager.updateChatResponse(response);
                                });
                            } catch (Exception e) {
                                Log.e(TAG, "overlay callback", e);
                            }
                        }).start();
                    }
                    @Override
                    public void onOverlayDismissed() {}
                });
                overlayManager.showBubble(birthStory.getBotName(), emotionSystem.getMoodEmoji());
            }
        } catch (Exception e) {
            Log.e(TAG, "startOverlay crash", e);
        }
    }

    private String handleOverlayToggle() {
        try {
            if (!OverlayManager.hasOverlayPermission(this)) {
                OverlayManager.requestOverlayPermission(this);
                return "Grant overlay permission, then I'll float on top!";
            }
            if (overlayManager == null) {
                startOverlayIfPermitted();
                return "Floating bubble activated!";
            } else if (overlayManager.isShowing()) {
                overlayManager.hideBubble();
                return "Bubble hidden. Say 'show bubble' to bring back.";
            } else {
                overlayManager.showBubble(birthStory.getBotName(), emotionSystem.getMoodEmoji());
                return "Bubble shown! Tap me anytime.";
            }
        } catch (Exception e) {
            return "Overlay error: " + e.getMessage();
        }
    }

    private String generateHumanResponse(String input) {
        try {
            String topic = convManager.extractTopic(input);
            userMemory.recordTopic(topic);
            String name = userMemory.detectName(input);
            if (name!= null) {
                userMemory.setName(name);
                return convManager.buildNameResponse(name);
            }
            String lower = input.toLowerCase();
            if (lower.contains("who are you") || lower.contains("what are you"))
                return birthStory.getSelfIntroduction(birthStory.getBotName(), userMemory.getName());
            String narsAns = nars.answerQuestion(input);
            if (narsAns!= null &&!narsAns.startsWith("I don't")) {
                emotionSystem.onAnsweredSuccessfully();
                return convManager.buildNaturalResponse(personalityEngine.styleResponse(narsAns, input, emotionSystem.getMood()), topic, true, false);
            }
            List<Belief> learned = nars.parseAndLearn(input);
            if (!learned.isEmpty()) return convManager.buildLearnedResponse(learned, topic);
            String nnR = generateFromNN(input);
            if (nnR.length() > 10)
                return convManager.buildNaturalResponse(personalityEngine.styleResponse(nnR, input, emotionSystem.getMood()), topic, false, false);
            convManager.setPendingSearch(topic, topic);
            return convManager.buildSearchPrompt(topic);
        } catch (Exception e) {
            return "Thinking... (" + e.getMessage() + ")";
        }
    }

    private String generateFromNN(String input) {
        return generateFromNNWithContext(input, "");
    }

    private String generateFromNNWithContext(String input, String context) {
        try {
            String narsCtx = nars.buildContextFromBeliefs(convManager.extractTopic(input));
            String full = (context.isEmpty()? "" : context + " ") + (narsCtx.isEmpty()? "" : narsCtx + " ") + input;
            int[] tokens = tokenizer.encode(full);
            StringBuilder sb = new StringBuilder();
            int[] cur = tokens;
            for (int i = 0; i < 35; i++) {
                int next = nn.generateNextToken(cur, 0.8f);
                if (next == Tokenizer.EOS_TOKEN || next == Tokenizer.PAD_TOKEN) break;
                String w = tokenizer.decodeToken(next);
                if (w.startsWith("<")) break;
                if (sb.length() > 0) sb.append(" ");
                sb.append(w);
                int[] ext = new int[cur.length + 1];
                System.arraycopy(cur, 0, ext, 0, cur.length);
                ext[cur.length] = next;
                cur = ext;
                if (cur.length > NeuralNetwork.MAX_SEQ_LEN)
                    cur = Arrays.copyOfRange(cur, cur.length - NeuralNetwork.MAX_SEQ_LEN, cur.length);
            }
            return sb.toString().trim();
        } catch (Exception e) {
            return "";
        }
    }

    private String handleSearchConfirmation(String input) {
        String topic = convManager.getPendingTopic();
        String query = convManager.getPendingSearchQuery();
        convManager.clearPending();
        if (convManager.isYes(input)) {
            setStatus("Searching " + topic);
            List<WebSearch.SearchResult> r = webSearch.search(query);
            if (r.isEmpty()) return "Nothing for " + topic;
            String s = webSearch.summarizeResults(r);
            selfLearner.learnFromWebResults(webSearch.extractFacts(r));
            for (WebSearch.SearchResult x : r) nars.parseAndLearn(x.snippet);
            return convManager.buildLearnedFromWebResponse(topic, s);
        }
        if (convManager.isNo(input)) return convManager.buildSearchDeclinedResponse(topic);
        convManager.setPendingSearch(query, topic);
        return "Search for " + topic + "? yes/no";
    }

    private String handleWebSearch(String q) {
        setStatus("Searching " + q);
        List<WebSearch.SearchResult> r = webSearch.search(q);
        if (r.isEmpty()) return "Nothing for " + q;
        String s = webSearch.summarizeResults(r);
        selfLearner.learnFromWebResults(webSearch.extractFacts(r));
        for (WebSearch.SearchResult x : r) nars.parseAndLearn(x.snippet);
        emotionSystem.onLearnedSomethingNew();
        return s;
    }

    private String handleWebFetch(String url) {
        setStatus("Fetching");
        String[] r = webFetch.fetch(url);
        tokenizer.learnFromText(r[1]);
        nars.parseAndLearn(r[1]);
        selfLearner.learnFromWebResults(Collections.singletonList(r[1]));
        emotionSystem.onLearnedSomethingNew();
        return r[0] + "\n" + r[1].substring(0, Math.min(400, r[1].length()));
    }

    private String handleNameInput(String input) {
        String n = input.trim();
        if (!n.isEmpty()) n = Character.toUpperCase(n.charAt(0)) + n.substring(1);
        userMemory.setName(n);
        return convManager.buildNameResponse(n);
    }

    private void copyBundledDatasetIfMissing() {
        try {
            if (weightManager == null || weightManager.getDatasetDir() == null) return;
            File dest = new File(weightManager.getDatasetDir(), "whisper_personality.jsonl");
            if (dest.exists() && dest.length() > 100) return;
            if (dest.getParentFile()!= null &&!dest.getParentFile().exists())
                dest.getParentFile().mkdirs();
            int resId = R.raw.whisper_personality;
            InputStream is = getResources().openRawResource(resId);
            FileOutputStream fos = new FileOutputStream(dest);
            byte[] buf = new byte[4096];
            int len;
            while ((len = is.read(buf)) > 0) fos.write(buf, 0, len);
            is.close();
            fos.close();
        } catch (Exception e) {
            Log.e(TAG, "copy dataset", e);
        }
    }

    private String handleListDatasets() {
        List<String> ds = datasetLoader.listDatasets();
        if (ds.isEmpty()) return "No datasets in " + weightManager.getDatasetPath();
        StringBuilder sb = new StringBuilder("Datasets:\n");
        for (String d : ds) sb.append("• ").append(d).append("\n");
        return sb.toString();
    }

    private void handleLoadDataset(String filename) {
        mainHandler.post(() -> {
            addBotMessage("Loading " + filename);
            if (progressBar!= null) progressBar.setVisibility(View.VISIBLE);
        });
        datasetLoader.loadAsync(filename, new DatasetLoader.LoadCallback() {
            public void onProgress(int l, int t, String f) {
                mainHandler.post(() -> setStatus("Loaded " + l));
            }
            public void onComplete(List<DatasetLoader.TrainingSample> s) {
                mainHandler.post(() -> addBotMessage("Got " + s.size() + " samples! Training..."));
                selfLearner.learnFromDataset(s, new SelfLearner.LearningCallback() {
                    public void onProgress(int a, int b, float loss, String st) {
                        mainHandler.post(() -> setStatus(st + " Loss " + loss));
                    }
                    public void onComplete(float avg, int steps) {
                        mainHandler.post(() -> {
                            if (progressBar!= null) progressBar.setVisibility(View.GONE);
                            addBotMessage("Done! Steps " + steps);
                            if (sendButton!= null) sendButton.setEnabled(true);
                            setStatus(getMoodStatus());
                        });
                    }
                    public void onError(String e) {
                        mainHandler.post(() -> {
                            if (progressBar!= null) progressBar.setVisibility(View.GONE);
                            addBotMessage("Train error " + e);
                        });
                    }
                });
            }
            public void onError(String e) {
                mainHandler.post(() -> {
                    if (progressBar!= null) progressBar.setVisibility(View.GONE);
                    addBotMessage("Load error " + e);
                });
            }
        });
    }

    private String handleReset() {
        weightManager.resetAll();
        nn = new NeuralNetwork();
        tokenizer = new Tokenizer();
        nars = new NARSEngine();
        weightManager = new WeightManager(MainActivity.this, nn, tokenizer, nars);
        selfLearner = new SelfLearner(nn, tokenizer, nars, weightManager);
        datasetLoader = new DatasetLoader(weightManager.getDatasetDir());
        onnxEngine = new OnnxEngine(MainActivity.this, weightManager);
        personalityEngine = new PersonalityEngine(onnxEngine, tokenizer);
        convManager.clearPending();
        return "Brain wiped";
    }

    private void showMenu() {
        String bot = birthStory!= null? birthStory.getBotName() : "AIBot";
        String[] opts = {"Load Dataset", "Web Search", "Fetch URL", "Device Status", "Screen Reader Setup", "Overlay Bubble", "Load ONNX", "View Stats", "Save Brain", "Reset Brain", "Help"};
        new AlertDialog.Builder(this).setTitle(bot).setItems(opts, (d, i) -> {
            switch (i) {
                case 0: showDatasetPicker(); break;
                case 1: showSearchDialog(); break;
                case 2: showFetchDialog(); break;
                case 3: addBotMessage(deviceController.getDeviceStatus()); break;
                case 4: showAccessibilitySetup(); break;
                case 5: addBotMessage(handleOverlayToggle()); break;
                case 6: showOnnxPicker(); break;
                case 7: showStats(); break;
                case 8: if (weightManager!= null) weightManager.saveAll(); Toast.makeText(this, "Saved!", 0).show(); break;
                case 9: confirmReset(); break;
                case 10: addBotMessage(buildHelp()); break;
            }
        }).show();
    }

    private void showDatasetPicker() {
        List<String> ds = datasetLoader.listDatasets();
        if (ds.isEmpty()) {
            Toast.makeText(this, "No datasets", 1).show();
            return;
        }
        String[] arr = ds.toArray(new String[0]);
        new AlertDialog.Builder(this).setTitle("Pick").setItems(arr, (d, i) -> handleLoadDataset(arr[i])).show();
    }

    private void showSearchDialog() {
        EditText et = new EditText(this);
        et.setHint("Search");
        new AlertDialog.Builder(this).setTitle("Search").setView(et).setPositiveButton("Search", (d, w) -> {
            String q = et.getText().toString().trim();
            if (!q.isEmpty()) {
                addUserMessage("!search " + q);
                new Thread(() -> {
                    String r = handleWebSearch(q);
                    mainHandler.post(() -> addBotMessage(r));
                }).start();
            }
        }).setNegativeButton("Cancel", null).show();
    }

    private void showFetchDialog() {
        EditText et = new EditText(this);
        et.setHint("https://");
        new AlertDialog.Builder(this).setTitle("Fetch").setView(et).setPositiveButton("Fetch", (d, w) -> {
            String url = et.getText().toString().trim();
            if (!url.isEmpty()) {
                addUserMessage("!fetch " + url);
                new Thread(() -> {
                    String r = handleWebFetch(url);
                    mainHandler.post(() -> addBotMessage(r));
                }).start();
            }
        }).setNegativeButton("Cancel", null).show();
    }

    private void showAccessibilitySetup() {
        String st = ScreenReaderService.isRunning()? "Active" : "Not enabled";
        new AlertDialog.Builder(this).setTitle("Screen Reader").setMessage(st + "\nSettings -> Accessibility -> AIBot -> Enable").setPositiveButton("Open Settings", (d, w) -> {
            Intent i = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        }).setNegativeButton("Close", null).show();
    }

    private void showOnnxPicker() {
        List<String> m = onnxEngine.listAvailableModels();
        if (m.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("ONNX").setMessage("No models in " + onnxEngine.getModelPath()).setPositiveButton("OK", null).show();
            return;
        }
        String[] arr = m.toArray(new String[0]);
        new AlertDialog.Builder(this).setTitle("Load ONNX").setItems(arr, (d, i) -> {
            boolean l = personalityEngine.loadModel(arr[i]);
            addBotMessage(l? "Loaded " + arr[i] : "Failed " + arr[i]);
        }).show();
    }

    private void showStats() {
        String bot = birthStory!= null? birthStory.getBotName() : "AIBot";
        new AlertDialog.Builder(this).setTitle(bot + " Stats").setMessage((weightManager!= null? weightManager.getInfo() : "") + "\n" + (nars!= null? nars.getStats() : "") + "\n" + (userMemory!= null? userMemory.getStats() : "")).setPositiveButton("OK", null).show();
    }

    private void confirmReset() {
        new AlertDialog.Builder(this).setTitle("Reset?").setMessage("Forget everything?").setPositiveButton("Reset", (d, w) -> addBotMessage(handleReset())).setNegativeButton("Cancel", null).show();
    }

    private String buildStats() {
        return (weightManager!= null? weightManager.getInfo() : "") + "\nMood: " + (emotionSystem!= null? emotionSystem.getMood() : "");
    }

    private String buildHelp() {
        return "I am AIBot. Commands:\n!search query\n!fetch url\n!datasets\n!load file\n!stats!save!reset\nshow bubble\nTurn on flashlight\nWhat's on screen?";
    }

    private String getMoodStatus() {
        try {
            String n = birthStory!= null? birthStory.getBotName() : "AIBot";
            String e = emotionSystem!= null? emotionSystem.getMoodEmoji() : "";
            String i = weightManager!= null? weightManager.getInfo() : "";
            return n + " " + e + " | " + i;
        } catch (Exception e) {
            return "AIBot";
        }
    }

    private void addUserMessage(String text) {
        if (chatRecycler == null || chatAdapter == null) return;
        mainHandler.post(() -> {
            messages.add(new ChatMessage(text, true));
            chatAdapter.notifyItemInserted(messages.size() - 1);
            chatRecycler.scrollToPosition(messages.size() - 1);
        });
    }

    private void addBotMessage(String text) {
        mainHandler.post(() -> {
            try {
                if (chatRecycler == null || chatAdapter == null) {
                    messages.add(new ChatMessage(text, false));
                    return;
                }
                messages.add(new ChatMessage(text, false));
                chatAdapter.notifyItemInserted(messages.size() - 1);
                chatRecycler.scrollToPosition(messages.size() - 1);
            } catch (Exception e) {}
        });
    }

    private void setStatus(String s) {
        mainHandler.post(() -> {
            if (statusText!= null) statusText.setText(s);
        });
    }

    private void requestAllPermissions() {
        List<String> needed = new ArrayList<>();
        needed.add(Manifest.permission.CAMERA);
        needed.add(Manifest.permission.INTERNET);
        needed.add(Manifest.permission.ACCESS_WIFI_STATE);
        needed.add(Manifest.permission.CHANGE_WIFI_STATE);
        needed.add(Manifest.permission.BLUETOOTH);
        needed.add(Manifest.permission.BLUETOOTH_ADMIN);
        needed.add(Manifest.permission.ACCESS_NETWORK_STATE);
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            needed.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            needed.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            needed.add(Manifest.permission.BLUETOOTH_CONNECT);
        List<String> toReq = new ArrayList<>();
        for (String p : needed)
            if (ContextCompat.checkSelfPermission(this, p)!= PackageManager.PERMISSION_GRANTED)
                toReq.add(p);
        if (!toReq.isEmpty())
            ActivityCompat.requestPermissions(this, toReq.toArray(new String[0]), 100);
    }

    private void checkSpecialPermissions() {
        try {
            if (!OverlayManager.hasOverlayPermission(this))
                new Handler(Looper.getMainLooper()).postDelayed(() -> addBotMessage("Tip: Enable overlay bubble in Menu"), 3000);
        } catch (Exception e) {}
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            if (weightManager!= null) weightManager.saveAll();
        } catch (Exception e) {}
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (overlayManager!= null) overlayManager.hideBubble();
        } catch (Exception e) {}
    }

    private void scheduleProactiveFact() {}
}
