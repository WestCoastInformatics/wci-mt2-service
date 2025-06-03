/*
 * Copyright 2024 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.model.enums;

import java.util.Arrays;
import java.util.List;

/**
 * The Enum WorkflowStatus.
 */
public enum WorkflowStatus {

    /** The Refset is published. */
    PUBLISHED,

    /** The Refset isready for edit. */
    READY_FOR_EDIT,

    /** The Refset is in edit. */
    IN_EDIT,

    /** The Refset is in upgrade. */
    IN_UPGRADE,

    /** The Refset is ready for review. */
    READY_FOR_REVIEW,

    /** The Refset is in review. */
    IN_REVIEW,

    /** The Refset is review completed. */
    REVIEW_COMPLETED,

    /** The Refset is ready for publication. */
    READY_FOR_PUBLICATION,

    /** The Refset in publication. */
    IN_PUBLICATION;

    /**
     * From string.
     *
     * @param text the text
     * @return the workflow status
     */
    // convert string to enum
    public static WorkflowStatus fromString(final String text) {

        for (final WorkflowStatus wfStatus : WorkflowStatus.values()) {
            if (wfStatus.toString().equalsIgnoreCase(text)) {
                return wfStatus;
            }
        }
        return null;
    }

    /**
     * Returns the values.
     *
     * @return the values
     */
    public static List<WorkflowStatus> getValues() {

        return Arrays.asList(WorkflowStatus.values());
    }
}
