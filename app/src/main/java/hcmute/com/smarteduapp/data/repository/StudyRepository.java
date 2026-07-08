package hcmute.com.smarteduapp.data.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.List;

import hcmute.com.smarteduapp.data.local.database.AppDatabase;
import hcmute.com.smarteduapp.data.local.dao.QuizAttemptDao;
import hcmute.com.smarteduapp.data.local.dao.StudyQuestionDao;
import hcmute.com.smarteduapp.data.local.dao.StudySummaryDao;
import hcmute.com.smarteduapp.data.local.entity.StudySummary;
import hcmute.com.smarteduapp.data.local.entity.StudyQuestion;
import hcmute.com.smarteduapp.data.local.entity.QuizAttempt;


public class StudyRepository {
    private final StudySummaryDao summaryDao;
    private final StudyQuestionDao questionDao;
    private final QuizAttemptDao quizAttemptDao;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public StudyRepository(Context context){
        AppDatabase database = AppDatabase.getInstance(context);
        summaryDao = database.studySummaryDao();
        questionDao = database.studyQuestionDao();
        quizAttemptDao = database.quizAttemptDao();
    }

    public void createSummary(long documentId, String content, RepositoryCallback<Long> callback){
        executor.execute(() -> {
            try {
                StudySummary summary = new StudySummary(
                    documentId,
                    content,
                    System.currentTimeMillis()
                );
                long id = summaryDao.insert(summary);

                mainHandler.post(() -> callback.onSuccess(id));
            } catch (Exception exception){
                mainHandler.post(() -> callback.onError(exception));
            }
        });
    }

    public void getSummariesByDocumentId(long documentId, RepositoryCallback<List<StudySummary>> callback){
        executor.execute(() -> {
            try {
                List<StudySummary> summaries = summaryDao.getByDocumentId(documentId);

                mainHandler.post(() -> callback.onSuccess(summaries));
            } catch (Exception exception){
                mainHandler.post(() -> callback.onError(exception));
            }
        });
    }

    public void replaceQuestions(long documentId, List<StudyQuestion> questions, RepositoryCallback<Integer> callback) {
    executor.execute(() -> {
        try {
            questionDao.deleteByDocumentId(documentId);

            for (StudyQuestion question : questions) {
                questionDao.insert(question);
            }

            mainHandler.post(() -> callback.onSuccess(questions.size()));
        } catch (Exception exception) {
            mainHandler.post(() -> callback.onError(exception));
        }
    });
    }

    public void getQuestionsByDocumentId(long documentId, RepositoryCallback<List<StudyQuestion>> callback) {
    executor.execute(() -> {
        try {
            List<StudyQuestion> questions = questionDao.getByDocumentId(documentId);

            mainHandler.post(() -> callback.onSuccess(questions));
        } catch (Exception exception) {
            mainHandler.post(() -> callback.onError(exception));
        }
    });
    }

    public void createQuizAttempt(QuizAttempt attempt, RepositoryCallback<Long> callback) {
        executor.execute(() -> {
            try {
                long id = quizAttemptDao.insert(attempt);
                mainHandler.post(() -> callback.onSuccess(id));
            } catch (Exception exception) {
                mainHandler.post(() -> callback.onError(exception));
            }
        });
    }

    public void getAllQuizAttempts(RepositoryCallback<List<QuizAttempt>> callback) {
        executor.execute(() -> {
            try {
                List<QuizAttempt> attempts = quizAttemptDao.getAll();
                mainHandler.post(() -> callback.onSuccess(attempts));
            } catch (Exception exception) {
                mainHandler.post(() -> callback.onError(exception));
            }
        });
    }

}
