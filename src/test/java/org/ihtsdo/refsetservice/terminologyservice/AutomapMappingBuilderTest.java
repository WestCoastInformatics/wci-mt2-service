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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.ihtsdo.refsetservice.model.MapEntry;
import org.ihtsdo.refsetservice.model.MapProject;
import org.ihtsdo.refsetservice.model.MapRelation;
import org.ihtsdo.refsetservice.model.MapSet;
import org.ihtsdo.refsetservice.model.Mapping;
import org.ihtsdo.refsetservice.util.ThreadLocalMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Automap result shaping. These tests do not call Snowstorm or the automap service.
 */
public class AutomapMappingBuilderTest {

    @Test
    public void highestConfidenceBecomesTheMapAndZeroCountBecomesNoTarget() throws Exception {

        final String response = """
            {
              "terms": [
                {
                  "code": "999",
                  "term": "Not a thing",
                  "mappingCt": 0,
                  "mappings": [{ "code": "should-ignore", "name": "Ignored", "confidence": 0.99 }]
                },
                {
                  "code": "22298006",
                  "term": "Heart attack",
                  "mappingCt": 2,
                  "mappings": [
                    { "code": "57054005", "name": "Acute myocardial infarction", "confidence": 0.81 },
                    { "code": "22298006", "name": "Myocardial infarction", "confidence": 0.95 }
                  ]
                }
              ]
            }
            """;
        final JsonNode task = ThreadLocalMapper.get().readTree(response);
        final List<Mapping> mappings = AutomapMappingBuilder.buildMappings(mapSet(), List.of("22298006", "999", "111"),
            Map.of("22298006", "Heart attack", "999", "Not a thing", "111", "Missing"), task);

        assertEquals(3, mappings.size());
        assertEquals("22298006", mappings.get(0).getCode());
        assertEquals("Heart attack", mappings.get(0).getName());
        assertEquals("map-set-1", mappings.get(0).getMapSetId());
        final MapEntry mapped = mappings.get(0).getMapEntries().get(0);
        assertEquals("22298006", mapped.getToCode());
        assertEquals("Myocardial infarction", mapped.getToName());
        assertEquals("Exact match", mapped.getRelation());
        assertEquals("447637006", mapped.getRelationCode());
        assertEquals("TRUE", mapped.getRule());
        assertEquals(1, mapped.getGroup());
        assertEquals(1, mapped.getPriority());
        assertEquals("51000202101", mapped.getModuleId());
        assertTrue(mapped.getAdvices().contains("ALWAYS 22298006"));

        assertNoTarget(mappings.get(1));
        assertNoTarget(mappings.get(2));
    }

    @Test
    public void equalConfidenceKeepsTheFirstMapping() throws Exception {

        final String response = """
            {
              "terms": [{
                "code": "1",
                "mappingCt": 2,
                "mappings": [
                  { "code": "first", "name": "First", "confidence": 0.9 },
                  { "code": "second", "name": "Second", "confidence": 0.9 }
                ]
              }]
            }
            """;
        final List<Mapping> mappings =
            AutomapMappingBuilder.buildMappings(mapSet(), List.of("1"), Map.of("1", "Term"), ThreadLocalMapper.get().readTree(response));

        assertEquals("first", mappings.get(0).getMapEntries().get(0).getToCode());
    }

    @Test
    public void preferredTermIsUsedBeforeTheFullySpecifiedName() throws Exception {

        final JsonNode concept = ThreadLocalMapper.get().readTree("""
            { "conceptId": "22298006", "pt": { "term": "Heart attack" }, "fsn": { "term": "Myocardial infarction (disorder)" } }
            """);

        assertEquals("22298006", AutomapMappingBuilder.conceptCode(concept));
        assertEquals("Heart attack", AutomapMappingBuilder.termFromConceptNode(concept));
    }

    @Test
    public void taskJsonSendsTermsAndTargetTerminology() throws Exception {

        final String json = AutomapClient.buildTaskJson(
            List.of(new AutomapTerm("Heart attack", "condition"), new AutomapTerm("Diabetes", "condition")), 0.8d);
        final JsonNode task = ThreadLocalMapper.get().readTree(json);
        final JsonNode term = task.get("terms").get(0);

        assertEquals(0.8d, task.get("minConfidence").asDouble());
        assertEquals(2, task.get("terms").size());
        assertEquals("Heart attack", term.get("term").asText());
        assertEquals("SNOMEDCT", term.get("toTerminology").asText());
        assertEquals("condition", term.get("entityType").asText());
        assertFalse(term.has("code"));
        assertFalse(term.has("terminology"));
    }

    @Test
    public void mapToSnomedNoTargetIsNotStored() {

        final MapSet mapSet = mapSet();
        mapSet.setFromTerminology("ICD10NO");
        mapSet.setToTerminology("SNOMEDCT");
        final Mapping target = mapping("A00", "22298006");
        final Mapping noTarget = mapping("A01", "");

        assertEquals(List.of(target), AutomapService.mappingsToSave(mapSet, List.of(target, noTarget)));
    }

    @Test
    public void noTargetIsStoredWhenTheTargetIsNotSnomed() {

        final MapSet mapSet = mapSet();
        mapSet.setFromTerminology("SNOMEDCT");
        mapSet.setToTerminology("ICD10NO");
        final Mapping noTarget = mapping("22298006", "");

        assertEquals(List.of(noTarget), AutomapService.mappingsToSave(mapSet, List.of(noTarget)));
    }

    @Test
    public void defaultsAndValidation() {

        assertEquals(0.5d, AutomapService.resolveMinConfidence(0.5d));
        assertEquals("procedure", AutomapService.resolveEntityType(" procedure "));
        assertEquals(List.of("22298006", "73211009"), AutomapMappingBuilder.distinctCodes(List.of(" 22298006 ", "", "22298006", "73211009")));

        final ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> AutomapService.resolveMinConfidence(1.5d));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
    }

    @Test
    public void expiresAtTreatsShortValuesAsSeconds() throws Exception {

        final long now = System.currentTimeMillis();
        final JsonNode seconds = ThreadLocalMapper.get().readTree("{\"expires_in\":3600}");
        final long fromSeconds = AutomapClient.expiresAt(seconds);
        assertTrue(fromSeconds > now + 3_500_000L);
        assertTrue(fromSeconds < now + 3_600_000L);

        final JsonNode epochSeconds = ThreadLocalMapper.get().readTree("{\"expires_on\":2000000000}");
        assertEquals(1_999_999_970_000L, AutomapClient.expiresAt(epochSeconds));
    }

    /**
     * Returns a mapping with one map entry.
     *
     * @param code the source code
     * @param toCode the target code
     * @return the mapping
     */
    private Mapping mapping(final String code, final String toCode) {

        final MapEntry entry = new MapEntry();
        entry.setToCode(toCode);
        final Mapping mapping = new Mapping();
        mapping.setCode(code);
        mapping.getMapEntries().add(entry);
        return mapping;
    }

    /**
     * Asserts a no-target map entry.
     *
     * @param mapping the mapping
     */
    private void assertNoTarget(final Mapping mapping) {

        final MapEntry entry = mapping.getMapEntries().get(0);
        assertEquals("", entry.getToCode());
        assertEquals(AutomapMappingBuilder.NO_TARGET_NAME, entry.getToName());
        assertEquals("Not classifiable", entry.getRelation());
        assertEquals("447638001", entry.getRelationCode());
        assertTrue(entry.getAdvices().isEmpty());
    }

    /**
     * Returns a map set with one target relation and one no-target relation.
     *
     * @return the map set
     */
    private MapSet mapSet() {

        final MapRelation exact = relation("EXACT MATCH", "447637006", false);
        final MapRelation noTarget = relation("NOT CLASSIFIABLE", "447638001", true);
        final Set<MapRelation> relations = new LinkedHashSet<>();
        relations.add(exact);
        relations.add(noTarget);

        final MapProject mapProject = new MapProject();
        mapProject.setRuleBased(false);
        mapProject.setModuleId("900000000000207008");
        mapProject.setMapRelations(relations);

        final MapSet mapSet = new MapSet();
        mapSet.setId("map-set-1");
        mapSet.setModuleId("51000202101");
        mapSet.setMapProject(mapProject);
        return mapSet;
    }

    /**
     * Returns a map relation.
     *
     * @param name the name
     * @param terminologyId the terminology id
     * @param nullTarget true when the relation is for an empty target
     * @return the relation
     */
    private MapRelation relation(final String name, final String terminologyId, final boolean nullTarget) {

        final MapRelation relation = new MapRelation();
        relation.setName(name);
        relation.setTerminologyId(terminologyId);
        relation.setAllowableForNullTarget(nullTarget);
        return relation;
    }
}
