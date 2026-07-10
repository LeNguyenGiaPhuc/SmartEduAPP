package hcmute.com.smarteduapp.ui.main;

import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import hcmute.com.smarteduapp.R;
import hcmute.com.smarteduapp.data.local.entity.QuizAttempt;
import hcmute.com.smarteduapp.data.local.entity.QuizAttemptAnswer;
import hcmute.com.smarteduapp.data.local.entity.StudyDocument;
import hcmute.com.smarteduapp.data.local.entity.StudyQuestion;
import hcmute.com.smarteduapp.data.local.entity.StudySummary;
import hcmute.com.smarteduapp.data.local.entity.Subject;
import hcmute.com.smarteduapp.data.repository.RepositoryCallback;
import hcmute.com.smarteduapp.ui.common.SimpleCardAdapter;
import hcmute.com.smarteduapp.ui.common.UiViewFactory;

/**
 * Owns the learning-history screen.
 * It loads document, summary, question and quiz attempt data from SQLite.
 */
class HistoryController {
    private final MainActivity activity;

    HistoryController(MainActivity activity) {
        this.activity = activity;
    }

    private static class LearningHistoryItem {
        StudyDocument document;
        Subject subject;
        int summaryCount;
        int attemptCount;
        QuizAttempt latestAttempt;
    }

    void showHistory() {
        activity.currentScreen = R.layout.screen_history;
        activity.setContentView(R.layout.screen_history);
        activity.applySystemBars();
        activity.bindClick(R.id.backHomeFromHistory, activity::showHome);
        loadLearningHistorySubjects();
    }

    private void loadLearningHistorySubjects() {
        activity.subjectRepository.getAll(new RepositoryCallback<List<Subject>>() {
            @Override
            public void onSuccess(List<Subject> subjects) {
                loadLearningHistoryDocuments(subjects);
            }

            @Override
            public void onError(Exception exception) {
                Toast.makeText(activity, "Không thể tải môn học trong lịch sử", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadLearningHistoryDocuments(List<Subject> subjects) {
        activity.documentRepository.getAll(new RepositoryCallback<List<StudyDocument>>() {
            @Override
            public void onSuccess(List<StudyDocument> documents) {
                loadLearningHistorySummaries(subjects, documents);
            }

            @Override
            public void onError(Exception exception) {
                Toast.makeText(activity, "Không thể tải tài liệu trong lịch sử", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadLearningHistorySummaries(List<Subject> subjects, List<StudyDocument> documents) {
        activity.studyRepository.getAllSummaries(new RepositoryCallback<List<StudySummary>>() {
            @Override
            public void onSuccess(List<StudySummary> summaries) {
                loadLearningHistoryAttempts(subjects, documents, summaries);
            }

            @Override
            public void onError(Exception exception) {
                Toast.makeText(activity, "Không thể tải tóm tắt trong lịch sử", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadLearningHistoryAttempts(List<Subject> subjects, List<StudyDocument> documents,
                                             List<StudySummary> summaries) {
        activity.studyRepository.getAllQuizAttempts(new RepositoryCallback<List<QuizAttempt>>() {
            @Override
            public void onSuccess(List<QuizAttempt> attempts) {
                renderLearningHistory(subjects, documents, summaries, attempts);
            }

            @Override
            public void onError(Exception exception) {
                Toast.makeText(activity, "Không thể tải kết quả quiz trong lịch sử", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void renderLearningHistory(List<Subject> subjects, List<StudyDocument> documents,
                                       List<StudySummary> summaries, List<QuizAttempt> attempts) {
        RecyclerView container = activity.findViewById(R.id.historyContainer);
        UiViewFactory.setupVerticalRecycler(container);

        SimpleCardAdapter adapter = new SimpleCardAdapter();
        List<SimpleCardAdapter.CardFactory> cards = new ArrayList<>();

        if (documents.isEmpty()) {
            cards.add((parent, position) -> createEmptyHistoryView());
            adapter.submit(cards);
            container.setAdapter(adapter);
            return;
        }

        Map<Long, Subject> subjectById = new HashMap<>();
        for (Subject subject : subjects) {
            subjectById.put(subject.id, subject);
        }

        Map<Long, LearningHistoryItem> itemByDocumentId = new LinkedHashMap<>();
        for (StudyDocument document : documents) {
            LearningHistoryItem item = new LearningHistoryItem();
            item.document = document;
            item.subject = subjectById.get(document.subject_id);
            itemByDocumentId.put(document.id, item);
        }

        for (StudySummary summary : summaries) {
            LearningHistoryItem item = itemByDocumentId.get(summary.document_id);
            if (item != null) {
                item.summaryCount++;
            }
        }

        for (QuizAttempt attempt : attempts) {
            LearningHistoryItem item = itemByDocumentId.get(attempt.document_id);
            if (item != null) {
                item.attemptCount++;
                if (item.latestAttempt == null || attempt.completedAt > item.latestAttempt.completedAt) {
                    item.latestAttempt = attempt;
                }
            }
        }

        for (LearningHistoryItem item : itemByDocumentId.values()) {
            cards.add((parent, position) -> createLearningHistoryCard(item, position));
        }

        adapter.submit(cards);
        container.setAdapter(adapter);
    }

    private TextView createEmptyHistoryView() {
        TextView empty = UiViewFactory.createText(
                activity,
                "Chưa có tài liệu học tập nào.",
                15,
                R.color.ink_muted,
                false
        );
        empty.setGravity(android.view.Gravity.CENTER);
        empty.setPadding(0, UiViewFactory.dp(activity, 18), 0, UiViewFactory.dp(activity, 18));
        return empty;
    }

    private MaterialCardView createLearningHistoryCard(LearningHistoryItem item, int index) {
        SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
        MaterialCardView card = UiViewFactory.createCard(activity);
        card.setOnClickListener(v -> openHistoryDocument(item));

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(
                UiViewFactory.dp(activity, 16),
                UiViewFactory.dp(activity, 14),
                UiViewFactory.dp(activity, 16),
                UiViewFactory.dp(activity, 14)
        );

        String subjectName = item.subject == null ? "Chưa rõ môn học" : item.subject.name;
        TextView title = UiViewFactory.createText(activity, item.document.title, 16, R.color.ink, true);
        TextView subject = UiViewFactory.createText(activity, "Môn: " + subjectName, 13, R.color.ink_muted, false);
        subject.setPadding(0, UiViewFactory.dp(activity, 5), 0, 0);

        String ocrState = activity.isBlank(item.document.ocrText) ? "Chưa OCR" : "Đã lưu OCR";
        TextView studyState = UiViewFactory.createText(
                activity,
                ocrState + " · " + item.summaryCount + " tóm tắt",
                13,
                R.color.ink_muted,
                false
        );
        studyState.setPadding(0, UiViewFactory.dp(activity, 5), 0, 0);

        content.addView(title);
        content.addView(subject);
        content.addView(studyState);

        if (item.latestAttempt != null) {
            int total = item.latestAttempt.correctCount + item.latestAttempt.wrongCount;
            TextView quiz = UiViewFactory.createText(
                    activity,
                    "Quiz gần nhất: " + item.latestAttempt.correctCount + "/" + total
                            + " đúng · " + String.format(Locale.US, "%.1f", item.latestAttempt.score)
                            + " điểm · " + item.attemptCount + " lần làm · "
                            + format.format(new Date(item.latestAttempt.completedAt)),
                    13,
                    R.color.brand_blue_dark,
                    true
            );
            quiz.setPadding(0, UiViewFactory.dp(activity, 7), 0, 0);
            content.addView(quiz);
        } else {
            TextView quiz = UiViewFactory.createText(activity, "Chưa có kết quả quiz", 13, R.color.ink_muted, false);
            quiz.setPadding(0, UiViewFactory.dp(activity, 7), 0, 0);
            content.addView(quiz);
        }

        LinearLayout primaryActions = createActionRow();
        if (item.summaryCount > 0) {
            addHistoryAction(primaryActions, "Tóm tắt", R.color.brand_blue_dark, R.drawable.bg_action_chip_blue,
                    () -> openHistorySummary(item));
        }
        if (item.latestAttempt != null) {
            addHistoryAction(primaryActions, "Xem quiz", R.color.brand_blue_dark, R.drawable.bg_action_chip_blue,
                    () -> openQuizAttemptResult(item));
        }
        if (primaryActions.getChildCount() > 0) {
            content.addView(primaryActions);
        }

        LinearLayout dangerActions = createActionRow();
        if (item.attemptCount > 0) {
            addHistoryAction(dangerActions, "Xóa lần quiz", R.color.danger, R.drawable.bg_action_chip_danger,
                    () -> confirmDeleteQuizHistory(item));
        }
        if (dangerActions.getChildCount() > 0) {
            content.addView(dangerActions);
        }

        card.addView(content);
        UiViewFactory.animateIn(card, index);
        return card;
    }

    private LinearLayout createActionRow() {
        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, UiViewFactory.dp(activity, 12), 0, 0);
        return actions;
    }

    private void addHistoryAction(LinearLayout actions, String label, int colorRes, int backgroundRes,
                                  Runnable action) {
        TextView actionView = UiViewFactory.createText(activity, label, 13, colorRes, true);
        actionView.setGravity(android.view.Gravity.CENTER);
        actionView.setBackgroundResource(backgroundRes);
        actionView.setPadding(
                UiViewFactory.dp(activity, 10),
                UiViewFactory.dp(activity, 8),
                UiViewFactory.dp(activity, 10),
                UiViewFactory.dp(activity, 8)
        );
        UiViewFactory.applyPressEffect(actionView);
        actionView.setOnClickListener(v -> action.run());

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        );
        params.setMargins(0, 0, UiViewFactory.dp(activity, 8), 0);
        actions.addView(actionView, params);
    }

    private void openHistoryDocument(LearningHistoryItem item) {
        setSelectedHistoryDocument(item);
        activity.showProcessDocument();
    }

    private void openHistorySummary(LearningHistoryItem item) {
        setSelectedHistoryDocument(item);
        activity.showSummary();
    }

    private void openQuizAttemptResult(LearningHistoryItem item) {
        openQuizAttemptResult(item.document, item.subject, item.latestAttempt, false);
    }

    void openQuizAttemptResult(StudyDocument document, QuizAttempt attempt, boolean openedFromHome) {
        openQuizAttemptResult(document, null, attempt, openedFromHome);
    }

    private void openQuizAttemptResult(StudyDocument document, Subject subject, QuizAttempt attempt, boolean openedFromHome) {
        if (document == null || attempt == null) {
            Toast.makeText(activity, "Tài liệu này chưa có lần làm quiz", Toast.LENGTH_SHORT).show();
            return;
        }

        activity.studyRepository.getQuizAttemptAnswers(
                attempt.id,
                new RepositoryCallback<List<QuizAttemptAnswer>>() {
                    @Override
                    public void onSuccess(List<QuizAttemptAnswer> answers) {
                        if (answers.isEmpty()) {
                            Toast.makeText(activity, "Lần làm quiz cũ chưa có chi tiết đáp án", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        activity.selectedSubjectId = document.subject_id;
                        activity.selectedSubject = subject;
                        activity.selectedDocument = document;
                        activity.documentOpenedFromHistory = !openedFromHome;
                        activity.quizResultOpenedFromHome = openedFromHome;
                        activity.latestQuizAttempt = attempt;
                        activity.currentQuizQuestions = convertAnswersToQuestions(answers);
                        activity.selectedQuizAnswers.clear();
                        for (QuizAttemptAnswer answer : answers) {
                            long questionId = answer.question_id > 0 ? answer.question_id : answer.id;
                            activity.selectedQuizAnswers.put(questionId, answer.selectedOption);
                        }
                        activity.showQuizResult();
                    }

                    @Override
                    public void onError(Exception exception) {
                        Toast.makeText(activity, "Không thể tải chi tiết lần làm quiz", Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private List<StudyQuestion> convertAnswersToQuestions(List<QuizAttemptAnswer> answers) {
        List<StudyQuestion> questions = new ArrayList<>();
        for (QuizAttemptAnswer answer : answers) {
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
            questions.add(question);
        }
        return questions;
    }

    private void setSelectedHistoryDocument(LearningHistoryItem item) {
        activity.selectedSubjectId = item.document.subject_id;
        activity.selectedSubject = item.subject;
        activity.selectedDocument = item.document;
        activity.documentOpenedFromHistory = true;
    }

    private void confirmDeleteQuizHistory(LearningHistoryItem item) {
        new AlertDialog.Builder(activity)
                .setTitle("Xóa lần làm quiz?")
                .setMessage("Chỉ xóa lần làm quiz gần nhất đang hiển thị. Tài liệu, OCR, tóm tắt và câu hỏi vẫn giữ nguyên.")
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Xóa", (dialog, which) -> deleteQuizHistory(item))
                .show();
    }

    private void deleteQuizHistory(LearningHistoryItem item) {
        if (item.latestAttempt == null) {
            Toast.makeText(activity, "Không có lần làm quiz để xóa", Toast.LENGTH_SHORT).show();
            return;
        }

        activity.studyRepository.deleteQuizAttempt(
                item.latestAttempt,
                new RepositoryCallback<Integer>() {
                    @Override
                    public void onSuccess(Integer result) {
                        Toast.makeText(activity, "Đã xóa lần làm quiz", Toast.LENGTH_SHORT).show();
                        showHistory();
                    }

                    @Override
                    public void onError(Exception exception) {
                        Toast.makeText(activity, "Không thể xóa lịch sử quiz", Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

}
