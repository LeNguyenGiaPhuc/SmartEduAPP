package hcmute.com.smarteduapp.ui.main;

import android.app.Activity;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.card.MaterialCardView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import hcmute.com.smarteduapp.R;
import hcmute.com.smarteduapp.data.local.entity.QuizAttempt;
import hcmute.com.smarteduapp.data.local.entity.StudyDocument;
import hcmute.com.smarteduapp.data.local.entity.Subject;
import hcmute.com.smarteduapp.ui.common.UiViewFactory;

/**
 * Renders the home dashboard cards.
 * MainActivity supplies data and click actions; this class only builds views.
 */
public class HomeDashboardRenderer {
    public interface SubjectClickListener {
        void onSubjectClick(Subject subject);
    }

    public interface QuizClickListener {
        void onQuizClick(StudyDocument document, QuizAttempt attempt);
    }

    private final Activity activity;

    public HomeDashboardRenderer(Activity activity) {
        this.activity = activity;
    }

    public void renderRecentSubjects(List<Subject> recentSubjects, SubjectClickListener listener) {
        LinearLayout container = activity.findViewById(R.id.recentSubjectContainer);
        TextView empty = activity.findViewById(R.id.emptyRecentSubjects);
        if (container == null || empty == null) {
            return;
        }

        container.removeAllViews();
        empty.setVisibility(recentSubjects.isEmpty() ? android.view.View.VISIBLE : android.view.View.GONE);

        int visibleIndex = 0;
        for (int index = recentSubjects.size() - 1; index >= 0; index--) {
            Subject subject = recentSubjects.get(index);
            MaterialCardView card = UiViewFactory.createCard(activity);
            card.setOnClickListener(v -> listener.onSubjectClick(subject));

            LinearLayout content = new LinearLayout(activity);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(
                    UiViewFactory.dp(activity, 16),
                    UiViewFactory.dp(activity, 14),
                    UiViewFactory.dp(activity, 16),
                    UiViewFactory.dp(activity, 14)
            );

            content.addView(UiViewFactory.createText(activity, subject.name, 16, R.color.ink, true));
            TextView description = UiViewFactory.createText(
                    activity,
                    isBlank(subject.description) ? "Vừa truy cập" : subject.description,
                    13,
                    R.color.ink_muted,
                    false
            );
            description.setPadding(0, UiViewFactory.dp(activity, 5), 0, 0);
            content.addView(description);
            card.addView(content);
            container.addView(card, UiViewFactory.verticalMargin(activity, 12));
            UiViewFactory.animateIn(card, visibleIndex++);
        }
    }

    public void renderHomeQuizHistory(
            List<StudyDocument> documents,
            List<QuizAttempt> attempts,
            QuizClickListener listener
    ) {
        LinearLayout container = activity.findViewById(R.id.homeQuizHistoryContainer);
        TextView empty = activity.findViewById(R.id.emptyHomeQuizHistory);
        if (container == null || empty == null) {
            return;
        }

        container.removeAllViews();
        empty.setVisibility(attempts.isEmpty() ? android.view.View.VISIBLE : android.view.View.GONE);
        if (attempts.isEmpty()) {
            return;
        }

        Map<Long, StudyDocument> documentById = new HashMap<>();
        for (StudyDocument document : documents) {
            documentById.put(document.id, document);
        }

        int max = Math.min(3, attempts.size());
        for (int index = 0; index < max; index++) {
            QuizAttempt attempt = attempts.get(index);
            StudyDocument document = documentById.get(attempt.document_id);
            MaterialCardView card = UiViewFactory.createCard(activity);
            card.setOnClickListener(v -> listener.onQuizClick(document, attempt));

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
                    document == null ? "Quiz tài liệu #" + attempt.document_id : document.title,
                    16,
                    R.color.ink,
                    true
            );
            TextView score = UiViewFactory.createText(
                    activity,
                    attempt.correctCount + "/" + total + " đúng · "
                            + String.format(Locale.US, "%.1f", attempt.score) + " điểm",
                    14,
                    R.color.brand_blue_dark,
                    true
            );
            score.setPadding(0, UiViewFactory.dp(activity, 5), 0, 0);

            SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
            TextView time = UiViewFactory.createText(
                    activity,
                    format.format(new Date(attempt.completedAt)),
                    13,
                    R.color.ink_muted,
                    false
            );
            time.setPadding(0, UiViewFactory.dp(activity, 5), 0, 0);

            content.addView(title);
            content.addView(score);
            content.addView(time);
            card.addView(content);
            container.addView(card, UiViewFactory.verticalMargin(activity, 12));
            UiViewFactory.animateIn(card, index);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
