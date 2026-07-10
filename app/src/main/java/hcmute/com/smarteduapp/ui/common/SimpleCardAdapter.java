package hcmute.com.smarteduapp.ui.common;

import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * Small reusable adapter for student-project screens that render simple dynamic cards.
 * It keeps RecyclerView usage explicit without forcing a complex adapter per screen.
 */
public class SimpleCardAdapter extends RecyclerView.Adapter<SimpleCardAdapter.CardHolder> {
    public interface CardFactory {
        View create(ViewGroup parent, int position);
    }

    private final List<CardFactory> items = new ArrayList<>();

    public void submit(List<CardFactory> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public CardHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        FrameLayout root = new FrameLayout(parent.getContext());
        root.setLayoutParams(new RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.WRAP_CONTENT
        ));
        return new CardHolder(root);
    }

    @Override
    public void onBindViewHolder(@NonNull CardHolder holder, int position) {
        holder.root.removeAllViews();

        RecyclerView.LayoutParams itemParams = new RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.WRAP_CONTENT
        );
        itemParams.setMargins(0, UiViewFactory.dp(holder.root.getContext(), 12), 0, 0);
        holder.root.setLayoutParams(itemParams);

        View child = items.get(position).create(holder.root, position);
        holder.root.addView(child, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        ));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class CardHolder extends RecyclerView.ViewHolder {
        final FrameLayout root;

        CardHolder(@NonNull FrameLayout root) {
            super(root);
            this.root = root;
        }
    }
}
