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

import java.util.Set;

import org.apache.commons.lang3.StringUtils;

/**
 * Collects source concept codes whose mapping content was written during the current save.
 *
 * <p>
 * {@link #begin(Set)} and {@link #end()} must be paired on the same thread. {@link #record(String)} outside an active collection is ignored, so unchanged
 * saves and callers that do not track changes do not report codes.
 */
public final class MappingContentChanges {

    /** Concept codes written by the save running on this thread. */
    private static final ThreadLocal<Set<String>> CURRENT = new ThreadLocal<>();

    /**
     * Instantiates a new {@link MappingContentChanges}.
     */
    private MappingContentChanges() {

        // n/a
    }

    /**
     * Start collecting concept codes into {@code target}.
     *
     * @param target the set that receives recorded concept codes
     */
    public static void begin(final Set<String> target) {

        CURRENT.set(target);
    }

    /**
     * Stop collecting concept codes on this thread.
     */
    public static void end() {

        CURRENT.remove();
    }

    /**
     * Record a source concept whose mapping content was written.
     *
     * @param conceptCode the source concept code
     */
    public static void record(final String conceptCode) {

        final Set<String> target = CURRENT.get();
        if (target == null || StringUtils.isBlank(conceptCode)) {
            return;
        }
        target.add(conceptCode.trim());
    }
}
