package hcmute.com.smarteduapp.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "questions",
        foreignKeys = @ForeignKey(
                entity = StudyDocument.class,
                parentColumns = "id",
                childColumns = "document_id",
                onDelete = ForeignKey.CASCADE
        ),
        indices = {@Index("document_id")}
)
public class StudyQuestion {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public long document_id;

    @NonNull
    public String questionText;
    @NonNull
    public String optionA;
    @NonNull
    public String optionB;
    @NonNull
    public String optionC;
    @NonNull
    public String optionD;
    @NonNull
    public String correctOption;
    public String explanation;
    public int questionOrder;

    public StudyQuestion(long document_id, @NonNull String questionText, @NonNull String optionA,
                         @NonNull String optionB, @NonNull String optionC, @NonNull String optionD,
                         @NonNull String correctOption, String explanation, int questionOrder) {
        this.document_id = document_id;
        this.questionText = questionText;
        this.optionA = optionA;
        this.optionB = optionB;
        this.optionC = optionC;
        this.optionD = optionD;
        this.correctOption = correctOption;
        this.explanation = explanation;
        this.questionOrder = questionOrder;
    }
}
