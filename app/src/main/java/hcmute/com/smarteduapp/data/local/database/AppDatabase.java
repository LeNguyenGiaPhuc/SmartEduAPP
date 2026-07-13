package hcmute.com.smarteduapp.data.local.database;

import android.content.Context;

import androidx.room.Database;
import androidx.room.migration.Migration;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;

import hcmute.com.smarteduapp.data.local.dao.QuizAttemptDao;
import hcmute.com.smarteduapp.data.local.dao.QuizAttemptAnswerDao;
import hcmute.com.smarteduapp.data.local.dao.StudyDocumentDao;
import hcmute.com.smarteduapp.data.local.dao.StudyDocumentAttachmentDao;
import hcmute.com.smarteduapp.data.local.dao.StudyQuestionDao;
import hcmute.com.smarteduapp.data.local.dao.StudySummaryDao;
import hcmute.com.smarteduapp.data.local.dao.SubjectDao;
import hcmute.com.smarteduapp.data.local.entity.QuizAttempt;
import hcmute.com.smarteduapp.data.local.entity.QuizAttemptAnswer;
import hcmute.com.smarteduapp.data.local.entity.StudyDocument;
import hcmute.com.smarteduapp.data.local.entity.StudyDocumentAttachment;
import hcmute.com.smarteduapp.data.local.entity.StudyQuestion;
import hcmute.com.smarteduapp.data.local.entity.StudySummary;
import hcmute.com.smarteduapp.data.local.entity.Subject;

@Database(
        entities = {Subject.class, StudyDocument.class, StudyDocumentAttachment.class, StudySummary.class,
                StudyQuestion.class, QuizAttempt.class, QuizAttemptAnswer.class},
        version = 4,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {
    private static volatile AppDatabase instance;

    private static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE quiz_attempts ADD COLUMN focusModeEnabled INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE quiz_attempts ADD COLUMN focusExitCount INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE quiz_attempts ADD COLUMN explanationUnlocked INTEGER NOT NULL DEFAULT 1");
        }
    };

    public abstract SubjectDao subjectDao();
    public abstract StudyDocumentDao studyDocumentDao();
    public abstract StudyDocumentAttachmentDao studyDocumentAttachmentDao();
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
                    .addMigrations(MIGRATION_3_4)
                    .build();
                }
            }
        }
        return instance;
    }
}
