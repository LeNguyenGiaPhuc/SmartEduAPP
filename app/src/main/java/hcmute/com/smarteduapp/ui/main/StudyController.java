package hcmute.com.smarteduapp.ui.main;

import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import java.util.List;
import java.util.Locale;

import hcmute.com.smarteduapp.R;
import hcmute.com.smarteduapp.data.local.entity.QuizAttempt;
import hcmute.com.smarteduapp.data.local.entity.StudyQuestion;
import hcmute.com.smarteduapp.data.local.entity.StudySummary;
import hcmute.com.smarteduapp.data.repository.RepositoryCallback;
import hcmute.com.smarteduapp.service.ai.GeminiService;
import hcmute.com.smarteduapp.ui.common.UiViewFactory;
import hcmute.com.smarteduapp.ui.study.QuestionBankRenderer;

/**
 * Owns AI summary, AI chat, question bank and quiz flow.
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
        activity.bindClick(R.id.buttonStartQuiz, this::showQuestionBank);
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
                    summaryText.setText("Chưa có nội dung AI. Hãy tạo tóm tắt hoặc giải thích!");
                    return;
                }

                StudySummary latestSummary = summaries.get(0);
                activity.latestDisplayedSummary = latestSummary;
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
                Toast.makeText(activity, "Không thể lưu OCR trước khi tóm tắt", Toast.LENGTH_SHORT).show();
            }
        });
    }


    void requestGeminiSummary(String ocrText, long documentId) {
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
            Toast.makeText(activity, "Không thể lưu OCR trước khi tạo quiz", Toast.LENGTH_SHORT).show();
        }
    });
    }


    void requestGeminiQuiz(String ocrText, long documentId) {
        Toast.makeText(activity, "Đang tạo quiz bằng Gemini...", Toast.LENGTH_SHORT).show();

        activity.geminiService.generateQuiz(
                ocrText,
                new GeminiService.GeminiCallback() {
                    @Override
                    public void onSuccess(String quizJson) {
                        activity.runOnUiThread(() -> saveGeneratedQuestions(quizJson, documentId, ocrText));
                    }

                    @Override
                    public void onError(Exception exception) {
                        activity.runOnUiThread(() -> {
                            Toast.makeText(
                                    activity,
                                    "Gemini lỗi, đã dùng câu hỏi mẫu",
                                    Toast.LENGTH_SHORT
                            ).show();
                            saveGeneratedQuestions(
                                    activity.quizParser.buildFallbackQuestions(ocrText, documentId)
                            );
                        });
                    }
                }
        );
    }


    void saveGeneratedQuestions(String quizJson) {
    if (activity.selectedDocument == null) {
        return;
    }
    saveGeneratedQuestions(quizJson, activity.selectedDocument.id, activity.selectedDocument.ocrText);
    }


    void saveGeneratedQuestions(String quizJson, long documentId, String fallbackText) {
    if (activity.selectedDocument == null) {
        return;
    }

    try {
        List<StudyQuestion> questions = activity.quizParser.parse(quizJson, documentId);
        if (questions.isEmpty()) {
            Toast.makeText(activity, "Gemini trả danh sách rỗng, đã dùng câu hỏi mẫu", Toast.LENGTH_SHORT).show();
            questions = activity.quizParser.buildFallbackQuestions(fallbackText, documentId);
        }

        saveGeneratedQuestions(questions, documentId);
    } catch (Exception exception) {
        android.util.Log.e("GeminiQuiz", "Raw quiz JSON: " + quizJson, exception);
        Toast.makeText(activity, "Không thể đọc JSON Gemini, đã dùng câu hỏi mẫu", Toast.LENGTH_SHORT).show();
        saveGeneratedQuestions(activity.quizParser.buildFallbackQuestions(fallbackText, documentId));
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
            Toast.makeText(activity, "Không tạo được câu hỏi", Toast.LENGTH_SHORT).show();
            return;
        }

        activity.studyRepository.replaceQuestions(
                documentId,
                questions,
                new RepositoryCallback<Integer>() {
                    @Override
                    public void onSuccess(Integer result) {
                        renderQuestionBankScreen();
                    }

                    @Override
                    public void onError(Exception exception) {
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
                    Toast.makeText(activity, "Không thể lưu tóm tắt", Toast.LENGTH_SHORT).show();
                }
            }
    );
    }


    void showQuestionBank() {
        if (activity.selectedDocument == null) {
            return;
        }

        activity.ensureCurrentDocumentHasAttachments(
                "Tài liệu không còn file đính kèm, không thể mở bộ câu hỏi",
                this::renderQuestionBankScreen
        );
    }


    void renderQuestionBankScreen() {
        activity.currentScreen = R.layout.screen_question_bank;
        activity.setContentView(R.layout.screen_question_bank);
        activity.applySystemBars();
        activity.bindClick(R.id.backProcessFromBank, activity::showProcessDocument);
        activity.bindClick(R.id.buttonStartQuizFromBank, this::showQuestions);
        activity.bindClick(R.id.buttonRegenerateQuestions, this::createQuizFromCurrentDocument);
        loadQuestionBankFromDatabase();
    }


    void loadQuestionBankFromDatabase() {
        if (activity.selectedDocument == null) {
            return;
        }

        TextView subtitle = activity.findViewById(R.id.questionBankSubtitle);
        subtitle.setText(activity.selectedDocument.title + " · Danh sách câu hỏi đã lưu");

        activity.studyRepository.getQuestionsByDocumentId(
                activity.selectedDocument.id,
                new RepositoryCallback<List<StudyQuestion>>() {
                    @Override
                    public void onSuccess(List<StudyQuestion> questions) {
                        renderQuestionBank(questions);
                    }

                    @Override
                    public void onError(Exception exception) {
                        Toast.makeText(activity, "Không thể tải câu hỏi", Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }


    void renderQuestionBank(List<StudyQuestion> questions) {
        activity.questionBankRenderer.render(questions, new QuestionBankRenderer.QuestionActionListener() {
            @Override
            public void onEditQuestion(StudyQuestion question) {
                showQuestionEditor(question);
            }

            @Override
            public void onDeleteQuestion(StudyQuestion question) {
                confirmDeleteQuestion(question);
            }
        });
    }


    void showQuestionEditor(StudyQuestion question) {
        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        int padding = UiViewFactory.dp(activity, 8);
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

        new AlertDialog.Builder(activity)
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


    EditText createDialogInput(String hint, String value) {
        EditText input = new EditText(activity);
        input.setHint(hint);
        input.setText(value == null ? "" : value);
        input.setSingleLine(false);
        input.setMinLines(1);
        input.setTextColor(activity.getColor(R.color.ink));
        input.setTextSize(14);
        return input;
    }


    void updateQuestion(StudyQuestion question) {
        if (activity.isBlank(question.questionText)
                || activity.isBlank(question.optionA)
                || activity.isBlank(question.optionB)
                || activity.isBlank(question.optionC)
                || activity.isBlank(question.optionD)) {
            Toast.makeText(activity, "Câu hỏi và đáp án không được để trống", Toast.LENGTH_SHORT).show();
            return;
        }

        activity.studyRepository.updateQuestion(question, new RepositoryCallback<Integer>() {
            @Override
            public void onSuccess(Integer result) {
                Toast.makeText(activity, "Đã cập nhật câu hỏi", Toast.LENGTH_SHORT).show();
                loadQuestionBankFromDatabase();
            }

            @Override
            public void onError(Exception exception) {
                Toast.makeText(activity, "Không thể cập nhật câu hỏi", Toast.LENGTH_SHORT).show();
            }
        });
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


    void confirmDeleteQuestion(StudyQuestion question) {
        new AlertDialog.Builder(activity)
                .setTitle("Xóa câu hỏi")
                .setMessage("Bạn có chắc muốn xóa câu hỏi này không?")
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Xóa", (dialog, which) -> deleteQuestion(question))
                .show();
    }


    void deleteQuestion(StudyQuestion question) {
        activity.studyRepository.deleteQuestion(question, new RepositoryCallback<Integer>() {
            @Override
            public void onSuccess(Integer result) {
                Toast.makeText(activity, "Đã xóa câu hỏi", Toast.LENGTH_SHORT).show();
                loadQuestionBankFromDatabase();
            }

            @Override
            public void onError(Exception exception) {
                Toast.makeText(activity, "Không thể xóa câu hỏi", Toast.LENGTH_SHORT).show();
            }
        });
    }


    void showQuestions() {
        activity.currentScreen = R.layout.screen_questions;
        activity.setContentView(R.layout.screen_questions);
        activity.applySystemBars();
        activity.bindClick(R.id.backProcessFromQuestions, activity::showProcessDocument);
        activity.bindClick(R.id.buttonCheckAnswers, this::submitQuizAnswers);
        loadQuizQuestionsFromDatabase();
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

        QuizAttempt attempt = activity.quizScoringService.score(
                activity.selectedDocument.id,
                activity.currentQuizQuestions,
                activity.selectedQuizAnswers
        );

        activity.studyRepository.createQuizAttempt(
                attempt,
                new RepositoryCallback<Long>() {
                    @Override
                    public void onSuccess(Long id) {
                        attempt.id = id;
                        activity.latestQuizAttempt = attempt;
                        showQuizResult();
                    }

                    @Override
                    public void onError(Exception exception) {
                        Toast.makeText(activity, "Không thể lưu kết quả quiz", Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }


    void showQuizResult() {
        activity.currentScreen = R.layout.screen_quiz_result;
        activity.setContentView(R.layout.screen_quiz_result);
        activity.applySystemBars();
        activity.bindClick(R.id.backQuestionBank, this::showQuestionBank);
        activity.bindClick(R.id.buttonViewHistory, activity::showHistory);
        activity.bindClick(R.id.buttonRetryQuiz, this::showQuestions);
        renderQuizResult();
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


    String normalizeCorrectOption(String option) {
        if (activity.isBlank(option)) {
            return "A";
        }
        String normalized = option.trim().toUpperCase(Locale.US);
        if (normalized.startsWith("A")) return "A";
        if (normalized.startsWith("B")) return "B";
        if (normalized.startsWith("C")) return "C";
        if (normalized.startsWith("D")) return "D";
        return "A";
    }


}
