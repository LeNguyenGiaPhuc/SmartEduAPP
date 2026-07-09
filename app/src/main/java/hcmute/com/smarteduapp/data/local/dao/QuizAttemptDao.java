package hcmute.com.smarteduapp.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

import hcmute.com.smarteduapp.data.local.entity.QuizAttempt;

@Dao
public interface QuizAttemptDao {
    @Query("SELECT * FROM quiz_attempts WHERE document_id = :documentId ORDER BY completedAt DESC")
    List<QuizAttempt> getByDocumentId(long documentId);

    @Query("SELECT * FROM quiz_attempts ORDER BY completedAt DESC")
    List<QuizAttempt> getAll();

    @Insert
    long insert(QuizAttempt attempt);

    @Query("DELETE FROM quiz_attempts WHERE document_id = :documentId")
    int deleteByDocumentId(long documentId);
}
