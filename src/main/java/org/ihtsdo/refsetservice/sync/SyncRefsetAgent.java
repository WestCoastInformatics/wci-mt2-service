/*
 * Copyright 2023 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.sync;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status.Family;

import org.ihtsdo.refsetservice.model.DefinitionClause;
import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.model.VersionStatus;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.sync.util.SyncRefsetMetadata;
import org.ihtsdo.refsetservice.terminologyservice.OrganizationService;
import org.ihtsdo.refsetservice.terminologyservice.RefsetMemberService;
import org.ihtsdo.refsetservice.terminologyservice.RefsetService;
import org.ihtsdo.refsetservice.terminologyservice.SnowstormConnection;
import org.ihtsdo.refsetservice.terminologyservice.WorkflowService;
import org.ihtsdo.refsetservice.util.CrowdGroupNameAlgorithm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The Class SyncRefsetAgent.
 */
public class SyncRefsetAgent extends SyncAgent {

    /** The Constant JAN_FIRST_2016. */
    private static final long PRE_SNOMED_SUPPORTED_RELEASES = 1451635200000L;

    /** The log. */
    private static final Logger LOG = LoggerFactory.getLogger(SyncRefsetAgent.class);

    /** The refset to module map. */
    private final Map<String, String> refsetToModuleMap = new HashMap<String, String>();

    /** The termserver refset id to refset versions data map. */
    private Map<String, Map<Long, SyncRefsetMetadata>> termserverRefsetIdToRefsetVersionsDataMap;

    /** The newly created and unchanged refset to versions map. */
    private Map<String, Set<Long>> newlyCreatedAndUnchangedRefsetToVersionsMap = new HashMap<>();

    /** The refset project map. */
    private final Map<String, Project> refsetProjectMap = new HashMap<>();

    /** The default organization project map. */
    private final Map<String, Project> defaultOrganizationProjectMap = new HashMap<>();

    private final Set<JsonNode> filteredCodeSystems;

    public SyncRefsetAgent(Set<JsonNode> filteredCodeSystems) {

        this.filteredCodeSystems = filteredCodeSystems;
    }

    /* see superclass */
    @Override
    public void syncComponent(final TerminologyService service) throws Exception {

        LOG.info("Starting sync of Refset Agent");

        initializeSync();

        // Determine code systems to sync
        final Set<SyncRefsetMetadata> filteredRefsets = analyzeCodeSystemBranches(service);

        if (filteredRefsets != null && !filteredRefsets.isEmpty()) {

            final List<String> addedOrInactivatedRefsetIds = analyzeRefsetsIds(service);

            analyzeRefsetVersions(service, addedOrInactivatedRefsetIds);

            // Final step for project connection
            finalizeNewOrChangedRefsets(service);

        }

    }

    /**
     * Analyze refsets ids.
     *
     * @param service the service
     * @return the list
     * @throws Exception the exception
     */
    private List<String> analyzeRefsetsIds(final TerminologyService service) throws Exception {

        LOG.info("analyze refsetIds");

        final List<String> addedOrInactivatedRefsetIds = new ArrayList<>();
        final Set<String> termserverRefsetIds = termserverRefsetIdToRefsetVersionsDataMap.keySet();
        STATISTICS.setRefsetIdsSynced(termserverRefsetIds.size());

        Map<String, Map<Long, Refset>> dbActiveRefsetIdToVersionRefsetMap = new HashMap<>();
        Map<String, Map<Long, Refset>> dbInactiveRefsetIdToVersionRefsetMap = new HashMap<>();
        populateRefsetToVersions(service, dbActiveRefsetIdToVersionRefsetMap, dbInactiveRefsetIdToVersionRefsetMap);

        // Determine new, inactivated, and existing refsets (Based on refsetId and version/branch info)
        // Determine and create new refsets (where db versions are needed). These are identified by those not in active nor in inactive DB refsets)
        final List<String> addedRefsetSctIds = termserverRefsetIds.stream()
            .filter(refsetId -> !dbActiveRefsetIdToVersionRefsetMap.containsKey(refsetId) && !dbInactiveRefsetIdToVersionRefsetMap.containsKey(refsetId)).collect(Collectors.toList());

        addMultipleRefsets(service, addedRefsetSctIds);
        STATISTICS.setRefsetIdsAdded(addedRefsetSctIds.size());

        addedRefsetSctIds.stream().filter(refsetId -> termserverRefsetIdToRefsetVersionsDataMap.containsKey(refsetId))
            .forEach(refsetId -> newlyCreatedAndUnchangedRefsetToVersionsMap.put(refsetId, termserverRefsetIdToRefsetVersionsDataMap.get(refsetId).keySet()));

        // Activate previously inactivated refsets. Note: Will log and update stats after remove those that were activatedAndModified\

        // TODO: No reason to support activated/inactivated in sync as will be handled strictly within RT2 DB?
        /*
         * final List<String> activatedRefsetSctIds = termserverRefsetIds.stream() .filter(refsetId -> dbInactiveRefsetIdToVersionRefsetMap.containsKey(refsetId) &&
         * (!dbActiveRefsetIdToVersionRefsetMap.containsKey(refsetId) || (dbActiveRefsetIdToVersionRefsetMap.get(refsetId).keySet().stream().noneMatch(version ->
         * dbActiveRefsetIdToVersionRefsetMap.get(refsetId).get(version).isActive())))) .collect(Collectors.toList());
         * 
         * activatedRefsetSctIds.stream().forEach(refsetId -> getDbHandler().updateRefsetStatusAllVersions(service, refsetId, true));
         * STATISTICS.setRefsetIdsActivated(activatedRefsetSctIds.size());
         * 
         * // Inactivate active DB refsets that are not in termserver final Map<String, Set<Long>> inactivatedRefsetIds = new HashMap<>();
         * 
         * for (final String refsetId : dbActiveRefsetIdToVersionRefsetMap.keySet()) {
         * 
         * if (isTesting() && getTestingRefset() != null && !getTestingRefset().equals(refsetId)) { continue; }
         * 
         * for (final long dbVersion : dbActiveRefsetIdToVersionRefsetMap.get(refsetId).keySet()) {
         * 
         * if (!termserverRefsetIdToRefsetVersionsDataMap.containsKey(refsetId) ||
         * termserverRefsetIdToRefsetVersionsDataMap.get(refsetId).keySet().stream().noneMatch(tsVersion -> isRefsetVersionMatches(tsVersion, dbVersion))) {
         * 
         * // Only consider published refsets for inactivation (when not found on termserver) as anything else is still strictly managed within RT2 if
         * (VersionStatus.PUBLISHED.getLabel().equals(dbActiveRefsetIdToVersionRefsetMap.get(refsetId).get(dbVersion).getVersionStatus())) {
         * 
         * if (!inactivatedRefsetIds.containsKey(refsetId)) { inactivatedRefsetIds.put(refsetId, new HashSet<>()); }
         * 
         * inactivatedRefsetIds.get(refsetId).add(dbVersion); } }
         * 
         * } }
         * 
         * inactivatedRefsetIds.keySet().stream() .forEach(refsetId -> inactivatedRefsetIds.get(refsetId).stream().forEach(version ->
         * getDbHandler().updateRefsetVersionStatus(service, refsetId, version, false))); STATISTICS.setRefsetIdsInactivated(inactivatedRefsetIds.size());
         * 
         * // Determine refsetIds that were just activated to see if there are any other changes necessary final List<String> activatedAndModifiedRefsetIds = new
         * ArrayList<>();
         * 
         * final List<Refset> dbRefsets = getAllPublishedRefsets(service);
         * 
         * for (final String refsetId : activatedRefsetSctIds) {
         * 
         * final List<Long> activatedVersionDates = new ArrayList<>(termserverRefsetIdToRefsetVersionsDataMap.get(refsetId).keySet());
         * java.util.Collections.sort(activatedVersionDates);
         * 
         * final List<Long> activatedAndModifiedVersionDates = compareAndModifyRefsetVersions(service, dbRefsets, refsetId, activatedVersionDates,
         * termserverRefsetIdToRefsetVersionsDataMap.get(refsetId));
         * 
         * if (!activatedAndModifiedVersionDates.isEmpty()) {
         * 
         * activatedAndModifiedRefsetIds.add(refsetId); } }
         * 
         * // Finalize those refset versions that were only activated (and not further modified) activatedAndModifiedRefsetIds.stream().forEach(n ->
         * activatedRefsetSctIds.remove(n));
         * 
         * addedOrInactivatedRefsetIds.addAll(inactivatedRefsetIds.keySet());
         */

        addedOrInactivatedRefsetIds.addAll(addedRefsetSctIds);

        return addedOrInactivatedRefsetIds;
    }

    /**
     * Returns the all published refsets.
     *
     * @param service the service
     * @return the all published refsets
     * @throws Exception the exception
     */
    private List<Refset> getAllPublishedRefsets(final TerminologyService service) throws Exception {

        List<Refset> dbRefsets = service.getAll(Refset.class);
        return dbRefsets.stream().filter(r -> VersionStatus.PUBLISHED.getLabel().equals(r.getVersionStatus())).collect(Collectors.toList());
    }

    /**
     * Analyze refset versions.
     *
     * @param service the service
     * @param addedOrInactivatedRefsetIds the added or inactivated refset ids
     * @throws Exception the exception
     */
    private void analyzeRefsetVersions(final TerminologyService service, final List<String> addedOrInactivatedRefsetIds) throws Exception {

        // Having identified new refsets (where db versions are new), as well as activated/inactivated refsets (where once again db versions are new), now check
        // refset
        // versions.
        // At end, also see with making newlyActivated versions as something to compare 1:1.
        // Perform analysis on one version at a time.
        // Dev note: Stream ignores those that are listed in the new or inactivated refsetId list (activated will be processed for changes)

        if (addedOrInactivatedRefsetIds.isEmpty()) {

            // Nothing to do if there are no added/inactivated refsets
            return;
        }

        LOG.info(("analyze refset versions"));

        Map<String, Map<Long, Refset>> dbActiveRefsetIdToVersionRefsetMap = new HashMap<>();
        Map<String, Map<Long, Refset>> dbInactiveRefsetIdToVersionRefsetMap = new HashMap<>();
        populateRefsetToVersions(service, dbActiveRefsetIdToVersionRefsetMap, dbInactiveRefsetIdToVersionRefsetMap);

        for (final String refsetId : termserverRefsetIdToRefsetVersionsDataMap.keySet().stream().filter(refsetId -> !addedOrInactivatedRefsetIds.contains(refsetId)).collect(Collectors.toList())) {

            STATISTICS.incrementRefsetVersionsSynced();

            if (isTesting() && getTestingRefset() != null && !getTestingRefset().equals(refsetId)) {

                continue;
            }

            final List<Long> activatedVersions = new ArrayList<>();
            final List<Long> inactivatedVersions = new ArrayList<>();
            final List<Long> existingInBothVersions = new ArrayList<>();
            final List<Long> modifiedDBVersions = new ArrayList<>();
            List<Long> unchangedVersions = new ArrayList<>();

            final Map<Long, SyncRefsetMetadata> termserverVersionDataMaps = termserverRefsetIdToRefsetVersionsDataMap.get(refsetId);
            final Set<Long> termserverVersions = termserverVersionDataMaps.keySet();

            // Determine and create new refsetVersions (not in active nor in inactive DB refsetVersions)
            final List<Long> addedVersions = new ArrayList<>();

            for (Long tsVersion : termserverVersions) {

                if ((!dbActiveRefsetIdToVersionRefsetMap.containsKey(refsetId)
                    || dbActiveRefsetIdToVersionRefsetMap.get(refsetId).keySet().stream().noneMatch(dbVersion -> isRefsetVersionMatches(tsVersion, dbVersion)))
                    && (!dbInactiveRefsetIdToVersionRefsetMap.containsKey(refsetId)
                        || dbInactiveRefsetIdToVersionRefsetMap.get(refsetId).keySet().stream().noneMatch(dbVersion -> isRefsetVersionMatches(tsVersion, dbVersion)))) {

                    addedVersions.add(tsVersion);
                }

            }

            addMultipleRefsets(service, addedVersions, termserverVersionDataMaps);

            // Activate previously inactivated refsetVersions. Note: Will log and update stats after remove those that were activatedAndModified
            if (dbInactiveRefsetIdToVersionRefsetMap.containsKey(refsetId)) {

                final List<Long> results = termserverVersions.stream().filter(version -> dbInactiveRefsetIdToVersionRefsetMap.get(refsetId).containsKey(version)).collect(Collectors.toList());
                activatedVersions.addAll(results);
                activatedVersions.stream().forEach(version -> getDbHandler().updateRefsetVersionStatus(service, refsetId, version, true));
            }

            // Inactivate active DB refsetVersions that are not in termserver
            if (dbActiveRefsetIdToVersionRefsetMap.containsKey(refsetId)) {

                for (final long dbVersion : dbActiveRefsetIdToVersionRefsetMap.get(refsetId).keySet()) {

                    boolean matchFound = false;

                    for (final long termserverVersion : termserverVersions) {

                        if (isRefsetVersionMatches(termserverVersion, dbVersion)) {

                            matchFound = true;

                        }

                    }

                    if (!matchFound) {

                        inactivatedVersions.add(dbVersion);

                    }

                }

                inactivatedVersions.stream().forEach(version -> getDbHandler().updateRefsetVersionStatus(service, refsetId, version, false));

                // Determine Versions that are active in DB and found in termserver and compare for changes
                for (final long dbVersion : dbActiveRefsetIdToVersionRefsetMap.get(refsetId).keySet()) {

                    boolean matchFound = false;

                    for (final long termserverVersion : termserverVersionDataMaps.keySet()) {

                        if (isRefsetVersionMatches(termserverVersion, dbVersion)) {

                            matchFound = true;

                        }

                    }

                    if (matchFound) {

                        existingInBothVersions.add(dbVersion);

                    }

                }

                // Compare in termserver & active in db
                final List<Refset> dbRefsets = getAllPublishedRefsets(service);

                modifiedDBVersions.addAll(compareAndModifyRefsetVersions(service, dbRefsets, refsetId, existingInBothVersions, termserverVersionDataMaps));

                unchangedVersions = existingInBothVersions.stream().filter(e -> !modifiedDBVersions.contains(e)).collect(Collectors.toList());
            }

            // Compare in termserver & newly activated in db
            final List<Refset> dbRefsets = getAllPublishedRefsets(service);

            if (!activatedVersions.isEmpty()) {

                final List<Long> activatedAndModifiedVersions = compareAndModifyRefsetVersions(service, dbRefsets, refsetId, activatedVersions, termserverVersionDataMaps);

                // Finalize those refset versions that were only activated (and not further modified)
                if (activatedVersions != null && !activatedVersions.isEmpty()) {

                    activatedAndModifiedVersions.stream().filter(n -> activatedVersions.contains(n)).forEach(version -> activatedVersions.remove(version));
                }

            }

            // Finally, populate newly CreatedAndUnchagned map to handle finalization
            if ((!addedVersions.isEmpty() || !unchangedVersions.isEmpty()) && !newlyCreatedAndUnchangedRefsetToVersionsMap.containsKey(refsetId)) {

                newlyCreatedAndUnchangedRefsetToVersionsMap.put(refsetId, new HashSet<>());
            }

            if (!newlyCreatedAndUnchangedRefsetToVersionsMap.containsKey(refsetId)) {

                newlyCreatedAndUnchangedRefsetToVersionsMap.put(refsetId, new HashSet<>());
            }

            newlyCreatedAndUnchangedRefsetToVersionsMap.get(refsetId).addAll(addedVersions);
            newlyCreatedAndUnchangedRefsetToVersionsMap.get(refsetId).addAll(unchangedVersions);

        }

    }

    /**
     * Adds the multiple refsets.
     *
     * @param service the service
     * @param addedVersions the added versions
     * @param termserverVersionDataMaps the termserver version data maps
     * @throws Exception the exception
     */
    public void addMultipleRefsets(final TerminologyService service, final List<Long> addedVersions, final Map<Long, SyncRefsetMetadata> termserverVersionDataMaps) throws Exception {

        service.setTransactionPerOperation(false);
        service.beginTransaction();

        addedVersions.stream().forEach(version -> addRefset(service, termserverVersionDataMaps.get(version)));

        service.commit();
        service.setTransactionPerOperation(true);

    }

    /**
     * Adds the multiple refsets.
     *
     * @param service the service
     * @param addedRefsetIds the added refset ids
     * @throws Exception the exception
     */
    public void addMultipleRefsets(final TerminologyService service, final List<String> addedRefsetIds) throws Exception {

        service.setTransactionPerOperation(false);
        service.beginTransaction();

        addedRefsetIds.stream().filter(refsetId -> termserverRefsetIdToRefsetVersionsDataMap.containsKey(refsetId)).forEach(refsetId -> termserverRefsetIdToRefsetVersionsDataMap.get(refsetId).keySet()
            .stream().forEach(version -> addRefset(service, termserverRefsetIdToRefsetVersionsDataMap.get(refsetId).get(version))));

        service.commit();
        service.setTransactionPerOperation(true);

    }

    /**
     * Generate database refset idto refset versions map.
     *
     * @param dbRefsets the db refsets
     * @return the map
     */
    // RefsetId to map of Dates to dbRefset
    private Map<String, Map<Long, Refset>> generateDatabaseRefsetIdtoRefsetVersionsMap(final Set<Refset> dbRefsets) {

        final Map<String, Map<Long, Refset>> generatedMap = new HashMap<>();

        for (final Refset dbRefset : dbRefsets) {

            if (!generatedMap.containsKey(dbRefset.getRefsetId())) {

                generatedMap.put(dbRefset.getRefsetId(), new HashMap<>());
            }

            generatedMap.get(dbRefset.getRefsetId()).put(dbRefset.getVersionDate().getTime(), dbRefset);
        }

        return generatedMap;

    }

    /**
     * Analyze code system branches.
     *
     * @param service the service
     * @return
     * @throws Exception the exception
     */
    private Set<SyncRefsetMetadata> analyzeCodeSystemBranches(final TerminologyService service) throws Exception {

        /** The filtered refsets. */
        final Set<SyncRefsetMetadata> filteredRefsets = new HashSet<>();

        // Determine all version dates, per edition, and mapped with the associated publication branch
        final Map<String, SortedMap<Long, String>> filteredTermserverShortNameToVersionBranchMap = determineEditionBranches(service, filteredCodeSystems);

        LOG.info("Gather refset data for each refset available with each edition's version for: " + filteredTermserverShortNameToVersionBranchMap.keySet());

        for (final String editionShortName : filteredTermserverShortNameToVersionBranchMap.keySet()) {

            final Edition edition = isEditionToProcess(service, editionShortName);

            // Have valid edition. Filter refsets to process
            if (edition != null) {

                final Set<SyncRefsetMetadata> refsetVersions = filterEditionRefsetVerions(edition, filteredTermserverShortNameToVersionBranchMap.get(editionShortName));
                filteredRefsets.addAll(refsetVersions);

            }

        }

        if (filteredRefsets.isEmpty()) {

            LOG.info("No refsets to process. This may be odd, but will happen based on certain criteria " + "(such as having an edition without any non-core refset changes");
            return filteredRefsets;

        }

        LOG.info("Finished processing db branches across all editions with " + filteredRefsets.size() + " filtered refsets versions.");

        // Based on FILTERED_CODE_SYSTEMS which already filtered for active code systems
        termserverRefsetIdToRefsetVersionsDataMap = generateTermserverRefsetIdtoRefsetVersionsMap(filteredRefsets);

        if (termserverRefsetIdToRefsetVersionsDataMap.isEmpty()) {

            throw new Exception("termServerRefsetIdToRefsetVersionsDataMap should never be empty ");
        }

        // add final attributes including narrative, intentional refset definition clauses (if exists), and tags (if exists)
        finalizeNewOrChangedRefsets(service);

        return filteredRefsets;
    }

    /**
     * Populate refset to versions.
     *
     * @param service the service
     * @param dbActiveRefsetIdToVersionRefsetMap the db active refset id to version refset map
     * @param dbInactiveRefsetIdToVersionRefsetMap the db inactive refset id to version refset map
     * @throws Exception the exception
     */
    private void populateRefsetToVersions(final TerminologyService service, final Map<String, Map<Long, Refset>> dbActiveRefsetIdToVersionRefsetMap,
        final Map<String, Map<Long, Refset>> dbInactiveRefsetIdToVersionRefsetMap) throws Exception {

        final Set<Refset> dbActiveRefsets = new HashSet<>();
        final Set<Refset> dbInactiveRefsets = new HashSet<>();
        final List<Refset> dbRefsets = service.getAll(Refset.class);

        dbRefsets.stream().filter(r -> VersionStatus.PUBLISHED.getLabel().equals(r.getVersionStatus()) && r.isActive()).forEach(ar -> dbActiveRefsets.add(ar));
        dbRefsets.stream().filter(r -> VersionStatus.PUBLISHED.getLabel().equals(r.getVersionStatus()) && !r.isActive()).forEach(ir -> dbInactiveRefsets.add(ir));

        // Map each refsetId/version pair's SyncRefsetMetadata
        dbActiveRefsetIdToVersionRefsetMap.putAll(generateDatabaseRefsetIdtoRefsetVersionsMap(dbActiveRefsets));
        dbInactiveRefsetIdToVersionRefsetMap.putAll(generateDatabaseRefsetIdtoRefsetVersionsMap(dbInactiveRefsets));

    }

    /**
     * Is edition to process.
     *
     * @param service the service
     * @param editionShortName the edition short name
     * @return the edition
     * @throws Exception the exception
     */
    private Edition isEditionToProcess(final TerminologyService service, final String editionShortName) throws Exception {

        // handle the ignoreCoreRefsets flag
        if (getIsIgnoreCoreRefsets() && getUtilities().isInternationalEdition(editionShortName)) {

            LOG.info("Not processing CORE refsets per ignoreCoreRefsets = " + getIsIgnoreCoreRefsets());
            return null;
        }

        // determine matching editions
        final Stream<Edition> editionStream = readDbActiveEditions(service).stream().filter(e -> e.getShortName().equals(editionShortName));
        final Edition edition = (Edition) getUtilities().validateMatches(editionStream, editionShortName);

        return edition;
    }

    /**
     * Initialize sync.
     *
     * @throws Exception the exception
     */
    private void initializeSync() throws Exception {

        refsetToModuleMap.clear();
        newlyCreatedAndUnchangedRefsetToVersionsMap.clear();

        RefsetMemberService.clearVersionsWithChanges();
    }

    /**
     * Generate termserver refset idto refset versions map.
     * @param filteredRefsets
     *
     * @return the map
     */
    // RefsetId to map of Dates to RefsetMetadata
    private Map<String, Map<Long, SyncRefsetMetadata>> generateTermserverRefsetIdtoRefsetVersionsMap(Set<SyncRefsetMetadata> filteredRefsets) {

        final Map<String, Map<Long, SyncRefsetMetadata>> generatedMap = new HashMap<>();

        for (final SyncRefsetMetadata termserverRefsetData : filteredRefsets) {

            final String refsetId = termserverRefsetData.getRefsetId();

            if (!generatedMap.containsKey(refsetId)) {

                generatedMap.put(refsetId, new HashMap<>());
            }

            generatedMap.get(refsetId).put(termserverRefsetData.getVersion(), termserverRefsetData);
        }

        return generatedMap;
    }

    /**
     * Compare and modify refset versions.
     *
     * @param service the service
     * @param dbRefsets the db refsets
     * @param refsetId the refset id
     * @param versionDatesToCompare the version dates to compare
     * @param termserverPairDataMap the termserver pair data map
     * @return the list
     * @throws Exception the exception
     */
    // Only includes name and moduleid. All other values are defined in RT2 or in connecting to edition data whose changes have already been reviewed
    private List<Long> compareAndModifyRefsetVersions(final TerminologyService service, final List<Refset> dbRefsets, final String refsetId, final List<Long> versionDatesToCompare,
        final Map<Long, SyncRefsetMetadata> termserverPairDataMap) throws Exception {

        final List<Long> modifiedVersions = new ArrayList<>();

        final List<Refset> dbVersions = dbRefsets.stream().filter(r -> r.getRefsetId().equals(refsetId)).collect(Collectors.toList());

        // Process one version at a time
        for (final long testingVersionDate : versionDatesToCompare) {

            // Find associated DB refset
            final Stream<Refset> refsetVersionStream = dbVersions.stream().filter(dbr -> dbr.getVersionDate().getTime() == testingVersionDate);
            final Refset modifyingVersion = (Refset) getUtilities().validateMatches(refsetVersionStream, refsetId + " / " + testingVersionDate);

            final List<Long> matchingTermserverRefsetVersionData =
                termserverPairDataMap.keySet().stream().filter(termserverVersion -> (isRefsetVersionMatches(termserverVersion, testingVersionDate))).collect(Collectors.toList());

            if (matchingTermserverRefsetVersionData.isEmpty()) {

                throw new Exception("Compare Refset Version - Failed to find find expected the termserver pairing for refsetId/testingVersionDate: " + refsetId + " / " + testingVersionDate);
            }

            final SyncRefsetMetadata termserverPairMetadata = termserverPairDataMap.get(matchingTermserverRefsetVersionData.iterator().next());
            final String termserverRefsetName = determineRefsetName(termserverPairMetadata);
            final String termserverRefsetBranch = termserverPairMetadata.getBranchPath();
            final String termserverRefsetModuleId = refsetToModuleMap.get(refsetId);

            // Start comparison
            boolean modificationMade = false;

            if (modifyingVersion.getName() == null || isDifferentAttribute(refsetId + " / " + testingVersionDate, "Refset name ", modifyingVersion.getName(), termserverRefsetName)) {

                modifyingVersion.setName(termserverRefsetName);
                modificationMade = true;
            }

            if (modifyingVersion.getBranchPath() == null || isDifferentAttribute(refsetId + " / " + testingVersionDate, "Refset branch ", modifyingVersion.getBranchPath(), termserverRefsetBranch)) {

                if (modifyingVersion.getBranchPath() != null) {

                    LOG.error("Likely an error as refsetId/version " + refsetId + "/" + testingVersionDate + " shouldn't be able to change their branch path from '" + modifyingVersion.getBranchPath()
                        + "' to '" + termserverRefsetBranch + "'");
                }

                modifyingVersion.setBranchPath(termserverRefsetBranch);
                modificationMade = true;
            }

            if (modifyingVersion.getModuleId() == null || isDifferentAttribute(refsetId + " / " + testingVersionDate, "Refset moduleId ", modifyingVersion.getModuleId(), termserverRefsetModuleId)) {

                modifyingVersion.setModuleId(termserverRefsetModuleId);
                modificationMade = true;
            }

            if (modificationMade) {

                final Refset updatedRefset = getDbHandler().updateRefset(service, modifyingVersion);

                modifiedVersions.add(updatedRefset.getVersionDate().getTime());
            }

        }

        return modifiedVersions;
    }

    /**
     * Determine refset name.
     *
     * @param refsettermserverData the refsettermserver data
     * @return the string
     * @throws Exception the exception
     */
    private String determineRefsetName(final SyncRefsetMetadata refsettermserverData) throws Exception {

        String refsetName;

        if (refsettermserverData.getRefsetNode().get("pt").has("term")) {

            refsetName = refsettermserverData.getRefsetNode().get("pt").get("term").asText();
        } else {

            refsetName = lookupRefsetName(refsettermserverData.getRefsetId(), refsettermserverData.getEdition(), refsettermserverData.getBranchPath());
        }

        return refsetName;
    }

    /**
     * Filter edition refset verions.
     *
     * @param edition the edition
     * @param filteredRefsets
     * @param termserverVersionBranchMap the termserver version branch map
     * @throws Exception the exception
     */
    private Set<SyncRefsetMetadata> filterEditionRefsetVerions(final Edition edition, final SortedMap<Long, String> termserverVersionBranchMap) throws Exception {

        final Set<SyncRefsetMetadata> filteredRefsets = new HashSet<>();

        for (final long versionDate : termserverVersionBranchMap.keySet()) {

            if (versionDate < PRE_SNOMED_SUPPORTED_RELEASES) {

                continue;
            }

            final Iterator<JsonNode> refsetIterator = getTermserverRefsetVersionMembers(edition.getName(), edition.getBranch(), termserverVersionBranchMap.get(versionDate), versionDate);

            while (refsetIterator != null && refsetIterator.hasNext()) {

                final JsonNode refsetNode = refsetIterator.next();

                // Check if should process Refset

                if (refsetNode != null) {

                    final String refsetId = refsetNode.get("conceptId").asText();
                    final String moduleId = determineConceptModuleId(refsetId, edition.getBranch());

                    if (isRefsetToProcess(refsetId, moduleId, edition)) {

                        boolean firstTime = false;

                        if (!refsetToModuleMap.containsKey(refsetId)) {

                            firstTime = true;
                            refsetToModuleMap.put(refsetId, moduleId);
                        }

                        final String termserverRefsetBranchPath = termserverVersionBranchMap.get(versionDate);
                        final Set<Long> termserverEditionBranchDates = termserverVersionBranchMap.keySet();

                        // If perVersionSync, then create version per branch and return. Otherwise, determine if changes exist in this version
                        if (firstTime || getIsPerVersionSync() || versionHasChanges(refsetId, versionDate, termserverRefsetBranchPath, edition.getName(), termserverEditionBranchDates)) {

                            final SyncRefsetMetadata refsetMetadata = new SyncRefsetMetadata(refsetNode, edition, termserverVersionBranchMap.keySet(), versionDate, termserverRefsetBranchPath);

                            // Found a refset to process later on
                            filteredRefsets.add(refsetMetadata);

                        }

                    }

                }

            }

        }

        return filteredRefsets;

    }

    /**
     * Returns the termserver refset version members.
     *
     * @param editionName the edition name
     * @param editionBranchPath the edition branch path
     * @param refsetBranchPath the refset branch path
     * @param branchVersion the branch version
     * @return the termserver refset version members
     * @throws Exception the exception
     */
    private Iterator<JsonNode> getTermserverRefsetVersionMembers(final String editionName, final String editionBranchPath, final String refsetBranchPath, final long branchVersion) throws Exception {

        // Process edition
        final String url = SnowstormConnection.getBaseUrl() + "browser/{branch}/members?active=true&referenceSet=%3C" + RefsetService.SIMPLE_TYPE_REFERENCE_SET;

        try (final Response response = SnowstormConnection.getResponse(url.replace("{branch}", refsetBranchPath))) {

            if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {

                if (editionBranchPath.startsWith("MAIN")) {

                    throw new Exception("Unable to process edition called with: " + url.replace("{branch}", refsetBranchPath));
                } else {

                    return null;
                }

            }

            // get RefSets from edition as long as a) active & b) not a core refset
            final String resultString = response.readEntity(String.class);
            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode root = mapper.readTree(resultString.toString());

            final Iterator<JsonNode> refsetIterator = root.get("referenceSets").iterator();
            LOG.info("getUrl (edition version refsets): " + url.replace("{branch}", refsetBranchPath));

            return refsetIterator;
        }

    }

    /**
     * Determine concept module id.
     *
     * @param refsetId the refset id
     * @param branch the branch
     * @return the string
     * @throws Exception the exception
     */
    private String determineConceptModuleId(final String refsetId, final String branch) throws Exception {

        final String url = SnowstormConnection.getBaseUrl() + branch + "/concepts/" + refsetId;

        try (final Response response = SnowstormConnection.getResponse(url)) {

            if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {

                throw new Exception("Unable to retrieve concept " + refsetId + " on branch " + branch + " in order to determine its moduleId");

            }

            final String resultString = response.readEntity(String.class);
            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode root = mapper.readTree(resultString.toString());

            // get RefSets from edition as long as a) active & b)
            // within edition's moduleˇ
            return root.get("moduleId").asText();

        }

    }

    /**
     * Version has changes.
     *
     * @param refsetId the refset id
     * @param versionDate the version date
     * @param termserverRefsetBranchPath the termserver refset branch path
     * @param editionName the edition name
     * @param termserverEditionBranchDates the termserver edition branch dates
     * @return true, if successful
     * @throws Exception the exception
     */
    private boolean versionHasChanges(final String refsetId, final long versionDate, final String termserverRefsetBranchPath, final String editionName, final Set<Long> termserverEditionBranchDates)
        throws Exception {

        // Check new version refset version date. If none returned (null), then:
        // a) no changes to refset itself and
        // b) thus no need to create new version.
        // c) Move onto next refset/version pair
        Long refsetVersionDate = RefsetMemberService.getLatestChangedVersionDate(termserverRefsetBranchPath, refsetId);
        long updatedVersionDate = versionDate;

        if (refsetVersionDate == null) {

            // No changes to refset so don't create a new version
            return false;
        }

        long earliestPublishedVersionDate = -1;

        if (!termserverEditionBranchDates.contains(refsetVersionDate)) {

            for (final long editionDate : termserverEditionBranchDates) {

                if (refsetVersionDate > editionDate) {

                    LOG.error("Ignoring this member as have bad content - Can't have refsets with a member that has an effectiveDate:  " + refsetVersionDate + " that is AFTER the editionDate: "
                        + editionDate);
                    continue;
                }

                if (earliestPublishedVersionDate < 0 || editionDate < earliestPublishedVersionDate) {

                    earliestPublishedVersionDate = editionDate;
                }

            }

            if (earliestPublishedVersionDate < 0) {

                throw new Exception("Bad content likely brought us here as unable to find a valid earliest w/ refsetId: " + refsetId + " & branchVersion: " + versionDate + " & versionDate: "
                    + versionDate + " & branchPath: " + termserverRefsetBranchPath);
            }

            refsetVersionDate = earliestPublishedVersionDate;
        }

        updatedVersionDate = refsetVersionDate;

        if (!termserverEditionBranchDates.contains(updatedVersionDate)) {

            LOG.info(" Don't add refset versions that don't have corresponding termserver -based edition versions with Refset / and VersionDate pair: " + refsetId + " / " + updatedVersionDate);

            return false;
        }

        return true;
    }

    /**
     * Lookup refset name.
     *
     * @param refsetId the refset id
     * @param edition the edition
     * @param branchPath the child branch
     * @return the string
     * @throws Exception the exception
     */
    private String lookupRefsetName(final String refsetId, final Edition edition, final String branchPath) throws Exception {

        final String url = SnowstormConnection.getBaseUrl() + "browser/" + branchPath + "/concepts/" + refsetId;

        try (final Response response = SnowstormConnection.getResponse(url)) {

            final String resultString = response.readEntity(String.class);
            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode conceptNode = mapper.readTree(resultString.toString());

            final Iterator<JsonNode> descriptionIterator = conceptNode.get("descriptions").iterator();

            while (descriptionIterator.hasNext()) {

                final JsonNode descriptionNode = descriptionIterator.next();
                String acceptability = null;

                if (descriptionNode.get("type").asText().equals("SYNONYM") && descriptionNode.get("lang").asText().equals(edition.getDefaultLanguageCode())) {

                    final JsonNode acceptabilityMap = descriptionNode.get("acceptabilityMap");

                    for (final String langRefsetId : edition.getDefaultLanguageRefsets()) {

                        if (acceptabilityMap.has(langRefsetId)) {

                            acceptability = acceptabilityMap.get(langRefsetId).asText();
                            break;
                        }

                    }

                    if (acceptability == null) {

                        throw new Exception("Not able to properly determine refset name for description: " + descriptionNode);
                    }

                    if (acceptability.equals("PREFERRED")) {

                        return descriptionNode.get("term").asText();
                    }

                }

            }

            throw new Exception("Unable to find PrefTerm for refset concept: " + conceptNode);
        }

    }

    /**
     * Determine project.
     *
     * @param service the service
     * @param metadata the metadata
     * @return the project
     * @throws Exception the exception
     */
    private Project determineProject(final TerminologyService service, final SyncRefsetMetadata metadata) throws Exception {
        /*
         * First checks if the refset is associated with an RTT project. If so return. If not, return the default Edition's project (creating it if not already existing)
         */

        // Cache contains refset project already?

        if (!refsetProjectMap.containsKey(metadata.getRefsetId())) {

            // refset project must be defined in RTT
            if (getUtilities().getPropertyReader().getSctIdToProjectIdMap().containsKey(metadata.getRefsetId())) {

                final String rttProjectId = getUtilities().getPropertyReader().getSctIdToProjectIdMap().get(metadata.getRefsetId());

                if (getUtilities().getPropertyReader().getProjectIdToProjectInfoMap().containsKey(rttProjectId)) {

                    if (getUtilities().getPropertyReader().getProjectIdToProjectInfoMap().get(rttProjectId).keySet().size() != 1) {

                        LOG.error("Have unexpected number of names/descriptions for projectId: " + rttProjectId + " with names: "
                            + getUtilities().getPropertyReader().getProjectIdToProjectInfoMap().get(rttProjectId).keySet());

                        return null;
                    }

                    final Map<String, String> rttProjectInfo = getUtilities().getPropertyReader().getProjectIdToProjectInfoMap().get(rttProjectId);

                    final String rttProjectName = rttProjectInfo.keySet().iterator().next();

                    // Rather than query the database each time, see if already have project used. Otherwise create it.
                    // Note: The primary key for a project in this case is it's projectName/organizationId pair.
                    if (refsetProjectMap.values().stream()
                        .anyMatch(project -> project.getName().equals(rttProjectName) && project.getOrganizationId().equals(metadata.getEdition().getOrganization().getId()))) {

                        /// Use if already have a refset pointed to that project, locate it and use that refset reference here to obtain it
                        final String refsetIdToUse = refsetProjectMap.keySet().stream().filter(refsetId -> refsetProjectMap.get(refsetId).getName().equals(rttProjectName)).iterator().next();
                        final Project projectToUse = refsetProjectMap.get(refsetIdToUse);

                        refsetProjectMap.put(metadata.getRefsetId(), projectToUse);

                    } else {

                        final String rttProjectDescription = rttProjectInfo.get(rttProjectName);

                        // Create project
                        final Project addedProject =
                            getDbHandler().addProject(service, rttProjectName, rttProjectDescription, metadata.getEdition(), CrowdGroupNameAlgorithm.getProjectString(rttProjectName));

                        refsetProjectMap.put(metadata.getRefsetId(), addedProject);
                    }

                }

            }

            if (!refsetProjectMap.containsKey(metadata.getRefsetId())) {

                // Refset is not associated with any project in RTT.
                // TODO: Determine if create default project or add to any random project in Org. For simplicity, for now will just add to random organization
                // project
                final List<Project> projects = OrganizationService.getOrganizationProjects(service, metadata.getEdition().getOrganizationId()).getItems();

                if (!defaultOrganizationProjectMap.containsKey(metadata.getEdition().getOrganizationId())) {

                    Project projectToAdd = null;

                    // TODO: Handle all
                    Optional<Project> defaultProject = projects.stream().filter(p -> p.getName().equalsIgnoreCase("all") && p.getEditionId().equals(metadata.getEdition().getId())).findAny();

                    if (defaultProject.isEmpty()) {

                        defaultProject = projects.stream().filter(p -> p.getEditionId().equals(metadata.getEdition().getId())).findAny();

                        if (defaultProject.isEmpty()) {

                            throw new Exception("Trying tro create refset " + metadata.getRefsetId() + " (" + metadata.getVersion() + ") on edition " + metadata.getEdition().getName()
                                + ", but there are no projects in crowd to support this.");
                        }

                    }

                    projectToAdd = defaultProject.get();

                    defaultOrganizationProjectMap.put(metadata.getEdition().getOrganizationId(), projectToAdd);
                }

                final Project projectToUse = defaultOrganizationProjectMap.get(metadata.getEdition().getOrganizationId());
                refsetProjectMap.put(metadata.getRefsetId(), projectToUse);

            }

        }

        return refsetProjectMap.get(metadata.getRefsetId());

    }

    /**
     * Update refsets with values (including Projects) from json and also identify latestVersion, but do not persist at this point.
     *
     * @param service the service
     * @throws Exception the exception
     */
    private void finalizeNewOrChangedRefsets(final TerminologyService service) throws Exception {

        final Map<String, Long> latestVersionCache = new HashMap<>();
        final Set<Refset> refsetsUpdated = new HashSet<>();

        getUtilities().getPropertyReader().parseRttData();

        // Refresh DB cache with additions just made
        Map<String, Map<Long, Refset>> dbActiveRefsetIdToVersionRefsetMap = new HashMap<>();
        Map<String, Map<Long, Refset>> dbInactiveRefsetIdToVersionRefsetMap = new HashMap<>();
        populateRefsetToVersions(service, dbActiveRefsetIdToVersionRefsetMap, dbInactiveRefsetIdToVersionRefsetMap);

        for (final String refsetId : dbActiveRefsetIdToVersionRefsetMap.keySet()) {

            for (final long version : dbActiveRefsetIdToVersionRefsetMap.get(refsetId).keySet()) {

                try {

                    final Refset refset = dbActiveRefsetIdToVersionRefsetMap.get(refsetId).get(version);

                    // Actually process the refset here
                    final Refset updatedRefset = finalizeRefset(service, refset, latestVersionCache);

                    refsetsUpdated.add(updatedRefset);

                } catch (Exception e) {

                    e.printStackTrace();
                    LOG.error("Failed on refsetVersion: " + refsetId + " / " + version + " --- with message: " + e.getMessage());
                }

            }

        }

        final List<Refset> dbRefsets = getAllPublishedRefsets(service);

        // Reset latestPublishedVersion before recalculate it
        for (final Refset dbRefset : dbRefsets) {

            if (dbRefset.isLatestPublishedVersion()) {

                dbRefset.setLatestPublishedVersion(false);
                refsetsUpdated.add(dbRefset);
            }

        }

        // Update the latest refset version cache per refset. Set the latestVersion flag to true for them
        Set<Refset> refsetsFinalized = new HashSet<>();

        for (final Refset dbRefset : refsetsUpdated) {

            if (latestVersionCache.containsKey(dbRefset.getRefsetId())) {

                for (final String refsetId : latestVersionCache.keySet()) {

                    if (dbRefset.getRefsetId().equals(refsetId) && isRefsetVersionMatches(latestVersionCache.get(refsetId), dbRefset.getVersionDate().getTime())) {

                        dbRefset.setLatestPublishedVersion(true);
                        refsetsFinalized.add(dbRefset);
                        break;
                    }

                }

            }

        }

        // Persist changes across all Refsets
        refsetsUpdated.addAll(refsetsFinalized);
        getDbHandler().updateMultipleRefsets(service, refsetsUpdated);

    }

    /**
     * Finalize refset.
     *
     * @param service the service
     * @param refset the refset
     * @param latestVersionCache the latest version cache
     * @return the refset
     * @throws Exception the exception
     */
    private Refset finalizeRefset(final TerminologyService service, final Refset refset, final Map<String, Long> latestVersionCache) throws Exception {

        // Update refset from JSON for Narrative, Type, tags, and ecl clauses. Project too.
        Refset updatedRefset = null;

        // setup data to persist later as at minimum will be defining the narrative in both scenarios
        if (getUtilities().getPropertyReader().getRefsetSctIdToRttIdMap().keySet().contains(refset.getRefsetId())) {

            updatedRefset = analyzeRttGenericData(service, refset);

        } else {

            updatedRefset = refset;
            // If JSON not available to the refset, it means it resides exclusively on termserver.
            // Set defaults for type & narrative (TAGS & ECL) are defined in RT2 not via termserver
            updatedRefset.setNarrative("No corresponding refset information found on RTT for " + refset.getRefsetId());

        }

        // Keep track of the latest version per refsetId
        if (!latestVersionCache.containsKey(updatedRefset.getRefsetId()) || latestVersionCache.get(updatedRefset.getRefsetId()) < updatedRefset.getVersionDate().getTime()) {

            latestVersionCache.put(updatedRefset.getRefsetId(), updatedRefset.getVersionDate().getTime());
        }

        return updatedRefset;
    }

    /**
     * Analyze rtt generic data.
     *
     * @param service the service
     * @param refset the refset
     * @return the refset
     * @throws Exception the exception
     */
    private Refset analyzeRttGenericData(final TerminologyService service, final Refset refset) throws Exception {

        /* Refset lived in RTT as well */
        final Set<String> rttIds = getUtilities().getPropertyReader().getRefsetSctIdToRttIdMap().get(refset.getRefsetId());

        // Add Refset with RTT data as long as it also version resides on termserver. Keep track of which are added this way as to not add them from RTT as
        // well

        final Map<String, Set<DefinitionClause>> refsetSctIdToClausesCreatedMap = new HashMap<>();
        final SimpleDateFormat sdf = new SimpleDateFormat(getUtilities().getIsoDateTimeFormat());

        for (final String rttId : rttIds) {

            if (getUtilities().getPropertyReader().getRttIdToRefsetJsonMap().containsKey(rttId)) {

                final String refsetJsonString = getUtilities().getPropertyReader().getRttIdToRefsetJsonMap().get(rttId);

                final ObjectMapper mapper = new ObjectMapper();
                final JsonNode refsetJson = mapper.readTree(refsetJsonString);

                final long rttDataRefsetVersion = sdf.parse(refsetJson.get("version").asText()).getTime();

                if (rttDataRefsetVersion < 0 || rttDataRefsetVersion == refset.getVersionDate().getTime()) {

                    // Populate tags from contents of refsetToTags.txt file
                    if (getUtilities().getPropertyReader().getRefsetSctToTagsMap().containsKey(refset.getRefsetId())) {

                        getUtilities().getPropertyReader().getRefsetSctToTagsMap().get(refset.getRefsetId()).stream().forEach(tag -> refset.getTags().add(tag));
                    }

                    // Populate ECL clauses from contents of refsetToClauses.txt file

                    Set<DefinitionClause> clauses = null;

                    if (getUtilities().getPropertyReader().getRefsetSctToClausesMap().containsKey(refset.getRefsetId())) {

                        if (!refsetSctIdToClausesCreatedMap.containsKey(refset.getRefsetId())) {

                            clauses = getDbHandler().addDefinitionClauses(service, refset.getRefsetId());
                            refsetSctIdToClausesCreatedMap.put(refset.getRefsetId(), clauses);
                        } else {

                            clauses = refsetSctIdToClausesCreatedMap.get(refset.getRefsetId());
                        }

                        refset.getDefinitionClauses().addAll(clauses);
                    }

                    break;
                }

            }

        }

        return refset;
    }

    /**
     * Indicates whether or not refset to process is the case.
     *
     * @param refsetId the refset id
     * @param moduleId
     * @param edition the short name
     * @return <code>true</code> if so, <code>false</code> otherwise
     * @throws Exception the exception
     */
    protected boolean isRefsetToProcess(final String refsetId, String moduleId, final Edition edition) throws Exception {

        // Refset is a testing refset, so use
        if (!isTesting() || (isTesting() && (getTestingRefset() == null || getTestingRefset().isEmpty()) || refsetId.equals(getTestingRefset()))) {

            return true;
        }

        // If not international ensure, ensure refset is not from CORE
        if (!getUtilities().isInternationalEdition(edition.getShortName()) && getUtilities().getCoreRefsets().contains(refsetId)) {

            return false;
        }

        if (getUtilities().getPropertyReader().getRefsetsToIgnore().contains(refsetId)) {

            LOG.info("Found refsetId: " + refsetId + ", but will not add it per property file refsetsToIgnore.txt");
            return false;
        }

        // Only continue processing refset if it is created within the current edition's modules
        if (edition.getModules().contains(moduleId)) {

            return true;
        }

        return false;
    }

    /**
     * Determine branches.
     *
     * @param service the service
     * @param codeSystems the code systems
     * @return the map
     * @throws Exception the exception
     */
    private Map<String, SortedMap<Long, String>> determineEditionBranches(final TerminologyService service, final Set<JsonNode> codeSystems) throws Exception {

        final Map<String, SortedMap<Long, String>> editionToDateBranchMap = new HashMap<>();

        LOG.info("Finding branches for editions: {}",
            readDbActiveEditions(service).stream().collect(StringBuilder::new, (x, y) -> x.append(y.getName()), (a, b) -> a.append(",").append(b)).toString());

        final Map<String, Set<String>> ignoredBranches = new HashMap<>();

        final SimpleDateFormat branchDateFormat = new SimpleDateFormat(BRANCH_DATE_FORMAT);

        for (final JsonNode codeSystem : codeSystems) {

            final String editionName = codeSystem.has("name") ? codeSystem.get("name").asText() : "";
            final String shortName = codeSystem.has("shortName") ? codeSystem.get("shortName").asText() : "";
            final String branch = codeSystem.has("branchPath") ? codeSystem.get("branchPath").asText() : "";

            LOG.info("Identifying CodeSystem branches for: " + editionName);

            final String genericUrl = SnowstormConnection.getBaseUrl() + "branches/{branch}/children";

            final SortedMap<Long, String> children = new TreeMap<>();
            LOG.info(" Branch children Url: " + genericUrl.replace("{branch}", branch));

            try (final Response response = SnowstormConnection.getResponse(genericUrl.replace("{branch}", branch))) {

                final String resultString = response.readEntity(String.class);
                final ObjectMapper mapper = new ObjectMapper();
                final JsonNode root = mapper.readTree(resultString.toString());

                // get RefSets from edition as long as a) active & b) within
                // edition's module
                final Iterator<JsonNode> branchIterator = root.iterator();

                while (branchIterator.hasNext()) {

                    final JsonNode child = branchIterator.next();
                    final String childBranch = child.get("path").asText();
                    String childDate = childBranch.replace(branch, "");

                    if (childDate.startsWith("/")) {

                        childDate = childDate.substring(1);
                    }

                    // Since grabbing all children branches, avoid
                    // attempting to parse extensions i.e. MAIN/SNOMEDCT-US
                    boolean childAdded = false;

                    if (childDate.matches("^\\d{4}-\\d{2}-\\d{2}$")) {

                        final long branchDate = branchDateFormat.parse(childDate).getTime();

                        if (branchDate < new Date().getTime()) {

                            children.put(branchDate, childBranch);
                            childAdded = true;
                        }

                    } else {

                        if (!ignoredBranches.containsKey(editionName)) {

                            ignoredBranches.put(editionName, new HashSet<>());
                        }

                        ignoredBranches.get(editionName).add(childDate);
                    }

                    // if (!childAdded) {

                    // logger.info("Skipping over childBranch/branchDate pair " + edition.getBranch() + "/" + childDate + " as the branch isn't an official
                    // release
                    // branch");
                    // }

                }

            }

            editionToDateBranchMap.put(shortName, children);
        }

        for (final String edition : editionToDateBranchMap.keySet()) {

            LOG.info("Processing edition {}'s branches {}", edition, editionToDateBranchMap.get(edition));
        }

        for (final String edition : ignoredBranches.keySet()) {

            LOG.info("Ignoring edition {}'s branches {}", edition, ignoredBranches.get(edition));
        }

        return editionToDateBranchMap;
    }

    /**
     * Adds the refset.
     *
     * @param service the service
     * @param syncRefsetMetadata the sync refset metadata
     * @return the refset
     */
    private Refset addRefset(final TerminologyService service, final SyncRefsetMetadata syncRefsetMetadata) {

        try {

            final String refsetId = syncRefsetMetadata.getRefsetId();
            final String moduleId = refsetToModuleMap.get(refsetId);

            final String refsetName = determineRefsetName(syncRefsetMetadata);

            final String refsetType = Refset.EXTENSIONAL; // All from termserver are strictly extension

            final long version = syncRefsetMetadata.getVersion();

            final Project project = determineProject(service, syncRefsetMetadata);

            if (project == null) {

                return null;
            }

            // Refset coming from term server, so already published
            final Refset newRefset = getDbHandler().addRefset(service, refsetName, refsetId, moduleId, version, refsetType, VersionStatus.PUBLISHED, WorkflowService.PUBLISHED, project);

            return newRefset;
        } catch (Exception e) {

            LOG.error("Unable to add refset: " + syncRefsetMetadata.getRefsetId());
            e.printStackTrace();
            return null;
        }

    }

    /**
     * Indicates whether or not refset version matches is the case.
     *
     * @param tsVersion the ts version
     * @param dbVersion the db version
     * @return <code>true</code> if so, <code>false</code> otherwise
     */
    private boolean isRefsetVersionMatches(final Long tsVersion, final long dbVersion) {

        final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

        // Get to midnight after dbVersion
        final LocalDate dbVersionDate = LocalDate.parse(sdf.format(new Date(dbVersion))).plusDays(1);

        return tsVersion == dbVersion || dbVersionDate.equals(LocalDate.parse(sdf.format(new Date(tsVersion))));
    }
}
