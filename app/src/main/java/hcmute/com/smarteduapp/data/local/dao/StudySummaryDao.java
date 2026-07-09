package hcmute.com.smarteduapp.data.local.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

import hcmute.com.smarteduapp.data.local.entity.StudySummary;

@Dao
public interface StudySummaryDao {
    @Query("SELECT * FROM summaries WHERE document_id = :documentId ORDER BY createdAt DESC")
    List<StudySummary> getByDocumentId(long documentId);

    @Query("SELECT * FROM summaries ORDER BY createdAt DESC")
    List<StudySummary> getAll();

    @Insert
    long insert(StudySummary summary);

    @Delete
    int delete(StudySummary summary);

    @Query("DELETE FROM summaries WHERE document_id = :documentId")
    int deleteByDocumentId(long documentId);
}
