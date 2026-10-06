package org.ihtsdo.refsetservice.handler.snowstorm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.ihtsdo.refsetservice.model.Description;
import org.ihtsdo.refsetservice.model.MapEntry;
import org.ihtsdo.refsetservice.model.MapProject;
import org.ihtsdo.refsetservice.model.MapSet;
import org.ihtsdo.refsetservice.model.Mapping;
import org.ihtsdo.refsetservice.util.LocalException;
import org.ihtsdo.refsetservice.util.ThreadLocalMapper;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.hibernate5.Hibernate5Module;

/**
 * Member JSON for maps to SNOMED CT (1193543008 |Simple map with correlation to SNOMED CT type reference set|).
 */
public class SnowstormMapToSnomedMemberTest {

    @Test
    public void directionFollowsProjectTerminologies() {

        final MapSet mapSet = new MapSet();
        mapSet.setFromTerminology("ICPC2");
        mapSet.setToTerminology("SNOMEDCT");
        assertTrue(SnowstormMapping.isMapToSnomed(mapSet, null));

        mapSet.setFromTerminology("SNOMEDCT");
        mapSet.setToTerminology("ICD10");
        assertFalse(SnowstormMapping.isMapToSnomed(mapSet, null));

        final MapProject mapProject = new MapProject();
        mapProject.setSourceTerminology("ICD10");
        mapProject.setDestinationTerminology("SNOMEDCT-NO");
        assertTrue(SnowstormMapping.isMapToSnomed(new MapSet(), mapProject));
    }

    @Test
    public void savePutsSnomedConceptOnReferencedComponentAndSourceOnMapSource() throws Exception {

        final MapSet mapSet = mapToSnomedSet();
        final MapEntry mapEntry = mapEntry("22298006");
        final String json = SnowstormMapping.mapEntryToSnowstormMap(new MapProject(), "999", "A01", "Abdominal pain", mapEntry, mapSet);
        final JsonNode member = ThreadLocalMapper.get().readTree(json);

        assertEquals("22298006", member.get("referencedComponentId").asText());
        assertEquals("A01", member.get("additionalFields").get("mapSource").asText());
        assertEquals(SnomedConstants.MAP_TO_SNOMED_EXACT_MATCH, member.get("additionalFields").get("correlationId").asText());
        assertFalse(member.get("additionalFields").has("mapTarget"));
        assertFalse(member.get("additionalFields").has("mapGroup"));
        assertFalse(member.get("additionalFields").has("mapRule"));
        assertFalse(member.get("additionalFields").has("mapAdvice"));
        assertEquals("A01", SnowstormMapping.memberSourceCode(member, true));
    }

    @Test
    public void saveKeepsSnomedSourceOnReferencedComponent() throws Exception {

        final MapSet mapSet = new MapSet();
        mapSet.setFromTerminology("SNOMEDCT");
        mapSet.setToTerminology("ICD10");
        mapSet.setModuleId("51000202101");
        final MapEntry mapEntry = mapEntry("R07.4");
        mapEntry.setRule("TRUE");
        mapEntry.setGroup(1);
        mapEntry.setPriority(1);

        final String json = SnowstormMapping.mapEntryToSnowstormMap(new MapProject(), "447562003", "22298006", "Pain", mapEntry, mapSet);
        final JsonNode member = ThreadLocalMapper.get().readTree(json);

        assertEquals("22298006", member.get("referencedComponentId").asText());
        assertEquals("R07.4", member.get("additionalFields").get("mapTarget").asText());
        assertFalse(member.get("additionalFields").has("mapSource"));
    }

    @Test
    public void loadReadsSnomedTargetFromReferencedComponent() throws Exception {

        final String json = SnowstormMapping.mapToSnomedMemberJson("999", "A01", mapEntry("22298006"), "51000202101");
        final JsonNode member = ThreadLocalMapper.get().readTree(json);
        ((com.fasterxml.jackson.databind.node.ObjectNode) member).put("released", false);

        final MapEntry loaded = SnowstormMapping.convertSnowstormMemberToMapEntry(member, mapToSnomedSet(), null, null);

        assertEquals("22298006", loaded.getToCode());
        assertEquals("", loaded.getRule());
    }

    @Test
    public void blankTargetIsRejected() {

        final MapSet mapSet = mapToSnomedSet();
        final LocalException error = assertThrows(LocalException.class,
            () -> SnowstormMapping.mapEntryToSnowstormMap(new MapProject(), "999", "A01", "Abdominal pain", mapEntry(""), mapSet));
        assertTrue(error.getMessage().contains("SNOMED CT target"));
    }

    @Test
    public void searchBodyFiltersMapSource() throws Exception {

        final JsonNode body = ThreadLocalMapper.get().readTree(SnowstormMapping.mapToSnomedMemberSearchBody("999", List.of("A01", "R07.4")));

        assertEquals("999", body.get("referenceSet").asText());
        assertEquals("A01", body.get("additionalFieldSets").get("mapSource").get(0).asText());
        assertEquals("R07.4", body.get("additionalFieldSets").get("mapSource").get(1).asText());
        assertFalse(body.has("referencedComponentIds"));
    }

    @Test
    public void unchangedTargetsAreTheSameMap() {

        final Mapping existing = new Mapping();
        existing.setMapEntries(List.of(mapEntry("22298006"), mapEntry("386661006")));
        final Mapping submitted = new Mapping();
        final MapEntry retitled = mapEntry("386661006");
        retitled.setRule("IFA pain");
        retitled.setGroup(2);
        submitted.setMapEntries(List.of(mapEntry("22298006"), retitled));

        assertTrue(SnowstormMapping.sameMapToSnomedTargets(submitted, existing));
    }

    @Test
    public void slotsAreStableForTheSameTargets() {

        final Mapping mapping = new Mapping();
        mapping.setMapEntries(List.of(mapEntry("386661006"), mapEntry("22298006")));
        SnowstormMapping.assignMapToSnomedSlots(mapping);

        assertEquals("22298006", mapping.getMapEntries().get(0).getToCode());
        assertEquals(1, mapping.getMapEntries().get(0).getPriority());
        assertEquals("386661006", mapping.getMapEntries().get(1).getToCode());
        assertEquals(2, mapping.getMapEntries().get(1).getPriority());
        assertEquals(1, mapping.getMapEntries().get(0).getGroup());
    }

    @Test
    public void targetDescriptionsAreOnTheMapEntry() throws Exception {

        final Description description = new Description();
        description.setTerm("Radiating chest pain");
        description.setLanguage("en");
        description.setType("SYNONYM");
        description.setConceptId("10000006");

        final MapEntry target = mapEntry("10000006");
        final MapEntry blank = mapEntry("");
        final Mapping mapping = new Mapping();
        mapping.setCode("K03.0");
        mapping.setMapEntries(List.of(target, blank));

        SnowstormMapping.applyTargetDescriptions(List.of(mapping), Map.of("10000006", List.of(description)));

        assertEquals(1, target.getDescriptions().size());
        assertEquals("Radiating chest pain", target.getDescriptions().get(0).getTerm());
        assertTrue(blank.getDescriptions().isEmpty());
        assertTrue(mapping.getDescriptions().isEmpty());

        final ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new Hibernate5Module());
        final JsonNode node = mapper.readTree(mapper.writeValueAsString(mapping));
        assertFalse(node.has("descriptions"));
        final JsonNode entries = node.get("mapEntries");
        assertEquals("Radiating chest pain", entries.get(0).get("descriptions").get(0).get("term").asText());
        assertFalse(entries.get(1).has("descriptions"));
    }

    private static MapSet mapToSnomedSet() {

        final MapSet mapSet = new MapSet();
        mapSet.setFromTerminology("ICPC2");
        mapSet.setToTerminology("SNOMEDCT");
        mapSet.setModuleId("51000202101");
        return mapSet;
    }

    private static MapEntry mapEntry(final String toCode) {

        final MapEntry mapEntry = new MapEntry();
        mapEntry.setToCode(toCode);
        mapEntry.setToName("");
        mapEntry.setRule("");
        mapEntry.setRelation("");
        mapEntry.setRelationCode("");
        mapEntry.setAdvices(new java.util.HashSet<>());
        mapEntry.setActive(true);
        return mapEntry;
    }
}
