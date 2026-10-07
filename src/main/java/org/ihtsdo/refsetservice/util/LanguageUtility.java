/*
 * Copyright 2026 SNOMED International - All Rights Reserved.
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status.Family;

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
    private static final Map<String, String> LANGUAGE_TO_COUNTRY_CODE_CACHE = new HashMap<>();

    /** SNOMED description type id: Fully specified name. */
    private static final String DESCRIPTION_TYPE_FSN = "900000000000003001";

    /**
     * Cache: branch|languageRefsetSctId &rarr; short human column label (from Snowstorm FSN, abbreviated).
     */
    private static final Map<String, String> LANGUAGE_REFSET_SHORT_LABEL_CACHE = new ConcurrentHashMap<>();

    /**
     * Cache: branch|fsnfull|languageRefsetSctId &rarr; unabbreviated FSN (or first active term) for column header tooltips.
     */
    private static final Map<String, String> LANGUAGE_REFSET_FULL_FSN_CACHE = new ConcurrentHashMap<>();

    /**
     * Cache: branch path &rarr; metadata per language-refset SCTID (label, key, language, dialectName).
     */
    private static final Map<String, Map<String, Map<String, String>>> BRANCH_LANGUAGE_REFSET_META_CACHE = new ConcurrentHashMap<>();

    /** Metadata map key: Snowstorm optional language refset label. */
    public static final String META_LABEL = "label";

    /** Metadata map key: Snowstorm optional key. */
    public static final String META_KEY = "key";

    /** Metadata map key: language code from branch metadata. */
    public static final String META_LANGUAGE = "language";

    /** Metadata map key: dialectName from requiredLanguageRefsets. */
    public static final String META_DIALECT_NAME = "dialectName";

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

        if (!LANGUAGE_TO_COUNTRY_CODE_CACHE.containsKey(languageRefsetSctId)) {

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
                    LANGUAGE_TO_COUNTRY_CODE_CACHE.put(languageRefsetSctId, UNITED_STATES_COUNTRY_CODE);

                    return UNITED_STATES_COUNTRY_CODE;
                } else if (languageRefsetSctId.equals(DEFAULT_LANGUAGE_REFSET_GB)) {
                    LANGUAGE_TO_COUNTRY_CODE_CACHE.put(languageRefsetSctId, GREAT_BRITIAN_COUNTRY_CODE);

                    return GREAT_BRITIAN_COUNTRY_CODE;
                }

                // Query DB to identify the edition's country code based on ownership of the language refset
                final ResultList<Edition> results = service.find("defaultLanguageRefsets: " + languageRefsetSctId, new PfsParameter(), Edition.class, null);

                if (results.getItems().size() > 1 || results.getItems().isEmpty()) {
                    throw new Exception("Lanaguage Refset " + languageRefsetSctId + " cannot have a defaultLangaugaeRefset associated with " + results.size()
                    + " country codes");
                }

                final Edition matchedEdition = results.getItems().iterator().next();
                final String countryCodeToCache = matchedEdition.getOrganization().getCountryCode() != null
                    ? matchedEdition.getOrganization().getCountryCode().toUpperCase() : "error in edition: " + matchedEdition.getShortName();

                LANGUAGE_TO_COUNTRY_CODE_CACHE.put(languageRefsetSctId, countryCodeToCache);
            }
        }

        return LANGUAGE_TO_COUNTRY_CODE_CACHE.get(languageRefsetSctId);
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

            final String resultString = response.readEntity(String.class);

            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode root = mapper.readTree(resultString.toString());

            final JsonNode conceptDescriptions = root.get("conceptDescriptions");
            final List<String> allTerms = new ArrayList<>();
            if (conceptDescriptions != null && conceptDescriptions.isArray()) {
                for (final JsonNode description : conceptDescriptions) {
                    if (description.path("active").asBoolean(true) && description.has("term")) {
                        allTerms.add(description.get("term").asText());
                    }
                }
            }

            MultiwordMatch bestMulti = null;
            for (final String termRaw : allTerms) {
                final MultiwordMatch m = matchLongestMultiwordKeyInTerm(termRaw);
                if (m != null && (bestMulti == null || m.keyLength > bestMulti.keyLength)) {
                    bestMulti = m;
                }
            }
            if (bestMulti != null) {
                LANGUAGE_TO_LANGUAGE_CODE_CACHE.put(languageRefsetSctId, bestMulti.code.toLowerCase());
                return bestMulti.code;
            }

            for (final String termRaw : allTerms) {
                final String[] termParts = termRaw.replace(",", "").split(SPACE_SPLIT_CHARACTER);

                for (int i = 0; i < termParts.length; i++) {
                    if (LANGUAGE_TO_LANGUAGE_CODE_REFERENCE_MAP.containsKey(termParts[i])) {
                        LANGUAGE_TO_LANGUAGE_CODE_CACHE.put(languageRefsetSctId, LANGUAGE_TO_LANGUAGE_CODE_REFERENCE_MAP.get(termParts[i]).toLowerCase());

                        return LANGUAGE_TO_LANGUAGE_CODE_REFERENCE_MAP.get(termParts[i]);
                    }
                }
                if (ENGLISH_SPEAKING_COUNTRIES.stream().anyMatch(country -> termRaw.contains(country))) {
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
     * Longest file key (e.g. "Norwegian Bokmål") that matches the term, with its language code.
     */
    private static final class MultiwordMatch {

        /** The code. */
        private final String code;

        /** The key length. */
        private final int keyLength;

        /**
         * Instantiates a {@link MultiwordMatch} from the specified parameters.
         *
         * @param code the code
         * @param keyLength the key length
         */
        private MultiwordMatch(final String code, final int keyLength) {

            this.code = code;
            this.keyLength = keyLength;
        }
    }

    /**
     * Match the longest multi-word key from the language file contained in the term. Used so more specific phrases (e.g. "Norwegian Bokmål") win over generic
     * tokens ("Norwegian") when scanning is done across all descriptions.
     *
     * @param term the term
     * @return the multiword match
     */
    private static MultiwordMatch matchLongestMultiwordKeyInTerm(final String term) {

        initializeMap();
        if (term == null || term.isEmpty()) {
            return null;
        }
        final String termLower = term.replace(",", "").toLowerCase();
        final List<String> multiWordKeys = LANGUAGE_TO_LANGUAGE_CODE_REFERENCE_MAP.keySet().stream().filter(k -> k.contains(SPACE_SPLIT_CHARACTER))
            .sorted(Comparator.comparingInt(String::length).reversed()).collect(Collectors.toList());
        for (final String key : multiWordKeys) {
            if (termLower.contains(key.toLowerCase())) {
                return new MultiwordMatch(LANGUAGE_TO_LANGUAGE_CODE_REFERENCE_MAP.get(key), key.length());
            }
        }
        return null;
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
                        final String[] columnEntries = columns[0].split(COMMA_SPLIT_CHARACTER);

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

    /** The Constant MAX_COLUMN_DISAMBIGUATION_LENGTH. */
    private static final int MAX_COLUMN_DISAMBIGUATION_LENGTH = 18;

    /**
     * Short tag to disambiguate language columns with the same dialect+type. Uses a very small phrase from the refset FSN; otherwise the last four digits of
     * the SCTID.
     *
     * @param languageRefsetSctId the language refset sct id
     * @param branchPath the branch path
     * @return the language refset column suffix
     */
    public static String getLanguageRefsetColumnSuffix(final String languageRefsetSctId, final String branchPath) {

        if (languageRefsetSctId == null || languageRefsetSctId.isEmpty()) {
            return "";
        }
        if (branchPath == null || branchPath.isEmpty()) {
            return trailingFourDigitsForLanguageRefsetId(languageRefsetSctId);
        }
        final String key = branchPath + "|" + languageRefsetSctId;
        final String cached = LANGUAGE_REFSET_SHORT_LABEL_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        try {
            SnowstormConnection.checkConnection();
        } catch (final Exception e) {
            final String shortLabel = trailingFourDigitsForLanguageRefsetId(languageRefsetSctId);
            LANGUAGE_REFSET_SHORT_LABEL_CACHE.put(key, shortLabel);
            return shortLabel;
        }
        final String url = SnowstormConnection.getBaseUrl() + branchPath + "/concepts/" + languageRefsetSctId + "/descriptions/";
        try (Response response = SnowstormConnection.getResponse(url)) {

            if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
                final String shortLabel = trailingFourDigitsForLanguageRefsetId(languageRefsetSctId);
                LANGUAGE_REFSET_SHORT_LABEL_CACHE.put(key, shortLabel);
                return shortLabel;
            }

            final String resultString = response.readEntity(String.class);
            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode root = mapper.readTree(resultString);
            final String bestTerm = firstActiveFsnOrTerm(root.get("conceptDescriptions"));
            if (bestTerm == null) {
                final String shortLabel = trailingFourDigitsForLanguageRefsetId(languageRefsetSctId);
                LANGUAGE_REFSET_SHORT_LABEL_CACHE.put(key, shortLabel);
                return shortLabel;
            }

            final String shortLabel = toCompactColumnTagFromRefsetTerm(bestTerm, languageRefsetSctId);
            LANGUAGE_REFSET_SHORT_LABEL_CACHE.put(key, shortLabel);
            return shortLabel;
        } catch (final Exception e) {
            final String shortLabel = trailingFourDigitsForLanguageRefsetId(languageRefsetSctId);
            LANGUAGE_REFSET_SHORT_LABEL_CACHE.put(key, shortLabel);
            return shortLabel;
        }
    }

    /**
     * Same as {@link #getLanguageRefsetColumnSuffix(String, String)}; kept for existing call sites.
     *
     * @param languageRefsetSctId the language refset sct id
     * @param branchPath the branch path
     * @return the language refset short label
     * @throws Exception the exception
     */
    public static String getLanguageRefsetShortLabel(final String languageRefsetSctId, final String branchPath) throws Exception {

        return getLanguageRefsetColumnSuffix(languageRefsetSctId, branchPath);
    }

    /**
     * Full FSN (or first active description term) for the language refset concept, for UI tooltips. Cached per branch+refset.
     *
     * @param languageRefsetSctId the language refset SCTID
     * @param branchPath edition branch
     * @return the raw term, or empty if unavailable
     */
    public static String getLanguageRefsetConceptFsn(final String languageRefsetSctId, final String branchPath) {

        if (languageRefsetSctId == null || languageRefsetSctId.isEmpty() || branchPath == null || branchPath.isEmpty()) {
            return "";
        }
        final String key = branchPath + "|fsnfull|" + languageRefsetSctId;
        if (LANGUAGE_REFSET_FULL_FSN_CACHE.containsKey(key)) {
            return LANGUAGE_REFSET_FULL_FSN_CACHE.get(key);
        }
        try {
            SnowstormConnection.checkConnection();
        } catch (final Exception e) {
            LANGUAGE_REFSET_FULL_FSN_CACHE.put(key, "");
            return "";
        }
        final String url = SnowstormConnection.getBaseUrl() + branchPath + "/concepts/" + languageRefsetSctId + "/descriptions/";
        try (Response response = SnowstormConnection.getResponse(url)) {

            if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
                LANGUAGE_REFSET_FULL_FSN_CACHE.put(key, "");
                return "";
            }
            final String resultString = response.readEntity(String.class);
            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode root = mapper.readTree(resultString);
            final String fsn = firstActiveFsnOrTerm(root.get("conceptDescriptions"));
            final String value = fsn != null ? fsn : "";
            LANGUAGE_REFSET_FULL_FSN_CACHE.put(key, value);
            return value;
        } catch (final Exception e) {
            LANGUAGE_REFSET_FULL_FSN_CACHE.put(key, "");
            return "";
        }
    }

    /**
     * First active fsn or term.
     *
     * @param list the list
     * @return the string
     */
    private static String firstActiveFsnOrTerm(final JsonNode list) {

        if (list == null || !list.isArray()) {
            return null;
        }
        String anyTerm = null;
        for (final JsonNode d : list) {
            if (!d.path("active").asBoolean(true) || !d.has("term")) {
                continue;
            }
            if (DESCRIPTION_TYPE_FSN.equals(d.path("typeId").asText(""))) {
                return d.get("term").asText();
            }
            if (anyTerm == null) {
                anyTerm = d.get("term").asText();
            }
        }
        return anyTerm;
    }

    /**
     * To compact column tag from refset term.
     *
     * @param term the term
     * @param sctid the sctid
     * @return the string
     */
    private static String toCompactColumnTagFromRefsetTerm(final String term, final String sctid) {

        String s = stripRefsetMetadataForColumn(term);
        s = s.replaceAll("(?i)\\[[^\\]]*\\]", "").replaceAll(" +", " ").trim();
        s = s.replaceAll("(?i)( language )?type( reference set)?$", "").trim();
        s = s.replace("…", " ").replace("...", " ").replaceAll(" +", " ").trim();
        if (s.isEmpty() || s.length() < 2) {
            return trailingFourDigitsForLanguageRefsetId(sctid);
        }
        s = limitLabelToWordBoundary(s, MAX_COLUMN_DISAMBIGUATION_LENGTH);
        if (s.isEmpty() || s.length() < 2) {
            return trailingFourDigitsForLanguageRefsetId(sctid);
        }
        return s;
    }

    /**
     * Limit label to word boundary.
     *
     * @param s the s
     * @param max the max
     * @return the string
     */
    private static String limitLabelToWordBoundary(final String s, final int max) {

        if (s.length() <= max) {
            return s;
        }
        final int cut = s.lastIndexOf(' ', max);
        if (cut > 2) {
            return s.substring(0, cut).trim();
        }
        return s.substring(0, max).trim();
    }

    /**
     * Strip refset metadata for column.
     *
     * @param term the term
     * @return the string
     */
    private static String stripRefsetMetadataForColumn(final String term) {

        if (term == null) {
            return "";
        }
        String s = term.replace('\u00A0', ' ').trim();
        while (true) {
            if (s.toLowerCase().endsWith("(foundation metadata concept)")) {
                s = s.substring(0, s.length() - "(foundation metadata concept)".length()).trim();
            } else if (s.toLowerCase().endsWith("(core metadata concept)")) {
                s = s.substring(0, s.length() - "(core metadata concept)".length()).trim();
            } else {
                break;
            }
        }
        if (s.toLowerCase().endsWith(" language reference set")) {
            s = s.substring(0, s.length() - " language reference set".length()).trim();
        }
        if (s.toLowerCase().endsWith(" language type reference set")) {
            s = s.substring(0, s.length() - " language type reference set".length()).trim();
        }
        return s;
    }

    /**
     * Trailing four digits for language refset id.
     *
     * @param languageRefsetSctId the language refset sct id
     * @return the string
     */
    private static String trailingFourDigitsForLanguageRefsetId(final String languageRefsetSctId) {

        return languageRefsetSctId != null && languageRefsetSctId.length() >= 4 ? languageRefsetSctId.substring(languageRefsetSctId.length() - 4)
            : languageRefsetSctId;
    }

    /**
     * Returns the supported languages.
     *
     * @return the supported languages
     */
    public static Set<String> getSupportedLanguages() {

        return LANGUAGE_TO_LANGUAGE_CODE_REFERENCE_MAP.keySet();
    }

    /**
     * Friendly label for a language refset from branch optional metadata, or empty if unavailable.
     *
     * @param languageRefsetSctId the language refset SCTID
     * @param branchPath the edition branch
     * @return Snowstorm optional label, or empty string
     */
    public static String getLanguageRefsetLabel(final String languageRefsetSctId, final String branchPath) {

        if (languageRefsetSctId == null || languageRefsetSctId.isEmpty() || branchPath == null || branchPath.isEmpty()) {
            return "";
        }
        final Map<String, String> meta = getBranchLanguageRefsetMetadata(branchPath).get(languageRefsetSctId);
        if (meta == null) {
            return "";
        }
        final String label = meta.get(META_LABEL);
        return label != null ? label : "";
    }

    /**
     * Dialect name from branch requiredLanguageRefsets metadata, or empty.
     *
     * @param languageRefsetSctId the language refset SCTID
     * @param branchPath the edition branch
     * @return dialectName or empty
     */
    public static String getLanguageRefsetDialectName(final String languageRefsetSctId, final String branchPath) {

        if (languageRefsetSctId == null || languageRefsetSctId.isEmpty() || branchPath == null || branchPath.isEmpty()) {
            return "";
        }
        final Map<String, String> meta = getBranchLanguageRefsetMetadata(branchPath).get(languageRefsetSctId);
        if (meta == null) {
            return "";
        }
        final String dialectName = meta.get(META_DIALECT_NAME);
        return dialectName != null ? dialectName : "";
    }

    /**
     * Strip common language-refset FSN suffix for a shorter column label.
     *
     * @param fsn the FSN
     * @return cleaned term
     */
    public static String cleanLanguageRefsetFsnForLabel(final String fsn) {

        if (fsn == null || fsn.isEmpty()) {
            return "";
        }
        String s = fsn.replace('\u00A0', ' ').trim();
        s = s.replaceAll("(?i) \\([^)]+\\)$", "").trim();
        s = s.replaceAll("(?i) language type reference set$", "").trim();
        s = s.replaceAll("(?i) language reference set$", "").trim();
        s = s.replaceAll("(?i) type reference set$", "").trim();
        return s;
    }

    /**
     * Branch metadata for required and optional language refsets, cached by branch path.
     *
     * @param branchPath the branch path
     * @return map of refsetId to metadata; empty on failure
     */
    public static Map<String, Map<String, String>> getBranchLanguageRefsetMetadata(final String branchPath) {

        if (branchPath == null || branchPath.isEmpty()) {
            return Map.of();
        }
        final Map<String, Map<String, String>> cached = BRANCH_LANGUAGE_REFSET_META_CACHE.get(branchPath);
        if (cached != null) {
            return cached;
        }
        final Map<String, Map<String, String>> parsed = fetchBranchLanguageRefsetMetadata(branchPath);
        BRANCH_LANGUAGE_REFSET_META_CACHE.put(branchPath, parsed);
        return parsed;
    }

    /**
     * Fetch and parse required and optional language refsets from Snowstorm branch metadata.
     *
     * @param branchPath the branch path
     * @return map of refsetId to metadata; empty on failure
     */
    private static Map<String, Map<String, String>> fetchBranchLanguageRefsetMetadata(final String branchPath) {

        final Map<String, Map<String, String>> result = new HashMap<>();
        try {
            SnowstormConnection.checkConnection();
        } catch (final Exception e) {
            LOG.warn("Cannot connect to Snowstorm for branch language refset metadata: {}", branchPath);
            return result;
        }
        final String url = SnowstormConnection.getBaseUrl() + "branches/" + branchPath + "?includeInheritedMetadata=false";
        try (Response response = SnowstormConnection.getResponse(url)) {

            if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
                LOG.warn("Failed to get branch metadata for language refsets: {} status={}", branchPath, response.getStatus());
                return result;
            }
            final String resultString = response.readEntity(String.class);
            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode root = mapper.readTree(resultString);
            final JsonNode metadata = root.get("metadata");
            if (metadata == null || metadata.isNull()) {
                return result;
            }
            return parseBranchLanguageRefsetMetadata(metadata);
        } catch (final Exception e) {
            LOG.warn("Failed to process branch language refset metadata for {}: {}", branchPath, e.getMessage());
        }
        return result;
    }

    /**
     * Parse required and optional language refsets from a branch metadata JSON node.
     *
     * @param metadata the metadata node
     * @return map of refsetId to metadata
     */
    public static Map<String, Map<String, String>> parseBranchLanguageRefsetMetadata(final JsonNode metadata) {

        final Map<String, Map<String, String>> result = new HashMap<>();
        if (metadata == null || metadata.isNull()) {
            return result;
        }
        parseRequiredLanguageRefsetsArray(metadata.get("requiredLanguageRefsets"), result);
        parseRequiredLanguageRefsetFlatKeys(metadata, result);
        parseOptionalLanguageRefsets(metadata.get("optionalLanguageRefsets"), result);
        return result;
    }

    /**
     * Parse requiredLanguageRefsets array.
     *
     * @param arrayNode the array node
     * @param result the result map to populate
     */
    private static void parseRequiredLanguageRefsetsArray(final JsonNode arrayNode, final Map<String, Map<String, String>> result) {

        if (arrayNode == null || !arrayNode.isArray()) {
            return;
        }
        for (final JsonNode entry : arrayNode) {
            if (entry == null || !entry.isObject()) {
                continue;
            }
            String dialectName = null;
            if (entry.has("dialectName") && !entry.get("dialectName").isNull()) {
                dialectName = entry.get("dialectName").asText();
            }
            final java.util.Iterator<Map.Entry<String, JsonNode>> fields = entry.fields();
            while (fields.hasNext()) {
                final Map.Entry<String, JsonNode> field = fields.next();
                final String fieldName = field.getKey();
                if ("default".equals(fieldName) || "dialectName".equals(fieldName)) {
                    continue;
                }
                final JsonNode value = field.getValue();
                if (value == null || !value.isTextual()) {
                    continue;
                }
                final String refsetId = value.asText();
                if (refsetId == null || refsetId.isEmpty()) {
                    continue;
                }
                final Map<String, String> meta = result.computeIfAbsent(refsetId, k -> new HashMap<>());
                meta.put(META_LANGUAGE, fieldName);
                if (dialectName != null && !dialectName.isEmpty()) {
                    meta.put(META_DIALECT_NAME, dialectName);
                }
            }
        }
    }

    /**
     * Parse flat requiredLanguageRefset.* keys.
     *
     * @param metadata the metadata node
     * @param result the result map to populate
     */
    private static void parseRequiredLanguageRefsetFlatKeys(final JsonNode metadata, final Map<String, Map<String, String>> result) {

        final java.util.Iterator<Map.Entry<String, JsonNode>> fields = metadata.fields();
        while (fields.hasNext()) {
            final Map.Entry<String, JsonNode> field = fields.next();
            final String fieldName = field.getKey();
            if (!fieldName.startsWith("requiredLanguageRefset.") || fieldName.equals("requiredLanguageRefsets")) {
                continue;
            }
            final JsonNode value = field.getValue();
            if (value == null || !value.isTextual()) {
                continue;
            }
            final String refsetId = value.asText();
            if (refsetId == null || refsetId.isEmpty()) {
                continue;
            }
            final String language = fieldName.substring("requiredLanguageRefset.".length());
            final Map<String, String> meta = result.computeIfAbsent(refsetId, k -> new HashMap<>());
            meta.put(META_LANGUAGE, language);
        }
    }

    /**
     * Parse optionalLanguageRefsets array.
     *
     * @param arrayNode the array node
     * @param result the result map to populate
     */
    private static void parseOptionalLanguageRefsets(final JsonNode arrayNode, final Map<String, Map<String, String>> result) {

        if (arrayNode == null || !arrayNode.isArray()) {
            return;
        }
        for (final JsonNode refset : arrayNode) {
            if (refset == null || !refset.has("refsetId")) {
                LOG.error("Optional language refset must have a refsetId defined: {}", refset);
                continue;
            }
            final String refsetId = refset.get("refsetId").asText();
            if (refsetId == null || refsetId.isEmpty()) {
                continue;
            }
            final Map<String, String> meta = result.computeIfAbsent(refsetId, k -> new HashMap<>());
            if (refset.has("label") && !refset.get("label").isNull()) {
                meta.put(META_LABEL, refset.get("label").asText());
            }
            if (refset.has("key") && !refset.get("key").isNull()) {
                meta.put(META_KEY, refset.get("key").asText());
            }
            if (refset.has("language") && !refset.get("language").isNull()) {
                meta.put(META_LANGUAGE, refset.get("language").asText());
            }
        }
    }
}