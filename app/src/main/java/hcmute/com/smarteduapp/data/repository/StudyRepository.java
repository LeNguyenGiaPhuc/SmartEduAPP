package hcmute.com.smarteduapp.data.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.List;

import hcmute.com.smarteduapp.data.local.database.AppDatabase;
import hcmute.com.smarteduapp.data.local.dao.QuizAttemptAnswerDao;
import hcmute.com.smarteduapp.data.local.dao.QuizAttemptDao;
import hcmute.com.smarteduapp.data.local.dao.StudyQuestionDao;
import hcmute.com.smarteduapp.data.local.dao.StudySummaryDao;
import hcmute.com.smarteduapp.data.local.entity.StudySummary;
import hcmute.com.smarteduapp.data.local.entity.StudyQuestion;
import hcmute.com.smarteduapp.data.local.entity.QuizAttempt;
import hcmute.com.smarteduapp.data.local.entity.QuizAttemptAnswer;


public class StudyRepository {
    private final StudySummaryDao summaryDao;
    private final StudyQuestionDao questionDao;
    private final QuizAttemptDao quizAttemptDao;
    private final QuizAttemptAnswerDao quizAttemptAnswerDao;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public StudyRepository(Context context){
        AppDatabase database = AppDatabase.getInstance(context);
        summaryDao = database.studySummaryDao();
        questionDao = database.studyQuestionDao();
        quizAttemptDao = database.quizAttemptDao();
        quizAttemptAnswerDao = database.quizAttemptAnswerDao();
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

    public void getAllSummaries(RepositoryCallback<List<StudySummary>> callback) {
        executor.execute(() -> {
            try {
                List<StudySummary> summaries = summaryDao.getAll();
                mainHandler.post(() -> callback.onSuccess(summaries));
            } catch (Exception exception) {
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

    public void deleteSummary(StudySummary summary, RepositoryCallback<Integer> callback) {
        executor.execute(() -> {
            try {
                int result = summaryDao.delete(summary);
                mainHandler.post(() -> callback.onSuccess(result));
            } catch (Exception exception) {
                mainHandler.post(() -> callback.onError(exception));
            }
        });
    }

    public void clearGeneratedDataByDocumentId(long documentId, RepositoryCallback<Integer> callback) {
        executor.execute(() -> {
            try {
                int deletedCount = 0;
                deletedCount += summaryDao.deleteByDocumentId(documentId);
                deletedCount += questionDao.deleteByDocumentId(documentId);
                deletedCount += quizAttemptAnswerDao.deleteByDocumentId(documentId);
                deletedCount += quizAttemptDao.deleteByDocumentId(documentId);
                int result = deletedCount;
                mainHandler.post(() -> callback.onSuccess(result));
            } catch (Exception exception) {
                mainHandler.post(() -> callback.onError(exception));
            }
        });
    }

    public void createQuizAttemptWithAnswers(QuizAttempt attempt, List<QuizAttemptAnswer> answers,
                                             RepositoryCallback<Long> callback) {
        executor.execute(() -> {
            try {
                long attemptId = quizAttemptDao.insert(attempt);
                for (QuizAttemptAnswer answer : answers) {
                    answer.attempt_id = attemptId;
                }
                if (!answers.isEmpty()) {
                    quizAttemptAnswerDao.insertAll(answers);
                }
                mainHandler.post(() -> callback.onSuccess(attemptId));
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

    public void getQuizAttemptAnswers(long attemptId, RepositoryCallback<List<QuizAttemptAnswer>> callback) {
        executor.execute(() -> {
            try {
                List<QuizAttemptAnswer> answers = quizAttemptAnswerDao.getByAttemptId(attemptId);
                mainHandler.post(() -> callback.onSuccess(answers));
            } catch (Exception exception) {
                mainHandler.post(() -> callback.onError(exception));
            }
        });
    }

    public void deleteQuizAttempt(QuizAttempt attempt, RepositoryCallback<Integer> callback) {
        executor.execute(() -> {
            try {
                int deletedCount = quizAttemptAnswerDao.deleteByAttemptId(attempt.id);
                deletedCount += quizAttemptDao.delete(attempt);
                int result = deletedCount;
                mainHandler.post(() -> callback.onSuccess(result));
            } catch (Exception exception) {
                mainHandler.post(() -> callback.onError(exception));
            }
        });
    }

}
