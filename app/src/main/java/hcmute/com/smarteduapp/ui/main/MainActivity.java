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
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

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
import hcmute.com.smarteduapp.data.local.entity.Subject;
import hcmute.com.smarteduapp.data.repository.DocumentRepository;
import hcmute.com.smarteduapp.data.repository.RepositoryCallback;
import hcmute.com.smarteduapp.data.repository.SubjectRepository;
import hcmute.com.smarteduapp.ui.common.UiViewFactory;
import hcmute.com.smarteduapp.data.repository.StudyRepository;
import hcmute.com.smarteduapp.data.local.entity.StudySummary;
import hcmute.com.smarteduapp.service.ai.GeminiService;
import hcmute.com.smarteduapp.service.ocr.MlKitOcrService;
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
    private List<StudyQuestion> currentQuizQuestions = new ArrayList<>();
    private final Map<Long, String> selectedQuizAnswers = new HashMap<>();
    private final LinkedHashMap<Long, Subject> recentSubjects = new LinkedHashMap<>();
    private QuizAttempt latestQuizAttempt;
    private Uri selectedDocumentImageUri;
    private Uri pendingCameraImageUri;
    private ActivityResultLauncher<String[]> documentImagePickerLauncher;
    private ActivityResultLauncher<String[]> documentFilePickerLauncher;
    private ActivityResultLauncher<Uri> cameraCaptureLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        subjectRepository = new SubjectRepository(this);
        documentRepository = new DocumentRepository(this);
        studyRepository = new StudyRepository(this);
        geminiService = new GeminiService();
        ocrService = new MlKitOcrService();
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

        for (StudyDocument document : documents) {
            MaterialCardView card = UiViewFactory.createCard(this);
            card.setOnClickListener(v -> openDocument(document.id));
            LinearLayout content = new LinearLayout(this);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(UiViewFactory.dp(this, 16), UiViewFactory.dp(this, 15),
                    UiViewFactory.dp(this, 16), UiViewFactory.dp(this, 15));
            content.addView(UiViewFactory.createText(this, document.title, 16,
                    R.color.ink, true));
            String imageState = isBlank(document.imageUri)
                    ? "Chưa có file"
                    : getDocumentAttachmentLabel(document.imageUri);
            String ocrState = isBlank(document.ocrText) ? "Chưa có nội dung OCR" : "Đã lưu nội dung OCR";
            TextView state = UiViewFactory.createText(this,
                    imageState + " · " + ocrState,
                    13, R.color.ink_muted, false
            );
            state.setPadding(0, UiViewFactory.dp(this, 5), 0, 0);
            content.addView(state);
            card.addView(content);
            container.addView(card, UiViewFactory.verticalMargin(this, 12));
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
            if (!isBlank(selectedDocument.imageUri)) {
                selectedDocumentImageUri = Uri.parse(selectedDocument.imageUri);
            }
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

        documentRepository.create(selectedSubjectId, title, imageUri, new RepositoryCallback<Long>() {
            @Override
            public void onSuccess(Long id) {
                showSubjectDetail();
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
        selectedDocument.imageUri = imageUri;
        documentRepository.update(selectedDocument, new RepositoryCallback<Integer>() {
            @Override
            public void onSuccess(Integer result) {
                Toast.makeText(MainActivity.this, "Đã cập nhật tài liệu", Toast.LENGTH_SHORT).show();
                showProcessDocument();
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
            showDocumentImage(selectedDocument.imageUri, imagePreview, imagePlaceholder);
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
        bindClick(R.id.buttonExplain, this::createSummaryFromCurrentDocument);
        bindClick(R.id.buttonDeleteDocumentFromDetail, this::confirmDeleteCurrentDocument);
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

        if (isBlank(selectedDocument.imageUri)) {
            Toast.makeText(this, "Tài liệu chưa có ảnh để OCR", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!isImageAttachment(selectedDocument.imageUri)) {
            Toast.makeText(this, "OCR hiện chỉ hỗ trợ ảnh. File này vẫn đã được lưu.", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "Đang nhận dạng OCR...", Toast.LENGTH_SHORT).show();
        ocrService.recognizeText(
                this,
                Uri.parse(selectedDocument.imageUri),
                new MlKitOcrService.OcrCallback() {
                    @Override
                    public void onSuccess(String recognizedText) {
                        runOnUiThread(() -> handleOcrResult(recognizedText));
                    }

                    @Override
                    public void onError(Exception exception) {
                        runOnUiThread(() ->
                                Toast.makeText(
                                        MainActivity.this,
                                        "Không thể nhận dạng văn bản từ ảnh",
                                        Toast.LENGTH_SHORT
                                ).show()
                        );
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

        loadSummaryFromDatabase();
    }

    private void loadSummaryFromDatabase(){
        if (selectedDocument == null) return;

        studyRepository.getSummariesByDocumentId(selectedDocument.id, new RepositoryCallback<List<StudySummary>>(){
            @Override
            public void onSuccess(List<StudySummary> summaries){
                TextView summaryText = findViewById(R.id.textSummaryContent);

                if(summaries.isEmpty()){
                    summaryText.setText("Chưa có tóm tắt. Hãy tạo tóm tắt!");
                    return;
                }

                StudySummary latestSummary = summaries.get(0);
                summaryText.setText(latestSummary.content);
            }

            @Override
            public void onError(Exception exception){
                Toast.makeText(MainActivity.this, "Lỗi khi lấy tóm tắt", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void createSummaryFromCurrentDocument(){
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
                                    buildMockQuestions(ocrText, documentId)
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
        List<StudyQuestion> questions = parseQuizQuestions(quizJson, documentId);
        if (questions.isEmpty()) {
            Toast.makeText(this, "Gemini trả danh sách rỗng, đã dùng câu hỏi mẫu", Toast.LENGTH_SHORT).show();
            questions = buildMockQuestions(fallbackText, documentId);
        }

        saveGeneratedQuestions(questions, documentId);
    } catch (Exception exception) {
        android.util.Log.e("GeminiQuiz", "Raw quiz JSON: " + quizJson, exception);
        Toast.makeText(this, "Không thể đọc JSON Gemini, đã dùng câu hỏi mẫu", Toast.LENGTH_SHORT).show();
        saveGeneratedQuestions(buildMockQuestions(fallbackText, documentId));
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
                        showQuestionBank();
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

            card.addView(content);
            container.addView(card, UiViewFactory.verticalMargin(this, 12));
        }
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

        int correct = 0;
        for (StudyQuestion question : currentQuizQuestions) {
            String selectedAnswer = selectedQuizAnswers.get(question.id);
            if (question.correctOption.equalsIgnoreCase(selectedAnswer)) {
                correct++;
            }
        }

        int total = currentQuizQuestions.size();
        int wrong = total - correct;
        float score = total == 0 ? 0f : (correct * 10f) / total;

        QuizAttempt attempt = new QuizAttempt(
                selectedDocument.id,
                score,
                correct,
                wrong,
                System.currentTimeMillis()
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

    private List<StudyQuestion> parseQuizQuestions(String quizJson, long documentId) throws Exception {
        String cleanJson = extractJsonPayload(quizJson);
        JSONArray array;
        if (cleanJson.startsWith("{")) {
            JSONObject object = new JSONObject(cleanJson);
            array = object.optJSONArray("questions");
            if (array == null) {
                array = object.optJSONArray("data");
            }
            if (array == null) {
                throw new IllegalArgumentException("Quiz JSON object does not contain questions array");
            }
        } else {
            array = new JSONArray(cleanJson);
        }
        List<StudyQuestion> questions = new ArrayList<>();

        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.getJSONObject(i);
            String correctOption = normalizeCorrectOption(item.optString("correctOption", "A"));

            StudyQuestion question = new StudyQuestion(
                    documentId,
                    item.optString("questionText", "Câu hỏi " + (i + 1)),
                    item.optString("optionA", "Đáp án A"),
                    item.optString("optionB", "Đáp án B"),
                    item.optString("optionC", "Đáp án C"),
                    item.optString("optionD", "Đáp án D"),
                    correctOption,
                    item.optString("explanation", ""),
                    i + 1
            );

            questions.add(question);
        }

        return questions;
    }

    private String extractJsonPayload(String rawText) {
        String cleanText = rawText == null ? "" : rawText.trim();
        if (cleanText.startsWith("```")) {
            int firstNewline = cleanText.indexOf('\n');
            if (firstNewline != -1) {
                cleanText = cleanText.substring(firstNewline + 1).trim();
            }
            if (cleanText.endsWith("```")) {
                cleanText = cleanText.substring(0, cleanText.length() - 3).trim();
            }
        }

        int firstArray = cleanText.indexOf('[');
        int lastArray = cleanText.lastIndexOf(']');
        if (firstArray >= 0 && lastArray > firstArray) {
            return cleanText.substring(firstArray, lastArray + 1);
        }

        int firstObject = cleanText.indexOf('{');
        int lastObject = cleanText.lastIndexOf('}');
        if (firstObject >= 0 && lastObject > firstObject) {
            return cleanText.substring(firstObject, lastObject + 1);
        }

        return cleanText;
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

    private List<StudyQuestion> buildMockQuestions(String ocrText, long documentId) {
        String source = isBlank(ocrText) ? "nội dung tài liệu" : ocrText.trim();
        String preview = source.length() > 90 ? source.substring(0, 90).trim() + "..." : source;
        List<StudyQuestion> questions = new ArrayList<>();

        questions.add(new StudyQuestion(
                documentId,
                "Ý chính của tài liệu này là gì?",
                preview,
                "Một nội dung không liên quan đến tài liệu",
                "Thông tin về tài khoản người dùng",
                "Cấu hình giao diện ứng dụng",
                "A",
                "Đáp án A được lấy trực tiếp từ nội dung OCR.",
                1
        ));
        questions.add(new StudyQuestion(
                documentId,
                "Nguồn dữ liệu nào được dùng để tạo bộ câu hỏi?",
                "Nội dung OCR của tài liệu",
                "Tên ứng dụng",
                "Màu nền giao diện",
                "Lịch sử hệ thống",
                "A",
                "Ứng dụng tạo câu hỏi dựa trên phần OCR đã lưu.",
                2
        ));
        questions.add(new StudyQuestion(
                documentId,
                "Sau khi tạo câu hỏi, dữ liệu nên được lưu ở đâu?",
                "SQLite",
                "Bộ nhớ tạm của màn hình",
                "Toast message",
                "Thanh trạng thái",
                "A",
                "Proposal yêu cầu lưu câu hỏi và đáp án vào SQLite.",
                3
        ));
        questions.add(new StudyQuestion(
                documentId,
                "Người dùng cần làm gì trước khi tạo quiz?",
                "Nhập hoặc lưu nội dung OCR",
                "Xóa môn học",
                "Đổi icon ứng dụng",
                "Tắt kết nối mạng",
                "A",
                "Quiz được tạo từ OCR text nên cần có OCR trước.",
                4
        ));
        questions.add(new StudyQuestion(
                documentId,
                "Kết quả quiz dùng để làm gì?",
                "Theo dõi lịch sử học tập",
                "Tạo màu nền mới",
                "Đổi tên package",
                "Xóa database",
                "A",
                "Điểm quiz được lưu để xem lại trong lịch sử học tập.",
                5
        ));

        return questions;
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
