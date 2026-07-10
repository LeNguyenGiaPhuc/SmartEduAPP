package hcmute.com.smarteduapp.ui.subject;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

import hcmute.com.smarteduapp.R;
import hcmute.com.smarteduapp.data.local.entity.StudyDocument;
import hcmute.com.smarteduapp.data.local.entity.Subject;
import hcmute.com.smarteduapp.ui.common.SimpleCardAdapter;
import hcmute.com.smarteduapp.ui.common.UiViewFactory;

/**
 * Renders subject and document list cards with RecyclerView.
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
        RecyclerView container = activity.findViewById(R.id.subjectListContainer);
        TextView empty = activity.findViewById(R.id.emptySubjects);
        UiViewFactory.setupVerticalRecycler(container);
        empty.setVisibility(subjects.isEmpty() ? View.VISIBLE : View.GONE);

        SimpleCardAdapter adapter = new SimpleCardAdapter();
        List<SimpleCardAdapter.CardFactory> cards = new ArrayList<>();
        for (Subject subject : subjects) {
            cards.add((parent, position) -> createSubjectCard(parent, subject, listener, position));
        }
        adapter.submit(cards);
        container.setAdapter(adapter);
    }

    public void renderDocuments(List<StudyDocument> documents, DocumentClickListener listener) {
        RecyclerView container = activity.findViewById(R.id.documentListContainer);
        TextView empty = activity.findViewById(R.id.emptyDocuments);
        UiViewFactory.setupVerticalRecycler(container);
        empty.setVisibility(documents.isEmpty() ? View.VISIBLE : View.GONE);

        SimpleCardAdapter adapter = new SimpleCardAdapter();
        List<SimpleCardAdapter.CardFactory> cards = new ArrayList<>();
        for (StudyDocument document : documents) {
            cards.add((parent, position) -> createDocumentCard(parent, document, listener, position));
        }
        adapter.submit(cards);
        container.setAdapter(adapter);
    }

    private View createSubjectCard(ViewGroup parent, Subject subject, SubjectClickListener listener, int position) {
        MaterialCardView card = UiViewFactory.createCard(parent.getContext());
        card.setOnClickListener(v -> listener.onSubjectClick(subject));

        LinearLayout content = new LinearLayout(parent.getContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(
                UiViewFactory.dp(parent.getContext(), 16),
                UiViewFactory.dp(parent.getContext(), 15),
                UiViewFactory.dp(parent.getContext(), 16),
                UiViewFactory.dp(parent.getContext(), 15)
        );

        TextView name = UiViewFactory.createText(parent.getContext(), subject.name, 17, R.color.ink, true);
        TextView description = UiViewFactory.createText(
                parent.getContext(),
                isBlank(subject.description) ? "Chưa có mô tả" : subject.description,
                13,
                R.color.ink_muted,
                false
        );
        description.setPadding(0, UiViewFactory.dp(parent.getContext(), 5), 0, 0);

        content.addView(name);
        content.addView(description);
        card.addView(content);
        UiViewFactory.animateIn(card, position);
        return card;
    }

    private View createDocumentCard(ViewGroup parent, StudyDocument document, DocumentClickListener listener,
                                    int position) {
        MaterialCardView card = UiViewFactory.createCard(parent.getContext());
        card.setOnClickListener(v -> listener.onDocumentClick(document));

        LinearLayout content = new LinearLayout(parent.getContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(
                UiViewFactory.dp(parent.getContext(), 16),
                UiViewFactory.dp(parent.getContext(), 15),
                UiViewFactory.dp(parent.getContext(), 16),
                UiViewFactory.dp(parent.getContext(), 15)
        );

        content.addView(UiViewFactory.createText(parent.getContext(), document.title, 16, R.color.ink, true));

        String imageState = "Tài liệu đính kèm";
        String ocrState = isBlank(document.ocrText) ? "Chưa có nội dung OCR" : "Đã lưu nội dung OCR";
        TextView state = UiViewFactory.createText(
                parent.getContext(),
                imageState + " · " + ocrState,
                13,
                R.color.ink_muted,
                false
        );
        state.setPadding(0, UiViewFactory.dp(parent.getContext(), 5), 0, 0);
        content.addView(state);

        card.addView(content);
        UiViewFactory.animateIn(card, position);
        return card;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
