package com.aibot;

import java.io.*;
import android.util.Log;

public class WeightManager {
    private android.content.Context context;
    private NeuralNetwork nn;
    private Tokenizer tokenizer;
    private NARSEngine nars;
    private File baseDir;
    private File datasetDir;

    public WeightManager(android.content.Context ctx, NeuralNetwork nn, Tokenizer tokenizer, NARSEngine nars) {
        this.context = ctx;
        this.nn = nn;
        this.tokenizer = tokenizer;
        this.nars = nars;
        this.baseDir = new File(ctx.getFilesDir(), "aibot_weights");
        if (!baseDir.exists()) baseDir.mkdirs();
        this.datasetDir = new File(ctx.getExternalFilesDir(null), "datasets");
        if (datasetDir == null) datasetDir = new File(baseDir, "datasets");
        if (!datasetDir.exists()) datasetDir.mkdirs();
    }

    public File getDatasetDir() { return datasetDir; }
    public String getDatasetPath() { return datasetDir.getAbsolutePath(); }
    public String getModelPath() { return baseDir.getAbsolutePath(); }

    public boolean hasExistingWeights() {
        File f = new File(baseDir, "weights.bin");
        return f.exists() && f.length() > 100;
    }

    // REAL SAVE - calls nn.saveWeights()
    public void saveAll() {
        try {
            // 1. Save neural weights (this is where weight increase is saved)
            File w = new File(baseDir, "weights.bin");
            nn.saveWeights(w);
            Log.d("WeightManager", "Saved weights: " + w.length() + " bytes");

            // 2. Save beliefs
            File bel = beliefsFile();
            FileWriter fw = new FileWriter(bel);
            // if you add getAllBeliefs() to NARSEngine, save them here
            fw.close();

            // 3. Save tokenizer vocab
            File vocab = new File(baseDir, "vocab.txt");
            // tokenizer.saveVocab(vocab); // add if you have save method

        } catch (Exception e) {
            Log.e("WeightManager", "saveAll", e);
        }
    }

    // REAL LOAD
    public void loadAll() {
        try {
            File w = new File(baseDir, "weights.bin");
            if (w.exists() && w.length() > 100) {
                nn.loadWeights(w);
                Log.d("WeightManager", "Loaded weights: " + w.length());
            }

            File bel = beliefsFile();
            if (bel.exists() && nars != null) {
                BufferedReader br = new BufferedReader(new FileReader(bel));
                String line;
                while ((line = br.readLine()) != null) {
                    nars.parseAndLearn(line);
                }
                br.close();
            }
        } catch (Exception e) {
            Log.e("WeightManager", "loadAll", e);
        }
    }

    public void appendHistory(String input, String response) {
        try {
            File hist = new File(baseDir, "history.jsonl");
            FileWriter fw = new FileWriter(hist, true);
            fw.write(input + " -> " + response + "\n");
            fw.close();
        } catch (Exception e) {}
    }

    public String getInfo() {
        int beliefCount = 0;
        try { beliefCount = nars != null ? nars.getBeliefCount() : 0; } catch (Exception e) {}
        File w = new File(baseDir, "weights.bin");
        String wInfo = w.exists() ? (w.length()/1024)+"KB" : "no weights";
        return "Beliefs:" + beliefCount + " | Weights:" + wInfo + " | " + baseDir.getName();
    }

    public void resetAll() {
        try {
            File[] files = baseDir.listFiles();
            if (files != null) for (File f : files) f.delete();
        } catch (Exception e) {}
    }

    private File beliefsFile() { return new File(baseDir, "beliefs.txt"); }
}