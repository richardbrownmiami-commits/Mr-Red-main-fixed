package com.aibot;

public class ChatMessage {
    public String text;
    public boolean isUser;
    public long timestamp;

    public ChatMessage(String text, boolean isUser) {
        this.text      = text;
        this.isUser    = isUser;
        this.timestamp = System.currentTimeMillis();
    }
}
