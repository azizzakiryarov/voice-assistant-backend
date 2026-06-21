package com.voiceassistant.service;

import com.voiceassistant.dto.TextAnalysisResponseDTO;
import com.voiceassistant.dto.TextAnalysisWarningDTO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class FormScanInterpreter {

    private static final Pattern CHILD_NAME = Pattern.compile(
            "(?im)^(?:barnets\\s+namn|namn\\s+p[åa]\\s+barn|child(?:'s)?\\s+name|namn)\\s*[:\\-]\\s*([^\\r\\n]{2,80})$");
    private static final Pattern DATE = Pattern.compile("\\b(?:20\\d{2}[-/.]\\d{1,2}[-/.]\\d{1,2}|\\d{1,2}[-/.]\\d{1,2}(?:[-/.]20\\d{2})?)\\b");

    public Interpretation interpret(String extractedText, TextAnalysisResponseDTO analysis) {
        String normalized = extractedText.toLowerCase(Locale.ROOT);
        List<TextAnalysisWarningDTO> warnings = new ArrayList<>(safeList(analysis.warnings()));
        String formType = detectFormType(normalized);
        String childName = childName(extractedText);
        boolean hasDate = DATE.matcher(extractedText).find();

        if (normalized.contains("[unclear]") || normalized.contains("oläslig")) {
            warnings.add(new TextAnalysisWarningDTO(
                    "UNCLEAR_HANDWRITING",
                    "Viss handskriven text var otydlig. Kontrollera alla datum och tider innan du godkänner.",
                    true));
        }
        if (!hasDate) {
            warnings.add(new TextAnalysisWarningDTO(
                    "MISSING_DATE",
                    "Inget tydligt datum hittades i formuläret. Skapa eller justera datum manuellt vid behov.",
                    true));
        }
        if (safeList(analysis.events()).isEmpty() && safeList(analysis.todos()).isEmpty()) {
            warnings.add(new TextAnalysisWarningDTO(
                    "NO_ACTIONABLE_ITEMS",
                    "Formuläret gav inga säkra förslag. Du kan ta en ny bild eller skriva in uppgifter manuellt.",
                    true));
        }

        return new Interpretation(formType, childName, confidence(analysis, normalized), List.copyOf(warnings));
    }

    private String detectFormType(String text) {
        if (containsAny(text, "förskola", "förskole", "dagis", "daycare", "barnomsorg")
                && containsAny(text, "schema", "tider", "närvaro", "attendance")) {
            return "daycare_schedule";
        }
        if (containsAny(text, "skola", "school", "fritids", "activity", "aktivitet")) {
            return "school_activity";
        }
        if (containsAny(text, "besök", "appointment", "läkare", "tandläkare", "vårdcentral")) {
            return "appointment";
        }
        if (containsAny(text, "sista datum", "deadline", "skicka in", "submit")) {
            return "deadline";
        }
        return "other";
    }

    private String childName(String text) {
        Matcher matcher = CHILD_NAME.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        String value = matcher.group(1).trim();
        return value.equalsIgnoreCase("[unclear]") ? null : value;
    }

    private double confidence(TextAnalysisResponseDTO analysis, String text) {
        List<Double> values = new ArrayList<>();
        safeList(analysis.events()).forEach(item -> values.add(item.confidence()));
        safeList(analysis.todos()).forEach(item -> values.add(item.confidence()));
        double result = values.isEmpty()
                ? 0.35
                : values.stream().filter(value -> value != null).mapToDouble(Double::doubleValue).average().orElse(0.35);
        if (text.contains("[unclear]") || text.contains("oläslig")) {
            result = Math.min(result, 0.55);
        }
        return Math.max(0.0, Math.min(1.0, result));
    }

    private boolean containsAny(String text, String... values) {
        for (String value : values) {
            if (text.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private <T> List<T> safeList(List<T> items) {
        return items == null ? List.of() : items;
    }

    public record Interpretation(
            String detectedFormType,
            String childName,
            double confidence,
            List<TextAnalysisWarningDTO> warnings
    ) {
    }
}
