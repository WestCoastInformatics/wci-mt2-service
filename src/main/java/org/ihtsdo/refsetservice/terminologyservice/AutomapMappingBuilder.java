/*
 * Copyright 2026 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.terminologyservice;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.refsetservice.model.MapEntry;
import org.ihtsdo.refsetservice.model.MapProject;
import org.ihtsdo.refsetservice.model.MapRelation;
import org.ihtsdo.refsetservice.model.MapSet;
import org.ihtsdo.refsetservice.model.Mapping;
import org.ihtsdo.refsetservice.util.MapEntryUtility;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Turns automap task results into unsaved map records.
 */
public final class AutomapMappingBuilder {

    /** Display name used by the editor for an empty target. */
    static final String NO_TARGET_NAME = "[NO TARGET]";

    /**
     * Instantiates a new automap mapping builder.
     */
    private AutomapMappingBuilder() {

        // n/a
    }

    /**
     * Builds one unsaved mapping per source code, in the submitted order.
     *
     * @param mapSet the map set
     * @param conceptCodes the source concept codes
     * @param termsByCode source term for each concept code
     * @param automapTask the automap task response
     * @return the map records
     */
    public static List<Mapping> buildMappings(final MapSet mapSet, final List<String> conceptCodes, final Map<String, String> termsByCode,
        final JsonNode automapTask) {

        final Map<String, JsonNode> byCode = new HashMap<>();
        final Map<String, JsonNode> byTerm = new HashMap<>();
        final Set<String> ambiguousTerms = new HashSet<>();
        final JsonNode terms = automapTask == null ? null : automapTask.get("terms");
        if (terms != null && terms.isArray()) {
            for (final JsonNode outputTerm : terms) {
                final String code = text(outputTerm, "code");
                if (StringUtils.isNotBlank(code)) {
                    byCode.put(code, outputTerm);
                }
                final String termKey = termKey(text(outputTerm, "term"));
                if (StringUtils.isNotBlank(termKey)) {
                    if (byTerm.containsKey(termKey)) {
                        ambiguousTerms.add(termKey);
                    } else {
                        byTerm.put(termKey, outputTerm);
                    }
                }
            }
        }

        final MapProject mapProject = mapSet == null ? null : mapSet.getMapProject();
        final String moduleId = moduleId(mapSet, mapProject);
        final String rule = mapProject != null && mapProject.isRuleBased() ? "" : "TRUE";
        final String targetRelation = defaultRelationName(mapProject, false);
        final String noTargetRelation = defaultRelationName(mapProject, true);
        final List<Mapping> mappings = new ArrayList<>();
        if (conceptCodes == null) {
            return mappings;
        }
        for (final String conceptCode : conceptCodes) {
            final String sourceTerm = termsByCode == null ? "" : StringUtils.defaultString(termsByCode.get(conceptCode));
            final JsonNode outputTerm = outputTermFor(conceptCode, sourceTerm, byCode, byTerm, ambiguousTerms);
            mappings.add(toMapping(mapSet, conceptCode, sourceTerm, outputTerm, moduleId, rule, targetRelation, noTargetRelation, mapProject));
        }
        return mappings;
    }

    /**
     * Returns the preferred term from a Snowstorm concept node.
     *
     * @param conceptNode the concept node
     * @return the term, or blank when none is present
     */
    public static String termFromConceptNode(final JsonNode conceptNode) {

        final String preferred = nestedTerm(conceptNode, "pt");
        if (StringUtils.isNotBlank(preferred)) {
            return preferred;
        }
        final String fsn = nestedTerm(conceptNode, "fsn");
        if (StringUtils.isNotBlank(fsn)) {
            return fsn;
        }
        return text(conceptNode, "name");
    }

    /**
     * Returns the concept code from a Snowstorm concept node.
     *
     * @param conceptNode the concept node
     * @return the code, or blank
     */
    public static String conceptCode(final JsonNode conceptNode) {

        final String conceptId = text(conceptNode, "conceptId");
        if (StringUtils.isNotBlank(conceptId)) {
            return conceptId;
        }
        final String id = text(conceptNode, "id");
        if (StringUtils.isNotBlank(id)) {
            return id;
        }
        return text(conceptNode, "code");
    }

    /**
     * Finds the automap output for a submitted code.
     *
     * @param conceptCode the concept code
     * @param sourceTerm the source term
     * @param byCode outputs keyed by code
     * @param byTerm outputs keyed by term
     * @param ambiguousTerms terms that were returned for more than one code
     * @return the output term, or null
     */
    private static JsonNode outputTermFor(final String conceptCode, final String sourceTerm, final Map<String, JsonNode> byCode,
        final Map<String, JsonNode> byTerm, final Set<String> ambiguousTerms) {

        final JsonNode bySubmittedCode = byCode.get(conceptCode);
        if (bySubmittedCode != null) {
            return bySubmittedCode;
        }
        final String termKey = termKey(sourceTerm);
        if (StringUtils.isBlank(termKey) || ambiguousTerms.contains(termKey)) {
            return null;
        }
        return byTerm.get(termKey);
    }

    /**
     * Builds one mapping.
     *
     * @param mapSet the map set
     * @param conceptCode the concept code
     * @param sourceTerm the source term
     * @param outputTerm the automap output, or null
     * @param moduleId the module id
     * @param rule the map rule
     * @param targetRelation the relation name for a mapped target
     * @param noTargetRelation the relation name for no target
     * @param mapProject the map project
     * @return the mapping
     */
    private static Mapping toMapping(final MapSet mapSet, final String conceptCode, final String sourceTerm, final JsonNode outputTerm,
        final String moduleId, final String rule, final String targetRelation, final String noTargetRelation, final MapProject mapProject) {

        final Mapping mapping = new Mapping();
        mapping.setActive(true);
        mapping.setCode(conceptCode);
        mapping.setName(sourceTerm);
        if (mapSet != null) {
            mapping.setMapSetId(mapSet.getId());
        }
        final JsonNode best = bestMapping(outputTerm);
        final boolean noTarget = outputTerm == null || mappingCount(outputTerm) == 0 || best == null || StringUtils.isBlank(text(best, "code"));
        final MapEntry mapEntry = new MapEntry();
        mapEntry.setActive(true);
        mapEntry.setReleased(false);
        mapEntry.setGroup(1);
        mapEntry.setPriority(1);
        mapEntry.setBlock(0);
        mapEntry.setModuleId(moduleId);
        mapEntry.setRule(rule);
        if (noTarget) {
            mapEntry.setToCode("");
            mapEntry.setToName(NO_TARGET_NAME);
            mapEntry.setRelation(noTargetRelation);
            mapEntry.setRelationCode(relationCode(mapProject, noTargetRelation));
            mapEntry.setAdvices(new HashSet<>());
        } else {
            mapEntry.setToCode(text(best, "code"));
            final String toName = firstNonBlank(text(best, "name"), text(best, "term"), mapEntry.getToCode());
            mapEntry.setToName(toName);
            mapEntry.setRelation(targetRelation);
            mapEntry.setRelationCode(relationCode(mapProject, targetRelation));
            mapEntry.setAdvices(MapEntryUtility.fixMapEntryAdvices(mapEntry));
        }
        mapping.getMapEntries().add(mapEntry);
        return mapping;
    }

    /**
     * Returns the highest-confidence mapping, or null when automap reported none.
     *
     * @param outputTerm the automap output term
     * @return the best mapping
     */
    private static JsonNode bestMapping(final JsonNode outputTerm) {

        if (outputTerm == null || mappingCount(outputTerm) == 0) {
            return null;
        }
        final JsonNode mappings = outputTerm.get("mappings");
        if (mappings == null || !mappings.isArray() || mappings.isEmpty()) {
            return null;
        }
        JsonNode best = null;
        double bestConfidence = Double.NEGATIVE_INFINITY;
        for (final JsonNode mapping : mappings) {
            final double confidence = mapping.hasNonNull("confidence") ? mapping.get("confidence").asDouble() : 0d;
            if (best == null || confidence > bestConfidence) {
                best = mapping;
                bestConfidence = confidence;
            }
        }
        return best;
    }

    /**
     * Returns the automap mapping count. A missing count is treated as unknown so the mappings array can still be used.
     *
     * @param outputTerm the output term
     * @return the count, or -1 when the field is absent
     */
    private static int mappingCount(final JsonNode outputTerm) {

        if (outputTerm != null && outputTerm.has("mappingCt") && !outputTerm.get("mappingCt").isNull()) {
            return outputTerm.get("mappingCt").asInt();
        }
        return -1;
    }

    /**
     * Returns the relation name the editor uses for a new row.
     *
     * @param mapProject the map project
     * @param forNullTarget true for a no-target row
     * @return the relation name
     */
    static String defaultRelationName(final MapProject mapProject, final boolean forNullTarget) {

        if (mapProject == null || mapProject.getMapRelations() == null || mapProject.getMapRelations().isEmpty()) {
            return "";
        }
        final List<MapRelation> relations = new ArrayList<>(mapProject.getMapRelations());
        if (relations.size() == 1) {
            return titleCase(relations.get(0).getName());
        }
        for (final MapRelation relation : relations) {
            if (relation.isAllowableForNullTarget() == forNullTarget && StringUtils.isNotBlank(relation.getName())) {
                return titleCase(relation.getName());
            }
        }
        return "";
    }

    /**
     * Returns the terminology id for a relation name.
     *
     * @param mapProject the map project
     * @param relationName the relation name
     * @return the terminology id, or blank
     */
    private static String relationCode(final MapProject mapProject, final String relationName) {

        if (mapProject == null || mapProject.getMapRelations() == null || StringUtils.isBlank(relationName)) {
            return "";
        }
        for (final MapRelation relation : mapProject.getMapRelations()) {
            if (relation.getName() != null && relation.getName().equalsIgnoreCase(relationName)) {
                return StringUtils.defaultString(relation.getTerminologyId());
            }
        }
        return "";
    }

    /**
     * Returns the module id from the map set, then the map project.
     *
     * @param mapSet the map set
     * @param mapProject the map project
     * @return the module id, or null
     */
    private static String moduleId(final MapSet mapSet, final MapProject mapProject) {

        if (mapSet != null && StringUtils.isNotBlank(mapSet.getModuleId())) {
            return mapSet.getModuleId();
        }
        if (mapProject != null && StringUtils.isNotBlank(mapProject.getModuleId())) {
            return mapProject.getModuleId();
        }
        return null;
    }

    /**
     * Returns a nested term field.
     *
     * @param conceptNode the concept node
     * @param field the field name
     * @return the term, or blank
     */
    private static String nestedTerm(final JsonNode conceptNode, final String field) {

        if (conceptNode == null || !conceptNode.has(field) || conceptNode.get(field) == null) {
            return "";
        }
        return text(conceptNode.get(field), "term");
    }

    /**
     * Returns trimmed text, or blank.
     *
     * @param node the node
     * @param field the field
     * @return the text
     */
    private static String text(final JsonNode node, final String field) {

        if (node == null || !node.hasNonNull(field)) {
            return "";
        }
        return StringUtils.trimToEmpty(node.get(field).asText());
    }

    /**
     * Returns the first non-blank value.
     *
     * @param values the values
     * @return the value, or blank
     */
    private static String firstNonBlank(final String... values) {

        if (values == null) {
            return "";
        }
        for (final String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value;
            }
        }
        return "";
    }

    /**
     * Returns a case-insensitive term key.
     *
     * @param term the term
     * @return the key
     */
    private static String termKey(final String term) {

        return StringUtils.isBlank(term) ? "" : term.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Title-cases a relation name the same way the editor does.
     *
     * @param value the value
     * @return the title-cased value
     */
    private static String titleCase(final String value) {

        if (StringUtils.isBlank(value)) {
            return "";
        }
        final String trimmed = value.trim();
        return Character.toUpperCase(trimmed.charAt(0)) + trimmed.substring(1).toLowerCase(Locale.ROOT);
    }

    /**
     * Returns distinct trimmed concept codes in submitted order.
     *
     * @param conceptCodes the concept codes
     * @return the distinct codes
     */
    static List<String> distinctCodes(final List<String> conceptCodes) {

        final LinkedHashSet<String> codes = new LinkedHashSet<>();
        if (conceptCodes != null) {
            for (final String conceptCode : conceptCodes) {
                if (StringUtils.isNotBlank(conceptCode)) {
                    codes.add(conceptCode.trim());
                }
            }
        }
        return new ArrayList<>(codes);
    }
}
