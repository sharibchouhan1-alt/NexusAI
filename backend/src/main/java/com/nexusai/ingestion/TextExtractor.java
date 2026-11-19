package com.nexusai.ingestion;

import com.nexusai.ingestion.ExtractedText.PageText;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

/**
 * Extracts normalized text from PDF (per page, via PDFBox) and UTF-8 plain text.
 * Untrusted input: page and character limits are enforced, PDFBox buffers through temp
 * files rather than heap, and failures surface as user-safe {@link IngestionException}s.
 */
@Component
class TextExtractor {

    static final String PDF = "application/pdf";
    static final String TEXT = "text/plain";

    // C0 controls except tab and newline (NUL in particular cannot be stored in PostgreSQL text).
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\x00-\\x08\\x0B-\\x1F\\x7F]");
    private static final Pattern TRAILING_SPACES = Pattern.compile("[ \\t]+\\n");
    private static final Pattern EXCESS_BLANK_LINES = Pattern.compile("\\n{3,}");

    private final IngestionProperties properties;

    TextExtractor(IngestionProperties properties) {
        this.properties = properties;
    }

    ExtractedText extract(String mediaType, InputStream content) throws IngestionException, IOException {
        ExtractedText text = switch (mediaType) {
            case PDF -> extractPdf(content);
            case TEXT -> extractText(content);
            default -> throw new IngestionException("Unsupported file type: " + mediaType);
        };
        if (text.isBlank()) {
            throw new IngestionException(PDF.equals(mediaType)
                    ? "No extractable text was found. Scanned (image-only) PDFs are not supported yet."
                    : "The file contains no text.");
        }
        long total = text.pages().stream().mapToLong(p -> p.text().length()).sum();
        if (total > properties.maxTextChars()) {
            throw new IngestionException("The document contains more than " + properties.maxTextChars()
                    + " characters of text, which exceeds the configured limit.");
        }
        return text;
    }

    private ExtractedText extractPdf(InputStream content) throws IngestionException, IOException {
        try (RandomAccessReadBuffer source = new RandomAccessReadBuffer(content);
             PDDocument pdf = Loader.loadPDF(source, "", null, null, IOUtils.createTempFileOnlyStreamCache())) {
            int pageCount = pdf.getNumberOfPages();
            if (pageCount > properties.maxPages()) {
                throw new IngestionException("The PDF has " + pageCount + " pages; the limit is "
                        + properties.maxPages() + ".");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setLineSeparator("\n");
            List<PageText> pages = new ArrayList<>(pageCount);
            long chars = 0;
            for (int page = 1; page <= pageCount; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = normalize(stripper.getText(pdf));
                chars += text.length();
                if (chars > properties.maxTextChars()) {
                    throw new IngestionException("The document contains more than " + properties.maxTextChars()
                            + " characters of text, which exceeds the configured limit.");
                }
                pages.add(new PageText(page, text));
            }
            return new ExtractedText(pages, pageCount);
        } catch (InvalidPasswordException e) {
            throw new IngestionException("Password-protected PDFs are not supported.", e);
        } catch (IngestionException e) {
            throw e;
        } catch (IOException e) {
            throw new IngestionException("The PDF could not be read. It may be damaged or not a valid PDF.", e);
        }
    }

    private ExtractedText extractText(InputStream content) throws IngestionException, IOException {
        byte[] bytes = content.readAllBytes();
        String decoded;
        try {
            decoded = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new IngestionException("The text file is not valid UTF-8.", e);
        }
        if (decoded.startsWith("﻿")) {
            decoded = decoded.substring(1); // byte-order mark
        }
        return new ExtractedText(List.of(new PageText(null, normalize(decoded))), null);
    }

    /** Unifies line endings, drops control characters and trailing spaces, caps blank runs. */
    static String normalize(String raw) {
        String text = raw.replace("\r\n", "\n").replace('\r', '\n');
        text = CONTROL_CHARS.matcher(text).replaceAll("");
        text = TRAILING_SPACES.matcher(text).replaceAll("\n");
        text = EXCESS_BLANK_LINES.matcher(text).replaceAll("\n\n");
        return text.strip();
    }
}
