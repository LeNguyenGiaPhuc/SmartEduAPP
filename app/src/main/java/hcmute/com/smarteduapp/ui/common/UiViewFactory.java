package hcmute.com.smarteduapp.ui.common;

import android.content.Context;
import android.graphics.Typeface;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.android.material.card.MaterialCardView;

import hcmute.com.smarteduapp.R;

/** Creates reusable cards and text used by dynamic subject/document lists. */
public final class UiViewFactory {
    private UiViewFactory() {
    }

    public static MaterialCardView createCard(Context context) {
        MaterialCardView card = new MaterialCardView(context);
        card.setCardBackgroundColor(ContextCompat.getColor(context, R.color.paper));
        card.setStrokeColor(ContextCompat.getColor(context, R.color.line_soft));
        card.setStrokeWidth(dp(context, 1));
        card.setRadius(dp(context, 18));
        card.setCardElevation(dp(context, 3));
        card.setClickable(true);
        card.setFocusable(true);
        return card;
    }

    public static TextView createText(Context context, String text, int sizeSp,
                                      int colorRes, boolean bold) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(sizeSp);
        view.setTextColor(ContextCompat.getColor(context, colorRes));
        if (bold) view.setTypeface(null, Typeface.BOLD);
        return view;
    }

    public static LinearLayout.LayoutParams verticalMargin(Context context, int topMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = dp(context, topMarginDp);
        return params;
    }

    public static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
