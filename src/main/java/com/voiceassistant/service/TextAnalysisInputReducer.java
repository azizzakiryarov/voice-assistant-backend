package com.voiceassistant.service;

import com.voiceassistant.dto.TextAnalysisRequestDTO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TextAnalysisInputReducer {

    private static final int MIN_LENGTH_FOR_RELEVANCE_FILTER = 1_800;
    private static final int MIN_RELEVANT_TEXT_LENGTH = 240;
    private static final Pattern SEPARATOR_LINE = Pattern.compile("(?m)^\\s*[_-]{10,}\\s*$");
    private static final Pattern SIGNATURE_MARKER = Pattern.compile(
            "(?im)^\\s*(Med\\s+Vänliga\\s+Hälsningar\\s*/\\s*Yours\\s+Sincerely|Med\\s+Vänliga\\s+Hälsningar|Yours\\s+Sincerely)\\b");
    private static final Pattern ENGLISH_GREETING = Pattern.compile("(?im)^\\s*Dear\\s+(Parents|Guardians|Parents\\s+and\\s+Guardians)\\b");
    private static final Pattern SWEDISH_GREETING = Pattern.compile("(?im)^\\s*Bästa\\s+(föräldrar|vårdnadshavare|föräldrar\\s+och\\s+vårdnadshavare)\\b");
    private static final Pattern MONTH_OR_DATE = Pattern.compile(
            "(?iu)\\b(\\d{1,2}\\s*(januari|februari|mars|april|maj|juni|juli|augusti|september|oktober|november|december)|"
                    + "(january|february|march|april|may|june|july|august|september|october|november|december)\\s+\\d{1,2}(st|nd|rd|th)?|"
                    + "\\d{1,2}[:.]\\d{2}|kl\\.?\\s*\\d{1,2})\\b");

    public Result reduce(TextAnalysisRequestDTO request) {
        String original = normalizeLineEndings(request.text()).trim();
        Reduction reduction = new Reduction(original);

        reduction = removeSignature(reduction);
        reduction = reduceClearBilingualDuplicate(reduction);
        reduction = keepRelevantParagraphs(reduction);

        String reducedText = reduction.text().trim();
        if (reducedText.isBlank()) {
            reducedText = original;
        }

        TextAnalysisRequestDTO reducedRequest = new TextAnalysisRequestDTO(
                request.title(),
                reducedText,
                request.sourceType(),
                request.receivedAt(),
                request.timeZone());

        return new Result(reducedRequest, original.length(), reducedText.length(), List.copyOf(reduction.rules()));
    }

    private Reduction removeSignature(Reduction reduction) {
        Matcher matcher = SIGNATURE_MARKER.matcher(reduction.text());
        if (!matcher.find() || matcher.start() < 500) {
            return reduction;
        }
        String shortened = reduction.text().substring(0, matcher.start()).trim();
        if (shortened.length() < MIN_RELEVANT_TEXT_LENGTH) {
            return reduction;
        }
        return reduction.withText(shortened, "signature_removed");
    }

    private Reduction reduceClearBilingualDuplicate(Reduction reduction) {
        String text = reduction.text();
        Matcher separator = SEPARATOR_LINE.matcher(text);
        if (separator.find()) {
            String first = text.substring(0, separator.start()).trim();
            String second = text.substring(separator.end()).trim();
            if (isClearDuplicatePair(first, second)) {
                return reduction.withText(preferredLanguageSection(first, second), "bilingual_duplicate_removed");
            }
        }

        Matcher englishGreeting = ENGLISH_GREETING.matcher(text);
        if (englishGreeting.find() && englishGreeting.start() > text.length() / 3) {
            String first = text.substring(0, englishGreeting.start()).trim();
            String second = text.substring(englishGreeting.start()).trim();
            if (isClearDuplicatePair(first, second)) {
                return reduction.withText(preferredLanguageSection(first, second), "bilingual_duplicate_removed");
            }
        }

        Matcher swedishGreeting = SWEDISH_GREETING.matcher(text);
        if (swedishGreeting.find() && swedishGreeting.start() > text.length() / 3) {
            String first = text.substring(0, swedishGreeting.start()).trim();
            String second = text.substring(swedishGreeting.start()).trim();
            if (isClearDuplicatePair(first, second)) {
                return reduction.withText(preferredLanguageSection(first, second), "bilingual_duplicate_removed");
            }
        }

        return reduction;
    }

    private boolean isClearDuplicatePair(String first, String second) {
        if (first.length() < 500 || second.length() < 500) {
            return false;
        }
        Set<String> firstConcepts = concepts(first);
        Set<String> secondConcepts = concepts(second);
        firstConcepts.retainAll(secondConcepts);
        return firstConcepts.size() >= 3;
    }

    private String preferredLanguageSection(String first, String second) {
        boolean firstLooksSwedish = looksSwedish(first);
        boolean secondLooksSwedish = looksSwedish(second);
        if (firstLooksSwedish != secondLooksSwedish) {
            return firstLooksSwedish ? first : second;
        }
        return first;
    }

    private Reduction keepRelevantParagraphs(Reduction reduction) {
        String text = reduction.text();
        if (text.length() < MIN_LENGTH_FOR_RELEVANCE_FILTER) {
            return reduction;
        }

        List<String> paragraphs = splitParagraphs(text);
        Set<Integer> keep = new LinkedHashSet<>();
        for (int i = 0; i < paragraphs.size(); i++) {
            if (isRelevantParagraph(paragraphs.get(i))) {
                keep.add(i);
                if (i > 0 && isHeading(paragraphs.get(i - 1))) {
                    keep.add(i - 1);
                }
            }
            if (isHeading(paragraphs.get(i)) && i + 1 < paragraphs.size() && isRelevantParagraph(paragraphs.get(i + 1))) {
                keep.add(i);
            }
        }

        if (keep.isEmpty()) {
            return reduction;
        }

        List<String> relevant = keep.stream()
                .sorted()
                .map(paragraphs::get)
                .toList();
        String reduced = String.join("\n\n", relevant).trim();
        if (reduced.length() < MIN_RELEVANT_TEXT_LENGTH || reduced.length() > text.length() * 0.90) {
            return reduction;
        }
        return reduction.withText(reduced, "irrelevant_paragraphs_removed");
    }

    private List<String> splitParagraphs(String text) {
        List<String> paragraphs = new ArrayList<>();
        for (String rawParagraph : text.split("\\n\\s*\\n+")) {
            String paragraph = rawParagraph.trim();
            if (!paragraph.isBlank()) {
                paragraphs.add(paragraph);
            }
        }
        return paragraphs;
    }

    private boolean isRelevantParagraph(String paragraph) {
        String normalized = normalizeForMatching(paragraph);
        return MONTH_OR_DATE.matcher(paragraph).find()
                || containsAny(normalized,
                "första skoldagen",
                "first day of school",
                "ordinarie schema",
                "regular timetable",
                "specialkost",
                "special dietary",
                "dietary requirement",
                "allerg",
                "junior club",
                "gratis",
                "free of charge",
                "kostnaden",
                "cost",
                "3 500",
                "3,500",
                "sek",
                "kr",
                "återbetal",
                "non refundable",
                "non-refundable",
                "schoolsoft",
                "logga in",
                "log in",
                "regelbundet",
                "regularly",
                "modersmål",
                "home language",
                "mother tongue",
                "fyll i",
                "complete",
                "skicka tillbaka",
                "return it",
                "formulär",
                "form",
                "blankett",
                "attached document",
                "så snart som möjligt",
                "as soon as possible",
                "earliest convenience",
                "registration");
    }

    private boolean isHeading(String paragraph) {
        String singleLine = paragraph.replace('\n', ' ').trim();
        return singleLine.length() <= 80
                && !singleLine.endsWith(".")
                && singleLine.split("\\s+").length <= 8;
    }

    private Set<String> concepts(String text) {
        String normalized = normalizeForMatching(text);
        Set<String> concepts = new LinkedHashSet<>();
        addConcept(concepts, normalized, "first-school-day", "första skoldagen", "first day of school");
        addConcept(concepts, normalized, "regular-schedule", "ordinarie schema", "regular timetable");
        addConcept(concepts, normalized, "special-diet", "specialkost", "special dietary", "dietary requirement", "allerg");
        addConcept(concepts, normalized, "junior-club", "junior club");
        addConcept(concepts, normalized, "schoolsoft", "schoolsoft");
        addConcept(concepts, normalized, "home-language", "modersmål", "home language", "mother tongue");
        addConcept(concepts, normalized, "cost", "3 500", "3,500", "sek", "kr", "cost", "kostnaden");
        addConcept(concepts, normalized, "login", "bank id", "bank-id", "logga in", "log in");
        return concepts;
    }

    private void addConcept(Set<String> concepts, String text, String concept, String... needles) {
        if (containsAny(text, needles)) {
            concepts.add(concept);
        }
    }

    private boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private boolean looksSwedish(String text) {
        String normalized = normalizeForMatching(text);
        return normalized.contains("föräldrar")
                || normalized.contains("vårdnadshavare")
                || normalized.contains("första skoldagen")
                || normalized.contains("så snart som möjligt")
                || normalized.contains("modersmål")
                || normalized.contains("vänligen");
    }

    private String normalizeLineEndings(String value) {
        return value.replace("\r\n", "\n").replace('\r', '\n');
    }

    private String normalizeForMatching(String value) {
        return normalizeLineEndings(value)
                .toLowerCase(Locale.ROOT)
                .replace('\u00a0', ' ')
                .replace('–', '-')
                .replaceAll("\\s+", " ")
                .trim();
    }

    public record Result(
            TextAnalysisRequestDTO request,
            int originalLength,
            int reducedLength,
            List<String> appliedRules
    ) {
        public boolean reduced() {
            return reducedLength < originalLength;
        }
    }

    private record Reduction(String text, List<String> rules) {
        private Reduction(String text) {
            this(text, List.of());
        }

        private Reduction withText(String text, String rule) {
            List<String> updatedRules = new ArrayList<>(rules);
            updatedRules.add(rule);
            return new Reduction(text, updatedRules);
        }
    }
}
