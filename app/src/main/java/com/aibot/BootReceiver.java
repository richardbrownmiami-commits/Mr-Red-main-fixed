package com.aibot;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * BootReceiver - referenced in AndroidManifest.xml as a receiver for
 * BOOT_COMPLETED / LOCKED_BOOT_COMPLETED / QUICKBOOT_POWERON.
 *
 * This class did not exist yet, which caused a fatal MissingClass lint
 * error during `assembleNormal` (lint-vital runs on non-debuggable
 * build types and treats MissingClass as fatal, failing the build).
 *
 * Kept intentionally minimal and safe: it does not auto-launch any UI
 * on boot (that would be intrusive and could trigger Android 12+
 * background-activity-start restrictions). It only logs that boot
 * completed, so the receiver is valid and present without adding
 * behavior you have not asked for.
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;

        String action = intent.getAction();
        Log.d(TAG, "Received boot action: " + action);

        switch (action) {
            case Intent.ACTION_BOOT_COMPLETED:
            case "android.intent.action.QUICKBOOT_POWERON":
            case "android.intent.action.LOCKED_BOOT_COMPLETED":
                // Intentionally no-op beyond logging. If you want AIBot to
                // restart something specific after boot (e.g. re-show the
                // overlay bubble once the user next opens the app), that
                // logic belongs here, gated behind an explicit user setting.
                break;
            default:
                break;
        }
    }
}
