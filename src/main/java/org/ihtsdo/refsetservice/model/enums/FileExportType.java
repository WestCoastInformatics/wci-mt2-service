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
 * The Enum FileExportType.
 */
public enum FileExportType {

    /** The rf2. */
    RF2,

    /** The rf2 with names. */
    RF2_WITH_NAMES,

    /** The sctids. */
    SCTIDS;

    /**
     * Returns the values.
     *
     * @return the values
     */
    public static List<FileExportType> getValues() {

        return Arrays.asList(FileExportType.values());
    }
}
