package hcmute.com.smarteduapp.ui.study;

import android.app.Activity;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.card.MaterialCardView;

import java.util.List;

import hcmute.com.smarteduapp.R;
import hcmute.com.smarteduapp.data.local.entity.StudyQuestion;
import hcmute.com.smarteduapp.ui.common.UiViewFactory;

/**
 * Renders saved study questions for one document.
 */
public class QuestionBankRenderer {
    public interface QuestionActionListener {
        void onEditQuestion(StudyQuestion question);

        void onDeleteQuestion(StudyQuestion question);
    }

    private final Activity activity;

    public QuestionBankRenderer(Activity activity) {
        this.activity = activity;
    }

    public void render(List<StudyQuestion> questions, QuestionActionListener listener) {
        LinearLayout container = activity.findViewById(R.id.questionBankContainer);
        container.removeAllViews();

        if (questions.isEmpty()) {
            TextView empty = UiViewFactory.createText(
                    activity,
                    "Chưa có câu hỏi cho tài liệu này.",
                    15,
                    R.color.ink_muted,
                    false
            );
            container.addView(empty);
            return;
        }

        for (StudyQuestion question : questions) {
            MaterialCardView card = UiViewFactory.createCard(activity);
            card.setClickable(true);
            card.setFocusable(true);
            card.setOnClickListener(v -> listener.onEditQuestion(question));

            LinearLayout content = new LinearLayout(activity);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(
                    UiViewFactory.dp(activity, 16),
                    UiViewFactory.dp(activity, 14),
                    UiViewFactory.dp(activity, 16),
                    UiViewFactory.dp(activity, 14)
            );

            TextView order = UiViewFactory.createText(
                    activity,
                    "Câu " + question.questionOrder,
                    14,
                    R.color.brand_blue_dark,
                    true
            );

            TextView text = UiViewFactory.createText(
                    activity,
                    question.questionText,
                    16,
                    R.color.ink,
                    true
            );
            text.setPadding(0, UiViewFactory.dp(activity, 6), 0, 0);

            TextView options = UiViewFactory.createText(
                    activity,
                    "A. " + question.optionA + "\n"
                            + "B. " + question.optionB + "\n"
                            + "C. " + question.optionC + "\n"
                            + "D. " + question.optionD,
                    14,
                    R.color.ink,
                    false
            );
            options.setPadding(0, UiViewFactory.dp(activity, 10), 0, 0);

            TextView answer = UiViewFactory.createText(
                    activity,
                    "Đáp án đúng: " + question.correctOption,
                    13,
                    R.color.ink_muted,
                    false
            );
            answer.setPadding(0, UiViewFactory.dp(activity, 10), 0, 0);

            content.addView(order);
            content.addView(text);
            content.addView(options);
            content.addView(answer);

            if (!isBlank(question.explanation)) {
                TextView explanation = UiViewFactory.createText(
                        activity,
                        "Giải thích: " + question.explanation,
                        13,
                        R.color.ink_muted,
                        false
                );
                explanation.setPadding(0, UiViewFactory.dp(activity, 6), 0, 0);
                content.addView(explanation);
            }

            LinearLayout actions = new LinearLayout(activity);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.setPadding(0, UiViewFactory.dp(activity, 12), 0, 0);

            TextView editAction = createAction("Sửa", R.color.brand_blue_dark, R.drawable.bg_action_chip_blue);
            editAction.setOnClickListener(v -> listener.onEditQuestion(question));

            TextView deleteAction = createAction("Xóa", R.color.danger, R.drawable.bg_action_chip_danger);
            deleteAction.setOnClickListener(v -> listener.onDeleteQuestion(question));

            LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
            );
            actionParams.setMargins(0, 0, UiViewFactory.dp(activity, 8), 0);
            actions.addView(editAction, actionParams);

            LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
            );
            deleteParams.setMargins(UiViewFactory.dp(activity, 8), 0, 0, 0);
            actions.addView(deleteAction, deleteParams);
            content.addView(actions);

            card.addView(content);
            container.addView(card, UiViewFactory.verticalMargin(activity, 12));
            UiViewFactory.animateIn(card, container.getChildCount());
        }
    }

    private TextView createAction(String label, int colorRes, int backgroundRes) {
        TextView action = UiViewFactory.createText(activity, label, 13, colorRes, true);
        action.setBackgroundResource(backgroundRes);
        action.setGravity(android.view.Gravity.CENTER);
        action.setPadding(UiViewFactory.dp(activity, 16), UiViewFactory.dp(activity, 8),
                UiViewFactory.dp(activity, 16), UiViewFactory.dp(activity, 8));
        UiViewFactory.applyPressEffect(action);
        return action;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
