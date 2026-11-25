package org.ihtsdo.refsetservice.terminologyservice;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link BranchService#generateBranchId()}.
 */
public class BranchServiceGenerateBranchIdTest {

    /**
     * A map branch and its edit branch are created back to back, and must not receive the same timestamp.
     */
    @Test
    public void successiveIdsDiffer() {

        final String mapBranchId = BranchService.generateBranchId();
        final String editBranchId = BranchService.generateBranchId();

        assertNotEquals(mapBranchId, editBranchId);
        assertTrue(Long.parseLong(editBranchId) > Long.parseLong(mapBranchId));
    }

    /**
     * A burst of ids stays unique even when they are all requested in the same millisecond.
     */
    @Test
    public void burstOfIdsIsUniqueAndIncreasing() {

        final Set<String> ids = new HashSet<>();
        String previous = BranchService.generateBranchId();
        ids.add(previous);
        for (int i = 0; i < 20; i++) {
            final String next = BranchService.generateBranchId();
            assertTrue(Long.parseLong(next) > Long.parseLong(previous));
            ids.add(next);
            previous = next;
        }
        assertTrue(ids.size() == 21);
    }
}
