package com.aibot;

import android.content.Context;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.util.*;

/**
 * DatasetLoader - loads training data from app's dataset folder
 * Android 11 compatible: uses path from WeightManager
 * Supports: .txt, .json, .jsonl, .csv formats
 * Compatible with HuggingFace dataset exports
 */
public class DatasetLoader {

    private static final String TAG = "DatasetLoader";

    // Set at runtime by WeightManager
    private File datasetDir;

    public DatasetLoader(File datasetDir) {
        this.datasetDir = datasetDir;
    }

    public interface LoadCallback {
        void onProgress(int loaded, int total, String filename);
        void onComplete(List<TrainingSample> samples);
        void onError(String error);
    }

    public static class TrainingSample {
        public String input;
        public String output;
        public String context;

        public TrainingSample(String input, String output) {
            this.input   = input;
            this.output  = output;
            this.context = "";
        }

        public TrainingSample(String input, String output, String context) {
            this.input   = input;
            this.output  = output;
            this.context = context;
        }
    }

    // ─── LIST AVAILABLE DATASETS ──────────────────────────────────────────────

    public List<String> listDatasets() {
        List<String> files = new ArrayList<>();
        if (!datasetDir.exists()) {
            datasetDir.mkdirs();
            return files;
        }
        File[] list = datasetDir.listFiles();
        if (list != null) {
            for (File f : list) {
                String name = f.getName().toLowerCase();
                if (name.endsWith(".txt") || name.endsWith(".json") ||
                    name.endsWith(".jsonl") || name.endsWith(".csv")) {
                    files.add(f.getName());
                }
            }
        }
        return files;
    }

    // ─── ASYNC LOAD ───────────────────────────────────────────────────────────

    public void loadAsync(String filename, LoadCallback callback) {
        new Thread(() -> {
            try {
                List<TrainingSample> samples = load(filename, callback);
                callback.onComplete(samples);
            } catch (Exception e) {
                callback.onError("Failed to load " + filename + ": " + e.getMessage());
            }
        }).start();
    }

    // ─── MAIN LOAD ────────────────────────────────────────────────────────────

    public List<TrainingSample> load(String filename, LoadCallback callback) throws IOException {
        File file = new File(datasetDir, filename);
        if (!file.exists()) throw new IOException("File not found: " + filename);

        String lower = filename.toLowerCase();
        if (lower.endsWith(".jsonl"))     return loadJsonl(file, callback);
        else if (lower.endsWith(".json")) return loadJson(file, callback);
        else if (lower.endsWith(".csv"))  return loadCsv(file, callback);
        else                              return loadTxt(file, callback);
    }

    // ─── TXT LOADER ───────────────────────────────────────────────────────────

    private List<TrainingSample> loadTxt(File file, LoadCallback callback) throws IOException {
        List<TrainingSample> samples = new ArrayList<>();
        BufferedReader br = new BufferedReader(new FileReader(file));
        List<String> lines = new ArrayList<>();
        String line;
        while ((line = br.readLine()) != null) {
            line = line.trim();
            if (!line.isEmpty()) lines.add(line);
        }
        br.close();

        // Create sliding window samples
        for (int i = 0; i < lines.size() - 1; i++) {
            samples.add(new TrainingSample(lines.get(i), lines.get(i + 1)));
            if (callback != null && i % 100 == 0) {
                callback.onProgress(i, lines.size(), file.getName());
            }
        }
        Log.d(TAG, "Loaded " + samples.size() + " samples from txt");
        return samples;
    }

    // ─── JSONL LOADER ─────────────────────────────────────────────────────────
    // HuggingFace datasets often export as JSONL

    private List<TrainingSample> loadJsonl(File file, LoadCallback callback) throws IOException {
        List<TrainingSample> samples = new ArrayList<>();
        BufferedReader br = new BufferedReader(new FileReader(file));
        String line;
        int count = 0;

        while ((line = br.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) continue;
            try {
                JSONObject obj = new JSONObject(line);
                TrainingSample sample = parseJsonObject(obj);
                if (sample != null) {
                    samples.add(sample);
                    count++;
                    if (callback != null && count % 100 == 0) {
                        callback.onProgress(count, -1, file.getName());
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Skip bad JSONL line: " + e.getMessage());
            }
        }
        br.close();
        Log.d(TAG, "Loaded " + samples.size() + " samples from jsonl");
        return samples;
    }

    // ─── JSON LOADER ──────────────────────────────────────────────────────────

    private List<TrainingSample> loadJson(File file, LoadCallback callback) throws IOException {
        List<TrainingSample> samples = new ArrayList<>();

        StringBuilder sb = new StringBuilder();
        BufferedReader br = new BufferedReader(new FileReader(file));
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();

        try {
            String text = sb.toString().trim();
            if (text.startsWith("[")) {
                // Array of objects
                JSONArray arr = new JSONArray(text);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    TrainingSample s = parseJsonObject(obj);
                    if (s != null) samples.add(s);
                    if (callback != null && i % 100 == 0)
                        callback.onProgress(i, arr.length(), file.getName());
                }
            } else {
                // Single object or nested
                JSONObject obj = new JSONObject(text);
                // Check for "data" or "train" key (HuggingFace format)
                if (obj.has("data")) {
                    JSONArray arr = obj.getJSONArray("data");
                    for (int i = 0; i < arr.length(); i++) {
                        TrainingSample s = parseJsonObject(arr.getJSONObject(i));
                        if (s != null) samples.add(s);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "JSON parse error: " + e.getMessage());
        }

        Log.d(TAG, "Loaded " + samples.size() + " samples from json");
        return samples;
    }

    // ─── CSV LOADER ───────────────────────────────────────────────────────────

    private List<TrainingSample> loadCsv(File file, LoadCallback callback) throws IOException {
        List<TrainingSample> samples = new ArrayList<>();
        BufferedReader br = new BufferedReader(new FileReader(file));

        // Read header
        String header = br.readLine();
        if (header == null) { br.close(); return samples; }

        String[] cols = header.split(",");
        int inputCol = -1, outputCol = -1;

        // Find input/output columns
        for (int i = 0; i < cols.length; i++) {
            String col = cols[i].toLowerCase().trim().replace("\"","");
            if (col.equals("input") || col.equals("question") ||
                col.equals("prompt") || col.equals("text") || col.equals("instruction"))
                inputCol = i;
            if (col.equals("output") || col.equals("answer") ||
                col.equals("response") || col.equals("completion") || col.equals("label"))
                outputCol = i;
        }

        if (inputCol == -1) inputCol = 0;
        if (outputCol == -1) outputCol = Math.min(1, cols.length - 1);

        String line;
        int count = 0;
        while ((line = br.readLine()) != null) {
            String[] parts = splitCsvLine(line);
            if (parts.length > Math.max(inputCol, outputCol)) {
                String input  = parts[inputCol].trim().replace("\"","");
                String output = parts[outputCol].trim().replace("\"","");
                if (!input.isEmpty() && !output.isEmpty()) {
                    samples.add(new TrainingSample(input, output));
                    count++;
                    if (callback != null && count % 100 == 0)
                        callback.onProgress(count, -1, file.getName());
                }
            }
        }
        br.close();
        Log.d(TAG, "Loaded " + samples.size() + " samples from csv");
        return samples;
    }

    // ─── JSON OBJECT PARSER ───────────────────────────────────────────────────

    private TrainingSample parseJsonObject(JSONObject obj) {
        try {
            String input = null, output = null, context = "";

            // Try common HuggingFace field names
            String[] inputKeys  = {"input","question","prompt","text","instruction","query"};
            String[] outputKeys = {"output","answer","response","completion","label","target"};

            for (String k : inputKeys) {
                if (obj.has(k)) { input = obj.getString(k); break; }
            }
            for (String k : outputKeys) {
                if (obj.has(k)) { output = obj.getString(k); break; }
            }

            // Conversation format: messages array (HuggingFace everyday-conversations)
            if (input == null && obj.has("messages")) {
                JSONArray msgs = obj.getJSONArray("messages");
                // Find user->assistant pairs
                for (int mi = 0; mi < msgs.length() - 1; mi++) {
                    JSONObject m1 = msgs.getJSONObject(mi);
                    JSONObject m2 = msgs.getJSONObject(mi + 1);
                    String r1 = m1.optString("role","");
                    String r2 = m2.optString("role","");
                    if ((r1.equals("user") || r1.equals("human")) &&
                        (r2.equals("assistant") || r2.equals("bot"))) {
                        input  = m1.optString("content","");
                        output = m2.optString("content","");
                        break;
                    }
                }
                // Fallback: just first two
                if (input == null && msgs.length() >= 2) {
                    input  = msgs.getJSONObject(0).optString("content","");
                    output = msgs.getJSONObject(1).optString("content","");
                }
            }

            // Persona-Chat format: dialogue array
            if (input == null && obj.has("dialogue")) {
                JSONArray dialogue = obj.getJSONArray("dialogue");
                if (dialogue.length() >= 2) {
                    input  = dialogue.getString(0);
                    output = dialogue.getString(1);
                }
            }

            // Synthetic-Persona-Chat format
            if (input == null && obj.has("Best Generated Conversation")) {
                String conv = obj.getString("Best Generated Conversation");
                String[] lines = conv.split("\n");
                for (int li = 0; li < lines.length - 1; li++) {
                    if (lines[li].startsWith("User 1:") || lines[li].startsWith("A:")) {
                        input  = lines[li].replaceFirst("^(User 1:|A:)\s*","").trim();
                        output = lines[li+1].replaceFirst("^(User 2:|B:)\s*","").trim();
                        break;
                    }
                }
            }

            // Context field
            if (obj.has("context")) context = obj.getString("context");

            if (input != null && output != null &&
                !input.isEmpty() && !output.isEmpty()) {
                return new TrainingSample(input, output, context);
            }
        } catch (Exception e) {
            Log.w(TAG, "parseJsonObject: " + e.getMessage());
        }
        return null;
    }

    // ─── CSV HELPER ───────────────────────────────────────────────────────────

    private String[] splitCsvLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        result.add(current.toString());
        return result.toArray(new String[0]);
    }
}
