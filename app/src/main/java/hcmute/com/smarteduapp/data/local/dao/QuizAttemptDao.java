package hcmute.com.smarteduapp.data.local.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

import hcmute.com.smarteduapp.data.local.entity.QuizAttempt;

@Dao
public interface QuizAttemptDao {
    @Query("SELECT * FROM quiz_attempts WHERE document_id = :documentId ORDER BY completedAt DESC")
    List<QuizAttempt> getByDocumentId(long documentId);

    @Query("SELECT * FROM quiz_attempts WHERE document_id = :documentId ORDER BY completedAt DESC LIMIT 1")
    QuizAttempt getLatestByDocumentId(long documentId);

    @Query("SELECT * FROM quiz_attempts ORDER BY completedAt DESC")
    List<QuizAttempt> getAll();

    @Insert
    long insert(QuizAttempt attempt);

    @Delete
    int delete(QuizAttempt attempt);

    @Query("DELETE FROM quiz_attempts WHERE document_id = :documentId")
    int deleteByDocumentId(long documentId);
}
