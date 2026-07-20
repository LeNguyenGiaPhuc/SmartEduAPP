package hcmute.com.smarteduapp.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "study_plans",
        foreignKeys = {
                @ForeignKey(
                        entity = StudyDocument.class,
                        parentColumns = "id",
                        childColumns = "document_id",
                        onDelete = ForeignKey.CASCADE
                ),
                @ForeignKey(
                        entity = QuizAttempt.class,
                        parentColumns = "id",
                        childColumns = "attempt_id",
                        onDelete = ForeignKey.CASCADE
                )
        },
        indices = {
                @Index("document_id"),
                @Index(value = "attempt_id", unique = true)
        }
)
public class StudyPlan {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public long document_id;
    public long attempt_id;

    @NonNull
    public String title;

    @NonNull
    public String overview;

    public long createdAt;

    public StudyPlan(long document_id, long attempt_id, @NonNull String title,
                     @NonNull String overview, long createdAt) {
        this.document_id = document_id;
        this.attempt_id = attempt_id;
        this.title = title;
        this.overview = overview;
        this.createdAt = createdAt;
    }
}
