/*
 * Copyright 2024 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.rest.test.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.ihtsdo.refsetservice.model.AdditionalMapEntryInfo;
import org.ihtsdo.refsetservice.model.AuditEntry;
import org.ihtsdo.refsetservice.model.MapEntry;
import org.ihtsdo.refsetservice.model.Mapping;
import org.ihtsdo.refsetservice.util.AuditEntryHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Class AuditEntryHelperUnitTest.
 */
public class AuditEntryHelperUnitTest {

    /** The Constant LOG. */
    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(AuditEntryHelperUnitTest.class);

    /** The mapping. */
    private Mapping mapping;

    /** The map entry. */
    private MapEntry mapEntry;

    /** The old map entry. */
    private MapEntry oldMapEntry;

    /**
     * Sets the up.
     */
    @BeforeEach
    void setUp() {

        mapping = new Mapping();
        mapping.setCode("c12345");
        mapping.setName("Mapping Name");

        mapEntry = new MapEntry();
        mapEntry.setToCode("c67890");
        mapEntry.setToName("Target Name");
        mapEntry.setActive(true);
        mapEntry.setRelation("Related");
        mapEntry.setRule("Rule");
        mapEntry.setPriority(1);
        mapEntry.setGroup(1);
        mapEntry.setModuleId("m123456");
        mapEntry.setReleased(false);
        mapEntry.getAdvices().add("Advice 1");
        mapEntry.getAdvices().add("Advice 2");
        mapEntry.getAdditionalMapEntryInfos().add(new AdditionalMapEntryInfo("1", "name-1", "field-1", "value-1"));
        mapEntry.getAdditionalMapEntryInfos().add(new AdditionalMapEntryInfo("2", "name-2", "field-2", "value-2"));

        oldMapEntry = new MapEntry();
        oldMapEntry.setToCode("c67890");
        oldMapEntry.setToName("Target Name");
        oldMapEntry.setActive(true);
        oldMapEntry.setRelation("Related");
        oldMapEntry.setRule("Rule");
        oldMapEntry.setPriority(1);
        oldMapEntry.setGroup(1);
        oldMapEntry.setModuleId("m123456");
        oldMapEntry.setReleased(false);
        oldMapEntry.getAdvices().add("Advice 1");
        oldMapEntry.getAdvices().add("Advice 2");
        oldMapEntry.getAdditionalMapEntryInfos().add(new AdditionalMapEntryInfo("1", "name-1", "field-1", "value-1"));
        oldMapEntry.getAdditionalMapEntryInfos().add(new AdditionalMapEntryInfo("2", "name-2", "field-2", "value-2"));

    }

    /**
     * Test add mapping entry.
     */
    @Test
    void testAddMappingEntry() {

        final AuditEntry entry = AuditEntryHelper.addMappingEntry("refsetId", mapping, mapEntry);

        assertNotNull(entry);
        assertEquals("ADD MapEntry for concept c12345", entry.getMessage());
        assertEquals("Add map entry c12345 to c67890", entry.getDetails());

    }

    /**
     * Test update mapping entry.
     */
    @Test
    void testUpdateMappingEntryChangeTarget() {

        mapEntry.setToCode("c12345");
        mapEntry.setToName("New Target Name");
        final AuditEntry entry = AuditEntryHelper.updateMappingEntry("refsetId", mapping, mapEntry, oldMapEntry);

        assertNotNull(entry);
        assertEquals("UPDATE MapEntry for concept c12345", entry.getMessage());
        assertEquals("Target Code changed from c67890 to c12345, Target Name changed from Target Name to New Target Name", entry.getDetails());
    }

    /**
     * Test update mapping entry change related.
     */
    @Test
    void testUpdateMappingEntryChangeRelated() {

        mapEntry.setRelation("New Relation");
        final AuditEntry entry = AuditEntryHelper.updateMappingEntry("refsetId", mapping, mapEntry, oldMapEntry);
        assertEquals("UPDATE MapEntry for concept c12345", entry.getMessage());
        assertEquals("Relation changed from Related to New Relation", entry.getDetails());

    }

    /**
     * Test update mapping entry change rule.
     */
    @Test
    void testUpdateMappingEntryChangeRule() {

        mapEntry.setRule("New Rule");
        final AuditEntry entry = AuditEntryHelper.updateMappingEntry("refsetId", mapping, mapEntry, oldMapEntry);
        assertEquals("UPDATE MapEntry for concept c12345", entry.getMessage());
        assertEquals("Rule changed from Rule to New Rule", entry.getDetails());

    }

    /**
     * Test update mapping entry change priority.
     */
    @Test
    void testUpdateMappingEntryChangePriority() {

        mapEntry.setPriority(9);
        final AuditEntry entry = AuditEntryHelper.updateMappingEntry("refsetId", mapping, mapEntry, oldMapEntry);
        assertEquals("UPDATE MapEntry for concept c12345", entry.getMessage());
        assertEquals("Priority changed from 1 to 9", entry.getDetails());

    }

    /**
     * Test update mapping entry change group.
     */
    @Test
    void testUpdateMappingEntryChangeGroup() {

        mapEntry.setGroup(9);
        final AuditEntry entry = AuditEntryHelper.updateMappingEntry("refsetId", mapping, mapEntry, oldMapEntry);
        assertEquals("UPDATE MapEntry for concept c12345", entry.getMessage());
        assertEquals("Group changed from 1 to 9", entry.getDetails());

    }

    /**
     * Test update mapping entry change module id.
     */
    @Test
    void testUpdateMappingEntryChangeModuleId() {

        mapEntry.setModuleId("m987654");
        final AuditEntry entry = AuditEntryHelper.updateMappingEntry("refsetId", mapping, mapEntry, oldMapEntry);
        assertEquals("UPDATE MapEntry for concept c12345", entry.getMessage());
        assertEquals("Module Id changed from m123456 to m987654", entry.getDetails());

    }

    /**
     * Test update mapping entry change released.
     */
    @Test
    void testUpdateMappingEntryChangeReleased() {

        mapEntry.setReleased(true);
        final AuditEntry entry = AuditEntryHelper.updateMappingEntry("refsetId", mapping, mapEntry, oldMapEntry);
        assertEquals("UPDATE MapEntry for concept c12345", entry.getMessage());
        assertEquals("Released changed from false to true", entry.getDetails());

    }

    /**
     * Test update mapping entry add advice.
     */
    @Test
    void testUpdateMappingEntryAddAdvice() {

        mapEntry.getAdvices().add("Advice 3");
        final AuditEntry entry = AuditEntryHelper.updateMappingEntry("refsetId", mapping, mapEntry, oldMapEntry);
        assertEquals("UPDATE MapEntry for concept c12345", entry.getMessage());
        assertEquals("Advices changed from [Advice 1, Advice 2] to [Advice 1, Advice 3, Advice 2]", entry.getDetails());

    }

    /**
     * Test update mapping entry remove advice.
     */
    @Test
    void testUpdateMappingEntryRemoveAdvice() {

        mapEntry.getAdvices().remove("Advice 2");
        final AuditEntry entry = AuditEntryHelper.updateMappingEntry("refsetId", mapping, mapEntry, oldMapEntry);
        assertEquals("UPDATE MapEntry for concept c12345", entry.getMessage());
        assertEquals("Advices changed from [Advice 1, Advice 2] to [Advice 1]", entry.getDetails());

    }

    /**
     * Test update mapping entry change additional map entry info add.
     */
    @Test
    void testUpdateMappingEntryChangeAdditionalMapEntryInfoAdd() {

        mapEntry.getAdditionalMapEntryInfos().add(new AdditionalMapEntryInfo("9", "name-9", "field-9", "value-9"));
        final AuditEntry entry = AuditEntryHelper.updateMappingEntry("refsetId", mapping, mapEntry, oldMapEntry);
        assertEquals("UPDATE MapEntry for concept c12345", entry.getMessage());
        assertEquals("Additional Map Entry Details changes: Added: field-9=value-9;", entry.getDetails());

    }

    /**
     * Test update mapping entry change additional map entry info update.
     */
    @Test
    void testUpdateMappingEntryChangeAdditionalMapEntryInfoUpdate() {

        final AdditionalMapEntryInfo additionalMapEntryInfo = mapEntry.getAdditionalMapEntryInfos().stream().findFirst().get();
        additionalMapEntryInfo.setField("field-update");
        additionalMapEntryInfo.setValue("value-update");
        additionalMapEntryInfo.setName("name-update");

        final AuditEntry entry = AuditEntryHelper.updateMappingEntry("refsetId", mapping, mapEntry, oldMapEntry);
        assertEquals("UPDATE MapEntry for concept c12345", entry.getMessage());
        assertEquals("Additional Map Entry Details changes: Added: field-update=value-update; Removed: field-1=value-1;", entry.getDetails());

    }

    /**
     * Test update mapping entry change additional map entry info remove.
     */
    @Test
    void testUpdateMappingEntryChangeAdditionalMapEntryInfoRemove() {

        mapEntry.getAdditionalMapEntryInfos().remove(mapEntry.getAdditionalMapEntryInfos().stream().reduce((first, second) -> second).orElse(null));
        final AuditEntry entry = AuditEntryHelper.updateMappingEntry("refsetId", mapping, mapEntry, oldMapEntry);
        assertEquals("UPDATE MapEntry for concept c12345", entry.getMessage());
        assertEquals("Additional Map Entry Details changes: Removed: field-2=value-2;", entry.getDetails());

    }

    /**
     * Test status change mapping entry.
     */
    @Test
    void testStatusChangeMappingEntry() {

        mapEntry.setActive(true);
        AuditEntry entry = AuditEntryHelper.statusChangeMappingEntry("refsetId", mapping, mapEntry);

        assertNotNull(entry);
        assertEquals("UPDATE MapEntry for concept c12345", entry.getMessage());
        assertEquals("Map entry for concept c12345 activated.", entry.getDetails());

        mapEntry.setActive(false);
        entry = AuditEntryHelper.statusChangeMappingEntry("refsetId", mapping, mapEntry);

        assertNotNull(entry);
        assertEquals("UPDATE MapEntry for concept c12345", entry.getMessage());
        assertEquals("Map entry for concept c12345 inactivated.", entry.getDetails());

    }

    /**
     * Test delete mapping entry.
     */
    @Test
    void testDeleteMappingEntry() {

        final AuditEntry entry = AuditEntryHelper.deleteMappingEntry("refsetId", mapping, mapEntry);

        assertNotNull(entry);
        assertEquals("DELETE MapEntry for concept c12345", entry.getMessage());
        assertEquals("Delete map entry c12345 mapped to c67890.", entry.getDetails());
    }
}
