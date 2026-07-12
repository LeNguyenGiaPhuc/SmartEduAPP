package hcmute.com.smarteduapp.data.local.entity;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "quiz_attempts",
        foreignKeys = @ForeignKey(
                entity = StudyDocument.class,
                parentColumns = "id",
                childColumns = "document_id",
                onDelete = ForeignKey.CASCADE
        ),
        indices = {@Index("document_id")}
)
public class QuizAttempt {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public long document_id;
    public float score;
    public int correctCount;
    public int wrongCount;
    public long completedAt;
    public boolean focusModeEnabled;
    public int focusExitCount;
    public boolean explanationUnlocked;

    @Ignore
    public QuizAttempt(long document_id, float score, int correctCount, int wrongCount,
                       long completedAt) {
        this.document_id = document_id;
        this.score = score;
        this.correctCount = correctCount;
        this.wrongCount = wrongCount;
        this.completedAt = completedAt;
        this.focusModeEnabled = false;
        this.focusExitCount = 0;
        this.explanationUnlocked = true;
    }

    public QuizAttempt(long document_id, float score, int correctCount, int wrongCount,
                       long completedAt, boolean focusModeEnabled, int focusExitCount,
                       boolean explanationUnlocked) {
        this.document_id = document_id;
        this.score = score;
        this.correctCount = correctCount;
        this.wrongCount = wrongCount;
        this.completedAt = completedAt;
        this.focusModeEnabled = focusModeEnabled;
        this.focusExitCount = focusExitCount;
        this.explanationUnlocked = explanationUnlocked;
    }
}
