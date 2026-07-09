package hcmute.com.smarteduapp.ui.subject;

import android.app.Activity;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.card.MaterialCardView;

import java.util.List;

import hcmute.com.smarteduapp.R;
import hcmute.com.smarteduapp.data.local.entity.StudyDocument;
import hcmute.com.smarteduapp.data.local.entity.Subject;
import hcmute.com.smarteduapp.ui.common.UiViewFactory;

/**
 * Renders subject and document list cards.
 */
public class SubjectListRenderer {
    public interface SubjectClickListener {
        void onSubjectClick(Subject subject);
    }

    public interface DocumentClickListener {
        void onDocumentClick(StudyDocument document);
    }

    private final Activity activity;

    public SubjectListRenderer(Activity activity) {
        this.activity = activity;
    }

    public void renderSubjects(List<Subject> subjects, SubjectClickListener listener) {
        LinearLayout container = activity.findViewById(R.id.subjectListContainer);
        TextView empty = activity.findViewById(R.id.emptySubjects);
        container.removeAllViews();
        empty.setVisibility(subjects.isEmpty() ? android.view.View.VISIBLE : android.view.View.GONE);

        int cardIndex = 0;
        for (Subject subject : subjects) {
            MaterialCardView card = UiViewFactory.createCard(activity);
            card.setOnClickListener(v -> listener.onSubjectClick(subject));

            LinearLayout content = new LinearLayout(activity);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(UiViewFactory.dp(activity, 16), UiViewFactory.dp(activity, 15),
                    UiViewFactory.dp(activity, 16), UiViewFactory.dp(activity, 15));

            TextView name = UiViewFactory.createText(activity, subject.name, 17, R.color.ink, true);
            TextView description = UiViewFactory.createText(activity,
                    isBlank(subject.description) ? "Chưa có mô tả" : subject.description,
                    13, R.color.ink_muted, false
            );
            description.setPadding(0, UiViewFactory.dp(activity, 5), 0, 0);
            content.addView(name);
            content.addView(description);
            card.addView(content);
            container.addView(card, UiViewFactory.verticalMargin(activity, 12));
            UiViewFactory.animateIn(card, cardIndex++);
        }
    }

    public void renderDocuments(List<StudyDocument> documents, DocumentClickListener listener) {
        LinearLayout container = activity.findViewById(R.id.documentListContainer);
        TextView empty = activity.findViewById(R.id.emptyDocuments);
        container.removeAllViews();
        empty.setVisibility(documents.isEmpty() ? android.view.View.VISIBLE : android.view.View.GONE);

        int cardIndex = 0;
        for (StudyDocument document : documents) {
            MaterialCardView card = UiViewFactory.createCard(activity);
            card.setOnClickListener(v -> listener.onDocumentClick(document));
            LinearLayout content = new LinearLayout(activity);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(UiViewFactory.dp(activity, 16), UiViewFactory.dp(activity, 15),
                    UiViewFactory.dp(activity, 16), UiViewFactory.dp(activity, 15));
            content.addView(UiViewFactory.createText(activity, document.title, 16,
                    R.color.ink, true));
            String imageState = "Tài liệu đính kèm";
            String ocrState = isBlank(document.ocrText) ? "Chưa có nội dung OCR" : "Đã lưu nội dung OCR";
            TextView state = UiViewFactory.createText(activity,
                    imageState + " · " + ocrState,
                    13, R.color.ink_muted, false
            );
            state.setPadding(0, UiViewFactory.dp(activity, 5), 0, 0);
            content.addView(state);
            card.addView(content);
            container.addView(card, UiViewFactory.verticalMargin(activity, 12));
            UiViewFactory.animateIn(card, cardIndex++);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
