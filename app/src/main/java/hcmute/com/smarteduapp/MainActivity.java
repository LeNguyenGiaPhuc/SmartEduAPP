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

        bindClick(R.id.cardSubjectMobile, this::showSubjectDetail);
        bindClick(R.id.cardSubjectDatabase, this::showSubjectDetail);
        bindClick(R.id.cardRecentDocument, this::showSubjectDetail);
        bindClick(R.id.buttonAddSubject, this::showSubjectForm);
        bindClick(R.id.linkHistory, this::showHistory);
        bindClick(R.id.navHistory, this::showHistory);
    }

    private void showSubjectDetail() {
        currentScreen = R.layout.screen_subject_detail;
        setContentView(R.layout.screen_subject_detail);
        applySystemBars();

        bindClick(R.id.backHomeFromSubject, this::showHome);
        bindClick(R.id.buttonEditSubject, this::showSubjectForm);
        bindClick(R.id.buttonAddDocument, this::showDocumentForm);
        bindClick(R.id.cardActivityDocument, this::showProcessDocument);
        bindClick(R.id.navSubjects, this::showHome);
        bindClick(R.id.navHistoryFromSubject, this::showHistory);
    }

    private void showSubjectForm() {
        currentScreen = R.layout.screen_subject_form;
        setContentView(R.layout.screen_subject_form);
        applySystemBars();

        bindClick(R.id.backSubjects, this::showHome);
        bindClick(R.id.buttonSaveSubject, this::showSubjectDetail);
        bindClick(R.id.buttonDeleteSubject, this::showHome);
    }

    private void showDocumentForm() {
        currentScreen = R.layout.screen_document_form;
        setContentView(R.layout.screen_document_form);
        applySystemBars();

        bindClick(R.id.backSubjectFromDocument, this::showSubjectDetail);
        bindClick(R.id.buttonCamera, this::showProcessDocument);
        bindClick(R.id.buttonGallery, this::showProcessDocument);
        bindClick(R.id.buttonContinueOcr, this::showProcessDocument);
    }

    private void showProcessDocument() {
        currentScreen = R.layout.screen_process_document;
        setContentView(R.layout.screen_process_document);
        applySystemBars();

        bindClick(R.id.backHome, this::showSubjectDetail);
        bindClick(R.id.buttonSummary, this::showSummary);
        bindClick(R.id.buttonQuestions, this::showQuestionBank);
        bindClick(R.id.buttonSaveDocument, this::showSubjectDetail);
    }

    private void showSummary() {
        currentScreen = R.layout.screen_summary_explain;
        setContentView(R.layout.screen_summary_explain);
        applySystemBars();

        bindClick(R.id.backProcess, this::showProcessDocument);
        bindClick(R.id.buttonStartQuiz, this::showQuestionBank);
    }

    private void showQuestionBank() {
        currentScreen = R.layout.screen_question_bank;
        setContentView(R.layout.screen_question_bank);
        applySystemBars();

        bindClick(R.id.backProcessFromBank, this::showProcessDocument);
        bindClick(R.id.buttonStartQuizFromBank, this::showQuestions);
    }

    private void showQuestions() {
        currentScreen = R.layout.screen_questions;
        setContentView(R.layout.screen_questions);
        applySystemBars();

        bindClick(R.id.backProcessFromQuestions, this::showProcessDocument);
        bindClick(R.id.buttonCheckAnswers, this::showQuizResult);
    }

    private void showQuizResult() {
        currentScreen = R.layout.screen_quiz_result;
        setContentView(R.layout.screen_quiz_result);
        applySystemBars();

        bindClick(R.id.backQuestionBank, this::showQuestionBank);
        bindClick(R.id.buttonViewHistory, this::showHistory);
        bindClick(R.id.buttonRetryQuiz, this::showQuestions);
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
            // Android 15 draws the app edge-to-edge by default. Include the display
            // cutout safe area so every screen header stays below a punch-hole camera.
            Insets systemBars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout()
            );
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }

}
