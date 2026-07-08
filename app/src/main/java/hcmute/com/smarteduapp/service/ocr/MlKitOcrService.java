package hcmute.com.smarteduapp.service.ocr;

import android.content.Context;
import android.net.Uri;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.IOException;

/**
 * Runs Google ML Kit Text Recognition on saved document images.
 */
public class MlKitOcrService {
    private final TextRecognizer recognizer =
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

    public interface OcrCallback {
        void onSuccess(String recognizedText);

        void onError(Exception exception);
    }

    public void recognizeText(Context context, Uri imageUri, OcrCallback callback) {
        try {
            InputImage image = InputImage.fromFilePath(context, imageUri);
            recognizer.process(image)
                    .addOnSuccessListener(text -> callback.onSuccess(extractText(text)))
                    .addOnFailureListener(callback::onError);
        } catch (IOException exception) {
            callback.onError(exception);
        }
    }

    private String extractText(Text text) {
        String rawText = text == null ? "" : text.getText();
        return rawText == null ? "" : rawText.trim();
    }
}
