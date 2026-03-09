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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.InvalidParameterException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.core.Response;

import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.PfsParameter;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.terminologyservice.SnowstormConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Utility class for interacting with files.
 */
public final class LanguageUtility {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(LanguageUtility.class);

    /** The Constant DEFAULT_LANGUAGE_REFSET_US. */
    public static final String DEFAULT_LANGUAGE_REFSET_US = "900000000000509007";

    /** The Constant DEFAULT_LANGUAGE_REFSET_GB. */
    public static final String DEFAULT_LANGUAGE_REFSET_GB = "900000000000508004";

    /** The Constant PREFERRED_TERM_EN. */
    public static final String PREFERRED_TERM_EN = "PREFERRED";

    /** The Constant SPACE_SPLIT_CHARACTER. */
    public static final String SPACE_SPLIT_CHARACTER = " ";

    /** The Constant TAB_SPLIT_CHARACTER. */
    private static final String TAB_SPLIT_CHARACTER = "\t";

    /** The Constant COMMA_SPLIT_CHARACTER. */
    public static final String COMMA_SPLIT_CHARACTER = ",";

    /** The Constant UNKNOWN_LANGUAGE_CODE. */
    public static final String UNKNOWN_LANGUAGE_CODE = "n/a";

    /** The Constant ENGLISH_LANGUAGE_CODE. */
    public static final String ENGLISH_LANGUAGE_CODE = "en";

    /** The Constant UNITED_STATES_COUNTRY_CODE. */
    private static final String UNITED_STATES_COUNTRY_CODE = "US";

    /** The Constant GREAT_BRITIAN_COUNTRY_CODE. */
    private static final String GREAT_BRITIAN_COUNTRY_CODE = "GB";

    /** The Constant LANGUAGE_TO_LANGUAGE_CODE_REFERENCE_RESOURCE. */
    private static final ClassPathResource LANGUAGE_TO_LANGUAGE_CODE_REFERENCE_RESOURCE =
        new ClassPathResource("sync/supporting-files/languageToLanguageCode.txt");

    /** The Constant LANGUAGE_TO_LANGUAGE_CODE_REFERENCE_MAP. */
    private static final Map<String, String> LANGUAGE_TO_LANGUAGE_CODE_REFERENCE_MAP = new HashMap<>();

    /** The Constant ENGLISH_SPEAKING_COUNTRIES. */
    private static final Set<String> ENGLISH_SPEAKING_COUNTRIES = new HashSet<>();

    /** The Constant languageToLanguageCodeCache. */
    private static final Map<String, String> LANGUAGE_TO_LANGUAGE_CODE_CACHE = new HashMap<>();

    /** The Constant languageToCountryCodeCache. */
    private static final Map<String, String> LANGUAGE_TO_COUNTGRY_CODE_CACHE = new HashMap<>();

    /**
     * Instantiates an empty {@link LanguageUtility}.
     */
    private LanguageUtility() {

    }

    /**
     * Identify country code.
     *
     * @param languageRefsetSctId the language refset sct id
     * @return the string
     * @throws Exception the exception
     */
    public static String identifyCountryCode(final String languageRefsetSctId) throws Exception {

        if (!LANGUAGE_TO_COUNTGRY_CODE_CACHE.containsKey(languageRefsetSctId)) {

            try (final TerminologyService service = new TerminologyService()) {

                /*-
                 * Previous hard coded map for review if issues arise
                 * 
                final Map<String, String> languageCodeToCountryCodeMap = Map.ofEntries(Map.entry("113491000052101", "SE"), Map.entry(DEFAULT_LANGUAGE_REFSET, "US"),
                Map.entry("15551000146102", "NL"), Map.entry("160161000146108", "NL"), Map.entry("188001000202106", "NO"), Map.entry("21000172104", "BE"),
                Map.entry("21000220103", "IE"), Map.entry("21000234103", "AT"), Map.entry("21000267104", "KR"), Map.entry("231621000210105", "NZ"),
                Map.entry("281000210109", "NZ"), Map.entry("31000146106", "NL"), Map.entry("31000172101", "BE"), Map.entry("32570271000036106", "AU"),
                Map.entry("46011000052107", "SE"), Map.entry("47351000202107", "NO"), Map.entry("500191000057100", "SE"), Map.entry("554461000005103", "DK"),
                Map.entry("61000202103", "NO"), Map.entry("63451000052100", "SE"), Map.entry("63461000052102", "SE"), Map.entry("63481000052108", "SE"),
                Map.entry("63491000052105", "SE"), Map.entry("64311000052107", "SE"), Map.entry("701000172104", "BE"), Map.entry("71000181105", "EE"),
                Map.entry("711000172101", "BE"), Map.entry("83461000052100", "SE"));
                */

                // Defaults for US & GB as used throughout system
                if (languageRefsetSctId.equals(DEFAULT_LANGUAGE_REFSET_US)) {
                    LANGUAGE_TO_COUNTGRY_CODE_CACHE.put(languageRefsetSctId, UNITED_STATES_COUNTRY_CODE);

                    return UNITED_STATES_COUNTRY_CODE;
                } else if (languageRefsetSctId.equals(DEFAULT_LANGUAGE_REFSET_GB)) {
                    LANGUAGE_TO_COUNTGRY_CODE_CACHE.put(languageRefsetSctId, GREAT_BRITIAN_COUNTRY_CODE);

                    return GREAT_BRITIAN_COUNTRY_CODE;
                }

                // Query DB to identify the edition's country code based on ownership of the language refset
                final ResultList<Edition> results = service.find("defaultLanguageRefsets: " + languageRefsetSctId, new PfsParameter(), Edition.class, null);

                if (results.getItems().size() > 1 || results.getItems().isEmpty()) {
                    throw new Exception("Lanaguage Refset " + languageRefsetSctId + " cannot have a defaultLangaugaeRefset associated with " + results.size()
                        + " country codes");
                }

                Edition matchedEdition = results.getItems().iterator().next();
                final String countryCodeToCache = matchedEdition.getOrganization().getCountryCode() != null
                    ? matchedEdition.getOrganization().getCountryCode().toUpperCase() : "error in edition: " + matchedEdition.getShortName();

                LANGUAGE_TO_COUNTGRY_CODE_CACHE.put(languageRefsetSctId, countryCodeToCache);
            }
        }

        return LANGUAGE_TO_COUNTGRY_CODE_CACHE.get(languageRefsetSctId);
    }

    /**
     * Identify language code.
     *
     * @param languageRefsetSctId the language refset sct id
     * @param branchPath the branch path
     * @return the string
     * @throws Exception the exception
     */
    public static String identifyLanguageCode(final String languageRefsetSctId, final String branchPath) throws Exception {

        if (LANGUAGE_TO_LANGUAGE_CODE_CACHE.containsKey(languageRefsetSctId)) {
            return LANGUAGE_TO_LANGUAGE_CODE_CACHE.get(languageRefsetSctId);
        }

        initializeMap();

        // Identify the language based on language refset name
        // e.g. https://snowstorm.ihtsdotools.org/snowstorm/snomed-ct/MAIN%2FSNOMEDCT-BE/concepts/48979004
        final String conceptLookupUrl = SnowstormConnection.getBaseUrl() + branchPath + "/concepts/" + languageRefsetSctId + "/descriptions/";
        LOG.info("getSnowstormConcept url: " + conceptLookupUrl);

        boolean matchedEnglish = false;

        try (final Response response = SnowstormConnection.getResponse(conceptLookupUrl)) {

            final String resultString = SnowstormConnection.readEntityAsString(response);

            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode root = mapper.readTree(resultString);

            final Iterator<JsonNode> descriptionIterator = root.get("conceptDescriptions").iterator();

            while (descriptionIterator.hasNext()) {

                final JsonNode description = descriptionIterator.next();

                String[] termParts = description.get("term").asText().replace(",", "").split(SPACE_SPLIT_CHARACTER);

                for (int i = 0; i < termParts.length; i++) {
                    if (LANGUAGE_TO_LANGUAGE_CODE_REFERENCE_MAP.containsKey(termParts[i])) {
                        LANGUAGE_TO_LANGUAGE_CODE_CACHE.put(languageRefsetSctId, LANGUAGE_TO_LANGUAGE_CODE_REFERENCE_MAP.get(termParts[i]).toLowerCase());

                        return LANGUAGE_TO_LANGUAGE_CODE_REFERENCE_MAP.get(termParts[i]);
                    }
                }

                // Still here, no match. Try going via country code
                if (ENGLISH_SPEAKING_COUNTRIES.stream().anyMatch(country -> description.get("term").asText().contains(country))) {
                    matchedEnglish = true;
                }
            }
        }

        if (matchedEnglish) {
            LANGUAGE_TO_LANGUAGE_CODE_CACHE.put(languageRefsetSctId, ENGLISH_LANGUAGE_CODE.toLowerCase());

            return ENGLISH_LANGUAGE_CODE;
        }

        LANGUAGE_TO_LANGUAGE_CODE_CACHE.put(languageRefsetSctId, UNKNOWN_LANGUAGE_CODE.toLowerCase());

        throw new InvalidParameterException(" Do not have language code defined for language refset: " + languageRefsetSctId + " on the branch: " + branchPath
            + ", so using: '" + UNKNOWN_LANGUAGE_CODE + "'");
    }

    /**
     * Initialize map.
     */
    private static void initializeMap() {

        /*
         * final Map<String, String> languageRefsetToLanguageCodeMap = Map.ofEntries(Map.entry("450828004", "es"), Map.entry("231621000210105", "en"),
         * Map.entry("188001000202106", "nn"), Map.entry("32570271000036106", "en"), Map.entry(DEFAULT_LANGUAGE_REFSET, "en"), Map.entry("21000172104", "fr"),
         * Map.entry("31000172101", "nl"), Map.entry("15551000146102", "nl"), Map.entry("63491000052105", "sv"), Map.entry("281000210109", "en"),
         * Map.entry("500191000057100", "sv"), Map.entry("47351000202107", "nb"), Map.entry("160161000146108", "nl"), Map.entry("31000146106", "nl"),
         * Map.entry("554461000005103", "da"), Map.entry("71000181105", "et"), Map.entry("711000172101", "fr"), Map.entry("21000234103", "de"),
         * Map.entry("5641000179103", "es"), Map.entry("21000267104", "ko"), Map.entry("701000172104", "nl"), Map.entry("21000220103", "en"),
         * Map.entry("61000202103", "no"), Map.entry("46011000052107", "sv"), Map.entry("64311000052107", "sv"), Map.entry("113491000052101", "sv"),
         * Map.entry("63451000052100", "sv"), Map.entry("83461000052100", "sv"), Map.entry("63461000052102", "sv"), Map.entry("63481000052108", "sv")
         * 
         * );
         */
        // Initialize language to code map from properties file
        if (LANGUAGE_TO_LANGUAGE_CODE_REFERENCE_MAP != null && LANGUAGE_TO_LANGUAGE_CODE_REFERENCE_MAP.isEmpty()) {

            try {

                final BufferedReader reader = new BufferedReader(new InputStreamReader(LANGUAGE_TO_LANGUAGE_CODE_REFERENCE_RESOURCE.getInputStream()));

                // ProjectId, refsetId, projectName, projectDescription
                String line = reader.readLine();

                while (line != null && !line.isEmpty()) {

                    final String[] columns = line.split(TAB_SPLIT_CHARACTER);

                    if (!columns[0].contains(COMMA_SPLIT_CHARACTER)) {
                        LANGUAGE_TO_LANGUAGE_CODE_REFERENCE_MAP.put(columns[0], columns[1].toLowerCase());
                    } else {
                        String[] columnEntries = columns[0].split(COMMA_SPLIT_CHARACTER);

                        for (int j = 0; j < columnEntries.length; j++) {
                            LANGUAGE_TO_LANGUAGE_CODE_REFERENCE_MAP.put(columnEntries[j], columns[1].toLowerCase());
                        }
                    }

                    line = reader.readLine();
                }

                reader.close();
            } catch (final IOException e) {

                e.printStackTrace();
            }

        }

        if (ENGLISH_SPEAKING_COUNTRIES.isEmpty()) {
            ENGLISH_SPEAKING_COUNTRIES.add("New Zealand");
            ENGLISH_SPEAKING_COUNTRIES.add("United States");
            ENGLISH_SPEAKING_COUNTRIES.add("Britian");
            ENGLISH_SPEAKING_COUNTRIES.add("England");
            ENGLISH_SPEAKING_COUNTRIES.add("Ireland");
            ENGLISH_SPEAKING_COUNTRIES.add("Australia");
            ENGLISH_SPEAKING_COUNTRIES.add("South Africa");
        }
    }

    /**
     * Returns the supported languages.
     *
     * @return the supported languages
     */
    public static Set<String> getSupportedLanguages() {

        return LANGUAGE_TO_LANGUAGE_CODE_REFERENCE_MAP.keySet();
    }
}
