/*
 * Copyright 2026 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.terminologyservice;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for promotion-merge branch-state handling in {@link BranchService}.
 */
public class BranchServiceMergeTest {

    /**
     * After promoting a child into a branch, DIVERGED vs that branch's parent is a settled Snowstorm state and must not be retried forever.
     */
    @Test
    public void divergedIsSettledAfterPromotion() {

        assertTrue(BranchService.isPromotedTargetBranchStateSettled("DIVERGED"));
        assertTrue(BranchService.isPromotedTargetBranchStateSettled("diverged"));
    }

    /**
     * Forward / up-to-date remain accepted post-promotion states.
     */
    @Test
    public void forwardAndUpToDateAreSettledAfterPromotion() {

        assertTrue(BranchService.isPromotedTargetBranchStateSettled("FORWARD"));
        assertTrue(BranchService.isPromotedTargetBranchStateSettled("UP_TO_DATE"));
        assertTrue(BranchService.isPromotedTargetBranchStateSettled("CURRENT"));
    }

    /**
     * Behind / stale may be cache lag and should keep polling until timeout.
     */
    @Test
    public void behindAndStaleAreNotSettledAfterPromotion() {

        assertFalse(BranchService.isPromotedTargetBranchStateSettled("BEHIND"));
        assertFalse(BranchService.isPromotedTargetBranchStateSettled("STALE"));
        assertFalse(BranchService.isPromotedTargetBranchStateSettled(null));
        assertFalse(BranchService.isPromotedTargetBranchStateSettled(""));
    }
}
