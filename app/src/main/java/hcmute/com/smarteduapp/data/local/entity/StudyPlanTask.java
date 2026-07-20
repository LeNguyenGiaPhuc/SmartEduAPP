package hcmute.com.smarteduapp.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "study_plan_tasks",
        foreignKeys = @ForeignKey(
                entity = StudyPlan.class,
                parentColumns = "id",
                childColumns = "plan_id",
                onDelete = ForeignKey.CASCADE
        ),
        indices = {@Index("plan_id")}
)
public class StudyPlanTask {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public long plan_id;
    public int taskOrder;

    @NonNull
    public String title;

    @NonNull
    public String description;

    public boolean completed;

    public StudyPlanTask(long plan_id, int taskOrder, @NonNull String title,
                         @NonNull String description, boolean completed) {
        this.plan_id = plan_id;
        this.taskOrder = taskOrder;
        this.title = title;
        this.description = description;
        this.completed = completed;
    }
}
