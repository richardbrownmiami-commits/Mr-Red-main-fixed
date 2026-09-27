package com.aibot;

import java.util.*;

/**
 * ConversationManager - makes conversation feel natural and human
 * Handles:
 * - Option B: ask before searching
 * - Follow-up questions
 * - Clarification requests
 * - Proactive fact sharing
 * - Thinking delays
 * - Varied responses
 */
public class ConversationManager {

    public enum State {
        NORMAL,
        WAITING_SEARCH_CONFIRM,  // asked "should I search?" waiting for yes/no
        WAITING_CLARIFICATION,   // asked "what do you mean?" waiting for answer
        WAITING_NAME             // asked "what's your name?" waiting
    }

    private State  currentState = State.NORMAL;
    private String pendingSearchQuery = null;
    private String pendingTopic       = null;

    private EmotionSystem emotionSystem;
    private UserMemory    userMemory;
    private NARSEngine    nars;
    private Random        rng = new Random();

    // Track recent responses to avoid repetition
    private List<String> recentResponses = new ArrayList<>();
    private static final int MAX_RECENT = 5;

    // Ambiguous/vague words that need clarification
    private static final Set<String> VAGUE_WORDS = new HashSet<>(Arrays.asList(
        "it", "that", "this", "thing", "stuff", "something", "anything"
    ));

    public ConversationManager(EmotionSystem emotionSystem,
                                UserMemory userMemory, NARSEngine nars) {
        this.emotionSystem = emotionSystem;
        this.userMemory    = userMemory;
        this.nars          = nars;
    }

    // ─── STATE MACHINE ────────────────────────────────────────────────────────

    public State getState() { return currentState; }

    public void setState(State state) { currentState = state; }

    public void setPendingSearch(String query, String topic) {
        this.pendingSearchQuery = query;
        this.pendingTopic       = topic;
        this.currentState       = State.WAITING_SEARCH_CONFIRM;
    }

    public String getPendingSearchQuery() { return pendingSearchQuery; }
    public String getPendingTopic()       { return pendingTopic; }

    public void clearPending() {
        pendingSearchQuery = null;
        pendingTopic       = null;
        currentState       = State.NORMAL;
    }

    // ─── YES/NO DETECTION ─────────────────────────────────────────────────────

    public boolean isYes(String input) {
        input = input.toLowerCase().trim();
        return input.equals("yes") || input.equals("y") ||
               input.equals("yeah") || input.equals("yep") ||
               input.equals("sure") || input.equals("ok") ||
               input.equals("okay") || input.equals("go ahead") ||
               input.equals("do it") || input.startsWith("yes") ||
               input.contains("search") || input.contains("look it up");
    }

    public boolean isNo(String input) {
        input = input.toLowerCase().trim();
        return input.equals("no") || input.equals("n") ||
               input.equals("nope") || input.equals("nah") ||
               input.equals("don't") || input.equals("skip") ||
               input.startsWith("no ");
    }

    // ─── VAGUE QUESTION DETECTION ─────────────────────────────────────────────

    public boolean isVague(String input) {
        String[] words = input.toLowerCase().split(" ");
        if (words.length <= 2) {
            for (String w : words) {
                if (VAGUE_WORDS.contains(w)) return true;
            }
        }
        return false;
    }

    public boolean needsClarification(String input) {
        input = input.toLowerCase().trim();
        // Very short question with no clear subject
        if (input.split(" ").length <= 3) {
            String[] vagueStarters = {"what", "how", "why", "when", "where"};
            for (String s : vagueStarters) {
                if (input.startsWith(s + " ") && input.split(" ").length <= 3) {
                    return isVague(input);
                }
            }
        }
        return false;
    }

    // ─── TOPIC EXTRACTION ─────────────────────────────────────────────────────

    public String extractTopic(String input) {
        String[] stopWords = {"what","is","are","a","an","the","how","does",
                              "do","can","i","you","me","my","tell","about",
                              "please","could","would","should","?","!"};
        Set<String> stops = new HashSet<>(Arrays.asList(stopWords));

        String[] words = input.toLowerCase()
                              .replaceAll("[?!.,]", "")
                              .split(" ");
        String best = "";
        for (String w : words) {
            if (!stops.contains(w) && w.length() > best.length() && w.length() > 2) {
                best = w;
            }
        }
        return best.isEmpty() ? input : best;
    }

    // ─── RESPONSE BUILDING ────────────────────────────────────────────────────

    /**
     * Wrap a response naturally — add prefix, suffix, personality
     */
    public String buildNaturalResponse(String coreResponse, String topic,
                                        boolean knewAnswer, boolean casual) {
        StringBuilder sb = new StringBuilder();

        // Prefix based on mood
        if (knewAnswer) {
            emotionSystem.onAnsweredSuccessfully();
            if (rng.nextFloat() > 0.6f) {
                sb.append(emotionSystem.getProudPhrase(topic)).append(" ");
            }
        }

        sb.append(coreResponse);

        // Add follow-up question sometimes
        if (rng.nextFloat() > 0.6f) {
            String followUp = emotionSystem.getFollowUpQuestion(topic);
            if (followUp != null) {
                sb.append("\n\n").append(followUp);
            }
        }

        // Adjust tone
        String result = emotionSystem.adjustTone(sb.toString(), casual);

        // Check for repetition
        if (isRepetitive(result)) {
            result = coreResponse; // fallback to plain
        }

        trackResponse(result);
        return result;
    }

    /**
     * Build the "should I search?" prompt - Option B
     */
    public String buildSearchPrompt(String topic) {
        emotionSystem.onDidntKnowAnswer();
        emotionSystem.onInteraction();
        return emotionSystem.getSearchPromptPhrase(topic);
    }

    /**
     * Build search confirmed response
     */
    public String buildSearchingResponse(String topic) {
        emotionSystem.onSearching();
        return pick(
            "On it! Searching for " + topic + "...",
            "Let me look that up for you!",
            "Searching the web for " + topic + "... one sec!"
        );
    }

    /**
     * Build search declined response
     */
    public String buildSearchDeclinedResponse(String topic) {
        return pick(
            "No problem! If you ever want to tell me about " + topic + ", I'm all ears.",
            "Okay! Feel free to teach me about " + topic + " yourself anytime.",
            "Sure, no search then. You can always teach me directly!"
        );
    }

    /**
     * Build learned from search response
     */
    public String buildLearnedFromWebResponse(String topic, String summary) {
        emotionSystem.onLearnedSomethingNew();
        return pick(
            "Found it! " + summary + "\n\nI've saved this so I'll remember " + topic + " next time!",
            "Here's what I learned about " + topic + ": " + summary + "\n\nThanks for letting me search!",
            "Got it! " + summary + "\n\nI'll remember this about " + topic + "!"
        );
    }

    /**
     * Build greeting based on user memory
     */
    public String buildGreeting(boolean isReturning, String userName,
                                 String favTopic) {
        String base = emotionSystem.getGreeting(isReturning);

        if (isReturning && userName != null) {
            base = "Hey " + userName + "! " + base.substring(base.indexOf(" ") + 1);
        }

        if (isReturning && favTopic != null) {
            base += "\n\nLast time we talked a lot about " + favTopic +
                    ". Want to continue or talk about something new?";
        } else {
            base += "\n\nI start with zero knowledge. Teach me things, " +
                    "ask me to search the web, or load a dataset!";
        }

        return base;
    }

    /**
     * Build name-learning response
     */
    public String buildNameResponse(String name) {
        return pick(
            "Nice to meet you, " + name + "! I'll remember that.",
            "Great, I'll call you " + name + " from now on!",
            "Got it, " + name + "! Good to know."
        );
    }

    /**
     * Build proactive fact sharing
     */
    public String buildProactiveFact(List<Belief> beliefs, String userName) {
        if (beliefs.isEmpty()) return null;

        // Pick a random high-confidence belief
        Belief b = beliefs.get(rng.nextInt(Math.min(5, beliefs.size())));
        String factKey = b.toStatement();

        if (userMemory.wasFactShared(factKey)) return null;
        userMemory.markFactShared(factKey);

        String name = userName != null ? userName : "hey";
        return pick(
            "Oh " + name + ", random thought — did you know: " + b.toStatement() + "? You taught me that!",
            "Hey, I was just thinking... " + b.toStatement() + ". Isn't that interesting?",
            "Random fact I remember: " + b.toStatement() + ". Cool right?"
        );
    }

    /**
     * Build thinking message shown while processing
     */
    public String buildThinkingMessage() {
        return emotionSystem.getThinkingPhrase();
    }

    /**
     * Build "I learned from you" response
     */
    public String buildLearnedResponse(List<Belief> beliefs, String topic) {
        emotionSystem.onUserTaughtSomething();
        String excited = emotionSystem.getExcitedLearningPhrase(topic);

        if (beliefs.size() == 1) {
            return "Got it! " + beliefs.get(0).toReadable() + ". " + excited;
        } else {
            StringBuilder sb = new StringBuilder("Understood! I learned:\n");
            for (Belief b : beliefs) {
                sb.append("• ").append(b.toReadable()).append("\n");
            }
            sb.append("\n").append(excited);
            return sb.toString().trim();
        }
    }

    // ─── REPETITION GUARD ─────────────────────────────────────────────────────

    private boolean isRepetitive(String response) {
        String key = response.substring(0, Math.min(20, response.length()));
        return recentResponses.contains(key);
    }

    private void trackResponse(String response) {
        String key = response.substring(0, Math.min(20, response.length()));
        recentResponses.add(key);
        if (recentResponses.size() > MAX_RECENT) {
            recentResponses.remove(0);
        }
    }

    // ─── MISC HELPERS ─────────────────────────────────────────────────────────

    private String pick(String... options) {
        return options[rng.nextInt(options.length)];
    }

    public String buildUncertainResponse(String topic) {
        return emotionSystem.getUncertaintyPhrase() +
               "I know a little about " + topic +
               " but I'm not totally sure. Want me to search for more? (yes/no)";
    }
}
