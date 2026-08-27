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
 * Per-mapping workflow actions. Separate from mapset {@link WorkflowAction}.
 */
public enum MappingWorkflowAction {

    /** Assign a mapping to a user for editing. */
    ASSIGN,

    /** Release assignment back to the queue. */
    RELEASE,

    /** Specialist finished editing this mapping. */
    FINISH_EDITING,

    /** Admin or lead force-release of an assignment. */
    FORCE_RELEASE,

    /** Transfer assignment to another user without changing phase. */
    REASSIGN,

    /** Lead or admin approves mapping for publication readiness. */
    APPROVE_FOR_PUBLICATION,

    /** Specialist or lead requests lead review of finished editing. */
    REQUEST_REVIEW,

    /** Lead starts review of a mapping awaiting review. */
    START_REVIEW,

    /** Lead accepts review and marks mapping resolved. */
    ACCEPT_REVIEW,

    /** Lead rejects review and returns mapping to the queue. */
    REJECT_REVIEW,

    /** Lead requests specialist revision after review. */
    REQUEST_REVISION,

    /** Lead starts conflict resolution for disagreeing specialist results. */
    START_CONFLICT_RESOLUTION,

    /** Lead resolves a mapping conflict. */
    RESOLVE_CONFLICT;

    /**
     * From string.
     *
     * @param text the text
     * @return the mapping workflow action
     */
    public static MappingWorkflowAction fromString(final String text) {

        for (final MappingWorkflowAction action : MappingWorkflowAction.values()) {
            if (action.toString().equalsIgnoreCase(text)) {
                return action;
            }
        }
        throw new IllegalArgumentException("Unknown MappingWorkflowAction: " + text);
    }

    /**
     * Returns the values.
     *
     * @return the values
     */
    public static List<MappingWorkflowAction> getValues() {

        return Arrays.asList(MappingWorkflowAction.values());
    }
}
