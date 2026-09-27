package com.aibot;

import java.io.*;
import java.util.Random;

/**
 * Tiny Transformer Neural Network
 * - No pretrained knowledge
 * - Fresh random weights
 * - Learns from conversation and datasets
 * - Designed for ARMv7a 32bit, 2GB RAM
 */
public class NeuralNetwork {

    // Model dimensions (tiny for ARMv7a)
    public static final int VOCAB_SIZE    = 8000;
    public static final int EMBED_DIM     = 128;
    public static final int NUM_HEADS     = 4;
    public static final int HEAD_DIM      = EMBED_DIM / NUM_HEADS; // 32
    public static final int FF_DIM        = 256;
    public static final int NUM_LAYERS    = 2;
    public static final int MAX_SEQ_LEN   = 128;
    public static final float LEARN_RATE  = 0.001f;

    // Weights - Embedding
    float[][] tokenEmbedding;   // [VOCAB_SIZE][EMBED_DIM]
    float[][] posEmbedding;     // [MAX_SEQ_LEN][EMBED_DIM]

    // Weights per layer
    float[][][] Wq, Wk, Wv, Wo; // [NUM_LAYERS][EMBED_DIM][EMBED_DIM]
    float[][][] W1, W2;          // [NUM_LAYERS][EMBED_DIM][FF_DIM]
    float[][] b1, b2;            // [NUM_LAYERS][FF_DIM/EMBED_DIM]

    // Output projection
    float[][] Wout; // [EMBED_DIM][VOCAB_SIZE]
    float[]   bout; // [VOCAB_SIZE]

    // Layer norms
    float[][] ln1_gamma, ln1_beta;
    float[][] ln2_gamma, ln2_beta;

    private Random rng = new Random(42);
    private boolean isInitialized = false;

    public NeuralNetwork() {
        initWeights();
    }

    // Initialize all weights randomly (no pretrained knowledge)
    private void initWeights() {
        tokenEmbedding = randomMatrix(VOCAB_SIZE, EMBED_DIM, 0.02f);
        posEmbedding   = randomMatrix(MAX_SEQ_LEN, EMBED_DIM, 0.02f);

        Wq = new float[NUM_LAYERS][EMBED_DIM][EMBED_DIM];
        Wk = new float[NUM_LAYERS][EMBED_DIM][EMBED_DIM];
        Wv = new float[NUM_LAYERS][EMBED_DIM][EMBED_DIM];
        Wo = new float[NUM_LAYERS][EMBED_DIM][EMBED_DIM];
        W1 = new float[NUM_LAYERS][EMBED_DIM][FF_DIM];
        W2 = new float[NUM_LAYERS][FF_DIM][EMBED_DIM];
        b1 = new float[NUM_LAYERS][FF_DIM];
        b2 = new float[NUM_LAYERS][EMBED_DIM];

        ln1_gamma = new float[NUM_LAYERS][EMBED_DIM];
        ln1_beta  = new float[NUM_LAYERS][EMBED_DIM];
        ln2_gamma = new float[NUM_LAYERS][EMBED_DIM];
        ln2_beta  = new float[NUM_LAYERS][EMBED_DIM];

        for (int l = 0; l < NUM_LAYERS; l++) {
            Wq[l] = randomMatrix(EMBED_DIM, EMBED_DIM, 0.02f);
            Wk[l] = randomMatrix(EMBED_DIM, EMBED_DIM, 0.02f);
            Wv[l] = randomMatrix(EMBED_DIM, EMBED_DIM, 0.02f);
            Wo[l] = randomMatrix(EMBED_DIM, EMBED_DIM, 0.02f);
            W1[l] = randomMatrix(EMBED_DIM, FF_DIM, 0.02f);
            W2[l] = randomMatrix(FF_DIM, EMBED_DIM, 0.02f);

            // Layer norm init: gamma=1, beta=0
            for (int i = 0; i < EMBED_DIM; i++) {
                ln1_gamma[l][i] = 1.0f;
                ln2_gamma[l][i] = 1.0f;
            }
        }

        Wout = randomMatrix(EMBED_DIM, VOCAB_SIZE, 0.02f);
        bout = new float[VOCAB_SIZE];

        isInitialized = true;
    }

    // Forward pass - returns logits over vocabulary
    public float[] forward(int[] inputTokens) {
        int seqLen = Math.min(inputTokens.length, MAX_SEQ_LEN);

        // Step 1: Token + Position Embeddings
        float[][] x = new float[seqLen][EMBED_DIM];
        for (int i = 0; i < seqLen; i++) {
            int tok = inputTokens[i];
            if (tok < 0 || tok >= VOCAB_SIZE) tok = 0;
            for (int d = 0; d < EMBED_DIM; d++) {
                x[i][d] = tokenEmbedding[tok][d] + posEmbedding[i][d];
            }
        }

        // Step 2: Transformer layers
        for (int l = 0; l < NUM_LAYERS; l++) {
            x = transformerLayer(x, seqLen, l);
        }

        // Step 3: Get last token's logits
        float[] lastHidden = x[seqLen - 1];
        float[] logits = new float[VOCAB_SIZE];
        for (int v = 0; v < VOCAB_SIZE; v++) {
            float sum = bout[v];
            for (int d = 0; d < EMBED_DIM; d++) {
                sum += lastHidden[d] * Wout[d][v];
            }
            logits[v] = sum;
        }

        return logits;
    }

    // Single transformer layer
    private float[][] transformerLayer(float[][] x, int seqLen, int layer) {
        // Multi-head self attention
        float[][] attnOut = multiHeadAttention(x, seqLen, layer);

        // Residual + LayerNorm
        float[][] x2 = new float[seqLen][EMBED_DIM];
        for (int i = 0; i < seqLen; i++) {
            for (int d = 0; d < EMBED_DIM; d++) {
                x2[i][d] = x[i][d] + attnOut[i][d];
            }
            x2[i] = layerNorm(x2[i], ln1_gamma[layer], ln1_beta[layer]);
        }

        // Feed Forward
        float[][] ffOut = feedForward(x2, seqLen, layer);

        // Residual + LayerNorm
        float[][] x3 = new float[seqLen][EMBED_DIM];
        for (int i = 0; i < seqLen; i++) {
            for (int d = 0; d < EMBED_DIM; d++) {
                x3[i][d] = x2[i][d] + ffOut[i][d];
            }
            x3[i] = layerNorm(x3[i], ln2_gamma[layer], ln2_beta[layer]);
        }

        return x3;
    }

    // Multi-head self attention
    private float[][] multiHeadAttention(float[][] x, int seqLen, int layer) {
        float[][] output = new float[seqLen][EMBED_DIM];

        for (int h = 0; h < NUM_HEADS; h++) {
            int start = h * HEAD_DIM;

            // Compute Q, K, V for this head
            float[][] Q = new float[seqLen][HEAD_DIM];
            float[][] K = new float[seqLen][HEAD_DIM];
            float[][] V = new float[seqLen][HEAD_DIM];

            for (int i = 0; i < seqLen; i++) {
                for (int d = 0; d < HEAD_DIM; d++) {
                    for (int e = 0; e < EMBED_DIM; e++) {
                        Q[i][d] += x[i][e] * Wq[layer][e][start + d];
                        K[i][d] += x[i][e] * Wk[layer][e][start + d];
                        V[i][d] += x[i][e] * Wv[layer][e][start + d];
                    }
                }
            }

            // Attention scores with causal mask
            float scale = (float)(1.0 / Math.sqrt(HEAD_DIM));
            for (int i = 0; i < seqLen; i++) {
                float[] scores = new float[seqLen];
                for (int j = 0; j <= i; j++) { // causal mask
                    float dot = 0;
                    for (int d = 0; d < HEAD_DIM; d++) {
                        dot += Q[i][d] * K[j][d];
                    }
                    scores[j] = dot * scale;
                }
                // Softmax
                scores = softmax(scores, i + 1);

                // Weighted sum of V
                for (int d = 0; d < HEAD_DIM; d++) {
                    float sum = 0;
                    for (int j = 0; j <= i; j++) {
                        sum += scores[j] * V[j][d];
                    }
                    output[i][start + d] += sum;
                }
            }
        }

        // Output projection
        float[][] projected = new float[seqLen][EMBED_DIM];
        for (int i = 0; i < seqLen; i++) {
            for (int d = 0; d < EMBED_DIM; d++) {
                for (int e = 0; e < EMBED_DIM; e++) {
                    projected[i][d] += output[i][e] * Wo[layer][e][d];
                }
            }
        }

        return projected;
    }

    // Feed forward network with ReLU
    private float[][] feedForward(float[][] x, int seqLen, int layer) {
        float[][] out = new float[seqLen][EMBED_DIM];
        for (int i = 0; i < seqLen; i++) {
            // Layer 1: EMBED_DIM -> FF_DIM
            float[] h = new float[FF_DIM];
            for (int f = 0; f < FF_DIM; f++) {
                float sum = b1[layer][f];
                for (int d = 0; d < EMBED_DIM; d++) {
                    sum += x[i][d] * W1[layer][d][f];
                }
                h[f] = Math.max(0, sum); // ReLU
            }
            // Layer 2: FF_DIM -> EMBED_DIM
            for (int d = 0; d < EMBED_DIM; d++) {
                float sum = b2[layer][d];
                for (int f = 0; f < FF_DIM; f++) {
                    sum += h[f] * W2[layer][f][d];
                }
                out[i][d] = sum;
            }
        }
        return out;
    }

    // Layer normalization
    private float[] layerNorm(float[] x, float[] gamma, float[] beta) {
        float mean = 0, variance = 0;
        for (float v : x) mean += v;
        mean /= x.length;
        for (float v : x) variance += (v - mean) * (v - mean);
        variance /= x.length;
        float std = (float) Math.sqrt(variance + 1e-5f);

        float[] out = new float[x.length];
        for (int i = 0; i < x.length; i++) {
            out[i] = gamma[i] * ((x[i] - mean) / std) + beta[i];
        }
        return out;
    }

    // Softmax over first n elements
    private float[] softmax(float[] x, int n) {
        float max = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < n; i++) max = Math.max(max, x[i]);
        float sum = 0;
        float[] out = new float[x.length];
        for (int i = 0; i < n; i++) {
            out[i] = (float) Math.exp(x[i] - max);
            sum += out[i];
        }
        for (int i = 0; i < n; i++) out[i] /= sum;
        return out;
    }

    // Generate next token given input tokens
    public int generateNextToken(int[] inputTokens, float temperature) {
        float[] logits = forward(inputTokens);

        // Apply temperature
        if (temperature > 0) {
            for (int i = 0; i < logits.length; i++) {
                logits[i] /= temperature;
            }
        }

        // Sample from softmax
        float[] probs = softmax(logits, logits.length);
        return sampleFromDistribution(probs);
    }

    // Sample token from probability distribution
    private int sampleFromDistribution(float[] probs) {
        float r = rng.nextFloat();
        float cumSum = 0;
        for (int i = 0; i < probs.length; i++) {
            cumSum += probs[i];
            if (r < cumSum) return i;
        }
        return probs.length - 1;
    }

    // Backpropagation - update weights from a training example
    public float train(int[] inputTokens, int targetToken) {
        // Forward pass
        float[] logits = forward(inputTokens);

        // Cross entropy loss
        float[] probs = softmax(logits, logits.length);
        float loss = -(float) Math.log(Math.max(probs[targetToken], 1e-10f));

        // Gradient of loss w.r.t. logits
        float[] dLogits = probs.clone();
        dLogits[targetToken] -= 1.0f;

        // Update output weights (simplified gradient descent)
        int seqLen = Math.min(inputTokens.length, MAX_SEQ_LEN);
        float[][] x = getHiddenState(inputTokens, seqLen);
        float[] lastHidden = x[seqLen - 1];

        for (int v = 0; v < VOCAB_SIZE; v++) {
            bout[v] -= LEARN_RATE * dLogits[v];
            for (int d = 0; d < EMBED_DIM; d++) {
                Wout[d][v] -= LEARN_RATE * dLogits[v] * lastHidden[d];
            }
        }

        // Update embeddings for input tokens
        float[] dHidden = new float[EMBED_DIM];
        for (int d = 0; d < EMBED_DIM; d++) {
            for (int v = 0; v < VOCAB_SIZE; v++) {
                dHidden[d] += dLogits[v] * Wout[d][v];
            }
        }

        for (int i = 0; i < seqLen; i++) {
            int tok = inputTokens[i];
            if (tok < 0 || tok >= VOCAB_SIZE) continue;
            for (int d = 0; d < EMBED_DIM; d++) {
                tokenEmbedding[tok][d] -= LEARN_RATE * dHidden[d] * 0.1f;
            }
        }

        return loss;
    }

    // Get hidden states for backprop
    private float[][] getHiddenState(int[] inputTokens, int seqLen) {
        float[][] x = new float[seqLen][EMBED_DIM];
        for (int i = 0; i < seqLen; i++) {
            int tok = inputTokens[i];
            if (tok < 0 || tok >= VOCAB_SIZE) tok = 0;
            for (int d = 0; d < EMBED_DIM; d++) {
                x[i][d] = tokenEmbedding[tok][d] + posEmbedding[i][d];
            }
        }
        for (int l = 0; l < NUM_LAYERS; l++) {
            x = transformerLayer(x, seqLen, l);
        }
        return x;
    }

    // Save weights to file
    public void saveWeights(File file) throws IOException {
        DataOutputStream dos = new DataOutputStream(
            new BufferedOutputStream(new FileOutputStream(file)));

        // Save token embeddings
        for (float[] row : tokenEmbedding)
            for (float v : row) dos.writeFloat(v);

        // Save pos embeddings
        for (float[] row : posEmbedding)
            for (float v : row) dos.writeFloat(v);

        // Save layer weights
        for (int l = 0; l < NUM_LAYERS; l++) {
            writeMatrix(dos, Wq[l]);
            writeMatrix(dos, Wk[l]);
            writeMatrix(dos, Wv[l]);
            writeMatrix(dos, Wo[l]);
            writeMatrix(dos, W1[l]);
            writeMatrix(dos, W2[l]);
            writeArray(dos, b1[l]);
            writeArray(dos, b2[l]);
            writeArray(dos, ln1_gamma[l]);
            writeArray(dos, ln1_beta[l]);
            writeArray(dos, ln2_gamma[l]);
            writeArray(dos, ln2_beta[l]);
        }

        writeMatrix(dos, Wout);
        writeArray(dos, bout);
        dos.close();
    }

    // Load weights from file
    public void loadWeights(File file) throws IOException {
        DataInputStream dis = new DataInputStream(
            new BufferedInputStream(new FileInputStream(file)));

        for (float[] row : tokenEmbedding)
            for (int i = 0; i < row.length; i++) row[i] = dis.readFloat();

        for (float[] row : posEmbedding)
            for (int i = 0; i < row.length; i++) row[i] = dis.readFloat();

        for (int l = 0; l < NUM_LAYERS; l++) {
            readMatrix(dis, Wq[l]);
            readMatrix(dis, Wk[l]);
            readMatrix(dis, Wv[l]);
            readMatrix(dis, Wo[l]);
            readMatrix(dis, W1[l]);
            readMatrix(dis, W2[l]);
            readArray(dis, b1[l]);
            readArray(dis, b2[l]);
            readArray(dis, ln1_gamma[l]);
            readArray(dis, ln1_beta[l]);
            readArray(dis, ln2_gamma[l]);
            readArray(dis, ln2_beta[l]);
        }

        readMatrix(dis, Wout);
        readArray(dis, bout);
        dis.close();
    }

    private void writeMatrix(DataOutputStream dos, float[][] m) throws IOException {
        for (float[] row : m) for (float v : row) dos.writeFloat(v);
    }

    private void writeArray(DataOutputStream dos, float[] arr) throws IOException {
        for (float v : arr) dos.writeFloat(v);
    }

    private void readMatrix(DataInputStream dis, float[][] m) throws IOException {
        for (float[] row : m) for (int i = 0; i < row.length; i++) row[i] = dis.readFloat();
    }

    private void readArray(DataInputStream dis, float[] arr) throws IOException {
        for (int i = 0; i < arr.length; i++) arr[i] = dis.readFloat();
    }

    private float[][] randomMatrix(int rows, int cols, float scale) {
        float[][] m = new float[rows][cols];
        for (int i = 0; i < rows; i++)
            for (int j = 0; j < cols; j++)
                m[i][j] = (float)(rng.nextGaussian() * scale);
        return m;
    }
}
