package org.ihtsdo.refsetservice.model.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.ihtsdo.refsetservice.model.MapWorkflowStatus;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link MapWorkflowStatus}.
 */
public class MapWorkflowStatusTest {

    /** Expected workflow phase order; enum ordinals must match. */
    private static final MapWorkflowStatus[] WORKFLOW_ORDER = {
        MapWorkflowStatus.NEW,
        MapWorkflowStatus.PUBLISHED,
        MapWorkflowStatus.EDITING_IN_PROGRESS,
        MapWorkflowStatus.EDITING_DONE,
        MapWorkflowStatus.CONFLICT_DETECTED,
        MapWorkflowStatus.CONFLICT_IN_PROGRESS,
        MapWorkflowStatus.CONFLICT_RESOLVED,
        MapWorkflowStatus.CONFLICT_FINISHED,
        MapWorkflowStatus.REVIEW_NEEDED,
        MapWorkflowStatus.REVIEW_IN_PROGRESS,
        MapWorkflowStatus.REVIEW_RESOLVED,
        MapWorkflowStatus.REVIEW_FINISHED,
        MapWorkflowStatus.QA_NEEDED,
        MapWorkflowStatus.QA_IN_PROGRESS,
        MapWorkflowStatus.QA_RESOLVED,
        MapWorkflowStatus.CONSENSUS_NEEDED,
        MapWorkflowStatus.CONSENSUS_NEW,
        MapWorkflowStatus.CONSENSUS_IN_PROGRESS,
        MapWorkflowStatus.READY_FOR_PUBLICATION,
        MapWorkflowStatus.REVISION
    };

    @Test
    public void testFromStringRoundTrip() {

        for (final MapWorkflowStatus status : MapWorkflowStatus.values()) {
            assertEquals(status, MapWorkflowStatus.fromString(status.name()));
        }
        assertThrows(IllegalArgumentException.class, () -> MapWorkflowStatus.fromString("BOGUS"));
    }

    @Test
    public void testPhaseOrdering() {

        assertEquals(MapWorkflowStatus.values().length, WORKFLOW_ORDER.length,
            "WORKFLOW_ORDER must list every MapWorkflowStatus constant");

        final Set<MapWorkflowStatus> expected = new HashSet<>(Arrays.asList(WORKFLOW_ORDER));
        final Set<MapWorkflowStatus> actual = Arrays.stream(MapWorkflowStatus.values()).collect(Collectors.toSet());
        assertEquals(expected, actual, "WORKFLOW_ORDER must match enum constants exactly");

        for (int i = 0; i < WORKFLOW_ORDER.length - 1; i++) {
            assertTrue(WORKFLOW_ORDER[i].ordinal() < WORKFLOW_ORDER[i + 1].ordinal(),
                WORKFLOW_ORDER[i] + " must appear before " + WORKFLOW_ORDER[i + 1]);
        }
    }

    @Test
    public void testDeprecatedRemoved() {

        final Set<String> removed = Set.of("CONFLICT_NEW", "REVIEW_NEW", "QA_NEW");
        for (final MapWorkflowStatus status : MapWorkflowStatus.values()) {
            assertTrue(!removed.contains(status.name()), "Unexpected deprecated status: " + status.name());
        }
    }
}
