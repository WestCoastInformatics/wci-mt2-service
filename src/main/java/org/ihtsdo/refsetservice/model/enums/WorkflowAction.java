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
 * The Enum WorkflowAction.
 */
public enum WorkflowAction {

    /** The create. */
    CREATE,

    /** The edit. */
    EDIT,

    /** The cancel edit. */
    CANCEL_EDIT,

    /** The finish edit. */
    FINISH_EDIT,

    /** The upgrade. */
    UPGRADE,

    /** The cancel upgrade. */
    CANCEL_UPGRADE,

    /** The finish upgrade. */
    FINISH_UPGRADE,

    /** The request review. */
    REQUEST_REVIEW,

    /** The withdraw. */
    WITHDRAW,

    /** The review. */
    REVIEW,

    /** The reject review. */
    REJECT_REVIEW,

    /** The accept review. */
    ACCEPT_REVIEW,

    /** The unassign. */
    UNASSIGN,

    /** The request publication. */
    REQUEST_PUBLICATION,

    /** The fails rvf. */
    FAILS_RVF,

    /** Initiate publish of refset. */
    START_PUBLISH,

    /** The publish refset. */
    PUBLISH_REFSET;

    /**
     * From string.
     *
     * @param text the text
     * @return the workflow action
     */
    // convert string to enum
    public static WorkflowAction fromString(final String text) {

        for (final WorkflowAction wfAction : WorkflowAction.values()) {
            if (wfAction.toString().equalsIgnoreCase(text)) {
                return wfAction;
            }
        }
        return null;
    }

    /**
     * Returns the values.
     *
     * @return the values
     */
    public static List<WorkflowAction> getValues() {

        return Arrays.asList(WorkflowAction.values());
    }
}
