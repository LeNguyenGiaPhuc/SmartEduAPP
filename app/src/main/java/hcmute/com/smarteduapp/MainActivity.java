package hcmute.com.smarteduapp;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.card.MaterialCardView;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import hcmute.com.smarteduapp.data.AppDatabase;
import hcmute.com.smarteduapp.data.StudyDocument;
import hcmute.com.smarteduapp.data.Subject;

public class MainActivity extends AppCompatActivity {

    private int currentScreen = R.layout.activity_main;
    private AppDatabase database;
    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();
    private long selectedSubjectId = -1L;
    private Subject selectedSubject;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        database = AppDatabase.getInstance(this);
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

        findViewById(R.id.cardSubjectMobile).setVisibility(View.GONE);
        findViewById(R.id.cardSubjectDatabase).setVisibility(View.GONE);
        findViewById(R.id.cardRecentDocument).setVisibility(View.GONE);
        bindClick(R.id.buttonAddSubject, () -> showSubjectForm(-1L));
        bindClick(R.id.linkHistory, this::showHistory);
        bindClick(R.id.navHistory, this::showHistory);
        loadSubjects();
    }

    private void loadSubjects() {
        databaseExecutor.execute(() -> {
            List<Subject> subjects = database.subjectDao().getAll();
            runOnUiThread(() -> renderSubjects(subjects));
        });
    }

    private void renderSubjects(List<Subject> subjects) {
        LinearLayout container = findViewById(R.id.subjectListContainer);
        TextView empty = findViewById(R.id.emptySubjects);
        container.removeAllViews();
        empty.setVisibility(subjects.isEmpty() ? View.VISIBLE : View.GONE);

        for (Subject subject : subjects) {
            MaterialCardView card = createCard();
            card.setOnClickListener(v -> {
                selectedSubjectId = subject.id;
                selectedSubject = subject;
                showSubjectDetail();
            });

            LinearLayout content = new LinearLayout(this);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(dp(16), dp(15), dp(16), dp(15));

            TextView name = createText(subject.name, 17, R.color.ink, true);
            TextView description = createText(
                    isBlank(subject.description) ? "Chưa có mô tả" : subject.description,
                    13, R.color.ink_muted, false
            );
            description.setPadding(0, dp(5), 0, 0);
            content.addView(name);
            content.addView(description);
            card.addView(content);
            container.addView(card, verticalMargin(12));
        }
    }

    private void showSubjectDetail() {
        if (selectedSubjectId < 0) {
            showHome();
            return;
        }
        currentScreen = R.layout.screen_subject_detail;
        setContentView(R.layout.screen_subject_detail);
        applySystemBars();

        findViewById(R.id.cardActivityDocument).setVisibility(View.GONE);
        findViewById(R.id.cardPendingDocument).setVisibility(View.GONE);
        bindClick(R.id.backHomeFromSubject, this::showHome);
        bindClick(R.id.buttonEditSubject, () -> showSubjectForm(selectedSubjectId));
        bindClick(R.id.buttonAddDocument, this::showDocumentForm);
        bindClick(R.id.navSubjects, this::showHome);
        bindClick(R.id.navHistoryFromSubject, this::showHistory);
        loadSubjectDetail();
    }

    private void loadSubjectDetail() {
        databaseExecutor.execute(() -> {
            Subject subject = database.subjectDao().getById(selectedSubjectId);
            List<StudyDocument> documents = database.studyDocumentDao().getBySubjectId(selectedSubjectId);
            runOnUiThread(() -> {
                if (subject == null) {
                    selectedSubjectId = -1L;
                    selectedSubject = null;
                    showHome();
                    return;
                }
                selectedSubject = subject;
                ((TextView) findViewById(R.id.subjectTitle)).setText(subject.name);
                ((TextView) findViewById(R.id.subjectStats)).setText(
                        documents.size() + " tài liệu đã lưu"
                );
                renderDocuments(documents);
            });
        });
    }

    private void renderDocuments(List<StudyDocument> documents) {
        LinearLayout container = findViewById(R.id.documentListContainer);
        TextView empty = findViewById(R.id.emptyDocuments);
        container.removeAllViews();
        empty.setVisibility(documents.isEmpty() ? View.VISIBLE : View.GONE);

        for (StudyDocument document : documents) {
            MaterialCardView card = createCard();
            card.setOnClickListener(v -> showProcessDocument());
            LinearLayout content = new LinearLayout(this);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(dp(16), dp(15), dp(16), dp(15));
            content.addView(createText(document.title, 16, R.color.ink, true));
            TextView state = createText(
                    isBlank(document.ocrText) ? "Chưa có nội dung OCR" : "Đã lưu nội dung OCR",
                    13, R.color.ink_muted, false
            );
            state.setPadding(0, dp(5), 0, 0);
            content.addView(state);
            card.addView(content);
            container.addView(card, verticalMargin(12));
        }
    }

    private void showSubjectForm(long subjectId) {
        currentScreen = R.layout.screen_subject_form;
        setContentView(R.layout.screen_subject_form);
        applySystemBars();

        EditText nameInput = findViewById(R.id.inputSubjectName);
        EditText descriptionInput = findViewById(R.id.inputSubjectDescription);
        TextView title = findViewById(R.id.subjectFormTitle);
        View deleteButton = findViewById(R.id.buttonDeleteSubject);
        boolean isEditing = subjectId > 0;
        deleteButton.setVisibility(isEditing ? View.VISIBLE : View.GONE);
        title.setText(isEditing ? "Chỉnh sửa môn học" : "Thêm môn học");

        if (isEditing) {
            databaseExecutor.execute(() -> {
                Subject subject = database.subjectDao().getById(subjectId);
                runOnUiThread(() -> {
                    if (subject == null) {
                        showHome();
                        return;
                    }
                    nameInput.setText(subject.name);
                    descriptionInput.setText(subject.description);
                });
            });
        }

        bindClick(R.id.backSubjects, this::showHome);
        bindClick(R.id.buttonSaveSubject, () -> saveSubject(subjectId, nameInput, descriptionInput));
        bindClick(R.id.buttonDeleteSubject, () -> deleteSubject(subjectId));
    }

    private void saveSubject(long subjectId, EditText nameInput, EditText descriptionInput) {
        String name = nameInput.getText().toString().trim();
        String description = descriptionInput.getText().toString().trim();
        if (name.isEmpty()) {
            nameInput.setError("Nhập tên môn học");
            return;
        }

        databaseExecutor.execute(() -> {
            try {
                if (subjectId > 0) {
                    Subject subject = database.subjectDao().getById(subjectId);
                    if (subject != null) {
                        subject.name = name;
                        subject.description = description;
                        database.subjectDao().update(subject);
                    }
                    selectedSubjectId = subjectId;
                } else {
                    selectedSubjectId = database.subjectDao().insert(
                            new Subject(name, description, System.currentTimeMillis())
                    );
                }
                runOnUiThread(this::showSubjectDetail);
            } catch (Exception exception) {
                runOnUiThread(() -> Toast.makeText(this,
                        "Tên môn học đã tồn tại hoặc không thể lưu", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void deleteSubject(long subjectId) {
        if (subjectId <= 0) return;
        databaseExecutor.execute(() -> {
            Subject subject = database.subjectDao().getById(subjectId);
            if (subject != null) database.subjectDao().delete(subject);
            selectedSubjectId = -1L;
            selectedSubject = null;
            runOnUiThread(this::showHome);
        });
    }

    private void showDocumentForm() {
        if (selectedSubjectId < 0) {
            showHome();
            return;
        }
        currentScreen = R.layout.screen_document_form;
        setContentView(R.layout.screen_document_form);
        applySystemBars();

        EditText titleInput = findViewById(R.id.inputDocumentTitle);
        TextView subjectLabel = findViewById(R.id.documentSubjectLabel);
        subjectLabel.setText("Môn học: " + (selectedSubject == null ? "" : selectedSubject.name));
        bindClick(R.id.backSubjectFromDocument, this::showSubjectDetail);
        bindClick(R.id.buttonCamera, () -> showImageFeatureMessage());
        bindClick(R.id.buttonGallery, () -> showImageFeatureMessage());
        bindClick(R.id.buttonContinueOcr, () -> saveDocument(titleInput));
    }

    private void saveDocument(EditText titleInput) {
        String title = titleInput.getText().toString().trim();
        if (title.isEmpty()) {
            titleInput.setError("Nhập tên tài liệu");
            return;
        }
        databaseExecutor.execute(() -> {
            long now = System.currentTimeMillis();
            database.studyDocumentDao().insert(
                    new StudyDocument(selectedSubjectId, title, null, "", now, now)
            );
            runOnUiThread(this::showSubjectDetail);
        });
    }

    private void showImageFeatureMessage() {
        Toast.makeText(this, "Chọn ảnh và camera sẽ được thêm sau CRUD cơ bản", Toast.LENGTH_SHORT).show();
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

    private MaterialCardView createCard() {
        MaterialCardView card = new MaterialCardView(this);
        card.setCardBackgroundColor(ContextCompat.getColor(this, R.color.paper));
        card.setStrokeColor(ContextCompat.getColor(this, R.color.line_soft));
        card.setStrokeWidth(dp(1));
        card.setRadius(dp(18));
        card.setCardElevation(dp(3));
        card.setClickable(true);
        card.setFocusable(true);
        return card;
    }

    private TextView createText(String text, int sizeSp, int colorRes, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(sizeSp);
        view.setTextColor(ContextCompat.getColor(this, colorRes));
        if (bold) view.setTypeface(null, android.graphics.Typeface.BOLD);
        return view;
    }

    private LinearLayout.LayoutParams verticalMargin(int topMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = dp(topMarginDp);
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private void bindClick(int viewId, Runnable action) {
        View view = findViewById(viewId);
        if (view != null) view.setOnClickListener(v -> action.run());
    }

    private void applySystemBars() {
        View root = findViewById(R.id.main);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets systemBars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()
            );
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }
}
