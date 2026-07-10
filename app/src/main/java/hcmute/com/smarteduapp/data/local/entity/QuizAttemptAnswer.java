package hcmute.com.smarteduapp.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "quiz_attempt_answers",
        foreignKeys = @ForeignKey(
                entity = QuizAttempt.class,
                parentColumns = "id",
                childColumns = "attempt_id",
                onDelete = ForeignKey.CASCADE
        ),
        indices = {
                @Index("attempt_id"),
                @Index("document_id"),
                @Index("question_id")
        }
)
public class QuizAttemptAnswer {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public long attempt_id;
    public long document_id;
    public long question_id;
    public int questionOrder;

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
    @NonNull
    public String selectedOption;
    public String explanation;
    public boolean correct;
    public long createdAt;

    public QuizAttemptAnswer(long attempt_id, long document_id, long question_id, int questionOrder,
                             @NonNull String questionText, @NonNull String optionA,
                             @NonNull String optionB, @NonNull String optionC,
                             @NonNull String optionD, @NonNull String correctOption,
                             @NonNull String selectedOption, String explanation,
                             boolean correct, long createdAt) {
        this.attempt_id = attempt_id;
        this.document_id = document_id;
        this.question_id = question_id;
        this.questionOrder = questionOrder;
        this.questionText = questionText;
        this.optionA = optionA;
        this.optionB = optionB;
        this.optionC = optionC;
        this.optionD = optionD;
        this.correctOption = correctOption;
        this.selectedOption = selectedOption;
        this.explanation = explanation;
        this.correct = correct;
        this.createdAt = createdAt;
    }
}
