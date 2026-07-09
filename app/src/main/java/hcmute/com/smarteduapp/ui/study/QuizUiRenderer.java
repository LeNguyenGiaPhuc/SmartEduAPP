package hcmute.com.smarteduapp.ui.study;

import android.app.Activity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.google.android.material.card.MaterialCardView;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import hcmute.com.smarteduapp.R;
import hcmute.com.smarteduapp.data.local.entity.QuizAttempt;
import hcmute.com.smarteduapp.data.local.entity.StudyDocument;
import hcmute.com.smarteduapp.data.local.entity.StudyQuestion;
import hcmute.com.smarteduapp.ui.common.UiViewFactory;

/**
 * Renders quiz screens and quiz result review.
 * Quiz scoring and database work stay outside this class.
 */
public class QuizUiRenderer {
    private final Activity activity;

    public QuizUiRenderer(Activity activity) {
        this.activity = activity;
    }

    public void renderQuizQuestions(List<StudyQuestion> questions, Map<Long, String> selectedAnswers) {
        TextView subtitle = activity.findViewById(R.id.quizSubtitle);
        LinearLayout container = activity.findViewById(R.id.quizQuestionsContainer);
        container.removeAllViews();

        subtitle.setText(questions.size() + " câu hỏi");

        if (questions.isEmpty()) {
            TextView empty = UiViewFactory.createText(
                    activity,
                    "Chưa có câu hỏi để làm quiz.",
                    15,
                    R.color.ink_muted,
                    false
            );
            container.addView(empty);
            return;
        }

        for (int index = 0; index < questions.size(); index++) {
            StudyQuestion question = questions.get(index);
            MaterialCardView card = UiViewFactory.createCard(activity);
            card.setClickable(false);
            card.setFocusable(false);

            LinearLayout content = new LinearLayout(activity);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(
                    UiViewFactory.dp(activity, 18),
                    UiViewFactory.dp(activity, 16),
                    UiViewFactory.dp(activity, 18),
                    UiViewFactory.dp(activity, 16)
            );

            TextView number = UiViewFactory.createText(
                    activity,
                    "Câu " + (index + 1) + " / " + questions.size(),
                    13,
                    R.color.brand_blue_dark,
                    true
            );

            TextView questionText = UiViewFactory.createText(
                    activity,
                    question.questionText,
                    17,
                    R.color.ink,
                    true
            );
            questionText.setPadding(0, UiViewFactory.dp(activity, 8), 0, 0);

            RadioGroup answers = new RadioGroup(activity);
            answers.setOrientation(RadioGroup.VERTICAL);
            answers.setPadding(0, UiViewFactory.dp(activity, 12), 0, 0);

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
            answers.setOnCheckedChangeListener((group, checkedId) -> {
                RadioButton selected = group.findViewById(checkedId);
                if (selected != null) {
                    selectedAnswers.put(question.id, selected.getTag().toString());
                }
            });

            content.addView(number);
            content.addView(questionText);
            content.addView(answers);
            card.addView(content);
            container.addView(card, UiViewFactory.verticalMargin(activity, 12));
            UiViewFactory.animateIn(card, container.getChildCount());
        }
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
            return;
        }

        int total = latestQuizAttempt.correctCount + latestQuizAttempt.wrongCount;
        scoreText.setText(String.format(Locale.US, "%.1f", latestQuizAttempt.score));
        correctText.setText(latestQuizAttempt.correctCount + " / " + total + " câu trả lời đúng");
        detailText.setText((selectedDocument == null ? "Tài liệu" : selectedDocument.title)
                + " · Sai " + latestQuizAttempt.wrongCount + " câu");
        renderQuizAnswerReview(currentQuizQuestions, selectedQuizAnswers);
    }

    private void renderQuizAnswerReview(
            List<StudyQuestion> currentQuizQuestions,
            Map<Long, String> selectedQuizAnswers
    ) {
        LinearLayout container = activity.findViewById(R.id.resultReviewContainer);
        if (container == null) {
            return;
        }
        container.removeAllViews();

        if (currentQuizQuestions.isEmpty()) {
            TextView empty = UiViewFactory.createText(
                    activity,
                    "Chưa có dữ liệu câu hỏi để review.",
                    14,
                    R.color.ink_muted,
                    false
            );
            container.addView(empty);
            return;
        }

        for (int index = 0; index < currentQuizQuestions.size(); index++) {
            StudyQuestion question = currentQuizQuestions.get(index);
            String selectedOption = selectedQuizAnswers.get(question.id);
            boolean isCorrect = question.correctOption.equalsIgnoreCase(
                    selectedOption == null ? "" : selectedOption
            );

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

            TextView number = UiViewFactory.createText(
                    activity,
                    "Câu " + (index + 1) + (isCorrect ? " · Đúng" : " · Sai"),
                    13,
                    isCorrect ? R.color.brand_blue_dark : R.color.danger,
                    true
            );
            TextView questionText = UiViewFactory.createText(activity, question.questionText, 16, R.color.ink, true);
            questionText.setPadding(0, UiViewFactory.dp(activity, 8), 0, 0);

            String selectedText = isBlank(selectedOption)
                    ? "Bạn chọn: Chưa chọn"
                    : "Bạn chọn: " + selectedOption + ". " + getQuestionOptionText(question, selectedOption);
            TextView selected = UiViewFactory.createText(
                    activity,
                    selectedText,
                    14,
                    isCorrect ? R.color.brand_blue_dark : R.color.danger,
                    true
            );
            selected.setPadding(0, UiViewFactory.dp(activity, 10), 0, 0);

            TextView correct = UiViewFactory.createText(
                    activity,
                    "Đáp án đúng: " + question.correctOption + ". "
                            + getQuestionOptionText(question, question.correctOption),
                    14,
                    R.color.ink,
                    true
            );
            correct.setPadding(0, UiViewFactory.dp(activity, 6), 0, 0);

            TextView explanation = UiViewFactory.createText(
                    activity,
                    "Giải thích: " + (isBlank(question.explanation)
                            ? "Chưa có giải thích cho câu này."
                            : question.explanation),
                    13,
                    R.color.ink_muted,
                    false
            );
            explanation.setPadding(0, UiViewFactory.dp(activity, 6), 0, 0);

            content.addView(number);
            content.addView(questionText);
            content.addView(selected);
            content.addView(correct);
            content.addView(explanation);
            card.addView(content);
            container.addView(card, UiViewFactory.verticalMargin(activity, 12));
            UiViewFactory.animateIn(card, index);
        }
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

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
