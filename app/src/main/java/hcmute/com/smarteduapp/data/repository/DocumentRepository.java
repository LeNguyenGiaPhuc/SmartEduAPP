package hcmute.com.smarteduapp.data.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import hcmute.com.smarteduapp.data.local.dao.StudyDocumentDao;
import hcmute.com.smarteduapp.data.local.database.AppDatabase;
import hcmute.com.smarteduapp.data.local.entity.StudyDocument;

/** Owns all document-related database operations. */
public class DocumentRepository {
    private final StudyDocumentDao documentDao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public DocumentRepository(Context context) {
        documentDao = AppDatabase.getInstance(context).studyDocumentDao();
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
}
