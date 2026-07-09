package hcmute.com.smarteduapp.ui.study;

import android.app.Activity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.material.card.MaterialCardView;

import hcmute.com.smarteduapp.R;
import hcmute.com.smarteduapp.ui.common.UiViewFactory;

/**
 * Renders AI chat bubbles for the document Q&A screen.
 */
public class ChatMessageRenderer {
    private final Activity activity;

    public ChatMessageRenderer(Activity activity) {
        this.activity = activity;
    }

    public void addMessage(String sender, String message, boolean isUser) {
        LinearLayout container = activity.findViewById(R.id.chatMessagesContainer);
        ScrollView scrollView = activity.findViewById(R.id.chatScrollView);
        if (container == null) {
            return;
        }

        MaterialCardView card = UiViewFactory.createCard(activity);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(
                UiViewFactory.dp(activity, 14),
                UiViewFactory.dp(activity, 12),
                UiViewFactory.dp(activity, 14),
                UiViewFactory.dp(activity, 12)
        );

        TextView senderView = UiViewFactory.createText(
                activity,
                sender,
                13,
                isUser ? R.color.brand_blue_dark : R.color.ink_muted,
                true
        );
        TextView messageView = UiViewFactory.createText(activity, message, 15, R.color.ink, false);
        messageView.setPadding(0, UiViewFactory.dp(activity, 6), 0, 0);

        content.addView(senderView);
        content.addView(messageView);
        card.addView(content);
        container.addView(card, UiViewFactory.verticalMargin(activity, 10));
        UiViewFactory.animateIn(card, container.getChildCount());

        if (scrollView != null) {
            scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
        }
    }

    public void removeLastMessage() {
        LinearLayout container = activity.findViewById(R.id.chatMessagesContainer);
        if (container != null && container.getChildCount() > 0) {
            container.removeViewAt(container.getChildCount() - 1);
        }
    }
}
