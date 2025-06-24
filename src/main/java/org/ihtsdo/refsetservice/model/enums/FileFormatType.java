/*
 * Copyright 2025 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.model.enums;

import java.util.Arrays;
import java.util.List;

/**
 * The Enum FileFormatType.
 */
public enum FileFormatType {

    /** The snapshot. */
    SNAPSHOT("Snapshot"),
    /** The delta. */
    DELTA("Delta"),;

    /** The name. */
    private final String name;

    /**
     * Instantiates a new file format type.
     *
     * @param name the name
     */
    FileFormatType(String name) {

        this.name = name;
    }

    /**
     * Gets the name.
     *
     * @return the name
     */
    public String getName() {

        return name;
    }

    /**
     * Returns the values.
     *
     * @return the values
     */
    public static List<FileFormatType> getValues() {

        return Arrays.asList(FileFormatType.values());
    }
}
