package com.aibot;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

/**
 * ShareReceiverActivity - referenced in AndroidManifest.xml to handle
 * ACTION_SEND for text/* and image/* so AIBot appears in Android's
 * system share sheet ("Share to...").
 *
 * This class did not exist in the project, which caused a fatal
 * MissingClass lint error during `assembleNormal` and failed the build.
 *
 * Text shares: forwarded into MainActivity's input field via the
 * "shared_text" extra (see MainActivity.prefillSharedTextIfPresent),
 * so the user can review/edit before it enters the same learning
 * pipeline as anything typed normally.
 *
 * Image shares: this project has no on-device OCR or image model
 * anywhere in the codebase, so pretending to "read" the image would be
 * dishonest. Instead the user is told plainly that image sharing is
 * not processed yet, rather than silently doing nothing.
 */
public class ShareReceiverActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent incoming = getIntent();
        String action = incoming.getAction();
        String type = incoming.getType();

        String sharedText = null;

        if (Intent.ACTION_SEND.equals(action) && type != null) {
            if (type.startsWith("text/")) {
                sharedText = incoming.getStringExtra(Intent.EXTRA_TEXT);
            } else if (type.startsWith("image/")) {
                Uri imageUri = incoming.getParcelableExtra(Intent.EXTRA_STREAM);
                sharedText = "[Shared an image"
                        + (imageUri != null ? ": " + imageUri.toString() : "")
                        + " — I can't read image content yet, "
                        + "there's no OCR or image model in this build.]";
            }
        }

        Intent forward = new Intent(this, MainActivity.class);
        forward.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (sharedText != null) {
            forward.putExtra("shared_text", sharedText);
        }
        startActivity(forward);

        finish();
    }
}
