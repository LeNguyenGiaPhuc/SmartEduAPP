package hcmute.com.smarteduapp.data.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import hcmute.com.smarteduapp.data.local.dao.StudyDocumentDao;
import hcmute.com.smarteduapp.data.local.dao.StudyDocumentImageDao;
import hcmute.com.smarteduapp.data.local.database.AppDatabase;
import hcmute.com.smarteduapp.data.local.entity.StudyDocument;
import hcmute.com.smarteduapp.data.local.entity.StudyDocumentImage;

/** Owns all document-related database operations. */
public class DocumentRepository {
    private final StudyDocumentDao documentDao;
    private final StudyDocumentImageDao imageDao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public DocumentRepository(Context context) {
        AppDatabase db = AppDatabase.getInstance(context);
        documentDao = db.studyDocumentDao();
        imageDao = db.studyDocumentImageDao();
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
        create(subjectId, title, null, callback);
    }

    public void create(long subjectId, String title, String imageUri, RepositoryCallback<Long> callback) {
        executor.execute(() -> {
            try {
                long now = System.currentTimeMillis();
                long id = documentDao.insert(
                        new StudyDocument(subjectId, title, imageUri, "", now, now)
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

    public void addImage(long documentId, String imageUri, RepositoryCallback<Long> callback) {
        executor.execute(() -> {
            try {
                List<StudyDocumentImage> existing = imageDao.getByDocumentId(documentId);
                for (StudyDocumentImage image : existing) {
                    if (image.imageUri != null && image.imageUri.equals(imageUri)) {
                        mainHandler.post(() -> callback.onSuccess(image.id));
                        return;
                    }
                }
                StudyDocumentImage image = new StudyDocumentImage(
                        documentId, imageUri, "", existing.size(), System.currentTimeMillis()
                );
                long id = imageDao.insert(image);
                mainHandler.post(() -> callback.onSuccess(id));
            } catch (Exception exception) {
                mainHandler.post(() -> callback.onError(exception));
            }
        });
    }

    public void getImagesByDocumentId(long documentId, RepositoryCallback<List<StudyDocumentImage>> callback) {
        executor.execute(() -> {
            try {
                List<StudyDocumentImage> images = imageDao.getByDocumentId(documentId);
                mainHandler.post(() -> callback.onSuccess(images));
            } catch (Exception exception) {
                mainHandler.post(() -> callback.onError(exception));
            }
        });
    }

    public void updateImage(StudyDocumentImage image, RepositoryCallback<Integer> callback) {
        executor.execute(() -> {
            try {
                int result = imageDao.update(image);
                mainHandler.post(() -> callback.onSuccess(result));
            } catch (Exception exception) {
                mainHandler.post(() -> callback.onError(exception));
            }
        });
    }

    public void deleteImage(StudyDocumentImage image, RepositoryCallback<Integer> callback) {
        executor.execute(() -> {
            try {
                int result = imageDao.delete(image);
                mainHandler.post(() -> callback.onSuccess(result));
            } catch (Exception exception) {
                mainHandler.post(() -> callback.onError(exception));
            }
        });
    }
}
