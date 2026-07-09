package hcmute.com.smarteduapp.service.ai;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;

import hcmute.com.smarteduapp.BuildConfig;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class GeminiService {
    private static final String API_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";

    private static final MediaType JSON =
            MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient client = new OkHttpClient();

    public interface GeminiCallback {
        void onSuccess(String text);

        void onError(Exception exception);
    }

    public void summarize(String ocrText, GeminiCallback callback) {
        if (BuildConfig.GEMINI_API_KEY.trim().isEmpty()) {
            callback.onError(new IllegalStateException("Missing Gemini API key"));
            return;
        }

        try {
            String prompt = buildSummaryPrompt(ocrText);

            JSONObject requestJson = new JSONObject()
                    .put("contents", new JSONArray()
                            .put(new JSONObject()
                                    .put("parts", new JSONArray()
                                            .put(new JSONObject()
                                                    .put("text", prompt)))));

            String url = String.format(API_URL, BuildConfig.GEMINI_MODEL, BuildConfig.GEMINI_API_KEY);

            RequestBody body = RequestBody.create(requestJson.toString(), JSON);

            Request request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException exception) {
                    callback.onError(exception);
                }

                @Override
                public void onResponse(Call call, Response response) {
                    String responseText = "";

                    try {
                        ResponseBody responseBody = response.body();
                        if (responseBody != null) {
                            responseText = responseBody.string();
                        }

                        if (!response.isSuccessful()) {
                            throw new IOException("Gemini API error: " + response.code() + " " + responseText);
                        }

                        JSONObject json = new JSONObject(responseText);
                        String summary = json
                                .getJSONArray("candidates")
                                .getJSONObject(0)
                                .getJSONObject("content")
                                .getJSONArray("parts")
                                .getJSONObject(0)
                                .getString("text");

                        callback.onSuccess(summary.trim());
                    } catch (Exception exception) {
                        callback.onError(exception);
                    } finally {
                        response.close();
                    }
                }
            });
        } catch (Exception exception) {
            callback.onError(exception);
        }
    }

    public void generateQuiz(String ocrText, GeminiCallback callback) {
        if (BuildConfig.GEMINI_API_KEY.trim().isEmpty()) {
            callback.onError(new IllegalStateException("Missing Gemini API key"));
            return;
        }

        try {
            String prompt = buildQuizPrompt(ocrText);

            JSONObject requestJson = new JSONObject()
                    .put("contents", new JSONArray()
                            .put(new JSONObject()
                                    .put("parts", new JSONArray()
                                            .put(new JSONObject()
                                                    .put("text", prompt)))));

            String url = String.format(API_URL, BuildConfig.GEMINI_MODEL, BuildConfig.GEMINI_API_KEY);

            RequestBody body = RequestBody.create(requestJson.toString(), JSON);

            Request request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException exception) {
                    callback.onError(exception);
                }

                @Override
                public void onResponse(Call call, Response response) {
                    String responseText = "";

                    try {
                        ResponseBody responseBody = response.body();
                        if (responseBody != null) {
                            responseText = responseBody.string();
                        }

                        if (!response.isSuccessful()) {
                            throw new IOException("Gemini API error: " + response.code() + " " + responseText);
                        }

                        JSONObject json = new JSONObject(responseText);
                        String quizJson = json
                                .getJSONArray("candidates")
                                .getJSONObject(0)
                                .getJSONObject("content")
                                .getJSONArray("parts")
                                .getJSONObject(0)
                                .getString("text");

                        callback.onSuccess(quizJson.trim());
                    } catch (Exception exception) {
                        callback.onError(exception);
                    } finally {
                        response.close();
                    }
                }
            });
        } catch (Exception exception) {
            callback.onError(exception);
        }
    }

    public void explain(String ocrText, GeminiCallback callback) {
        if (BuildConfig.GEMINI_API_KEY.trim().isEmpty()) {
            callback.onError(new IllegalStateException("Missing Gemini API key"));
            return;
        }

        try {
            String prompt = buildExplainPrompt(ocrText);

            JSONObject requestJson = new JSONObject()
                    .put("contents", new JSONArray()
                            .put(new JSONObject()
                                    .put("parts", new JSONArray()
                                            .put(new JSONObject()
                                                    .put("text", prompt)))));

            String url = String.format(API_URL, BuildConfig.GEMINI_MODEL, BuildConfig.GEMINI_API_KEY);

            RequestBody body = RequestBody.create(requestJson.toString(), JSON);

            Request request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException exception) {
                    callback.onError(exception);
                }

                @Override
                public void onResponse(Call call, Response response) {
                    String responseText = "";

                    try {
                        ResponseBody responseBody = response.body();
                        if (responseBody != null) {
                            responseText = responseBody.string();
                        }

                        if (!response.isSuccessful()) {
                            throw new IOException("Gemini API error: " + response.code() + " " + responseText);
                        }

                        JSONObject json = new JSONObject(responseText);
                        String explanation = json
                                .getJSONArray("candidates")
                                .getJSONObject(0)
                                .getJSONObject("content")
                                .getJSONArray("parts")
                                .getJSONObject(0)
                                .getString("text");

                        callback.onSuccess(explanation.trim());
                    } catch (Exception exception) {
                        callback.onError(exception);
                    } finally {
                        response.close();
                    }
                }
            });
        } catch (Exception exception) {
            callback.onError(exception);
        }
    }

    public void askAboutDocument(String documentText, String question, GeminiCallback callback) {
        if (BuildConfig.GEMINI_API_KEY.trim().isEmpty()) {
            callback.onError(new IllegalStateException("Missing Gemini API key"));
            return;
        }

        try {
            String prompt = buildDocumentQuestionPrompt(documentText, question);

            JSONObject requestJson = new JSONObject()
                    .put("contents", new JSONArray()
                            .put(new JSONObject()
                                    .put("parts", new JSONArray()
                                            .put(new JSONObject()
                                                    .put("text", prompt)))));

            String url = String.format(API_URL, BuildConfig.GEMINI_MODEL, BuildConfig.GEMINI_API_KEY);

            RequestBody body = RequestBody.create(requestJson.toString(), JSON);

            Request request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException exception) {
                    callback.onError(exception);
                }

                @Override
                public void onResponse(Call call, Response response) {
                    String responseText = "";

                    try {
                        ResponseBody responseBody = response.body();
                        if (responseBody != null) {
                            responseText = responseBody.string();
                        }

                        if (!response.isSuccessful()) {
                            throw new IOException("Gemini API error: " + response.code() + " " + responseText);
                        }

                        JSONObject json = new JSONObject(responseText);
                        String answer = json
                                .getJSONArray("candidates")
                                .getJSONObject(0)
                                .getJSONObject("content")
                                .getJSONArray("parts")
                                .getJSONObject(0)
                                .getString("text");

                        callback.onSuccess(answer.trim());
                    } catch (Exception exception) {
                        callback.onError(exception);
                    } finally {
                        response.close();
                    }
                }
            });
        } catch (Exception exception) {
            callback.onError(exception);
        }
    }

    private String buildSummaryPrompt(String ocrText) {
        return "Bạn là trợ lý học tập cho học sinh/sinh viên.\n"
                + "Hãy tóm tắt nội dung OCR sau thành 3 đến 5 ý chính bằng tiếng Việt.\n"
                + "Viết rõ ràng, ngắn gọn, dạng gạch đầu dòng.\n\n"
                + "Nội dung OCR:\n"
                + ocrText;
    }

    private String buildExplainPrompt(String ocrText) {
        return "Bạn là một giáo viên tận tâm.\n"
                + "Hãy giải thích nội dung OCR sau đây một cách dễ hiểu nhất cho học sinh.\n"
                + "Sử dụng ngôn ngữ bình dị, ví dụ minh họa sinh động nếu có thể.\n"
                + "Hãy chia nội dung thành các phần nhỏ có tiêu đề rõ ràng.\n\n"
                + "Nội dung OCR:\n"
                + ocrText;
    }

    private String buildDocumentQuestionPrompt(String documentText, String question) {
        return "Bạn là chatbot học tập trong app SmartEdu AI.\n"
                + "Chỉ trả lời dựa trên nội dung tài liệu bên dưới. Nếu tài liệu không đủ thông tin, hãy nói rõ là tài liệu chưa cung cấp thông tin đó.\n"
                + "Trả lời bằng tiếng Việt, ngắn gọn, dễ hiểu, có ví dụ nếu cần.\n\n"
                + "Nội dung tài liệu:\n"
                + documentText
                + "\n\nCâu hỏi của người học:\n"
                + question;
    }

    private String buildQuizPrompt(String ocrText) {
        return "Bạn là trợ lý học tập cho học sinh/sinh viên.\n"
                + "Hãy tạo một bộ 5 câu hỏi trắc nghiệm từ nội dung OCR sau.\n"
                + "Mỗi câu phải có 4 đáp án (A, B, C, D) và chỉ có 1 đáp án đúng.\n"
                + "\n"
                + "Hãy trả về một JSON Array duy nhất, chứa các đối tượng có cấu trúc chính xác như sau:\n"
                + "[\n"
                + "  {\n"
                + "    \"questionText\": \"Câu hỏi 1...\",\n"
                + "    \"optionA\": \"Nội dung đáp án A\",\n"
                + "    \"optionB\": \"Nội dung đáp án B\",\n"
                + "    \"optionC\": \"Nội dung đáp án C\",\n"
                + "    \"optionD\": \"Nội dung đáp án D\",\n"
                + "    \"correctOption\": \"A\",\n"
                + "    \"explanation\": \"Giải thích tại sao A đúng\"\n"
                + "  }\n"
                + "]\n"
                + "\n"
                + "Chú ý: Chỉ trả về chuỗi JSON hợp lệ. Không thêm bất kỳ văn bản giải thích nào khác ngoài JSON.\n"
                + "Nội dung OCR:\n"
                + ocrText;
    }

}
