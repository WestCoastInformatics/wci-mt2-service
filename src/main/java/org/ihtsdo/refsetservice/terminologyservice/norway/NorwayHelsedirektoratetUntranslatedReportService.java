/*
 * Copyright 2026 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.terminologyservice.norway;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Report of Helsedirektoratet refset concepts that lack a Bokmål translation, including ICD-10 / ICPC map
 * targets. Nynorsk, Bokmål GP, and Nynorsk GP flags are omitted: those language refsets require a Bokmål
 * description, so they are always false for concepts in this report. Uses the Snowstorm URL configured as
 * {@code norway.snowstorm.url}.
 */
public class NorwayHelsedirektoratetUntranslatedReportService {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(NorwayHelsedirektoratetUntranslatedReportService.class);

    /** Branch containing Norwegian refsets. */
    private static final String BRANCH = "MAIN/SNOMEDCT-NO/REFSETS";

    /** Helsedirektoratet / Helseplattformen simple refset. */
    private static final String HELSEDIREKTORATET_REFSET_ID = "88161000202101";

    /** Bokmål language refset. */
    private static final String BOKMAL_LANG_REFSET_ID = "61000202103";

    /** ICD-10-NO map refset. */
    private static final String ICD10_MAP_REFSET_ID = "447562003";

    /** ICPC-2-NO map refset. */
    private static final String ICPC_MAP_REFSET_ID = "68101000202102";

    /**
     * Runs the Helsedirektoratet untranslated concepts report and emails the result zip.
     *
     * @throws Exception if generation or email fails
     */
    public void runReport() throws Exception {

        LOG.info("Start Norway Helsedirektoratet Untranslated Report");

        try (final NorwaySnowstormClient client = new NorwaySnowstormClient()) {

            LOG.info("Using Norway Snowstorm at {}", client.getBaseUrl());

            LOG.info("Collect Helsedirektoratet refset members ({}) on {}", HELSEDIREKTORATET_REFSET_ID, BRANCH);
            final Map<String, String> helsedirektoratetFsns = new HashMap<>();
            final Set<String> helsedirektoratetConceptIds = collectSimpleRefsetConceptIds(client, HELSEDIREKTORATET_REFSET_ID, helsedirektoratetFsns);
            LOG.info("Helsedirektoratet refset members: {}", helsedirektoratetConceptIds.size());

            LOG.info("Collect Bokmål language refset memberships");
            final Set<String> bokmalConceptIds = collectLanguageRefsetConceptIds(client, BOKMAL_LANG_REFSET_ID);
            LOG.info("Bokmål {}", bokmalConceptIds.size());

            final List<String> illegalConceptIds = new ArrayList<>();
            final List<String> untranslatedConceptIds = new ArrayList<>();
            for (final String conceptId : helsedirektoratetConceptIds) {
                if (!isNumericConceptId(conceptId)) {
                    illegalConceptIds.add(conceptId);
                    continue;
                }
                if (!bokmalConceptIds.contains(conceptId)) {
                    untranslatedConceptIds.add(conceptId);
                }
            }
            Collections.sort(illegalConceptIds);
            Collections.sort(untranslatedConceptIds);
            if (!illegalConceptIds.isEmpty()) {
                LOG.warn("Skipping Snowstorm lookups for {} non-numeric Helsedirektoratet concept id(s); " +
                    "they will appear in the report without FSN/language/map data: {}", illegalConceptIds.size(), illegalConceptIds);
            }
            LOG.info("Helsedirektoratet concepts without Bokmål translation: {}", untranslatedConceptIds.size());

            final Set<String> untranslatedSet = new HashSet<>(untranslatedConceptIds);
            fillMissingFsns(client, untranslatedConceptIds, helsedirektoratetFsns);

            LOG.info("Collect ICD-10 and ICPC maps for untranslated concepts");
            final Map<String, String> icd10Maps = collectPreferredMapTargets(client, ICD10_MAP_REFSET_ID, untranslatedSet);
            final Map<String, String> icpcMaps = collectPreferredMapTargets(client, ICPC_MAP_REFSET_ID, untranslatedSet);

            final List<String> results = new ArrayList<>();
            results.add("Id\tFSN\tSemTag\tBokmål\tICD-10 MAP\tICPC MAP");

            for (final String conceptId : untranslatedConceptIds) {
                final String fsn = helsedirektoratetFsns.get(conceptId);
                final String[] fsnParts = splitFsn(fsn);
                results.add(String.join(NorwayReplacementReportSupport.COLUMN_DELIMITER, conceptId, fsnParts[0], fsnParts[1],
                    flag(bokmalConceptIds.contains(conceptId)), StringUtils.defaultString(icd10Maps.get(conceptId)),
                    StringUtils.defaultString(icpcMaps.get(conceptId))));
            }
            for (final String conceptId : illegalConceptIds) {
                results.add(String.join(NorwayReplacementReportSupport.COLUMN_DELIMITER, conceptId, "INVALID CONCEPT ID", "", "", "", ""));
            }

            final DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
            final String dateStamp = dateFormat.format(new Date());
            final String filename = "/helsedirektoratetUntranslatedReport_" + dateStamp;
            final File resultFile = NorwayReplacementReportSupport.writeResultFile(filename, results);
            final File zipFile = NorwayReplacementReportSupport.zipResultFile(filename, resultFile);

            NorwayReplacementReportSupport.emailReportFile("[MT2] Norway Helsedirektoratet Untranslated Report",
                buildEmailBody(illegalConceptIds),
                NorwayReplacementReportSupport.HELSEDIREKTORATET_UNTRANSLATED_RECIPIENTS_PROPERTY, zipFile);

            LOG.info("Norway Helsedirektoratet Untranslated Report completed.");

        } catch (final Exception e) {
            LOG.error("ERROR", e);

            NorwayReplacementReportSupport.emailReportError("Error generating Norway Helsedirektoratet Untranslated Report",
                "There was an error generating the Norway Helsedirektoratet Untranslated Report.  Please contact support for assistance.",
                NorwayReplacementReportSupport.HELSEDIREKTORATET_UNTRANSLATED_RECIPIENTS_PROPERTY);

            throw new Exception("Norway Helsedirektoratet Untranslated Report failed to complete", e);
        }
    }

    /**
     * Collects concept IDs (and FSNs when present) for a simple refset on {@link #BRANCH}.
     *
     * @param client the Snowstorm client
     * @param refsetId the refset id
     * @param conceptIdToFsn FSN terms keyed by concept id (populated when the member payload includes them)
     * @return concept ids
     * @throws Exception on Snowstorm failure
     */
    private Set<String> collectSimpleRefsetConceptIds(final NorwaySnowstormClient client, final String refsetId, final Map<String, String> conceptIdToFsn)
        throws Exception {

        final Set<String> conceptIds = new HashSet<>();
        pageMembers(client, refsetId, true, member -> {
            final JsonNode referencedComponentId = member.get("referencedComponentId");
            if (referencedComponentId == null || referencedComponentId.isNull()) {
                return;
            }
            final String conceptId = referencedComponentId.asText();
            if (StringUtils.isBlank(conceptId)) {
                return;
            }
            conceptIds.add(conceptId);
            final JsonNode referencedComponent = member.get("referencedComponent");
            if (referencedComponent != null && referencedComponent.has("fsn")) {
                final JsonNode fsnNode = referencedComponent.get("fsn");
                if (fsnNode != null && fsnNode.has("term")) {
                    conceptIdToFsn.put(conceptId, fsnNode.get("term").asText());
                }
            }
        });
        return conceptIds;
    }

    /**
     * Collects concept IDs from a language refset (members point at descriptions).
     *
     * @param client the Snowstorm client
     * @param refsetId the language refset id
     * @return concept ids that have at least one description in the language refset
     * @throws Exception on Snowstorm failure
     */
    private Set<String> collectLanguageRefsetConceptIds(final NorwaySnowstormClient client, final String refsetId) throws Exception {

        final Set<String> conceptIds = new HashSet<>();
        pageMembers(client, refsetId, true, member -> {
            final JsonNode referencedComponent = member.get("referencedComponent");
            if (referencedComponent != null && referencedComponent.has("conceptId")) {
                conceptIds.add(referencedComponent.get("conceptId").asText());
            }
        });
        return conceptIds;
    }

    /**
     * Collects active map targets for the given concepts, preferring Norwegian-module maps over international.
     *
     * @param client the Snowstorm client
     * @param mapRefsetId the map refset id
     * @param conceptIds concepts of interest
     * @return comma-separated map targets keyed by concept id
     * @throws Exception on Snowstorm failure
     */
    private Map<String, String> collectPreferredMapTargets(final NorwaySnowstormClient client, final String mapRefsetId, final Set<String> conceptIds)
        throws Exception {

        final Map<String, Set<String>> norwayTargets = new HashMap<>();
        final Map<String, Set<String>> internationalTargets = new HashMap<>();

        pageMembers(client, mapRefsetId, true, member -> {
            final JsonNode referencedComponentId = member.get("referencedComponentId");
            if (referencedComponentId == null || referencedComponentId.isNull()) {
                return;
            }
            final String conceptId = referencedComponentId.asText();
            if (!conceptIds.contains(conceptId)) {
                return;
            }
            final JsonNode additionalFields = member.get("additionalFields");
            if (additionalFields == null || additionalFields.get("mapTarget") == null) {
                return;
            }
            final String mapTarget = additionalFields.get("mapTarget").asText();
            if (StringUtils.isBlank(mapTarget)) {
                return;
            }
            final String moduleId = member.has("moduleId") ? member.get("moduleId").asText() : "";
            if (NorwayReplacementReportSupport.NORWAY_MODULE.equals(moduleId)) {
                norwayTargets.computeIfAbsent(conceptId, key -> new TreeSet<>()).add(mapTarget);
            } else if (NorwayReplacementReportSupport.INTERNATIONAL_MODULE.equals(moduleId)) {
                internationalTargets.computeIfAbsent(conceptId, key -> new TreeSet<>()).add(mapTarget);
            }
        });

        final Map<String, String> preferred = new HashMap<>();
        for (final String conceptId : conceptIds) {
            final Set<String> norway = norwayTargets.get(conceptId);
            final Set<String> international = internationalTargets.get(conceptId);
            if (norway != null && !norway.isEmpty()) {
                preferred.put(conceptId, String.join(", ", norway));
            } else if (international != null && !international.isEmpty()) {
                preferred.put(conceptId, String.join(", ", international));
            }
        }
        return preferred;
    }

    /**
     * Fills FSNs for untranslated concepts that did not include FSN on the refset member payload.
     *
     * @param client the Snowstorm client
     * @param conceptIds untranslated concept ids
     * @param conceptIdToFsn FSN map to populate
     * @throws Exception on Snowstorm failure
     */
    private void fillMissingFsns(final NorwaySnowstormClient client, final List<String> conceptIds, final Map<String, String> conceptIdToFsn) throws Exception {

        final List<String> missing = new ArrayList<>();
        for (final String conceptId : conceptIds) {
            if (!isNumericConceptId(conceptId)) {
                continue;
            }
            if (StringUtils.isBlank(conceptIdToFsn.get(conceptId))) {
                missing.add(conceptId);
            }
        }
        if (missing.isEmpty()) {
            return;
        }

        LOG.info("Looking up FSNs for {} concepts missing FSN on the refset member payload", missing.size());
        final int batchSize = 100;
        for (int start = 0; start < missing.size(); start += batchSize) {
            final int end = Math.min(start + batchSize, missing.size());
            final StringBuilder uri = new StringBuilder("/browser/");
            uri.append(NorwaySnowstormClient.encodeBranch(BRANCH)).append("/concepts?");
            for (int i = start; i < end; i++) {
                uri.append("conceptIds=").append(missing.get(i)).append("&");
            }
            uri.append("limit=").append(batchSize);
            final JsonNode doc = client.getJson(uri.toString());
            final JsonNode items = doc.get("items");
            if (items == null) {
                continue;
            }
            for (final JsonNode conceptNode : items) {
                if (conceptNode.get("conceptId") == null || conceptNode.get("fsn") == null || conceptNode.get("fsn").get("term") == null) {
                    continue;
                }
                conceptIdToFsn.put(conceptNode.get("conceptId").asText(), conceptNode.get("fsn").get("term").asText());
            }
        }
    }

    /**
     * Pages active members of a refset on {@link #BRANCH}.
     *
     * @param client the Snowstorm client
     * @param refsetId the refset id
     * @param activeOnly whether to request only active members
     * @param consumer member consumer
     * @throws Exception on Snowstorm failure
     */
    private void pageMembers(final NorwaySnowstormClient client, final String refsetId, final boolean activeOnly, final MemberConsumer consumer)
        throws Exception {

        String searchAfter = null;
        final int limit = NorwaySnowstormClient.DEFAULT_LIMIT;
        final String encodedBranch = NorwaySnowstormClient.encodeBranch(BRANCH);

        while (true) {
            final StringBuilder uri = new StringBuilder("/");
            uri.append(encodedBranch).append("/members?referenceSet=").append(refsetId).append("&limit=").append(limit);
            if (activeOnly) {
                uri.append("&active=true");
            }
            if (searchAfter != null) {
                uri.append("&searchAfter=").append(NorwaySnowstormClient.encodeSearchAfter(searchAfter));
            }

            final JsonNode doc = client.getJson(uri.toString());
            final JsonNode items = doc.get("items");
            int returnedCount = 0;
            if (items != null) {
                for (final JsonNode member : items) {
                    consumer.accept(member);
                    returnedCount++;
                }
            }

            final JsonNode searchAfterNode = doc.get("searchAfter");
            searchAfter = searchAfterNode == null || searchAfterNode.isNull() ? null : searchAfterNode.asText();
            if (returnedCount < limit || StringUtils.isBlank(searchAfter)) {
                break;
            }
        }
    }

    /**
     * Splits an FSN into term (without semantic tag) and semantic tag, matching the report sample columns.
     *
     * @param fsn the full FSN
     * @return array of [term, semtag]
     */
    private static String[] splitFsn(final String fsn) {

        if (StringUtils.isBlank(fsn)) {
            return new String[] {
                "", ""
            };
        }
        final int lastOpen = fsn.lastIndexOf('(');
        final int lastClose = fsn.lastIndexOf(')');
        if (lastOpen >= 0 && lastClose > lastOpen) {
            return new String[] {
                fsn.substring(0, lastOpen).trim(), fsn.substring(lastOpen, lastClose + 1)
            };
        }
        return new String[] {
            fsn, ""
        };
    }

    /**
     * Returns TRUE/FALSE for a boolean flag column.
     *
     * @param value the value
     * @return TRUE or FALSE
     */
    private static String flag(final boolean value) {

        return value ? "TRUE" : "FALSE";
    }

    /**
     * Returns true when the value is a numeric SNOMED concept id (digits only).
     *
     * @param conceptId the concept id
     * @return true if numeric
     */
    private static boolean isNumericConceptId(final String conceptId) {

        return StringUtils.isNotBlank(conceptId) && StringUtils.isNumeric(conceptId);
    }

    /**
     * Builds the success email body, including any non-numeric concept ids that were not looked up.
     *
     * @param illegalConceptIds non-numeric ids included in the report without associated data
     * @return email body
     */
    private static String buildEmailBody(final List<String> illegalConceptIds) {

        final StringBuilder body = new StringBuilder();
        body.append("Hello,\n\nThe Norway Helsedirektoratet untranslated report has been generated.");
        if (illegalConceptIds != null && !illegalConceptIds.isEmpty()) {
            body.append("\n\n");
            body.append(illegalConceptIds.size());
            body.append(" refset member(s) had non-numeric concept ids and are included at the end of the report");
            body.append(" as INVALID CONCEPT ID, without FSN, language, or map lookups:\n");
            for (final String conceptId : illegalConceptIds) {
                body.append("\n- ").append(conceptId);
            }
        }
        return body.toString();
    }

    /**
     * Consumer for a Snowstorm refset member node.
     */
    @FunctionalInterface
    private interface MemberConsumer {

        /**
         * Accepts a member node.
         *
         * @param member the member
         * @throws Exception on processing failure
         */
        void accept(JsonNode member) throws Exception;
    }
}
