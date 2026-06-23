package hcmute.com.smarteduapp.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface StudySummaryDao {
    @Query("SELECT * FROM summaries WHERE document_id = :documentId ORDER BY createdAt DESC")
    List<StudySummary> getByDocumentId(long documentId);

    @Insert
    long insert(StudySummary summary);

    @Delete
    int delete(StudySummary summary);
}
