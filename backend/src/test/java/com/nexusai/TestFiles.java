package com.nexusai;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/** Generates real test documents (no binary fixtures in the repository). */
public final class TestFiles {

    private TestFiles() {
    }

    /** A PDF with one page per argument; {@code null} produces a page without any text. */
    public static byte[] pdf(String... pages) {
        try (PDDocument document = build(pages)) {
            return save(document);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A PDF that requires a user password to open. */
    public static byte[] encryptedPdf(String text) {
        try (PDDocument document = build(text)) {
            StandardProtectionPolicy policy = new StandardProtectionPolicy("owner-secret", "user-secret", new AccessPermission());
            policy.setEncryptionKeyLength(128);
            document.protect(policy);
            return save(document);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Paragraphs of readable ASCII prose, long enough to produce several chunks. */
    public static String prose(int paragraphs) {
        StringBuilder text = new StringBuilder();
        for (int p = 1; p <= paragraphs; p++) {
            text.append("Paragraph ").append(p).append(". ");
            for (int s = 1; s <= 6; s++) {
                text.append("Sentence ").append(s).append(" of paragraph ").append(p)
                        .append(" explains retrieval augmented generation in some detail. ");
            }
            text.append("\n\n");
        }
        return text.toString();
    }

    private static PDDocument build(String... pages) throws IOException {
        PDDocument document = new PDDocument();
        PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        for (String text : pages) {
            PDPage page = new PDPage();
            document.addPage(page);
            if (text == null) {
                continue;
            }
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(font, 11);
                content.newLineAtOffset(50, 740);
                for (String line : text.split("\n")) {
                    content.showText(line);
                    content.newLineAtOffset(0, -14);
                }
                content.endText();
            }
        }
        return document;
    }

    private static byte[] save(PDDocument document) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        document.save(out);
        return out.toByteArray();
    }
}
