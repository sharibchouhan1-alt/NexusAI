package com.nexusai.ingestion;

import com.nexusai.ingestion.ExtractedText.PageText;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Splits extracted text into retrievable chunks with provenance.
 *
 * <p>Strategy (deliberately simple; retrieval quality will be measured later):
 * <ul>
 *   <li>Chunks never cross a page boundary, so every chunk has an exact page number.</li>
 *   <li>Each chunk is at most {@code chunkSize} characters, ending at the best natural
 *       boundary in its second half: paragraph, then line, then sentence, then word.</li>
 *   <li>Consecutive chunks overlap by about {@code chunkOverlap} characters, starting at a
 *       word boundary, so a sentence cut at a chunk edge is still retrievable whole.</li>
 *   <li>{@code charStart}/{@code charEnd} index into the page's extracted text, and
 *       {@code content} is exactly that substring, so citations can be located precisely.</li>
 * </ul>
 */
@Component
class TextChunker {

    record TextChunk(int index, Integer pageNumber, int charStart, int charEnd, String content) {}

    private final int chunkSize;
    private final int overlap;

    @Autowired
    TextChunker(IngestionProperties properties) {
        this(properties.chunkSize(), properties.chunkOverlap());
    }

    TextChunker(int chunkSize, int overlap) {
        if (chunkSize < 1 || overlap < 0 || overlap * 2 >= chunkSize) {
            throw new IllegalArgumentException("Require chunkSize > 0 and 0 <= overlap < chunkSize / 2");
        }
        this.chunkSize = chunkSize;
        this.overlap = overlap;
    }

    List<TextChunk> chunk(ExtractedText text) {
        List<TextChunk> chunks = new ArrayList<>();
        for (PageText page : text.pages()) {
            chunkPage(page, chunks);
        }
        return chunks;
    }

    private void chunkPage(PageText page, List<TextChunk> out) {
        String text = page.text();
        int length = text.length();
        int start = skipWhitespace(text, 0, length);

        while (start < length) {
            int limit = Math.min(start + chunkSize, length);
            int end = limit == length ? length : breakPoint(text, start, limit);

            int trimmedEnd = end;
            while (trimmedEnd > start && Character.isWhitespace(text.charAt(trimmedEnd - 1))) {
                trimmedEnd--;
            }
            if (trimmedEnd > start) {
                out.add(new TextChunk(out.size(), page.pageNumber(), start, trimmedEnd,
                        text.substring(start, trimmedEnd)));
            }
            if (end >= length) {
                break;
            }

            // Next chunk starts ~overlap characters back, aligned to the start of a word.
            int next = Math.max(end - overlap, start + 1);
            if (next < end && next > 0 && !Character.isWhitespace(text.charAt(next - 1))) {
                int space = indexOfWhitespace(text, next, end);
                next = space < 0 ? end : space;
            }
            start = skipWhitespace(text, next, length);
        }
    }

    /** Best split position in (start + chunkSize/2, limit]; falls back to a hard cut. */
    private int breakPoint(String text, int start, int limit) {
        int floor = start + chunkSize / 2;
        String window = text.substring(floor, limit);
        int paragraph = window.lastIndexOf("\n\n");
        if (paragraph >= 0) return floor + paragraph + 2;
        int line = window.lastIndexOf('\n');
        if (line >= 0) return floor + line + 1;
        int sentence = lastSentenceEnd(window);
        if (sentence >= 0) return floor + sentence;
        for (int i = limit; i > floor; i--) {
            if (Character.isWhitespace(text.charAt(i - 1))) return i;
        }
        return limit;
    }

    /** Index just after the last ". ", "! " or "? " in the window, or -1. */
    private static int lastSentenceEnd(String window) {
        for (int i = window.length() - 1; i > 0; i--) {
            char c = window.charAt(i - 1);
            if ((c == '.' || c == '!' || c == '?') && Character.isWhitespace(window.charAt(i))) {
                return i + 1;
            }
        }
        return -1;
    }

    private static int skipWhitespace(String text, int from, int length) {
        int i = from;
        while (i < length && Character.isWhitespace(text.charAt(i))) i++;
        return i;
    }

    private static int indexOfWhitespace(String text, int from, int to) {
        for (int i = from; i < to; i++) {
            if (Character.isWhitespace(text.charAt(i))) return i;
        }
        return -1;
    }
}
