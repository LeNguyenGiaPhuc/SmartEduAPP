package hcmute.com.smarteduapp.ui.main;

import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.card.MaterialCardView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import hcmute.com.smarteduapp.R;
import hcmute.com.smarteduapp.data.local.entity.QuizAttempt;
import hcmute.com.smarteduapp.data.local.entity.StudyDocument;
import hcmute.com.smarteduapp.data.local.entity.StudyQuestion;
import hcmute.com.smarteduapp.data.local.entity.StudySummary;
import hcmute.com.smarteduapp.data.local.entity.Subject;
import hcmute.com.smarteduapp.data.repository.RepositoryCallback;
import hcmute.com.smarteduapp.ui.common.UiViewFactory;

/**
 * Owns the learning-history screen.
 * It loads data from repositories and renders document, summary, question and quiz history.
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
        int questionCount;
        int attemptCount;
        QuizAttempt latestAttempt;
    }

    void showHistory() {
        activity.currentScreen = R.layout.screen_history;
        activity.setContentView(R.layout.screen_history);
        activity.applySystemBars();
        activity.bindClick(R.id.backHomeFromHistory, activity::showHome);
        loadHistoryFromDatabase();
    }

    private void loadHistoryFromDatabase() {
        loadLearningHistorySubjects();
    }

    private void loadLegacyQuizHistoryFromDatabase() {
        activity.studyRepository.getAllQuizAttempts(new RepositoryCallback<List<QuizAttempt>>() {
            @Override
            public void onSuccess(List<QuizAttempt> attempts) {
                renderHistory(attempts);
            }

            @Override
            public void onError(Exception exception) {
                Toast.makeText(activity, "Không thể tải lịch sử", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void renderHistory(List<QuizAttempt> attempts) {
        LinearLayout container = activity.findViewById(R.id.historyContainer);
        container.removeAllViews();

        if (attempts.isEmpty()) {
            TextView empty = UiViewFactory.createText(
                    activity,
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

            int total = attempt.correctCount + attempt.wrongCount;
            TextView title = UiViewFactory.createText(
                    activity,
                    "Quiz tài liệu #" + attempt.document_id,
                    16,
                    R.color.ink,
                    true
            );

            TextView score = UiViewFactory.createText(
                    activity,
                    attempt.correctCount + "/" + total + " câu đúng · "
                            + String.format(Locale.US, "%.1f", attempt.score) + " điểm",
                    14,
                    R.color.brand_blue_dark,
                    true
            );
            score.setPadding(0, UiViewFactory.dp(activity, 5), 0, 0);

            TextView detail = UiViewFactory.createText(
                    activity,
                    "Sai " + attempt.wrongCount + " câu · " + format.format(new Date(attempt.completedAt)),
                    13,
                    R.color.ink_muted,
                    false
            );
            detail.setPadding(0, UiViewFactory.dp(activity, 5), 0, 0);

            content.addView(title);
            content.addView(score);
            content.addView(detail);
            card.addView(content);
            container.addView(card, UiViewFactory.verticalMargin(activity, 12));
            UiViewFactory.animateIn(card, container.getChildCount());
        }
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
                loadLearningHistoryQuestions(subjects, documents, summaries);
            }

            @Override
            public void onError(Exception exception) {
                Toast.makeText(activity, "Không thể tải tóm tắt trong lịch sử", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadLearningHistoryQuestions(List<Subject> subjects, List<StudyDocument> documents,
                                              List<StudySummary> summaries) {
        activity.studyRepository.getAllQuestions(new RepositoryCallback<List<StudyQuestion>>() {
            @Override
            public void onSuccess(List<StudyQuestion> questions) {
                loadLearningHistoryAttempts(subjects, documents, summaries, questions);
            }

            @Override
            public void onError(Exception exception) {
                Toast.makeText(activity, "Không thể tải câu hỏi trong lịch sử", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadLearningHistoryAttempts(List<Subject> subjects, List<StudyDocument> documents,
                                             List<StudySummary> summaries, List<StudyQuestion> questions) {
        activity.studyRepository.getAllQuizAttempts(new RepositoryCallback<List<QuizAttempt>>() {
            @Override
            public void onSuccess(List<QuizAttempt> attempts) {
                renderLearningHistory(subjects, documents, summaries, questions, attempts);
            }

            @Override
            public void onError(Exception exception) {
                Toast.makeText(activity, "Không thể tải kết quả quiz trong lịch sử", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void renderLearningHistory(List<Subject> subjects, List<StudyDocument> documents,
                                       List<StudySummary> summaries, List<StudyQuestion> questions,
                                       List<QuizAttempt> attempts) {
        LinearLayout container = activity.findViewById(R.id.historyContainer);
        container.removeAllViews();

        if (documents.isEmpty()) {
            TextView empty = UiViewFactory.createText(
                    activity,
                    "Chưa có tài liệu học tập nào.",
                    15,
                    R.color.ink_muted,
                    false
            );
            container.addView(empty);
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

        for (StudyQuestion question : questions) {
            LearningHistoryItem item = itemByDocumentId.get(question.document_id);
            if (item != null) {
                item.questionCount++;
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

        int index = 0;
        for (LearningHistoryItem item : itemByDocumentId.values()) {
            renderLearningHistoryCard(container, item, index++);
        }
    }

    private void renderLearningHistoryCard(LinearLayout container, LearningHistoryItem item, int index) {
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
                ocrState + " · " + item.summaryCount + " tóm tắt · "
                        + item.questionCount + " câu hỏi",
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

        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, UiViewFactory.dp(activity, 12), 0, 0);
        addHistoryAction(actions, "Mở tài liệu", () -> openHistoryDocument(item));
        if (item.summaryCount > 0) {
            addHistoryAction(actions, "Tóm tắt", () -> openHistorySummary(item));
        }
        if (item.questionCount > 0) {
            addHistoryAction(actions, "Câu hỏi", () -> openHistoryQuestionBank(item));
        }
        content.addView(actions);

        card.addView(content);
        container.addView(card, UiViewFactory.verticalMargin(activity, 12));
        UiViewFactory.animateIn(card, index);
    }

    private void addHistoryAction(LinearLayout actions, String label, Runnable action) {
        TextView actionView = UiViewFactory.createText(activity, label, 13, R.color.brand_blue_dark, true);
        actionView.setGravity(android.view.Gravity.CENTER);
        actionView.setBackgroundResource(R.drawable.bg_action_chip_blue);
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
        activity.selectedSubjectId = item.document.subject_id;
        activity.selectedSubject = item.subject;
        activity.selectedDocument = item.document;
        activity.showProcessDocument();
    }

    private void openHistorySummary(LearningHistoryItem item) {
        activity.selectedSubjectId = item.document.subject_id;
        activity.selectedSubject = item.subject;
        activity.selectedDocument = item.document;
        activity.showSummary();
    }

    private void openHistoryQuestionBank(LearningHistoryItem item) {
        activity.selectedSubjectId = item.document.subject_id;
        activity.selectedSubject = item.subject;
        activity.selectedDocument = item.document;
        activity.renderQuestionBankScreen();
    }
}
