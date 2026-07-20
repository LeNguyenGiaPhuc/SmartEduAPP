package hcmute.com.smarteduapp.ui.study;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import hcmute.com.smarteduapp.R;
import hcmute.com.smarteduapp.data.local.entity.QuizAttempt;
import hcmute.com.smarteduapp.data.local.entity.StudyDocument;
import hcmute.com.smarteduapp.data.local.entity.StudyQuestion;
import hcmute.com.smarteduapp.ui.common.SimpleCardAdapter;
import hcmute.com.smarteduapp.ui.common.UiViewFactory;

/**
 * Renders quiz screens and quiz result review.
 * Quiz scoring and database work stay outside this class.
 */
public class QuizUiRenderer {
    public interface AnswerSelectionListener {
        void onAnswerSelected(StudyQuestion question, String previousOption, String newOption);
    }

    private final Activity activity;

    public QuizUiRenderer(Activity activity) {
        this.activity = activity;
    }

    public void renderQuizQuestions(List<StudyQuestion> questions, Map<Long, String> selectedAnswers) {
        TextView subtitle = activity.findViewById(R.id.quizSubtitle);
        RecyclerView container = activity.findViewById(R.id.quizQuestionsContainer);
        UiViewFactory.setupVerticalRecycler(container);

        subtitle.setText(questions.size() + " câu hỏi");

        SimpleCardAdapter adapter = new SimpleCardAdapter();
        List<SimpleCardAdapter.CardFactory> cards = new ArrayList<>();

        if (questions.isEmpty()) {
            cards.add((parent, position) -> UiViewFactory.createText(
                    activity,
                    "Chưa có câu hỏi để làm quiz.",
                    15,
                    R.color.ink_muted,
                    false
            ));
        } else {
            for (int index = 0; index < questions.size(); index++) {
                final int position = index;
                StudyQuestion question = questions.get(index);
                cards.add((parent, ignored) -> createQuizQuestionCard(
                        parent,
                        question,
                        position,
                        questions.size(),
                        selectedAnswers
                ));
            }
        }

        container.setAdapter(adapter);
        adapter.submit(cards);
    }

    public void renderSingleQuestion(
            List<StudyQuestion> questions,
            Map<Long, String> selectedAnswers,
            int currentIndex
    ) {
        renderSingleQuestion(questions, selectedAnswers, currentIndex, null);
    }

    public void renderSingleQuestion(
            List<StudyQuestion> questions,
            Map<Long, String> selectedAnswers,
            int currentIndex,
            AnswerSelectionListener listener
    ) {
        TextView subtitle = activity.findViewById(R.id.quizSubtitle);
        TextView previousButton = activity.findViewById(R.id.buttonPreviousQuestion);
        TextView nextButton = activity.findViewById(R.id.buttonCheckAnswers);
        RecyclerView container = activity.findViewById(R.id.quizQuestionsContainer);
        UiViewFactory.setupVerticalRecycler(container);

        if (questions.isEmpty()) {
            subtitle.setText("0 câu hỏi");
            previousButton.setVisibility(View.INVISIBLE);
            nextButton.setText("Nộp bài");
            return;
        }

        int safeIndex = Math.max(0, Math.min(currentIndex, questions.size() - 1));
        subtitle.setText("Câu " + (safeIndex + 1) + " / " + questions.size());
        previousButton.setVisibility(safeIndex == 0 ? View.INVISIBLE : View.VISIBLE);
        nextButton.setText(safeIndex == questions.size() - 1 ? "Nộp bài" : "Câu tiếp theo");

        SimpleCardAdapter adapter = new SimpleCardAdapter();
        List<SimpleCardAdapter.CardFactory> cards = new ArrayList<>();
        StudyQuestion question = questions.get(safeIndex);
        cards.add((parent, ignored) -> createQuizQuestionCard(
                parent,
                question,
                safeIndex,
                questions.size(),
                selectedAnswers,
                listener
        ));
        container.setAdapter(adapter);
        adapter.submit(cards);
    }

    public void renderQuizResult(
            QuizAttempt latestQuizAttempt,
            StudyDocument selectedDocument,
            List<StudyQuestion> currentQuizQuestions,
            Map<Long, String> selectedQuizAnswers
    ) {
        TextView scoreText = activity.findViewById(R.id.resultScore);
        TextView correctText = activity.findViewById(R.id.resultCorrect);
        TextView detailText = activity.findViewById(R.id.resultDetail);

        if (latestQuizAttempt == null) {
            scoreText.setText("0.0");
            correctText.setText("Chưa có kết quả");
            detailText.setText(selectedDocument == null ? "" : selectedDocument.title);
            renderQuizAnswerReview(new ArrayList<>(), selectedQuizAnswers);
            return;
        }

        int total = latestQuizAttempt.correctCount + latestQuizAttempt.wrongCount;
        scoreText.setText(String.format(Locale.US, "%.1f", latestQuizAttempt.score));
        correctText.setText(latestQuizAttempt.correctCount + " / " + total + " câu trả lời đúng");
        detailText.setText((selectedDocument == null ? "Tài liệu" : selectedDocument.title)
                + " · Sai " + latestQuizAttempt.wrongCount + " câu"
                + " · Thời gian " + formatSeconds(latestQuizAttempt.totalTimeSeconds));
        if (!latestQuizAttempt.explanationUnlocked) {
            renderLockedQuizReview(latestQuizAttempt);
            return;
        }

        renderQuizAnswerReview(currentQuizQuestions, selectedQuizAnswers);
    }

    private View createQuizQuestionCard(
            ViewGroup parent,
            StudyQuestion question,
            int index,
            int total,
            Map<Long, String> selectedAnswers
    ) {
        return createQuizQuestionCard(parent, question, index, total, selectedAnswers, null);
    }

    private View createQuizQuestionCard(
            ViewGroup parent,
            StudyQuestion question,
            int index,
            int total,
            Map<Long, String> selectedAnswers,
            AnswerSelectionListener listener
    ) {
        MaterialCardView card = UiViewFactory.createCard(parent.getContext());
        card.setClickable(false);
        card.setFocusable(false);

        LinearLayout content = new LinearLayout(parent.getContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(
                UiViewFactory.dp(parent.getContext(), 18),
                UiViewFactory.dp(parent.getContext(), 16),
                UiViewFactory.dp(parent.getContext(), 18),
                UiViewFactory.dp(parent.getContext(), 16)
        );

        TextView number = UiViewFactory.createText(
                parent.getContext(),
                "Câu " + (index + 1) + " / " + total,
                13,
                R.color.brand_blue_dark,
                true
        );

        TextView questionText = UiViewFactory.createText(
                parent.getContext(),
                question.questionText,
                17,
                R.color.ink,
                true
        );
        questionText.setPadding(0, UiViewFactory.dp(parent.getContext(), 8), 0, 0);

        RadioGroup answers = new RadioGroup(parent.getContext());
        answers.setOrientation(RadioGroup.VERTICAL);
        answers.setPadding(0, UiViewFactory.dp(parent.getContext(), 12), 0, 0);

        RadioButton optionA = createAnswerRadioButton("A. " + question.optionA);
        RadioButton optionB = createAnswerRadioButton("B. " + question.optionB);
        RadioButton optionC = createAnswerRadioButton("C. " + question.optionC);
        RadioButton optionD = createAnswerRadioButton("D. " + question.optionD);

        optionA.setTag("A");
        optionB.setTag("B");
        optionC.setTag("C");
        optionD.setTag("D");

        answers.addView(optionA);
        answers.addView(optionB);
        answers.addView(optionC);
        answers.addView(optionD);

        String selectedOption = selectedAnswers.get(question.id);
        if ("A".equalsIgnoreCase(selectedOption)) answers.check(optionA.getId());
        if ("B".equalsIgnoreCase(selectedOption)) answers.check(optionB.getId());
        if ("C".equalsIgnoreCase(selectedOption)) answers.check(optionC.getId());
        if ("D".equalsIgnoreCase(selectedOption)) answers.check(optionD.getId());

        answers.setOnCheckedChangeListener((group, checkedId) -> {
            RadioButton selected = group.findViewById(checkedId);
            if (selected != null) {
                String newOption = selected.getTag().toString();
                String previousOption = selectedAnswers.get(question.id);
                if (listener != null) {
                    listener.onAnswerSelected(question, previousOption, newOption);
                }
                selectedAnswers.put(question.id, newOption);
            }
        });

        content.addView(number);
        content.addView(questionText);
        content.addView(answers);
        card.addView(content);
        UiViewFactory.animateIn(card, index);
        return card;
    }

    private void renderQuizAnswerReview(
            List<StudyQuestion> currentQuizQuestions,
            Map<Long, String> selectedQuizAnswers
    ) {
        RecyclerView container = activity.findViewById(R.id.resultReviewContainer);
        if (container == null) {
            return;
        }
        UiViewFactory.setupVerticalRecycler(container);

        SimpleCardAdapter adapter = new SimpleCardAdapter();
        List<SimpleCardAdapter.CardFactory> cards = new ArrayList<>();

        if (currentQuizQuestions.isEmpty()) {
            cards.add((parent, position) -> UiViewFactory.createText(
                    activity,
                    "Chưa có dữ liệu câu hỏi để review.",
                    14,
                    R.color.ink_muted,
                    false
            ));
        } else {
            for (int index = 0; index < currentQuizQuestions.size(); index++) {
                final int position = index;
                StudyQuestion question = currentQuizQuestions.get(index);
                cards.add((parent, ignored) -> createAnswerReviewCard(
                        parent,
                        question,
                        position,
                        selectedQuizAnswers
                ));
            }
        }

        container.setAdapter(adapter);
        adapter.submit(cards);
    }

    private void renderLockedQuizReview(QuizAttempt attempt) {
        RecyclerView container = activity.findViewById(R.id.resultReviewContainer);
        if (container == null) {
            return;
        }
        UiViewFactory.setupVerticalRecycler(container);

        SimpleCardAdapter adapter = new SimpleCardAdapter();
        List<SimpleCardAdapter.CardFactory> cards = new ArrayList<>();
        cards.add((parent, position) -> createLockedReviewCard(parent, attempt));

        container.setAdapter(adapter);
        adapter.submit(cards);
    }

    private View createLockedReviewCard(ViewGroup parent, QuizAttempt attempt) {
        MaterialCardView card = UiViewFactory.createCard(parent.getContext());
        card.setClickable(false);
        card.setFocusable(false);

        LinearLayout content = new LinearLayout(parent.getContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(
                UiViewFactory.dp(parent.getContext(), 16),
                UiViewFactory.dp(parent.getContext(), 14),
                UiViewFactory.dp(parent.getContext(), 16),
                UiViewFactory.dp(parent.getContext(), 14)
        );

        TextView title = UiViewFactory.createText(
                parent.getContext(),
                "Lời giải đang bị khóa",
                16,
                R.color.danger,
                true
        );

        TextView message = UiViewFactory.createText(
                parent.getContext(),
                "Bạn đã rời khỏi app " + attempt.focusExitCount
                        + " lần trong chế độ tập trung. Hãy làm lại quiz nghiêm túc để xem đáp án đúng và giải thích.",
                14,
                R.color.ink_muted,
                false
        );
        message.setPadding(0, UiViewFactory.dp(parent.getContext(), 8), 0, 0);

        content.addView(title);
        content.addView(message);
        card.addView(content);
        UiViewFactory.animateIn(card, 0);
        return card;
    }

    private View createAnswerReviewCard(
            ViewGroup parent,
            StudyQuestion question,
            int index,
            Map<Long, String> selectedQuizAnswers
    ) {
        String selectedOption = selectedQuizAnswers.get(question.id);
        boolean isCorrect = question.correctOption.equalsIgnoreCase(
                selectedOption == null ? "" : selectedOption
        );

        MaterialCardView card = UiViewFactory.createCard(parent.getContext());
        card.setClickable(false);
        card.setFocusable(false);

        LinearLayout content = new LinearLayout(parent.getContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(
                UiViewFactory.dp(parent.getContext(), 16),
                UiViewFactory.dp(parent.getContext(), 14),
                UiViewFactory.dp(parent.getContext(), 16),
                UiViewFactory.dp(parent.getContext(), 14)
        );

        TextView number = UiViewFactory.createText(
                parent.getContext(),
                "Câu " + (index + 1) + (isCorrect ? " · Đúng" : " · Sai"),
                13,
                isCorrect ? R.color.brand_blue_dark : R.color.danger,
                true
        );
        TextView questionText = UiViewFactory.createText(parent.getContext(), question.questionText, 16, R.color.ink, true);
        questionText.setPadding(0, UiViewFactory.dp(parent.getContext(), 8), 0, 0);

        String selectedText = isBlank(selectedOption)
                ? "Bạn chọn: Chưa chọn"
                : "Bạn chọn: " + selectedOption + ". " + getQuestionOptionText(question, selectedOption);
        TextView selected = UiViewFactory.createText(
                parent.getContext(),
                selectedText,
                14,
                isCorrect ? R.color.brand_blue_dark : R.color.danger,
                true
        );
        selected.setPadding(0, UiViewFactory.dp(parent.getContext(), 10), 0, 0);

        TextView correct = UiViewFactory.createText(
                parent.getContext(),
                "Đáp án đúng: " + question.correctOption + ". "
                        + getQuestionOptionText(question, question.correctOption),
                14,
                R.color.ink,
                true
        );
        correct.setPadding(0, UiViewFactory.dp(parent.getContext(), 6), 0, 0);

        TextView explanation = UiViewFactory.createText(
                parent.getContext(),
                "Giải thích: " + (isBlank(question.explanation)
                        ? "Chưa có giải thích cho câu này."
                        : question.explanation),
                13,
                R.color.ink_muted,
                false
        );
        explanation.setPadding(0, UiViewFactory.dp(parent.getContext(), 6), 0, 0);

        content.addView(number);
        content.addView(questionText);
        content.addView(selected);
        content.addView(correct);
        content.addView(explanation);
        card.addView(content);
        UiViewFactory.animateIn(card, index);
        return card;
    }

    private RadioButton createAnswerRadioButton(String text) {
        RadioButton button = new RadioButton(activity);
        button.setId(View.generateViewId());
        button.setText(text);
        button.setTextColor(activity.getColor(R.color.ink));
        button.setTextSize(15);
        button.setBackgroundResource(R.drawable.bg_field_outline);
        button.setPadding(
                UiViewFactory.dp(activity, 14),
                UiViewFactory.dp(activity, 12),
                UiViewFactory.dp(activity, 14),
                UiViewFactory.dp(activity, 12)
        );

        RadioGroup.LayoutParams params = new RadioGroup.LayoutParams(
                RadioGroup.LayoutParams.MATCH_PARENT,
                RadioGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, UiViewFactory.dp(activity, 8));
        button.setLayoutParams(params);
        return button;
    }

    private String getQuestionOptionText(StudyQuestion question, String option) {
        if (isBlank(option)) {
            return "";
        }
        String normalized = option.trim().toUpperCase(Locale.US);
        if (normalized.startsWith("A")) return question.optionA;
        if (normalized.startsWith("B")) return question.optionB;
        if (normalized.startsWith("C")) return question.optionC;
        if (normalized.startsWith("D")) return question.optionD;
        return "";
    }

    private String formatSeconds(int totalSeconds) {
        int safeSeconds = Math.max(0, totalSeconds);
        int minutes = safeSeconds / 60;
        int seconds = safeSeconds % 60;
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
