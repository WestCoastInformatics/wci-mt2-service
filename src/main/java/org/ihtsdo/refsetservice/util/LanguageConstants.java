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

import java.util.Collections;
import java.util.Map;

/**
 * The Class LanguageConstants.
 */
public final class LanguageConstants {

    /**
     * Instantiates an empty {@link LanguageConstants}.
     */
    private LanguageConstants() {

        // n/a
    };

    /** The Constant LANGUAGE_CODE_TO_COUNTRY_CODE. */
    public static final Map<String, String> LANGUAGE_CODE_TO_COUNTRY_CODE;

    // TODO: move this so it is not hard coded.
    // SNOMED does not have dialect. Build here with country code + language code
    static {
        final Map<String, String> map = Map.ofEntries(Map.entry("113491000052101", "SE"), Map.entry("900000000000509007", "US"),
            Map.entry("15551000146102", "NL"), Map.entry("160161000146108", "NL"), Map.entry("188001000202106", "NO"), Map.entry("21000172104", "BE"),
            Map.entry("21000220103", "IE"), Map.entry("21000234103", "AT"), Map.entry("21000267104", "KR"), Map.entry("231621000210105", "NZ"),
            Map.entry("281000210109", "NZ"), Map.entry("31000146106", "NL"), Map.entry("31000172101", "BE"), Map.entry("32570271000036106", "AU"),
            Map.entry("46011000052107", "SE"), Map.entry("47351000202107", "NO"), Map.entry("500191000057100", "SE"), Map.entry("554461000005103", "DK"),
            Map.entry("61000202103", "NO"), Map.entry("63451000052100", "SE"), Map.entry("63461000052102", "SE"), Map.entry("63481000052108", "SE"),
            Map.entry("63491000052105", "SE"), Map.entry("64311000052107", "SE"), Map.entry("701000172104", "BE"), Map.entry("71000181105", "EE"),
            Map.entry("711000172101", "BE"), Map.entry("83461000052100", "SE"));

        LANGUAGE_CODE_TO_COUNTRY_CODE = Collections.unmodifiableMap(map);

    }

}
