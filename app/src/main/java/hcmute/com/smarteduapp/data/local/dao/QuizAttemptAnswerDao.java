package hcmute.com.smarteduapp.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

import hcmute.com.smarteduapp.data.local.entity.QuizAttemptAnswer;

@Dao
public interface QuizAttemptAnswerDao {
    @Query("SELECT * FROM quiz_attempt_answers WHERE attempt_id = :attemptId ORDER BY questionOrder ASC, id ASC")
    List<QuizAttemptAnswer> getByAttemptId(long attemptId);

    @Query("SELECT * FROM quiz_attempt_answers WHERE document_id = :documentId ORDER BY createdAt DESC")
    List<QuizAttemptAnswer> getByDocumentId(long documentId);

    @Insert
    void insertAll(List<QuizAttemptAnswer> answers);

    @Query("DELETE FROM quiz_attempt_answers WHERE attempt_id = :attemptId")
    int deleteByAttemptId(long attemptId);

    @Query("DELETE FROM quiz_attempt_answers WHERE document_id = :documentId")
    int deleteByDocumentId(long documentId);
}
