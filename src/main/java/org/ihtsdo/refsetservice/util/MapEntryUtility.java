/*
 * Copyright 2024 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.util;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.refsetservice.model.MapEntry;
import org.ihtsdo.refsetservice.model.MapProject;
import org.ihtsdo.refsetservice.model.MapRelation;
import org.ihtsdo.refsetservice.model.Mapping;

public class MapEntryUtility {

    /**
     * Instantiates a new map entry utility.
     */
    private MapEntryUtility() {

        // Utility class, no instantiation
    }

    /**
     * Sort map entries.
     *
     * @param mapping the mapping
     */
    public static void sortMapEntries(final Mapping mapping) {

        final List<MapEntry> entries = mapping.getMapEntries();
        entries.sort(Comparator.comparingInt(MapEntry::getGroup).thenComparingInt(MapEntry::getPriority));

        mapping.setMapEntries(entries);
    }

    /**
     * Return true if maps have equivalent content. This only considers the map information: target, advice, relations, etc. This does not compare other
     * information: release date, last modified, etc. Note: this intentionally ignores moduleId, so we can check edition maps against international maps
     *
     * @param mapping1 the mapping 1
     * @param mapping2 the mapping 2
     * @return the boolean
     */
    public static Boolean areMapsEquivalent(final Mapping mapping1, final Mapping mapping2) {

        // Check for null mappings
        if ((mapping1 == null && mapping2 == null)) {
            return true;
        }
        if ((mapping1 == null || mapping2 == null)) {
            return false;
        }

        // Check top-level mapping information
        if (!(mapping1.getCode().equals(mapping2.getCode()))) {
            return false;
        }

        // Check for null map entries
        if (mapping1.getMapEntries() == null && mapping2.getMapEntries() == null) {
            return true;
        }
        if (mapping1.getMapEntries() == null || mapping2.getMapEntries() == null) {
            return false;
        }

        // Check for map-entry count
        if (!(mapping1.getMapEntries().size() == mapping2.getMapEntries().size())) {
            return false;
        }

        // Sort the map entries for both mappings in Group/Priority order
        sortMapEntries(mapping1);
        sortMapEntries(mapping2);

        // Check individual map entry information
        // Since the map entries were sorted, we can compare entries by index location.
        for (int i = 0; i < mapping1.getMapEntries().size(); i++) {
            if (!areMapEntriesEquivalent(mapping1.getMapEntries().get(i), mapping2.getMapEntries().get(i))) {
                return false;
            }
        }

        return true;
    }

    /**
     * Return true if map entries have equivalent content. This only considers the map information: target, advice, relations, etc. This does not compare other
     * information: release date, last modified, etc. Note: this intentionally ignores moduleId, so we can check edition maps against international maps
     *
     * @param mapEntry1 the map entry 1
     * @param mapEntry2 the map entry 2
     * @return the boolean
     */
    public static Boolean areMapEntriesEquivalent(final MapEntry mapEntry1, final MapEntry mapEntry2) {

        Boolean mapEntriesEquivalent = true;

        // Check for null map entries
        if (mapEntry1 == null && mapEntry2 == null) {
            return true;
        }
        if (mapEntry1 == null || mapEntry2 == null) {
            return false;
        }

        // Check individual map entry information
        mapEntriesEquivalent = mapEntry1.getGroup() == mapEntry2.getGroup() && mapEntry1.getPriority() == mapEntry2.getPriority()
            && Objects.equals(mapEntry1.getAdditionalMapEntryInfos(), mapEntry2.getAdditionalMapEntryInfos())
            && Objects.equals(mapEntry1.getAdvices(), mapEntry2.getAdvices()) && mapEntry1.getBlock() == mapEntry2.getBlock()
            && Objects.equals(mapEntry1.getRelationCode(), mapEntry2.getRelationCode()) && Objects.equals(mapEntry1.getRule(), mapEntry2.getRule())
            && Objects.equals(mapEntry1.getToCode(), mapEntry2.getToCode());

        return mapEntriesEquivalent;
    }

    /**
     * If two map entries have the same group, priority, target, and rule, then they represent the same object (i.e. will have the same UUID in snowstorm). This
     * determines whether changes should update an existing map entry, or create a new one.
     * 
     *
     * @param mapEntry1 the map entry 1
     * @param mapEntry2 the map entry 2
     * @return the boolean
     */
    public static Boolean doMapEntriesShareUUID(MapEntry mapEntry1, MapEntry mapEntry2) {

        Boolean mapEntriesShareUUID = true;

        // Check for null map entries
        if (mapEntry1 == null && mapEntry2 == null) {
            return true;
        }
        if (mapEntry1 == null || mapEntry2 == null) {
            return false;
        }

        // Check individual map entry information
        mapEntriesShareUUID =
            mapEntry1.getGroup() == mapEntry2.getGroup() && mapEntry1.getPriority() == mapEntry2.getPriority() && mapEntry1.getBlock() == mapEntry2.getBlock()
                && Objects.equals(mapEntry1.getRule(), mapEntry2.getRule()) && Objects.equals(mapEntry1.getToCode(), mapEntry2.getToCode());

        return mapEntriesShareUUID;
    }

    /**
     * Update an existing map entry with the non-defining content of the submitted map entry This can only be done on map entries that share a UUID
     * 
     *
     * @param existingMapEntry the existing map entry
     * @param submittedMapEntry the submitted map entry
     * @return the map entry
     */
    public static MapEntry updateExistingMapEntry(MapEntry existingMapEntry, MapEntry submittedMapEntry) throws Exception {

        if (!doMapEntriesShareUUID(existingMapEntry, submittedMapEntry)) {
            throw new Exception("You cannot update an existing map entry with a non UUID-sharing new entry");
        }

        existingMapEntry.setAdditionalMapEntryInfos(submittedMapEntry.getAdditionalMapEntryInfos());
        existingMapEntry.setAdvices(submittedMapEntry.getAdvices());
        existingMapEntry.setRelation(submittedMapEntry.getRelation());
        existingMapEntry.setRelationCode(submittedMapEntry.getRelationCode());

        return existingMapEntry;
    }

    /**
     * Fix map entry advices.
     *
     * @param mapEntry the map entry
     * @return the sets the
     */
    public static Set<String> fixMapEntryAdvices(final MapEntry mapEntry) {

        // Handle "ALWAYS {target}" advices
        // TODO - this will be different for RULE-based projects
        final Set<String> mapEntryAdvices = new HashSet<>();
        mapEntryAdvices.addAll(mapEntry.getAdvices());
        boolean alwaysAdviceFound = false;
        for (final String advice : mapEntry.getAdvices()) {
            if (advice.startsWith("ALWAYS ")) {
                alwaysAdviceFound = true;
                // If no current target code, remove ALWAYS advice
                if (StringUtils.isBlank(mapEntry.getToCode())) {
                    mapEntryAdvices.remove(advice);
                }
                // If ALWAYS advice doesn't end with the current target code, remove and replace it.
                else if (!advice.endsWith(mapEntry.getToCode())) {
                    mapEntryAdvices.remove(advice);
                    mapEntryAdvices.add("ALWAYS " + mapEntry.getToCode());
                }
            }
        }
        // If there is a specified target and no ALWAYS advice found, add it
        if (!alwaysAdviceFound && !StringUtils.isBlank(mapEntry.getToCode())) {
            mapEntryAdvices.add("ALWAYS " + mapEntry.getToCode());
        }

        return mapEntryAdvices;
    }

    /**
     * Calculate map entry relation code.
     *
     * @param mapProject the map project
     * @param mapEntry the map entry
     * @return the string
     */
    public static String calculateMapEntryRelationCode(final MapProject mapProject, final MapEntry mapEntry) {

        // Calculate a map's relation code
        String relationCode = "";
        if (mapEntry.getRelation() != null) {
            final String relationString = mapEntry.getRelation();

            for (final MapRelation mapRelation : mapProject.getMapRelations()) {
                if (mapRelation.getName().toUpperCase().equals(relationString.toUpperCase())) {
                    relationCode = mapRelation.getTerminologyId();
                    break;
                }
            }
        }
        return relationCode;
    }

}
