package hcmute.com.smarteduapp.ui.main;

import android.view.View;
import android.widget.EditText;
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
            Toast.makeText(activity, "Chưa có nội dung tài liệu để tóm tắt", Toast.LENGTH_SHORT).show();
            return;
        }

        long documentId = activity.selectedDocument.id;
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
            requestGeminiQuiz(ocrText, documentId);
        }

        @Override
        public void onError(Exception exception) {
            setQuizLoading(false);
            Toast.makeText(activity, "Không thể lưu OCR trước khi tạo quiz", Toast.LENGTH_SHORT).show();
        }
    });
    }


    void requestGeminiQuiz(String ocrText, long documentId) {
        int questionCount = estimateQuizQuestionCount(ocrText);
        setQuizLoading(true);
        Toast.makeText(activity, "Đang tạo " + questionCount + " câu quiz bằng Gemini...", Toast.LENGTH_SHORT).show();

        activity.geminiService.generateQuiz(
                ocrText,
                questionCount,
                new GeminiService.GeminiCallback() {
                    @Override
                    public void onSuccess(String quizJson) {
                        activity.runOnUiThread(() -> saveGeneratedQuestions(quizJson, documentId));
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
    if (activity.selectedDocument == null) {
        return;
    }

    try {
        List<StudyQuestion> questions = activity.quizParser.parse(quizJson, documentId);
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
                        activity.latestQuizAttempt = null;
                        activity.selectedQuizAnswers.clear();
                        showQuestions();
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
        showQuestions(true);
    }


    void showQuestions(boolean reloadFromDatabase) {
        activity.currentScreen = R.layout.screen_questions;
        activity.setContentView(R.layout.screen_questions);
        activity.applySystemBars();
        activity.bindClick(R.id.backProcessFromQuestions, activity::showProcessDocument);
        activity.bindClick(R.id.buttonCheckAnswers, this::submitQuizAnswers);
        if (reloadFromDatabase || activity.currentQuizQuestions.isEmpty()) {
            loadQuizQuestionsFromDatabase();
            return;
        }

        TextView title = activity.findViewById(R.id.quizTitle);
        title.setText("Quiz: " + activity.selectedDocument.title);
        activity.selectedQuizAnswers.clear();
        renderQuizQuestions(activity.currentQuizQuestions);
    }


    void loadQuizQuestionsFromDatabase() {
        if (activity.selectedDocument == null) {
            return;
        }

        TextView title = activity.findViewById(R.id.quizTitle);
        title.setText("Quiz: " + activity.selectedDocument.title);

        activity.studyRepository.getQuestionsByDocumentId(
                activity.selectedDocument.id,
                new RepositoryCallback<List<StudyQuestion>>() {
                    @Override
                    public void onSuccess(List<StudyQuestion> questions) {
                        activity.currentQuizQuestions = questions;
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
        activity.quizUiRenderer.renderQuizQuestions(questions, activity.selectedQuizAnswers);
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
        List<QuizAttemptAnswer> answers = buildAttemptAnswers(attempt);

        setSubmitLoading(true);
        activity.studyRepository.createQuizAttemptWithAnswers(
                attempt,
                answers,
                new RepositoryCallback<Long>() {
                    @Override
                    public void onSuccess(Long id) {
                        attempt.id = id;
                        activity.latestQuizAttempt = attempt;
                        showQuizResult();
                    }

                    @Override
                    public void onError(Exception exception) {
                        setSubmitLoading(false);
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
        activity.bindClick(R.id.buttonRetryQuiz, () -> showQuestions(false));
        renderQuizResult();
    }


    void goBackFromQuizResult() {
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
