package com.nexusai.citation;

import com.nexusai.citation.ValidatedAnswer.Outcome;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Verifies a model's citations against the passages it was actually given. Pure logic:
 * no I/O, deterministic, so every rule is unit-testable.
 *
 * <p>A citation survives only if (1) it names a source that was provided, and (2) its
 * quote occurs in that source's text, ignoring case, whitespace and typographic quote
 * styles. Quotes elided with "..." must match segment by segment. Markers in the answer
 * that point at unverified sources are removed; the rest are renumbered 1..n in order of
 * first appearance so the answer and its citation list agree.
 */
final class CitationValidator {

    /** [1], [2, 3], [1;2], [Source 4] (case-insensitive). */
    private static final Pattern MARKER = Pattern.compile("\\[\\s*(?:sources?\\s*)?(\\d+(?:\\s*[,;]\\s*\\d+)*)\\s*]",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ELLIPSIS = Pattern.compile("\\.{3,}|…");
    private static final Pattern SPACE_BEFORE_PUNCT = Pattern.compile("[ \\t]+([.,;:!?])");
    private static final Pattern MULTI_SPACE = Pattern.compile("[ \\t]{2,}");

    private final int minQuoteChars;

    CitationValidator(int minQuoteChars) {
        this.minQuoteChars = minQuoteChars;
    }

    ValidatedAnswer validate(ModelAnswer answer, List<CitableSource> sources) {
        if (!answer.answerable()) {
            return new ValidatedAnswer(Outcome.INSUFFICIENT_EVIDENCE, null, List.of(), answer.citations().size());
        }
        String text = answer.answer() == null ? "" : answer.answer().strip();
        if (text.isEmpty()) {
            return new ValidatedAnswer(Outcome.UNVERIFIABLE, null, List.of(), answer.citations().size());
        }

        Map<Integer, CitableSource> byNumber = new LinkedHashMap<>();
        sources.forEach(s -> byNumber.put(s.number(), s));

        // 1. Verify claims; keep the first verified quote per source.
        Map<Integer, String> verifiedQuote = new LinkedHashMap<>();
        int rejected = 0;
        for (ModelAnswer.Claim claim : answer.citations()) {
            CitableSource source = byNumber.get(claim.source());
            String quote = cleanQuote(claim.quote());
            if (source == null || quote == null || !occursIn(quote, source.content())) {
                rejected++;
                continue;
            }
            verifiedQuote.putIfAbsent(claim.source(), quote);
        }
        if (verifiedQuote.isEmpty()) {
            return new ValidatedAnswer(Outcome.UNVERIFIABLE, null, List.of(), rejected);
        }

        // 2. Ordinals: verified sources in order of first marker appearance, then the rest.
        Map<Integer, Integer> ordinalOf = new LinkedHashMap<>();
        Matcher m = MARKER.matcher(text);
        while (m.find()) {
            for (String n : m.group(1).split("[,;]")) {
                int number = Integer.parseInt(n.strip());
                if (verifiedQuote.containsKey(number)) {
                    ordinalOf.putIfAbsent(number, ordinalOf.size() + 1);
                }
            }
        }
        verifiedQuote.keySet().forEach(n -> ordinalOf.putIfAbsent(n, ordinalOf.size() + 1));

        // 3. Rewrite markers: keep verified (renumbered), drop the rest.
        StringBuilder rewritten = new StringBuilder();
        m = MARKER.matcher(text);
        while (m.find()) {
            StringBuilder replacement = new StringBuilder();
            for (String n : m.group(1).split("[,;]")) {
                Integer ordinal = ordinalOf.get(Integer.parseInt(n.strip()));
                if (ordinal != null && replacement.indexOf("[" + ordinal + "]") < 0) {
                    replacement.append('[').append(ordinal).append(']');
                }
            }
            m.appendReplacement(rewritten, Matcher.quoteReplacement(replacement.toString()));
        }
        m.appendTail(rewritten);
        String finalText = MULTI_SPACE.matcher(SPACE_BEFORE_PUNCT.matcher(rewritten).replaceAll("$1")).replaceAll(" ").strip();

        List<CitationDraft> drafts = new ArrayList<>();
        ordinalOf.forEach((number, ordinal) -> {
            CitableSource s = byNumber.get(number);
            drafts.add(new CitationDraft(ordinal, s.documentId(), s.chunkId(), s.documentTitle(), s.pageNumber(),
                    s.chunkIndex(), s.charStart(), s.charEnd(), verifiedQuote.get(number), s.content(), s.score()));
        });
        drafts.sort((a, b) -> Integer.compare(a.ordinal(), b.ordinal()));
        return new ValidatedAnswer(Outcome.GROUNDED, finalText, List.copyOf(drafts), rejected);
    }

    /** Trims, removes wrapping quotes, rejects quotes too short to verify anything. */
    private String cleanQuote(String quote) {
        if (quote == null) return null;
        String q = quote.strip();
        q = q.replaceAll("^[\"'“”‘’«»]+|[\"'“”‘’«»]+$", "").strip();
        String withoutEllipses = ELLIPSIS.matcher(q).replaceAll("").strip();
        return withoutEllipses.length() < minQuoteChars ? null : q;
    }

    /**
     * Case/whitespace/quote-style-insensitive containment; "..." splits into ordered segments.
     * Falls back to comparing with all whitespace removed, because PDF extraction often breaks
     * words across lines ("galax\ny"): every character must still match, in order.
     */
    boolean occursIn(String quote, String passage) {
        return occursIn(quote, passage, false) || occursIn(quote, passage, true);
    }

    private static boolean occursIn(String quote, String passage, boolean ignoreAllWhitespace) {
        String haystack = prepare(passage, ignoreAllWhitespace);
        int from = 0;
        for (String segment : ELLIPSIS.split(quote)) {
            String needle = prepare(segment, ignoreAllWhitespace);
            if (needle.isEmpty()) continue;
            int at = haystack.indexOf(needle, from);
            if (at < 0) return false;
            from = at + needle.length();
        }
        return true;
    }

    private static String prepare(String text, boolean ignoreAllWhitespace) {
        String normalized = normalize(text);
        return ignoreAllWhitespace ? normalized.replace(" ", "") : normalized;
    }

    static String normalize(String text) {
        String t = Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        t = t.replaceAll("[“”«»„]", "\"").replaceAll("[‘’‚]", "'").replaceAll("[‐‑‒–—]", "-");
        t = t.replaceAll("-\\s*\\n\\s*", "-");            // hyphenated line breaks in PDFs
        t = t.replaceAll("\\s+", " ");
        return t.strip().replaceAll("^[\\p{Punct}\\s]+|[\\p{Punct}\\s]+$", "");
    }
}
