package com.aibot;

import android.content.Context;
import android.util.Log;
import java.util.*;

/**
 * PersonalityEngine - gives the bot the Whisper personality
 *
 * Works in two modes:
 * Mode 1: ONNX model loaded → uses it for personality styling
 * Mode 2: No ONNX model → uses phrase templates (always works)
 *
 * The Whisper personality:
 * - Calm, thoughtful, never rushed
 * - Speaks with weight and intention
 * - Honest about uncertainty
 * - Asks rather than assumes
 * - Short when short is enough
 * - Never hollow or filler
 */
public class PersonalityEngine {

    private static final String TAG = "PersonalityEngine";

    private OnnxEngine   onnxEngine;
    private Tokenizer    tokenizer;
    private boolean      useOnnx = false;
    private Random       rng     = new Random();

    // Whisper personality model filename
    public static final String PERSONALITY_MODEL = "whisper_personality.onnx";

    public PersonalityEngine(OnnxEngine onnxEngine, Tokenizer tokenizer) {
        this.onnxEngine = onnxEngine;
        this.tokenizer  = tokenizer;
        tryLoadPersonalityModel();
    }

    // ─── MODEL LOADING ────────────────────────────────────────────────────────

    private void tryLoadPersonalityModel() {
        if (onnxEngine != null) {
            List<String> models = onnxEngine.listAvailableModels();
            if (models.contains(PERSONALITY_MODEL)) {
                useOnnx = onnxEngine.loadModel(PERSONALITY_MODEL);
                Log.d(TAG, "Personality ONNX model loaded: " + useOnnx);
            } else {
                Log.d(TAG, "No personality ONNX model found, using templates");
            }
        }
    }

    public boolean loadModel(String filename) {
        if (onnxEngine == null) return false;
        useOnnx = onnxEngine.loadModel(filename);
        return useOnnx;
    }

    // ─── STYLE A RESPONSE ─────────────────────────────────────────────────────

    /**
     * Apply Whisper personality to any response
     * Either through ONNX model or template rules
     */
    public String styleResponse(String rawResponse, String userInput, EmotionSystem.Mood mood) {
        if (rawResponse == null || rawResponse.isEmpty()) return rawResponse;

        if (useOnnx) {
            String styled = styleWithOnnx(rawResponse, userInput);
            if (styled != null && styled.length() > 5) return styled;
        }

        // Always falls back to template styling
        return styleWithTemplates(rawResponse, userInput, mood);
    }

    // ─── ONNX STYLING ─────────────────────────────────────────────────────────

    private String styleWithOnnx(String response, String userInput) {
        try {
            // Build prompt: "rephrase this in a calm thoughtful tone: <response>"
            String prompt = "calm: " + response;
            int[] inputIds = tokenizer.encode(prompt, true, false);

            // Convert to long[]
            long[] tokenIds = new long[inputIds.length];
            for (int i = 0; i < inputIds.length; i++) tokenIds[i] = inputIds[i];

            // Generate styled tokens
            long[] outputIds = onnxEngine.generateTokens(tokenIds, 40, 0.7f);
            if (outputIds == null || outputIds.length == 0) return null;

            // Convert back to int[] for decoding
            int[] outInts = new int[outputIds.length];
            for (int i = 0; i < outputIds.length; i++) outInts[i] = (int) outputIds[i];

            String result = tokenizer.decode(outInts).trim();
            return result.length() > 5 ? result : null;

        } catch (Exception e) {
            Log.e(TAG, "ONNX styling error: " + e.getMessage());
            return null;
        }
    }

    // ─── TEMPLATE STYLING ─────────────────────────────────────────────────────

    /**
     * Apply Whisper personality rules to raw response
     * Works without any model
     */
    private String styleWithTemplates(String response, String userInput, EmotionSystem.Mood mood) {
        String lower = userInput.toLowerCase().trim();

        // Rule 1: Remove filler phrases
        response = removeFiller(response);

        // Rule 2: Shorten if too long
        response = trimToEssential(response);

        // Rule 3: Apply mood-based prefix occasionally
        if (rng.nextFloat() > 0.7f) {
            response = applyMoodPrefix(response, mood);
        }

        // Rule 4: Fix hollow endings
        response = fixHollowEndings(response);

        return response.trim();
    }

    private String removeFiller(String text) {
        return text
            .replaceAll("(?i)^(certainly|absolutely|of course|sure thing|great question)[!,.]?\\s*", "")
            .replaceAll("(?i)^(I'd be happy to help[!.]?\\s*)", "")
            .replaceAll("(?i)^(That's a great question[!.]?\\s*)", "")
            .replaceAll("(?i)^(Definitely[!.]?\\s*)", "")
            .replaceAll("(?i)^(No problem[!.]?\\s*)", "")
            .replaceAll("(?i)\\s*(Is there anything else I can help you with\\??)$", "")
            .replaceAll("(?i)\\s*(Let me know if you need anything else\\.?)$", "")
            .replaceAll("(?i)\\s*(Hope that helps!?)$", "")
            .trim();
    }

    private String trimToEssential(String text) {
        // If more than 3 sentences, keep the most important ones
        String[] sentences = text.split("(?<=[.!?])\\s+");
        if (sentences.length <= 3) return text;

        // Keep first 2 + last 1
        StringBuilder sb = new StringBuilder();
        sb.append(sentences[0]).append(" ");
        sb.append(sentences[1]);
        if (sentences.length > 2) {
            sb.append(" ").append(sentences[sentences.length - 1]);
        }
        return sb.toString();
    }

    private String applyMoodPrefix(String response, EmotionSystem.Mood mood) {
        switch (mood) {
            case CURIOUS:
                return pick("Interesting. ", "I'm thinking about this. ") + response;
            case CONFUSED:
                return pick("Hmm. ", "Let me think. ") + response;
            case EXCITED:
                return pick("Oh. ", "Wait — ") + response;
            case PROUD:
                return pick("I know this one. ", "Actually — ") + response;
            default:
                return response;
        }
    }

    private String fixHollowEndings(String text) {
        // Replace weak endings with nothing or something more intentional
        text = text.replaceAll("(?i)\\s*you know[?.]?$", ".");
        text = text.replaceAll("(?i)\\s*right[?.]?$", ".");
        text = text.replaceAll("(?i)\\s*okay[?.]?$", ".");
        return text;
    }

    // ─── WHISPER RESPONSE TEMPLATES ───────────────────────────────────────────

    /**
     * Generate a Whisper-style response for common situations
     * Used when NN has nothing to say
     */
    public String getWhisperResponse(String situation) {
        switch (situation) {
            case "dont_know":
                return pick(
                    "That's beyond what I know yet. Want me to search for it?",
                    "I haven't learned that. Should I look it up?",
                    "Not something I know. Teach me, or I can search.",
                    "I don't have that yet. Should I find out?"
                );
            case "uncertain":
                return pick(
                    "I think so, but I'm not certain. Want me to confirm?",
                    "That's my understanding, though I could be wrong.",
                    "I believe that's right. But verify if it matters."
                );
            case "learned":
                return pick(
                    "Got it. I'll hold onto that.",
                    "Understood. Filed away.",
                    "I've got that now.",
                    "Noted. Tell me more if there's more."
                );
            case "thinking":
                return pick(
                    "Give me a moment.",
                    "Let me work through that.",
                    "Thinking.",
                    "One second."
                );
            case "searching":
                return pick(
                    "Looking that up now.",
                    "Searching.",
                    "On it.",
                    "Finding that for you."
                );
            case "greeting_fresh":
                return pick(
                    "I'm here. What's on your mind?",
                    "Ready. What do you need?",
                    "I'm listening.",
                    "What would you like to talk about?"
                );
            case "greeting_return":
                return pick(
                    "You're back. What's next?",
                    "Welcome back. What do you need?",
                    "I remembered everything. What now?",
                    "Still here. What's going on?"
                );
            case "error":
                return pick(
                    "Something went wrong on my end. Try again.",
                    "That didn't work. Let me try differently.",
                    "I hit a problem. What would you like me to do instead?"
                );
            case "device_done":
                return pick(
                    "Done.",
                    "Done. Anything else?",
                    "Handled."
                );
            case "cant_do":
                return pick(
                    "I can't do that from here.",
                    "That's outside what I can reach.",
                    "Not something I can do directly. But I can try another way."
                );
            default:
                return null;
        }
    }

    // ─── EXPORT HELPER ────────────────────────────────────────────────────────

    /**
     * Instructions for exporting our trained NeuralNetwork to ONNX
     * Run this Python script on PC after training
     */
    public static String getExportInstructions() {
        return "# Export AIBot NeuralNetwork to ONNX\n" +
               "# Run on PC after copying model.bin from device\n\n" +
               "# pip install torch onnx numpy\n\n" +
               "import torch\n" +
               "import torch.nn as nn\n\n" +
               "class TinyTransformer(nn.Module):\n" +
               "    def __init__(self):\n" +
               "        super().__init__()\n" +
               "        self.embed = nn.Embedding(8000, 128)\n" +
               "        self.pos   = nn.Embedding(128, 128)\n" +
               "        layer = nn.TransformerEncoderLayer(128, 4, 256, batch_first=True)\n" +
               "        self.transformer = nn.TransformerEncoder(layer, 2)\n" +
               "        self.out = nn.Linear(128, 8000)\n\n" +
               "    def forward(self, x):\n" +
               "        pos = torch.arange(x.size(1)).unsqueeze(0)\n" +
               "        x = self.embed(x) + self.pos(pos)\n" +
               "        x = self.transformer(x)\n" +
               "        return self.out(x[:, -1, :])\n\n" +
               "model = TinyTransformer()\n" +
               "dummy = torch.zeros(1, 10, dtype=torch.long)\n" +
               "torch.onnx.export(\n" +
               "    model, dummy, 'aibot_model.onnx',\n" +
               "    input_names=['input_ids'],\n" +
               "    output_names=['logits'],\n" +
               "    dynamic_axes={'input_ids': {1: 'seq_len'}},\n" +
               "    opset_version=12\n" +
               ")\n" +
               "print('Exported! Copy aibot_model.onnx to device models/ folder')";
    }

    // ─── STATUS ───────────────────────────────────────────────────────────────

    public boolean isOnnxActive() { return useOnnx; }

    public String getStatus() {
        return useOnnx
            ? "Personality: ONNX model active (" + onnxEngine.getLoadedModelName() + ")"
            : "Personality: Template mode (place whisper_personality.onnx in models/ to upgrade)";
    }

    // ─── HELPER ───────────────────────────────────────────────────────────────

    private String pick(String... options) {
        return options[rng.nextInt(options.length)];
    }
}
