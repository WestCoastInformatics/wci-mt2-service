/*
 * Copyright 2023 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.util;

/**
 * Custom claims for JWT.
 */
public enum Claims {

    /** The id. */
    ID("https://snomed.org/id"),
    /** The org. */
    ORG("https://snomed.org/org"),
    /** The role. */
    ROLE("https://snomed.org/role");

    /** The value. */
    private String value;

    /**
     * Instantiates a {@link Claims} from the specified parameters.
     *
     * @param value the value
     */
    Claims(final String value) {

        this.value = value;
    }

    /**
     * Returns the value.
     *
     * @return the value
     */
    public String getValue() {

        return value;
    }
}
