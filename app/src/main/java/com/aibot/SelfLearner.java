package com.aibot;

import android.util.Log;
import java.util.*;

/**
 * SelfLearner - trains the neural network and updates NARS beliefs
 * from conversations and loaded datasets
 */
public class SelfLearner {

    private static final String TAG = "SelfLearner";
    private static final int TRAIN_EPOCHS   = 3;
    private static final int SAVE_INTERVAL  = 50; // save every N samples

    private NeuralNetwork nn;
    private Tokenizer     tokenizer;
    private NARSEngine    nars;
    private WeightManager weightManager;

    private float totalLoss   = 0;
    private int   trainSteps  = 0;
    private boolean isTraining = false;

    public interface LearningCallback {
        void onProgress(int step, int total, float loss, String status);
        void onComplete(float avgLoss, int steps);
        void onError(String error);
    }

    public SelfLearner(NeuralNetwork nn, Tokenizer tokenizer,
                       NARSEngine nars, WeightManager wm) {
        this.nn            = nn;
        this.tokenizer     = tokenizer;
        this.nars          = nars;
        this.weightManager = wm;
    }

    // ─── LEARN FROM CONVERSATION ──────────────────────────────────────────────

    /**
     * Learn from a single user message immediately
     * Called after every user input
     */
    public void learnFromMessage(String userMessage, String botResponse) {
        new Thread(() -> {
            // 1. Update tokenizer vocabulary
            tokenizer.learnFromText(userMessage);
            tokenizer.learnFromText(botResponse);

            // 2. Update NARS beliefs from user message
            List<Belief> learned = nars.parseAndLearn(userMessage);
            if (!learned.isEmpty()) {
                Log.d(TAG, "Learned " + learned.size() + " beliefs from message");
            }

            // 3. Train neural network on this exchange
            trainOnPair(userMessage, botResponse);

            // 4. Save periodically
            if (trainSteps % SAVE_INTERVAL == 0) {
                weightManager.saveAll();
            }
        }).start();
    }

    /**
     * Train on a single input→output pair
     */
    public float trainOnPair(String input, String output) {
        // Combine: encode input + output as sequence
        String combined = input + " " + output;
        tokenizer.learnFromText(combined);

        int[] inputIds  = tokenizer.encode(input,  true, false);
        int[] outputIds = tokenizer.encode(output, false, true);

        // Train: for each output token, predict it given all previous tokens
        float totalPairLoss = 0;
        int count = 0;

        // Build full sequence
        int[] fullSeq = concat(inputIds, outputIds);

        for (int i = inputIds.length; i < fullSeq.length - 1; i++) {
            // Input is everything up to position i
            int[] contextTokens = Arrays.copyOfRange(fullSeq, 0, i + 1);
            int   targetToken   = fullSeq[i + 1];

            if (targetToken >= NeuralNetwork.VOCAB_SIZE) continue;

            float loss = nn.train(contextTokens, targetToken);
            totalPairLoss += loss;
            count++;
            trainSteps++;
            totalLoss += loss;
        }

        return count > 0 ? totalPairLoss / count : 0f;
    }

    // ─── LEARN FROM DATASET ───────────────────────────────────────────────────

    /**
     * Train on a full dataset asynchronously
     */
    public void learnFromDataset(List<DatasetLoader.TrainingSample> samples,
                                  LearningCallback callback) {
        if (isTraining) {
            if (callback != null) callback.onError("Already training!");
            return;
        }

        new Thread(() -> {
            isTraining = true;
            int total  = samples.size() * TRAIN_EPOCHS;
            int step   = 0;
            float epochLoss = 0;

            try {
                for (int epoch = 0; epoch < TRAIN_EPOCHS; epoch++) {
                    // Shuffle for each epoch
                    Collections.shuffle(samples);

                    for (DatasetLoader.TrainingSample sample : samples) {
                        // Learn vocabulary
                        tokenizer.learnFromText(sample.input);
                        tokenizer.learnFromText(sample.output);

                        // Learn NARS beliefs
                        nars.parseAndLearn(sample.input);
                        nars.parseAndLearn(sample.output);
                        if (!sample.context.isEmpty()) {
                            nars.parseAndLearn(sample.context);
                        }

                        // Train neural network
                        float loss = trainOnPair(sample.input, sample.output);
                        epochLoss += loss;
                        step++;

                        // Report progress
                        if (callback != null && step % 10 == 0) {
                            float avgLoss = step > 0 ? epochLoss / step : 0;
                            callback.onProgress(step, total, avgLoss,
                                "Epoch " + (epoch+1) + "/" + TRAIN_EPOCHS);
                        }

                        // Save periodically
                        if (step % SAVE_INTERVAL == 0) {
                            weightManager.saveAll();
                        }
                    }
                }

                // Final save
                weightManager.saveAll();
                float avgLoss = step > 0 ? epochLoss / step : 0;
                if (callback != null) callback.onComplete(avgLoss, step);

            } catch (Exception e) {
                Log.e(TAG, "Training error: " + e.getMessage());
                if (callback != null) callback.onError(e.getMessage());
            } finally {
                isTraining = false;
            }
        }).start();
    }

    // ─── LEARN FROM WEB ───────────────────────────────────────────────────────

    /**
     * Learn from web search results
     */
    public void learnFromWebResults(List<String> facts) {
        new Thread(() -> {
            for (String fact : facts) {
                tokenizer.learnFromText(fact);
                nars.parseAndLearn(fact);

                // Train neural network on facts
                if (fact.length() > 10) {
                    int[] tokens = tokenizer.encode(fact);
                    for (int i = 0; i < tokens.length - 1; i++) {
                        int[] context = Arrays.copyOfRange(tokens, 0, i + 1);
                        nn.train(context, tokens[i + 1]);
                        trainSteps++;
                    }
                }
            }
            Log.d(TAG, "Learned from " + facts.size() + " web facts");
        }).start();
    }

    // ─── STATS ────────────────────────────────────────────────────────────────

    public float getAverageLoss() {
        return trainSteps > 0 ? totalLoss / trainSteps : 0;
    }

    public int getTrainSteps() { return trainSteps; }

    public boolean isTraining() { return isTraining; }

    // ─── HELPERS ──────────────────────────────────────────────────────────────

    private int[] concat(int[] a, int[] b) {
        int[] result = new int[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }
}
