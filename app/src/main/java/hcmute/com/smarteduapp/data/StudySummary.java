package hcmute.com.smarteduapp.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "summaries",
        foreignKeys = @ForeignKey(
                entity = StudyDocument.class,
                parentColumns = "id",
                childColumns = "document_id",
                onDelete = ForeignKey.CASCADE
        ),
        indices = {@Index("document_id")}
)
public class StudySummary {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public long document_id;

    @NonNull
    public String content;

    public long createdAt;

    public StudySummary(long document_id, @NonNull String content, long createdAt) {
        this.document_id = document_id;
        this.content = content;
        this.createdAt = createdAt;
    }
}
