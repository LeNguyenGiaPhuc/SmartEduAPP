package hcmute.com.smarteduapp.ui.main;

import android.view.View;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.widget.EditText;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

import hcmute.com.smarteduapp.R;
import hcmute.com.smarteduapp.data.local.entity.QuizAttempt;
import hcmute.com.smarteduapp.data.local.entity.QuizAttemptAnswer;
import hcmute.com.smarteduapp.data.local.entity.StudyQuestion;
import hcmute.com.smarteduapp.data.local.entity.StudySummary;
import hcmute.com.smarteduapp.data.local.entity.StudyPlan;
import hcmute.com.smarteduapp.data.local.entity.StudyPlanTask;
import hcmute.com.smarteduapp.data.repository.RepositoryCallback;
import hcmute.com.smarteduapp.service.ai.GeminiService;
import hcmute.com.smarteduapp.ui.common.SimpleCardAdapter;
import hcmute.com.smarteduapp.ui.common.UiViewFactory;

/**
 * Owns AI summary, AI chat and quiz flow.
 */
class StudyController {
    private final MainActivity activity;
    private final Handler quizTimerHandler = new Handler(Looper.getMainLooper());
    private Runnable quizTimerRunnable;
    private long activeQuestionId = -1L;
    private long activeQuestionStartedAt = 0L;
    private boolean questionTimerRunning;
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


    void pauseQuizTimingForBackground() {
        pauseCurrentQuestionTimer();
    }


    void resumeQuizTimingAfterForeground() {
        if (activity.currentScreen == R.layout.screen_questions
                && !activity.focusQuizSubmitting
                && activeQuestionId > 0
                && !questionTimerRunning) {
            activeQuestionStartedAt = SystemClock.elapsedRealtime();
            questionTimerRunning = true;
            scheduleQuestionTimer();
        }
    }


    private void startNewQuizAttemptTracking() {
        stopQuestionTimerRunnable();
        activeQuestionId = -1L;
        activeQuestionStartedAt = 0L;
        questionTimerRunning = false;
        activity.quizQuestionTimeMillis.clear();
        activity.quizAnswerChangeCounts.clear();
    }


    private void startCurrentQuestionTimer() {
        if (activity.currentQuizQuestions.isEmpty()) {
            updateCurrentQuestionTimeLabel();
            return;
        }
        int safeIndex = Math.max(0, Math.min(
                activity.currentQuizQuestionIndex,
                activity.currentQuizQuestions.size() - 1
        ));
        StudyQuestion question = activity.currentQuizQuestions.get(safeIndex);
        activeQuestionId = question.id;
        activeQuestionStartedAt = SystemClock.elapsedRealtime();
        questionTimerRunning = true;
        scheduleQuestionTimer();
        updateCurrentQuestionTimeLabel();
    }


    private void pauseCurrentQuestionTimer() {
        stopQuestionTimerRunnable();
        if (activeQuestionId > 0 && questionTimerRunning) {
            long elapsed = Math.max(0L, SystemClock.elapsedRealtime() - activeQuestionStartedAt);
            long saved = activity.quizQuestionTimeMillis.getOrDefault(activeQuestionId, 0L);
            activity.quizQuestionTimeMillis.put(activeQuestionId, saved + elapsed);
        }
        questionTimerRunning = false;
        updateCurrentQuestionTimeLabel();
    }


    private void scheduleQuestionTimer() {
        stopQuestionTimerRunnable();
        quizTimerRunnable = new Runnable() {
            @Override
            public void run() {
                updateCurrentQuestionTimeLabel();
                quizTimerHandler.postDelayed(this, 1000);
            }
        };
        quizTimerRunnable.run();
    }


    private void stopQuestionTimerRunnable() {
        if (quizTimerRunnable != null) {
            quizTimerHandler.removeCallbacks(quizTimerRunnable);
            quizTimerRunnable = null;
        }
    }


    private void updateCurrentQuestionTimeLabel() {
        TextView currentTime = activity.findViewById(R.id.quizCurrentTime);
        if (currentTime == null) {
            return;
        }
        if (activity.currentQuizQuestions.isEmpty() || activeQuestionId <= 0) {
            currentTime.setText("Thời gian câu hiện tại: 00:00");
            return;
        }
        currentTime.setText("Thời gian câu hiện tại: " + formatSeconds(getCurrentQuestionSeconds()));
    }


    private int getCurrentQuestionSeconds() {
        if (activeQuestionId <= 0) {
            return 0;
        }
        long total = activity.quizQuestionTimeMillis.getOrDefault(activeQuestionId, 0L);
        if (questionTimerRunning) {
            total += Math.max(0L, SystemClock.elapsedRealtime() - activeQuestionStartedAt);
        }
        return millisToSeconds(total);
    }


    private int getQuestionSeconds(long questionId) {
        long millis = activity.quizQuestionTimeMillis.getOrDefault(questionId, 0L);
        return millisToSeconds(millis);
    }


    private int calculateTotalQuestionSeconds() {
        int total = 0;
        for (StudyQuestion question : activity.currentQuizQuestions) {
            total += getQuestionSeconds(question.id);
        }
        return total;
    }


    private int millisToSeconds(long millis) {
        return (int) Math.max(0L, (millis + 999L) / 1000L);
    }


    private String formatSeconds(int totalSeconds) {
        int safeSeconds = Math.max(0, totalSeconds);
        int minutes = safeSeconds / 60;
        int seconds = safeSeconds % 60;
        return String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds);
    }


    private void handleAnswerSelected(StudyQuestion question, String previousOption, String newOption) {
        if (question == null || activity.isBlank(newOption)) {
            return;
        }
        if (!activity.isBlank(previousOption) && !previousOption.equalsIgnoreCase(newOption)) {
            int current = activity.quizAnswerChangeCounts.getOrDefault(question.id, 0);
            activity.quizAnswerChangeCounts.put(question.id, current + 1);
        }
    }


    private void openQuestions(boolean reloadFromDatabase) {
        startNewQuizAttemptTracking();
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
        startCurrentQuestionTimer();
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
        pauseCurrentQuestionTimer();
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
                        startCurrentQuestionTimer();
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
                activity.currentQuizQuestionIndex,
                this::handleAnswerSelected
        );
    }


    private void showPreviousQuestion() {
        if (activity.currentQuizQuestionIndex <= 0) {
            return;
        }

        pauseCurrentQuestionTimer();
        activity.currentQuizQuestionIndex--;
        renderQuizQuestions(activity.currentQuizQuestions);
        startCurrentQuestionTimer();
    }


    private void showNextQuestionOrSubmit() {
        if (activity.currentQuizQuestions.isEmpty()) {
            return;
        }

        if (activity.currentQuizQuestionIndex
                < activity.currentQuizQuestions.size() - 1) {
            pauseCurrentQuestionTimer();
            activity.currentQuizQuestionIndex++;
            renderQuizQuestions(activity.currentQuizQuestions);
            startCurrentQuestionTimer();
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

        pauseCurrentQuestionTimer();
        QuizAttempt attempt = activity.quizScoringService.score(
                activity.selectedDocument.id,
                activity.currentQuizQuestions,
                activity.selectedQuizAnswers
        );
        attempt.focusModeEnabled = activity.focusQuizModeEnabled;
        attempt.focusExitCount = activity.focusQuizExitCount;
        attempt.explanationUnlocked = activity.shouldUnlockQuizExplanation();
        attempt.totalTimeSeconds = calculateTotalQuestionSeconds();

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
            int timeSpentSeconds = getQuestionSeconds(question.id);
            int answerChangeCount = activity.quizAnswerChangeCounts.getOrDefault(question.id, 0);

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
                    now,
                    timeSpentSeconds,
                    answerChangeCount
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
        activity.bindClick(R.id.buttonAnalyzeQuiz, this::showQuizAnalysis);
        renderQuizResult();
    }


    void showQuizAnalysis() {
        activity.currentScreen = R.layout.screen_quiz_analysis;
        activity.setContentView(R.layout.screen_quiz_analysis);
        activity.applySystemBars();
        activity.bindClick(R.id.backQuizAnalysis, this::showQuizResult);
        activity.bindClick(R.id.buttonCreateStudyPlan, this::createOrOpenStudyPlan);
        renderQuizAnalysis();
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


    private void renderQuizAnalysis() {
        TextView summary = activity.findViewById(R.id.analysisSummary);
        RecyclerView container = activity.findViewById(R.id.analysisContainer);
        UiViewFactory.setupVerticalRecycler(container);

        QuizAttempt attempt = activity.latestQuizAttempt;
        int questionCount = activity.currentQuizQuestions.size();
        int totalSeconds = attempt == null ? calculateTotalQuestionSeconds() : attempt.totalTimeSeconds;
        int averageSeconds = questionCount == 0 ? 0 : totalSeconds / Math.max(1, questionCount);
        int focusExits = attempt == null ? activity.focusQuizExitCount : attempt.focusExitCount;

        summary.setText(
                "Tổng thời gian: " + formatSeconds(totalSeconds)
                        + " · Trung bình: " + formatSeconds(averageSeconds)
                        + " · Số câu: " + questionCount
                        + " · Thoát app: " + focusExits
        );

        SimpleCardAdapter adapter = new SimpleCardAdapter();
        List<SimpleCardAdapter.CardFactory> cards = new ArrayList<>();

        cards.add((parent, position) -> createAnalysisCard(
                "Câu làm lâu nhất",
                buildLongestQuestionText(),
                position
        ));
        cards.add((parent, position) -> createAnalysisCard(
                "Câu đổi đáp án nhiều nhất",
                buildMostChangedQuestionText(),
                position
        ));
        cards.add((parent, position) -> createAnalysisCard(
                "Danh sách câu trả lời sai",
                attempt != null && !attempt.explanationUnlocked
                        ? "Danh sách câu sai đang bị khóa vì bạn đã rời app trong chế độ tập trung."
                        : buildWrongAnswerText(),
                position
        ));
        cards.add((parent, position) -> createAnalysisCard(
                "Thời gian trung bình mỗi câu",
                formatSeconds(averageSeconds),
                position
        ));
        cards.add((parent, position) -> createAnalysisCard(
                "Số lần thoát app",
                (attempt != null && attempt.focusModeEnabled)
                        ? focusExits + " lần trong chế độ tập trung"
                        : "Không bật chế độ tập trung",
                position
        ));

        container.setAdapter(adapter);
        adapter.submit(cards);
    }


    private void createOrOpenStudyPlan() {
        QuizAttempt attempt = activity.latestQuizAttempt;
        if (attempt == null || activity.selectedDocument == null) {
            Toast.makeText(activity, "Chưa có dữ liệu quiz để tạo kế hoạch", Toast.LENGTH_SHORT).show();
            return;
        }

        activity.studyRepository.getStudyPlan(attempt.id, new RepositoryCallback<StudyPlan>() {
            @Override
            public void onSuccess(StudyPlan existingPlan) {
                if (existingPlan != null) {
                    loadStudyPlan(existingPlan);
                    return;
                }

                StudyPlan plan = new StudyPlan(
                        attempt.document_id,
                        attempt.id,
                        "Kế hoạch cải thiện: " + activity.selectedDocument.title,
                        buildStudyPlanOverview(attempt),
                        System.currentTimeMillis()
                );
                List<StudyPlanTask> tasks = buildStudyPlanTasks(plan, attempt);
                activity.studyRepository.createStudyPlanWithTasks(
                        plan,
                        tasks,
                        new RepositoryCallback<Long>() {
                            @Override
                            public void onSuccess(Long planId) {
                                plan.id = planId;
                                loadStudyPlan(plan);
                            }

                            @Override
                            public void onError(Exception exception) {
                                Toast.makeText(activity, "Không thể lưu kế hoạch học", Toast.LENGTH_SHORT).show();
                            }
                        }
                );
            }

            @Override
            public void onError(Exception exception) {
                Toast.makeText(activity, "Không thể kiểm tra kế hoạch học", Toast.LENGTH_SHORT).show();
            }
        });
    }


    private void loadStudyPlan(StudyPlan plan) {
        activity.currentStudyPlan = plan;
        activity.studyRepository.getStudyPlanTasks(plan.id, new RepositoryCallback<List<StudyPlanTask>>() {
            @Override
            public void onSuccess(List<StudyPlanTask> tasks) {
                activity.currentStudyPlanTasks = tasks;
                showStudyPlan();
            }

            @Override
            public void onError(Exception exception) {
                Toast.makeText(activity, "Không thể tải nhiệm vụ học", Toast.LENGTH_SHORT).show();
            }
        });
    }


    private String buildStudyPlanOverview(QuizAttempt attempt) {
        int questionCount = activity.currentQuizQuestions.size();
        int wrongCount = Math.max(0, attempt.wrongCount);
        int averageSeconds = questionCount == 0 ? 0 : attempt.totalTimeSeconds / questionCount;
        StringBuilder overview = new StringBuilder();
        overview.append("Kế hoạch được tạo từ ")
                .append(wrongCount)
                .append(" câu sai, thời gian trung bình ")
                .append(formatSeconds(averageSeconds));
        if (attempt.focusModeEnabled && attempt.focusExitCount > 0) {
            overview.append(" và ").append(attempt.focusExitCount).append(" lần rời app");
        }
        return overview.toString();
    }


    private List<StudyPlanTask> buildStudyPlanTasks(StudyPlan plan, QuizAttempt attempt) {
        List<StudyPlanTask> tasks = new ArrayList<>();
        int order = 1;
        int questionCount = activity.currentQuizQuestions.size();
        int averageSeconds = questionCount == 0 ? 0 : attempt.totalTimeSeconds / questionCount;

        tasks.add(new StudyPlanTask(
                0,
                order++,
                "Đọc lại tài liệu",
                "Xem lại nội dung của tài liệu trước khi làm lại quiz.",
                false
        ));

        if (attempt.wrongCount > 0) {
            String description = attempt.explanationUnlocked
                    ? "Xem lại các câu sai và đọc đáp án đúng, giải thích tương ứng."
                    : "Đọc lại tài liệu vì phần đáp án và giải thích đang bị khóa do rời app trong chế độ tập trung.";
            tasks.add(new StudyPlanTask(0, order++, "Ôn các câu trả lời sai", description, false));
        }

        StudyQuestion longestQuestion = findLongestQuestion();
        if (longestQuestion != null && getQuestionSeconds(longestQuestion.id) > Math.max(10, averageSeconds * 3 / 2)) {
            tasks.add(new StudyPlanTask(
                    0,
                    order++,
                    "Ôn câu làm lâu nhất",
                    "Tập trung vào câu " + longestQuestion.questionOrder
                            + " vì thời gian làm cao hơn mức trung bình.",
                    false
            ));
        }

        StudyQuestion changedQuestion = findMostChangedQuestion();
        if (changedQuestion != null && activity.quizAnswerChangeCounts.getOrDefault(changedQuestion.id, 0) > 0) {
            tasks.add(new StudyPlanTask(
                    0,
                    order++,
                    "Ôn câu chưa chắc đáp án",
                    "Xem lại câu " + changedQuestion.questionOrder
                            + " vì bạn đã đổi đáp án nhiều lần.",
                    false
            ));
        }

        if (attempt.focusModeEnabled && attempt.focusExitCount > 0) {
            tasks.add(new StudyPlanTask(
                    0,
                    order++,
                    "Làm lại quiz trong chế độ tập trung",
                    "Tắt thông báo và hoàn thành quiz mà không rời ứng dụng.",
                    false
            ));
        }

        tasks.add(new StudyPlanTask(
                0,
                order,
                "Làm lại quiz để kiểm tra tiến bộ",
                "Làm lại quiz sau khi ôn tập và so sánh số câu đúng với lần trước.",
                false
        ));
        return tasks;
    }


    void showStudyPlan() {
        activity.currentScreen = R.layout.screen_study_plan;
        activity.setContentView(R.layout.screen_study_plan);
        activity.applySystemBars();
        activity.bindClick(R.id.backStudyPlan, this::showQuizAnalysis);
        renderStudyPlan();
    }


    private void renderStudyPlan() {
        TextView overview = activity.findViewById(R.id.studyPlanOverview);
        TextView progress = activity.findViewById(R.id.studyPlanProgress);
        RecyclerView container = activity.findViewById(R.id.studyPlanTasks);
        UiViewFactory.setupVerticalRecycler(container);

        if (activity.currentStudyPlan == null) {
            overview.setText("Chưa có kế hoạch học.");
            progress.setText("0/0 nhiệm vụ hoàn thành");
            container.setAdapter(new SimpleCardAdapter());
            return;
        }

        overview.setText(activity.currentStudyPlan.overview);
        updateStudyPlanProgress(progress);

        SimpleCardAdapter adapter = new SimpleCardAdapter();
        List<SimpleCardAdapter.CardFactory> cards = new ArrayList<>();
        for (StudyPlanTask task : activity.currentStudyPlanTasks) {
            cards.add((parent, position) -> createStudyPlanTaskCard(task, progress, position));
        }
        container.setAdapter(adapter);
        adapter.submit(cards);
    }


    private void updateStudyPlanProgress(TextView progress) {
        int completed = 0;
        for (StudyPlanTask task : activity.currentStudyPlanTasks) {
            if (task.completed) {
                completed++;
            }
        }
        progress.setText(completed + "/" + activity.currentStudyPlanTasks.size() + " nhiệm vụ hoàn thành");
    }


    private MaterialCardView createStudyPlanTaskCard(StudyPlanTask task, TextView progress, int index) {
        MaterialCardView card = UiViewFactory.createCard(activity);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(
                UiViewFactory.dp(activity, 14),
                UiViewFactory.dp(activity, 10),
                UiViewFactory.dp(activity, 14),
                UiViewFactory.dp(activity, 10)
        );

        CheckBox checkBox = new CheckBox(activity);
        checkBox.setText(task.taskOrder + ". " + task.title);
        checkBox.setTextSize(16);
        checkBox.setTextColor(activity.getColor(R.color.ink));
        checkBox.setTypeface(null, android.graphics.Typeface.BOLD);
        checkBox.setChecked(task.completed);
        checkBox.setOnCheckedChangeListener((button, checked) -> {
            if (task.completed == checked) {
                return;
            }
            boolean oldValue = task.completed;
            task.completed = checked;
            updateStudyPlanProgress(progress);
            activity.studyRepository.updateStudyPlanTask(task.id, checked, new RepositoryCallback<Integer>() {
                @Override
                public void onSuccess(Integer result) {
                }

                @Override
                public void onError(Exception exception) {
                    task.completed = oldValue;
                    button.setChecked(oldValue);
                    updateStudyPlanProgress(progress);
                    Toast.makeText(activity, "Không thể cập nhật nhiệm vụ", Toast.LENGTH_SHORT).show();
                }
            });
        });

        TextView description = UiViewFactory.createText(activity, task.description, 14, R.color.ink_muted, false);
        description.setPadding(
                UiViewFactory.dp(activity, 8),
                0,
                UiViewFactory.dp(activity, 8),
                0
        );
        content.addView(checkBox);
        content.addView(description);
        card.addView(content);
        UiViewFactory.animateIn(card, index);
        return card;
    }


    private StudyQuestion findLongestQuestion() {
        StudyQuestion result = null;
        int longest = -1;
        for (StudyQuestion question : activity.currentQuizQuestions) {
            int seconds = getQuestionSeconds(question.id);
            if (seconds > longest) {
                longest = seconds;
                result = question;
            }
        }
        return result;
    }


    private StudyQuestion findMostChangedQuestion() {
        StudyQuestion result = null;
        int mostChanged = -1;
        for (StudyQuestion question : activity.currentQuizQuestions) {
            int changes = activity.quizAnswerChangeCounts.getOrDefault(question.id, 0);
            if (changes > mostChanged) {
                mostChanged = changes;
                result = question;
            }
        }
        return result;
    }


    private MaterialCardView createAnalysisCard(String titleText, String bodyText, int index) {
        MaterialCardView card = UiViewFactory.createCard(activity);
        card.setClickable(false);
        card.setFocusable(false);

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(
                UiViewFactory.dp(activity, 16),
                UiViewFactory.dp(activity, 14),
                UiViewFactory.dp(activity, 16),
                UiViewFactory.dp(activity, 14)
        );

        TextView title = UiViewFactory.createText(activity, titleText, 16, R.color.ink, true);
        TextView body = UiViewFactory.createText(activity, bodyText, 14, R.color.ink_muted, false);
        body.setPadding(0, UiViewFactory.dp(activity, 8), 0, 0);

        content.addView(title);
        content.addView(body);
        card.addView(content);
        UiViewFactory.animateIn(card, index);
        return card;
    }


    private String buildLongestQuestionText() {
        StudyQuestion longestQuestion = null;
        int longestSeconds = -1;
        for (StudyQuestion question : activity.currentQuizQuestions) {
            int seconds = getQuestionSeconds(question.id);
            if (seconds > longestSeconds) {
                longestSeconds = seconds;
                longestQuestion = question;
            }
        }
        if (longestQuestion == null) {
            return "Chưa có dữ liệu thời gian.";
        }
        return "Câu " + longestQuestion.questionOrder + " · "
                + formatSeconds(longestSeconds) + "\n" + longestQuestion.questionText;
    }


    private String buildMostChangedQuestionText() {
        StudyQuestion mostChangedQuestion = null;
        int mostChanges = -1;
        for (StudyQuestion question : activity.currentQuizQuestions) {
            int changes = activity.quizAnswerChangeCounts.getOrDefault(question.id, 0);
            if (changes > mostChanges) {
                mostChanges = changes;
                mostChangedQuestion = question;
            }
        }
        if (mostChangedQuestion == null || mostChanges <= 0) {
            return "Không có câu nào bị đổi đáp án.";
        }
        return "Câu " + mostChangedQuestion.questionOrder + " · "
                + mostChanges + " lần đổi đáp án\n" + mostChangedQuestion.questionText;
    }


    private String buildWrongAnswerText() {
        StringBuilder builder = new StringBuilder();
        for (StudyQuestion question : activity.currentQuizQuestions) {
            String selected = activity.selectedQuizAnswers.get(question.id);
            if (!question.correctOption.equalsIgnoreCase(selected == null ? "" : selected)) {
                if (builder.length() > 0) {
                    builder.append("\n\n");
                }
                builder.append("Câu ")
                        .append(question.questionOrder)
                        .append(": chọn ")
                        .append(activity.isBlank(selected) ? "chưa chọn" : selected)
                        .append(", đúng ")
                        .append(question.correctOption)
                        .append("\n")
                        .append(question.questionText);
            }
        }
        return builder.length() == 0 ? "Không có câu trả lời sai." : builder.toString();
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
