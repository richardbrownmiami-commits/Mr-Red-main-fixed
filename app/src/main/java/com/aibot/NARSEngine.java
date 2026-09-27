package com.aibot;

import java.io.*;
import java.util.*;

/**
 * NARS - Non-Axiomatic Reasoning System
 * Gives the bot ability to REASON, not just pattern match
 *
 * Features:
 * - Stores beliefs with confidence values
 * - Deduction, Induction, Abduction
 * - Question answering from beliefs
 * - Learns new beliefs from conversation
 * - Saves/loads belief memory
 */
public class NARSEngine {

    // All beliefs stored by key
    private Map<String, Belief> beliefMemory = new LinkedHashMap<>();

    // Derived beliefs queue (to be processed)
    private Queue<Belief> derivedQueue = new LinkedList<>();

    // Max beliefs to store (memory limit for ARMv7a)
    private static final int MAX_BELIEFS = 5000;
    private static final int MAX_DERIVED_PER_CYCLE = 10;

    // Confidence threshold to accept derived beliefs
    private static final float MIN_CONFIDENCE = 0.1f;

    public NARSEngine() {}

    // ─── INPUT ────────────────────────────────────────────────────────────────

    /**
     * Add a new belief from user input
     * e.g. "cat is animal" → subject=cat, relation=is, object=animal
     */
    public void addBelief(String subject, String relation, String object, float confidence) {
        Belief newBelief = new Belief(subject, relation, object,
                                       1.0f, confidence, Belief.SOURCE_USER);
        String key = newBelief.key();

        if (beliefMemory.containsKey(key)) {
            // Revise existing belief
            Belief existing = beliefMemory.get(key);
            beliefMemory.put(key, Belief.revise(existing, newBelief));
        } else {
            beliefMemory.put(key, newBelief);
        }

        // Run inference cycle
        inferenceStep(newBelief);
        trimMemory();
    }

    /**
     * Parse natural language and extract beliefs
     * Handles: "X is Y", "X has Y", "X can Y", "X needs Y"
     */
    public List<Belief> parseAndLearn(String text) {
        List<Belief> learned = new ArrayList<>();
        text = text.toLowerCase().trim();

        String[] sentences = text.split("[.!?]");
        for (String sentence : sentences) {
            sentence = sentence.trim();
            if (sentence.isEmpty()) continue;

            Belief b = extractBelief(sentence);
            if (b != null) {
                addBelief(b.subject, b.relation, b.object, 0.9f);
                learned.add(b);
            }
        }
        return learned;
    }

    /**
     * Extract a belief from a sentence
     */
    private Belief extractBelief(String sentence) {
        // Patterns: subject RELATION object
        String[] relations = {"is a", "is an", "is", "has", "can", "needs",
                              "have", "are", "were", "will", "makes", "uses",
                              "contains", "runs on", "belongs to", "part of"};

        for (String rel : relations) {
            int idx = sentence.indexOf(" " + rel + " ");
            if (idx > 0) {
                String subject = sentence.substring(0, idx).trim();
                String object  = sentence.substring(idx + rel.length() + 2).trim();
                // Clean up
                subject = subject.replaceAll("^(a |an |the )", "");
                object  = object.replaceAll("^(a |an |the )", "");
                if (!subject.isEmpty() && !object.isEmpty() &&
                    subject.length() < 50 && object.length() < 100) {
                    return new Belief(subject, rel.trim(), object, 1.0f, 0.9f, Belief.SOURCE_USER);
                }
            }
        }
        return null;
    }

    // ─── REASONING ────────────────────────────────────────────────────────────

    /**
     * Run one inference step given a new belief
     * Tries to derive new beliefs using NAL rules
     */
    private void inferenceStep(Belief newBelief) {
        List<Belief> allBeliefs = new ArrayList<>(beliefMemory.values());
        int count = 0;

        for (Belief existing : allBeliefs) {
            if (count >= MAX_DERIVED_PER_CYCLE) break;

            // Deduction: new is B, existing B is C → new is C
            Belief deduced = Belief.deduce(newBelief, existing);
            if (deduced != null && deduced.confidence > MIN_CONFIDENCE) {
                storeDerived(deduced);
                count++;
            }

            // Deduction reversed: existing A is new, new is C → existing A is C
            Belief deduced2 = Belief.deduce(existing, newBelief);
            if (deduced2 != null && deduced2.confidence > MIN_CONFIDENCE) {
                storeDerived(deduced2);
                count++;
            }

            // Induction
            Belief induced = Belief.induce(newBelief, existing);
            if (induced != null && induced.confidence > MIN_CONFIDENCE) {
                storeDerived(induced);
                count++;
            }

            // Abduction
            Belief abduced = Belief.abduct(newBelief, existing);
            if (abduced != null && abduced.confidence > MIN_CONFIDENCE) {
                storeDerived(abduced);
                count++;
            }
        }
    }

    private void storeDerived(Belief derived) {
        String key = derived.key();
        if (beliefMemory.containsKey(key)) {
            Belief existing = beliefMemory.get(key);
            // Only update if derived is more confident
            if (derived.confidence > existing.confidence) {
                beliefMemory.put(key, Belief.revise(existing, derived));
            }
        } else {
            beliefMemory.put(key, derived);
        }
    }

    // ─── QUERY ────────────────────────────────────────────────────────────────

    /**
     * Answer a question from beliefs
     * e.g. "what is cat?" → finds all beliefs about cat
     */
    public String answerQuestion(String question) {
        question = question.toLowerCase().trim()
                           .replaceAll("[?!.]", "");

        // What is X?
        if (question.startsWith("what is ") || question.startsWith("what are ")) {
            String subject = question.replace("what is ", "")
                                     .replace("what are ", "").trim();
            return answerWhatIs(subject);
        }

        // Does X have Y?
        if (question.startsWith("does ") || question.startsWith("do ")) {
            return answerDoesHave(question);
        }

        // Can X do Y?
        if (question.startsWith("can ")) {
            String rest = question.substring(4).trim();
            String[] parts = rest.split(" ", 2);
            if (parts.length == 2) {
                return answerCan(parts[0], parts[1]);
            }
        }

        // How does X work?
        if (question.startsWith("how does ") || question.startsWith("how do ")) {
            String subject = question.replace("how does ", "")
                                     .replace("how do ", "")
                                     .replace(" work", "").trim();
            return answerHow(subject);
        }

        // General: search all beliefs for subject
        String[] words = question.split(" ");
        for (String word : words) {
            if (word.length() > 3) {
                String answer = answerWhatIs(word);
                if (!answer.startsWith("I don't")) return answer;
            }
        }

        return null; // No answer found
    }

    private String answerWhatIs(String subject) {
        subject = subject.replaceAll("^(a |an |the )", "").trim();
        List<Belief> found = findBySubject(subject);
        if (found.isEmpty()) return "I don't know what " + subject + " is yet.";

        StringBuilder sb = new StringBuilder();
        sb.append(capitalize(subject));

        // Sort by confidence
        found.sort((a, b) -> Float.compare(b.confidence, a.confidence));

        for (int i = 0; i < Math.min(3, found.size()); i++) {
            Belief b = found.get(i);
            if (i == 0) sb.append(" ").append(b.relation).append(" ").append(b.object);
            else sb.append(", and ").append(b.relation).append(" ").append(b.object);
        }
        sb.append(".");

        // Add derived beliefs
        List<Belief> derived = findDerivedBySubject(subject);
        if (!derived.isEmpty()) {
            sb.append(" Also, I think ").append(subject).append(" ")
              .append(derived.get(0).relation).append(" ")
              .append(derived.get(0).object)
              .append(" (").append(String.format("%.0f", derived.get(0).confidence * 100))
              .append("% sure).");
        }

        return sb.toString();
    }

    private String answerDoesHave(String question) {
        // "does cat have fur?"
        question = question.replaceAll("^(does |do )", "");
        String[] parts = question.split(" have | has ", 2);
        if (parts.length == 2) {
            String subject = parts[0].trim();
            String object  = parts[1].trim();
            List<Belief> found = findBySubjectAndRelation(subject, "has");
            for (Belief b : found) {
                if (b.object.contains(object) || object.contains(b.object)) {
                    return "Yes, " + subject + " has " + b.object +
                           " (" + String.format("%.0f", b.confidence * 100) + "% sure).";
                }
            }
            return "I'm not sure if " + subject + " has " + object + ".";
        }
        return null;
    }

    private String answerCan(String subject, String action) {
        List<Belief> found = findBySubjectAndRelation(subject, "can");
        for (Belief b : found) {
            if (b.object.contains(action) || action.contains(b.object)) {
                return "Yes, " + subject + " can " + b.object + ".";
            }
        }
        return "I don't know if " + subject + " can " + action + ".";
    }

    private String answerHow(String subject) {
        List<Belief> found = findBySubject(subject);
        if (found.isEmpty()) return null;

        StringBuilder sb = new StringBuilder();
        sb.append("Based on what I know: ").append(subject);
        for (Belief b : found) {
            sb.append(" ").append(b.relation).append(" ").append(b.object).append(",");
        }
        if (sb.charAt(sb.length()-1) == ',')
            sb.setCharAt(sb.length()-1, '.');
        return sb.toString();
    }

    // ─── SEARCH HELPERS ───────────────────────────────────────────────────────

    private List<Belief> findBySubject(String subject) {
        List<Belief> result = new ArrayList<>();
        for (Belief b : beliefMemory.values()) {
            if (b.subject.equals(subject) && b.source != Belief.SOURCE_DERIVED) {
                result.add(b);
            }
        }
        return result;
    }

    private List<Belief> findDerivedBySubject(String subject) {
        List<Belief> result = new ArrayList<>();
        for (Belief b : beliefMemory.values()) {
            if (b.subject.equals(subject) && b.source == Belief.SOURCE_DERIVED
                && b.confidence > 0.3f) {
                result.add(b);
            }
        }
        result.sort((a, b) -> Float.compare(b.confidence, a.confidence));
        return result;
    }

    private List<Belief> findBySubjectAndRelation(String subject, String relation) {
        List<Belief> result = new ArrayList<>();
        for (Belief b : beliefMemory.values()) {
            if (b.subject.equals(subject) && b.relation.contains(relation)) {
                result.add(b);
            }
        }
        return result;
    }

    /**
     * Find beliefs related to a topic (for context building)
     */
    public List<Belief> getRelatedBeliefs(String topic) {
        List<Belief> result = new ArrayList<>();
        topic = topic.toLowerCase().trim();
        for (Belief b : beliefMemory.values()) {
            if (b.subject.contains(topic) || b.object.contains(topic)) {
                result.add(b);
            }
        }
        result.sort((a, b) -> Float.compare(b.confidence, a.confidence));
        return result.subList(0, Math.min(10, result.size()));
    }

    /**
     * Build context string from beliefs for neural network input
     */
    public String buildContextFromBeliefs(String topic) {
        List<Belief> related = getRelatedBeliefs(topic);
        if (related.isEmpty()) return "";

        StringBuilder sb = new StringBuilder("Known facts: ");
        for (Belief b : related) {
            sb.append(b.toStatement()).append(". ");
        }
        return sb.toString().trim();
    }

    // ─── MEMORY MANAGEMENT ────────────────────────────────────────────────────

    private void trimMemory() {
        if (beliefMemory.size() <= MAX_BELIEFS) return;

        // Remove lowest priority derived beliefs first
        List<Map.Entry<String, Belief>> entries = new ArrayList<>(beliefMemory.entrySet());
        entries.sort((a, b) -> {
            // Keep user beliefs, remove derived ones first
            if (a.getValue().source != b.getValue().source)
                return Integer.compare(b.getValue().source, a.getValue().source);
            return Float.compare(a.getValue().expectation(), b.getValue().expectation());
        });

        int toRemove = beliefMemory.size() - MAX_BELIEFS;
        for (int i = 0; i < toRemove; i++) {
            beliefMemory.remove(entries.get(i).getKey());
        }
    }

    public int getBeliefCount() { return beliefMemory.size(); }

    public String getStats() {
        int user = 0, derived = 0, web = 0, dataset = 0;
        for (Belief b : beliefMemory.values()) {
            switch (b.source) {
                case Belief.SOURCE_USER:    user++;    break;
                case Belief.SOURCE_DERIVED: derived++; break;
                case Belief.SOURCE_WEB:     web++;     break;
                case Belief.SOURCE_DATASET: dataset++; break;
            }
        }
        return "Beliefs: " + beliefMemory.size() +
               " (user=" + user + " derived=" + derived +
               " web=" + web + " dataset=" + dataset + ")";
    }

    // ─── PERSISTENCE ──────────────────────────────────────────────────────────

    public void saveBeliefs(File file) throws IOException {
        ObjectOutputStream oos = new ObjectOutputStream(
            new BufferedOutputStream(new FileOutputStream(file)));
        oos.writeObject(beliefMemory);
        oos.close();
    }

    @SuppressWarnings("unchecked")
    public void loadBeliefs(File file) throws IOException, ClassNotFoundException {
        ObjectInputStream ois = new ObjectInputStream(
            new BufferedInputStream(new FileInputStream(file)));
        beliefMemory = (Map<String, Belief>) ois.readObject();
        ois.close();
    }

    // ─── UTILS ────────────────────────────────────────────────────────────────

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
