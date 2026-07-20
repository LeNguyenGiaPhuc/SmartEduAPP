package hcmute.com.smarteduapp.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

import hcmute.com.smarteduapp.data.local.entity.StudyPlanTask;

@Dao
public interface StudyPlanTaskDao {
    @Query("SELECT * FROM study_plan_tasks WHERE plan_id = :planId ORDER BY taskOrder ASC, id ASC")
    List<StudyPlanTask> getByPlanId(long planId);

    @Insert
    void insertAll(List<StudyPlanTask> tasks);

    @Query("UPDATE study_plan_tasks SET completed = :completed WHERE id = :taskId")
    int updateCompleted(long taskId, boolean completed);
}
