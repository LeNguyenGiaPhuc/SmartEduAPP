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
import hcmute.com.smarteduapp.data.local.dao.StudyPlanDao;
import hcmute.com.smarteduapp.data.local.dao.StudyPlanTaskDao;
import hcmute.com.smarteduapp.data.local.dao.SubjectDao;
import hcmute.com.smarteduapp.data.local.entity.QuizAttempt;
import hcmute.com.smarteduapp.data.local.entity.QuizAttemptAnswer;
import hcmute.com.smarteduapp.data.local.entity.StudyDocument;
import hcmute.com.smarteduapp.data.local.entity.StudyDocumentAttachment;
import hcmute.com.smarteduapp.data.local.entity.StudyQuestion;
import hcmute.com.smarteduapp.data.local.entity.StudySummary;
import hcmute.com.smarteduapp.data.local.entity.Subject;
import hcmute.com.smarteduapp.data.local.entity.StudyPlan;
import hcmute.com.smarteduapp.data.local.entity.StudyPlanTask;

@Database(
        entities = {Subject.class, StudyDocument.class, StudyDocumentAttachment.class, StudySummary.class,
                StudyQuestion.class, QuizAttempt.class, QuizAttemptAnswer.class,
                StudyPlan.class, StudyPlanTask.class},
        version = 7,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {
    private static volatile AppDatabase instance;

    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS document_images (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "document_id INTEGER NOT NULL, " +
                    "imageUri TEXT, " +
                    "ocrText TEXT, " +
                    "orderIndex INTEGER NOT NULL, " +
                    "createdAt INTEGER NOT NULL, " +
                    "FOREIGN KEY(document_id) REFERENCES documents(id) ON UPDATE NO ACTION ON DELETE CASCADE" +
                    ")");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_document_images_document_id " +
                    "ON document_images(document_id)");
        }
    };

    private static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS quiz_attempt_answers (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "attempt_id INTEGER NOT NULL, " +
                    "document_id INTEGER NOT NULL, " +
                    "question_id INTEGER NOT NULL, " +
                    "questionOrder INTEGER NOT NULL, " +
                    "questionText TEXT NOT NULL, " +
                    "optionA TEXT NOT NULL, " +
                    "optionB TEXT NOT NULL, " +
                    "optionC TEXT NOT NULL, " +
                    "optionD TEXT NOT NULL, " +
                    "correctOption TEXT NOT NULL, " +
                    "selectedOption TEXT NOT NULL, " +
                    "explanation TEXT, " +
                    "correct INTEGER NOT NULL, " +
                    "createdAt INTEGER NOT NULL, " +
                    "FOREIGN KEY(attempt_id) REFERENCES quiz_attempts(id) ON UPDATE NO ACTION ON DELETE CASCADE" +
                    ")");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_quiz_attempt_answers_attempt_id " +
                    "ON quiz_attempt_answers(attempt_id)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_quiz_attempt_answers_document_id " +
                    "ON quiz_attempt_answers(document_id)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_quiz_attempt_answers_question_id " +
                    "ON quiz_attempt_answers(question_id)");
        }
    };

    private static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE quiz_attempts ADD COLUMN focusModeEnabled INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE quiz_attempts ADD COLUMN focusExitCount INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE quiz_attempts ADD COLUMN explanationUnlocked INTEGER NOT NULL DEFAULT 1");
        }
    };

    private static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // Attachment OCR was never used: OCR is stored once on documents. Keep
            // all attachment rows while removing that unused column.
            database.execSQL("CREATE TABLE document_images_new (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "document_id INTEGER NOT NULL, " +
                    "imageUri TEXT, " +
                    "orderIndex INTEGER NOT NULL, " +
                    "createdAt INTEGER NOT NULL, " +
                    "FOREIGN KEY(document_id) REFERENCES documents(id) ON UPDATE NO ACTION ON DELETE CASCADE" +
                    ")");
            database.execSQL("INSERT INTO document_images_new " +
                    "(id, document_id, imageUri, orderIndex, createdAt) " +
                    "SELECT id, document_id, imageUri, orderIndex, createdAt FROM document_images");
            database.execSQL("DROP TABLE document_images");
            database.execSQL("ALTER TABLE document_images_new RENAME TO document_images");
            database.execSQL("CREATE INDEX index_document_images_document_id " +
                    "ON document_images(document_id)");
        }
    };

    private static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE quiz_attempts ADD COLUMN totalTimeSeconds INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE quiz_attempt_answers ADD COLUMN timeSpentSeconds INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE quiz_attempt_answers ADD COLUMN answerChangeCount INTEGER NOT NULL DEFAULT 0");
        }
    };

    private static final Migration MIGRATION_6_7 = new Migration(6, 7) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS study_plans (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "document_id INTEGER NOT NULL, " +
                    "attempt_id INTEGER NOT NULL, " +
                    "title TEXT NOT NULL, " +
                    "overview TEXT NOT NULL, " +
                    "createdAt INTEGER NOT NULL, " +
                    "FOREIGN KEY(document_id) REFERENCES documents(id) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                    "FOREIGN KEY(attempt_id) REFERENCES quiz_attempts(id) ON UPDATE NO ACTION ON DELETE CASCADE" +
                    ")");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_study_plans_document_id ON study_plans(document_id)");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_study_plans_attempt_id ON study_plans(attempt_id)");
            database.execSQL("CREATE TABLE IF NOT EXISTS study_plan_tasks (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "plan_id INTEGER NOT NULL, " +
                    "taskOrder INTEGER NOT NULL, " +
                    "title TEXT NOT NULL, " +
                    "description TEXT NOT NULL, " +
                    "completed INTEGER NOT NULL, " +
                    "FOREIGN KEY(plan_id) REFERENCES study_plans(id) ON UPDATE NO ACTION ON DELETE CASCADE" +
                    ")");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_study_plan_tasks_plan_id ON study_plan_tasks(plan_id)");
        }
    };

    public abstract SubjectDao subjectDao();
    public abstract StudyDocumentDao studyDocumentDao();
    public abstract StudyDocumentAttachmentDao studyDocumentAttachmentDao();
    public abstract StudySummaryDao studySummaryDao();
    public abstract StudyQuestionDao studyQuestionDao();
    public abstract QuizAttemptDao quizAttemptDao();
    public abstract QuizAttemptAnswerDao quizAttemptAnswerDao();
    public abstract StudyPlanDao studyPlanDao();
    public abstract StudyPlanTaskDao studyPlanTaskDao();

    public static AppDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            "smartedu.db"
                    )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                    .build();
                }
            }
        }
        return instance;
    }
}
