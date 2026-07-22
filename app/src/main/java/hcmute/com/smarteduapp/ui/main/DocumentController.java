package hcmute.com.smarteduapp.ui.main;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import hcmute.com.smarteduapp.R;
import hcmute.com.smarteduapp.data.local.entity.StudyDocument;
import hcmute.com.smarteduapp.data.local.entity.StudyDocumentAttachment;
import hcmute.com.smarteduapp.data.repository.RepositoryCallback;
import hcmute.com.smarteduapp.service.document.DocumentTextScannerService;
import hcmute.com.smarteduapp.ui.common.UiViewFactory;
import hcmute.com.smarteduapp.ui.document.DocumentAttachmentUi;

/**
 * Owns document CRUD, attachments, OCR and scanned-content screens.
 */
class DocumentController {
    private final MainActivity activity;

    DocumentController(MainActivity activity) {
        this.activity = activity;
    }

    void handlePickedDocumentUri(Uri uri) {
        try {
            activity.getContentResolver().takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
            );
        } catch (SecurityException ignored) {
            // Some providers do not expose persistable permissions. The URI is still usable
            // during the current app session, while ACTION_OPEN_DOCUMENT providers persist.
        }
        if (!activity.pendingDocumentAttachmentUris.contains(uri)) {
            activity.pendingDocumentAttachmentUris.add(uri);
        }
        activity.selectedDocumentAttachmentUri = uri;
        updateSelectedImageLabel();
    }


    void showDocumentForm() {
        showDocumentForm(-1L);
    }


    void showDocumentForm(long documentId) {
        if (activity.selectedSubjectId < 0) {
            activity.showHome();
            return;
        }
        activity.selectedDocumentAttachmentUri = null;
        activity.pendingCameraAttachmentUri = null;
        activity.pendingDocumentAttachmentUris.clear();
        boolean isEditing = documentId > 0;
        activity.currentScreen = R.layout.screen_document_form;
        activity.setContentView(R.layout.screen_document_form);
        activity.applySystemBars();

        EditText titleInput = activity.findViewById(R.id.inputDocumentTitle);
        TextView formTitle = activity.findViewById(R.id.documentFormTitle);
        TextView subjectLabel = activity.findViewById(R.id.documentSubjectLabel);
        View deleteButton = activity.findViewById(R.id.buttonDeleteDocument);
        formTitle.setText(isEditing ? "Chỉnh sửa tài liệu" : "Thêm tài liệu");
        deleteButton.setVisibility(isEditing ? View.VISIBLE : View.GONE);
        subjectLabel.setText("Môn học: " + (activity.selectedSubject == null ? "" : activity.selectedSubject.name));

        if (isEditing && activity.selectedDocument != null) {
            titleInput.setText(activity.selectedDocument.title);
        }

        updateSelectedImageLabel();
        activity.bindClick(R.id.backSubjectFromDocument, () -> {
            if (isEditing && activity.selectedDocument != null) {
                showProcessDocument();
                return;
            }
            activity.showSubjectDetail();
        });
        activity.bindClick(R.id.buttonCamera, this::captureDocumentImage);
        activity.bindClick(R.id.buttonGallery, this::pickDocumentImage);
        activity.bindClick(R.id.buttonFile, this::pickDocumentFile);
        activity.bindClick(R.id.buttonContinueOcr, () -> saveDocument(documentId, titleInput));
        activity.bindClick(R.id.buttonDeleteDocument, this::confirmDeleteCurrentDocument);
    }


    void saveDocument(long documentId, EditText titleInput) {
        String title = titleInput.getText().toString().trim();
        if (title.isEmpty()) {
            titleInput.setError("Nhập tên tài liệu");
            return;
        }
        if (documentId > 0) {
            updateDocument(documentId, title);
            return;
        }

        activity.documentRepository.create(activity.selectedSubjectId, title, new RepositoryCallback<Long>() {
            @Override
            public void onSuccess(Long id) {
                if (activity.pendingDocumentAttachmentUris.isEmpty()) {
                    activity.showSubjectDetail();
                    return;
                }
                savePendingAttachments(id, 0, activity::showSubjectDetail);
            }

            @Override
            public void onError(Exception exception) {
                Toast.makeText(activity, "Không thể lưu tài liệu",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }


    private void savePendingAttachments(long documentId, int index, Runnable onFinished) {
        if (index >= activity.pendingDocumentAttachmentUris.size()) {
            activity.pendingDocumentAttachmentUris.clear();
            activity.selectedDocumentAttachmentUri = null;
            onFinished.run();
            return;
        }

        String attachmentUri = activity.pendingDocumentAttachmentUris.get(index).toString();
        activity.documentRepository.addAttachment(documentId, attachmentUri, new RepositoryCallback<Long>() {
            @Override
            public void onSuccess(Long attachmentId) {
                savePendingAttachments(documentId, index + 1, onFinished);
            }

            @Override
            public void onError(Exception exception) {
                Toast.makeText(activity, "Đã lưu tài liệu nhưng có file chưa lưu được", Toast.LENGTH_SHORT).show();
                savePendingAttachments(documentId, index + 1, onFinished);
            }
        });
    }


    void updateDocument(long documentId, String title) {
        if (activity.selectedDocument == null || activity.selectedDocument.id != documentId) {
            Toast.makeText(activity, "Không tìm thấy tài liệu để cập nhật", Toast.LENGTH_SHORT).show();
            return;
        }

        activity.selectedDocument.title = title;
        activity.selectedDocument.legacyAttachmentUri = null;
        activity.documentRepository.update(activity.selectedDocument, new RepositoryCallback<Integer>() {
            @Override
            public void onSuccess(Integer result) {
                if (activity.pendingDocumentAttachmentUris.isEmpty()) {
                    Toast.makeText(activity, "Đã cập nhật tài liệu", Toast.LENGTH_SHORT).show();
                    showProcessDocument();
                    return;
                }
                savePendingAttachments(documentId, 0, () -> {
                    Toast.makeText(activity, "Đã cập nhật tài liệu", Toast.LENGTH_SHORT).show();
                    showProcessDocument();
                });
            }

            @Override
            public void onError(Exception exception) {
                Toast.makeText(activity, "Không thể cập nhật tài liệu", Toast.LENGTH_SHORT).show();
            }
        });
    }


    void pickDocumentImage() {
        activity.documentImagePickerLauncher.launch(new String[]{"image/*"});
    }


    void pickDocumentFile() {
        activity.documentFilePickerLauncher.launch(new String[]{
                "application/pdf",
                "text/plain",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "image/*"
        });
    }


    void captureDocumentImage() {
        try {
            activity.pendingCameraAttachmentUri = createCameraImageUri();
            activity.cameraCaptureLauncher.launch(activity.pendingCameraAttachmentUri);
        } catch (IOException exception) {
            Toast.makeText(activity, "Không thể tạo file ảnh", Toast.LENGTH_SHORT).show();
        }
    }


    Uri createCameraImageUri() throws IOException {
        File picturesDirectory = activity.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (picturesDirectory == null) {
            throw new IOException("Cannot access app pictures directory");
        }
        File imageDirectory = new File(picturesDirectory, "documents");
        if (!imageDirectory.exists() && !imageDirectory.mkdirs()) {
            throw new IOException("Cannot create image directory");
        }
        File imageFile = File.createTempFile(
                "smartedu_" + System.currentTimeMillis() + "_",
                ".jpg",
                imageDirectory
        );
        return FileProvider.getUriForFile(
                activity,
                activity.getPackageName() + ".fileprovider",
                imageFile
        );
    }


    void updateSelectedImageLabel() {
        TextView label = activity.findViewById(R.id.selectedImageLabel);
        LinearLayout list = activity.findViewById(R.id.selectedAttachmentPreview);
        if (label == null) {
            return;
        }

        if (list != null) {
            list.removeAllViews();
            list.setOrientation(LinearLayout.VERTICAL);
        }

        if (activity.pendingDocumentAttachmentUris.isEmpty()) {
            label.setVisibility(View.VISIBLE);
            if (list != null) {
                list.setVisibility(View.GONE);
            }
            label.setText("Chưa chọn ảnh hoặc file tài liệu");
            return;
        }

        if (list == null) {
            label.setVisibility(View.VISIBLE);
            label.setText("Đã chọn " + activity.pendingDocumentAttachmentUris.size() + " file/ảnh");
            return;
        }

        label.setVisibility(View.GONE);
        list.setVisibility(View.VISIBLE);

        TextView hint = new TextView(activity);
        hint.setText("Đã chọn " + activity.pendingDocumentAttachmentUris.size()
                + " file/ảnh · Bấm giữ một file để xóa");
        hint.setTextColor(activity.getColor(R.color.ink_muted));
        hint.setTextSize(13);
        list.addView(hint);

        for (int index = 0; index < activity.pendingDocumentAttachmentUris.size(); index++) {
            Uri uri = activity.pendingDocumentAttachmentUris.get(index);
            View row = createPendingAttachmentRow(uri, index);
            list.addView(row);
        }
    }


    private View createPendingAttachmentRow(Uri uri, int index) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(
                UiViewFactory.dp(activity, 8),
                UiViewFactory.dp(activity, 8),
                UiViewFactory.dp(activity, 8),
                UiViewFactory.dp(activity, 8)
        );

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        rowParams.topMargin = UiViewFactory.dp(activity, 8);
        row.setLayoutParams(rowParams);

        ImageView thumb = new ImageView(activity);
        int imageSize = UiViewFactory.dp(activity, 52);
        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(imageSize, imageSize);
        thumb.setLayoutParams(imageParams);
        thumb.setBackgroundResource(R.drawable.bg_document_thumb);
        thumb.setPadding(UiViewFactory.dp(activity, 8), UiViewFactory.dp(activity, 8),
                UiViewFactory.dp(activity, 8), UiViewFactory.dp(activity, 8));

        String attachmentUri = uri.toString();
        if (isImageAttachment(attachmentUri)) {
            thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumb.setPadding(0, 0, 0, 0);
            thumb.setImageURI(uri);
        } else {
            thumb.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            thumb.setImageResource(android.R.drawable.ic_menu_upload);
        }

        LinearLayout textBox = new LinearLayout(activity);
        textBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        );
        textParams.setMargins(UiViewFactory.dp(activity, 12), 0, 0, 0);
        textBox.setLayoutParams(textParams);

        TextView title = new TextView(activity);
        title.setText(getAttachmentDisplayName(uri));
        title.setTextColor(activity.getColor(R.color.ink));
        title.setTextSize(14);
        title.setTypeface(null, Typeface.BOLD);
        title.setSingleLine(true);

        TextView subtitle = new TextView(activity);
        subtitle.setText(getDocumentAttachmentLabel(attachmentUri));
        subtitle.setTextColor(activity.getColor(R.color.ink_muted));
        subtitle.setTextSize(12);

        textBox.addView(title);
        textBox.addView(subtitle);
        row.addView(thumb);
        row.addView(textBox);

        row.setOnLongClickListener(v -> {
            confirmRemovePendingAttachment(index);
            return true;
        });
        UiViewFactory.applyPressEffect(row);
        return row;
    }


    private void confirmRemovePendingAttachment(int index) {
        if (index < 0 || index >= activity.pendingDocumentAttachmentUris.size()) {
            return;
        }

        new AlertDialog.Builder(activity)
                .setTitle("Xóa khỏi danh sách chọn")
                .setMessage("Bạn có muốn bỏ file/ảnh này khỏi tài liệu đang chọn không?")
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Xóa", (dialog, which) -> removePendingAttachment(index))
                .show();
    }


    private void removePendingAttachment(int index) {
        if (index < 0 || index >= activity.pendingDocumentAttachmentUris.size()) {
            return;
        }

        activity.pendingDocumentAttachmentUris.remove(index);
        if (activity.pendingDocumentAttachmentUris.isEmpty()) {
            activity.selectedDocumentAttachmentUri = null;
        } else {
            activity.selectedDocumentAttachmentUri = activity.pendingDocumentAttachmentUris
                    .get(activity.pendingDocumentAttachmentUris.size() - 1);
        }
        updateSelectedImageLabel();
    }


    private boolean isImageAttachment(String attachmentUri) {
        if (activity.isBlank(attachmentUri)) {
            return false;
        }

        String mimeType = activity.getContentResolver().getType(Uri.parse(attachmentUri));
        if (mimeType != null) {
            return mimeType.startsWith("image/");
        }

        String lower = attachmentUri.toLowerCase();
        return lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".png")
                || lower.endsWith(".webp");
    }


    private String getAttachmentDisplayName(Uri uri) {
        try (Cursor cursor = activity.getContentResolver().query(
                uri,
                new String[]{OpenableColumns.DISPLAY_NAME},
                null,
                null,
                null
        )) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIndex >= 0) {
                    String name = cursor.getString(nameIndex);
                    if (!activity.isBlank(name)) {
                        return name;
                    }
                }
            }
        } catch (Exception ignored) {
            // Camera/FileProvider URI có thể không trả về display name.
        }
        return "Sẵn sàng lưu vào tài liệu";
    }


    private String getSelectedAttachmentSummary() {
        if (activity.pendingDocumentAttachmentUris.isEmpty()) {
            return "";
        }

        String firstName = getAttachmentDisplayName(activity.pendingDocumentAttachmentUris.get(0));
        int remaining = activity.pendingDocumentAttachmentUris.size() - 1;
        if (remaining <= 0) {
            return firstName;
        }
        return firstName + " và " + remaining + " file khác";
    }


    void openDocument(long id) {
        activity.documentRepository.getById(id, new RepositoryCallback<StudyDocument>() {
            @Override
            public void onSuccess(StudyDocument document) {
                if (document != null) {
                    activity.selectedDocument = document;
                    showProcessDocument();
                }
            }
        });
    }


    void showProcessDocument() {
        activity.currentScreen = R.layout.screen_process_document;
        activity.setContentView(R.layout.screen_process_document);
        activity.applySystemBars();

        TextView textDocName = activity.findViewById(R.id.textDocName);
        TextView textContentStatus = activity.findViewById(R.id.textContentStatus);
        if (activity.selectedDocument != null) {
            textDocName.setText("Tài liệu: " + activity.selectedDocument.title);
            if (activity.isBlank(activity.selectedDocument.ocrText)) {
                textContentStatus.setText("Chưa quét nội dung. Hãy quét tài liệu trước khi dùng AI.");
            } else {
                textContentStatus.setText("Đã có nội dung được quét. Bạn có thể xem nội dung hoặc dùng AI.");
            }
            loadDocumentAttachments(activity.selectedDocument.id);
        }

        activity.bindClick(R.id.backHome, this::goBackFromProcessDocument);
        activity.bindClick(R.id.buttonOpenDocumentAttachments, this::showAttachmentList);
        activity.bindClick(R.id.buttonEditDocument, () -> {
            if (activity.selectedDocument != null) {
                showDocumentForm(activity.selectedDocument.id);
            }
        });
        activity.bindClick(R.id.buttonRunOcr, this::runOcrForCurrentDocument);
        activity.bindClick(R.id.buttonViewDocumentContent, this::showDocumentContent);
        activity.bindClick(R.id.buttonSummary, activity::createSummaryFromCurrentDocument);
        activity.bindClick(R.id.buttonCreateQuiz, activity::createQuizFromCurrentDocument);
        activity.bindClick(R.id.buttonExplain, activity::showAiChat);
        activity.bindClick(R.id.buttonReviewMistakes, activity::reviewMistakesForCurrentDocument);
    }


    void showAttachmentList() {
        if (activity.selectedDocument == null) {
            activity.showSubjectDetail();
            return;
        }

        activity.currentScreen = R.layout.screen_document_attachments;
        activity.setContentView(R.layout.screen_document_attachments);
        activity.applySystemBars();

        TextView title = activity.findViewById(R.id.documentAttachmentsTitle);
        TextView subtitle = activity.findViewById(R.id.documentAttachmentsSubtitle);
        title.setText("Danh sách tài liệu");
        subtitle.setText("Các ảnh và file của: " + activity.selectedDocument.title);

        activity.bindClick(R.id.backDocumentAttachments, this::showProcessDocument);
        activity.bindClick(R.id.buttonAddDocumentAttachment, () -> activity.addMoreAttachmentsPickerLauncher.launch(new String[]{
                "image/*",
                "application/pdf",
                "text/plain",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        }));
        loadDocumentAttachments(activity.selectedDocument.id);
    }


    void goBackFromProcessDocument() {
        if (activity.documentOpenedFromHistory) {
            activity.documentOpenedFromHistory = false;
            activity.showHistory();
            return;
        }
        activity.showSubjectDetail();
    }


    void ensureCurrentDocumentHasAttachments(String unavailableMessage, Runnable action) {
        if (activity.selectedDocument == null) {
            activity.showSubjectDetail();
            return;
        }

        long documentId = activity.selectedDocument.id;
        activity.documentRepository.getById(documentId, new RepositoryCallback<StudyDocument>() {
            @Override
            public void onSuccess(StudyDocument document) {
                if (document == null) {
                    activity.selectedDocument = null;
                    activity.selectedDocumentAttachments = new ArrayList<>();
                    Toast.makeText(activity, "Tài liệu đã bị xóa", Toast.LENGTH_SHORT).show();
                    activity.showSubjectDetail();
                    return;
                }

                activity.documentRepository.getAttachmentsByDocumentId(documentId, new RepositoryCallback<List<StudyDocumentAttachment>>() {
                    @Override
                    public void onSuccess(List<StudyDocumentAttachment> attachments) {
                        activity.selectedDocument = document;
                        activity.selectedDocumentAttachments = attachments;
                        if (attachments.isEmpty()) {
                            Toast.makeText(activity, unavailableMessage, Toast.LENGTH_SHORT).show();
                            clearGeneratedStateForCurrentDocument(false);
                            return;
                        }
                        action.run();
                    }

                    @Override
                    public void onError(Exception exception) {
                        Toast.makeText(activity, "Không thể kiểm tra file tài liệu", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(Exception exception) {
                Toast.makeText(activity, "Không thể kiểm tra tài liệu", Toast.LENGTH_SHORT).show();
            }
        });
    }


    void loadDocumentAttachments(long documentId) {
        activity.documentRepository.getAttachmentsByDocumentId(documentId, new RepositoryCallback<List<StudyDocumentAttachment>>() {
            @Override
            public void onSuccess(List<StudyDocumentAttachment> attachments) {
                if (activity.selectedDocument != null && !activity.isBlank(activity.selectedDocument.legacyAttachmentUri)) {
                    migrateLegacyDocumentAttachment(attachments);
                    return;
                }
                activity.selectedDocumentAttachments = attachments;
                renderAttachmentList(attachments);
            }
            @Override
            public void onError(Exception e) {}
        });
    }


    void migrateLegacyDocumentAttachment(List<StudyDocumentAttachment> existingAttachments) {
        if (activity.selectedDocument == null || activity.isBlank(activity.selectedDocument.legacyAttachmentUri)) {
            activity.selectedDocumentAttachments = existingAttachments;
            renderAttachmentList(existingAttachments);
            return;
        }

        String legacyUri = activity.selectedDocument.legacyAttachmentUri;
        activity.documentRepository.addAttachment(activity.selectedDocument.id, legacyUri, new RepositoryCallback<Long>() {
            @Override
            public void onSuccess(Long id) {
                activity.selectedDocument.legacyAttachmentUri = null;
                activity.documentRepository.update(activity.selectedDocument, new RepositoryCallback<Integer>() {
                    @Override
                    public void onSuccess(Integer result) {
                        loadDocumentAttachments(activity.selectedDocument.id);
                    }

                    @Override
                    public void onError(Exception exception) {
                        loadDocumentAttachments(activity.selectedDocument.id);
                    }
                });
            }

            @Override
            public void onError(Exception exception) {
                activity.selectedDocumentAttachments = existingAttachments;
                renderAttachmentList(existingAttachments);
            }
        });
    }


    void renderAttachmentList(List<StudyDocumentAttachment> attachments) {
        activity.documentAttachmentUi.renderAttachmentList(attachments, new DocumentAttachmentUi.AttachmentCallbacks() {
            @Override
            public void onDeleteAttachment(StudyDocumentAttachment attachment) {
                confirmDeleteAttachment(attachment);
            }
        });
        setStudyActionsEnabled(!attachments.isEmpty());
    }


    void setStudyActionsEnabled(boolean enabled) {
        int[] actionIds = {
                R.id.buttonRunOcr,
                R.id.buttonSummary,
                R.id.buttonCreateQuiz,
                R.id.buttonExplain
        };
        float alpha = enabled ? 1f : 0.45f;
        for (int actionId : actionIds) {
            View action = activity.findViewById(actionId);
            if (action != null) {
                action.setEnabled(enabled);
                action.animate().alpha(alpha).setDuration(160).start();
            }
        }
    }


    void showDocumentContent() {
        if (activity.selectedDocument == null) {
            activity.showSubjectDetail();
            return;
        }

        activity.currentScreen = R.layout.screen_document_content;
        activity.setContentView(R.layout.screen_document_content);
        activity.applySystemBars();

        TextView title = activity.findViewById(R.id.textDocumentContentTitle);
        TextView content = activity.findViewById(R.id.textDocumentContent);
        title.setText("Nội dung: " + activity.selectedDocument.title);
        content.setText(activity.isBlank(activity.selectedDocument.ocrText)
                ? "Chưa có nội dung được quét."
                : activity.selectedDocument.ocrText);

        activity.bindClick(R.id.backProcessFromContent, this::showProcessDocument);
        activity.bindClick(R.id.buttonEditDocumentContent, this::showEditDocumentContent);
    }


    void showEditDocumentContent() {
        if (activity.selectedDocument == null) {
            activity.showSubjectDetail();
            return;
        }

        activity.currentScreen = R.layout.screen_document_content_edit;
        activity.setContentView(R.layout.screen_document_content_edit);
        activity.applySystemBars();

        TextView title = activity.findViewById(R.id.textEditContentTitle);
        EditText contentInput = activity.findViewById(R.id.editDocumentContent);
        title.setText("Sửa nội dung: " + activity.selectedDocument.title);
        contentInput.setText(activity.selectedDocument.ocrText == null ? "" : activity.selectedDocument.ocrText);

        activity.bindClick(R.id.backContentFromEdit, this::showDocumentContent);
        activity.bindClick(R.id.buttonSaveDocumentContent, () -> saveEditedDocumentContent(contentInput));
    }


    void saveEditedDocumentContent(EditText contentInput) {
        if (activity.selectedDocument == null) {
            return;
        }

        activity.selectedDocument.ocrText = contentInput.getText().toString().trim();
        activity.documentRepository.update(activity.selectedDocument, new RepositoryCallback<Integer>() {
            @Override
            public void onSuccess(Integer result) {
                Toast.makeText(activity, "Đã lưu nội dung tài liệu", Toast.LENGTH_SHORT).show();
                showDocumentContent();
            }

            @Override
            public void onError(Exception exception) {
                Toast.makeText(activity, "Không thể lưu nội dung", Toast.LENGTH_SHORT).show();
            }
        });
    }


    void confirmDeleteAttachment(StudyDocumentAttachment attachment) {
        new AlertDialog.Builder(activity)
                .setTitle("Xóa tài liệu đính kèm")
                .setMessage("Bạn có muốn xóa file/ảnh này khỏi bài học không?")
                .setPositiveButton("Xóa", (dialog, which) -> deleteAttachment(attachment))
                .setNegativeButton("Hủy", null)
                .show();
    }


    void deleteAttachment(StudyDocumentAttachment attachment) {
        activity.documentRepository.deleteAttachment(attachment, new RepositoryCallback<Integer>() {
            @Override
            public void onSuccess(Integer result) {
                Toast.makeText(activity, "Đã xóa tài liệu đính kèm", Toast.LENGTH_SHORT).show();
                if (activity.selectedDocument != null) {
                    refreshAttachmentsAfterDelete(activity.selectedDocument.id);
                }
            }

            @Override
            public void onError(Exception exception) {
                Toast.makeText(activity, "Không thể xóa tài liệu đính kèm", Toast.LENGTH_SHORT).show();
            }
        });
    }


    void refreshAttachmentsAfterDelete(long documentId) {
        activity.documentRepository.getAttachmentsByDocumentId(documentId, new RepositoryCallback<List<StudyDocumentAttachment>>() {
            @Override
            public void onSuccess(List<StudyDocumentAttachment> attachments) {
                activity.selectedDocumentAttachments = attachments;
                if (attachments.isEmpty()) {
                    clearGeneratedStateForCurrentDocument(true);
                    return;
                }
                renderAttachmentList(attachments);
            }

            @Override
            public void onError(Exception exception) {
                loadDocumentAttachments(documentId);
            }
        });
    }


    void clearGeneratedStateForCurrentDocument(boolean showSuccessMessage) {
        if (activity.selectedDocument == null) {
            return;
        }

        activity.selectedDocument.ocrText = "";
        activity.latestDisplayedSummary = null;
        activity.currentQuizQuestions.clear();
        activity.selectedQuizAnswers.clear();
        activity.latestQuizAttempt = null;
        long documentId = activity.selectedDocument.id;

        activity.documentRepository.update(activity.selectedDocument, new RepositoryCallback<Integer>() {
            @Override
            public void onSuccess(Integer result) {
                activity.studyRepository.clearGeneratedDataByDocumentId(documentId, new RepositoryCallback<Integer>() {
                    @Override
                    public void onSuccess(Integer deletedCount) {
                        if (showSuccessMessage) {
                            Toast.makeText(
                                    activity,
                                    "Đã xóa file cuối cùng và dọn OCR/AI/quiz của tài liệu",
                                    Toast.LENGTH_SHORT
                            ).show();
                        }
                        showProcessDocument();
                    }

                    @Override
                    public void onError(Exception exception) {
                        Toast.makeText(activity, "Đã xóa file nhưng chưa dọn được dữ liệu AI/quiz", Toast.LENGTH_SHORT).show();
                        showProcessDocument();
                    }
                });
            }

            @Override
            public void onError(Exception exception) {
                Toast.makeText(activity, "Không thể cập nhật trạng thái tài liệu", Toast.LENGTH_SHORT).show();
                showProcessDocument();
            }
        });
    }


    void confirmDeleteCurrentDocument() {
        if (activity.selectedDocument == null) {
            return;
        }

        new AlertDialog.Builder(activity)
                .setTitle("Xóa tài liệu")
                .setMessage("Bạn có chắc muốn xóa tài liệu \"" + activity.selectedDocument.title + "\" không?")
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Xóa", (dialog, which) -> deleteCurrentDocument())
                .show();
    }


    void deleteCurrentDocument() {
        if (activity.selectedDocument == null) {
            return;
        }

        activity.documentRepository.delete(activity.selectedDocument, new RepositoryCallback<Integer>() {
            @Override
            public void onSuccess(Integer result) {
                activity.selectedDocument = null;
                Toast.makeText(activity, "Đã xóa tài liệu", Toast.LENGTH_SHORT).show();
                if (activity.documentOpenedFromHistory) {
                    activity.documentOpenedFromHistory = false;
                    activity.showHistory();
                } else {
                    activity.showSubjectDetail();
                }
            }

            @Override
            public void onError(Exception exception) {
                Toast.makeText(activity, "Không thể xóa tài liệu", Toast.LENGTH_SHORT).show();
            }
        });
    }


    void runOcrForCurrentDocument() {
        if (activity.selectedDocument == null) {
            return;
        }

        List<String> attachmentUris = new ArrayList<>();
        for (StudyDocumentAttachment attachment : activity.selectedDocumentAttachments) {
            if (!activity.isBlank(attachment.attachmentUri)) {
                attachmentUris.add(attachment.attachmentUri);
            }
        }

        if (attachmentUris.isEmpty()) {
            Toast.makeText(activity, "Tài liệu chưa có ảnh/file hợp lệ để quét", Toast.LENGTH_SHORT).show();
            return;
        }

        setOcrLoading(true);
        Toast.makeText(activity, "Đang quét nội dung tài liệu...", Toast.LENGTH_SHORT).show();
        activity.documentTextScannerService.scanAttachments(
                activity,
                attachmentUris,
                new DocumentTextScannerService.ScanCallback() {
                    @Override
                    public void onSuccess(String text) {
                        activity.runOnUiThread(() -> handleOcrResult(text));
                    }

                    @Override
                    public void onPartialFailure(int failedCount, int totalCount) {
                        if (failedCount < totalCount) {
                            activity.runOnUiThread(() -> Toast.makeText(
                                    activity,
                                    "Đã quét được " + (totalCount - failedCount) + "/" + totalCount
                                            + " file. Một số file chưa đọc được.",
                                    Toast.LENGTH_LONG
                            ).show());
                        }
                    }

                    @Override
                    public void onError(Exception exception) {
                        activity.runOnUiThread(() -> {
                            setOcrLoading(false);
                            Toast.makeText(
                                    activity,
                                    exception.getMessage() == null
                                            ? "Không thể quét nội dung tài liệu"
                                            : exception.getMessage(),
                                    Toast.LENGTH_SHORT
                            ).show();
                        });
                    }
                }
        );
    }


    void handleOcrResult(String recognizedText) {
        if (activity.isBlank(recognizedText)) {
            setOcrLoading(false);
            Toast.makeText(activity, "Ảnh không có văn bản nhận dạng được", Toast.LENGTH_SHORT).show();
            return;
        }

        activity.selectedDocument.ocrText = recognizedText;
        activity.documentRepository.update(activity.selectedDocument, new RepositoryCallback<Integer>() {
            @Override
            public void onSuccess(Integer result) {
                Toast.makeText(activity, "Quét nội dung tài liệu thành công", Toast.LENGTH_SHORT).show();
                showProcessDocument();
            }

            @Override
            public void onError(Exception exception) {
                setOcrLoading(false);
                Toast.makeText(activity, "Quét được nội dung nhưng chưa lưu được", Toast.LENGTH_SHORT).show();
            }
        });
    }


    private void setOcrLoading(boolean loading) {
        TextView button = activity.findViewById(R.id.buttonRunOcr);
        if (button != null) {
            button.setText(loading ? "Đang quét..." : "Quét nội dung tài liệu");
        }
        setStudyActionsEnabled(!loading);
    }


    String getDocumentAttachmentLabel(String attachmentUri) {
        return activity.documentAttachmentUi.getDocumentAttachmentLabel(attachmentUri);
    }


    void saveDocumentContent() {
        if (activity.selectedDocument == null) return;
        activity.documentRepository.update(activity.selectedDocument, new RepositoryCallback<Integer>() {
            @Override
            public void onSuccess(Integer result) {
                Toast.makeText(activity, "Đã lưu nội dung tài liệu!", Toast.LENGTH_SHORT).show();
            }
        });
    }


}
