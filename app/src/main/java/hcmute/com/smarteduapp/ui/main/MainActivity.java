package hcmute.com.smarteduapp.ui.main;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.card.MaterialCardView;

import java.util.List;
import hcmute.com.smarteduapp.R;
import hcmute.com.smarteduapp.data.local.entity.StudyDocument;
import hcmute.com.smarteduapp.data.local.entity.Subject;
import hcmute.com.smarteduapp.data.repository.DocumentRepository;
import hcmute.com.smarteduapp.data.repository.RepositoryCallback;
import hcmute.com.smarteduapp.data.repository.SubjectRepository;
import hcmute.com.smarteduapp.ui.common.UiViewFactory;

public class MainActivity extends AppCompatActivity {

    private int currentScreen = R.layout.activity_main;
    private SubjectRepository subjectRepository;
    private DocumentRepository documentRepository;
    private long selectedSubjectId = -1L;
    private Subject selectedSubject;
    private StudyDocument selectedDocument;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        subjectRepository = new SubjectRepository(this);
        documentRepository = new DocumentRepository(this);
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
        subjectRepository.getAll(new RepositoryCallback<List<Subject>>() {
            @Override
            public void onSuccess(List<Subject> subjects) {
                renderSubjects(subjects);
            }
        });
    }

    private void renderSubjects(List<Subject> subjects) {
        LinearLayout container = findViewById(R.id.subjectListContainer);
        TextView empty = findViewById(R.id.emptySubjects);
        container.removeAllViews();
        empty.setVisibility(subjects.isEmpty() ? View.VISIBLE : View.GONE);

        for (Subject subject : subjects) {
            MaterialCardView card = UiViewFactory.createCard(this);
            card.setOnClickListener(v -> {
                selectedSubjectId = subject.id;
                selectedSubject = subject;
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
                documentRepository.getBySubjectId(selectedSubjectId,
                        new RepositoryCallback<List<StudyDocument>>() {
                            @Override
                            public void onSuccess(List<StudyDocument> documents) {
                                ((TextView) findViewById(R.id.subjectTitle)).setText(subject.name);
                                ((TextView) findViewById(R.id.subjectStats)).setText(
                                        documents.size() + " tài liệu đã lưu"
                                );
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
            TextView state = UiViewFactory.createText(this,
                    isBlank(document.ocrText) ? "Chưa có nội dung OCR" : "Đã lưu nội dung OCR",
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
        documentRepository.create(selectedSubjectId, title, new RepositoryCallback<Long>() {
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

    private void showImageFeatureMessage() {
        Toast.makeText(this, "Chọn ảnh và camera sẽ được thêm sau CRUD cơ bản", Toast.LENGTH_SHORT).show();
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
        EditText editOCRContent = findViewById(R.id.editOCRContent);

        if (selectedDocument != null) {
            textDocName.setText("Tài liệu: " + selectedDocument.title);
            editOCRContent.setText(selectedDocument.ocrText);
        }

        bindClick(R.id.backHome, this::showSubjectDetail);
        bindClick(R.id.buttonSaveDoc, this::saveDocumentContent);
        bindClick(R.id.buttonSummary, this::showSummary);
        bindClick(R.id.buttonQuestions, this::showQuestionBank);
        bindClick(R.id.buttonSaveDocument, this::showSubjectDetail);
        bindClick(R.id.buttonExplain, this::showSummary);
    }

    private void saveDocumentContent() {
        if (selectedDocument == null) return;
        EditText editOCRContent = findViewById(R.id.editOCRContent);
        String newContent = editOCRContent.getText().toString();

        selectedDocument.ocrText = newContent;
        documentRepository.update(selectedDocument, new RepositoryCallback<Integer>() {
            @Override
            public void onSuccess(Integer result) {
                Toast.makeText(MainActivity.this, "Đã lưu nội dung OCR!", Toast.LENGTH_SHORT).show();
            }
        });
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
