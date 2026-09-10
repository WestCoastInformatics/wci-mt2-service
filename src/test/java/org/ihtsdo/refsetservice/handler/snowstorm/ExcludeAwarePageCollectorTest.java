package org.ihtsdo.refsetservice.handler.snowstorm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.ihtsdo.refsetservice.handler.snowstorm.SnowstormMapping.ExcludeAwarePageCollector;
import org.ihtsdo.refsetservice.handler.snowstorm.SnowstormMapping.ExcludeAwarePageCollector.Decision;
import org.junit.jupiter.api.Test;

/**
 * Tests published-member paging after non-published source concepts are excluded.
 */
public class ExcludeAwarePageCollectorTest {

    @Test
    public void fillsPageWhenExclusionsPunchHolesInTheFirstSnowstormPage() {

        final Set<String> exclude = excludedCodes(9);
        final ExcludeAwarePageCollector collector = new ExcludeAwarePageCollector(exclude, 10, 0);
        final List<String> accepted = walk(collector, interleavedMembers(exclude, 10));

        assertEquals(10, accepted.size());
        assertEquals("pub-0", accepted.get(0));
        assertEquals("pub-9", accepted.get(9));
        assertFalse(collector.hasRoom());
    }

    @Test
    public void skipsOffsetPublishedConceptsForLaterPages() {

        final Set<String> exclude = excludedCodes(2);
        final ExcludeAwarePageCollector collector = new ExcludeAwarePageCollector(exclude, 10, 10);
        final List<String> accepted = walk(collector, interleavedMembers(exclude, 20));

        assertEquals(10, accepted.size());
        assertEquals("pub-10", accepted.get(0));
        assertEquals("pub-19", accepted.get(9));
    }

    @Test
    public void keepsAdditionalMembersForAnAlreadyAcceptedConcept() {

        final ExcludeAwarePageCollector collector = new ExcludeAwarePageCollector(Set.of("ex-0"), 1, 0);
        assertEquals(Decision.ACCEPT_NEW, collector.decide("pub-0"));
        assertEquals(Decision.ACCEPT_EXISTING, collector.decide("pub-0"));
        assertEquals(Decision.DROP, collector.decide("pub-1"));
        assertFalse(collector.hasRoom());
    }

    @Test
    public void publishedTotalSubtractsExcludedConcepts() {

        assertEquals(181428, SnowstormMapping.publishedMemberTotal(181437, 9));
        assertEquals(1, SnowstormMapping.publishedMemberTotal(181437, 181436));
        assertEquals(0, SnowstormMapping.publishedMemberTotal(10, 20));
    }

    private static List<String> walk(final ExcludeAwarePageCollector collector, final List<String> memberCodes) {

        final List<String> accepted = new ArrayList<>();
        for (final String code : memberCodes) {
            if (collector.decide(code) == Decision.ACCEPT_NEW) {
                accepted.add(code);
            }
        }
        return accepted;
    }

    private static Set<String> excludedCodes(final int count) {

        final Set<String> exclude = new HashSet<>();
        for (int i = 0; i < count; i++) {
            exclude.add("ex-" + i);
        }
        return exclude;
    }

    /**
     * Repeats {@code exclude} then one published code, matching a Snowstorm page with holes.
     *
     * @param exclude excluded source concepts
     * @param publishedCount how many published concepts to emit
     * @return member encounter order
     */
    private static List<String> interleavedMembers(final Set<String> exclude, final int publishedCount) {

        final List<String> codes = new ArrayList<>();
        for (int published = 0; published < publishedCount; published++) {
            codes.addAll(exclude);
            codes.add("pub-" + published);
        }
        return codes;
    }
}
