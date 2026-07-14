package hcmute.com.smarteduapp.data.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import hcmute.com.smarteduapp.data.local.dao.StudyDocumentDao;
import hcmute.com.smarteduapp.data.local.dao.StudyDocumentAttachmentDao;
import hcmute.com.smarteduapp.data.local.database.AppDatabase;
import hcmute.com.smarteduapp.data.local.entity.StudyDocument;
import hcmute.com.smarteduapp.data.local.entity.StudyDocumentAttachment;

/** Owns all document-related database operations. */
public class DocumentRepository {
    private final StudyDocumentDao documentDao;
    private final StudyDocumentAttachmentDao attachmentDao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public DocumentRepository(Context context) {
        AppDatabase db = AppDatabase.getInstance(context);
        documentDao = db.studyDocumentDao();
        attachmentDao = db.studyDocumentAttachmentDao();
    }

    public void getBySubjectId(long subjectId, RepositoryCallback<List<StudyDocument>> callback) {
        executor.execute(() -> {
            try {
                List<StudyDocument> documents = documentDao.getBySubjectId(subjectId);
                mainHandler.post(() -> callback.onSuccess(documents));
            } catch (Exception exception) {
                mainHandler.post(() -> callback.onError(exception));
            }
        });
    }

    public void getAll(RepositoryCallback<List<StudyDocument>> callback) {
        executor.execute(() -> {
            try {
                List<StudyDocument> documents = documentDao.getAll();
                mainHandler.post(() -> callback.onSuccess(documents));
            } catch (Exception exception) {
                mainHandler.post(() -> callback.onError(exception));
            }
        });
    }

    public void create(long subjectId, String title, RepositoryCallback<Long> callback) {
        executor.execute(() -> {
            try {
                long now = System.currentTimeMillis();
                long id = documentDao.insert(
                        new StudyDocument(subjectId, title, null, "", now, now)
                );
                mainHandler.post(() -> callback.onSuccess(id));
            } catch (Exception exception) {
                mainHandler.post(() -> callback.onError(exception));
            }
        });
    }

    public void getById(long id, RepositoryCallback<StudyDocument> callback) {
        executor.execute(() -> {
            try {
                StudyDocument document = documentDao.getById(id);
                mainHandler.post(() -> callback.onSuccess(document));
            } catch (Exception exception) {
                mainHandler.post(() -> callback.onError(exception));
            }
        });
    }

    public void update(StudyDocument document, RepositoryCallback<Integer> callback) {
        executor.execute(() -> {
            try {
                document.updatedAt = System.currentTimeMillis();
                int result = documentDao.update(document);
                mainHandler.post(() -> callback.onSuccess(result));
            } catch (Exception exception) {
                mainHandler.post(() -> callback.onError(exception));
            }
        });
    }

    public void delete(StudyDocument document, RepositoryCallback<Integer> callback) {
        executor.execute(() -> {
            try {
                int result = documentDao.delete(document);
                mainHandler.post(() -> callback.onSuccess(result));
            } catch (Exception exception) {
                mainHandler.post(() -> callback.onError(exception));
            }
        });
    }

    public void addAttachment(long documentId, String attachmentUri, RepositoryCallback<Long> callback) {
        executor.execute(() -> {
            try {
                List<StudyDocumentAttachment> existing = attachmentDao.getByDocumentId(documentId);
                for (StudyDocumentAttachment attachment : existing) {
                    if (attachment.attachmentUri != null && attachment.attachmentUri.equals(attachmentUri)) {
                        mainHandler.post(() -> callback.onSuccess(attachment.id));
                        return;
                    }
                }
                StudyDocumentAttachment attachment = new StudyDocumentAttachment(
                        documentId, attachmentUri, existing.size(), System.currentTimeMillis()
                );
                long id = attachmentDao.insert(attachment);
                mainHandler.post(() -> callback.onSuccess(id));
            } catch (Exception exception) {
                mainHandler.post(() -> callback.onError(exception));
            }
        });
    }

    public void getAttachmentsByDocumentId(long documentId, RepositoryCallback<List<StudyDocumentAttachment>> callback) {
        executor.execute(() -> {
            try {
                List<StudyDocumentAttachment> attachments = attachmentDao.getByDocumentId(documentId);
                mainHandler.post(() -> callback.onSuccess(attachments));
            } catch (Exception exception) {
                mainHandler.post(() -> callback.onError(exception));
            }
        });
    }

    public void deleteAttachment(StudyDocumentAttachment attachment, RepositoryCallback<Integer> callback) {
        executor.execute(() -> {
            try {
                int result = attachmentDao.delete(attachment);
                mainHandler.post(() -> callback.onSuccess(result));
            } catch (Exception exception) {
                mainHandler.post(() -> callback.onError(exception));
            }
        });
    }
}
