package hcmute.com.smarteduapp.data.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import hcmute.com.smarteduapp.data.local.dao.SubjectDao;
import hcmute.com.smarteduapp.data.local.database.AppDatabase;
import hcmute.com.smarteduapp.data.local.entity.Subject;

/** Owns all subject-related database operations. */
public class SubjectRepository {
    private final SubjectDao subjectDao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public SubjectRepository(Context context) {
        subjectDao = AppDatabase.getInstance(context).subjectDao();
    }

    public void getAll(RepositoryCallback<List<Subject>> callback) {
        executor.execute(() -> {
            try {
                List<Subject> subjects = subjectDao.getAll();
                mainHandler.post(() -> callback.onSuccess(subjects));
            } catch (Exception exception) {
                mainHandler.post(() -> callback.onError(exception));
            }
        });
    }

    public void getById(long id, RepositoryCallback<Subject> callback) {
        executor.execute(() -> {
            try {
                Subject subject = subjectDao.getById(id);
                mainHandler.post(() -> callback.onSuccess(subject));
            } catch (Exception exception) {
                mainHandler.post(() -> callback.onError(exception));
            }
        });
    }

    public void create(String name, String description, RepositoryCallback<Long> callback) {
        executor.execute(() -> {
            try {
                long id = subjectDao.insert(new Subject(name, description, System.currentTimeMillis()));
                mainHandler.post(() -> callback.onSuccess(id));
            } catch (Exception exception) {
                mainHandler.post(() -> callback.onError(exception));
            }
        });
    }

    public void update(Subject subject, RepositoryCallback<Integer> callback) {
        executor.execute(() -> {
            try {
                int count = subjectDao.update(subject);
                mainHandler.post(() -> callback.onSuccess(count));
            } catch (Exception exception) {
                mainHandler.post(() -> callback.onError(exception));
            }
        });
    }

    public void delete(Subject subject, RepositoryCallback<Integer> callback) {
        executor.execute(() -> {
            try {
                int count = subjectDao.delete(subject);
                mainHandler.post(() -> callback.onSuccess(count));
            } catch (Exception exception) {
                mainHandler.post(() -> callback.onError(exception));
            }
        });
    }
}
