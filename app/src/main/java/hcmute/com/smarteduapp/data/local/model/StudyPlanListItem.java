package hcmute.com.smarteduapp.data.local.model;

/** A small joined view used by the study-plan list screen. */
public class StudyPlanListItem {
    public long planId;
    public long documentId;
    public long attemptId;
    public long subjectId;
    public String subjectName;
    public String documentTitle;
    public float score;
    public int correctCount;
    public int wrongCount;
    public long completedAt;
    public long planCreatedAt;
}
