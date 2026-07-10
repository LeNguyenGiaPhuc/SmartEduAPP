package hcmute.com.smarteduapp.data.local.database;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import hcmute.com.smarteduapp.data.local.dao.QuizAttemptDao;
import hcmute.com.smarteduapp.data.local.dao.QuizAttemptAnswerDao;
import hcmute.com.smarteduapp.data.local.dao.StudyDocumentDao;
import hcmute.com.smarteduapp.data.local.dao.StudyDocumentImageDao;
import hcmute.com.smarteduapp.data.local.dao.StudyQuestionDao;
import hcmute.com.smarteduapp.data.local.dao.StudySummaryDao;
import hcmute.com.smarteduapp.data.local.dao.SubjectDao;
import hcmute.com.smarteduapp.data.local.entity.QuizAttempt;
import hcmute.com.smarteduapp.data.local.entity.QuizAttemptAnswer;
import hcmute.com.smarteduapp.data.local.entity.StudyDocument;
import hcmute.com.smarteduapp.data.local.entity.StudyDocumentImage;
import hcmute.com.smarteduapp.data.local.entity.StudyQuestion;
import hcmute.com.smarteduapp.data.local.entity.StudySummary;
import hcmute.com.smarteduapp.data.local.entity.Subject;

@Database(
        entities = {Subject.class, StudyDocument.class, StudyDocumentImage.class, StudySummary.class,
                StudyQuestion.class, QuizAttempt.class, QuizAttemptAnswer.class},
        version = 3,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {
    private static volatile AppDatabase instance;

    public abstract SubjectDao subjectDao();
    public abstract StudyDocumentDao studyDocumentDao();
    public abstract StudyDocumentImageDao studyDocumentImageDao();
    public abstract StudySummaryDao studySummaryDao();
    public abstract StudyQuestionDao studyQuestionDao();
    public abstract QuizAttemptDao quizAttemptDao();
    public abstract QuizAttemptAnswerDao quizAttemptAnswerDao();

    public static AppDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            "smartedu.db"
                    )
                    .fallbackToDestructiveMigration()
                    .build();
                }
            }
        }
        return instance;
    }
}
