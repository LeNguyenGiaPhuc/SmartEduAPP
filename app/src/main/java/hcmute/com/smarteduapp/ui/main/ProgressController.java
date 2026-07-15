package hcmute.com.smarteduapp.ui.main;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.card.MaterialCardView;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import hcmute.com.smarteduapp.R;
import hcmute.com.smarteduapp.data.local.entity.QuizAttempt;
import hcmute.com.smarteduapp.data.local.entity.StudyDocument;
import hcmute.com.smarteduapp.data.local.entity.Subject;
import hcmute.com.smarteduapp.data.repository.RepositoryCallback;
import hcmute.com.smarteduapp.ui.common.UiViewFactory;

/** Loads and renders the local learning progress dashboard. */
class ProgressController {
    private final MainActivity activity;

    ProgressController(MainActivity activity) {
        this.activity = activity;
    }

    void showProgress() {
        activity.currentScreen = R.layout.screen_progress;
        activity.setContentView(R.layout.screen_progress);
        activity.applySystemBars();
        activity.bindClick(R.id.backFromProgress, activity::showHome);
        loadProgress();
    }

    private void loadProgress() {
        activity.subjectRepository.getAll(new RepositoryCallback<List<Subject>>() {
            @Override
            public void onSuccess(List<Subject> subjects) {
                activity.documentRepository.getAll(new RepositoryCallback<List<StudyDocument>>() {
                    @Override
                    public void onSuccess(List<StudyDocument> documents) {
                        activity.studyRepository.getAllQuizAttempts(new RepositoryCallback<List<QuizAttempt>>() {
                            @Override
                            public void onSuccess(List<QuizAttempt> attempts) {
                                render(subjects, documents, attempts);
                            }

                            @Override
                            public void onError(Exception exception) {
                                render(subjects, documents, java.util.Collections.emptyList());
                            }
                        });
                    }

                    @Override
                    public void onError(Exception exception) {
                        render(subjects, java.util.Collections.emptyList(), java.util.Collections.emptyList());
                    }
                });
            }

            @Override
            public void onError(Exception exception) {
                render(java.util.Collections.emptyList(), java.util.Collections.emptyList(), java.util.Collections.emptyList());
            }
        });
    }

    private void render(
            List<Subject> subjects,
            List<StudyDocument> documents,
            List<QuizAttempt> attempts
    ) {
        int totalCorrect = 0;
        int totalQuestions = 0;
        float totalScore = 0f;
        for (QuizAttempt attempt : attempts) {
            totalCorrect += attempt.correctCount;
            totalQuestions += attempt.correctCount + attempt.wrongCount;
            totalScore += attempt.score;
        }

        TextView subjectCount = activity.findViewById(R.id.progressSubjectCount);
        TextView documentCount = activity.findViewById(R.id.progressDocumentCount);
        TextView quizCount = activity.findViewById(R.id.progressQuizCount);
        TextView accuracy = activity.findViewById(R.id.progressAccuracy);
        TextView averageScore = activity.findViewById(R.id.progressAverageScore);
        TextView empty = activity.findViewById(R.id.emptyProgressSubjects);
        LinearLayout subjectContainer = activity.findViewById(R.id.progressSubjectContainer);

        subjectCount.setText(String.valueOf(subjects.size()));
        documentCount.setText(String.valueOf(documents.size()));
        quizCount.setText(String.valueOf(attempts.size()));
        accuracy.setText(totalQuestions == 0
                ? "0%"
                : String.format(Locale.US, "%.0f%%", totalCorrect * 100f / totalQuestions));
        averageScore.setText(attempts.isEmpty()
                ? "0.0"
                : String.format(Locale.US, "%.1f", totalScore / attempts.size()));

        Map<Long, Subject> subjectById = new HashMap<>();
        for (Subject subject : subjects) {
            subjectById.put(subject.id, subject);
        }
        Map<Long, StudyDocument> documentById = new HashMap<>();
        for (StudyDocument document : documents) {
            documentById.put(document.id, document);
        }

        Map<Long, Integer> attemptsBySubject = new HashMap<>();
        Map<Long, Integer> correctBySubject = new HashMap<>();
        Map<Long, Integer> questionsBySubject = new HashMap<>();
        for (QuizAttempt attempt : attempts) {
            StudyDocument document = documentById.get(attempt.document_id);
            if (document == null) continue;
            long subjectId = document.subject_id;
            attemptsBySubject.put(subjectId, attemptsBySubject.getOrDefault(subjectId, 0) + 1);
            correctBySubject.put(subjectId, correctBySubject.getOrDefault(subjectId, 0) + attempt.correctCount);
            questionsBySubject.put(subjectId, questionsBySubject.getOrDefault(subjectId, 0)
                    + attempt.correctCount + attempt.wrongCount);
        }

        subjectContainer.removeAllViews();
        empty.setVisibility(attemptsBySubject.isEmpty() ? View.VISIBLE : View.GONE);
        int animationIndex = 0;
        for (Subject subject : subjects) {
            int subjectAttempts = attemptsBySubject.getOrDefault(subject.id, 0);
            if (subjectAttempts == 0) continue;
            int subjectCorrect = correctBySubject.getOrDefault(subject.id, 0);
            int subjectQuestions = questionsBySubject.getOrDefault(subject.id, 0);
            int percent = subjectQuestions == 0 ? 0 : subjectCorrect * 100 / subjectQuestions;

            MaterialCardView card = UiViewFactory.createCard(activity);
            LinearLayout content = new LinearLayout(activity);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(
                    UiViewFactory.dp(activity, 16),
                    UiViewFactory.dp(activity, 14),
                    UiViewFactory.dp(activity, 16),
                    UiViewFactory.dp(activity, 14)
            );
            content.addView(UiViewFactory.createText(activity, subject.name, 16, R.color.ink, true));
            content.addView(UiViewFactory.createText(
                    activity,
                    subjectAttempts + " lần làm quiz · Độ chính xác " + percent + "%",
                    13,
                    R.color.ink_muted,
                    false
            ));
            card.addView(content);
            subjectContainer.addView(card, UiViewFactory.verticalMargin(activity, 10));
            UiViewFactory.animateIn(card, animationIndex++);
        }
    }
}
