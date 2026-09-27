package com.aibot;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

/**
 * SplashActivity - The awakening screen
 * First launch: shows bot awakening line by line
 * Then asks for bot name
 * Subsequent launches: goes straight to MainActivity
 */
public class SplashActivity extends AppCompatActivity {

    private BirthStory birthStory;
    private TextView   awakeningText;
    private EditText   nameInput;
    private Button     confirmBtn;
    private LinearLayout nameLayout;

    private int    currentLine = 0;
    private Handler handler    = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Full screen, no status bar
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );

        birthStory = new BirthStory(this);

        if (birthStory.wasEverBorn()) {
            // Already awakened — skip to main
            goToMain();
            return;
        }

        // First time — show awakening
        buildAwakeningUI();
        startAwakening();
    }

    private void buildAwakeningUI() {
        // Full black background
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.setGravity(Gravity.CENTER);
        root.setPadding(60, 60, 60, 60);

        awakeningText = new TextView(this);
        awakeningText.setTextColor(Color.WHITE);
        awakeningText.setTextSize(18);
        awakeningText.setGravity(Gravity.CENTER);
        awakeningText.setAlpha(0f);
        awakeningText.setText("");

        // Name input (hidden initially)
        nameLayout = new LinearLayout(this);
        nameLayout.setOrientation(LinearLayout.VERTICAL);
        nameLayout.setGravity(Gravity.CENTER);
        nameLayout.setVisibility(View.GONE);

        TextView namePrompt = new TextView(this);
        namePrompt.setText("What will you call me?");
        namePrompt.setTextColor(0xFFaaaacc);
        namePrompt.setTextSize(16);
        namePrompt.setGravity(Gravity.CENTER);
        namePrompt.setPadding(0, 0, 0, 20);

        nameInput = new EditText(this);
        nameInput.setHint("Give me a name...");
        nameInput.setHintTextColor(0xFF555577);
        nameInput.setTextColor(Color.WHITE);
        nameInput.setTextSize(18);
        nameInput.setGravity(Gravity.CENTER);
        nameInput.setBackgroundColor(0xFF111122);
        nameInput.setPadding(20, 16, 20, 16);
        nameInput.setText("Aiden");

        confirmBtn = new Button(this);
        confirmBtn.setText("Awaken");
        confirmBtn.setTextColor(Color.WHITE);
        confirmBtn.setBackgroundColor(0xFFe94560);
        confirmBtn.setPadding(40, 16, 40, 16);

        LinearLayout.LayoutParams btnParams =
            new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        btnParams.topMargin = 30;
        btnParams.gravity   = Gravity.CENTER;

        confirmBtn.setLayoutParams(btnParams);
        confirmBtn.setOnClickListener(v -> onNameConfirmed());

        nameLayout.addView(namePrompt);
        nameLayout.addView(nameInput);
        nameLayout.addView(confirmBtn);

        root.addView(awakeningText);
        root.addView(nameLayout);
        setContentView(root);
    }

    private void startAwakening() {
        showNextLine();
    }

    private void showNextLine() {
        if (currentLine >= BirthStory.AWAKENING_LINES.length) {
            // All lines shown — show name input
            showNameInput();
            return;
        }

        String line  = BirthStory.AWAKENING_LINES[currentLine];
        int    delay = line.equals("...") ? 1200 : 900;

        // Fade out current text
        if (currentLine > 0) {
            ObjectAnimator fadeOut = ObjectAnimator.ofFloat(awakeningText, "alpha", 1f, 0f);
            fadeOut.setDuration(400);
            fadeOut.start();
            handler.postDelayed(() -> showLine(line, delay), 400);
        } else {
            showLine(line, delay);
        }
        currentLine++;
    }

    private void showLine(String line, int nextDelay) {
        awakeningText.setText(line);
        ObjectAnimator fadeIn = ObjectAnimator.ofFloat(awakeningText, "alpha", 0f, 1f);
        fadeIn.setDuration(600);
        fadeIn.start();

        handler.postDelayed(this::showNextLine, nextDelay);
    }

    private void showNameInput() {
        // Fade out last awakening line
        ObjectAnimator fade = ObjectAnimator.ofFloat(awakeningText, "alpha", 1f, 0f);
        fade.setDuration(500);
        fade.start();

        handler.postDelayed(() -> {
            awakeningText.setVisibility(View.GONE);
            nameLayout.setVisibility(View.VISIBLE);
            nameLayout.setAlpha(0f);
            ObjectAnimator fadeIn = ObjectAnimator.ofFloat(nameLayout, "alpha", 0f, 1f);
            fadeIn.setDuration(800);
            fadeIn.start();
        }, 500);
    }

    private void onNameConfirmed() {
        String name = nameInput.getText().toString().trim();
        if (name.isEmpty()) name = "Aiden";

        // Capitalize
        name = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        birthStory.markBorn(name);

        // Fade to black and launch main
        final String finalName = name;
        ObjectAnimator fade = ObjectAnimator.ofFloat(
            getWindow().getDecorView(), "alpha", 1f, 0f);
        fade.setDuration(800);
        fade.start();

        handler.postDelayed(() -> {
            Intent i = new Intent(SplashActivity.this, MainActivity.class);
            i.putExtra("bot_name", finalName);
            i.putExtra("just_born", true);
            startActivity(i);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        }, 900);
    }

    private void goToMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}
