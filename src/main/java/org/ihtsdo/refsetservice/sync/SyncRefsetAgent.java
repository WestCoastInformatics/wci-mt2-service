/*
 * Copyright 2024 SNOMED International - All Rights Reserved.
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
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status.Family;

import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.model.enums.RefsetType;
import org.ihtsdo.refsetservice.model.enums.VersionStatus;
import org.ihtsdo.refsetservice.model.enums.WorkflowStatus;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.sync.util.SyncRefsetMetadata;
import org.ihtsdo.refsetservice.terminologyservice.EditionService;
import org.ihtsdo.refsetservice.terminologyservice.ProjectService;
import org.ihtsdo.refsetservice.terminologyservice.RefsetMemberService;
import org.ihtsdo.refsetservice.terminologyservice.RefsetService;
import org.ihtsdo.refsetservice.terminologyservice.SnowstormConnection;
import org.ihtsdo.refsetservice.util.CrowdGroupNameAlgorithm;
import org.ihtsdo.refsetservice.util.DateUtility;
import org.ihtsdo.refsetservice.util.LanguageUtility;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.ihtsdo.refsetservice.util.ThreadLocalMapper;
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

    /** The refsetId to refset name cache. */
    private final Map<String, String> refsetNameCache = new HashMap<String, String>();

    /** The db project cache. */
    private List<Project> dbProjectCache = new ArrayList<>();

    /** The termserver refset id to refset versions data map. */
    private Map<String, Map<Long, SyncRefsetMetadata>> termserverRefsetIdToRefsetVersionsDataMap;

    /** The newly created and unchanged refset to versions map. */
    private final Map<String, Set<Long>> newlyCreatedAndUnchangedRefsetToVersionsMap = new HashMap<>();

    /** The refset project map. */
    private final Map<String, Project> refsetProjectMap = new HashMap<>();

    /** The filtered code systems. */
    private final Set<JsonNode> filteredCodeSystems;

    /** The non rtt based refsets. */
    private final Set<SyncRefsetMetadata> nonRttBasedRefsets = new HashSet<>();

    /**
     * Instantiates a {@link SyncRefsetAgent} from the specified parameters.
     *
     * @param filteredCodeSystems the filtered code systems
     */
    public SyncRefsetAgent(final Set<JsonNode> filteredCodeSystems) {

        this.filteredCodeSystems = filteredCodeSystems;
    }

    /* see superclass */
    @Override
    public void syncComponent(final TerminologyService service) throws Exception {

        LOG.info("Starting sync of Refset Agent");

        initializeSync(service);

        // Determine code systems to sync
        final Set<SyncRefsetMetadata> filteredRefsets = analyzeCodeSystemBranches(service);

        // Clear module dependency caches for modules associated with filteredRefsets
        final Set<String> moduleIdsToReview = new HashSet<>();
        filteredRefsets.stream().forEach(r -> moduleIdsToReview.addAll(r.getEdition().getModules()));
        moduleIdsToReview.stream().forEach(id -> EditionService.clearModuleDependencyCaches(id));

        if (filteredRefsets != null && !filteredRefsets.isEmpty()) {
            List<String> addedOrInactivatedRefsetIds = analyzeRttBasedRefsetsIds(service);
            analyzeRefsetVersions(service, addedOrInactivatedRefsetIds);

            // Final step for project connection
            finalizeRefsetMetadata(service);

            service.commit();

            if (!nonRttBasedRefsets.isEmpty()) {
                service.beginTransaction();

                addedOrInactivatedRefsetIds = analyzeNonRttBasedRefsetIds(service);
                analyzeRefsetVersions(service, addedOrInactivatedRefsetIds);

                // Final step for project connection
                finalizeRefsetMetadata(service);

                service.commit();
            }
        } else {
            service.rollback();
        }

        service.beginTransaction();
        final List<Refset> dbRefsets = service.getAll(Refset.class);

        // @TODO: Review need and approach to this
        // Nightly review of refsets that need to be updated
        for (final Refset refset : dbRefsets) {
            try {
                if (refset.getInternationalContentVersion() == null || "1".equals(refset.getInternationalContentVersion())) {
                    for (final SyncRefsetMetadata filteredRefset : filteredRefsets) {
                        if (filteredRefset.getRefsetId().equals(refset.getRefsetId()) && filteredRefset.getVersion() == refset.getVersionDate().getTime()) {
                            // TODO: Bug somewhere in that branchPath is null here, so finding corresponding filitered Refset Metadata spec to pull from rather
                            // than further investigate for now.
                            refset.setInternationalContentVersion(EditionService.getDependencyModuleNameFromBranchMetadata(refset.getEditionShortName(),
                                filteredRefset.getModuleId(), filteredRefset.getVersion(), filteredRefset.getBranchPath()));

                            service.update(refset);
                            break;
                        }
                    }
                }
            } catch (final Exception e) {
                LOG.error("Unable to do complete review of moduleDepenency for edition/refset/versionDate: {}/{}/{}" + refset.getEditionShortName(),
                    refset.getRefsetId(), refset.getVersionDate());
            }
        }

        service.commit();

        LOG.info("Finished sync of Refset Agent");
    }

    /**
     * Analyze non rtt based refset ids.
     *
     * @param service the service
     * @return the list
     * @throws Exception the exception
     */
    private List<String> analyzeNonRttBasedRefsetIds(final TerminologyService service) throws Exception {

        final List<String> addedOrInactivatedRefsetIds = new ArrayList<>();

        final Map<Edition, Set<SyncRefsetMetadata>> editionRefsetMap = new HashMap<>();

        for (final SyncRefsetMetadata refset : nonRttBasedRefsets) {
            if (!editionRefsetMap.containsKey(refset.getEdition())) {
                editionRefsetMap.put(refset.getEdition(), new HashSet<>());
            }
        }

        nonRttBasedRefsets.stream().forEach(r -> editionRefsetMap.get(r.getEdition()).add(r));

        for (final Edition edition : editionRefsetMap.keySet()) {
            final List<Project> projects = ProjectService.getProjectsForEdition(service, edition.getId());
            if (projects.isEmpty()) {
                LOG.error("Edition: " + edition.getShortName() + " must have a project defined to have refsets imported. Create one in UI then resync.");
                continue;
            }

            final Project defaultProject = projects.iterator().next();

            editionRefsetMap.get(edition).stream().forEach(r -> addRefset(service, r, defaultProject));
            editionRefsetMap.get(edition).stream().forEach(r -> refsetProjectMap.put(r.getRefsetId(), defaultProject));

            STATISTICS.setRefsetIdsAdded(STATISTICS.getRefsetIdsAdded() + editionRefsetMap.get(edition).size());

            editionRefsetMap.get(edition).stream().filter(r -> termserverRefsetIdToRefsetVersionsDataMap.containsKey(r.getRefsetId())).forEach(
                r -> newlyCreatedAndUnchangedRefsetToVersionsMap.put(r.getRefsetId(), termserverRefsetIdToRefsetVersionsDataMap.get(r.getRefsetId()).keySet()));

            editionRefsetMap.get(edition).stream().filter(r -> termserverRefsetIdToRefsetVersionsDataMap.containsKey(r.getRefsetId())).forEach(
                r -> newlyCreatedAndUnchangedRefsetToVersionsMap.put(r.getRefsetId(), termserverRefsetIdToRefsetVersionsDataMap.get(r.getRefsetId()).keySet()));

            editionRefsetMap.get(edition).stream().forEach(r -> addedOrInactivatedRefsetIds.add(r.getRefsetId()));
        }

        return addedOrInactivatedRefsetIds;
    }

    /**
     * Analyze refsets ids.
     *
     * @param service the service
     * @return the list
     * @throws Exception the exception
     */
    private List<String> analyzeRttBasedRefsetsIds(final TerminologyService service) throws Exception {

        LOG.info("analyze refsetIds");

        final List<String> addedOrInactivatedRefsetIds = new ArrayList<>();
        final Set<String> termserverRefsetIds = termserverRefsetIdToRefsetVersionsDataMap.keySet();
        STATISTICS.setRefsetIdsSynced(termserverRefsetIds.size());

        final Map<String, Map<Long, Refset>> dbActiveRefsetIdToVersionRefsetMap = new HashMap<>();
        final Map<String, Map<Long, Refset>> dbInactiveRefsetIdToVersionRefsetMap = new HashMap<>();
        populateDbRefsetCache(service, dbActiveRefsetIdToVersionRefsetMap, dbInactiveRefsetIdToVersionRefsetMap);

        // Determine new, inactivated, and existing refsets (Based on refsetId and version/branch info)
        // Determine and create new refsets (where db versions are needed). These are identified by those not in active nor in inactive DB refsets)
        final List<String> addedRefsetSctIds = termserverRefsetIds.stream()
            .filter(refsetId -> !dbActiveRefsetIdToVersionRefsetMap.containsKey(refsetId) && !dbInactiveRefsetIdToVersionRefsetMap.containsKey(refsetId))
            .collect(Collectors.toList());

        addMultipleRefsets(service, addedRefsetSctIds);

        addedRefsetSctIds.stream().filter(refsetId -> termserverRefsetIdToRefsetVersionsDataMap.containsKey(refsetId))
        .forEach(refsetId -> newlyCreatedAndUnchangedRefsetToVersionsMap.put(refsetId, termserverRefsetIdToRefsetVersionsDataMap.get(refsetId).keySet()));

        // Note: No reason to support activated/inactivated in sync as will be handled strictly within RT2 DB?
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

        final List<Refset> dbRefsets = service.getAll(Refset.class);
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
        // refset versions.
        // At end, also see with making newlyActivated versions as something to compare 1:1.
        // Perform analysis on one version at a time.
        // Dev note: Stream ignores those that are listed in the new or inactivated refsetId list (activated will be processed for changes)
        if (addedOrInactivatedRefsetIds.isEmpty()) {

            // Nothing to do if there are no added/inactivated refsets
            return;
        }

        LOG.info(("analyze refset versions"));

        final Map<String, Map<Long, Refset>> dbActiveRefsetIdToVersionRefsetMap = new HashMap<>();
        final Map<String, Map<Long, Refset>> dbInactiveRefsetIdToVersionRefsetMap = new HashMap<>();
        populateDbRefsetCache(service, dbActiveRefsetIdToVersionRefsetMap, dbInactiveRefsetIdToVersionRefsetMap);

        for (final String refsetId : termserverRefsetIdToRefsetVersionsDataMap.keySet().stream()
            .filter(refsetId -> !addedOrInactivatedRefsetIds.contains(refsetId)).collect(Collectors.toList())) {

            STATISTICS.incrementRefsetVersionsSynced();

            final List<Long> activatedVersions = new ArrayList<>();
            final List<Long> inactivatedVersions = new ArrayList<>();
            final List<Long> existingInBothVersions = new ArrayList<>();
            final List<Long> modifiedDBVersions = new ArrayList<>();
            List<Long> unchangedVersions = new ArrayList<>();

            final Map<Long, SyncRefsetMetadata> termserverVersionDataMaps = termserverRefsetIdToRefsetVersionsDataMap.get(refsetId);
            final Set<Long> termserverVersions = termserverVersionDataMaps.keySet();

            // Determine and create new refsetVersions (not in active nor in inactive DB refsetVersions)
            final Set<Long> addedVersions = new HashSet<>();

            for (final Long tsVersion : termserverVersions) {

                if ((!dbActiveRefsetIdToVersionRefsetMap.containsKey(refsetId)
                    || dbActiveRefsetIdToVersionRefsetMap.get(refsetId).keySet().stream().noneMatch(dbVersion -> isRefsetVersionMatches(tsVersion, dbVersion)))
                    && (!dbInactiveRefsetIdToVersionRefsetMap.containsKey(refsetId) || dbInactiveRefsetIdToVersionRefsetMap.get(refsetId).keySet().stream()
                        .noneMatch(dbVersion -> isRefsetVersionMatches(tsVersion, dbVersion)))) {

                    addedVersions.add(tsVersion);
                }

            }

            addMultipleRefsets(service, addedVersions, termserverVersionDataMaps);

            // Activate previously inactivated refsetVersions. Note: Will log and update stats after remove those that were activatedAndModified
            if (dbInactiveRefsetIdToVersionRefsetMap.containsKey(refsetId)) {

                final List<Long> results = termserverVersions.stream()
                    .filter(version -> dbInactiveRefsetIdToVersionRefsetMap.get(refsetId).containsKey(version)).collect(Collectors.toList());
                activatedVersions.addAll(results);
                activatedVersions.stream().forEach(version -> {

                    getDbHandler().updateRefsetVersionStatus(service, refsetId, version, true);
                    STATISTICS.incrementRefsetVersionsReactivated();
                });
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

                inactivatedVersions.stream().forEach(version -> {

                    getDbHandler().updateRefsetVersionStatus(service, refsetId, version, false);
                    STATISTICS.incrementRefsetVersionsInactivated();
                });

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

                final List<Long> activatedAndModifiedVersions =
                    compareAndModifyRefsetVersions(service, dbRefsets, refsetId, activatedVersions, termserverVersionDataMaps);

                // Finalize those refset versions that were only activated (and not further modified)
                if (activatedVersions != null && !activatedVersions.isEmpty()) {

                    activatedAndModifiedVersions.stream().filter(n -> activatedVersions.contains(n)).forEach(version -> activatedVersions.remove(version));
                }

            }

            // Finally, populate newly CreatedAndUnchagned map to handle finalization
            if (!newlyCreatedAndUnchangedRefsetToVersionsMap.containsKey(refsetId)) {

                newlyCreatedAndUnchangedRefsetToVersionsMap.put(refsetId, new HashSet<>());
            }

            try {
                newlyCreatedAndUnchangedRefsetToVersionsMap.get(refsetId).addAll(addedVersions);
                newlyCreatedAndUnchangedRefsetToVersionsMap.get(refsetId).addAll(unchangedVersions);
            } catch (final Exception e) {
                LOG.error("Error adding all from addedVersions: {} & unchangedVersions: {}", addedVersions, unchangedVersions);
            }
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
    public void addMultipleRefsets(final TerminologyService service, final Set<Long> addedVersions,
        final Map<Long, SyncRefsetMetadata> termserverVersionDataMaps) throws Exception {

        addedVersions.stream().forEach(version -> addRefset(service, termserverVersionDataMaps.get(version)));
    }

    /**
     * Adds the multiple refsets.
     *
     * @param service the service
     * @param addedRefsetIds the added refset ids
     * @throws Exception the exception
     */
    public void addMultipleRefsets(final TerminologyService service, final List<String> addedRefsetIds) throws Exception {

        addedRefsetIds.stream().filter(refsetId -> termserverRefsetIdToRefsetVersionsDataMap.containsKey(refsetId))
        .forEach(refsetId -> termserverRefsetIdToRefsetVersionsDataMap.get(refsetId).keySet().stream()
            .forEach(version -> addRefset(service, termserverRefsetIdToRefsetVersionsDataMap.get(refsetId).get(version))));
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
     * @return the sets the
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

                final Set<SyncRefsetMetadata> refsetVersions =
                    filterEditionRefsetVersions(edition, filteredTermserverShortNameToVersionBranchMap.get(editionShortName));

                filteredRefsets.addAll(refsetVersions);
            }

        }

        if (filteredRefsets.isEmpty()) {

            LOG.info("No refsets to process. This may be odd, but will happen based on certain criteria "
                + "(such as having an edition without any non-core refset changes");
            return filteredRefsets;

        }

        LOG.info("Finished processing db branches across all editions with " + filteredRefsets.size() + " filtered refsets versions.");

        // Based on FILTERED_CODE_SYSTEMS which already filtered for active code systems
        termserverRefsetIdToRefsetVersionsDataMap = generateTermserverRefsetIdtoRefsetVersionsMap(filteredRefsets);

        if (termserverRefsetIdToRefsetVersionsDataMap.isEmpty()) {

            throw new Exception("termServerRefsetIdToRefsetVersionsDataMap should never be empty ");
        }

        // add final attributes including narrative, intentional refset definition clauses (if exists), and tags (if exists)
        finalizeRefsetMetadata(service);

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
    private void populateDbRefsetCache(final TerminologyService service, final Map<String, Map<Long, Refset>> dbActiveRefsetIdToVersionRefsetMap,
        final Map<String, Map<Long, Refset>> dbInactiveRefsetIdToVersionRefsetMap) throws Exception {

        final Set<Refset> dbActiveRefsets = new HashSet<>();
        final Set<Refset> dbInactiveRefsets = new HashSet<>();
        final List<Refset> dbRefsets = service.getAll(Refset.class);

        dbRefsets.stream().filter(r -> VersionStatus.PUBLISHED.getLabel().equals(r.getVersionStatus()) && r.isActive()).forEach(ar -> dbActiveRefsets.add(ar));
        dbRefsets.stream().filter(r -> VersionStatus.PUBLISHED.getLabel().equals(r.getVersionStatus()) && !r.isActive())
        .forEach(ir -> dbInactiveRefsets.add(ir));

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
        final List<Edition> dbEditions = readDbActiveEditions(service);

        if (!dbEditions.stream().anyMatch(e -> e.getShortName().equals(editionShortName))) {

            return null;
        }

        final Stream<Edition> editionStream = dbEditions.stream().filter(e -> e.getShortName().equals(editionShortName));
        final Edition edition = (Edition) getUtilities().validateMatches(editionStream, editionShortName);

        return edition;
    }

    /**
     * Initialize sync.
     *
     * @param service the service
     * @throws Exception the exception
     */
    private void initializeSync(final TerminologyService service) throws Exception {

        service.beginTransaction();

        refsetNameCache.clear();
        newlyCreatedAndUnchangedRefsetToVersionsMap.clear();
        dbProjectCache.clear();
        nonRttBasedRefsets.clear();

        RefsetMemberService.clearVersionsWithChanges();
    }

    /**
     * Generate termserver refset idto refset versions map.
     *
     * @param filteredRefsets the filtered refsets
     * @return the map
     */
    // RefsetId to map of Dates to RefsetMetadata
    private Map<String, Map<Long, SyncRefsetMetadata>> generateTermserverRefsetIdtoRefsetVersionsMap(final Set<SyncRefsetMetadata> filteredRefsets) {

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
    private List<Long> compareAndModifyRefsetVersions(final TerminologyService service, final List<Refset> dbRefsets, final String refsetId,
        final List<Long> versionDatesToCompare, final Map<Long, SyncRefsetMetadata> termserverPairDataMap) throws Exception {

        final List<Long> modifiedVersions = new ArrayList<>();

        final List<Refset> dbVersions = dbRefsets.stream().filter(r -> r.getRefsetId().equals(refsetId)).collect(Collectors.toList());

        // Process one version at a time
        for (final long testingVersionDate : versionDatesToCompare) {

            // Find associated DB refset
            final Stream<Refset> dbVersionStream = dbVersions.stream().filter(dbr -> dbr.getVersionDate().getTime() == testingVersionDate);
            final Refset modifyingVersion = (Refset) getUtilities().validateMatches(dbVersionStream, refsetId + " / " + testingVersionDate);

            final List<Long> matchingTermserverRefsetVersionData = termserverPairDataMap.keySet().stream()
                .filter(termserverVersion -> (isRefsetVersionMatches(termserverVersion, testingVersionDate))).collect(Collectors.toList());

            if (matchingTermserverRefsetVersionData.isEmpty()) {

                throw new Exception("Compare Refset Version - Failed to find find expected the termserver pairing for refsetId/testingVersionDate: " + refsetId
                    + " / " + testingVersionDate);
            }

            final SyncRefsetMetadata termserverRefsetMetadata = termserverPairDataMap.get(matchingTermserverRefsetVersionData.iterator().next());
            final String termserverRefsetName = determineRefsetName(termserverRefsetMetadata);
            final String termserverRefsetBranch = termserverRefsetMetadata.getBranchPath();
            final String termserverRefsetModuleId = termserverRefsetMetadata.getModuleId();

            // Start comparison
            boolean modificationMade = false;

            if (modifyingVersion.getName() == null
                || isDifferentAttribute(refsetId + " / " + testingVersionDate, "Refset name ", modifyingVersion.getName(), termserverRefsetName)) {

                modifyingVersion.setName(termserverRefsetName);
                modificationMade = true;
            }

            if (modifyingVersion.getBranchPath() == null
                || isDifferentAttribute(refsetId + " / " + testingVersionDate, "Refset branch ", modifyingVersion.getBranchPath(), termserverRefsetBranch)) {

                if (modifyingVersion.getBranchPath() != null) {

                    LOG.error("Likely an error as refsetId/version " + refsetId + "/" + testingVersionDate
                        + " shouldn't be able to change their branch path from '" + modifyingVersion.getBranchPath() + "' to '" + termserverRefsetBranch + "'");
                }

                modifyingVersion.setBranchPath(termserverRefsetBranch);
                modificationMade = true;
            }

            if (modifyingVersion.getModuleId() == null
                || isDifferentAttribute(refsetId + " / " + testingVersionDate, "Refset moduleId ", modifyingVersion.getModuleId(), termserverRefsetModuleId)) {

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

        if (!refsetNameCache.containsKey(refsettermserverData.getRefsetId())) {

            final String refsetName =
                lookupRefsetName(refsettermserverData.getRefsetId(), refsettermserverData.getEdition(), refsettermserverData.getBranchPath());

            refsetNameCache.put(refsettermserverData.getRefsetId(), refsetName);
        }

        return refsetNameCache.get(refsettermserverData.getRefsetId());
    }

    /**
     * Filter edition refset versions.
     *
     * @param edition the edition
     * @param termserverVersionBranchMap the termserver version branch map
     * @return the sets the
     * @throws Exception the exception
     */
    private Set<SyncRefsetMetadata> filterEditionRefsetVersions(final Edition edition, final SortedMap<Long, String> termserverVersionBranchMap)
        throws Exception {

        final Set<SyncRefsetMetadata> filteredRefsets = new HashSet<>();

        for (final String moduleId : edition.getModules()) {

            final JsonNode refsetMembersMainBranchRoot = getTermserverRefsetVersionMembers(edition.getBranch(), moduleId);
            final Map<String, Integer> validRefsetsCountMap = determineRefsetCounts(refsetMembersMainBranchRoot);

            for (final long versionDate : termserverVersionBranchMap.keySet()) {

                final Iterator<JsonNode> refsetIterator = refsetMembersMainBranchRoot.get("referenceSets").iterator();

                while (refsetIterator != null && refsetIterator.hasNext()) {

                    final JsonNode refsetNode = refsetIterator.next();

                    // Check if should process Refset
                    if (refsetNode != null) {

                        final String refsetId = refsetNode.get("conceptId").asText();

                        if (getTestingRefset() != null && !refsetId.equals(getTestingRefset())) {

                            continue;
                        }

                        if (isRefsetToProcess(refsetId, edition, validRefsetsCountMap, filteredRefsets)) {

                            final String termserverRefsetBranchPath = termserverVersionBranchMap.get(versionDate);
                            final Set<Long> termserverEditionBranchDates = termserverVersionBranchMap.keySet();

                            // If perVersionSync, then create version per branch and return. Otherwise, determine if changes exist in this version
                            if (getIsPerVersionSync()
                                || versionHasChanges(refsetId, versionDate, termserverRefsetBranchPath, edition.getName(), termserverEditionBranchDates)) {

                                final SyncRefsetMetadata refsetMetadata = new SyncRefsetMetadata(refsetNode, edition, termserverVersionBranchMap.keySet(),
                                    versionDate, termserverRefsetBranchPath, moduleId);

                                // Found a refset to process later on
                                filteredRefsets.add(refsetMetadata);
                            }
                        }
                    }
                }
            }
        }

        return filteredRefsets;
    }

    /**
     * Determine refsets branch.
     *
     * @param edition the edition
     * @return the string
     * @throws Exception the exception
     */
    private String determineRefsetsBranch(final Edition edition) throws Exception {

        final String url = SnowstormConnection.getBaseUrl() + "branches/{branch}/children?immediateChildren=true&page=0&size=100";
        LOG.info("getRefsetMembers URL: " + url.replace("{branch}", edition.getBranch()));

        try (final Response response = SnowstormConnection.getResponse(url.replace("{branch}", edition.getBranch()))) {

            if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {

                if (edition.getBranch().startsWith("MAIN")) {

                    throw new Exception("Unable to process edition called with: " + url.replace("{branch}", edition.getBranch()));
                } else {

                    return null;
                }

            }

            // get RefSets from edition as long as a) active & b) not a core refset
            final String resultString = SnowstormConnection.readEntityAsString(response);
            final ObjectMapper mapper = ThreadLocalMapper.get();

            final JsonNode root = mapper.readTree(resultString);

            final Iterator<JsonNode> branchIterator = root.iterator();

            while (branchIterator.hasNext()) {

                final JsonNode branchNode = branchIterator.next();

                final String path = branchNode.get("path").asText();

                if (path.toLowerCase().endsWith("refsets")) {
                    return path;
                }

            }
        }

        LOG.error("Unable to identify refsets branch for edition: " + edition.getName());

        return null;
    }

    /**
     * Determine refset counts.
     *
     * @param refsetMembersRoot the refset members root
     * @return the map
     * @throws Exception the exception
     */
    private Map<String, Integer> determineRefsetCounts(final JsonNode refsetMembersRoot) throws Exception {

        final Map<String, Integer> refsetCountMap = new HashMap<>();

        final JsonNode refsetCountsMap = refsetMembersRoot.get("memberCountsByReferenceSet");
        final Iterator<Entry<String, JsonNode>> refsetCountsIterator = refsetCountsMap.fields();

        while (refsetCountsIterator != null && refsetCountsIterator.hasNext()) {

            final Entry<String, JsonNode> refsetCountNode = refsetCountsIterator.next();

            // Check if should process Refset
            final String refsetId = refsetCountNode.getKey();
            final Integer memberCount = refsetCountNode.getValue().asInt();

            refsetCountMap.put(refsetId, memberCount);
        }

        return refsetCountMap;
    }

    /**
     * Returns the termserver refset version members.
     *
     * @param branch the branch
     * @param moduleId the module id
     * @return the termserver refset version members
     * @throws Exception the exception
     */
    private JsonNode getTermserverRefsetVersionMembers(final String branch, final String moduleId) throws Exception {

        // Process edition
        final String url = SnowstormConnection.getBaseUrl() + "browser/{branch}/members?active=true&module=" + moduleId + "&referenceSet=%3C"
            + RefsetService.SIMPLE_TYPE_REFERENCE_SET;

        try (final Response response = SnowstormConnection.getResponse(url.replace("{branch}", branch))) {

            LOG.info("getRefsetMembers URL: " + url.replace("{branch}", branch));

            if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {

                if (branch.startsWith("MAIN")) {

                    throw new Exception("Unable to process edition called with: " + url.replace("{branch}", branch));
                } else {

                    return null;
                }

            }

            // get RefSets from edition as long as a) active & b) not a core refset
            final String resultString = SnowstormConnection.readEntityAsString(response);
            final ObjectMapper mapper = ThreadLocalMapper.get();
            final JsonNode root = mapper.readTree(resultString);

            return root;
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
    private boolean versionHasChanges(final String refsetId, final long versionDate, final String termserverRefsetBranchPath, final String editionName,
        final Set<Long> termserverEditionBranchDates) throws Exception {

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

                    LOG.error("Ignoring this member as have bad content - Can't have refsets with a member that has an effectiveDate:  " + refsetVersionDate
                        + " that is AFTER the editionDate: " + editionDate);
                    continue;
                }

                if (earliestPublishedVersionDate < 0 || editionDate < earliestPublishedVersionDate) {

                    earliestPublishedVersionDate = editionDate;
                }

            }

            if (earliestPublishedVersionDate < 0) {

                throw new Exception("Bad content likely brought us here as unable to find a valid earliest w/ refsetId: " + refsetId + " & versionDate: "
                    + versionDate + " & branchPath: " + termserverRefsetBranchPath);
            }

            refsetVersionDate = earliestPublishedVersionDate;
        }

        updatedVersionDate = refsetVersionDate;

        if (!termserverEditionBranchDates.contains(updatedVersionDate)) {

            LOG.info(" Don't add refset versions that don't have corresponding termserver -based edition versions with Refset / and VersionDate pair: "
                + refsetId + " / " + updatedVersionDate);

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

            final String resultString = SnowstormConnection.readEntityAsString(response);
            final ObjectMapper mapper = ThreadLocalMapper.get();
            final JsonNode conceptNode = mapper.readTree(resultString);

            final Iterator<JsonNode> descriptionIterator = conceptNode.get("descriptions").iterator();

            while (descriptionIterator.hasNext()) {

                final JsonNode descriptionNode = descriptionIterator.next();

                if (descriptionNode.get("type").asText().equals("SYNONYM")
                    && LanguageUtility.ENGLISH_LANGUAGE_CODE.equals(descriptionNode.get("lang").asText())) {

                    final JsonNode acceptabilityMap = descriptionNode.get("acceptabilityMap");

                    if (!acceptabilityMap.isEmpty() && acceptabilityMap.has(LanguageUtility.DEFAULT_LANGUAGE_REFSET_US)
                        && LanguageUtility.PREFERRED_TERM_EN.equals(acceptabilityMap.get(LanguageUtility.DEFAULT_LANGUAGE_REFSET_US).asText())) {

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

        // If already processed refset, just return already identified project
        if (refsetProjectMap.containsKey(metadata.getRefsetId())) {
            return refsetProjectMap.get(metadata.getRefsetId());
        }

        if (dbProjectCache.isEmpty()) {
            dbProjectCache = service.getAll(Project.class);
        }

        String projectName = null;
        String projectDescription = null;
        // Need to determine the project name associated with the refset.
        if (getUtilities().getPropertyReader().getSctIdToProjectIdMap().containsKey(metadata.getRefsetId())) {

            // If refset is from RTT, then use RTT's project name.
            final String rttProjectId = getUtilities().getPropertyReader().getSctIdToProjectIdMap().get(metadata.getRefsetId());

            if (getUtilities().getPropertyReader().getProjectIdToProjectInfoMap().containsKey(rttProjectId)) {
                if (getUtilities().getPropertyReader().getProjectIdToProjectInfoMap().get(rttProjectId).keySet().size() != 1) {
                    LOG.error("Have unexpected number of names/descriptions for projectId: " + rttProjectId + " with names: "
                        + getUtilities().getPropertyReader().getProjectIdToProjectInfoMap().get(rttProjectId).keySet());

                    return null;
                }

                final Map<String, String> rttProjectInfo = getUtilities().getPropertyReader().getProjectIdToProjectInfoMap().get(rttProjectId);
                projectName = rttProjectInfo.keySet().iterator().next();
                projectDescription = rttProjectInfo.get(projectName);

                Project matchingProject = null;
                for (final Project project : dbProjectCache) {
                    if (project.getName().equals(projectName) && project.getEditionId().equals(metadata.getEdition().getId())) {
                        matchingProject = project;
                        refsetProjectMap.put(metadata.getRefsetId(), matchingProject);

                        return matchingProject;
                    }
                }

                // Matching type not found, create new project
                final Project addedProject = getDbHandler().addProject(service, projectName, projectDescription, metadata.getEdition(),
                    CrowdGroupNameAlgorithm.getProjectString(projectName));

                refsetProjectMap.put(metadata.getRefsetId(), addedProject);
            } else {

                throw new Exception(
                    "Refset found in RTT, but not enough info has been pulled to be able to identify the associated project: " + metadata.getRefsetId());
            }
        } else {
            nonRttBasedRefsets.add(metadata);
            return null;
        }

        dbProjectCache.add(refsetProjectMap.get(metadata.getRefsetId()));

        return refsetProjectMap.get(metadata.getRefsetId());
    }

    /**
     * Update refsets with values (including Projects) from json and also identify latestVersion, but do not persist at this point.
     *
     * @param service the service
     * @throws Exception the exception
     */
    private void finalizeRefsetMetadata(final TerminologyService service) throws Exception {

        final Map<String, Long> latestVersionCache = new HashMap<>();
        final Set<Refset> refsetsUpdated = new HashSet<>();

        getUtilities().getPropertyReader().parseRttData();

        // Refresh DB cache with additions just made
        final Map<String, Map<Long, Refset>> dbActiveRefsetIdToVersionRefsetMap = new HashMap<>();
        final Map<String, Map<Long, Refset>> dbInactiveRefsetIdToVersionRefsetMap = new HashMap<>();
        populateDbRefsetCache(service, dbActiveRefsetIdToVersionRefsetMap, dbInactiveRefsetIdToVersionRefsetMap);

        for (final String refsetId : dbActiveRefsetIdToVersionRefsetMap.keySet()) {

            for (final long version : dbActiveRefsetIdToVersionRefsetMap.get(refsetId).keySet()) {

                try {

                    final Refset refset = dbActiveRefsetIdToVersionRefsetMap.get(refsetId).get(version);

                    // Actually process the refset here
                    final Refset updatedRefset = finalizeRefset(service, refset, latestVersionCache);

                    refsetsUpdated.add(updatedRefset);

                } catch (final Exception e) {

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
        final Set<Refset> refsetsFinalized = new HashSet<>();

        for (final Refset dbRefset : refsetsUpdated) {

            if (latestVersionCache.containsKey(dbRefset.getRefsetId())) {

                for (final String refsetId : latestVersionCache.keySet()) {

                    if (dbRefset.getRefsetId().equals(refsetId)
                        && isRefsetVersionMatches(latestVersionCache.get(refsetId), dbRefset.getVersionDate().getTime())) {

                        dbRefset.setLatestPublishedVersion(true);
                        refsetsFinalized.add(dbRefset);
                        break;
                    }

                }

            }

        }

        // Persist changes across all Refsets
        refsetsUpdated.addAll(refsetsFinalized);

        // Updates the metadata, not necessarily modified refsets in true sense
        LOG.info("Updating refset metadata.");
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
            updatedRefset.setNarrative("No corresponding reference set information found on RTT for " + refset.getRefsetId());

        }

        // Keep track of the latest version per refsetId
        if (!latestVersionCache.containsKey(updatedRefset.getRefsetId())
            || latestVersionCache.get(updatedRefset.getRefsetId()) < updatedRefset.getVersionDate().getTime()) {

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

        final SimpleDateFormat sdf = new SimpleDateFormat(getUtilities().getIsoDateTimeFormat());

        for (final String rttId : rttIds) {

            if (getUtilities().getPropertyReader().getRttIdToRefsetJsonMap().containsKey(rttId)) {

                final String refsetJsonString = getUtilities().getPropertyReader().getRttIdToRefsetJsonMap().get(rttId);

                final ObjectMapper mapper = ThreadLocalMapper.get();
                final JsonNode refsetJson = mapper.readTree(refsetJsonString);

                final long rttDataRefsetVersion = sdf.parse(refsetJson.get("version").asText()).getTime();

                if (rttDataRefsetVersion < 0 || rttDataRefsetVersion == refset.getVersionDate().getTime()) {

                    // Populate tags from contents of refsetToTags.txt file
                    if (getUtilities().getPropertyReader().getRefsetSctToTagsMap().containsKey(refset.getRefsetId())) {

                        getUtilities().getPropertyReader().getRefsetSctToTagsMap().get(refset.getRefsetId()).stream().forEach(tag -> refset.getTags().add(tag));
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
     * @param edition the short name
     * @param refsetCountMap the refset count map
     * @param filteredRefsets the filtered refsets
     * @return <code>true</code> if so, <code>false</code> otherwise
     * @throws Exception the exception
     */
    private boolean isRefsetToProcess(final String refsetId, final Edition edition, final Map<String, Integer> refsetCountMap,
        final Set<SyncRefsetMetadata> filteredRefsets) throws Exception {

        // Only continue processing refset if it is created within the current edition's modules
        // No populated refsets within edition
        if (refsetCountMap == null || refsetCountMap.isEmpty() || !refsetCountMap.containsKey(refsetId) || refsetCountMap.get(refsetId) == null) {

            LOG.info("Ignoring refset: " + refsetId + " as either not in module or no members associated with it at this point");
            return false;
        }

        // Don't bring in refsets from the intensional refsets list
        if (RefsetService.getRttIntensionalRefsets().contains(refsetId)) {
            return false;
        }

        if (filteredRefsets.stream().anyMatch(meta -> meta.getRefsetId().equals(refsetId))) {
            // Refset was previously stored, so must keep updating its contents regardless of the below
            return true;
        }

        // Finally, ensure there aren't other special refset restrictions.
        // Restriction 1: Don't import any version of those refsets whose latest version contains more than 10k members
        final int maxMembersSupported = Integer.parseInt(PropertyUtility.getProperty("refset.service.size.max"));
        final String commaSeparatedLargeRefsetsString = PropertyUtility.getProperty("refset.service.size.max.override");
        final Set<String> permittedLargeRefsets = new HashSet<>();
        Collections.addAll(permittedLargeRefsets, commaSeparatedLargeRefsetsString.split(","));

        if (refsetCountMap.get(refsetId) > maxMembersSupported && !permittedLargeRefsets.contains(refsetId)) {

            LOG.info("Ignoring refset: " + refsetId + " given it contains more than {} members", maxMembersSupported
                + " and is not in the list of permitted refsets exceeding the maximum permitted member size: " + commaSeparatedLargeRefsetsString);
            return false;
        }

        return true;
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
        final List<Edition> dbActiveEditions = readDbActiveEditions(service);

        LOG.info("Finding branches for editions: {}",
            dbActiveEditions.stream().collect(StringBuilder::new, (x, y) -> x.append(y.getName()), (a, b) -> a.append(", ").append(b)).toString());

        final Map<String, Set<String>> ignoredBranches = new HashMap<>();

        final SimpleDateFormat BRANCH_DATE_FORMAT = new SimpleDateFormat(DateUtility.DATE_FORMAT_REVERSE);

        for (final JsonNode codeSystem : codeSystems) {

            final String editionName = codeSystem.has("name") ? codeSystem.get("name").asText() : "";
            final String shortName = codeSystem.has("shortName") ? codeSystem.get("shortName").asText() : "";

            // Only process code systems that are now active in RT2.
            if (dbActiveEditions.stream().noneMatch(e -> e.getShortName().equals(shortName))) {
                continue;
            }

            final String parentBranchPath = codeSystem.has("branchPath") ? codeSystem.get("branchPath").asText() : "";

            LOG.info("Identifying CodeSystem branches for: " + editionName);

            final String genericUrl = SnowstormConnection.getBaseUrl() + "branches/{branch}/children";

            final SortedMap<Long, String> versionToBranchMap = new TreeMap<>();
            LOG.info(" Branch children Url: " + genericUrl.replace("{branch}", parentBranchPath));

            try (final Response response = SnowstormConnection.getResponse(genericUrl.replace("{branch}", parentBranchPath))) {

                final String resultString = SnowstormConnection.readEntityAsString(response);
                final ObjectMapper mapper = ThreadLocalMapper.get();
                final JsonNode root = mapper.readTree(resultString);

                // get RefSets from edition as long as a) active & b) within
                // edition's module
                final Iterator<JsonNode> branchIterator = root.iterator();

                while (branchIterator.hasNext()) {

                    final JsonNode child = branchIterator.next();
                    final String childBranch = child.get("path").asText();
                    String childDate = childBranch.replace(parentBranchPath, "");

                    if (childDate.startsWith("/")) {

                        childDate = childDate.substring(1);
                    }

                    // Since grabbing all children branches, avoid
                    // attempting to parse extensions i.e. MAIN/SNOMEDCT-US
                    if (childDate.matches("^\\d{4}-\\d{2}-\\d{2}$")) {

                        final long branchDate = BRANCH_DATE_FORMAT.parse(childDate).getTime();

                        if (branchDate < PRE_SNOMED_SUPPORTED_RELEASES) {

                            continue;
                        }

                        // Support release Branch
                        versionToBranchMap.put(branchDate, childBranch);

                    } else {

                        if (!ignoredBranches.containsKey(editionName)) {

                            ignoredBranches.put(editionName, new HashSet<>());
                        }

                        ignoredBranches.get(editionName).add(childDate);
                    }

                }

            }

            // Make final version the main branch
            // versionToBranchMap.put(MAIN_BRANCH_VERSION, parentBranchPath);

            editionToDateBranchMap.put(shortName, versionToBranchMap);
        }

        // For any yet-to-be-released refsets, also check the "project branch".
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
            final String moduleId = syncRefsetMetadata.getModuleId();
            final String branchPath = syncRefsetMetadata.getBranchPath();
            final String refsetName = determineRefsetName(syncRefsetMetadata);
            final long version = syncRefsetMetadata.getVersion();

            final Project project = determineProject(service, syncRefsetMetadata);

            if (project == null) {

                return null;
            }

            // Refset coming from term server, so already published
            final Refset newRefset = getDbHandler().addRefset(service, refsetName, refsetId, moduleId, version, RefsetType.EXTENSIONAL, branchPath,
                VersionStatus.PUBLISHED, WorkflowStatus.PUBLISHED, project);

            return newRefset;
        } catch (final Exception e) {

            LOG.error("Unable to add refset: " + syncRefsetMetadata.getRefsetId());
            e.printStackTrace();
            return null;
        }

    }

    /**
     * Adds the refset.
     *
     * @param service the service
     * @param syncRefsetMetadata the sync refset metadata
     * @param project the project
     * @return the refset
     */
    private Refset addRefset(final TerminologyService service, final SyncRefsetMetadata syncRefsetMetadata, final Project project) {

        try {

            final String refsetId = syncRefsetMetadata.getRefsetId();
            final String moduleId = syncRefsetMetadata.getModuleId();
            final String branchPath = syncRefsetMetadata.getBranchPath();
            final String refsetName = determineRefsetName(syncRefsetMetadata);
            final long version = syncRefsetMetadata.getVersion();

            // Refset coming from term server, so already published
            final Refset newRefset = getDbHandler().addRefset(service, refsetName, refsetId, moduleId, version, RefsetType.EXTENSIONAL, branchPath,
                VersionStatus.PUBLISHED, WorkflowStatus.PUBLISHED, project);

            return newRefset;
        } catch (final Exception e) {

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
