package hcmute.com.smarteduapp.ui.main;

import android.view.View;
import android.text.InputType;
import android.widget.EditText;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import java.util.ArrayList;
import java.util.List;

import hcmute.com.smarteduapp.R;
import hcmute.com.smarteduapp.data.local.entity.QuizAttempt;
import hcmute.com.smarteduapp.data.local.entity.QuizAttemptAnswer;
import hcmute.com.smarteduapp.data.local.entity.StudyQuestion;
import hcmute.com.smarteduapp.data.local.entity.StudySummary;
import hcmute.com.smarteduapp.data.repository.RepositoryCallback;
import hcmute.com.smarteduapp.service.ai.GeminiService;
import hcmute.com.smarteduapp.ui.common.UiViewFactory;

/**
 * Owns AI summary, AI chat and quiz flow.
 */
class StudyController {
    private final MainActivity activity;
    private boolean mistakeReviewMode;

    StudyController(MainActivity activity) {
        this.activity = activity;
    }

    void showAiChat() {
        if (activity.selectedDocument == null) {
            return;
        }

        activity.ensureCurrentDocumentHasAttachments(
                "Tài liệu không còn file đính kèm, không thể hỏi đáp AI",
                this::openAiChatScreen
        );
    }


    void openAiChatScreen() {
        if (activity.selectedDocument == null) {
            return;
        }

        String documentText = activity.selectedDocument.ocrText == null ? "" : activity.selectedDocument.ocrText.trim();
        if (activity.isBlank(documentText)) {
            Toast.makeText(activity, "Chưa có nội dung tài liệu để hỏi đáp", Toast.LENGTH_SHORT).show();
            return;
        }

        activity.currentScreen = R.layout.screen_ai_chat;
        activity.setContentView(R.layout.screen_ai_chat);
        activity.applySystemBars();

        TextView title = activity.findViewById(R.id.textAiChatTitle);
        title.setText("Hỏi đáp: " + activity.selectedDocument.title);
        activity.chatMessageRenderer.addMessage("AI", "Bạn có thể hỏi về nội dung tài liệu này. Mình sẽ trả lời dựa trên phần đã quét.", false);

        EditText input = activity.findViewById(R.id.inputAiQuestion);
        activity.bindClick(R.id.backProcessFromChat, activity::showProcessDocument);
        activity.bindClick(R.id.buttonSendAiQuestion, () -> sendAiQuestion(input));
    }


    void sendAiQuestion(EditText input) {
        if (activity.selectedDocument == null) {
            return;
        }

        String question = input.getText().toString().trim();
        String documentText = activity.selectedDocument.ocrText == null ? "" : activity.selectedDocument.ocrText.trim();
        if (activity.isBlank(question)) {
            input.setError("Nhập câu hỏi");
            return;
        }
        if (activity.isBlank(documentText)) {
            Toast.makeText(activity, "Chưa có nội dung tài liệu để hỏi đáp", Toast.LENGTH_SHORT).show();
            return;
        }

        input.setText("");
        activity.chatMessageRenderer.addMessage("Bạn", question, true);
        activity.chatMessageRenderer.addMessage("AI", "Đang suy nghĩ...", false);

        activity.geminiService.askAboutDocument(documentText, question, new GeminiService.GeminiCallback() {
            @Override
            public void onSuccess(String text) {
                activity.runOnUiThread(() -> {
                    activity.chatMessageRenderer.removeLastMessage();
                    activity.chatMessageRenderer.addMessage("AI", activity.isBlank(text) ? "Mình chưa tìm thấy câu trả lời phù hợp trong tài liệu." : text, false);
                });
            }

            @Override
            public void onError(Exception exception) {
                activity.runOnUiThread(() -> {
                    activity.chatMessageRenderer.removeLastMessage();
                    activity.chatMessageRenderer.addMessage("AI", "Không thể trả lời lúc này. Kiểm tra API key hoặc kết nối mạng.", false);
                });
            }
        });
    }


    void showSummary() {
        activity.currentScreen = R.layout.screen_summary_explain;
        activity.setContentView(R.layout.screen_summary_explain);
        activity.applySystemBars();
        activity.bindClick(R.id.backProcess, activity::showProcessDocument);
        activity.bindClick(R.id.buttonStartQuiz, this::createQuizFromCurrentDocument);
        activity.bindClick(R.id.buttonDeleteSummary, this::confirmDeleteDisplayedSummary);

        loadSummaryFromDatabase();
    }


    void loadSummaryFromDatabase(){
        if (activity.selectedDocument == null) return;

        activity.studyRepository.getSummariesByDocumentId(activity.selectedDocument.id, new RepositoryCallback<List<StudySummary>>(){
            @Override
            public void onSuccess(List<StudySummary> summaries){
                TextView summaryText = activity.findViewById(R.id.textSummaryContent);
                TextView summaryTitle = activity.findViewById(R.id.textSummaryTitle);
                TextView resultHeader = activity.findViewById(R.id.textResultHeader);

                if(summaries.isEmpty()){
                    activity.latestDisplayedSummary = null;
                    summaryText.setText("Chưa có tóm tắt AI. Hãy tạo tóm tắt từ tài liệu trước.");
                    return;
                }

                StudySummary latestSummary = summaries.get(0);
                activity.latestDisplayedSummary = latestSummary;
                summaryText.setText(latestSummary.content);
                summaryTitle.setText("Tóm tắt tài liệu");
                resultHeader.setText("Tóm tắt ý chính");
            }

            @Override
            public void onError(Exception exception){
                Toast.makeText(activity, "Lỗi khi lấy tóm tắt", Toast.LENGTH_SHORT).show();
            }
        });
    }


    void createSummaryFromCurrentDocument(){
        if (activity.selectedDocument == null) return;

        activity.ensureCurrentDocumentHasAttachments(
                "Tài liệu không còn file đính kèm, không thể tóm tắt AI",
                this::createSummaryAfterValidation
        );
    }


    void createSummaryAfterValidation() {
        if (activity.selectedDocument == null) return;

        String ocrText = activity.selectedDocument.ocrText == null ? "" : activity.selectedDocument.ocrText.trim();

        if(activity.isBlank(ocrText)){
            setSummaryLoading(false);
            Toast.makeText(activity, "Chưa có nội dung tài liệu để tóm tắt", Toast.LENGTH_SHORT).show();
            return;
        }

        long documentId = activity.selectedDocument.id;
        // Read the cached result first. Gemini is only called when this document
        // does not have a saved summary yet (or after the user deletes it).
        setSummaryLoading(true);
        activity.studyRepository.getSummariesByDocumentId(documentId, new RepositoryCallback<List<StudySummary>>() {
            @Override
            public void onSuccess(List<StudySummary> summaries) {
                for (StudySummary summary : summaries) {
                    if (!activity.isBlank(summary.content)) {
                        activity.latestDisplayedSummary = summary;
                        setSummaryLoading(false);
                        activity.showSummary();
                        return;
                    }
                }

                activity.documentRepository.update(activity.selectedDocument, new RepositoryCallback<Integer>() {
                    @Override
                    public void onSuccess(Integer result) {
                        requestGeminiSummary(ocrText, documentId);
                    }

                    @Override
                    public void onError(Exception exception) {
                        setSummaryLoading(false);
                        Toast.makeText(activity, "Không thể lưu OCR trước khi tóm tắt", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(Exception exception) {
                setSummaryLoading(false);
                Toast.makeText(activity, "Lỗi khi kiểm tra tóm tắt đã lưu", Toast.LENGTH_SHORT).show();
            }
        });
    }


    void requestGeminiSummary(String ocrText, long documentId) {
        setSummaryLoading(true);
        Toast.makeText(activity, "Đang tóm tắt bằng Gemini...", Toast.LENGTH_SHORT).show();

        activity.geminiService.summarize(
                ocrText,
                new GeminiService.GeminiCallback() {
                    @Override
                    public void onSuccess(String summaryContent) {
                        activity.runOnUiThread(() -> {
                            if (activity.isBlank(summaryContent)) {
                                Toast.makeText(
                                        activity,
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
                        activity.runOnUiThread(() -> {
                            Toast.makeText(
                                    activity,
                                    "Gemini lỗi, đã dùng tóm tắt tạm",
                                    Toast.LENGTH_SHORT
                            ).show();
                            saveGeneratedSummary(buildMockSummary(ocrText), documentId);
                        });
                    }
                }
        );
    }


    void createQuizFromCurrentDocument() {
    if (activity.selectedDocument == null) {
        return;
    }

    activity.ensureCurrentDocumentHasAttachments(
            "Tài liệu không còn file đính kèm, không thể tạo quiz",
            this::createQuizAfterValidation
    );
    }


    void createQuizAfterValidation() {
    if (activity.selectedDocument == null) {
        return;
    }

    String ocrText = activity.selectedDocument.ocrText == null ? "" : activity.selectedDocument.ocrText.trim();

    if (activity.isBlank(ocrText)) {
        Toast.makeText(activity, "Chưa có nội dung tài liệu để tạo quiz", Toast.LENGTH_SHORT).show();
        return;
    }

    long documentId = activity.selectedDocument.id;
    activity.documentRepository.update(activity.selectedDocument, new RepositoryCallback<Integer>() {
        @Override
        public void onSuccess(Integer result) {
            showQuizSetupDialog(ocrText, documentId);
        }

        @Override
        public void onError(Exception exception) {
            setQuizLoading(false);
            Toast.makeText(activity, "Không thể lưu OCR trước khi tạo quiz", Toast.LENGTH_SHORT).show();
        }
    });
    }


    private void showQuizSetupDialog(String ocrText, long documentId) {
        int defaultCount = estimateQuizQuestionCount(ocrText);

        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        int horizontalPadding = UiViewFactory.dp(activity, 4);
        form.setPadding(horizontalPadding, 0, horizontalPadding, 0);

        TextView countLabel = UiViewFactory.createText(
                activity,
                "Số câu hỏi (3 - 10)",
                14,
                R.color.ink,
                true
        );
        EditText countInput = new EditText(activity);
        countInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        countInput.setSingleLine(true);
        countInput.setText(String.valueOf(defaultCount));
        countInput.setSelectAllOnFocus(true);

        TextView difficultyLabel = UiViewFactory.createText(
                activity,
                "Độ khó",
                14,
                R.color.ink,
                true
        );
        Spinner difficultyInput = new Spinner(activity);
        String[] difficulties = {"Dễ", "Trung bình", "Khó"};
        ArrayAdapter<String> difficultyAdapter = new ArrayAdapter<>(
                activity,
                android.R.layout.simple_spinner_item,
                difficulties
        );
        difficultyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        difficultyInput.setAdapter(difficultyAdapter);
        difficultyInput.setSelection(1);

        TextView descriptionLabel = UiViewFactory.createText(
                activity,
                "Mô tả hướng ra đề (không bắt buộc)",
                14,
                R.color.ink,
                true
        );
        EditText descriptionInput = new EditText(activity);
        descriptionInput.setHint("Ví dụ: tập trung vào khái niệm và ví dụ thực tế");
        descriptionInput.setGravity(android.view.Gravity.TOP);
        descriptionInput.setMinLines(3);
        descriptionInput.setInputType(
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
        );

        form.addView(countLabel);
        form.addView(countInput);
        form.addView(difficultyLabel);
        form.addView(difficultyInput);
        form.addView(descriptionLabel);
        form.addView(descriptionInput);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Thiết lập quiz")
                .setView(form)
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Tạo quiz", null)
                .create();

        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    int questionCount;
                    try {
                        questionCount = Integer.parseInt(
                                countInput.getText().toString().trim()
                        );
                    } catch (NumberFormatException exception) {
                        countInput.setError("Nhập số câu từ 3 đến 10");
                        return;
                    }

                    if (questionCount < 3 || questionCount > 10) {
                        countInput.setError("Số câu phải từ 3 đến 10");
                        return;
                    }

                    String difficulty = difficultyInput.getSelectedItem().toString();
                    String description = descriptionInput.getText().toString().trim();
                    dialog.dismiss();
                    requestGeminiQuiz(
                            ocrText,
                            documentId,
                            questionCount,
                            difficulty,
                            description
                    );
                }));
        dialog.show();
    }


    void requestGeminiQuiz(String ocrText, long documentId) {
        requestGeminiQuiz(
                ocrText,
                documentId,
                estimateQuizQuestionCount(ocrText),
                "Trung bình",
                ""
        );
    }

    void requestGeminiQuiz(
            String ocrText,
            long documentId,
            int questionCount,
            String difficulty,
            String description
    ) {
        setQuizLoading(true);
        Toast.makeText(activity, "Đang tạo " + questionCount + " câu quiz bằng Gemini...", Toast.LENGTH_SHORT).show();

        activity.geminiService.generateQuiz(
                ocrText,
                questionCount,
                difficulty,
                description,
                new GeminiService.GeminiCallback() {
                    @Override
                    public void onSuccess(String quizJson) {
                        activity.runOnUiThread(() -> saveGeneratedQuestions(quizJson, documentId, questionCount));
                    }

                    @Override
                    public void onError(Exception exception) {
                        activity.runOnUiThread(() -> {
                            setQuizLoading(false);
                            Toast.makeText(
                                    activity,
                                    "Không tạo được quiz từ AI. Kiểm tra API key hoặc mạng rồi thử lại",
                                    Toast.LENGTH_SHORT
                            ).show();
                        });
                    }
                }
        );
    }


    void saveGeneratedQuestions(String quizJson) {
    if (activity.selectedDocument == null) {
        return;
    }
    saveGeneratedQuestions(quizJson, activity.selectedDocument.id);
    }


    void saveGeneratedQuestions(String quizJson, long documentId) {
        saveGeneratedQuestions(quizJson, documentId, -1);
    }

    void saveGeneratedQuestions(String quizJson, long documentId, int expectedCount) {
    if (activity.selectedDocument == null) {
        return;
    }

    try {
        List<StudyQuestion> questions = activity.quizParser.parse(quizJson, documentId, expectedCount);
        if (questions.isEmpty()) {
            setQuizLoading(false);
            Toast.makeText(activity, "Gemini chưa trả về câu hỏi hợp lệ. Vui lòng thử lại", Toast.LENGTH_SHORT).show();
            return;
        }

        saveGeneratedQuestions(questions, documentId);
    } catch (Exception exception) {
        android.util.Log.e("GeminiQuiz", "Raw quiz JSON: " + quizJson, exception);
        setQuizLoading(false);
        Toast.makeText(activity, "Không thể đọc câu hỏi từ Gemini. Vui lòng tạo lại quiz", Toast.LENGTH_SHORT).show();
    }
    }


    void saveGeneratedQuestions(List<StudyQuestion> questions) {
        if (activity.selectedDocument == null) {
            return;
        }
        saveGeneratedQuestions(questions, activity.selectedDocument.id);
    }


    void saveGeneratedQuestions(List<StudyQuestion> questions, long documentId) {
        if (activity.selectedDocument == null) {
            return;
        }

        if (questions.isEmpty()) {
            setQuizLoading(false);
            Toast.makeText(activity, "Không tạo được câu hỏi", Toast.LENGTH_SHORT).show();
            return;
        }

        activity.studyRepository.replaceQuestions(
                documentId,
                questions,
                new RepositoryCallback<Integer>() {
                    @Override
                    public void onSuccess(Integer result) {
                        setQuizLoading(false);
                        mistakeReviewMode = false;
                        activity.latestQuizAttempt = null;
                        activity.selectedQuizAnswers.clear();
                        showFocusQuizModeDialog(true);
                    }

                    @Override
                    public void onError(Exception exception) {
                        setQuizLoading(false);
                        Toast.makeText(activity, "Không thể lưu câu hỏi", Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }


    void saveGeneratedSummary(String summaryContent) {
    if (activity.selectedDocument == null) {
        return;
    }
    saveGeneratedSummary(summaryContent, activity.selectedDocument.id);
    }


    void saveGeneratedSummary(String summaryContent, long documentId) {
    if (activity.isBlank(summaryContent)) {
        setSummaryLoading(false);
        Toast.makeText(activity, "Không tạo được nội dung tóm tắt", Toast.LENGTH_SHORT).show();
        return;
    }

    activity.studyRepository.createSummary(
            documentId,
            summaryContent,
            new RepositoryCallback<Long>() {
                @Override
                public void onSuccess(Long result) {
                    showSummary();
                }

                @Override
                public void onError(Exception exception) {
                    setSummaryLoading(false);
                    Toast.makeText(activity, "Không thể lưu tóm tắt", Toast.LENGTH_SHORT).show();
                }
            }
    );
    }


    void confirmDeleteDisplayedSummary() {
        if (activity.latestDisplayedSummary == null) {
            Toast.makeText(activity, "Chưa có kết quả AI để xóa", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(activity)
                .setTitle("Xóa kết quả AI")
                .setMessage("Bạn có chắc muốn xóa kết quả AI đang xem không?")
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Xóa", (dialog, which) -> deleteDisplayedSummary())
                .show();
    }


    void deleteDisplayedSummary() {
        if (activity.latestDisplayedSummary == null) {
            return;
        }

        activity.studyRepository.deleteSummary(activity.latestDisplayedSummary, new RepositoryCallback<Integer>() {
            @Override
            public void onSuccess(Integer result) {
                activity.latestDisplayedSummary = null;
                Toast.makeText(activity, "Đã xóa kết quả AI", Toast.LENGTH_SHORT).show();
                activity.showProcessDocument();
            }

            @Override
            public void onError(Exception exception) {
                Toast.makeText(activity, "Không thể xóa kết quả AI", Toast.LENGTH_SHORT).show();
            }
        });
    }


    void showQuestions() {
        showFocusQuizModeDialog(true);
    }

    /**
     * Starts a short quiz from the questions answered incorrectly in the latest attempt.
     * The answer snapshot is used so this still works after the original quiz is regenerated.
     */
    void reviewMistakesForCurrentDocument() {
        if (activity.selectedDocument == null) {
            return;
        }

        long documentId = activity.selectedDocument.id;
        activity.studyRepository.getLatestQuizAttempt(documentId, new RepositoryCallback<QuizAttempt>() {
            @Override
            public void onSuccess(QuizAttempt attempt) {
                if (attempt == null) {
                    Toast.makeText(activity, "Chưa có lần làm quiz để ôn câu sai", Toast.LENGTH_SHORT).show();
                    return;
                }

                activity.studyRepository.getQuizAttemptAnswers(attempt.id,
                        new RepositoryCallback<List<QuizAttemptAnswer>>() {
                            @Override
                            public void onSuccess(List<QuizAttemptAnswer> answers) {
                                List<StudyQuestion> wrongQuestions = new ArrayList<>();
                                for (QuizAttemptAnswer answer : answers) {
                                    if (!answer.correct) {
                                        wrongQuestions.add(convertAnswerToQuestion(answer));
                                    }
                                }

                                if (wrongQuestions.isEmpty()) {
                                    Toast.makeText(activity, "Bạn đã trả lời đúng toàn bộ câu trong lần gần nhất", Toast.LENGTH_SHORT).show();
                                    return;
                                }

                                mistakeReviewMode = true;
                                activity.latestQuizAttempt = null;
                                activity.currentQuizQuestions = wrongQuestions;
                                activity.selectedQuizAnswers.clear();
                                activity.currentQuizQuestionIndex = 0;
                                showFocusQuizModeDialog(false);
                            }

                            @Override
                            public void onError(Exception exception) {
                                Toast.makeText(activity, "Không thể tải các câu sai", Toast.LENGTH_SHORT).show();
                            }
                        });
            }

            @Override
            public void onError(Exception exception) {
                Toast.makeText(activity, "Không thể tải lịch sử quiz", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private StudyQuestion convertAnswerToQuestion(QuizAttemptAnswer answer) {
        StudyQuestion question = new StudyQuestion(
                answer.document_id,
                answer.questionText,
                answer.optionA,
                answer.optionB,
                answer.optionC,
                answer.optionD,
                answer.correctOption,
                answer.explanation,
                answer.questionOrder
        );
        question.id = answer.question_id > 0 ? answer.question_id : answer.id;
        return question;
    }


    void showFocusQuizModeDialog(boolean reloadFromDatabase) {
        new AlertDialog.Builder(activity)
                .setTitle("Bật chế độ tập trung?")
                .setMessage("Nếu bật, bạn không thể quay lại khi đang làm quiz. Nếu rời app trong lúc làm, kết quả vẫn được lưu nhưng đáp án đúng và giải thích sẽ bị khóa.")
                .setNegativeButton("Làm bình thường", (dialog, which) -> {
                    activity.startFocusQuizSession(false);
                    openQuestions(reloadFromDatabase);
                })
                .setPositiveButton("Bật tập trung", (dialog, which) -> {
                    activity.startFocusQuizSession(true);
                    openQuestions(reloadFromDatabase);
                })
                .show();
    }


    void showQuestions(boolean reloadFromDatabase) {
        openQuestions(reloadFromDatabase);
    }


    private void openQuestions(boolean reloadFromDatabase) {
        activity.currentScreen = R.layout.screen_questions;
        activity.setContentView(R.layout.screen_questions);
        activity.applySystemBars();
        activity.bindClick(R.id.backProcessFromQuestions, this::handleBackFromQuestions);
        activity.bindClick(R.id.buttonPreviousQuestion, this::showPreviousQuestion);
        activity.bindClick(R.id.buttonCheckAnswers, this::showNextQuestionOrSubmit);
        if (reloadFromDatabase || activity.currentQuizQuestions.isEmpty()) {
            activity.currentQuizQuestionIndex = 0;
            loadQuizQuestionsFromDatabase();
            return;
        }

        TextView title = activity.findViewById(R.id.quizTitle);
        title.setText((mistakeReviewMode ? "Ôn câu sai: " : "Quiz: ") + activity.selectedDocument.title);
        activity.currentQuizQuestionIndex = 0;
        renderQuizQuestions(activity.currentQuizQuestions);
    }


    private void handleBackFromQuestions() {
        if (activity.isFocusQuizInProgress()) {
            Toast.makeText(
                    activity,
                    "Đang ở chế độ tập trung. Hãy nộp quiz trước khi thoát.",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        activity.resetFocusQuizSession();
        activity.showProcessDocument();
    }


    void loadQuizQuestionsFromDatabase() {
        if (activity.selectedDocument == null) {
            return;
        }

        TextView title = activity.findViewById(R.id.quizTitle);
        title.setText((mistakeReviewMode ? "Ôn câu sai: " : "Quiz: ") + activity.selectedDocument.title);

        activity.studyRepository.getQuestionsByDocumentId(
                activity.selectedDocument.id,
                new RepositoryCallback<List<StudyQuestion>>() {
                    @Override
                    public void onSuccess(List<StudyQuestion> questions) {
                        activity.currentQuizQuestions = questions;
                        activity.currentQuizQuestionIndex = 0;
                        activity.selectedQuizAnswers.clear();
                        renderQuizQuestions(questions);
                    }

                    @Override
                    public void onError(Exception exception) {
                        Toast.makeText(activity, "Không thể tải quiz", Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }


    void renderQuizQuestions(List<StudyQuestion> questions) {
        activity.quizUiRenderer.renderSingleQuestion(
                questions,
                activity.selectedQuizAnswers,
                activity.currentQuizQuestionIndex
        );
    }


    private void showPreviousQuestion() {
        if (activity.currentQuizQuestionIndex <= 0) {
            return;
        }

        activity.currentQuizQuestionIndex--;
        renderQuizQuestions(activity.currentQuizQuestions);
    }


    private void showNextQuestionOrSubmit() {
        if (activity.currentQuizQuestions.isEmpty()) {
            return;
        }

        if (activity.currentQuizQuestionIndex
                < activity.currentQuizQuestions.size() - 1) {
            activity.currentQuizQuestionIndex++;
            renderQuizQuestions(activity.currentQuizQuestions);
            return;
        }

        submitQuizAnswers();
    }


    void submitQuizAnswers() {
        if (activity.selectedDocument == null || activity.currentQuizQuestions.isEmpty()) {
            Toast.makeText(activity, "Chưa có câu hỏi để chấm điểm", Toast.LENGTH_SHORT).show();
            return;
        }

        int firstMissingQuestion = findFirstUnansweredQuestion();
        if (firstMissingQuestion > 0) {
            Toast.makeText(
                    activity,
                    "Bạn chưa trả lời đủ câu hỏi. Vui lòng chọn đáp án cho câu " + firstMissingQuestion,
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        QuizAttempt attempt = activity.quizScoringService.score(
                activity.selectedDocument.id,
                activity.currentQuizQuestions,
                activity.selectedQuizAnswers
        );
        attempt.focusModeEnabled = activity.focusQuizModeEnabled;
        attempt.focusExitCount = activity.focusQuizExitCount;
        attempt.explanationUnlocked = activity.shouldUnlockQuizExplanation();

        List<QuizAttemptAnswer> answers = buildAttemptAnswers(attempt);

        setSubmitLoading(true);
        activity.focusQuizSubmitting = true;
        activity.studyRepository.createQuizAttemptWithAnswers(
                attempt,
                answers,
                new RepositoryCallback<Long>() {
                    @Override
                    public void onSuccess(Long id) {
                        attempt.id = id;
                        activity.latestQuizAttempt = attempt;
                        activity.stopFocusQuizTracking();
                        showQuizResult();
                    }

                    @Override
                    public void onError(Exception exception) {
                        setSubmitLoading(false);
                        activity.focusQuizSubmitting = false;
                        Toast.makeText(activity, "Không thể lưu kết quả quiz", Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }


    private List<QuizAttemptAnswer> buildAttemptAnswers(QuizAttempt attempt) {
        List<QuizAttemptAnswer> answers = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (StudyQuestion question : activity.currentQuizQuestions) {
            String selectedOption = activity.selectedQuizAnswers.get(question.id);
            if (selectedOption == null) {
                selectedOption = "";
            }

            answers.add(new QuizAttemptAnswer(
                    0,
                    attempt.document_id,
                    question.id,
                    question.questionOrder,
                    question.questionText,
                    question.optionA,
                    question.optionB,
                    question.optionC,
                    question.optionD,
                    question.correctOption,
                    selectedOption,
                    question.explanation,
                    selectedOption.equalsIgnoreCase(question.correctOption),
                    now
            ));
        }
        return answers;
    }


    void showQuizResult() {
        activity.currentScreen = R.layout.screen_quiz_result;
        activity.setContentView(R.layout.screen_quiz_result);
        activity.applySystemBars();
        activity.bindClick(R.id.backQuizResult, this::goBackFromQuizResult);
        activity.bindClick(R.id.buttonViewHistory, activity::showHistory);
        activity.bindClick(R.id.buttonRetryQuiz, () -> showFocusQuizModeDialog(false));
        renderQuizResult();
    }


    void goBackFromQuizResult() {
        activity.resetFocusQuizSession();
        if (activity.quizResultOpenedFromHome) {
            activity.quizResultOpenedFromHome = false;
            activity.showHome();
            return;
        }
        if (activity.documentOpenedFromHistory) {
            activity.documentOpenedFromHistory = false;
            activity.showHistory();
            return;
        }
        activity.showProcessDocument();
    }


    void renderQuizResult() {
        activity.quizUiRenderer.renderQuizResult(
                activity.latestQuizAttempt,
                activity.selectedDocument,
                activity.currentQuizQuestions,
                activity.selectedQuizAnswers
        );
    }


    String buildMockSummary(String ocrText){
        if(activity.isBlank(ocrText))
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


    private int estimateQuizQuestionCount(String text) {
        if (activity.isBlank(text)) {
            return 3;
        }

        String cleanText = text.trim();
        int wordCount = cleanText.split("\\s+").length;
        int paragraphCount = cleanText.split("\\n\\s*\\n|\\n").length;
        int sentenceCount = Math.max(1, cleanText.split("[.!?。！？]+").length);
        int averageWordsPerSentence = Math.max(1, wordCount / sentenceCount);

        int questionCount;
        if (wordCount < 120) {
            questionCount = 3;
        } else if (wordCount < 300) {
            questionCount = 5;
        } else if (wordCount < 700) {
            questionCount = 7;
        } else {
            questionCount = 10;
        }

        if (paragraphCount >= 6 || averageWordsPerSentence >= 22) {
            questionCount++;
        }

        return Math.max(3, Math.min(10, questionCount));
    }


    private int findFirstUnansweredQuestion() {
        for (int index = 0; index < activity.currentQuizQuestions.size(); index++) {
            StudyQuestion question = activity.currentQuizQuestions.get(index);
            String selectedAnswer = activity.selectedQuizAnswers.get(question.id);
            if (activity.isBlank(selectedAnswer)) {
                return index + 1;
            }
        }
        return -1;
    }


    private void setSummaryLoading(boolean loading) {
        setButtonLoading(R.id.buttonSummary, "Tóm tắt AI", "Đang tóm tắt...", loading);
    }


    private void setQuizLoading(boolean loading) {
        setButtonLoading(R.id.buttonCreateQuiz, "Tạo quiz", "Đang tạo quiz...", loading);
        setButtonLoading(R.id.buttonStartQuiz, "Tạo quiz", "Đang tạo quiz...", loading);
    }


    private void setSubmitLoading(boolean loading) {
        setButtonLoading(R.id.buttonCheckAnswers, "Nộp bài và chấm điểm", "Đang lưu kết quả...", loading);
    }


    private void setButtonLoading(int viewId, String normalText, String loadingText, boolean loading) {
        TextView button = activity.findViewById(viewId);
        if (button == null) {
            return;
        }
        button.setEnabled(!loading);
        button.setText(loading ? loadingText : normalText);
        button.animate().alpha(loading ? 0.65f : 1f).setDuration(160).start();
    }

}
