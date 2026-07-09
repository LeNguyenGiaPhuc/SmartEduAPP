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

    public List<StudyQuestion> buildFallbackQuestions(String sourceText, long documentId) {
        String source = isBlank(sourceText) ? "nội dung tài liệu" : sourceText.trim();
        String preview = source.length() > 90 ? source.substring(0, 90).trim() + "..." : source;
        List<StudyQuestion> questions = new ArrayList<>();

        questions.add(new StudyQuestion(
                documentId,
                "Ý chính của tài liệu này là gì?",
                preview,
                "Một nội dung không liên quan đến tài liệu",
                "Thông tin về tài khoản người dùng",
                "Cấu hình giao diện ứng dụng",
                "A",
                "Đáp án A được lấy trực tiếp từ nội dung tài liệu.",
                1
        ));
        questions.add(new StudyQuestion(
                documentId,
                "Nguồn dữ liệu nào được dùng để tạo bộ câu hỏi?",
                "Nội dung đã quét của tài liệu",
                "Tên ứng dụng",
                "Màu nền giao diện",
                "Lịch sử hệ thống",
                "A",
                "Ứng dụng tạo câu hỏi dựa trên phần nội dung đã lưu.",
                2
        ));
        questions.add(new StudyQuestion(
                documentId,
                "Sau khi tạo câu hỏi, dữ liệu nên được lưu ở đâu?",
                "SQLite",
                "Bộ nhớ tạm của màn hình",
                "Toast message",
                "Thanh trạng thái",
                "A",
                "Proposal yêu cầu lưu câu hỏi và đáp án vào SQLite.",
                3
        ));
        questions.add(new StudyQuestion(
                documentId,
                "Người dùng cần làm gì trước khi tạo quiz?",
                "Quét hoặc lưu nội dung tài liệu",
                "Xóa môn học",
                "Đổi icon ứng dụng",
                "Tắt kết nối mạng",
                "A",
                "Quiz được tạo từ text tài liệu nên cần có nội dung trước.",
                4
        ));
        questions.add(new StudyQuestion(
                documentId,
                "Kết quả quiz dùng để làm gì?",
                "Theo dõi lịch sử học tập",
                "Tạo màu nền mới",
                "Đổi tên package",
                "Xóa database",
                "A",
                "Điểm quiz được lưu để xem lại trong lịch sử học tập.",
                5
        ));

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
