package com.nexusai.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexusai.TestFiles;
import com.nexusai.ingestion.ExtractedText.PageText;
import com.nexusai.ingestion.TextChunker.TextChunk;
import java.util.List;
import org.junit.jupiter.api.Test;

class TextChunkerTest {

    private final TextChunker chunker = new TextChunker(300, 60);

    @Test
    void shortTextIsOneChunk() {
        List<TextChunk> chunks = chunker.chunk(unpaged("Just a short note."));
        assertThat(chunks).singleElement().satisfies(c -> {
            assertThat(c.index()).isZero();
            assertThat(c.pageNumber()).isNull();
            assertThat(c.content()).isEqualTo("Just a short note.");
            assertThat(c.charStart()).isZero();
            assertThat(c.charEnd()).isEqualTo(18);
        });
    }

    @Test
    void chunksRespectSizeAndPointExactlyIntoTheSourceText() {
        String text = TextExtractor.normalize(TestFiles.prose(8));
        List<TextChunk> chunks = chunker.chunk(unpaged(text));

        assertThat(chunks).hasSizeGreaterThan(5);
        for (int i = 0; i < chunks.size(); i++) {
            TextChunk c = chunks.get(i);
            assertThat(c.index()).isEqualTo(i);
            assertThat(c.content().length()).isLessThanOrEqualTo(300);
            assertThat(c.content()).isNotBlank().isEqualTo(c.content().strip());
            assertThat(text.substring(c.charStart(), c.charEnd())).isEqualTo(c.content());
        }
    }

    @Test
    void chunksCoverTheWholeTextWithOverlapAndNeverSplitWords() {
        String text = TextExtractor.normalize(TestFiles.prose(5));
        List<TextChunk> chunks = chunker.chunk(unpaged(text));

        assertThat(chunks.getFirst().charStart()).isZero();
        assertThat(chunks.getLast().charEnd()).isEqualTo(text.length());
        for (int i = 1; i < chunks.size(); i++) {
            TextChunk previous = chunks.get(i - 1);
            TextChunk current = chunks.get(i);
            assertThat(current.charStart()).as("no gap").isLessThanOrEqualTo(previous.charEnd());
            assertThat(current.charStart()).as("moves forward").isGreaterThan(previous.charStart());
            assertThat(Character.isWhitespace(text.charAt(current.charStart() - 1)))
                    .as("chunk %d starts at a word boundary", i).isTrue();
        }
    }

    @Test
    void prefersParagraphBoundaries() {
        String text = "A".repeat(200) + "\n\n" + "B".repeat(200);
        List<TextChunk> chunks = new TextChunker(300, 0).chunk(unpaged(text));
        assertThat(chunks).extracting(TextChunk::content).containsExactly("A".repeat(200), "B".repeat(200));
    }

    @Test
    void hardCutsTextWithoutAnyWhitespace() {
        String text = "x".repeat(1000);
        List<TextChunk> chunks = new TextChunker(300, 0).chunk(unpaged(text));
        assertThat(chunks).hasSize(4);
        assertThat(String.join("", chunks.stream().map(TextChunk::content).toList())).isEqualTo(text);
    }

    @Test
    void chunksNeverCrossPagesAndKeepPageNumbers() {
        ExtractedText text = new ExtractedText(List.of(
                new PageText(1, "First page text."),
                new PageText(2, ""),
                new PageText(3, "Third page text.")), 3);
        List<TextChunk> chunks = chunker.chunk(text);
        assertThat(chunks).extracting(TextChunk::pageNumber).containsExactly(1, 3);
        assertThat(chunks).extracting(TextChunk::index).containsExactly(0, 1);
        assertThat(chunks.get(1).charStart()).isZero(); // offsets are per page
    }

    @Test
    void rejectsOverlapThatCouldStall() {
        assertThatThrownBy(() -> new TextChunker(100, 50)).isInstanceOf(IllegalArgumentException.class);
    }

    private static ExtractedText unpaged(String text) {
        return new ExtractedText(List.of(new PageText(null, text)), null);
    }
}
