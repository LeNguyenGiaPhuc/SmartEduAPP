package hcmute.com.smarteduapp.service.document;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import hcmute.com.smarteduapp.service.ocr.MlKitOcrService;

/**
 * Scans the text content of a saved document attachment.
 * Supports images through ML Kit OCR, plain text files by reading directly,
 * DOCX by extracting document text, and PDF by rendering pages to bitmaps before OCR.
 */
public class DocumentTextScannerService {
    private final MlKitOcrService ocrService;

    public DocumentTextScannerService(MlKitOcrService ocrService) {
        this.ocrService = ocrService;
    }

    public interface ScanCallback {
        void onSuccess(String text);

        void onError(Exception exception);
    }

    public void scanAttachments(Context context, List<String> attachmentUris, ScanCallback callback) {
        List<String> safeUris = attachmentUris == null ? new ArrayList<>() : new ArrayList<>(attachmentUris);
        if (safeUris.isEmpty()) {
            callback.onError(new IllegalArgumentException("Tài liệu chưa có ảnh/file hợp lệ để quét"));
            return;
        }

        scanAttachmentQueue(context, safeUris, 0, new StringBuilder(), callback);
    }

    private void scanAttachmentQueue(
            Context context,
            List<String> attachmentUris,
            int index,
            StringBuilder resultBuilder,
            ScanCallback callback
    ) {
        if (index >= attachmentUris.size()) {
            callback.onSuccess(resultBuilder.toString().trim());
            return;
        }

        String attachmentUri = attachmentUris.get(index);
        String mimeType = getMimeType(context, attachmentUri);

        if (isImageAttachment(attachmentUri, mimeType)) {
            ocrService.recognizeText(context, Uri.parse(attachmentUri), new MlKitOcrService.OcrCallback() {
                @Override
                public void onSuccess(String recognizedText) {
                    appendPageText(resultBuilder, recognizedText);
                    scanAttachmentQueue(context, attachmentUris, index + 1, resultBuilder, callback);
                }

                @Override
                public void onError(Exception exception) {
                    scanAttachmentQueue(context, attachmentUris, index + 1, resultBuilder, callback);
                }
            });
            return;
        }

        if (isPlainText(attachmentUri, mimeType)) {
            try {
                appendPageText(resultBuilder, readTextFile(context, Uri.parse(attachmentUri)));
            } catch (Exception ignored) {
            }
            scanAttachmentQueue(context, attachmentUris, index + 1, resultBuilder, callback);
            return;
        }

        if (isDocx(attachmentUri, mimeType)) {
            try {
                appendPageText(resultBuilder, readDocxFile(context, Uri.parse(attachmentUri)));
            } catch (Exception ignored) {
            }
            scanAttachmentQueue(context, attachmentUris, index + 1, resultBuilder, callback);
            return;
        }

        if (isPdf(attachmentUri, mimeType)) {
            scanPdf(context, Uri.parse(attachmentUri), new ScanCallback() {
                @Override
                public void onSuccess(String text) {
                    appendPageText(resultBuilder, text);
                    scanAttachmentQueue(context, attachmentUris, index + 1, resultBuilder, callback);
                }

                @Override
                public void onError(Exception exception) {
                    scanAttachmentQueue(context, attachmentUris, index + 1, resultBuilder, callback);
                }
            });
            return;
        }

        scanAttachmentQueue(context, attachmentUris, index + 1, resultBuilder, callback);
    }

    private String readTextFile(Context context, Uri uri) throws Exception {
        StringBuilder builder = new StringBuilder();
        try (InputStream stream = context.getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(stream, StandardCharsets.UTF_8)
             )) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (builder.length() > 0) {
                    builder.append("\n");
                }
                builder.append(line);
            }
        }
        return builder.toString().trim();
    }


    private String readDocxFile(Context context, Uri uri) throws Exception {
        StringBuilder builder = new StringBuilder();
        try (InputStream stream = context.getContentResolver().openInputStream(uri);
             ZipInputStream zipStream = new ZipInputStream(stream)) {
            ZipEntry entry;
            while ((entry = zipStream.getNextEntry()) != null) {
                if (!"word/document.xml".equals(entry.getName())) {
                    zipStream.closeEntry();
                    continue;
                }

                XmlPullParser parser = Xml.newPullParser();
                parser.setInput(new InputStreamReader(zipStream, StandardCharsets.UTF_8));
                int eventType = parser.getEventType();
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    String tagName = parser.getName();
                    if (eventType == XmlPullParser.START_TAG && "t".equals(tagName)) {
                        builder.append(parser.nextText());
                    } else if (eventType == XmlPullParser.END_TAG && "p".equals(tagName)) {
                        if (builder.length() > 0 && builder.charAt(builder.length() - 1) != '\n') {
                            builder.append('\n');
                        }
                    }
                    eventType = parser.next();
                }
                break;
            }
        }
        return builder.toString().trim();
    }

    private void scanPdf(Context context, Uri pdfUri, ScanCallback callback) {
        ParcelFileDescriptor descriptor;
        PdfRenderer renderer;

        try {
            descriptor = context.getContentResolver().openFileDescriptor(pdfUri, "r");
            if (descriptor == null) {
                callback.onError(new IllegalArgumentException("Không mở được file PDF"));
                return;
            }
            renderer = new PdfRenderer(descriptor);
        } catch (Exception exception) {
            callback.onError(exception);
            return;
        }

        scanPdfPage(renderer, descriptor, 0, new StringBuilder(), callback);
    }

    private void scanPdfPage(
            PdfRenderer renderer,
            ParcelFileDescriptor descriptor,
            int pageIndex,
            StringBuilder resultBuilder,
            ScanCallback callback
    ) {
        if (pageIndex >= renderer.getPageCount()) {
            closePdf(renderer, descriptor);
            callback.onSuccess(resultBuilder.toString().trim());
            return;
        }

        PdfRenderer.Page page = renderer.openPage(pageIndex);
        int width = Math.max(page.getWidth() * 2, 1);
        int height = Math.max(page.getHeight() * 2, 1);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.WHITE);
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
        page.close();

        ocrService.recognizeBitmap(bitmap, new MlKitOcrService.OcrCallback() {
            @Override
            public void onSuccess(String recognizedText) {
                appendPageText(resultBuilder, recognizedText);
                bitmap.recycle();
                scanPdfPage(renderer, descriptor, pageIndex + 1, resultBuilder, callback);
            }

            @Override
            public void onError(Exception exception) {
                bitmap.recycle();
                scanPdfPage(renderer, descriptor, pageIndex + 1, resultBuilder, callback);
            }
        });
    }

    private void closePdf(PdfRenderer renderer, ParcelFileDescriptor descriptor) {
        try {
            renderer.close();
        } catch (Exception ignored) {
        }
        try {
            descriptor.close();
        } catch (Exception ignored) {
        }
    }

    private void appendPageText(StringBuilder builder, String text) {
        if (isBlank(text)) {
            return;
        }
        if (builder.length() > 0) {
            builder.append("\n\n");
        }
        builder.append(text.trim());
    }

    private String getMimeType(Context context, String uriText) {
        try {
            String mimeType = context.getContentResolver().getType(Uri.parse(uriText));
            return mimeType == null ? "" : mimeType;
        } catch (Exception exception) {
            return "";
        }
    }

    private boolean isImageAttachment(String uriText, String mimeType) {
        if (!isBlank(mimeType)) {
            return mimeType.startsWith("image/");
        }
        String lowerUri = uriText == null ? "" : uriText.toLowerCase(Locale.US);
        return lowerUri.endsWith(".jpg")
                || lowerUri.endsWith(".jpeg")
                || lowerUri.endsWith(".png")
                || lowerUri.endsWith(".webp");
    }

    private boolean isPlainText(String uriText, String mimeType) {
        if (!isBlank(mimeType)) {
            return mimeType.startsWith("text/");
        }
        return uriText != null && uriText.toLowerCase(Locale.US).endsWith(".txt");
    }

    private boolean isPdf(String uriText, String mimeType) {
        if ("application/pdf".equals(mimeType)) {
            return true;
        }
        return uriText != null && uriText.toLowerCase(Locale.US).endsWith(".pdf");
    }


    private boolean isDocx(String uriText, String mimeType) {
        if ("application/vnd.openxmlformats-officedocument.wordprocessingml.document".equals(mimeType)) {
            return true;
        }
        return uriText != null && uriText.toLowerCase(Locale.US).endsWith(".docx");
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
