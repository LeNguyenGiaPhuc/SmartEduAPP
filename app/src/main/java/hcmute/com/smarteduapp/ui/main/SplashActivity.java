package hcmute.com.smarteduapp.ui.main;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import hcmute.com.smarteduapp.R;

/**
 * Short startup screen that shows the full wordmark before opening the app.
 * Android's system splash treats a wide logo as a square icon, so the text
 * becomes too small. This screen keeps the original logo ratio.
 */
public class SplashActivity extends AppCompatActivity {
    private static final long SPLASH_DURATION_MS = 900L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            startActivity(new Intent(SplashActivity.this, MainActivity.class));
            finish();
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        }, SPLASH_DURATION_MS);
    }
}
