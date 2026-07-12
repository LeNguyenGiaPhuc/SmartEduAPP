package hcmute.com.smarteduapp.ui.main;

import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
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
import hcmute.com.smarteduapp.data.local.entity.StudyDocumentImage;
import hcmute.com.smarteduapp.data.repository.RepositoryCallback;
import hcmute.com.smarteduapp.service.document.DocumentTextScannerService;
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
        activity.selectedDocumentImageUri = uri;
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
        activity.selectedDocumentImageUri = null;
        activity.pendingCameraImageUri = null;
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
        String imageUri = activity.selectedDocumentImageUri == null ? null : activity.selectedDocumentImageUri.toString();
        if (documentId > 0) {
            updateDocument(documentId, title, imageUri);
            return;
        }

        activity.documentRepository.create(activity.selectedSubjectId, title, null, new RepositoryCallback<Long>() {
            @Override
            public void onSuccess(Long id) {
                if (activity.isBlank(imageUri)) {
                    activity.showSubjectDetail();
                    return;
                }
                activity.documentRepository.addImage(id, imageUri, new RepositoryCallback<Long>() {
                    @Override
                    public void onSuccess(Long imageId) {
                        activity.showSubjectDetail();
                    }

                    @Override
                    public void onError(Exception exception) {
                        Toast.makeText(activity, "Đã lưu tài liệu nhưng chưa lưu được file", Toast.LENGTH_SHORT).show();
                        activity.showSubjectDetail();
                    }
                });
            }

            @Override
            public void onError(Exception exception) {
                Toast.makeText(activity, "Không thể lưu tài liệu",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }


    void updateDocument(long documentId, String title, String imageUri) {
        if (activity.selectedDocument == null || activity.selectedDocument.id != documentId) {
            Toast.makeText(activity, "Không tìm thấy tài liệu để cập nhật", Toast.LENGTH_SHORT).show();
            return;
        }

        activity.selectedDocument.title = title;
        activity.selectedDocument.imageUri = null;
        activity.documentRepository.update(activity.selectedDocument, new RepositoryCallback<Integer>() {
            @Override
            public void onSuccess(Integer result) {
                if (activity.isBlank(imageUri)) {
                    Toast.makeText(activity, "Đã cập nhật tài liệu", Toast.LENGTH_SHORT).show();
                    showProcessDocument();
                    return;
                }
                activity.documentRepository.addImage(documentId, imageUri, new RepositoryCallback<Long>() {
                    @Override
                    public void onSuccess(Long imageId) {
                        activity.selectedDocumentImageUri = null;
                        Toast.makeText(activity, "Đã cập nhật tài liệu", Toast.LENGTH_SHORT).show();
                        showProcessDocument();
                    }

                    @Override
                    public void onError(Exception exception) {
                        Toast.makeText(activity, "Không thể thêm file vào tài liệu", Toast.LENGTH_SHORT).show();
                    }
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
            activity.pendingCameraImageUri = createCameraImageUri();
            activity.cameraCaptureLauncher.launch(activity.pendingCameraImageUri);
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
        if (label == null) {
            return;
        }
        if (activity.selectedDocumentImageUri == null) {
            label.setText("Chưa chọn ảnh hoặc file tài liệu");
            return;
        }
        label.setText(getDocumentAttachmentLabel(activity.selectedDocumentImageUri.toString()));
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
        ImageView imagePreview = activity.findViewById(R.id.imageDocPreview);
        TextView imagePlaceholder = activity.findViewById(R.id.imageDocThumb);

        if (activity.selectedDocument != null) {
            textDocName.setText("Tài liệu: " + activity.selectedDocument.title);
            if (activity.isBlank(activity.selectedDocument.ocrText)) {
                textContentStatus.setText("Chưa quét nội dung. Hãy quét tài liệu trước khi dùng AI.");
            } else {
                textContentStatus.setText("Đã có nội dung được quét. Bạn có thể xem nội dung hoặc dùng AI.");
            }
            showDocumentImage(null, imagePreview, imagePlaceholder);
            loadDocumentImages(activity.selectedDocument.id);
        }

        activity.bindClick(R.id.backHome, this::goBackFromProcessDocument);
        activity.bindClick(R.id.buttonRunOcr, this::runOcrForCurrentDocument);
        activity.bindClick(R.id.buttonViewDocumentContent, this::showDocumentContent);
        activity.bindClick(R.id.buttonEditDocument, () -> {
            if (activity.selectedDocument != null) {
                showDocumentForm(activity.selectedDocument.id);
            }
        });
        activity.bindClick(R.id.buttonSummary, activity::createSummaryFromCurrentDocument);
        activity.bindClick(R.id.buttonCreateQuiz, activity::createQuizFromCurrentDocument);
        activity.bindClick(R.id.buttonExplain, activity::showAiChat);
        activity.bindClick(R.id.buttonDeleteDocumentFromDetail, this::confirmDeleteCurrentDocument);
        activity.bindClick(R.id.buttonAddMoreImages, () -> activity.addMoreImagesPickerLauncher.launch(new String[]{
                "image/*",
                "application/pdf",
                "text/plain",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        }));
    }


    void goBackFromProcessDocument() {
        if (activity.documentOpenedFromHistory) {
            activity.documentOpenedFromHistory = false;
            activity.showHistory();
            return;
        }
        activity.showSubjectDetail();
    }


    void createExplanationFromCurrentDocument() {
        activity.showAiChat();
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
                    activity.selectedDocumentImages = new ArrayList<>();
                    Toast.makeText(activity, "Tài liệu đã bị xóa", Toast.LENGTH_SHORT).show();
                    activity.showSubjectDetail();
                    return;
                }

                activity.documentRepository.getImagesByDocumentId(documentId, new RepositoryCallback<List<StudyDocumentImage>>() {
                    @Override
                    public void onSuccess(List<StudyDocumentImage> images) {
                        activity.selectedDocument = document;
                        activity.selectedDocumentImages = images;
                        if (images.isEmpty()) {
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


    void loadDocumentImages(long documentId) {
        activity.documentRepository.getImagesByDocumentId(documentId, new RepositoryCallback<List<StudyDocumentImage>>() {
            @Override
            public void onSuccess(List<StudyDocumentImage> images) {
                if (activity.selectedDocument != null && !activity.isBlank(activity.selectedDocument.imageUri)) {
                    migrateLegacyDocumentAttachment(images);
                    return;
                }
                activity.selectedDocumentImages = images;
                renderThumbnails(images);
            }
            @Override
            public void onError(Exception e) {}
        });
    }


    void migrateLegacyDocumentAttachment(List<StudyDocumentImage> existingImages) {
        if (activity.selectedDocument == null || activity.isBlank(activity.selectedDocument.imageUri)) {
            activity.selectedDocumentImages = existingImages;
            renderThumbnails(existingImages);
            return;
        }

        String legacyUri = activity.selectedDocument.imageUri;
        activity.documentRepository.addImage(activity.selectedDocument.id, legacyUri, new RepositoryCallback<Long>() {
            @Override
            public void onSuccess(Long id) {
                activity.selectedDocument.imageUri = null;
                activity.documentRepository.update(activity.selectedDocument, new RepositoryCallback<Integer>() {
                    @Override
                    public void onSuccess(Integer result) {
                        loadDocumentImages(activity.selectedDocument.id);
                    }

                    @Override
                    public void onError(Exception exception) {
                        loadDocumentImages(activity.selectedDocument.id);
                    }
                });
            }

            @Override
            public void onError(Exception exception) {
                activity.selectedDocumentImages = existingImages;
                renderThumbnails(existingImages);
            }
        });
    }


    void renderThumbnails(List<StudyDocumentImage> images) {
        activity.documentAttachmentUi.renderThumbnails(images, new DocumentAttachmentUi.AttachmentCallbacks() {
            @Override
            public void onDeleteAttachment(StudyDocumentImage image) {
                confirmDeleteAttachment(image);
            }

            @Override
            public void onStudyActionsEnabledChanged(boolean enabled) {
                setStudyActionsEnabled(enabled);
            }
        });
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


    void confirmDeleteAttachment(StudyDocumentImage image) {
        new AlertDialog.Builder(activity)
                .setTitle("Xóa tài liệu đính kèm")
                .setMessage("Bạn có muốn xóa file/ảnh này khỏi bài học không?")
                .setPositiveButton("Xóa", (dialog, which) -> deleteAttachment(image))
                .setNegativeButton("Hủy", null)
                .show();
    }


    void deleteAttachment(StudyDocumentImage image) {
        activity.documentRepository.deleteImage(image, new RepositoryCallback<Integer>() {
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
        activity.documentRepository.getImagesByDocumentId(documentId, new RepositoryCallback<List<StudyDocumentImage>>() {
            @Override
            public void onSuccess(List<StudyDocumentImage> images) {
                activity.selectedDocumentImages = images;
                if (images.isEmpty()) {
                    clearGeneratedStateForCurrentDocument(true);
                    return;
                }
                renderThumbnails(images);
            }

            @Override
            public void onError(Exception exception) {
                loadDocumentImages(documentId);
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
        for (StudyDocumentImage img : activity.selectedDocumentImages) {
            if (!activity.isBlank(img.imageUri)) {
                attachmentUris.add(img.imageUri);
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


    void showDocumentImage(String imageUri, ImageView imagePreview, TextView imagePlaceholder) {
        activity.documentAttachmentUi.showDocumentImage(imageUri, imagePreview, imagePlaceholder);
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
