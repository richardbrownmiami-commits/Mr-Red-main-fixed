package com.aibot;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Optional ONNX support.
 *
 * ONNX is deliberately disabled by default so the app can start on small
 * ARMv7 devices. It is enabled from the existing Menu -> Load ONNX Model
 * action. No ONNX native environment is created while disabled.
 */
public class OnnxEngine {
    private static final String TAG = "OnnxEngine";
    private static final String PREFS = "onnx_settings";
    private static final String ENABLED = "enabled";
    private static final String ENABLE_ACTION = "__enable_onnx__";

    private final Context ctx;
    private final WeightManager wm;
    private final File modelDir;
    private OrtEnvironment env;
    private OrtSession sessionMini;
    private OrtSession sessionYolo;
    private String loadedName = "none";

    public OnnxEngine(Context ctx, WeightManager wm) {
        this.ctx = ctx.getApplicationContext();
        this.wm = wm;
        File external = this.ctx.getExternalFilesDir(null);
        this.modelDir = new File(
            external != null ? external : this.ctx.getFilesDir(),
            "AIBot/models"
        );
        modelDir.mkdirs();
        // Important: do not call OrtEnvironment.getEnvironment() here.
    }

    public OnnxEngine(Context ctx) {
        this(ctx, null);
    }

    public boolean isEnabled() {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(ENABLED, false);
    }

    public void setEnabled(boolean enabled) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(ENABLED, enabled).apply();
        if (!enabled) close();
    }

    private boolean ensureEnvironment() {
        if (!isEnabled()) return false;
        if (env != null) return true;
        try {
            env = OrtEnvironment.getEnvironment();
            return true;
        } catch (Throwable e) {
            Log.e(TAG, "ONNX Runtime is unavailable on this device", e);
            env = null;
            return false;
        }
    }

    private OrtSession.SessionOptions options() throws OrtException {
        OrtSession.SessionOptions options = new OrtSession.SessionOptions();
        options.setIntraOpNumThreads(1);
        options.setInterOpNumThreads(1);
        options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT);
        options.setExecutionMode(OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL);
        return options;
    }

    public String getModelPath() {
        return modelDir.getAbsolutePath();
    }

    public String getLoadedModelName() {
        return loadedName;
    }

    /** Used by the existing menu as an enable/disable control. */
    public List<String> listAvailableModels() {
        List<String> models = new ArrayList<>();
        if (!isEnabled()) {
            models.add(ENABLE_ACTION);
            return models;
        }

        File[] files = modelDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.getName().endsWith(".onnx")) models.add(file.getName());
            }
        }
        // Keep the existing menu useful even before models are copied.
        if (models.isEmpty()) {
            models.add("minilm.onnx");
            models.add("yolo.onnx");
        }
        models.add("Disable ONNX Runtime");
        return models;
    }

    public boolean loadModel(String name) {
        if (ENABLE_ACTION.equals(name)) {
            setEnabled(true);
            loadedName = "enabled (no model loaded)";
            return true;
        }
        if ("Disable ONNX Runtime".equals(name)) {
            setEnabled(false);
            loadedName = "none";
            return true;
        }
        if (!isEnabled()) return false;
        if (name != null && name.toLowerCase().contains("yolo")) return loadYolo();
        return loadText();
    }

    public boolean load(String name) {
        return loadModel(name);
    }

    public boolean loadText() {
        if (!ensureEnvironment()) return false;
        try {
            File file = new File(modelDir, "minilm.onnx");
            if (!file.exists()) {
                download(
                    "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/onnx/model.onnx",
                    file
                );
            }
            if (sessionMini != null) sessionMini.close();
            sessionMini = env.createSession(file.getAbsolutePath(), options());
            loadedName = "minilm.onnx";
            return true;
        } catch (Throwable e) {
            Log.e(TAG, "Unable to load text ONNX model", e);
            return false;
        }
    }

    public boolean loadYolo() {
        if (!ensureEnvironment()) return false;
        try {
            File file = new File(modelDir, "yolo.onnx");
            if (!file.exists()) {
                download(
                    "https://huggingface.co/ultralytics/yolov8n/resolve/main/yolov8n.onnx",
                    file
                );
            }
            if (sessionYolo != null) sessionYolo.close();
            sessionYolo = env.createSession(file.getAbsolutePath(), options());
            loadedName = "yolo.onnx";
            return true;
        } catch (Throwable e) {
            Log.e(TAG, "Unable to load vision ONNX model", e);
            return false;
        }
    }

    public long[] generateTokens(long[] ids, int max, float temperature) {
        // The current personality export does not provide generation yet.
        return ids;
    }

    public float[] embed(String text) {
        return embedText(text);
    }

    public float[] embedText(String text) {
        if (!isEnabled() || !ensureEnvironment()) return null;
        try {
            if (sessionMini == null && !loadText()) return null;
            long[] ids = tokenize(text, 128);
            long[][] input = new long[][] { ids };
            long[][] mask = new long[1][128];
            Arrays.fill(mask[0], 1L);

            OnnxTensor inputTensor = OnnxTensor.createTensor(env, input);
            OnnxTensor maskTensor = OnnxTensor.createTensor(env, mask);
            Map<String, OnnxTensor> values = new HashMap<>();
            values.put("input_ids", inputTensor);
            values.put("attention_mask", maskTensor);

            OrtSession.Result result = sessionMini.run(values);
            float[][][] output = (float[][][]) result.get(0).getValue();
            float[] average = new float[output[0][0].length];
            for (int i = 0; i < output[0].length; i++) {
                for (int j = 0; j < average.length; j++) average[j] += output[0][i][j];
            }
            for (int j = 0; j < average.length; j++) average[j] /= output[0].length;
            inputTensor.close();
            maskTensor.close();
            result.close();
            return average;
        } catch (Throwable e) {
            Log.e(TAG, "ONNX embedding failed", e);
            return null;
        }
    }

    public String detectToString(Bitmap bitmap) {
        return "Vision is unavailable until a YOLO ONNX model is loaded.";
    }

    private long[] tokenize(String text, int count) {
        long[] result = new long[count];
        String[] words = text.toLowerCase().split("\\s+");
        for (int i = 0; i < Math.min(words.length, count); i++) {
            result[i] = Math.abs(words[i].hashCode() % 30000) + 1;
        }
        return result;
    }

    private void download(String address, File destination) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(30000);
        connection.setReadTimeout(30000);
        try (InputStream input = connection.getInputStream();
             FileOutputStream output = new FileOutputStream(destination)) {
            byte[] buffer = new byte[8192];
            int length;
            while ((length = input.read(buffer)) != -1) output.write(buffer, 0, length);
        } finally {
            connection.disconnect();
        }
    }

    public void close() {
        try {
            if (sessionMini != null) sessionMini.close();
            if (sessionYolo != null) sessionYolo.close();
        } catch (Exception e) {
            Log.e(TAG, "Error closing ONNX sessions", e);
        }
        sessionMini = null;
        sessionYolo = null;
        env = null;
        loadedName = "none";
    }
}
