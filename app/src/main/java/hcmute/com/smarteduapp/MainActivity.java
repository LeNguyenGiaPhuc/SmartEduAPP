package hcmute.com.smarteduapp;

import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {

    private int currentScreen = R.layout.activity_main;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (currentScreen == R.layout.activity_main) {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                } else {
                    showHome();
                }
            }
        });
        showHome();
    }

    private void showHome() {
        currentScreen = R.layout.activity_main;
        setContentView(R.layout.activity_main);
        applySystemBars();

        bindClick(R.id.cardCapture, this::showProcessDocument);
        bindClick(R.id.cardGallery, this::showProcessDocument);
        bindClick(R.id.linkHistory, this::showHistory);
        bindClick(R.id.navHistory, this::showHistory);
    }

    private void showProcessDocument() {
        currentScreen = R.layout.screen_process_document;
        setContentView(R.layout.screen_process_document);
        applySystemBars();

        bindClick(R.id.backHome, this::showHome);
        bindClick(R.id.buttonSummary, this::showSummaryExplain);
        bindClick(R.id.buttonExplain, this::showSummaryExplain);
        bindClick(R.id.buttonQuestions, this::showQuestions);
    }

    private void showSummaryExplain() {
        currentScreen = R.layout.screen_summary_explain;
        setContentView(R.layout.screen_summary_explain);
        applySystemBars();

        bindClick(R.id.backProcess, this::showProcessDocument);
        bindClick(R.id.buttonSaveHistory, this::showHistory);
    }

    private void showQuestions() {
        currentScreen = R.layout.screen_questions;
        setContentView(R.layout.screen_questions);
        applySystemBars();

        bindClick(R.id.backProcessFromQuestions, this::showProcessDocument);
        bindClick(R.id.buttonCheckAnswers, this::showHistory);
    }

    private void showHistory() {
        currentScreen = R.layout.screen_history;
        setContentView(R.layout.screen_history);
        applySystemBars();

        bindClick(R.id.backHomeFromHistory, this::showHome);
    }

    private void bindClick(int viewId, Runnable action) {
        View view = findViewById(viewId);
        if (view != null) {
            view.setOnClickListener(v -> action.run());
        }
    }

    private void applySystemBars() {
        View root = findViewById(R.id.main);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

}
