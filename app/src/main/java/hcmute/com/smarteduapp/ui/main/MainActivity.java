package hcmute.com.smarteduapp.ui.main;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
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
import hcmute.com.smarteduapp.data.local.entity.StudyDocumentImage;
import hcmute.com.smarteduapp.data.local.entity.Subject;
import hcmute.com.smarteduapp.data.repository.DocumentRepository;
import hcmute.com.smarteduapp.data.repository.RepositoryCallback;
import hcmute.com.smarteduapp.data.repository.SubjectRepository;
import hcmute.com.smarteduapp.ui.common.UiViewFactory;
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

    private static final String RECENT_SUBJECT_PREF = "recent_subject_ids";
    private static final int MAX_RECENT_SUBJECTS = 3;
    private int currentScreen = R.layout.activity_main;
    private SubjectRepository subjectRepository;
    private DocumentRepository documentRepository;
    private StudyRepository studyRepository;
    private long selectedSubjectId = -1L;
    private Subject selectedSubject;
    private StudyDocument selectedDocument;
    private GeminiService geminiService;
    private MlKitOcrService ocrService;
    private DocumentTextScannerService documentTextScannerService;
    private QuizParser quizParser;
    private QuizScoringService quizScoringService;
    private List<StudyQuestion> currentQuizQuestions = new ArrayList<>();
    private final Map<Long, String> selectedQuizAnswers = new HashMap<>();
    private final LinkedHashMap<Long, Subject> recentSubjects = new LinkedHashMap<>();
    private QuizAttempt latestQuizAttempt;
    private StudySummary latestDisplayedSummary;
    private Uri selectedDocumentImageUri;
    private Uri pendingCameraImageUri;
    private ActivityResultLauncher<String[]> documentImagePickerLauncher;
    private ActivityResultLauncher<String[]> documentFilePickerLauncher;
    private ActivityResultLauncher<Uri> cameraCaptureLauncher;
    private ActivityResultLauncher<String[]> addMoreImagesPickerLauncher;
    private List<StudyDocumentImage> selectedDocumentImages = new ArrayList<>();

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
        registerImageLaunchers();
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

        addMoreImagesPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri == null || selectedDocument == null) return;
                    try {
                        getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } catch (Exception ignored) {}
                    
                    documentRepository.addImage(selectedDocument.id, uri.toString(), new RepositoryCallback<Long>() {
                        @Override
                        public void onSuccess(Long id) {
                            loadDocumentImages(selectedDocument.id);
                        }
                        @Override
                        public void onError(Exception e) {
                            Toast.makeText(MainActivity.this, "Lỗi thêm ảnh", Toast.LENGTH_SHORT).show();
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
                            if (Boolean.TRUE.equals(success) && pendingCameraImageUri != null) {
                                selectedDocumentImageUri = pendingCameraImageUri;
                                updateSelectedImageLabel();
                        return;
                    }
                    pendingCameraImageUri = null;
                    Toast.makeText(this, "Không chụp được ảnh", Toast.LENGTH_SHORT).show();
                }
        );
    }

    private void handlePickedDocumentUri(Uri uri) {
        try {
            getContentResolver().takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
            );
        } catch (SecurityException ignored) {
            // Some providers do not expose persistable permissions. The URI is still usable
            // during the current app session, while ACTION_OPEN_DOCUMENT providers persist.
        }
        selectedDocumentImageUri = uri;
        updateSelectedImageLabel();
    }

    private void showHome() {
        currentScreen = R.layout.activity_main;
        setContentView(R.layout.activity_main);
        applySystemBars();

        findViewById(R.id.cardSubjectMobile).setVisibility(View.GONE);
        findViewById(R.id.cardSubjectDatabase).setVisibility(View.GONE);
        bindClick(R.id.buttonAddSubject, () -> showSubjectForm(-1L));
        loadSubjects();
        renderRecentSubjects();
    }

    private void loadSubjects() {
        subjectRepository.getAll(new RepositoryCallback<List<Subject>>() {
            @Override
            public void onSuccess(List<Subject> subjects) {
                renderSubjects(subjects);
            }
        });
    }

    private void renderSubjects(List<Subject> subjects) {
        syncRecentSubjects(subjects);
        renderRecentSubjects();

        LinearLayout container = findViewById(R.id.subjectListContainer);
        TextView empty = findViewById(R.id.emptySubjects);
        container.removeAllViews();
        empty.setVisibility(subjects.isEmpty() ? View.VISIBLE : View.GONE);

        int cardIndex = 0;
        for (Subject subject : subjects) {
            MaterialCardView card = UiViewFactory.createCard(this);
            card.setOnClickListener(v -> {
                selectedSubjectId = subject.id;
                selectedSubject = subject;
                rememberRecentSubject(subject);
                showSubjectDetail();
            });

            LinearLayout content = new LinearLayout(this);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(UiViewFactory.dp(this, 16), UiViewFactory.dp(this, 15),
                    UiViewFactory.dp(this, 16), UiViewFactory.dp(this, 15));

            TextView name = UiViewFactory.createText(this, subject.name, 17, R.color.ink, true);
            TextView description = UiViewFactory.createText(this,
                    isBlank(subject.description) ? "Chưa có mô tả" : subject.description,
                    13, R.color.ink_muted, false
            );
            description.setPadding(0, UiViewFactory.dp(this, 5), 0, 0);
            content.addView(name);
            content.addView(description);
            card.addView(content);
            container.addView(card, UiViewFactory.verticalMargin(this, 12));
            UiViewFactory.animateIn(card, cardIndex++);
        }
    }

    private void rememberRecentSubject(Subject subject) {
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

    private void syncRecentSubjects(List<Subject> subjects) {
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

    private void renderRecentSubjects() {
        LinearLayout container = findViewById(R.id.recentSubjectContainer);
        TextView empty = findViewById(R.id.emptyRecentSubjects);
        if (container == null || empty == null) {
            return;
        }

        container.removeAllViews();
        empty.setVisibility(recentSubjects.isEmpty() ? View.VISIBLE : View.GONE);

        List<Subject> subjects = new ArrayList<>(recentSubjects.values());
        int visibleIndex = 0;
        for (int index = subjects.size() - 1; index >= 0; index--) {
            Subject subject = subjects.get(index);
            MaterialCardView card = UiViewFactory.createCard(this);
            card.setOnClickListener(v -> {
                selectedSubjectId = subject.id;
                selectedSubject = subject;
                rememberRecentSubject(subject);
                showSubjectDetail();
            });

            LinearLayout content = new LinearLayout(this);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(
                    UiViewFactory.dp(this, 16),
                    UiViewFactory.dp(this, 14),
                    UiViewFactory.dp(this, 16),
                    UiViewFactory.dp(this, 14)
            );

            content.addView(UiViewFactory.createText(this, subject.name, 16, R.color.ink, true));
            TextView description = UiViewFactory.createText(
                    this,
                    isBlank(subject.description) ? "Vừa truy cập" : subject.description,
                    13,
                    R.color.ink_muted,
                    false
            );
            description.setPadding(0, UiViewFactory.dp(this, 5), 0, 0);
            content.addView(description);
            card.addView(content);
            container.addView(card, UiViewFactory.verticalMargin(this, 12));
            UiViewFactory.animateIn(card, visibleIndex++);
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
        loadSubjectDetail();
    }

    private void loadSubjectDetail() {
        subjectRepository.getById(selectedSubjectId, new RepositoryCallback<Subject>() {
            @Override
            public void onSuccess(Subject subject) {
                if (subject == null) {
                    selectedSubjectId = -1L;
                    selectedSubject = null;
                    showHome();
                    return;
                }
                selectedSubject = subject;
                rememberRecentSubject(subject);
                documentRepository.getBySubjectId(selectedSubjectId,
                        new RepositoryCallback<List<StudyDocument>>() {
                            @Override
                            public void onSuccess(List<StudyDocument> documents) {
                                ((TextView) findViewById(R.id.subjectTitle)).setText(subject.name);
                                renderDocuments(documents);
                            }
                        });
            }
        });
    }

    private void renderDocuments(List<StudyDocument> documents) {
        LinearLayout container = findViewById(R.id.documentListContainer);
        TextView empty = findViewById(R.id.emptyDocuments);
        container.removeAllViews();
        empty.setVisibility(documents.isEmpty() ? View.VISIBLE : View.GONE);

        int cardIndex = 0;
        for (StudyDocument document : documents) {
            MaterialCardView card = UiViewFactory.createCard(this);
            card.setOnClickListener(v -> openDocument(document.id));
            LinearLayout content = new LinearLayout(this);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(UiViewFactory.dp(this, 16), UiViewFactory.dp(this, 15),
                    UiViewFactory.dp(this, 16), UiViewFactory.dp(this, 15));
            content.addView(UiViewFactory.createText(this, document.title, 16,
                    R.color.ink, true));
            String imageState = "Tài liệu đính kèm";
            String ocrState = isBlank(document.ocrText) ? "Chưa có nội dung OCR" : "Đã lưu nội dung OCR";
            TextView state = UiViewFactory.createText(this,
                    imageState + " · " + ocrState,
                    13, R.color.ink_muted, false
            );
            state.setPadding(0, UiViewFactory.dp(this, 5), 0, 0);
            content.addView(state);
            card.addView(content);
            container.addView(card, UiViewFactory.verticalMargin(this, 12));
            UiViewFactory.animateIn(card, cardIndex++);
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
            subjectRepository.getById(subjectId, new RepositoryCallback<Subject>() {
                @Override
                public void onSuccess(Subject subject) {
                    if (subject == null) {
                        showHome();
                        return;
                    }
                    nameInput.setText(subject.name);
                    descriptionInput.setText(subject.description);
                }
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

        if (subjectId > 0) {
            subjectRepository.getById(subjectId, new RepositoryCallback<Subject>() {
                @Override
                public void onSuccess(Subject subject) {
                    if (subject != null) {
                        subject.name = name;
                        subject.description = description;
                        subjectRepository.update(subject, new RepositoryCallback<Integer>() {
                            @Override
                            public void onSuccess(Integer result) {
                                selectedSubjectId = subjectId;
                                selectedSubject = subject;
                                showSubjectDetail();
                            }

                            @Override
                            public void onError(Exception exception) {
                                showSubjectSaveError();
                            }
                        });
                    }
                }
            });
            return;
        }

        subjectRepository.create(name, description, new RepositoryCallback<Long>() {
            @Override
            public void onSuccess(Long id) {
                selectedSubjectId = id;
                showSubjectDetail();
            }

            @Override
            public void onError(Exception exception) {
                showSubjectSaveError();
            }
        });
    }

    private void deleteSubject(long subjectId) {
        if (subjectId <= 0) return;
        subjectRepository.getById(subjectId, new RepositoryCallback<Subject>() {
            @Override
            public void onSuccess(Subject subject) {
                if (subject == null) return;
                subjectRepository.delete(subject, new RepositoryCallback<Integer>() {
                    @Override
                    public void onSuccess(Integer result) {
                        selectedSubjectId = -1L;
                        selectedSubject = null;
                        showHome();
                    }
                });
            }
        });
    }

    private void showSubjectSaveError() {
        Toast.makeText(this, "Tên môn học đã tồn tại hoặc không thể lưu",
                Toast.LENGTH_SHORT).show();
    }

    private void showDocumentForm() {
        showDocumentForm(-1L);
    }

    private void showDocumentForm(long documentId) {
        if (selectedSubjectId < 0) {
            showHome();
            return;
        }
        selectedDocumentImageUri = null;
        pendingCameraImageUri = null;
        boolean isEditing = documentId > 0;
        currentScreen = R.layout.screen_document_form;
        setContentView(R.layout.screen_document_form);
        applySystemBars();

        EditText titleInput = findViewById(R.id.inputDocumentTitle);
        TextView formTitle = findViewById(R.id.documentFormTitle);
        TextView subjectLabel = findViewById(R.id.documentSubjectLabel);
        View deleteButton = findViewById(R.id.buttonDeleteDocument);
        formTitle.setText(isEditing ? "Chỉnh sửa tài liệu" : "Thêm tài liệu");
        deleteButton.setVisibility(isEditing ? View.VISIBLE : View.GONE);
        subjectLabel.setText("Môn học: " + (selectedSubject == null ? "" : selectedSubject.name));

        if (isEditing && selectedDocument != null) {
            titleInput.setText(selectedDocument.title);
        }

        updateSelectedImageLabel();
        bindClick(R.id.backSubjectFromDocument, () -> {
            if (isEditing && selectedDocument != null) {
                showProcessDocument();
                return;
            }
            showSubjectDetail();
        });
        bindClick(R.id.buttonCamera, this::captureDocumentImage);
        bindClick(R.id.buttonGallery, this::pickDocumentImage);
        bindClick(R.id.buttonFile, this::pickDocumentFile);
        bindClick(R.id.buttonContinueOcr, () -> saveDocument(documentId, titleInput));
        bindClick(R.id.buttonDeleteDocument, this::confirmDeleteCurrentDocument);
    }

    private void saveDocument(long documentId, EditText titleInput) {
        String title = titleInput.getText().toString().trim();
        if (title.isEmpty()) {
            titleInput.setError("Nhập tên tài liệu");
            return;
        }
        String imageUri = selectedDocumentImageUri == null ? null : selectedDocumentImageUri.toString();
        if (documentId > 0) {
            updateDocument(documentId, title, imageUri);
            return;
        }

        documentRepository.create(selectedSubjectId, title, null, new RepositoryCallback<Long>() {
            @Override
            public void onSuccess(Long id) {
                if (isBlank(imageUri)) {
                    showSubjectDetail();
                    return;
                }
                documentRepository.addImage(id, imageUri, new RepositoryCallback<Long>() {
                    @Override
                    public void onSuccess(Long imageId) {
                        showSubjectDetail();
                    }

                    @Override
                    public void onError(Exception exception) {
                        Toast.makeText(MainActivity.this, "Đã lưu tài liệu nhưng chưa lưu được file", Toast.LENGTH_SHORT).show();
                        showSubjectDetail();
                    }
                });
            }

            @Override
            public void onError(Exception exception) {
                Toast.makeText(MainActivity.this, "Không thể lưu tài liệu",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateDocument(long documentId, String title, String imageUri) {
        if (selectedDocument == null || selectedDocument.id != documentId) {
            Toast.makeText(this, "Không tìm thấy tài liệu để cập nhật", Toast.LENGTH_SHORT).show();
            return;
        }

        selectedDocument.title = title;
        selectedDocument.imageUri = null;
        documentRepository.update(selectedDocument, new RepositoryCallback<Integer>() {
            @Override
            public void onSuccess(Integer result) {
                if (isBlank(imageUri)) {
                    Toast.makeText(MainActivity.this, "Đã cập nhật tài liệu", Toast.LENGTH_SHORT).show();
                    showProcessDocument();
                    return;
                }
                documentRepository.addImage(documentId, imageUri, new RepositoryCallback<Long>() {
                    @Override
                    public void onSuccess(Long imageId) {
                        selectedDocumentImageUri = null;
                        Toast.makeText(MainActivity.this, "Đã cập nhật tài liệu", Toast.LENGTH_SHORT).show();
                        showProcessDocument();
                    }

                    @Override
                    public void onError(Exception exception) {
                        Toast.makeText(MainActivity.this, "Không thể thêm file vào tài liệu", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(Exception exception) {
                Toast.makeText(MainActivity.this, "Không thể cập nhật tài liệu", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void pickDocumentImage() {
        documentImagePickerLauncher.launch(new String[]{"image/*"});
    }

    private void pickDocumentFile() {
        documentFilePickerLauncher.launch(new String[]{
                "application/pdf",
                "text/plain",
                "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.ms-powerpoint",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                "application/vnd.ms-excel",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "image/*"
        });
    }

    private void captureDocumentImage() {
        try {
            pendingCameraImageUri = createCameraImageUri();
            cameraCaptureLauncher.launch(pendingCameraImageUri);
        } catch (IOException exception) {
            Toast.makeText(this, "Không thể tạo file ảnh", Toast.LENGTH_SHORT).show();
        }
    }

    private Uri createCameraImageUri() throws IOException {
        File picturesDirectory = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (picturesDirectory == null) {
            throw new IOException("Cannot access app pictures directory");
        }
        File imageDirectory = new File(picturesDirectory, "documents");
        if (!imageDirectory.exists() && !imageDirectory.mkdirs()) {
            throw new IOException("Cannot create image directory");
        }
        File imageFile = File.createTempFile(
                "smartedu_" + System.currentTimeMillis() + "_",
                ".jpg",
                imageDirectory
        );
        return FileProvider.getUriForFile(
                this,
                getPackageName() + ".fileprovider",
                imageFile
        );
    }

    private void updateSelectedImageLabel() {
        TextView label = findViewById(R.id.selectedImageLabel);
        if (label == null) {
            return;
        }
        if (selectedDocumentImageUri == null) {
            label.setText("Chưa chọn ảnh hoặc file tài liệu");
            return;
        }
        label.setText(getDocumentAttachmentLabel(selectedDocumentImageUri.toString()));
    }

    private void openDocument(long id) {
        documentRepository.getById(id, new RepositoryCallback<StudyDocument>() {
            @Override
            public void onSuccess(StudyDocument document) {
                if (document != null) {
                    selectedDocument = document;
                    showProcessDocument();
                }
            }
        });
    }

    private void showProcessDocument() {
        currentScreen = R.layout.screen_process_document;
        setContentView(R.layout.screen_process_document);
        applySystemBars();

        TextView textDocName = findViewById(R.id.textDocName);
        TextView textContentStatus = findViewById(R.id.textContentStatus);
        ImageView imagePreview = findViewById(R.id.imageDocPreview);
        TextView imagePlaceholder = findViewById(R.id.imageDocThumb);

        if (selectedDocument != null) {
            textDocName.setText("Tài liệu: " + selectedDocument.title);
            if (isBlank(selectedDocument.ocrText)) {
                textContentStatus.setText("Chưa quét nội dung. Hãy quét tài liệu trước khi dùng AI.");
            } else {
                textContentStatus.setText("Đã có nội dung được quét. Bạn có thể xem nội dung hoặc dùng AI.");
            }
            showDocumentImage(null, imagePreview, imagePlaceholder);
            loadDocumentImages(selectedDocument.id);
        }

        bindClick(R.id.backHome, this::showSubjectDetail);
        bindClick(R.id.buttonRunOcr, this::runOcrForCurrentDocument);
        bindClick(R.id.buttonViewDocumentContent, this::showDocumentContent);
        bindClick(R.id.buttonEditDocument, () -> {
            if (selectedDocument != null) {
                showDocumentForm(selectedDocument.id);
            }
        });
        bindClick(R.id.buttonSummary, this::createSummaryFromCurrentDocument);
        bindClick(R.id.buttonQuestions, this::createQuizFromCurrentDocument);
        bindClick(R.id.buttonExplain, this::showAiChat);
        bindClick(R.id.buttonDeleteDocumentFromDetail, this::confirmDeleteCurrentDocument);
        bindClick(R.id.buttonAddMoreImages, () -> addMoreImagesPickerLauncher.launch(new String[]{
                "image/*",
                "application/pdf",
                "text/plain",
                "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.ms-powerpoint",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                "application/vnd.ms-excel",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        }));
    }

    private void createExplanationFromCurrentDocument() {
        showAiChat();
    }

    private void ensureCurrentDocumentHasAttachments(String unavailableMessage, Runnable action) {
        if (selectedDocument == null) {
            showSubjectDetail();
            return;
        }

        long documentId = selectedDocument.id;
        documentRepository.getById(documentId, new RepositoryCallback<StudyDocument>() {
            @Override
            public void onSuccess(StudyDocument document) {
                if (document == null) {
                    selectedDocument = null;
                    selectedDocumentImages = new ArrayList<>();
                    Toast.makeText(MainActivity.this, "Tài liệu đã bị xóa", Toast.LENGTH_SHORT).show();
                    showSubjectDetail();
                    return;
                }

                documentRepository.getImagesByDocumentId(documentId, new RepositoryCallback<List<StudyDocumentImage>>() {
                    @Override
                    public void onSuccess(List<StudyDocumentImage> images) {
                        selectedDocument = document;
                        selectedDocumentImages = images;
                        if (images.isEmpty()) {
                            Toast.makeText(MainActivity.this, unavailableMessage, Toast.LENGTH_SHORT).show();
                            clearGeneratedStateForCurrentDocument(false);
                            return;
                        }
                        action.run();
                    }

                    @Override
                    public void onError(Exception exception) {
                        Toast.makeText(MainActivity.this, "Không thể kiểm tra file tài liệu", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(Exception exception) {
                Toast.makeText(MainActivity.this, "Không thể kiểm tra tài liệu", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadDocumentImages(long documentId) {
        documentRepository.getImagesByDocumentId(documentId, new RepositoryCallback<List<StudyDocumentImage>>() {
            @Override
            public void onSuccess(List<StudyDocumentImage> images) {
                if (selectedDocument != null && !isBlank(selectedDocument.imageUri)) {
                    migrateLegacyDocumentAttachment(images);
                    return;
                }
                selectedDocumentImages = images;
                renderThumbnails(images);
            }
            @Override
            public void onError(Exception e) {}
        });
    }

    private void migrateLegacyDocumentAttachment(List<StudyDocumentImage> existingImages) {
        if (selectedDocument == null || isBlank(selectedDocument.imageUri)) {
            selectedDocumentImages = existingImages;
            renderThumbnails(existingImages);
            return;
        }

        String legacyUri = selectedDocument.imageUri;
        documentRepository.addImage(selectedDocument.id, legacyUri, new RepositoryCallback<Long>() {
            @Override
            public void onSuccess(Long id) {
                selectedDocument.imageUri = null;
                documentRepository.update(selectedDocument, new RepositoryCallback<Integer>() {
                    @Override
                    public void onSuccess(Integer result) {
                        loadDocumentImages(selectedDocument.id);
                    }

                    @Override
                    public void onError(Exception exception) {
                        loadDocumentImages(selectedDocument.id);
                    }
                });
            }

            @Override
            public void onError(Exception exception) {
                selectedDocumentImages = existingImages;
                renderThumbnails(existingImages);
            }
        });
    }

    private void renderThumbnails(List<StudyDocumentImage> images) {
        LinearLayout container = findViewById(R.id.imageThumbnailsContainer);
        if (container == null) return;
        
        // Remove all except the add button
        View addButton = findViewById(R.id.buttonAddMoreImages);
        container.removeAllViews();
        if (addButton != null) container.addView(addButton);

        if (images.isEmpty()) {
            setStudyActionsEnabled(false);
            showDocumentImage(null, findViewById(R.id.imageDocPreview), findViewById(R.id.imageDocThumb));
            return;
        }

        setStudyActionsEnabled(true);
        showDocumentImage(images.get(0).imageUri, findViewById(R.id.imageDocPreview), findViewById(R.id.imageDocThumb));

        for (StudyDocumentImage image : images) {
            ImageView thumb = new ImageView(this);
            int size = UiViewFactory.dp(this, 60);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMargins(0, 0, UiViewFactory.dp(this, 8), 0);
            thumb.setLayoutParams(lp);
            thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumb.setBackgroundResource(R.drawable.bg_pill);
            thumb.setClipToOutline(true);
            
            try {
                thumb.setImageURI(Uri.parse(image.imageUri));
            } catch (Exception e) {
                thumb.setImageResource(android.R.drawable.ic_menu_report_image);
            }

            thumb.setOnClickListener(v -> showDocumentImage(image.imageUri, findViewById(R.id.imageDocPreview), findViewById(R.id.imageDocThumb)));
            
            thumb.setOnLongClickListener(v -> {
                confirmDeleteAttachment(image);
                return true;
            });

            container.addView(thumb);
            UiViewFactory.applyPressEffect(thumb);
            UiViewFactory.animateIn(thumb, container.getChildCount());
        }
    }

    private void setStudyActionsEnabled(boolean enabled) {
        int[] actionIds = {
                R.id.buttonRunOcr,
                R.id.buttonSummary,
                R.id.buttonQuestions,
                R.id.buttonExplain
        };
        float alpha = enabled ? 1f : 0.45f;
        for (int actionId : actionIds) {
            View action = findViewById(actionId);
            if (action != null) {
                action.setEnabled(enabled);
                action.animate().alpha(alpha).setDuration(160).start();
            }
        }
    }

    private void showDocumentContent() {
        if (selectedDocument == null) {
            showSubjectDetail();
            return;
        }

        currentScreen = R.layout.screen_document_content;
        setContentView(R.layout.screen_document_content);
        applySystemBars();

        TextView title = findViewById(R.id.textDocumentContentTitle);
        TextView content = findViewById(R.id.textDocumentContent);
        title.setText("Nội dung: " + selectedDocument.title);
        content.setText(isBlank(selectedDocument.ocrText)
                ? "Chưa có nội dung được quét."
                : selectedDocument.ocrText);

        bindClick(R.id.backProcessFromContent, this::showProcessDocument);
        bindClick(R.id.buttonEditDocumentContent, this::showEditDocumentContent);
    }

    private void showEditDocumentContent() {
        if (selectedDocument == null) {
            showSubjectDetail();
            return;
        }

        currentScreen = R.layout.screen_document_content_edit;
        setContentView(R.layout.screen_document_content_edit);
        applySystemBars();

        TextView title = findViewById(R.id.textEditContentTitle);
        EditText contentInput = findViewById(R.id.editDocumentContent);
        title.setText("Sửa nội dung: " + selectedDocument.title);
        contentInput.setText(selectedDocument.ocrText == null ? "" : selectedDocument.ocrText);

        bindClick(R.id.backContentFromEdit, this::showDocumentContent);
        bindClick(R.id.buttonSaveDocumentContent, () -> saveEditedDocumentContent(contentInput));
    }

    private void saveEditedDocumentContent(EditText contentInput) {
        if (selectedDocument == null) {
            return;
        }

        selectedDocument.ocrText = contentInput.getText().toString().trim();
        documentRepository.update(selectedDocument, new RepositoryCallback<Integer>() {
            @Override
            public void onSuccess(Integer result) {
                Toast.makeText(MainActivity.this, "Đã lưu nội dung tài liệu", Toast.LENGTH_SHORT).show();
                showDocumentContent();
            }

            @Override
            public void onError(Exception exception) {
                Toast.makeText(MainActivity.this, "Không thể lưu nội dung", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showAiChat() {
        if (selectedDocument == null) {
            return;
        }

        ensureCurrentDocumentHasAttachments(
                "Tài liệu không còn file đính kèm, không thể hỏi đáp AI",
                this::openAiChatScreen
        );
    }

    private void openAiChatScreen() {
        if (selectedDocument == null) {
            return;
        }

        String documentText = selectedDocument.ocrText == null ? "" : selectedDocument.ocrText.trim();
        if (isBlank(documentText)) {
            Toast.makeText(this, "Chưa có nội dung tài liệu để hỏi đáp", Toast.LENGTH_SHORT).show();
            return;
        }

        currentScreen = R.layout.screen_ai_chat;
        setContentView(R.layout.screen_ai_chat);
        applySystemBars();

        TextView title = findViewById(R.id.textAiChatTitle);
        title.setText("Hỏi đáp: " + selectedDocument.title);
        addChatMessage("AI", "Bạn có thể hỏi về nội dung tài liệu này. Mình sẽ trả lời dựa trên phần đã quét.", false);

        EditText input = findViewById(R.id.inputAiQuestion);
        bindClick(R.id.backProcessFromChat, this::showProcessDocument);
        bindClick(R.id.buttonSendAiQuestion, () -> sendAiQuestion(input));
    }

    private void sendAiQuestion(EditText input) {
        if (selectedDocument == null) {
            return;
        }

        String question = input.getText().toString().trim();
        String documentText = selectedDocument.ocrText == null ? "" : selectedDocument.ocrText.trim();
        if (isBlank(question)) {
            input.setError("Nhập câu hỏi");
            return;
        }
        if (isBlank(documentText)) {
            Toast.makeText(this, "Chưa có nội dung tài liệu để hỏi đáp", Toast.LENGTH_SHORT).show();
            return;
        }

        input.setText("");
        addChatMessage("Bạn", question, true);
        addChatMessage("AI", "Đang suy nghĩ...", false);

        geminiService.askAboutDocument(documentText, question, new GeminiService.GeminiCallback() {
            @Override
            public void onSuccess(String text) {
                runOnUiThread(() -> {
                    removeLastChatMessage();
                    addChatMessage("AI", isBlank(text) ? "Mình chưa tìm thấy câu trả lời phù hợp trong tài liệu." : text, false);
                });
            }

            @Override
            public void onError(Exception exception) {
                runOnUiThread(() -> {
                    removeLastChatMessage();
                    addChatMessage("AI", "Không thể trả lời lúc này. Kiểm tra API key hoặc kết nối mạng.", false);
                });
            }
        });
    }

    private void addChatMessage(String sender, String message, boolean isUser) {
        LinearLayout container = findViewById(R.id.chatMessagesContainer);
        ScrollView scrollView = findViewById(R.id.chatScrollView);
        if (container == null) {
            return;
        }

        MaterialCardView card = UiViewFactory.createCard(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(
                UiViewFactory.dp(this, 14),
                UiViewFactory.dp(this, 12),
                UiViewFactory.dp(this, 14),
                UiViewFactory.dp(this, 12)
        );

        TextView senderView = UiViewFactory.createText(
                this,
                sender,
                13,
                isUser ? R.color.brand_blue_dark : R.color.ink_muted,
                true
        );
        TextView messageView = UiViewFactory.createText(this, message, 15, R.color.ink, false);
        messageView.setPadding(0, UiViewFactory.dp(this, 6), 0, 0);

        content.addView(senderView);
        content.addView(messageView);
        card.addView(content);
        container.addView(card, UiViewFactory.verticalMargin(this, 10));
        UiViewFactory.animateIn(card, container.getChildCount());

        if (scrollView != null) {
            scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
        }
    }

    private void confirmDeleteAttachment(StudyDocumentImage image) {
        new AlertDialog.Builder(this)
                .setTitle("Xóa tài liệu đính kèm")
                .setMessage("Bạn có muốn xóa file/ảnh này khỏi bài học không?")
                .setPositiveButton("Xóa", (dialog, which) -> deleteAttachment(image))
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void deleteAttachment(StudyDocumentImage image) {
        documentRepository.deleteImage(image, new RepositoryCallback<Integer>() {
            @Override
            public void onSuccess(Integer result) {
                Toast.makeText(MainActivity.this, "Đã xóa tài liệu đính kèm", Toast.LENGTH_SHORT).show();
                if (selectedDocument != null) {
                    refreshAttachmentsAfterDelete(selectedDocument.id);
                }
            }

            @Override
            public void onError(Exception exception) {
                Toast.makeText(MainActivity.this, "Không thể xóa tài liệu đính kèm", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void refreshAttachmentsAfterDelete(long documentId) {
        documentRepository.getImagesByDocumentId(documentId, new RepositoryCallback<List<StudyDocumentImage>>() {
            @Override
            public void onSuccess(List<StudyDocumentImage> images) {
                selectedDocumentImages = images;
                if (images.isEmpty()) {
                    clearGeneratedStateForCurrentDocument(true);
                    return;
                }
                renderThumbnails(images);
            }

            @Override
            public void onError(Exception exception) {
                loadDocumentImages(documentId);
            }
        });
    }

    private void clearGeneratedStateForCurrentDocument(boolean showSuccessMessage) {
        if (selectedDocument == null) {
            return;
        }

        selectedDocument.ocrText = "";
        latestDisplayedSummary = null;
        currentQuizQuestions.clear();
        selectedQuizAnswers.clear();
        latestQuizAttempt = null;
        long documentId = selectedDocument.id;

        documentRepository.update(selectedDocument, new RepositoryCallback<Integer>() {
            @Override
            public void onSuccess(Integer result) {
                studyRepository.clearGeneratedDataByDocumentId(documentId, new RepositoryCallback<Integer>() {
                    @Override
                    public void onSuccess(Integer deletedCount) {
                        if (showSuccessMessage) {
                            Toast.makeText(
                                    MainActivity.this,
                                    "Đã xóa file cuối cùng và dọn OCR/AI/quiz của tài liệu",
                                    Toast.LENGTH_SHORT
                            ).show();
                        }
                        showProcessDocument();
                    }

                    @Override
                    public void onError(Exception exception) {
                        Toast.makeText(MainActivity.this, "Đã xóa file nhưng chưa dọn được dữ liệu AI/quiz", Toast.LENGTH_SHORT).show();
                        showProcessDocument();
                    }
                });
            }

            @Override
            public void onError(Exception exception) {
                Toast.makeText(MainActivity.this, "Không thể cập nhật trạng thái tài liệu", Toast.LENGTH_SHORT).show();
                showProcessDocument();
            }
        });
    }

    private void removeLastChatMessage() {
        LinearLayout container = findViewById(R.id.chatMessagesContainer);
        if (container != null && container.getChildCount() > 0) {
            container.removeViewAt(container.getChildCount() - 1);
        }
    }

    private void confirmDeleteCurrentDocument() {
        if (selectedDocument == null) {
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Xóa tài liệu")
                .setMessage("Bạn có chắc muốn xóa tài liệu \"" + selectedDocument.title + "\" không?")
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Xóa", (dialog, which) -> deleteCurrentDocument())
                .show();
    }

    private void deleteCurrentDocument() {
        if (selectedDocument == null) {
            return;
        }

        documentRepository.delete(selectedDocument, new RepositoryCallback<Integer>() {
            @Override
            public void onSuccess(Integer result) {
                selectedDocument = null;
                Toast.makeText(MainActivity.this, "Đã xóa tài liệu", Toast.LENGTH_SHORT).show();
                showSubjectDetail();
            }

            @Override
            public void onError(Exception exception) {
                Toast.makeText(MainActivity.this, "Không thể xóa tài liệu", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void runOcrForCurrentDocument() {
        if (selectedDocument == null) {
            return;
        }

        List<String> attachmentUris = new ArrayList<>();
        for (StudyDocumentImage img : selectedDocumentImages) {
            if (!isBlank(img.imageUri)) {
                attachmentUris.add(img.imageUri);
            }
        }

        if (attachmentUris.isEmpty()) {
            Toast.makeText(this, "Tài liệu chưa có ảnh/file hợp lệ để quét", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "Đang quét nội dung tài liệu...", Toast.LENGTH_SHORT).show();
        documentTextScannerService.scanAttachments(
                this,
                attachmentUris,
                new DocumentTextScannerService.ScanCallback() {
                    @Override
                    public void onSuccess(String text) {
                        runOnUiThread(() -> handleOcrResult(text));
                    }

                    @Override
                    public void onError(Exception exception) {
                        runOnUiThread(() -> Toast.makeText(
                                MainActivity.this,
                                exception.getMessage() == null
                                        ? "Không thể quét nội dung tài liệu"
                                        : exception.getMessage(),
                                Toast.LENGTH_SHORT
                        ).show());
                    }
                }
        );
    }

    private void handleOcrResult(String recognizedText) {
        if (isBlank(recognizedText)) {
            Toast.makeText(this, "Ảnh không có văn bản nhận dạng được", Toast.LENGTH_SHORT).show();
            return;
        }

        selectedDocument.ocrText = recognizedText;
        documentRepository.update(selectedDocument, new RepositoryCallback<Integer>() {
            @Override
            public void onSuccess(Integer result) {
                Toast.makeText(MainActivity.this, "Quét nội dung tài liệu thành công", Toast.LENGTH_SHORT).show();
                showProcessDocument();
            }

            @Override
            public void onError(Exception exception) {
                Toast.makeText(MainActivity.this, "Quét được nội dung nhưng chưa lưu được", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showDocumentImage(String imageUri, ImageView imagePreview, TextView imagePlaceholder) {
        if (isBlank(imageUri)) {
            imagePreview.setVisibility(View.GONE);
            imagePlaceholder.setVisibility(View.VISIBLE);
            imagePlaceholder.setText("Chưa có file tài liệu");
            return;
        }

        if (!isImageAttachment(imageUri)) {
            imagePreview.setVisibility(View.GONE);
            imagePlaceholder.setVisibility(View.VISIBLE);
            imagePlaceholder.setText(getDocumentAttachmentLabel(imageUri));
            return;
        }

        try {
            imagePreview.setImageURI(Uri.parse(imageUri));
            imagePreview.setVisibility(View.VISIBLE);
            imagePlaceholder.setVisibility(View.GONE);
        } catch (Exception exception) {
            imagePreview.setVisibility(View.GONE);
            imagePlaceholder.setVisibility(View.VISIBLE);
            imagePlaceholder.setText("Không thể hiển thị ảnh");
        }
    }

    private boolean isImageAttachment(String attachmentUri) {
        String mimeType = getAttachmentMimeType(attachmentUri);
        if (!isBlank(mimeType)) {
            return mimeType.startsWith("image/");
        }
        String lowerUri = attachmentUri == null ? "" : attachmentUri.toLowerCase(Locale.US);
        return lowerUri.endsWith(".jpg")
                || lowerUri.endsWith(".jpeg")
                || lowerUri.endsWith(".png")
                || lowerUri.endsWith(".webp");
    }

    private String getDocumentAttachmentLabel(String attachmentUri) {
        if (isBlank(attachmentUri)) {
            return "Chưa có file";
        }
        String mimeType = getAttachmentMimeType(attachmentUri);
        if (!isBlank(mimeType)) {
            if (mimeType.startsWith("image/")) return "Có ảnh tài liệu";
            if ("application/pdf".equals(mimeType)) return "Có file PDF";
            if (mimeType.contains("wordprocessingml") || "application/msword".equals(mimeType)) return "Có file Word";
            if (mimeType.contains("presentationml") || mimeType.contains("powerpoint")) return "Có file PowerPoint";
            if (mimeType.contains("spreadsheetml") || mimeType.contains("excel")) return "Có file Excel";
            if (mimeType.startsWith("text/")) return "Có file văn bản";
        }
        return "Có file tài liệu";
    }

    private String getAttachmentMimeType(String attachmentUri) {
        if (isBlank(attachmentUri)) {
            return "";
        }
        try {
            String mimeType = getContentResolver().getType(Uri.parse(attachmentUri));
            return mimeType == null ? "" : mimeType;
        } catch (Exception exception) {
            return "";
        }
    }

    private void saveDocumentContent() {
        if (selectedDocument == null) return;
        documentRepository.update(selectedDocument, new RepositoryCallback<Integer>() {
            @Override
            public void onSuccess(Integer result) {
                Toast.makeText(MainActivity.this, "Đã lưu nội dung tài liệu!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showSummary() {
        currentScreen = R.layout.screen_summary_explain;
        setContentView(R.layout.screen_summary_explain);
        applySystemBars();
        bindClick(R.id.backProcess, this::showProcessDocument);
        bindClick(R.id.buttonStartQuiz, this::showQuestionBank);
        bindClick(R.id.buttonDeleteSummary, this::confirmDeleteDisplayedSummary);

        loadSummaryFromDatabase();
    }

    private void loadSummaryFromDatabase(){
        if (selectedDocument == null) return;

        studyRepository.getSummariesByDocumentId(selectedDocument.id, new RepositoryCallback<List<StudySummary>>(){
            @Override
            public void onSuccess(List<StudySummary> summaries){
                TextView summaryText = findViewById(R.id.textSummaryContent);
                TextView summaryTitle = findViewById(R.id.textSummaryTitle);
                TextView resultHeader = findViewById(R.id.textResultHeader);

                if(summaries.isEmpty()){
                    latestDisplayedSummary = null;
                    summaryText.setText("Chưa có nội dung AI. Hãy tạo tóm tắt hoặc giải thích!");
                    return;
                }

                StudySummary latestSummary = summaries.get(0);
                latestDisplayedSummary = latestSummary;
                summaryText.setText(latestSummary.content);
                
                // Heuristic to decide title
                if (latestSummary.content.contains("•") || latestSummary.content.length() < 500) {
                    summaryTitle.setText("Tóm tắt tài liệu");
                    resultHeader.setText("Tóm tắt ý chính");
                } else {
                    summaryTitle.setText("Giải thích chuyên sâu");
                    resultHeader.setText("Nội dung chi tiết");
                }
            }

            @Override
            public void onError(Exception exception){
                Toast.makeText(MainActivity.this, "Lỗi khi lấy tóm tắt", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void createSummaryFromCurrentDocument(){
        if (selectedDocument == null) return;

        ensureCurrentDocumentHasAttachments(
                "Tài liệu không còn file đính kèm, không thể tóm tắt AI",
                this::createSummaryAfterValidation
        );
    }

    private void createSummaryAfterValidation() {
        if (selectedDocument == null) return;

        String ocrText = selectedDocument.ocrText == null ? "" : selectedDocument.ocrText.trim();

        if(isBlank(ocrText)){
            Toast.makeText(this, "Chưa có nội dung tài liệu để tóm tắt", Toast.LENGTH_SHORT).show();
            return;
        }

        long documentId = selectedDocument.id;
        documentRepository.update(selectedDocument, new RepositoryCallback<Integer>() {
            @Override
            public void onSuccess(Integer result) {
                requestGeminiSummary(ocrText, documentId);
            }

            @Override
            public void onError(Exception exception) {
                Toast.makeText(MainActivity.this, "Không thể lưu OCR trước khi tóm tắt", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void requestGeminiSummary(String ocrText, long documentId) {
        Toast.makeText(this, "Đang tóm tắt bằng Gemini...", Toast.LENGTH_SHORT).show();

        geminiService.summarize(
                ocrText,
                new GeminiService.GeminiCallback() {
                    @Override
                    public void onSuccess(String summaryContent) {
                        runOnUiThread(() -> {
                            if (isBlank(summaryContent)) {
                                Toast.makeText(
                                        MainActivity.this,
                                        "Gemini trả nội dung rỗng, đã dùng tóm tắt tạm",
                                        Toast.LENGTH_SHORT
                                ).show();
                                saveGeneratedSummary(buildMockSummary(ocrText), documentId);
                                return;
                            }
                            saveGeneratedSummary(summaryContent, documentId);
                        });
                    }

                    @Override
                    public void onError(Exception exception) {
                        runOnUiThread(() -> {
                            Toast.makeText(
                                    MainActivity.this,
                                    "Gemini lỗi, đã dùng tóm tắt tạm",
                                    Toast.LENGTH_SHORT
                            ).show();
                            saveGeneratedSummary(buildMockSummary(ocrText), documentId);
                        });
                    }
                }
        );
    }

    private void createQuizFromCurrentDocument() {
    if (selectedDocument == null) {
        return;
    }

    ensureCurrentDocumentHasAttachments(
            "Tài liệu không còn file đính kèm, không thể tạo quiz",
            this::createQuizAfterValidation
    );
    }

    private void createQuizAfterValidation() {
    if (selectedDocument == null) {
        return;
    }

    String ocrText = selectedDocument.ocrText == null ? "" : selectedDocument.ocrText.trim();

    if (isBlank(ocrText)) {
        Toast.makeText(this, "Chưa có nội dung tài liệu để tạo quiz", Toast.LENGTH_SHORT).show();
        return;
    }

    long documentId = selectedDocument.id;
    documentRepository.update(selectedDocument, new RepositoryCallback<Integer>() {
        @Override
        public void onSuccess(Integer result) {
            requestGeminiQuiz(ocrText, documentId);
        }

        @Override
        public void onError(Exception exception) {
            Toast.makeText(MainActivity.this, "Không thể lưu OCR trước khi tạo quiz", Toast.LENGTH_SHORT).show();
        }
    });
    }

    private void requestGeminiQuiz(String ocrText, long documentId) {
        Toast.makeText(this, "Đang tạo quiz bằng Gemini...", Toast.LENGTH_SHORT).show();

        geminiService.generateQuiz(
                ocrText,
                new GeminiService.GeminiCallback() {
                    @Override
                    public void onSuccess(String quizJson) {
                        runOnUiThread(() -> saveGeneratedQuestions(quizJson, documentId, ocrText));
                    }

                    @Override
                    public void onError(Exception exception) {
                        runOnUiThread(() -> {
                            Toast.makeText(
                                    MainActivity.this,
                                    "Gemini lỗi, đã dùng câu hỏi mẫu",
                                    Toast.LENGTH_SHORT
                            ).show();
                            saveGeneratedQuestions(
                                    quizParser.buildFallbackQuestions(ocrText, documentId)
                            );
                        });
                    }
                }
        );
    }

    private void saveGeneratedQuestions(String quizJson) {
    if (selectedDocument == null) {
        return;
    }
    saveGeneratedQuestions(quizJson, selectedDocument.id, selectedDocument.ocrText);
    }

    private void saveGeneratedQuestions(String quizJson, long documentId, String fallbackText) {
    if (selectedDocument == null) {
        return;
    }

    try {
        List<StudyQuestion> questions = quizParser.parse(quizJson, documentId);
        if (questions.isEmpty()) {
            Toast.makeText(this, "Gemini trả danh sách rỗng, đã dùng câu hỏi mẫu", Toast.LENGTH_SHORT).show();
            questions = quizParser.buildFallbackQuestions(fallbackText, documentId);
        }

        saveGeneratedQuestions(questions, documentId);
    } catch (Exception exception) {
        android.util.Log.e("GeminiQuiz", "Raw quiz JSON: " + quizJson, exception);
        Toast.makeText(this, "Không thể đọc JSON Gemini, đã dùng câu hỏi mẫu", Toast.LENGTH_SHORT).show();
        saveGeneratedQuestions(quizParser.buildFallbackQuestions(fallbackText, documentId));
    }
    }

    private void saveGeneratedQuestions(List<StudyQuestion> questions) {
        if (selectedDocument == null) {
            return;
        }
        saveGeneratedQuestions(questions, selectedDocument.id);
    }

    private void saveGeneratedQuestions(List<StudyQuestion> questions, long documentId) {
        if (selectedDocument == null) {
            return;
        }

        if (questions.isEmpty()) {
            Toast.makeText(this, "Không tạo được câu hỏi", Toast.LENGTH_SHORT).show();
            return;
        }

        studyRepository.replaceQuestions(
                documentId,
                questions,
                new RepositoryCallback<Integer>() {
                    @Override
                    public void onSuccess(Integer result) {
                        renderQuestionBankScreen();
                    }

                    @Override
                    public void onError(Exception exception) {
                        Toast.makeText(MainActivity.this, "Không thể lưu câu hỏi", Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private void saveGeneratedSummary(String summaryContent) {
    if (selectedDocument == null) {
        return;
    }
    saveGeneratedSummary(summaryContent, selectedDocument.id);
    }

    private void saveGeneratedSummary(String summaryContent, long documentId) {
    if (isBlank(summaryContent)) {
        Toast.makeText(this, "Không tạo được nội dung tóm tắt", Toast.LENGTH_SHORT).show();
        return;
    }

    studyRepository.createSummary(
            documentId,
            summaryContent,
            new RepositoryCallback<Long>() {
                @Override
                public void onSuccess(Long result) {
                    showSummary();
                }

                @Override
                public void onError(Exception exception) {
                    Toast.makeText(MainActivity.this, "Không thể lưu tóm tắt", Toast.LENGTH_SHORT).show();
                }
            }
    );
    }

    private void showQuestionBank() {
        if (selectedDocument == null) {
            return;
        }

        ensureCurrentDocumentHasAttachments(
                "Tài liệu không còn file đính kèm, không thể mở bộ câu hỏi",
                this::renderQuestionBankScreen
        );
    }

    private void renderQuestionBankScreen() {
        currentScreen = R.layout.screen_question_bank;
        setContentView(R.layout.screen_question_bank);
        applySystemBars();
        bindClick(R.id.backProcessFromBank, this::showProcessDocument);
        bindClick(R.id.buttonStartQuizFromBank, this::showQuestions);
        bindClick(R.id.buttonRegenerateQuestions, this::createQuizFromCurrentDocument);
        loadQuestionBankFromDatabase();
    }

    private void loadQuestionBankFromDatabase() {
        if (selectedDocument == null) {
            return;
        }

        TextView subtitle = findViewById(R.id.questionBankSubtitle);
        subtitle.setText(selectedDocument.title + " · Danh sách câu hỏi đã lưu");

        studyRepository.getQuestionsByDocumentId(
                selectedDocument.id,
                new RepositoryCallback<List<StudyQuestion>>() {
                    @Override
                    public void onSuccess(List<StudyQuestion> questions) {
                        renderQuestionBank(questions);
                    }

                    @Override
                    public void onError(Exception exception) {
                        Toast.makeText(MainActivity.this, "Không thể tải câu hỏi", Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private void renderQuestionBank(List<StudyQuestion> questions) {
        LinearLayout container = findViewById(R.id.questionBankContainer);
        container.removeAllViews();

        if (questions.isEmpty()) {
            TextView empty = UiViewFactory.createText(
                    this,
                    "Chưa có câu hỏi cho tài liệu này.",
                    15,
                    R.color.ink_muted,
                    false
            );
            container.addView(empty);
            return;
        }

        for (StudyQuestion question : questions) {
            MaterialCardView card = UiViewFactory.createCard(this);
            card.setClickable(true);
            card.setFocusable(true);
            card.setOnClickListener(v -> showQuestionEditor(question));

            LinearLayout content = new LinearLayout(this);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(
                    UiViewFactory.dp(this, 16),
                    UiViewFactory.dp(this, 14),
                    UiViewFactory.dp(this, 16),
                    UiViewFactory.dp(this, 14)
            );

            TextView order = UiViewFactory.createText(
                    this,
                    "Câu " + question.questionOrder,
                    14,
                    R.color.brand_blue_dark,
                    true
            );

            TextView text = UiViewFactory.createText(
                    this,
                    question.questionText,
                    16,
                    R.color.ink,
                    true
            );
            text.setPadding(0, UiViewFactory.dp(this, 6), 0, 0);

            TextView options = UiViewFactory.createText(
                    this,
                    "A. " + question.optionA + "\n"
                            + "B. " + question.optionB + "\n"
                            + "C. " + question.optionC + "\n"
                            + "D. " + question.optionD,
                    14,
                    R.color.ink,
                    false
            );
            options.setPadding(0, UiViewFactory.dp(this, 10), 0, 0);

            TextView answer = UiViewFactory.createText(
                    this,
                    "Đáp án đúng: " + question.correctOption,
                    13,
                    R.color.ink_muted,
                    false
            );
            answer.setPadding(0, UiViewFactory.dp(this, 10), 0, 0);

            content.addView(order);
            content.addView(text);
            content.addView(options);
            content.addView(answer);

            if (!isBlank(question.explanation)) {
                TextView explanation = UiViewFactory.createText(
                        this,
                        "Giải thích: " + question.explanation,
                        13,
                        R.color.ink_muted,
                        false
                );
                explanation.setPadding(0, UiViewFactory.dp(this, 6), 0, 0);
                content.addView(explanation);
            }

            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.setPadding(0, UiViewFactory.dp(this, 12), 0, 0);

            TextView editAction = UiViewFactory.createText(this, "Sửa", 13, R.color.brand_blue_dark, true);
            editAction.setBackgroundResource(R.drawable.bg_action_chip_blue);
            editAction.setGravity(android.view.Gravity.CENTER);
            editAction.setPadding(UiViewFactory.dp(this, 16), UiViewFactory.dp(this, 8),
                    UiViewFactory.dp(this, 16), UiViewFactory.dp(this, 8));
            UiViewFactory.applyPressEffect(editAction);
            editAction.setOnClickListener(v -> showQuestionEditor(question));

            TextView deleteAction = UiViewFactory.createText(this, "Xóa", 13, R.color.danger, true);
            deleteAction.setBackgroundResource(R.drawable.bg_action_chip_danger);
            deleteAction.setGravity(android.view.Gravity.CENTER);
            deleteAction.setPadding(UiViewFactory.dp(this, 16), UiViewFactory.dp(this, 8),
                    UiViewFactory.dp(this, 16), UiViewFactory.dp(this, 8));
            UiViewFactory.applyPressEffect(deleteAction);
            deleteAction.setOnClickListener(v -> confirmDeleteQuestion(question));

            LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
            );
            actionParams.setMargins(0, 0, UiViewFactory.dp(this, 8), 0);
            actions.addView(editAction, actionParams);

            LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
            );
            deleteParams.setMargins(UiViewFactory.dp(this, 8), 0, 0, 0);
            actions.addView(deleteAction, deleteParams);
            content.addView(actions);

            card.addView(content);
            container.addView(card, UiViewFactory.verticalMargin(this, 12));
            UiViewFactory.animateIn(card, container.getChildCount());
        }
    }

    private void showQuestionEditor(StudyQuestion question) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int padding = UiViewFactory.dp(this, 8);
        form.setPadding(padding, padding, padding, 0);

        EditText questionInput = createDialogInput("Câu hỏi", question.questionText);
        EditText optionAInput = createDialogInput("Đáp án A", question.optionA);
        EditText optionBInput = createDialogInput("Đáp án B", question.optionB);
        EditText optionCInput = createDialogInput("Đáp án C", question.optionC);
        EditText optionDInput = createDialogInput("Đáp án D", question.optionD);
        EditText correctInput = createDialogInput("Đáp án đúng (A/B/C/D)", question.correctOption);
        EditText explanationInput = createDialogInput("Giải thích", question.explanation);

        form.addView(questionInput);
        form.addView(optionAInput);
        form.addView(optionBInput);
        form.addView(optionCInput);
        form.addView(optionDInput);
        form.addView(correctInput);
        form.addView(explanationInput);

        new AlertDialog.Builder(this)
                .setTitle("Sửa câu hỏi")
                .setView(form)
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Lưu", (dialog, which) -> {
                    question.questionText = questionInput.getText().toString().trim();
                    question.optionA = optionAInput.getText().toString().trim();
                    question.optionB = optionBInput.getText().toString().trim();
                    question.optionC = optionCInput.getText().toString().trim();
                    question.optionD = optionDInput.getText().toString().trim();
                    question.correctOption = normalizeCorrectOption(correctInput.getText().toString());
                    question.explanation = explanationInput.getText().toString().trim();
                    updateQuestion(question);
                })
                .show();
    }

    private EditText createDialogInput(String hint, String value) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setText(value == null ? "" : value);
        input.setSingleLine(false);
        input.setMinLines(1);
        input.setTextColor(getColor(R.color.ink));
        input.setTextSize(14);
        return input;
    }

    private void updateQuestion(StudyQuestion question) {
        if (isBlank(question.questionText)
                || isBlank(question.optionA)
                || isBlank(question.optionB)
                || isBlank(question.optionC)
                || isBlank(question.optionD)) {
            Toast.makeText(this, "Câu hỏi và đáp án không được để trống", Toast.LENGTH_SHORT).show();
            return;
        }

        studyRepository.updateQuestion(question, new RepositoryCallback<Integer>() {
            @Override
            public void onSuccess(Integer result) {
                Toast.makeText(MainActivity.this, "Đã cập nhật câu hỏi", Toast.LENGTH_SHORT).show();
                loadQuestionBankFromDatabase();
            }

            @Override
            public void onError(Exception exception) {
                Toast.makeText(MainActivity.this, "Không thể cập nhật câu hỏi", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void confirmDeleteDisplayedSummary() {
        if (latestDisplayedSummary == null) {
            Toast.makeText(this, "Chưa có kết quả AI để xóa", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Xóa kết quả AI")
                .setMessage("Bạn có chắc muốn xóa kết quả AI đang xem không?")
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Xóa", (dialog, which) -> deleteDisplayedSummary())
                .show();
    }

    private void deleteDisplayedSummary() {
        if (latestDisplayedSummary == null) {
            return;
        }

        studyRepository.deleteSummary(latestDisplayedSummary, new RepositoryCallback<Integer>() {
            @Override
            public void onSuccess(Integer result) {
                latestDisplayedSummary = null;
                Toast.makeText(MainActivity.this, "Đã xóa kết quả AI", Toast.LENGTH_SHORT).show();
                showProcessDocument();
            }

            @Override
            public void onError(Exception exception) {
                Toast.makeText(MainActivity.this, "Không thể xóa kết quả AI", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void confirmDeleteQuestion(StudyQuestion question) {
        new AlertDialog.Builder(this)
                .setTitle("Xóa câu hỏi")
                .setMessage("Bạn có chắc muốn xóa câu hỏi này không?")
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Xóa", (dialog, which) -> deleteQuestion(question))
                .show();
    }

    private void deleteQuestion(StudyQuestion question) {
        studyRepository.deleteQuestion(question, new RepositoryCallback<Integer>() {
            @Override
            public void onSuccess(Integer result) {
                Toast.makeText(MainActivity.this, "Đã xóa câu hỏi", Toast.LENGTH_SHORT).show();
                loadQuestionBankFromDatabase();
            }

            @Override
            public void onError(Exception exception) {
                Toast.makeText(MainActivity.this, "Không thể xóa câu hỏi", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showQuestions() {
        currentScreen = R.layout.screen_questions;
        setContentView(R.layout.screen_questions);
        applySystemBars();
        bindClick(R.id.backProcessFromQuestions, this::showProcessDocument);
        bindClick(R.id.buttonCheckAnswers, this::submitQuizAnswers);
        loadQuizQuestionsFromDatabase();
    }

    private void loadQuizQuestionsFromDatabase() {
        if (selectedDocument == null) {
            return;
        }

        TextView title = findViewById(R.id.quizTitle);
        title.setText("Quiz: " + selectedDocument.title);

        studyRepository.getQuestionsByDocumentId(
                selectedDocument.id,
                new RepositoryCallback<List<StudyQuestion>>() {
                    @Override
                    public void onSuccess(List<StudyQuestion> questions) {
                        currentQuizQuestions = questions;
                        selectedQuizAnswers.clear();
                        renderQuizQuestions(questions);
                    }

                    @Override
                    public void onError(Exception exception) {
                        Toast.makeText(MainActivity.this, "Không thể tải quiz", Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private void renderQuizQuestions(List<StudyQuestion> questions) {
        TextView subtitle = findViewById(R.id.quizSubtitle);
        LinearLayout container = findViewById(R.id.quizQuestionsContainer);
        container.removeAllViews();

        subtitle.setText(questions.size() + " câu hỏi");

        if (questions.isEmpty()) {
            TextView empty = UiViewFactory.createText(
                    this,
                    "Chưa có câu hỏi để làm quiz.",
                    15,
                    R.color.ink_muted,
                    false
            );
            container.addView(empty);
            return;
        }

        for (int index = 0; index < questions.size(); index++) {
            StudyQuestion question = questions.get(index);
            MaterialCardView card = UiViewFactory.createCard(this);
            card.setClickable(false);
            card.setFocusable(false);

            LinearLayout content = new LinearLayout(this);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(
                    UiViewFactory.dp(this, 18),
                    UiViewFactory.dp(this, 16),
                    UiViewFactory.dp(this, 18),
                    UiViewFactory.dp(this, 16)
            );

            TextView number = UiViewFactory.createText(
                    this,
                    "Câu " + (index + 1) + " / " + questions.size(),
                    13,
                    R.color.brand_blue_dark,
                    true
            );

            TextView questionText = UiViewFactory.createText(
                    this,
                    question.questionText,
                    17,
                    R.color.ink,
                    true
            );
            questionText.setPadding(0, UiViewFactory.dp(this, 8), 0, 0);

            RadioGroup answers = new RadioGroup(this);
            answers.setOrientation(RadioGroup.VERTICAL);
            answers.setPadding(0, UiViewFactory.dp(this, 12), 0, 0);

            RadioButton optionA = createAnswerRadioButton("A. " + question.optionA);
            RadioButton optionB = createAnswerRadioButton("B. " + question.optionB);
            RadioButton optionC = createAnswerRadioButton("C. " + question.optionC);
            RadioButton optionD = createAnswerRadioButton("D. " + question.optionD);

            optionA.setTag("A");
            optionB.setTag("B");
            optionC.setTag("C");
            optionD.setTag("D");

            answers.addView(optionA);
            answers.addView(optionB);
            answers.addView(optionC);
            answers.addView(optionD);
            answers.setOnCheckedChangeListener((group, checkedId) -> {
                RadioButton selected = group.findViewById(checkedId);
                if (selected != null) {
                    selectedQuizAnswers.put(question.id, selected.getTag().toString());
                }
            });

            content.addView(number);
            content.addView(questionText);
            content.addView(answers);
            card.addView(content);
            container.addView(card, UiViewFactory.verticalMargin(this, 12));
            UiViewFactory.animateIn(card, container.getChildCount());
        }
    }

    private RadioButton createAnswerRadioButton(String text) {
        RadioButton button = new RadioButton(this);
        button.setId(View.generateViewId());
        button.setText(text);
        button.setTextColor(getColor(R.color.ink));
        button.setTextSize(15);
        button.setBackgroundResource(R.drawable.bg_field_outline);
        button.setPadding(
                UiViewFactory.dp(this, 14),
                UiViewFactory.dp(this, 12),
                UiViewFactory.dp(this, 14),
                UiViewFactory.dp(this, 12)
        );

        RadioGroup.LayoutParams params = new RadioGroup.LayoutParams(
                RadioGroup.LayoutParams.MATCH_PARENT,
                RadioGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, UiViewFactory.dp(this, 8));
        button.setLayoutParams(params);
        return button;
    }

    private void submitQuizAnswers() {
        if (selectedDocument == null || currentQuizQuestions.isEmpty()) {
            Toast.makeText(this, "Chưa có câu hỏi để chấm điểm", Toast.LENGTH_SHORT).show();
            return;
        }

        QuizAttempt attempt = quizScoringService.score(
                selectedDocument.id,
                currentQuizQuestions,
                selectedQuizAnswers
        );

        studyRepository.createQuizAttempt(
                attempt,
                new RepositoryCallback<Long>() {
                    @Override
                    public void onSuccess(Long id) {
                        attempt.id = id;
                        latestQuizAttempt = attempt;
                        showQuizResult();
                    }

                    @Override
                    public void onError(Exception exception) {
                        Toast.makeText(MainActivity.this, "Không thể lưu kết quả quiz", Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private void showQuizResult() {
        currentScreen = R.layout.screen_quiz_result;
        setContentView(R.layout.screen_quiz_result);
        applySystemBars();
        bindClick(R.id.backQuestionBank, this::showQuestionBank);
        bindClick(R.id.buttonViewHistory, this::showHistory);
        bindClick(R.id.buttonRetryQuiz, this::showQuestions);
        renderQuizResult();
    }

    private void renderQuizResult() {
        TextView scoreText = findViewById(R.id.resultScore);
        TextView correctText = findViewById(R.id.resultCorrect);
        TextView detailText = findViewById(R.id.resultDetail);

        if (latestQuizAttempt == null) {
            scoreText.setText("0.0");
            correctText.setText("Chưa có kết quả");
            detailText.setText(selectedDocument == null ? "" : selectedDocument.title);
            return;
        }

        int total = latestQuizAttempt.correctCount + latestQuizAttempt.wrongCount;
        scoreText.setText(String.format(Locale.US, "%.1f", latestQuizAttempt.score));
        correctText.setText(latestQuizAttempt.correctCount + " / " + total + " câu trả lời đúng");
        detailText.setText((selectedDocument == null ? "Tài liệu" : selectedDocument.title)
                + " · Sai " + latestQuizAttempt.wrongCount + " câu");
    }

    private void showHistory() {
        currentScreen = R.layout.screen_history;
        setContentView(R.layout.screen_history);
        applySystemBars();
        bindClick(R.id.backHomeFromHistory, this::showHome);
        loadHistoryFromDatabase();
    }

    private void loadHistoryFromDatabase() {
        studyRepository.getAllQuizAttempts(new RepositoryCallback<List<QuizAttempt>>() {
            @Override
            public void onSuccess(List<QuizAttempt> attempts) {
                renderHistory(attempts);
            }

            @Override
            public void onError(Exception exception) {
                Toast.makeText(MainActivity.this, "Không thể tải lịch sử", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void renderHistory(List<QuizAttempt> attempts) {
        LinearLayout container = findViewById(R.id.historyContainer);
        container.removeAllViews();

        if (attempts.isEmpty()) {
            TextView empty = UiViewFactory.createText(
                    this,
                    "Chưa có kết quả quiz nào.",
                    15,
                    R.color.ink_muted,
                    false
            );
            container.addView(empty);
            return;
        }

        SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
        for (QuizAttempt attempt : attempts) {
            MaterialCardView card = UiViewFactory.createCard(this);
            card.setClickable(false);
            card.setFocusable(false);

            LinearLayout content = new LinearLayout(this);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(
                    UiViewFactory.dp(this, 16),
                    UiViewFactory.dp(this, 14),
                    UiViewFactory.dp(this, 16),
                    UiViewFactory.dp(this, 14)
            );

            int total = attempt.correctCount + attempt.wrongCount;
            TextView title = UiViewFactory.createText(
                    this,
                    "Quiz tài liệu #" + attempt.document_id,
                    16,
                    R.color.ink,
                    true
            );

            TextView score = UiViewFactory.createText(
                    this,
                    attempt.correctCount + "/" + total + " câu đúng · "
                            + String.format(Locale.US, "%.1f", attempt.score) + " điểm",
                    14,
                    R.color.brand_blue_dark,
                    true
            );
            score.setPadding(0, UiViewFactory.dp(this, 5), 0, 0);

            TextView detail = UiViewFactory.createText(
                    this,
                    "Sai " + attempt.wrongCount + " câu · " + format.format(new Date(attempt.completedAt)),
                    13,
                    R.color.ink_muted,
                    false
            );
            detail.setPadding(0, UiViewFactory.dp(this, 5), 0, 0);

            content.addView(title);
            content.addView(score);
            content.addView(detail);
            card.addView(content);
            container.addView(card, UiViewFactory.verticalMargin(this, 12));
            UiViewFactory.animateIn(card, container.getChildCount());
        }
    }

    private String buildMockSummary(String ocrText){
        if(isBlank(ocrText))
        {
            return "Không có nội dung";
        }

        String[] sentences = ocrText.split("(?<=[.!?])\\s+");
        StringBuilder summary = new StringBuilder();

        int count = 0;
        for(String sentence : sentences){
            String cleanSentence = sentence.trim();
            if(cleanSentence.isEmpty()){
                continue;
            }

            if(summary.length() > 0){
                summary.append("\n");
            }

            summary.append("-").append(cleanSentence);
            count++;

            if(count == 4){
                break;
            }
        }

        return summary.toString();
    }

    private String normalizeCorrectOption(String option) {
        if (isBlank(option)) {
            return "A";
        }
        String normalized = option.trim().toUpperCase(Locale.US);
        if (normalized.startsWith("A")) return "A";
        if (normalized.startsWith("B")) return "B";
        if (normalized.startsWith("C")) return "C";
        if (normalized.startsWith("D")) return "D";
        return "A";
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private void bindClick(int viewId, Runnable action) {
        View view = findViewById(viewId);
        if (view != null) {
            UiViewFactory.applyPressEffect(view);
            view.setOnClickListener(v -> action.run());
        }
    }

    private void applySystemBars() {
        View root = findViewById(R.id.main);
        animateScreenEnter(root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets systemBars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()
            );
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
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
