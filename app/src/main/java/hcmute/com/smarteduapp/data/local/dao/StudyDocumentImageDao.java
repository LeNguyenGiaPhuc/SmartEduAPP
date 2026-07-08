package hcmute.com.smarteduapp.data.local.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import hcmute.com.smarteduapp.data.local.entity.StudyDocumentImage;

@Dao
public interface StudyDocumentImageDao {
    @Query("SELECT * FROM document_images WHERE document_id = :documentId ORDER BY orderIndex ASC")
    List<StudyDocumentImage> getByDocumentId(long documentId);

    @Insert
    long insert(StudyDocumentImage image);

    @Update
    int update(StudyDocumentImage image);

    @Delete
    int delete(StudyDocumentImage image);

    @Query("DELETE FROM document_images WHERE document_id = :documentId")
    void deleteByDocumentId(long documentId);
}
