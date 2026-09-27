package com.aibot;

import java.io.Serializable;

/**
 * NARS Belief - stores a fact with confidence and priority
 * Based on Non-Axiomatic Logic (NAL)
 */
public class Belief implements Serializable {

    // The statement e.g. "cat is animal"
    public String subject;
    public String predicate;
    public String object;
    public String relation; // "is", "has", "can", "needs", etc.

    // Truth value (Non-Axiomatic Logic)
    public float frequency;   // how often true (0.0 to 1.0)
    public float confidence;  // how sure we are (0.0 to 1.0)

    // Priority for attention
    public float priority;

    // When this belief was created/updated
    public long timestamp;

    // How many times this belief was used in reasoning
    public int useCount;

    // Source of belief
    public static final int SOURCE_USER       = 0;
    public static final int SOURCE_DERIVED    = 1;
    public static final int SOURCE_WEB        = 2;
    public static final int SOURCE_DATASET    = 3;
    public int source;

    public Belief(String subject, String relation, String object,
                  float frequency, float confidence, int source) {
        this.subject    = subject.toLowerCase().trim();
        this.relation   = relation.toLowerCase().trim();
        this.object     = object.toLowerCase().trim();
        this.predicate  = relation + " " + object;
        this.frequency  = frequency;
        this.confidence = confidence;
        this.priority   = frequency * confidence;
        this.timestamp  = System.currentTimeMillis();
        this.useCount   = 0;
        this.source     = source;
    }

    /**
     * NAL Truth: expectation value
     * E = confidence * (frequency - 0.5) + 0.5
     */
    public float expectation() {
        return confidence * (frequency - 0.5f) + 0.5f;
    }

    /**
     * Revision: merge two beliefs about same statement
     * NAL revision rule
     */
    public static Belief revise(Belief b1, Belief b2) {
        float c1 = b1.confidence;
        float c2 = b2.confidence;
        float totalC = c1 + c2 - c1 * c2;
        float newFreq = (b1.frequency * c1 + b2.frequency * c2) / (c1 + c2);
        float newConf = totalC / (totalC + 1.0f / (1.0f - Math.max(c1, c2)));
        newConf = Math.min(newConf, 0.99f);

        Belief revised = new Belief(
            b1.subject, b1.relation, b1.object,
            newFreq, newConf, b1.source
        );
        revised.useCount = b1.useCount + b2.useCount;
        return revised;
    }

    /**
     * Deduction: if A->B and B->C then A->C
     * NAL deduction rule
     */
    public static Belief deduce(Belief premise1, Belief premise2) {
        // premise1: A is B
        // premise2: B is C
        // conclusion: A is C
        if (!premise1.object.equals(premise2.subject)) return null;

        float newFreq = premise1.frequency * premise2.frequency;
        float newConf = premise1.confidence * premise2.confidence * 0.9f; // slight discount

        return new Belief(
            premise1.subject,
            premise2.relation,
            premise2.object,
            newFreq,
            newConf,
            SOURCE_DERIVED
        );
    }

    /**
     * Induction: if A->B and A->C then B->C (with lower confidence)
     */
    public static Belief induce(Belief premise1, Belief premise2) {
        if (!premise1.subject.equals(premise2.subject)) return null;

        float newFreq = premise2.frequency;
        float newConf = premise1.confidence * premise2.confidence * 0.45f;

        return new Belief(
            premise1.object,
            premise2.relation,
            premise2.object,
            newFreq,
            newConf,
            SOURCE_DERIVED
        );
    }

    /**
     * Abduction: if B->C and A->C then A->B (hypothesize cause)
     */
    public static Belief abduct(Belief premise1, Belief premise2) {
        if (!premise1.object.equals(premise2.object)) return null;

        float newFreq = premise1.frequency;
        float newConf = premise1.confidence * premise2.confidence * 0.45f;

        return new Belief(
            premise2.subject,
            premise1.relation,
            premise1.subject,
            newFreq,
            newConf,
            SOURCE_DERIVED
        );
    }

    public String toStatement() {
        return subject + " " + relation + " " + object;
    }

    public String toReadable() {
        String conf = String.format("%.0f%%", confidence * 100);
        return subject + " " + relation + " " + object + " (" + conf + " sure)";
    }

    @Override
    public String toString() {
        return "Belief{" + toStatement() +
               " f=" + String.format("%.2f", frequency) +
               " c=" + String.format("%.2f", confidence) + "}";
    }

    // Unique key for this belief
    public String key() {
        return subject + "|" + relation + "|" + object;
    }
}
