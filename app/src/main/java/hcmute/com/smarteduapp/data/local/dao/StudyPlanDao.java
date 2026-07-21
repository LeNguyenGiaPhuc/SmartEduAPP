package hcmute.com.smarteduapp.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

import hcmute.com.smarteduapp.data.local.entity.StudyPlan;
import hcmute.com.smarteduapp.data.local.model.StudyPlanListItem;

@Dao
public interface StudyPlanDao {
    @Query("SELECT * FROM study_plans WHERE id = :planId LIMIT 1")
    StudyPlan getById(long planId);

    @Query("SELECT * FROM study_plans WHERE attempt_id = :attemptId LIMIT 1")
    StudyPlan getByAttemptId(long attemptId);

    @Insert
    long insert(StudyPlan plan);

    @Query("SELECT p.id AS planId, p.document_id AS documentId, p.attempt_id AS attemptId, " +
            "s.id AS subjectId, s.name AS subjectName, d.title AS documentTitle, " +
            "a.score AS score, a.correctCount AS correctCount, a.wrongCount AS wrongCount, " +
            "a.completedAt AS completedAt, p.createdAt AS planCreatedAt " +
            "FROM study_plans p " +
            "INNER JOIN documents d ON p.document_id = d.id " +
            "INNER JOIN subjects s ON d.subject_id = s.id " +
            "INNER JOIN quiz_attempts a ON p.attempt_id = a.id " +
            "ORDER BY p.createdAt DESC")
    List<StudyPlanListItem> getAllWithMetadata();

    @Query("DELETE FROM study_plans WHERE id = :planId")
    int deleteById(long planId);
}
