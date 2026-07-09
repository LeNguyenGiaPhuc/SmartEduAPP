package hcmute.com.smarteduapp.service.ocr;

import android.content.Context;
import android.net.Uri;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs OCR over multiple image URIs sequentially and returns one merged text block.
 */
public class BatchOcrService {
    private final MlKitOcrService ocrService;

    public BatchOcrService(MlKitOcrService ocrService) {
        this.ocrService = ocrService;
    }

    public interface BatchOcrCallback {
        void onSuccess(String text);

        void onError(Exception exception);
    }

    public void recognizeImages(Context context, List<String> imageUris, BatchOcrCallback callback) {
        List<String> safeUris = imageUris == null ? new ArrayList<>() : new ArrayList<>(imageUris);
        if (safeUris.isEmpty()) {
            callback.onError(new IllegalArgumentException("No image URIs to OCR"));
            return;
        }

        processNext(context, safeUris, 0, new StringBuilder(), callback);
    }

    private void processNext(
            Context context,
            List<String> imageUris,
            int index,
            StringBuilder resultBuilder,
            BatchOcrCallback callback
    ) {
        if (index >= imageUris.size()) {
            callback.onSuccess(resultBuilder.toString().trim());
            return;
        }

        ocrService.recognizeText(
                context,
                Uri.parse(imageUris.get(index)),
                new MlKitOcrService.OcrCallback() {
                    @Override
                    public void onSuccess(String recognizedText) {
                        appendPageText(resultBuilder, recognizedText);
                        processNext(context, imageUris, index + 1, resultBuilder, callback);
                    }

                    @Override
                    public void onError(Exception exception) {
                        processNext(context, imageUris, index + 1, resultBuilder, callback);
                    }
                }
        );
    }

    private void appendPageText(StringBuilder builder, String text) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        if (builder.length() > 0) {
            builder.append("\n\n");
        }
        builder.append(text.trim());
    }
}
