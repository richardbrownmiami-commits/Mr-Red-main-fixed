package com.aibot;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

/**
 * AssistantActivity - referenced in AndroidManifest.xml as the handler for
 * android.intent.action.ASSIST, which lets this app be selected as the
 * device's default assistant (long-press home / assistant gesture).
 *
 * This class did not exist in the project, which caused a fatal
 * MissingClass lint error during `assembleNormal` and failed the build.
 *
 * It deliberately does not duplicate MainActivity's chat UI. It forwards
 * straight into MainActivity so there is exactly one real implementation
 * of the chat screen, and finishes itself so it does not sit in the
 * back stack as an empty extra screen.
 */
public class AssistantActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent forward = new Intent(this, MainActivity.class);
        forward.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        forward.putExtra("launched_from_assist", true);
        startActivity(forward);

        finish();
    }
}
