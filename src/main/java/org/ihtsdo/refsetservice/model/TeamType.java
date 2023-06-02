/*
 * Copyright 2023 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.model;

/**
 * The Enum TeamType.
 */
public enum TeamType {

    /** For organization administration team. */
    ORGANIZATION("O"),
    /** For project team. */
    PPROJECT("P");

    /** The text. */
    private final String text;

    /**
     * Instantiates a {@link TeamType} from the specified parameters.
     *
     * @param text the text
     */
    private TeamType(final String text) {

        this.text = text;
    }

    /**
     * Returns the text.
     *
     * @return the text
     */
    public String getText() {

        return this.text;
    }

}
