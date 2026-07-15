package hcmute.com.smarteduapp.ui.main;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.content.FileProvider;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.card.MaterialCardView;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.io.File;
import java.io.IOException;

import hcmute.com.smarteduapp.R;
import hcmute.com.smarteduapp.data.local.entity.StudyDocument;
import hcmute.com.smarteduapp.data.local.entity.StudyDocumentAttachment;
import hcmute.com.smarteduapp.data.local.entity.Subject;
import hcmute.com.smarteduapp.data.repository.DocumentRepository;
import hcmute.com.smarteduapp.data.repository.RepositoryCallback;
import hcmute.com.smarteduapp.data.repository.SubjectRepository;
import hcmute.com.smarteduapp.ui.common.UiViewFactory;
import hcmute.com.smarteduapp.ui.document.DocumentAttachmentUi;
import hcmute.com.smarteduapp.ui.study.ChatMessageRenderer;
import hcmute.com.smarteduapp.ui.study.QuizUiRenderer;
import hcmute.com.smarteduapp.ui.subject.SubjectListRenderer;
import hcmute.com.smarteduapp.data.repository.StudyRepository;
import hcmute.com.smarteduapp.data.local.entity.StudySummary;
import hcmute.com.smarteduapp.service.ai.GeminiService;
import hcmute.com.smarteduapp.service.document.DocumentTextScannerService;
import hcmute.com.smarteduapp.service.ocr.MlKitOcrService;
import hcmute.com.smarteduapp.service.study.QuizParser;
import hcmute.com.smarteduapp.service.study.QuizScoringService;
import hcmute.com.smarteduapp.data.local.entity.StudyQuestion;
import hcmute.com.smarteduapp.data.local.entity.QuizAttempt;

public class MainActivity extends AppCompatActivity {

    static final String RECENT_SUBJECT_PREF = "recent_subject_ids";
    static final int MAX_RECENT_SUBJECTS = 3;
    int currentScreen = R.layout.activity_main;
    SubjectRepository subjectRepository;
    DocumentRepository documentRepository;
    StudyRepository studyRepository;
    long selectedSubjectId = -1L;
    Subject selectedSubject;
    StudyDocument selectedDocument;
    GeminiService geminiService;
    MlKitOcrService ocrService;
    DocumentTextScannerService documentTextScannerService;
    QuizParser quizParser;
    QuizScoringService quizScoringService;
    HomeMenuController homeMenuController;
    HomeDashboardRenderer homeDashboardRenderer;
    DocumentAttachmentUi documentAttachmentUi;
    ChatMessageRenderer chatMessageRenderer;
    QuizUiRenderer quizUiRenderer;
    SubjectListRenderer subjectListRenderer;
    HistoryController historyController;
    SubjectController subjectController;
    StudyController studyController;
    DocumentController documentController;
    List<StudyQuestion> currentQuizQuestions = new ArrayList<>();
    int currentQuizQuestionIndex;
    final Map<Long, String> selectedQuizAnswers = new HashMap<>();
    final LinkedHashMap<Long, Subject> recentSubjects = new LinkedHashMap<>();
    QuizAttempt latestQuizAttempt;
    StudySummary latestDisplayedSummary;
    boolean documentOpenedFromHistory;
    boolean quizResultOpenedFromHome;
    boolean focusQuizModeEnabled;
    boolean focusQuizTrackingActive;
    int focusQuizExitCount;
    boolean focusQuizSubmitting;
    Uri selectedDocumentAttachmentUri;
    Uri pendingCameraAttachmentUri;
    ActivityResultLauncher<String[]> documentImagePickerLauncher;
    ActivityResultLauncher<String[]> documentFilePickerLauncher;
    ActivityResultLauncher<Uri> cameraCaptureLauncher;
    ActivityResultLauncher<String[]> addMoreAttachmentsPickerLauncher;
    List<Uri> pendingDocumentAttachmentUris = new ArrayList<>();
    List<StudyDocumentAttachment> selectedDocumentAttachments = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        subjectRepository = new SubjectRepository(this);
        documentRepository = new DocumentRepository(this);
        studyRepository = new StudyRepository(this);
        geminiService = new GeminiService();
        ocrService = new MlKitOcrService();
        documentTextScannerService = new DocumentTextScannerService(ocrService);
        quizParser = new QuizParser();
        quizScoringService = new QuizScoringService();
        homeMenuController = new HomeMenuController(this);
        homeDashboardRenderer = new HomeDashboardRenderer(this);
        documentAttachmentUi = new DocumentAttachmentUi(this);
        chatMessageRenderer = new ChatMessageRenderer(this);
        quizUiRenderer = new QuizUiRenderer(this);
        subjectListRenderer = new SubjectListRenderer(this);
        historyController = new HistoryController(this);
        subjectController = new SubjectController(this);
        studyController = new StudyController(this);
        documentController = new DocumentController(this);
        registerImageLaunchers();
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (isFocusQuizInProgress()) {
                    Toast.makeText(
                            MainActivity.this,
                            "Đang ở chế độ tập trung. Hãy nộp quiz trước khi thoát.",
                            Toast.LENGTH_SHORT
                    ).show();
                    return;
                }

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

    @Override
    protected void onStop() {
        super.onStop();
        // onPause() can be triggered by short-lived system UI. onStop() is a
        // better approximation of the user really leaving the app.
        if (isChangingConfigurations()) {
            return;
        }
        if (isFocusQuizInProgress() && !focusQuizSubmitting) {
            focusQuizExitCount++;
        }
    }

    private void registerImageLaunchers() {
        documentImagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri == null) {
                        return;
                    }
                    handlePickedDocumentUri(uri);
                }
        );

        addMoreAttachmentsPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri == null || selectedDocument == null) return;
                    try {
                        getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } catch (Exception ignored) {}
                    
                    documentRepository.addAttachment(selectedDocument.id, uri.toString(), new RepositoryCallback<Long>() {
                        @Override
                        public void onSuccess(Long id) {
                            loadDocumentAttachments(selectedDocument.id);
                        }
                        @Override
                        public void onError(Exception e) {
                            Toast.makeText(MainActivity.this, "Lỗi thêm file tài liệu", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
        );

        documentFilePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri == null) {
                        return;
                    }
                    handlePickedDocumentUri(uri);
                }
        );

        cameraCaptureLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicture(),
                success -> {
                            if (Boolean.TRUE.equals(success) && pendingCameraAttachmentUri != null) {
                                handlePickedDocumentUri(pendingCameraAttachmentUri);
                        return;
                    }
                    pendingCameraAttachmentUri = null;
                    Toast.makeText(this, "Không chụp được ảnh", Toast.LENGTH_SHORT).show();
                }
        );
    }

    void handlePickedDocumentUri(Uri uri) {
        documentController.handlePickedDocumentUri(uri);
    }

    void showDocumentForm() {
        documentController.showDocumentForm();
    }

    void showDocumentForm(long documentId) {
        documentController.showDocumentForm(documentId);
    }

    void updateSelectedImageLabel() {
        documentController.updateSelectedImageLabel();
    }

    void openDocument(long id) {
        documentOpenedFromHistory = false;
        quizResultOpenedFromHome = false;
        documentController.openDocument(id);
    }

    void showProcessDocument() {
        documentController.showProcessDocument();
    }

    void ensureCurrentDocumentHasAttachments(String unavailableMessage, Runnable action) {
        documentController.ensureCurrentDocumentHasAttachments(unavailableMessage, action);
    }

    void loadDocumentAttachments(long documentId) {
        documentController.loadDocumentAttachments(documentId);
    }

    void showHome() {
        currentScreen = R.layout.activity_main;
        setContentView(R.layout.activity_main);
        applySystemBars();
        documentOpenedFromHistory = false;
        quizResultOpenedFromHome = false;

        bindClick(R.id.buttonOpenHomeMenu, this::showHomeMenu);
        bindClick(R.id.buttonCloseHomeMenu, this::hideHomeMenu);
        bindClick(R.id.homeMenuBackdrop, this::hideHomeMenu);
        bindClick(R.id.buttonSubjectList, () -> {
            hideHomeMenu();
            showSubjectList();
        });
        bindClick(R.id.buttonLearningHistory, () -> {
            hideHomeMenu();
            showHistory();
        });
        bindClick(R.id.buttonLogout, () -> {
            hideHomeMenu();
            Toast.makeText(this, "Chức năng đăng xuất sẽ dùng khi app có đăng nhập", Toast.LENGTH_SHORT).show();
        });
        loadHomeDashboard();
    }

    private void showHomeMenu() {
        homeMenuController.show();
    }

    private void hideHomeMenu() {
        homeMenuController.hide();
    }

    private void loadHomeDashboard() {
        subjectRepository.getAll(new RepositoryCallback<List<Subject>>() {
            @Override
            public void onSuccess(List<Subject> subjects) {
                syncRecentSubjects(subjects);
                renderRecentSubjects();
                loadHomeQuizHistory();
            }

            @Override
            public void onError(Exception exception) {
                renderRecentSubjects();
                loadHomeQuizHistory();
            }
        });
    }

    void rememberRecentSubject(Subject subject) {
        if (subject == null) {
            return;
        }
        recentSubjects.remove(subject.id);
        recentSubjects.put(subject.id, subject);
        while (recentSubjects.size() > MAX_RECENT_SUBJECTS) {
            Long oldestKey = recentSubjects.keySet().iterator().next();
            recentSubjects.remove(oldestKey);
        }
        saveRecentSubjectIds();
    }

    void syncRecentSubjects(List<Subject> subjects) {
        if (!recentSubjects.isEmpty()) {
            return;
        }

        String savedIds = getPreferences(MODE_PRIVATE).getString(RECENT_SUBJECT_PREF, "");
        if (isBlank(savedIds)) {
            return;
        }

        Map<Long, Subject> subjectMap = new HashMap<>();
        for (Subject subject : subjects) {
            subjectMap.put(subject.id, subject);
        }

        String[] ids = savedIds.split(",");
        for (String rawId : ids) {
            try {
                long id = Long.parseLong(rawId.trim());
                Subject subject = subjectMap.get(id);
                if (subject != null) {
                    recentSubjects.put(id, subject);
                }
            } catch (NumberFormatException ignored) {
                // Skip invalid persisted ids.
            }
        }
    }

    private void saveRecentSubjectIds() {
        StringBuilder ids = new StringBuilder();
        for (Long id : recentSubjects.keySet()) {
            if (ids.length() > 0) {
                ids.append(",");
            }
            ids.append(id);
        }
        getPreferences(MODE_PRIVATE)
                .edit()
                .putString(RECENT_SUBJECT_PREF, ids.toString())
                .apply();
    }

    void renderRecentSubjects() {
        homeDashboardRenderer.renderRecentSubjects(new ArrayList<>(recentSubjects.values()), subject -> {
            selectedSubjectId = subject.id;
            selectedSubject = subject;
            rememberRecentSubject(subject);
            showSubjectDetail();
        });
    }

    private void loadHomeQuizHistory() {
        documentRepository.getAll(new RepositoryCallback<List<StudyDocument>>() {
            @Override
            public void onSuccess(List<StudyDocument> documents) {
                studyRepository.getAllQuizAttempts(new RepositoryCallback<List<QuizAttempt>>() {
                    @Override
                    public void onSuccess(List<QuizAttempt> attempts) {
                        renderHomeQuizHistory(documents, attempts);
                    }

                    @Override
                    public void onError(Exception exception) {
                        renderHomeQuizHistory(documents, new ArrayList<>());
                    }
                });
            }

            @Override
            public void onError(Exception exception) {
                renderHomeQuizHistory(new ArrayList<>(), new ArrayList<>());
            }
        });
    }

    private void renderHomeQuizHistory(List<StudyDocument> documents, List<QuizAttempt> attempts) {
        homeDashboardRenderer.renderHomeQuizHistory(documents, attempts, (document, attempt) -> {
            if (document == null || attempt == null) {
                showHistory();
                return;
            }
            historyController.openQuizAttemptResult(document, attempt, true);
        });
    }

    void showSubjectList() {
        subjectController.showSubjectList();
    }

    void showSubjectDetail() {
        subjectController.showSubjectDetail();
    }

    void showSubjectForm(long subjectId) {
        subjectController.showSubjectForm(subjectId);
    }

    void showAiChat() {
        studyController.showAiChat();
    }

    void showSummary() {
        studyController.showSummary();
    }

    void createSummaryFromCurrentDocument() {
        studyController.createSummaryFromCurrentDocument();
    }

    void createQuizFromCurrentDocument() {
        studyController.createQuizFromCurrentDocument();
    }

    void showQuestions() {
        studyController.showQuestions();
    }

    void showQuizResult() {
        studyController.showQuizResult();
    }

    void showHistory() {
        quizResultOpenedFromHome = false;
        historyController.showHistory();
    }

    void startFocusQuizSession(boolean enabled) {
        focusQuizModeEnabled = enabled;
        focusQuizTrackingActive = enabled;
        focusQuizExitCount = 0;
        focusQuizSubmitting = false;
    }

    void stopFocusQuizTracking() {
        focusQuizTrackingActive = false;
        focusQuizSubmitting = false;
    }

    void resetFocusQuizSession() {
        focusQuizModeEnabled = false;
        focusQuizTrackingActive = false;
        focusQuizExitCount = 0;
        focusQuizSubmitting = false;
    }

    boolean isFocusQuizInProgress() {
        return focusQuizModeEnabled
                && focusQuizTrackingActive
                && currentScreen == R.layout.screen_questions;
    }

    boolean shouldUnlockQuizExplanation() {
        return !focusQuizModeEnabled || focusQuizExitCount == 0;
    }

    boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    void bindClick(int viewId, Runnable action) {
        View view = findViewById(viewId);
        if (view != null) {
            UiViewFactory.applyPressEffect(view);
            view.setOnClickListener(v -> action.run());
        }
    }

    void applySystemBars() {
        View root = findViewById(R.id.main);
        animateScreenEnter(root);
        int initialLeft = root == null ? 0 : root.getPaddingLeft();
        int initialTop = root == null ? 0 : root.getPaddingTop();
        int initialRight = root == null ? 0 : root.getPaddingRight();
        int initialBottom = root == null ? 0 : root.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets systemBars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()
            );
            v.setPadding(
                    initialLeft + systemBars.left,
                    initialTop + systemBars.top,
                    initialRight + systemBars.right,
                    initialBottom + systemBars.bottom
            );
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    private void animateScreenEnter(View root) {
        if (root == null) {
            return;
        }
        root.setAlpha(0f);
        root.setTranslationY(UiViewFactory.dp(this, 10));
        root.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(240)
                .start();
    }
}
