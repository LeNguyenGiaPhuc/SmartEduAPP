package hcmute.com.smarteduapp.service.study;

import java.util.List;
import java.util.Map;

import hcmute.com.smarteduapp.data.local.entity.QuizAttempt;
import hcmute.com.smarteduapp.data.local.entity.StudyQuestion;

/**
 * Scores selected quiz answers against saved question records.
 */
public class QuizScoringService {
    public QuizAttempt score(long documentId, List<StudyQuestion> questions, Map<Long, String> selectedAnswers) {
        int correct = 0;
        for (StudyQuestion question : questions) {
            String selectedAnswer = selectedAnswers.get(question.id);
            if (question.correctOption.equalsIgnoreCase(selectedAnswer)) {
                correct++;
            }
        }

        int total = questions.size();
        int wrong = total - correct;
        float score = total == 0 ? 0f : (correct * 10f) / total;
        return new QuizAttempt(documentId, score, correct, wrong, System.currentTimeMillis());
    }
}
