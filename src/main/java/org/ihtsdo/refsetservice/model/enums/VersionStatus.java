/*
 * Copyright 2023 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.model.enums;

/**
 * The Enum VersionStatus.
 */
public enum VersionStatus {

    /** Captures all non-published status. */
    IN_DEVELOPMENT("IN DEVELOPMENT"),

    /** The published status. */
    PUBLISHED("PUBLISHED");

    /** The label. */
    private final String label;

    /**
     * Instantiates a {@link VersionStatus} from the specified parameters.
     *
     * @param label the label
     */
    private VersionStatus(final String label) {

        this.label = label;
    }

    /**
     * Returns the label.
     *
     * @return the label
     */
    public String getLabel() {

        return label;
    }
}
