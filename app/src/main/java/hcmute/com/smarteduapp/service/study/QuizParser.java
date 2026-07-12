package hcmute.com.smarteduapp.service.study;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import hcmute.com.smarteduapp.data.local.entity.StudyQuestion;

/**
 * Converts Gemini quiz JSON into local StudyQuestion records.
 */
public class QuizParser {
    public List<StudyQuestion> parse(String quizJson, long documentId) throws Exception {
        String cleanJson = extractJsonPayload(quizJson);
        JSONArray array;
        if (cleanJson.startsWith("{")) {
            JSONObject object = new JSONObject(cleanJson);
            array = object.optJSONArray("questions");
            if (array == null) {
                array = object.optJSONArray("data");
            }
            if (array == null) {
                throw new IllegalArgumentException("Quiz JSON object does not contain questions array");
            }
        } else {
            array = new JSONArray(cleanJson);
        }

        List<StudyQuestion> questions = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.getJSONObject(i);
            questions.add(new StudyQuestion(
                    documentId,
                    item.optString("questionText", "Câu hỏi " + (i + 1)),
                    item.optString("optionA", "Đáp án A"),
                    item.optString("optionB", "Đáp án B"),
                    item.optString("optionC", "Đáp án C"),
                    item.optString("optionD", "Đáp án D"),
                    normalizeCorrectOption(item.optString("correctOption", "A")),
                    item.optString("explanation", ""),
                    i + 1
            ));
        }
        return questions;
    }

    private String extractJsonPayload(String rawText) {
        String cleanText = rawText == null ? "" : rawText.trim();
        if (cleanText.startsWith("```")) {
            int firstNewline = cleanText.indexOf('\n');
            if (firstNewline != -1) {
                cleanText = cleanText.substring(firstNewline + 1).trim();
            }
            if (cleanText.endsWith("```")) {
                cleanText = cleanText.substring(0, cleanText.length() - 3).trim();
            }
        }

        int firstArray = cleanText.indexOf('[');
        int lastArray = cleanText.lastIndexOf(']');
        if (firstArray >= 0 && lastArray > firstArray) {
            return cleanText.substring(firstArray, lastArray + 1);
        }

        int firstObject = cleanText.indexOf('{');
        int lastObject = cleanText.lastIndexOf('}');
        if (firstObject >= 0 && lastObject > firstObject) {
            return cleanText.substring(firstObject, lastObject + 1);
        }

        return cleanText;
    }

    private String normalizeCorrectOption(String option) {
        if (isBlank(option)) {
            return "A";
        }
        String normalized = option.trim().toUpperCase(Locale.US);
        if (normalized.startsWith("A")) return "A";
        if (normalized.startsWith("B")) return "B";
        if (normalized.startsWith("C")) return "C";
        if (normalized.startsWith("D")) return "D";
        return "A";
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
