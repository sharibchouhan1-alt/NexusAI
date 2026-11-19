package com.nexusai.document;

import com.nexusai.platform.web.InvalidRequestException;
import com.nexusai.platform.web.UnsupportedFileTypeException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Decides what an uploaded file is from its name <em>and</em> its bytes. The client's
 * declared Content-Type is never trusted. Accepted: PDF ({@code %PDF-} header) and plain
 * text (no NUL bytes in the sniffed prefix; full UTF-8 validation happens at extraction).
 */
final class UploadInspector {

    static final String PDF = "application/pdf";
    static final String TEXT = "text/plain";

    private static final int SNIFF_BYTES = 8192;
    private static final int PDF_HEADER_WINDOW = 1024; // the PDF spec allows leading junk
    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final int MAX_FILENAME_LENGTH = 255;

    private UploadInspector() {
    }

    record Inspection(String mediaType, String safeFilename) {}

    static Inspection inspect(String originalFilename, long size, InputStream content) throws IOException {
        if (size <= 0) {
            throw new InvalidRequestException("The uploaded file is empty");
        }
        String filename = sanitizeFilename(originalFilename);
        String extension = extension(filename);
        byte[] head = content.readNBytes(SNIFF_BYTES);

        return switch (extension) {
            case "pdf" -> {
                if (!startsWithPdfMagic(head)) {
                    throw new UnsupportedFileTypeException("The file is named .pdf but is not a PDF document");
                }
                yield new Inspection(PDF, filename);
            }
            case "txt" -> {
                if (containsNul(head)) {
                    throw new UnsupportedFileTypeException("The file is named .txt but is not UTF-8 text");
                }
                yield new Inspection(TEXT, filename);
            }
            default -> throw new UnsupportedFileTypeException("Only PDF (.pdf) and plain text (.txt) files are supported");
        };
    }

    /**
     * Keeps only the last path segment, removes control characters and caps the length.
     * The result is display metadata only; it is never used to build a storage path.
     */
    static String sanitizeFilename(String original) {
        if (original == null) {
            return "upload";
        }
        String name = original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        name = name.replaceAll("\\p{Cntrl}", "").strip();
        if (name.isEmpty() || name.equals(".") || name.equals("..")) {
            return "upload";
        }
        if (name.length() > MAX_FILENAME_LENGTH) {
            String ext = extension(name);
            int keep = MAX_FILENAME_LENGTH - (ext.isEmpty() ? 0 : ext.length() + 1);
            name = name.substring(0, keep) + (ext.isEmpty() ? "" : "." + ext);
        }
        return name;
    }

    /** Default document title: the file name without its extension. */
    static String titleFrom(String safeFilename) {
        int dot = safeFilename.lastIndexOf('.');
        String base = dot > 0 ? safeFilename.substring(0, dot) : safeFilename;
        return base.isBlank() ? safeFilename : base.strip();
    }

    private static String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static boolean startsWithPdfMagic(byte[] head) {
        int limit = Math.min(head.length, PDF_HEADER_WINDOW) - PDF_MAGIC.length;
        outer:
        for (int i = 0; i <= limit; i++) {
            for (int j = 0; j < PDF_MAGIC.length; j++) {
                if (head[i + j] != PDF_MAGIC[j]) continue outer;
            }
            return true;
        }
        return false;
    }

    private static boolean containsNul(byte[] head) {
        for (byte b : head) {
            if (b == 0) return true;
        }
        return false;
    }
}
