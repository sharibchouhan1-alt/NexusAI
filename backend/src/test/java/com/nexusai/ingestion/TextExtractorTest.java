package com.nexusai.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexusai.TestFiles;
import com.nexusai.ingestion.ExtractedText.PageText;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class TextExtractorTest {

    private final TextExtractor extractor = new TextExtractor(
            new IngestionProperties(1200, 200, 3, 10_000, 1, 10));

    @Test
    void extractsPdfTextPerPage() throws Exception {
        ExtractedText text = extract(TextExtractor.PDF, TestFiles.pdf("Alpha page one", "Beta page two"));
        assertThat(text.pageCount()).isEqualTo(2);
        assertThat(text.pages()).extracting(PageText::pageNumber).containsExactly(1, 2);
        assertThat(text.pages().get(0).text()).isEqualTo("Alpha page one");
        assertThat(text.pages().get(1).text()).isEqualTo("Beta page two");
    }

    @Test
    void keepsEmptyPagesSoPageNumbersStayTrue() throws Exception {
        ExtractedText text = extract(TextExtractor.PDF, TestFiles.pdf("Intro", null, "Conclusion"));
        assertThat(text.pages()).extracting(PageText::text).containsExactly("Intro", "", "Conclusion");
    }

    @Test
    void imageOnlyPdfFails() {
        assertThatThrownBy(() -> extract(TextExtractor.PDF, TestFiles.pdf((String) null)))
                .isInstanceOf(IngestionException.class)
                .hasMessageContaining("No extractable text");
    }

    @Test
    void encryptedPdfFails() {
        assertThatThrownBy(() -> extract(TextExtractor.PDF, TestFiles.encryptedPdf("secret")))
                .isInstanceOf(IngestionException.class)
                .hasMessageContaining("Password-protected");
    }

    @Test
    void corruptPdfFails() {
        byte[] corrupt = "%PDF-1.7\nthis is not really a pdf".getBytes(StandardCharsets.US_ASCII);
        assertThatThrownBy(() -> extract(TextExtractor.PDF, corrupt))
                .isInstanceOf(IngestionException.class)
                .hasMessageContaining("could not be read");
    }

    @Test
    void pdfOverPageLimitFails() {
        assertThatThrownBy(() -> extract(TextExtractor.PDF, TestFiles.pdf("1", "2", "3", "4")))
                .isInstanceOf(IngestionException.class)
                .hasMessageContaining("4 pages");
    }

    @Test
    void decodesUtf8TextAndNormalizes() throws Exception {
        byte[] bytes = "﻿Café — naïve\r\nline two   \r\n\r\n\r\n\r\nend\u0000"
                .getBytes(StandardCharsets.UTF_8);
        ExtractedText text = extract(TextExtractor.TEXT, bytes);
        assertThat(text.pageCount()).isNull();
        assertThat(text.pages()).singleElement().satisfies(p -> {
            assertThat(p.pageNumber()).isNull();
            assertThat(p.text()).isEqualTo("Café — naïve\nline two\n\nend");
        });
    }

    @Test
    void invalidUtf8Fails() {
        byte[] latin1 = {'c', 'a', 'f', (byte) 0xE9, ' ', (byte) 0xC3, '('};
        assertThatThrownBy(() -> extract(TextExtractor.TEXT, latin1))
                .isInstanceOf(IngestionException.class)
                .hasMessageContaining("not valid UTF-8");
    }

    @Test
    void whitespaceOnlyTextFails() {
        assertThatThrownBy(() -> extract(TextExtractor.TEXT, " \n\t \n".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IngestionException.class)
                .hasMessageContaining("no text");
    }

    @Test
    void textOverCharacterLimitFails() {
        byte[] big = "a ".repeat(6_000).getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> extract(TextExtractor.TEXT, big))
                .isInstanceOf(IngestionException.class)
                .hasMessageContaining("exceeds the configured limit");
    }

    private ExtractedText extract(String mediaType, byte[] bytes) throws Exception {
        return extractor.extract(mediaType, new ByteArrayInputStream(bytes));
    }
}
