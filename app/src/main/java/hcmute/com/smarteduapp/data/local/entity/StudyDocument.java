package hcmute.com.smarteduapp.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import hcmute.com.smarteduapp.data.local.entity.Subject;

@Entity(
        tableName = "documents",
        foreignKeys = @ForeignKey(
                entity = Subject.class,
                parentColumns = "id",
                childColumns = "subject_id",
                onDelete = ForeignKey.CASCADE
        ),
        indices = {@Index("subject_id")}
)
public class StudyDocument {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public long subject_id;

    @NonNull
    public String title;

    public String imageUri;
    public String ocrText;
    public long createdAt;
    public long updatedAt;

    public StudyDocument(long subject_id, @NonNull String title, String imageUri,
                         String ocrText, long createdAt, long updatedAt) {
        this.subject_id = subject_id;
        this.title = title;
        this.imageUri = imageUri;
        this.ocrText = ocrText;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
