package hcmute.com.smarteduapp.ui.document;

import android.app.Activity;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import hcmute.com.smarteduapp.R;
import hcmute.com.smarteduapp.data.local.entity.StudyDocumentAttachment;
import hcmute.com.smarteduapp.ui.common.SimpleCardAdapter;
import hcmute.com.smarteduapp.ui.common.UiViewFactory;

/** Renders the attachment list. Long-pressing a row asks the controller to delete it. */
public class DocumentAttachmentUi {
    public interface AttachmentCallbacks {
        void onDeleteAttachment(StudyDocumentAttachment attachment);
    }

    private final Activity activity;

    public DocumentAttachmentUi(Activity activity) {
        this.activity = activity;
    }

    public void renderAttachmentList(List<StudyDocumentAttachment> attachments,
                                     AttachmentCallbacks callbacks) {
        RecyclerView container = activity.findViewById(R.id.documentAttachmentListContainer);
        TextView empty = activity.findViewById(R.id.emptyDocumentAttachments);
        if (container == null) {
            return;
        }

        UiViewFactory.setupVerticalRecycler(container);
        empty.setVisibility(attachments.isEmpty() ? View.VISIBLE : View.GONE);

        SimpleCardAdapter adapter = new SimpleCardAdapter();
        List<SimpleCardAdapter.CardFactory> cards = new ArrayList<>();
        for (StudyDocumentAttachment attachment : attachments) {
            cards.add((parent, position) -> createAttachmentCard(parent, attachment, callbacks, position));
        }
        adapter.submit(cards);
        container.setAdapter(adapter);
    }

    private View createAttachmentCard(android.view.ViewGroup parent,
                                      StudyDocumentAttachment attachment,
                                      AttachmentCallbacks callbacks,
                                      int position) {
        MaterialCardView card = UiViewFactory.createCard(parent.getContext());
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.HORIZONTAL);
        content.setGravity(android.view.Gravity.CENTER_VERTICAL);
        content.setPadding(UiViewFactory.dp(activity, 14), UiViewFactory.dp(activity, 12),
                UiViewFactory.dp(activity, 14), UiViewFactory.dp(activity, 12));

        ImageView preview = new ImageView(activity);
        int previewSize = UiViewFactory.dp(activity, 58);
        preview.setLayoutParams(new LinearLayout.LayoutParams(previewSize, previewSize));
        preview.setBackgroundResource(R.drawable.bg_document_thumb);
        preview.setPadding(UiViewFactory.dp(activity, 8), UiViewFactory.dp(activity, 8),
                UiViewFactory.dp(activity, 8), UiViewFactory.dp(activity, 8));
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (isImageAttachment(attachment.attachmentUri)) {
            preview.setPadding(0, 0, 0, 0);
            try {
                preview.setImageURI(Uri.parse(attachment.attachmentUri));
            } catch (Exception exception) {
                preview.setImageResource(android.R.drawable.ic_menu_report_image);
            }
        } else {
            preview.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            preview.setImageResource(android.R.drawable.ic_menu_upload);
        }

        LinearLayout textBox = new LinearLayout(activity);
        textBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
        );
        textParams.setMargins(UiViewFactory.dp(activity, 14), 0, 0, 0);
        textBox.setLayoutParams(textParams);

        TextView title = UiViewFactory.createText(activity,
                getAttachmentDisplayName(attachment.attachmentUri), 15, R.color.ink, true);
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);

        TextView subtitle = UiViewFactory.createText(activity,
                getDocumentAttachmentLabel(attachment.attachmentUri)
                        + " · Nhấn giữ để xóa", 12, R.color.ink_muted, false);
        subtitle.setMaxLines(2);

        textBox.addView(title);
        textBox.addView(subtitle);
        content.addView(preview);
        content.addView(textBox);
        card.addView(content);
        card.setOnLongClickListener(v -> {
            callbacks.onDeleteAttachment(attachment);
            return true;
        });
        UiViewFactory.animateIn(card, position);
        return card;
    }

    public String getAttachmentDisplayName(String attachmentUri) {
        if (isBlank(attachmentUri)) {
            return "Tệp tài liệu";
        }
        Uri uri = Uri.parse(attachmentUri);
        try (Cursor cursor = activity.getContentResolver().query(
                uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    String name = cursor.getString(index);
                    if (!isBlank(name)) {
                        return name;
                    }
                }
            }
        } catch (Exception ignored) {
            // Một số URI do camera tạo không có display name.
        }
        String path = uri.getPath();
        if (!isBlank(path)) {
            int separator = path.lastIndexOf('/');
            return separator >= 0 ? path.substring(separator + 1) : path;
        }
        return "Tệp tài liệu";
    }

    public String getDocumentAttachmentLabel(String attachmentUri) {
        if (isBlank(attachmentUri)) {
            return "Chưa có file";
        }
        String mimeType = getAttachmentMimeType(attachmentUri);
        if (!isBlank(mimeType)) {
            if (mimeType.startsWith("image/")) return "Ảnh tài liệu";
            if ("application/pdf".equals(mimeType)) return "File PDF";
            if (mimeType.contains("wordprocessingml")) return "File DOCX";
            if (mimeType.startsWith("text/")) return "File văn bản";
        }
        return "File tài liệu";
    }

    private boolean isImageAttachment(String attachmentUri) {
        String mimeType = getAttachmentMimeType(attachmentUri);
        if (!isBlank(mimeType)) {
            return mimeType.startsWith("image/");
        }
        String lowerUri = attachmentUri == null ? "" : attachmentUri.toLowerCase(Locale.US);
        return lowerUri.endsWith(".jpg") || lowerUri.endsWith(".jpeg")
                || lowerUri.endsWith(".png") || lowerUri.endsWith(".webp");
    }

    private String getAttachmentMimeType(String attachmentUri) {
        if (isBlank(attachmentUri)) {
            return "";
        }
        try {
            String mimeType = activity.getContentResolver().getType(Uri.parse(attachmentUri));
            return mimeType == null ? "" : mimeType;
        } catch (Exception exception) {
            return "";
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
