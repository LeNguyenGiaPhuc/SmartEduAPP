package hcmute.com.smarteduapp.ui.main;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.card.MaterialCardView;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

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
import hcmute.com.smarteduapp.data.local.entity.StudyQuestion;
import hcmute.com.smarteduapp.data.local.entity.QuizAttempt;

public class MainActivity extends AppCompatActivity {

    private int currentScreen = R.layout.activity_main;
    private SubjectRepository subjectRepository;
    private DocumentRepository documentRepository;
    private StudyRepository studyRepository;
    private long selectedSubjectId = -1L;
    private Subject selectedSubject;
    private StudyDocument selectedDocument;
    private GeminiService geminiService;
    private List<StudyQuestion> currentQuizQuestions = new ArrayList<>();
    private final Map<Long, String> selectedQuizAnswers = new HashMap<>();
    private QuizAttempt latestQuizAttempt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        subjectRepository = new SubjectRepository(this);
        documentRepository = new DocumentRepository(this);
        studyRepository = new StudyRepository(this);
        geminiService = new GeminiService();
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
        bindClick(R.id.buttonSummary, this::createSummaryFromCurrentDocument);
        bindClick(R.id.buttonQuestions, this::createQuizFromCurrentDocument);
        bindClick(R.id.buttonExplain, this::createSummaryFromCurrentDocument);
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

        EditText editOCRContent = findViewById(R.id.editOCRContent);
        String ocrText = editOCRContent.getText().toString();

        if(isBlank(ocrText)){
            Toast.makeText(this, "Chưa có nội dung OCR để tóm tắt", Toast.LENGTH_SHORT).show();
            return;
        }

        selectedDocument.ocrText = ocrText;

        Toast.makeText(this, "Đang tóm tắt bằng Gemini...", Toast.LENGTH_SHORT).show();

        geminiService.summarize(
        ocrText,
        new GeminiService.GeminiCallback() {
            @Override
            public void onSuccess(String summaryContent) {
                runOnUiThread(() -> saveGeneratedSummary(summaryContent));
            }

            @Override
            public void onError(Exception exception) {
                runOnUiThread(() ->
                        Toast.makeText(MainActivity.this, "Không thể tóm tắt bằng Gemini", Toast.LENGTH_SHORT).show()
                );
            }
        }
        );
    }

    private void createQuizFromCurrentDocument() {
    if (selectedDocument == null) {
        return;
    }

    EditText editOCRContent = findViewById(R.id.editOCRContent);
    String ocrText = editOCRContent.getText().toString();

    if (isBlank(ocrText)) {
        Toast.makeText(this, "Chưa có nội dung OCR để tạo quiz", Toast.LENGTH_SHORT).show();
        return;
    }

    selectedDocument.ocrText = ocrText;

    Toast.makeText(this, "Đang tạo quiz bằng Gemini...", Toast.LENGTH_SHORT).show();

    geminiService.generateQuiz(
            ocrText,
            new GeminiService.GeminiCallback() {
                @Override
                public void onSuccess(String quizJson) {
                    runOnUiThread(() -> saveGeneratedQuestions(quizJson));
                }

                @Override
                public void onError(Exception exception) {
                    runOnUiThread(() ->
                            Toast.makeText(MainActivity.this, "Không thể tạo quiz bằng Gemini", Toast.LENGTH_SHORT).show()
                    );
                }
            }
    );
    }

    private void saveGeneratedQuestions(String quizJson) {
    if (selectedDocument == null) {
        return;
    }

    try {
        List<StudyQuestion> questions = parseQuizQuestions(quizJson, selectedDocument.id);

        if (questions.isEmpty()) {
            Toast.makeText(this, "Gemini không tạo được câu hỏi", Toast.LENGTH_SHORT).show();
            return;
        }

        studyRepository.replaceQuestions(
                selectedDocument.id,
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
    } catch (Exception exception) {
        android.util.Log.e("GeminiQuiz", "Raw quiz JSON: " + quizJson, exception);
        Toast.makeText(this, "Không thể đọc JSON câu hỏi từ Gemini", Toast.LENGTH_SHORT).show();
    }
    }

    private void saveGeneratedSummary(String summaryContent) {
    if (selectedDocument == null) {
        return;
    }

    if (isBlank(summaryContent)) {
        Toast.makeText(this, "Gemini không trả về nội dung tóm tắt", Toast.LENGTH_SHORT).show();
        return;
    }

    studyRepository.createSummary(
            selectedDocument.id,
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
        String cleanJson = quizJson.trim();
        if (cleanJson.startsWith("```")) {
            int firstNewline = cleanJson.indexOf('\n');
            if (firstNewline != -1) {
                cleanJson = cleanJson.substring(firstNewline).trim();
            }
            if (cleanJson.endsWith("```")) {
                cleanJson = cleanJson.substring(0, cleanJson.length() - 3).trim();
            }
        }
        JSONArray array = new JSONArray(cleanJson);
        List<StudyQuestion> questions = new ArrayList<>();

        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.getJSONObject(i);

            StudyQuestion question = new StudyQuestion(
                    documentId,
                    item.getString("questionText"),
                    item.getString("optionA"),
                    item.getString("optionB"),
                    item.getString("optionC"),
                    item.getString("optionD"),
                    item.getString("correctOption"),
                    item.optString("explanation", ""),
                    i + 1
            );

            questions.add(question);
        }

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
