package hcmute.com.smarteduapp.data.local.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import hcmute.com.smarteduapp.data.local.entity.StudyDocumentAttachment;

@Dao
public interface StudyDocumentAttachmentDao {
    @Query("SELECT * FROM document_images WHERE document_id = :documentId ORDER BY orderIndex ASC")
    List<StudyDocumentAttachment> getByDocumentId(long documentId);

    @Insert
    long insert(StudyDocumentAttachment attachment);

    @Update
    int update(StudyDocumentAttachment attachment);

    @Delete
    int delete(StudyDocumentAttachment attachment);

    @Query("DELETE FROM document_images WHERE document_id = :documentId")
    void deleteByDocumentId(long documentId);
}
