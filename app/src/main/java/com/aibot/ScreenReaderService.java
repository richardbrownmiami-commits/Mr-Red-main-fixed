package com.aibot;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayList;
import java.util.List;

/**
 * ScreenReaderService - Accessibility Service
 * Reads all text visible on screen
 * Used for: "what's on screen?", game helper, context-aware responses
 *
 * Must be enabled in:
 * Settings → Accessibility → AIBot Screen Reader → Enable
 */
public class ScreenReaderService extends AccessibilityService {

    private static final String TAG = "ScreenReaderService";

    // Static reference so MainActivity can query it
    public static ScreenReaderService instance;

    // Last captured screen state
    private static String lastScreenText     = "";
    private static String lastPackageName    = "";
    private static String lastActivityName   = "";
    private static long   lastUpdateTime     = 0;

    // Callback interface
    public interface ScreenCallback {
        void onScreenRead(String text, String appName);
    }
    private static ScreenCallback screenCallback;

    public static void setScreenCallback(ScreenCallback cb) {
        screenCallback = cb;
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Log.d(TAG, "Screen reader service connected!");

        // Configure what events to listen to
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes =
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED |
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED   |
            AccessibilityEvent.TYPE_VIEW_SCROLLED          |
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED;
        info.feedbackType   = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.notificationTimeout = 300;
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS |
                     AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        setServiceInfo(info);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;

        // Track current app
        CharSequence pkg = event.getPackageName();
        if (pkg != null) lastPackageName = pkg.toString();

        // Capture screen text on significant changes
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {

            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                lastScreenText  = extractAllText(root);
                lastUpdateTime  = System.currentTimeMillis();
                root.recycle();

                if (screenCallback != null) {
                    screenCallback.onScreenRead(lastScreenText, lastPackageName);
                }
            }
        }
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "Accessibility interrupted");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
        Log.d(TAG, "Screen reader service destroyed");
    }

    // ─── TEXT EXTRACTION ──────────────────────────────────────────────────────

    /**
     * Recursively extract all text from accessibility node tree
     */
    private String extractAllText(AccessibilityNodeInfo node) {
        if (node == null) return "";
        StringBuilder sb = new StringBuilder();
        extractTextRecursive(node, sb, 0);
        return sb.toString().trim();
    }

    private void extractTextRecursive(AccessibilityNodeInfo node,
                                       StringBuilder sb, int depth) {
        if (node == null || depth > 20) return; // prevent infinite recursion

        // Get text from this node
        CharSequence text = node.getText();
        CharSequence desc = node.getContentDescription();

        if (text != null && text.length() > 0) {
            sb.append(text.toString().trim()).append(" ");
        }
        if (desc != null && desc.length() > 0 && !desc.equals(text)) {
            sb.append(desc.toString().trim()).append(" ");
        }

        // Recurse into children
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                extractTextRecursive(child, sb, depth + 1);
                child.recycle();
            }
        }
    }

    // ─── CLICK ON SCREEN ──────────────────────────────────────────────────────

    /**
     * Find and click a node with specific text
     */
    public boolean clickOnText(String targetText) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;

        List<AccessibilityNodeInfo> nodes =
            root.findAccessibilityNodeInfosByText(targetText);
        root.recycle();

        for (AccessibilityNodeInfo node : nodes) {
            if (node.isClickable()) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                node.recycle();
                return true;
            }
            // Try parent
            AccessibilityNodeInfo parent = node.getParent();
            if (parent != null && parent.isClickable()) {
                parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                parent.recycle();
                node.recycle();
                return true;
            }
            node.recycle();
        }
        return false;
    }

    // ─── STATIC ACCESSORS ─────────────────────────────────────────────────────

    public static String getLastScreenText()  { return lastScreenText; }
    public static String getCurrentApp()      { return lastPackageName; }
    public static long   getLastUpdateTime()  { return lastUpdateTime; }

    public static boolean isRunning() { return instance != null; }

    /**
     * Get a readable summary of what's currently on screen
     */
    public static String getScreenSummary() {
        if (!isRunning()) {
            return "Screen reader is not enabled. Go to Settings → Accessibility → AIBot → Enable.";
        }
        if (lastScreenText.isEmpty()) {
            return "The screen appears to be empty or I can't read it.";
        }
        // Clean and truncate
        String text = lastScreenText
            .replaceAll("\\s+", " ")
            .trim();
        if (text.length() > 800) text = text.substring(0, 800) + "...";
        return text;
    }

    /**
     * Get the friendly app name from package name
     */
    public static String getFriendlyAppName(String pkg) {
        if (pkg == null || pkg.isEmpty()) return "unknown app";
        if (pkg.contains("youtube"))   return "YouTube";
        if (pkg.contains("whatsapp"))  return "WhatsApp";
        if (pkg.contains("chrome"))    return "Chrome";
        if (pkg.contains("maps"))      return "Maps";
        if (pkg.contains("gmail"))     return "Gmail";
        if (pkg.contains("facebook"))  return "Facebook";
        if (pkg.contains("instagram")) return "Instagram";
        if (pkg.contains("twitter"))   return "Twitter";
        if (pkg.contains("telegram"))  return "Telegram";
        if (pkg.contains("spotify"))   return "Spotify";
        if (pkg.contains("netflix"))   return "Netflix";
        if (pkg.contains("settings"))  return "Settings";
        if (pkg.contains("calculator"))return "Calculator";
        if (pkg.contains("camera"))    return "Camera";
        if (pkg.contains("gallery"))   return "Gallery";
        if (pkg.contains("aibot"))     return "AIBot (me!)";
        // Extract last part of package name
        String[] parts = pkg.split("\\.");
        return parts.length > 0 ? parts[parts.length - 1] : pkg;
    }
}
