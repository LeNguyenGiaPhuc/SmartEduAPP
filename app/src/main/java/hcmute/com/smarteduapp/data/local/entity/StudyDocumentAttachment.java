package hcmute.com.smarteduapp.data.local.entity;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.ColumnInfo;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "document_images",
        foreignKeys = @ForeignKey(
                entity = StudyDocument.class,
                parentColumns = "id",
                childColumns = "document_id",
                onDelete = ForeignKey.CASCADE
        ),
        indices = {@Index("document_id")}
)
public class StudyDocumentAttachment {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public long document_id;
    @ColumnInfo(name = "imageUri")
    public String attachmentUri;
    public String ocrText;
    public int orderIndex;
    public long createdAt;

    public StudyDocumentAttachment(long document_id, String attachmentUri, String ocrText, int orderIndex, long createdAt) {
        this.document_id = document_id;
        this.attachmentUri = attachmentUri;
        this.ocrText = ocrText;
        this.orderIndex = orderIndex;
        this.createdAt = createdAt;
    }
}
