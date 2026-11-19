package com.nexusai.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexusai.TestFiles;
import com.nexusai.platform.web.InvalidRequestException;
import com.nexusai.platform.web.UnsupportedFileTypeException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class UploadInspectorTest {

    @Test
    void acceptsRealPdf() throws Exception {
        assertThat(inspect("Paper.PDF", TestFiles.pdf("hello")))
                .isEqualTo(new UploadInspector.Inspection("application/pdf", "Paper.PDF"));
    }

    @Test
    void acceptsUtf8Text() throws Exception {
        assertThat(inspect("notes.txt", "hello\n".getBytes(StandardCharsets.UTF_8)).mediaType())
                .isEqualTo("text/plain");
    }

    @Test
    void rejectsEmptyFile() {
        assertThatThrownBy(() -> inspect("a.txt", new byte[0])).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void rejectsUnsupportedExtensionEvenWithPdfContent() {
        assertThatThrownBy(() -> inspect("paper.docx", TestFiles.pdf("x")))
                .isInstanceOf(UnsupportedFileTypeException.class)
                .hasMessageContaining("Only PDF");
    }

    @Test
    void rejectsPdfNameWithoutPdfContent() {
        assertThatThrownBy(() -> inspect("malware.pdf", "MZ\u0090\u0000binary".getBytes(StandardCharsets.ISO_8859_1)))
                .isInstanceOf(UnsupportedFileTypeException.class)
                .hasMessageContaining("not a PDF");
    }

    @Test
    void rejectsBinaryNamedTxt() {
        assertThatThrownBy(() -> inspect("notes.txt", new byte[] {'a', 0, 'b'}))
                .isInstanceOf(UnsupportedFileTypeException.class);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "../../etc/passwd.txt     | passwd.txt",
            "C:\\Users\\me\\report.pdf | report.pdf",
            "/abs/path/file.txt       | file.txt",
            "..                       | upload",
            "'  spaced name.txt  '    | spaced name.txt",
    })
    void filenamesAreReducedToASafeBaseName(String original, String expected) {
        assertThat(UploadInspector.sanitizeFilename(original)).isEqualTo(expected);
    }

    @Test
    void controlCharactersAreRemovedAndLengthCapped() {
        assertThat(UploadInspector.sanitizeFilename("a\u0000b\nc.txt")).isEqualTo("abc.txt");
        String longName = "n".repeat(400) + ".pdf";
        String safe = UploadInspector.sanitizeFilename(longName);
        assertThat(safe).hasSize(255).endsWith(".pdf");
    }

    @Test
    void titleIsFileNameWithoutExtension() {
        assertThat(UploadInspector.titleFrom("Attention Is All You Need.pdf")).isEqualTo("Attention Is All You Need");
        assertThat(UploadInspector.titleFrom(".txt")).isEqualTo(".txt");
    }

    private static UploadInspector.Inspection inspect(String name, byte[] bytes) throws Exception {
        return UploadInspector.inspect(name, bytes.length, new ByteArrayInputStream(bytes));
    }
}
