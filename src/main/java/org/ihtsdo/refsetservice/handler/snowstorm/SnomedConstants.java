/*
 * Copyright 2025 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.handler.snowstorm;

import java.text.SimpleDateFormat;

import org.ihtsdo.refsetservice.util.DateUtility;

/**
 * The Class SnomedConstants for common Snomed constants.
 */
public final class SnomedConstants {

    /** The Constant SNOMED_SNOMEDCT_ICD10_MAPPING_MODULE. */
    public static final String SNOMEDCT_TO_ICD10_MAPPING_MODULE = "449080006";
    
    /** The parent of simple type reference set (the top-level concept for all refsets). */
    public static final String SIMPLE_TYPE_REFERENCE_SET = "446609009";

    /** The Constant DEFAULT_LANGUAGE_REFSET_US. */
    public static final String DEFAULT_LANGUAGE_REFSET_US = "900000000000509007";

    /** The description language code and type combined. */
    public static final String LANGUAGE_ID = "languageId";

    /** The description term. */
    public static final String DESCRIPTION_TERM = "term";

    /** The column index of the concept ID in an RF2 file. */
    public static final int REFEST_RF2_CONCEPTID_COLUMN = 5;
    
    /** The description type. */
    public static final String DESCRIPTION_TYPE = "type";

    /** The description language. */
    public static final String DESCRIPTION_LANGUAGE = "language";

    /** The description language. */
    public  static final String DESCRIPTION_ID = "descriptionId";

    /** The description language code. */
    public  static final String LANGUAGE_CODE = "languageCode";

    /** The description language. */
    public  static final String LANGUAGE_NAME = "languageName";
    
    /** The Constant BRANCH_DATE_FORMAT. */
    public static final SimpleDateFormat BRANCH_DATE_FORMAT = new SimpleDateFormat(DateUtility.DATE_FORMAT_REVERSE);
    
    /** The Constant BRANCH_DATE_FORMAT_ONLY_NUMBERS. */
    public static final SimpleDateFormat BRANCH_DATE_FORMAT_ONLY_NUMBERS = new SimpleDateFormat(DateUtility.DATE_FORMAT_REVERSE_ONLY_NUMBERS);

    /**
     * Instantiates a new snomed constants.
     */
    // Prevent instantiation
    private SnomedConstants() {

        throw new UnsupportedOperationException("Cannot instantiate constants class");
    }
}
