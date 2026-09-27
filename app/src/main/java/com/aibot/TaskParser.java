package com.aibot;

import java.util.*;

/**
 * TaskParser - understands natural language device commands
 * Maps user input to device actions
 */
public class TaskParser {

    public enum TaskType {
        FLASHLIGHT_ON, FLASHLIGHT_OFF, FLASHLIGHT_TOGGLE,
        WIFI_ON, WIFI_OFF, WIFI_STATUS,
        BLUETOOTH_ON, BLUETOOTH_OFF, BLUETOOTH_STATUS,
        DATA_ON, DATA_OFF,
        OPEN_APP,
        SCREEN_READ, SCREEN_APP, SCREEN_WHAT,
        BATTERY_STATUS,
        DEVICE_STATUS,
        BRIGHTNESS_SETTINGS,
        VOLUME_SETTINGS,
        UNKNOWN
    }

    public static class ParsedTask {
        public TaskType type;
        public String   argument; // e.g. app name
        public String   original;

        public ParsedTask(TaskType type, String argument, String original) {
            this.type     = type;
            this.argument = argument;
            this.original = original;
        }

        public boolean isDeviceTask() {
            return type != TaskType.UNKNOWN;
        }
    }

    // ─── PARSE ────────────────────────────────────────────────────────────────

    public static ParsedTask parse(String input) {
        String lower = input.toLowerCase().trim()
            .replaceAll("[?!.,]", "");

        // ── Flashlight ──
        if (matches(lower, "turn on flashlight", "flashlight on", "enable flashlight",
                    "open flashlight", "switch on torch", "turn on torch", "torch on")) {
            return new ParsedTask(TaskType.FLASHLIGHT_ON, null, input);
        }
        if (matches(lower, "turn off flashlight", "flashlight off", "disable flashlight",
                    "close flashlight", "torch off", "switch off torch")) {
            return new ParsedTask(TaskType.FLASHLIGHT_OFF, null, input);
        }
        if (matches(lower, "toggle flashlight", "flashlight", "torch")) {
            return new ParsedTask(TaskType.FLASHLIGHT_TOGGLE, null, input);
        }

        // ── WiFi ──
        if (matches(lower, "turn on wifi", "enable wifi", "wifi on", "switch on wifi",
                    "connect wifi", "turn on wi-fi")) {
            return new ParsedTask(TaskType.WIFI_ON, null, input);
        }
        if (matches(lower, "turn off wifi", "disable wifi", "wifi off", "disconnect wifi",
                    "switch off wifi", "turn off wi-fi")) {
            return new ParsedTask(TaskType.WIFI_OFF, null, input);
        }
        if (matches(lower, "wifi status", "is wifi on", "check wifi")) {
            return new ParsedTask(TaskType.WIFI_STATUS, null, input);
        }

        // ── Bluetooth ──
        if (matches(lower, "turn on bluetooth", "enable bluetooth", "bluetooth on",
                    "switch on bluetooth", "bt on")) {
            return new ParsedTask(TaskType.BLUETOOTH_ON, null, input);
        }
        if (matches(lower, "turn off bluetooth", "disable bluetooth", "bluetooth off",
                    "switch off bluetooth", "bt off")) {
            return new ParsedTask(TaskType.BLUETOOTH_OFF, null, input);
        }
        if (matches(lower, "bluetooth status", "is bluetooth on", "check bluetooth")) {
            return new ParsedTask(TaskType.BLUETOOTH_STATUS, null, input);
        }

        // ── Mobile Data ──
        if (matches(lower, "turn on data", "enable data", "mobile data on",
                    "turn on mobile data", "enable mobile data")) {
            return new ParsedTask(TaskType.DATA_ON, null, input);
        }
        if (matches(lower, "turn off data", "disable data", "mobile data off",
                    "turn off mobile data", "disable mobile data", "no cellular")) {
            return new ParsedTask(TaskType.DATA_OFF, null, input);
        }

        // ── Open App ──
        if (lower.startsWith("open ") || lower.startsWith("launch ") ||
            lower.startsWith("start ") || lower.startsWith("run ")) {
            String appName = lower
                .replaceFirst("^(open|launch|start|run) ", "").trim();
            if (!appName.isEmpty()) {
                return new ParsedTask(TaskType.OPEN_APP, appName, input);
            }
        }

        // ── Screen Reading ──
        if (matches(lower, "what is on screen", "what's on screen", "read screen",
                    "what do you see", "what can you see", "read the screen",
                    "whats on my screen", "describe screen")) {
            return new ParsedTask(TaskType.SCREEN_READ, null, input);
        }
        if (matches(lower, "what app is open", "which app", "what app am i using",
                    "current app", "what app is this")) {
            return new ParsedTask(TaskType.SCREEN_APP, null, input);
        }
        if (lower.contains("on screen") || lower.contains("what do i see") ||
            lower.contains("next move") || lower.contains("what should i do") ||
            lower.contains("help me with") && lower.contains("game")) {
            return new ParsedTask(TaskType.SCREEN_WHAT, null, input);
        }

        // ── Battery ──
        if (matches(lower, "battery", "battery level", "how much battery",
                    "battery status", "is charging", "battery percent")) {
            return new ParsedTask(TaskType.BATTERY_STATUS, null, input);
        }

        // ── Device status ──
        if (matches(lower, "device status", "phone status", "status",
                    "system status", "what is my device")) {
            return new ParsedTask(TaskType.DEVICE_STATUS, null, input);
        }

        // ── Brightness ──
        if (matches(lower, "brightness", "screen brightness", "adjust brightness",
                    "change brightness")) {
            return new ParsedTask(TaskType.BRIGHTNESS_SETTINGS, null, input);
        }

        // ── Volume ──
        if (matches(lower, "volume", "sound settings", "adjust volume",
                    "change volume", "mute")) {
            return new ParsedTask(TaskType.VOLUME_SETTINGS, null, input);
        }

        return new ParsedTask(TaskType.UNKNOWN, null, input);
    }

    // ─── HELPERS ──────────────────────────────────────────────────────────────

    private static boolean matches(String input, String... patterns) {
        for (String p : patterns) {
            if (input.equals(p) || input.contains(p)) return true;
        }
        return false;
    }

    public static boolean isDeviceCommand(String input) {
        return parse(input).isDeviceTask();
    }
}
