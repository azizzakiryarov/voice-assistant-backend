package com.voiceassistant.service;

import com.voiceassistant.dto.DeadlineType;
import com.voiceassistant.dto.ItemCategory;
import com.voiceassistant.dto.Urgency;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextAnalysisUrgencyResolverTest {

    private final TextAnalysisUrgencyResolver resolver = new TextAnalysisUrgencyResolver();

    @Test
    void specialDietAndAllergiesAreAtLeastHighUrgency() {
        Urgency urgency = resolver.resolveTodoUrgency(
                Urgency.LOW,
                DeadlineType.AS_SOON_AS_POSSIBLE,
                ItemCategory.SCHOOL,
                "Fyll i formuläret för specialkost om barnet har allergier.");

        assertThat(urgency).isEqualTo(Urgency.HIGH);
    }

    @Test
    void recurringInformationalTaskCanStayLowUrgency() {
        Urgency urgency = resolver.resolveTodoUrgency(
                Urgency.LOW,
                DeadlineType.RECURRING,
                ItemCategory.SCHOOL,
                "Vänligen logga in regelbundet.");

        assertThat(urgency).isEqualTo(Urgency.LOW);
    }

    @Test
    void asSoonAsPossibleWithoutSafetyContextIsMediumUrgency() {
        Urgency urgency = resolver.resolveTodoUrgency(
                Urgency.LOW,
                DeadlineType.AS_SOON_AS_POSSIBLE,
                ItemCategory.SCHOOL,
                "Skicka tillbaka blanketten så snart som möjligt.");

        assertThat(urgency).isEqualTo(Urgency.MEDIUM);
    }
}
