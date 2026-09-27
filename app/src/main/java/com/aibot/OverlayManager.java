package com.aibot;

import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.*;
import android.view.WindowManager;
import android.widget.*;

/**
 * OverlayManager - floating bubble that stays on top of all apps
 * Tap → open quick chat
 * Long press → quick commands
 * Drag → move anywhere
 *
 * Requires: Settings → Apps → AIBot → Display over other apps → ON
 */
public class OverlayManager {

    private Context       context;
    private WindowManager windowManager;
    private View          bubbleView;
    private View          chatView;
    private boolean       isShowing = false;
    private boolean       chatOpen  = false;

    private OverlayCallback callback;

    public interface OverlayCallback {
        void onQuickMessage(String message);
        void onOverlayDismissed();
    }

    public OverlayManager(Context context, OverlayCallback callback) {
        this.context       = context.getApplicationContext();
        this.callback      = callback;
        this.windowManager = (WindowManager)
            context.getSystemService(Context.WINDOW_SERVICE);
    }

    // ─── PERMISSION CHECK ─────────────────────────────────────────────────────

    public static boolean hasOverlayPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(context);
        }
        return true;
    }

    public static void requestOverlayPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
            i.setData(android.net.Uri.parse("package:" + context.getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(i);
        }
    }

    // ─── SHOW BUBBLE ──────────────────────────────────────────────────────────

    public void showBubble(String botName, String moodEmoji) {
        if (isShowing) return;
        if (!hasOverlayPermission(context)) return;

        // Create bubble view
        bubbleView = createBubbleView(botName, moodEmoji);

        // Window params
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0;
        params.y = 200;

        windowManager.addView(bubbleView, params);
        isShowing = true;
        makeDraggable(bubbleView, params);
    }

    public void hideBubble() {
        if (!isShowing || bubbleView == null) return;
        try {
            windowManager.removeView(bubbleView);
        } catch (Exception ignored) {}
        isShowing = false;
    }

    public void updateMood(String moodEmoji) {
        if (bubbleView == null) return;
        TextView tv = bubbleView.findViewById(android.R.id.text1);
        if (tv != null) tv.setText(moodEmoji);
    }

    // ─── BUBBLE VIEW ──────────────────────────────────────────────────────────

    private View createBubbleView(String botName, String moodEmoji) {
        // Build a simple circular button with emoji
        FrameLayout frame = new FrameLayout(context);

        TextView bubble = new TextView(context);
        bubble.setId(android.R.id.text1);
        bubble.setText(moodEmoji);
        bubble.setTextSize(28);
        bubble.setGravity(Gravity.CENTER);
        bubble.setPadding(8, 8, 8, 8);
        bubble.setBackgroundResource(android.R.drawable.btn_default);

        frame.addView(bubble);

        // Tap → open mini chat
        bubble.setOnClickListener(v -> toggleMiniChat());

        // Long press → quick commands menu
        bubble.setOnLongClickListener(v -> {
            showQuickCommands();
            return true;
        });

        return frame;
    }

    // ─── DRAG SUPPORT ─────────────────────────────────────────────────────────

    private void makeDraggable(View view, WindowManager.LayoutParams params) {
        view.setOnTouchListener(new View.OnTouchListener() {
            float initX, initY, initTouchX, initTouchY;
            boolean moved = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initX      = params.x;
                        initY      = params.y;
                        initTouchX = event.getRawX();
                        initTouchY = event.getRawY();
                        moved      = false;
                        return false; // pass click events through

                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - initTouchX;
                        float dy = event.getRawY() - initTouchY;
                        if (Math.abs(dx) > 5 || Math.abs(dy) > 5) {
                            moved      = true;
                            params.x   = (int)(initX + dx);
                            params.y   = (int)(initY + dy);
                            try {
                                windowManager.updateViewLayout(view, params);
                            } catch (Exception ignored) {}
                        }
                        return moved;

                    case MotionEvent.ACTION_UP:
                        return moved;
                }
                return false;
            }
        });
    }

    // ─── MINI CHAT ────────────────────────────────────────────────────────────

    private void toggleMiniChat() {
        if (chatOpen) {
            closeMiniChat();
        } else {
            openMiniChat();
        }
    }

    private void openMiniChat() {
        if (chatOpen) return;
        chatOpen = true;

        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            : WindowManager.LayoutParams.TYPE_PHONE;

        // Build mini chat overlay
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(0xDD1a1a2e);
        layout.setPadding(16, 16, 16, 16);

        // Response text
        TextView responseText = new TextView(context);
        responseText.setTextColor(0xFFe0e0e0);
        responseText.setTextSize(13);
        responseText.setText("Ask me anything...");
        responseText.setPadding(8, 8, 8, 8);

        // Input row
        LinearLayout inputRow = new LinearLayout(context);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);

        EditText input = new EditText(context);
        input.setHint("Ask...");
        input.setHintTextColor(0xFF666680);
        input.setTextColor(0xFFffffff);
        input.setTextSize(13);
        LinearLayout.LayoutParams inputParams =
            new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        input.setLayoutParams(inputParams);

        Button sendBtn = new Button(context);
        sendBtn.setText("→");
        sendBtn.setTextColor(0xFFffffff);
        sendBtn.setBackgroundColor(0xFFe94560);

        Button closeBtn = new Button(context);
        closeBtn.setText("✕");
        closeBtn.setTextColor(0xFFffffff);
        closeBtn.setBackgroundColor(0xFF333355);

        inputRow.addView(input);
        inputRow.addView(sendBtn);
        inputRow.addView(closeBtn);

        layout.addView(responseText);
        layout.addView(inputRow);

        chatView = layout;

        WindowManager.LayoutParams chatParams = new WindowManager.LayoutParams(
            700, WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        );
        chatParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        chatParams.y = 100;

        windowManager.addView(chatView, chatParams);

        // Send button
        sendBtn.setOnClickListener(v -> {
            String msg = input.getText().toString().trim();
            if (!msg.isEmpty()) {
                responseText.setText("Thinking...");
                input.setText("");
                if (callback != null) callback.onQuickMessage(msg);
            }
        });

        closeBtn.setOnClickListener(v -> closeMiniChat());
    }

    public void updateChatResponse(String response) {
        if (chatView == null) return;
        LinearLayout layout = (LinearLayout) chatView;
        if (layout.getChildCount() > 0) {
            TextView tv = (TextView) layout.getChildAt(0);
            tv.setText(response);
        }
    }

    private void closeMiniChat() {
        if (!chatOpen || chatView == null) return;
        try {
            windowManager.removeView(chatView);
        } catch (Exception ignored) {}
        chatView = null;
        chatOpen = false;
    }

    // ─── QUICK COMMANDS ───────────────────────────────────────────────────────

    private void showQuickCommands() {
        // Launch main app with quick command flag
        Intent i = new Intent(context, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        i.putExtra("quick_command", true);
        context.startActivity(i);
    }

    public boolean isShowing() { return isShowing; }
}
