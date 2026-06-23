package hcmute.com.smarteduapp.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface StudyDocumentDao {
    @Query("SELECT * FROM documents WHERE subject_id = :subjectId ORDER BY updatedAt DESC")
    List<StudyDocument> getBySubjectId(long subjectId);

    @Query("SELECT * FROM documents WHERE id = :id LIMIT 1")
    StudyDocument getById(long id);

    @Insert
    long insert(StudyDocument document);

    @Update
    int update(StudyDocument document);

    @Delete
    int delete(StudyDocument document);
}
