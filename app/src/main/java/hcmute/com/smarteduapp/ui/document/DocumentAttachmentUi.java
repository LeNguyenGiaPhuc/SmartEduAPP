package hcmute.com.smarteduapp.ui.document;

import android.app.Activity;
import android.net.Uri;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

import hcmute.com.smarteduapp.R;
import hcmute.com.smarteduapp.data.local.entity.StudyDocumentAttachment;
import hcmute.com.smarteduapp.ui.common.UiViewFactory;

/**
 * Displays document attachments and their preview.
 */
public class DocumentAttachmentUi {
    public interface AttachmentCallbacks {
        void onDeleteAttachment(StudyDocumentAttachment attachment);

        void onStudyActionsEnabledChanged(boolean enabled);
    }

    private final Activity activity;

    public DocumentAttachmentUi(Activity activity) {
        this.activity = activity;
    }

    public void renderThumbnails(List<StudyDocumentAttachment> attachments, AttachmentCallbacks callbacks) {
        LinearLayout container = activity.findViewById(R.id.imageThumbnailsContainer);
        if (container == null) {
            return;
        }

        View addButton = activity.findViewById(R.id.buttonAddMoreImages);
        container.removeAllViews();
        if (addButton != null) {
            container.addView(addButton);
        }

        ImageView preview = activity.findViewById(R.id.imageDocPreview);
        TextView placeholder = activity.findViewById(R.id.imageDocThumb);
        if (attachments.isEmpty()) {
            callbacks.onStudyActionsEnabledChanged(false);
            showDocumentImage(null, preview, placeholder);
            return;
        }

        callbacks.onStudyActionsEnabledChanged(true);
        showDocumentImage(attachments.get(0).attachmentUri, preview, placeholder);

        for (StudyDocumentAttachment attachment : attachments) {
            ImageView thumb = new ImageView(activity);
            int size = UiViewFactory.dp(activity, 60);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMargins(0, 0, UiViewFactory.dp(activity, 8), 0);
            thumb.setLayoutParams(lp);
            thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumb.setBackgroundResource(R.drawable.bg_pill);
            thumb.setClipToOutline(true);

            try {
                thumb.setImageURI(Uri.parse(attachment.attachmentUri));
            } catch (Exception e) {
                thumb.setImageResource(android.R.drawable.ic_menu_report_image);
            }

            thumb.setOnClickListener(v -> showDocumentImage(attachment.attachmentUri, preview, placeholder));
            thumb.setOnLongClickListener(v -> {
                callbacks.onDeleteAttachment(attachment);
                return true;
            });

            container.addView(thumb);
            UiViewFactory.applyPressEffect(thumb);
            UiViewFactory.animateIn(thumb, container.getChildCount());
        }
    }

    public void showDocumentImage(String imageUri, ImageView imagePreview, TextView imagePlaceholder) {
        if (imagePreview == null || imagePlaceholder == null) {
            return;
        }

        if (isBlank(imageUri)) {
            imagePreview.setVisibility(View.GONE);
            imagePlaceholder.setVisibility(View.VISIBLE);
            imagePlaceholder.setText("Chưa có file tài liệu");
            return;
        }

        if (!isImageAttachment(imageUri)) {
            imagePreview.setVisibility(View.GONE);
            imagePlaceholder.setVisibility(View.VISIBLE);
            imagePlaceholder.setText(getDocumentAttachmentLabel(imageUri));
            return;
        }

        try {
            imagePreview.setImageURI(Uri.parse(imageUri));
            imagePreview.setVisibility(View.VISIBLE);
            imagePlaceholder.setVisibility(View.GONE);
        } catch (Exception exception) {
            imagePreview.setVisibility(View.GONE);
            imagePlaceholder.setVisibility(View.VISIBLE);
            imagePlaceholder.setText("Không thể hiển thị ảnh");
        }
    }

    public String getDocumentAttachmentLabel(String attachmentUri) {
        if (isBlank(attachmentUri)) {
            return "Chưa có file";
        }
        String mimeType = getAttachmentMimeType(attachmentUri);
        if (!isBlank(mimeType)) {
            if (mimeType.startsWith("image/")) return "Có ảnh tài liệu";
            if ("application/pdf".equals(mimeType)) return "Có file PDF";
            if (mimeType.contains("wordprocessingml")) return "Có file DOCX";
            if (mimeType.startsWith("text/")) return "Có file văn bản";
        }
        return "Có file tài liệu";
    }

    private boolean isImageAttachment(String attachmentUri) {
        String mimeType = getAttachmentMimeType(attachmentUri);
        if (!isBlank(mimeType)) {
            return mimeType.startsWith("image/");
        }
        String lowerUri = attachmentUri == null ? "" : attachmentUri.toLowerCase(Locale.US);
        return lowerUri.endsWith(".jpg")
                || lowerUri.endsWith(".jpeg")
                || lowerUri.endsWith(".png")
                || lowerUri.endsWith(".webp");
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
