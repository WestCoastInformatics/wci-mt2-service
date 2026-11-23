package org.ihtsdo.refsetservice.terminologyservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link MappingContentChanges}.
 */
public class MappingContentChangesTest {

    /**
     * Codes are collected only between begin and end, and blank codes are ignored.
     */
    @Test
    public void recordCollectsCodesOnlyWhileTracking() {

        final Set<String> changed = new LinkedHashSet<>();
        MappingContentChanges.record("ignored");
        assertTrue(changed.isEmpty());

        MappingContentChanges.begin(changed);
        try {
            MappingContentChanges.record(" 123 ");
            MappingContentChanges.record("");
            MappingContentChanges.record(null);
            MappingContentChanges.record("123");
        } finally {
            MappingContentChanges.end();
        }

        assertEquals(Set.of("123"), changed);
        MappingContentChanges.record("after");
        assertEquals(Set.of("123"), changed);
    }
}
