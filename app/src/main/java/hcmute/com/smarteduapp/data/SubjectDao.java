package hcmute.com.smarteduapp.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface SubjectDao {
    @Query("SELECT * FROM subjects ORDER BY name COLLATE NOCASE")
    List<Subject> getAll();

    @Query("SELECT * FROM subjects WHERE id = :id LIMIT 1")
    Subject getById(long id);

    @Insert(onConflict = OnConflictStrategy.ABORT)
    long insert(Subject subject);

    @Update
    int update(Subject subject);

    @Delete
    int delete(Subject subject);
}
