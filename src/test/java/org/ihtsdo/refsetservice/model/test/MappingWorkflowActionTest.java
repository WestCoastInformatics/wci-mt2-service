package org.ihtsdo.refsetservice.model.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.ihtsdo.refsetservice.model.enums.MappingWorkflowAction;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link MappingWorkflowAction}.
 */
public class MappingWorkflowActionTest {

    @Test
    public void testFromStringRoundTrip() {

        for (final MappingWorkflowAction action : MappingWorkflowAction.values()) {
            assertEquals(action, MappingWorkflowAction.fromString(action.name()));
        }
        assertThrows(IllegalArgumentException.class, () -> MappingWorkflowAction.fromString("BOGUS"));
    }
}
