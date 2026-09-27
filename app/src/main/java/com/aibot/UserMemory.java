package com.aibot;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.*;

/**
 * UserMemory - remembers things about the user across sessions
 * Stored in SharedPreferences (always works on Android 11)
 */
public class UserMemory {

    private static final String PREFS_NAME = "aibot_user_memory";
    private SharedPreferences prefs;
    private SharedPreferences.Editor editor;

    // Keys
    private static final String KEY_NAME           = "user_name";
    private static final String KEY_TOPICS         = "fav_topics";
    private static final String KEY_SESSION_COUNT  = "session_count";
    private static final String KEY_TOTAL_MESSAGES = "total_messages";
    private static final String KEY_LAST_TOPIC     = "last_topic";
    private static final String KEY_LAST_SEEN      = "last_seen";
    private static final String KEY_FACTS_SHARED   = "facts_shared";
    private static final String KEY_TONE           = "preferred_tone"; // casual/formal

    // In-memory topic frequency map
    private Map<String, Integer> topicFrequency = new HashMap<>();

    public UserMemory(Context context) {
        prefs  = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        editor = prefs.edit();
        loadTopicFrequency();
        incrementSessionCount();
    }

    // ─── USER NAME ────────────────────────────────────────────────────────────

    public void setName(String name) {
        editor.putString(KEY_NAME, name).apply();
    }

    public String getName() {
        return prefs.getString(KEY_NAME, null);
    }

    public boolean knowsName() {
        return getName() != null;
    }

    public String getNameOrFallback() {
        String name = getName();
        return name != null ? name : "you";
    }

    // ─── SESSION TRACKING ─────────────────────────────────────────────────────

    private void incrementSessionCount() {
        int count = prefs.getInt(KEY_SESSION_COUNT, 0);
        editor.putInt(KEY_SESSION_COUNT, count + 1);
        editor.putLong(KEY_LAST_SEEN, System.currentTimeMillis());
        editor.apply();
    }

    public int getSessionCount() {
        return prefs.getInt(KEY_SESSION_COUNT, 0);
    }

    public boolean isReturningUser() {
        return getSessionCount() > 1;
    }

    public void incrementMessages() {
        int count = prefs.getInt(KEY_TOTAL_MESSAGES, 0);
        editor.putInt(KEY_TOTAL_MESSAGES, count + 1).apply();
    }

    public int getTotalMessages() {
        return prefs.getInt(KEY_TOTAL_MESSAGES, 0);
    }

    /**
     * Returns a compact human-readable summary for the Stats dialog.
     */
    public String getStats() {
        String name = getName();
        String favorite = getFavoriteTopic();
        String last = getLastTopic();
        return "Memory: " + (name != null ? "name=" + name : "name=unknown")
            + ", sessions=" + getSessionCount()
            + ", messages=" + getTotalMessages()
            + ", topics=" + topicFrequency.size()
            + ", favorite=" + (favorite != null ? favorite : "none")
            + ", last=" + (last != null ? last : "none");
    }

    // ─── TOPIC TRACKING ───────────────────────────────────────────────────────

    public void recordTopic(String topic) {
        if (topic == null || topic.isEmpty()) return;
        topic = topic.toLowerCase().trim();
        topicFrequency.put(topic, topicFrequency.getOrDefault(topic, 0) + 1);
        editor.putString(KEY_LAST_TOPIC, topic);
        saveTopicFrequency();
        editor.apply();
    }

    public String getLastTopic() {
        return prefs.getString(KEY_LAST_TOPIC, null);
    }

    public String getFavoriteTopic() {
        if (topicFrequency.isEmpty()) return null;
        return Collections.max(topicFrequency.entrySet(),
            Map.Entry.comparingByValue()).getKey();
    }

    public List<String> getTopTopics(int n) {
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(topicFrequency.entrySet());
        entries.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        List<String> result = new ArrayList<>();
        for (int i = 0; i < Math.min(n, entries.size()); i++) {
            result.add(entries.get(i).getKey());
        }
        return result;
    }

    private void saveTopicFrequency() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : topicFrequency.entrySet()) {
            sb.append(e.getKey()).append(":").append(e.getValue()).append(",");
        }
        editor.putString(KEY_TOPICS, sb.toString());
    }

    private void loadTopicFrequency() {
        String saved = prefs.getString(KEY_TOPICS, "");
        if (saved.isEmpty()) return;
        for (String entry : saved.split(",")) {
            String[] parts = entry.split(":");
            if (parts.length == 2) {
                try {
                    topicFrequency.put(parts[0], Integer.parseInt(parts[1]));
                } catch (NumberFormatException ignored) {}
            }
        }
    }

    // ─── TONE PREFERENCE ──────────────────────────────────────────────────────

    public void setToneCasual()  { editor.putString(KEY_TONE, "casual").apply(); }
    public void setToneFormal()  { editor.putString(KEY_TONE, "formal").apply(); }

    public boolean prefersCasual() {
        return "casual".equals(prefs.getString(KEY_TONE, "casual"));
    }

    // ─── FACTS SHARED ─────────────────────────────────────────────────────────

    public void markFactShared(String fact) {
        Set<String> shared = new HashSet<>(prefs.getStringSet(KEY_FACTS_SHARED,
                                           new HashSet<>()));
        shared.add(fact.substring(0, Math.min(30, fact.length())));
        editor.putStringSet(KEY_FACTS_SHARED, shared).apply();
    }

    public boolean wasFactShared(String fact) {
        Set<String> shared = prefs.getStringSet(KEY_FACTS_SHARED, new HashSet<>());
        return shared.contains(fact.substring(0, Math.min(30, fact.length())));
    }

    // ─── PERSONALITY INTRO ────────────────────────────────────────────────────

    public String buildPersonalContext() {
        StringBuilder sb = new StringBuilder();
        String name = getName();
        if (name != null) sb.append("Talking to ").append(name).append(". ");

        List<String> topics = getTopTopics(3);
        if (!topics.isEmpty()) {
            sb.append("They like talking about: ")
              .append(String.join(", ", topics)).append(". ");
        }

        String lastTopic = getLastTopic();
        if (lastTopic != null) {
            sb.append("Last topic: ").append(lastTopic).append(".");
        }

        return sb.toString();
    }

    // ─── NAME DETECTION ───────────────────────────────────────────────────────

    /**
     * Try to detect if user is telling us their name
     * e.g. "my name is Ahmed" / "I'm Ahmed" / "call me Ahmed"
     */
    public String detectName(String input) {
        input = input.toLowerCase().trim();
        String[] patterns = {
            "my name is ", "i'm ", "i am ", "call me ", "name's "
        };
        for (String p : patterns) {
            int idx = input.indexOf(p);
            if (idx >= 0) {
                String after = input.substring(idx + p.length()).trim();
                String[] words = after.split(" ");
                if (words.length > 0 && words[0].length() > 1) {
                    // Capitalize first letter
                    return Character.toUpperCase(words[0].charAt(0)) +
                           words[0].substring(1);
                }
            }
        }
        return null;
    }
}
