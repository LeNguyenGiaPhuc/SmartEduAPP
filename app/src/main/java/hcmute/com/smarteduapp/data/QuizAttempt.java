package hcmute.com.smarteduapp.data;

import androidx.room.Entity;
import androidx.room.ForeignKey;
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

    public QuizAttempt(long document_id, float score, int correctCount, int wrongCount,
                       long completedAt) {
        this.document_id = document_id;
        this.score = score;
        this.correctCount = correctCount;
        this.wrongCount = wrongCount;
        this.completedAt = completedAt;
    }
}
