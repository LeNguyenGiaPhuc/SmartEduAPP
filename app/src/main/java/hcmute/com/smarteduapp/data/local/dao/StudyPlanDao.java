package hcmute.com.smarteduapp.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import hcmute.com.smarteduapp.data.local.entity.StudyPlan;

@Dao
public interface StudyPlanDao {
    @Query("SELECT * FROM study_plans WHERE attempt_id = :attemptId LIMIT 1")
    StudyPlan getByAttemptId(long attemptId);

    @Insert
    long insert(StudyPlan plan);
}
