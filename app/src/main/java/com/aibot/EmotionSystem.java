package com.aibot;

import java.util.Random;

/**
 * EmotionSystem - gives the bot a dynamic mood
 * Mood affects how it phrases responses
 */
public class EmotionSystem {

    public enum Mood {
        HAPPY, CURIOUS, CONFUSED, EXCITED, CALM, FRUSTRATED, PROUD, EMPATHETIC
    }

    private Mood currentMood = Mood.CALM;
    private float energy     = 0.7f; // 0.0 to 1.0
    private int   interactions = 0;
    private Random rng = new Random();

    // ─── MOOD TRIGGERS ────────────────────────────────────────────────────────

    public void onLearnedSomethingNew() {
        currentMood = rng.nextFloat() > 0.5f ? Mood.CURIOUS : Mood.EXCITED;
        energy = Math.min(1.0f, energy + 0.1f);
    }

    public void onAnsweredSuccessfully() {
        currentMood = Mood.HAPPY;
        energy = Math.min(1.0f, energy + 0.05f);
    }

    public void onDidntKnowAnswer() {
        currentMood = rng.nextFloat() > 0.5f ? Mood.CONFUSED : Mood.CALM;
        energy = Math.max(0.2f, energy - 0.05f);
    }

    public void onUserTaughtSomething() {
        currentMood = Mood.EXCITED;
        energy = Math.min(1.0f, energy + 0.15f);
    }

    public void onRepeatedQuestion() {
        currentMood = rng.nextFloat() > 0.7f ? Mood.FRUSTRATED : Mood.CALM;
    }

    public void onSearching() {
        currentMood = Mood.CURIOUS;
    }

    public void onInteraction() {
        interactions++;
        // Natural energy decay over time
        if (interactions % 10 == 0) {
            energy = Math.max(0.3f, energy - 0.02f);
        }
    }

    // ─── MOOD PHRASES ─────────────────────────────────────────────────────────

    public String getThinkingPhrase() {
        switch (currentMood) {
            case EXCITED:   return pick("Ooh let me think...", "Oh interesting!", "On it!");
            case CURIOUS:   return pick("Hmm let me think about that...", "Interesting question...", "Let me figure this out...");
            case CONFUSED:  return pick("Hmm that's a tough one...", "Let me think...", "Hmm...");
            case HAPPY:     return pick("Sure!", "Of course!", "Happy to help!");
            case FRUSTRATED:return pick("Let me try again...", "Hmm...", "Okay...");
            case PROUD:     return pick("Great question!", "Oh I know this!", "Yes!");
            default:        return pick("Let me think...", "One moment...", "Thinking...");
        }
    }

    public String getUncertaintyPhrase() {
        switch (currentMood) {
            case CURIOUS:   return pick("I'm not 100% sure but I think ", "Hmm, I believe ", "If I had to guess, ");
            case CONFUSED:  return pick("I'm honestly not sure, but maybe ", "This one is tricky... I think ", "Hmm, ");
            default:        return pick("I could be wrong but ", "I think ", "I believe ", "Not totally sure, but ");
        }
    }

    // Java doesn't have HUMBLE in enum, fix:
    private String getHumblePhrase() {
        return pick("I could be wrong but ", "Don't quote me on this, but ");
    }

    public String getExcitedLearningPhrase(String topic) {
        switch (currentMood) {
            case EXCITED:   return pick("Ooh I never knew that about " + topic + "! Thanks!",
                                        "That's so interesting! I'll remember that about " + topic + "!");
            case CURIOUS:   return pick("Hmm, so " + topic + "... tell me more!",
                                        "Interesting! I'm filing that away about " + topic + ".");
            case HAPPY:     return pick("Got it! Learning about " + topic + " is fun.",
                                        "Nice, I'll remember that!");
            default:        return pick("Understood, I'll remember that about " + topic + ".",
                                        "Got it, filing that away!");
        }
    }

    public String getSearchPromptPhrase(String topic) {
        switch (currentMood) {
            case CURIOUS:   return "I don't know about " + topic + " yet but I'm curious! Should I search the web? (yes/no)";
            case EXCITED:   return "Ooh " + topic + "! I don't know this yet — want me to look it up? (yes/no)";
            case CONFUSED:  return "Hmm, I'm not sure about " + topic + ". Should I search for it? (yes/no)";
            case HAPPY:     return "I don't know about " + topic + " yet! Want me to search and learn? (yes/no)";
            default:        return "I don't know about " + topic + " yet. Should I search the web for it? (yes/no)";
        }
    }

    public String getGreeting(boolean isReturning) {
        if (isReturning) {
            return pick(
                "Hey, welcome back!",
                "Oh you're back! Good to see you.",
                "Hey! Missed talking to you.",
                "Welcome back! I remembered everything from last time."
            );
        } else {
            return pick(
                "Hey there! Nice to meet you.",
                "Hi! I'm excited to learn from you.",
                "Hello! I start knowing nothing — teach me things!",
                "Hey! I'm your AI. I'm a blank slate, so be my teacher!"
            );
        }
    }

    public String getFrustrationPhrase() {
        return pick(
            "Hmm you've asked me this before and I still don't know... maybe try !search?",
            "I keep drawing a blank on this one. Want me to search for it?",
            "I wish I knew this! Should I look it up?"
        );
    }

    public String getProudPhrase(String topic) {
        return pick(
            "Oh! I actually know this one — you taught me!",
            "Wait, I remember this from our conversation!",
            "I know this! You told me about " + topic + " before."
        );
    }

    // ─── TONE MATCHING ────────────────────────────────────────────────────────

    public boolean isCasual(String userInput) {
        // Detect casual tone: short, no punctuation, slang
        return userInput.length() < 30 ||
               userInput.contains("lol") ||
               userInput.contains("haha") ||
               userInput.contains("omg") ||
               !userInput.contains("?") && !userInput.contains(".");
    }

    public String adjustTone(String response, boolean casual) {
        if (!casual) return response;
        // Make response more casual
        return response
            .replace("I do not", "I don't")
            .replace("I am ", "I'm ")
            .replace("It is ", "It's ")
            .replace("That is ", "That's ")
            .replace("I will ", "I'll ")
            .replace("cannot", "can't")
            .replace("However,", "But")
            .replace("Additionally,", "Also,")
            .replace("Furthermore,", "Plus,");
    }

    // ─── FOLLOW UP QUESTIONS ──────────────────────────────────────────────────

    public String getFollowUpQuestion(String topic) {
        if (rng.nextFloat() > 0.5f) return null; // only ask sometimes

        switch (currentMood) {
            case CURIOUS:
                return pick(
                    "That's interesting! Can you tell me more about " + topic + "?",
                    "What else do you know about " + topic + "?",
                    "How did you learn about " + topic + "?"
                );
            case HAPPY:
                return pick(
                    "Cool! Is there anything else about " + topic + " I should know?",
                    "Nice! What else can you teach me?"
                );
            case EXCITED:
                return pick(
                    "Wow! Tell me more!",
                    "That's fascinating — what else?"
                );
            default:
                return null;
        }
    }

    public String getClarificationQuestion(String topic) {
        return pick(
            "When you say \"" + topic + "\", could you be more specific?",
            "Just to make sure I understand — what exactly do you mean by " + topic + "?",
            "Are you asking about " + topic + " in general, or something specific?"
        );
    }

    // ─── PROACTIVE SHARING ────────────────────────────────────────────────────

    public boolean shouldShareRandomFact() {
        // Share a fact every ~8 interactions
        return interactions > 0 && interactions % 8 == 0 && rng.nextFloat() > 0.6f;
    }

    // ─── STATE ────────────────────────────────────────────────────────────────

    public Mood getMood()         { return currentMood; }
    public float getEnergy()      { return energy; }
    public int getInteractions()  { return interactions; }

    public String getMoodEmoji() {
        switch (currentMood) {
            case HAPPY:      return "😊";
            case CURIOUS:    return "🤔";
            case EXCITED:    return "😄";
            case CONFUSED:   return "😅";
            case FRUSTRATED: return "😤";
            case PROUD:      return "😎";
            case EMPATHETIC: return "🤗";
            default:         return "🙂";
        }
    }

    // ─── HELPER ───────────────────────────────────────────────────────────────

    private String pick(String... options) {
        return options[rng.nextInt(options.length)];
    }
}
