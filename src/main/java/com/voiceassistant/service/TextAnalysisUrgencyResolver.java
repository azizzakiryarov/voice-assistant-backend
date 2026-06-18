package com.voiceassistant.service;

import com.voiceassistant.dto.DeadlineType;
import com.voiceassistant.dto.ItemCategory;
import com.voiceassistant.dto.Urgency;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class TextAnalysisUrgencyResolver {

    public Urgency resolveTodoUrgency(
            Urgency suggested,
            DeadlineType deadlineType,
            ItemCategory category,
            String sourceText) {
        Urgency resolved = suggested == null ? Urgency.LOW : suggested;
        String source = sourceText == null ? "" : sourceText.toLowerCase(Locale.ROOT);

        if (source.contains("allerg")
                || source.contains("specialkost")
                || source.contains("special diet")
                || source.contains("dietary")) {
            resolved = max(resolved, Urgency.HIGH);
        }

        if (source.contains("omgående")
                || source.contains("immediately")) {
            resolved = max(resolved, Urgency.HIGH);
        } else if (source.contains("så snart")
                || source.contains("as soon as possible")
                || deadlineType == DeadlineType.AS_SOON_AS_POSSIBLE) {
            resolved = max(resolved, Urgency.MEDIUM);
        }

        if (category == ItemCategory.AUTHORITY || category == ItemCategory.HEALTH) {
            resolved = max(resolved, Urgency.MEDIUM);
        }

        return resolved;
    }

    private Urgency max(Urgency current, Urgency minimum) {
        return current.ordinal() <= minimum.ordinal() ? current : minimum;
    }
}
