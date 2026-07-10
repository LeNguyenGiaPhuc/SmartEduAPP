package hcmute.com.smarteduapp.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

import hcmute.com.smarteduapp.data.local.entity.StudyQuestion;

@Dao
public interface StudyQuestionDao {
    @Query("SELECT * FROM questions WHERE document_id = :documentId ORDER BY questionOrder, id")
    List<StudyQuestion> getByDocumentId(long documentId);

    @Insert
    long insert(StudyQuestion question);

    @Query("DELETE FROM questions WHERE document_id = :documentId")
    int deleteByDocumentId(long documentId);
}
