package org.ihtsdo.refsetservice.sync;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status.Family;

import org.ihtsdo.refsetservice.model.DefinitionClause;
import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.sync.util.SyncRefsetMetadata;
import org.ihtsdo.refsetservice.terminologyservice.RefsetMemberService;
import org.ihtsdo.refsetservice.terminologyservice.RefsetService;
import org.ihtsdo.refsetservice.terminologyservice.SnowstormConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class SyncRefsetAgent extends SyncAgent {

    private final Logger logger = LoggerFactory.getLogger(SyncRefsetAgent.class);

    private final Set<Refset> snowstormRefsets = new HashSet<>();

    private final Set<SyncRefsetMetadata> filteredRefsets = new HashSet<>();

    private final Map<String, String> refsetToModuleMap = new HashMap<String, String>();

    private final Map<String, Edition> refsetEditions = new HashMap<>();

    private Map<String, Map<Long, SyncRefsetMetadata>> termserverRefsetIdToRefsetVersionsDataMap;

    private Map<String, Map<Long, Refset>> activeDbRefsetIdToVersionRefsetMap;

    private Map<String, Map<Long, Refset>> inactiveDbRefsetIdToVersionRefsetMap;

    private Map<String, Set<Long>> newlyCreatedAndUnchangedRefsetToVersionsMap = new HashMap<>();

    private List<Refset> dbRefsets = new ArrayList<>();

    private List<Edition> dbEditions = new ArrayList<>();

    private Set<Refset> activeDbRefsets = new HashSet<>();

    private Set<Refset> inactiveDbRefsets = new HashSet<>();

    // rttProject Id to Rt2Project
    private final static Map<String, Project> rttProjects = new HashMap<>();

    public void sync() throws Exception {

        initializeSync();

        // Determine code systems to sync
        analyzeCodeSystemBranches();

        List<String> addedOrInactivatedRefsetIds = analyzeRefsetsIds();

        analyzeRefsetVersions(addedOrInactivatedRefsetIds);

        // Final step for project connection
        finalizeNewOrChangedRefsets();

    }

    private List<String> analyzeRefsetsIds() throws Exception {
        final List<String> addedOrInactivatedRefsetIds = new ArrayList<>();
        final Set<String> termserverRefsetIds = termserverRefsetIdToRefsetVersionsDataMap.keySet();

        logger.info(("analyze refsetIds"));

        try (TerminologyService service = new TerminologyService()) {
            // Determine new, inactivated, and existing refsets (Based on refsetId and version/branch info)
            // Determine and create new refsets (where db versions are needed). These are identified by those not in active nor in inactive DB refsets)
            List<String> addedRefsetIds = termserverRefsetIds.stream()
                    .filter(refsetId -> !activeDbRefsetIdToVersionRefsetMap.containsKey(refsetId) && !inactiveDbRefsetIdToVersionRefsetMap.containsKey(refsetId)).collect(Collectors.toList());

            addedRefsetIds.stream().filter(refsetId -> termserverRefsetIdToRefsetVersionsDataMap.containsKey(refsetId)).forEach(refsetId -> termserverRefsetIdToRefsetVersionsDataMap.get(refsetId)
                    .keySet().stream().forEach(version -> addRefset(termserverRefsetIdToRefsetVersionsDataMap.get(refsetId).get(version))));

            logger.debug("ccc New RefsetIds: " + addedRefsetIds);
            addedRefsetIds.stream().filter(refsetId -> termserverRefsetIdToRefsetVersionsDataMap.containsKey(refsetId))
                    .forEach(refsetId -> newlyCreatedAndUnchangedRefsetToVersionsMap.put(refsetId, termserverRefsetIdToRefsetVersionsDataMap.get(refsetId).keySet()));
            statistics.setRefsetIdsAdded(addedRefsetIds.size());

            // Activate previously inactivated refsets. Note: Will log and update stats after remove those that were activatedAndModified
            // TODO: Define solution although for now simply activating

            for (String refsetId : termserverRefsetIds) {
                logger.debug("ggg refsetId: " + refsetId);
                logger.debug("ggg dbRefsetIdToInactiveRefsetVersionsMap.containsKey(refsetId): " + inactiveDbRefsetIdToVersionRefsetMap.containsKey(refsetId));
                logger.debug("ggg dbRefsetIdToActiveRefsetVersionsMap.containsKey(refsetId): " + activeDbRefsetIdToVersionRefsetMap.containsKey(refsetId));

                if (activeDbRefsetIdToVersionRefsetMap.containsKey(refsetId)) {
                    logger.debug("ggg Is in Active DB");
                } else {
                    logger.debug("ggg Not in Active DB");
                }

            }
            List<String> activatedRefsetIds = termserverRefsetIds.stream()
                    .filter(refsetId -> inactiveDbRefsetIdToVersionRefsetMap.containsKey(refsetId) && (!activeDbRefsetIdToVersionRefsetMap.containsKey(refsetId)
                            || (activeDbRefsetIdToVersionRefsetMap.get(refsetId).keySet().stream().noneMatch(version -> activeDbRefsetIdToVersionRefsetMap.get(refsetId).get(version).isActive()))))
                    .collect(Collectors.toList());
            logger.debug("ccc activatedRefsetIds v1 RefsetIds: " + activatedRefsetIds);
            activatedRefsetIds.stream().forEach(refsetId -> dbHandler.updateRefsetIdsStatus(refsetId, true));

            // Inactivate active DB refsets that are not in snowstorm
            // TODO: Define solution although for now simply inactivating
            List<String> inactivatedRefsetIds = new ArrayList<>();
            logger.debug("ddd proessing inactiveRefsetIds");
            for (String refsetId : activeDbRefsetIdToVersionRefsetMap.keySet()) {
                logger.debug("ddd refsetId " + refsetId);

                if (isTesting() && testingRefset != null && !testingRefset.equals(refsetId)) {
                    continue;
                }

                for (long dbVersion : activeDbRefsetIdToVersionRefsetMap.get(refsetId).keySet()) {
                    logger.debug("ddd version " + dbVersion);
                    logger.debug("ddd termServerRefsetIdToRefsetVersionsDataMap.containsKey(refsetId): " + termserverRefsetIdToRefsetVersionsDataMap.containsKey(refsetId));
                    // logger.debug("ddd termServerRefsetIdToRefsetVersionsDataMap.get(refsetId).containsKey(version): " +
                    // termServerRefsetIdToRefsetVersionsDataMap.get(refsetId).containsKey(dbVersion));

                    logger.debug("ddd termserverRefsetIds: " + termserverRefsetIds);

                    if (termserverRefsetIdToRefsetVersionsDataMap.containsKey(refsetId)) {

                        logger.debug("ddd termServerRefsetIdToRefsetVersionsDataMap.get(refsetId).keySet(): " + termserverRefsetIdToRefsetVersionsDataMap.get(refsetId).keySet());
                    }

                    if (!termserverRefsetIdToRefsetVersionsDataMap.containsKey(refsetId)
                            || termserverRefsetIdToRefsetVersionsDataMap.get(refsetId).keySet().stream().noneMatch(tsVersion -> dbVersion == tsVersion)) {
                        inactivatedRefsetIds.add(refsetId);
                    }
                }
            }

            inactivatedRefsetIds.stream().forEach(refsetId -> dbHandler.updateRefsetIdsStatus(refsetId, false));
            logger.debug("ccc Inactivated RefsetIds: " + inactivatedRefsetIds);
            statistics.setRefsetIdsInactivated(inactivatedRefsetIds.size());

            // Determine refsetIds that were just activated to see if there are any other changes necessary
            logger.info("ddd About to perform comparison on activated RefsetIds");
            List<String> activatedAndModifiedRefsetIds = new ArrayList<>();

            for (String refsetId : activatedRefsetIds) {

                List<Long> activatedVersionDates = new ArrayList<>(termserverRefsetIdToRefsetVersionsDataMap.get(refsetId).keySet());
                java.util.Collections.sort(activatedVersionDates);
                logger.info("ddd activatedVersionDates: " + activatedVersionDates);

                List<Long> activatedAndModifiedVersionDates = compareAndModifyRefsetVersions(refsetId, activatedVersionDates, termserverRefsetIdToRefsetVersionsDataMap.get(refsetId));
                logger.info("ddd activatedAndModifiedVersionDates: " + activatedAndModifiedVersionDates);

                if (!activatedAndModifiedVersionDates.isEmpty()) {

                    activatedAndModifiedRefsetIds.add(refsetId);
                }
            }
            logger.debug("ccc ActivatedAndModified RefsetIds: " + activatedAndModifiedRefsetIds);

            statistics.setRefsetIdsActivatedAndModified(activatedAndModifiedRefsetIds.size());

            // Finalize those refset versions that were only activated (and not further modified)
            activatedAndModifiedRefsetIds.stream().forEach(n -> activatedRefsetIds.remove(n));
            statistics.setRefsetIdsActivated(activatedRefsetIds.size());
            logger.debug("ccc Activated RefsetIds: " + activatedRefsetIds);

            addedOrInactivatedRefsetIds.addAll(addedRefsetIds);
            addedOrInactivatedRefsetIds.addAll(inactivatedRefsetIds);
            logger.debug("ddd newOrInactivatedRefsetIds: " + addedOrInactivatedRefsetIds);

            return addedOrInactivatedRefsetIds;
        }
    }

    private void analyzeRefsetVersions(List<String> addedOrInactivatedRefsetIds) throws Exception {

        // Having identified new refsets (where db versions are new), as well as activated/inactivated refsets (where once again db versions are new), now check refset
        // versions.
        // At end, also see with making newlyActivated versions as something to compare 1:1.
        // Perform analysis on one version at a time.
        // Dev note: Stream ignores those that are listed in the new or inactivated refsetId list (activated will be processed for changes)
        logger.info(("analyze refset versions"));
        logger.debug("fff ignoring these added or inactved (from when analyzed refsetIds): " + addedOrInactivatedRefsetIds);
        termserverRefsetIdToRefsetVersionsDataMap.keySet().stream()
                .forEach(refsetId -> statistics.incrementRefsetVersionsSynced(termserverRefsetIdToRefsetVersionsDataMap.get(refsetId).keySet().size()));

        for (String refsetId : termserverRefsetIdToRefsetVersionsDataMap.keySet().stream().filter(refsetId -> !addedOrInactivatedRefsetIds.contains(refsetId)).collect(Collectors.toList())) {
            logger.debug("fff refsetId: " + refsetId);
            if (isTesting() && testingRefset != null && !testingRefset.equals(refsetId)) {
                continue;
            }

            List<Long> activatedVersions = new ArrayList<>();
            List<Long> inactivatedVersions = new ArrayList<>();
            List<Long> existingInBothVersions = new ArrayList<>();
            List<Long> modifiedDBVersions = new ArrayList<>();
            List<Long> unchangedVersions = new ArrayList<>();

            final Map<Long, SyncRefsetMetadata> termserverVersionDataMaps = termserverRefsetIdToRefsetVersionsDataMap.get(refsetId);
            final Set<Long> termserverVersions = termserverVersionDataMaps.keySet();

            // Determine and create new refsetVersions (not in active nor in inactive DB refsetVersions)
            List<Long> addedVersions =
                    termserverVersions.stream()
                            .filter(date -> (!activeDbRefsetIdToVersionRefsetMap.containsKey(refsetId) || !activeDbRefsetIdToVersionRefsetMap.get(refsetId).containsKey(date))
                                    && (!inactiveDbRefsetIdToVersionRefsetMap.containsKey(refsetId) || !inactiveDbRefsetIdToVersionRefsetMap.get(refsetId).containsKey(date)))
                            .collect(Collectors.toList());
            addedVersions.stream().forEach(version -> addRefset(termserverVersionDataMaps.get(version)));
            logger.debug("ccc New RefsetVersions size: " + addedVersions.size());
            statistics.incrementRefsetVersionsAdded(addedVersions.size());

            // Activate previously inactivated refsetVersions. Note: Will log and update stats after remove those that were activatedAndModified
            // TODO: Define solution although for now simply activating
            logger.debug("fff proessing activeRefsetIds");
            if (inactiveDbRefsetIdToVersionRefsetMap.containsKey(refsetId)) {
                logger.debug("fff dbRefsetIdToInactiveRefsetVersionsMap.get(refsetId).keySet(): " + inactiveDbRefsetIdToVersionRefsetMap.get(refsetId).keySet());

                List<Long> results = termserverVersions.stream().filter(version -> inactiveDbRefsetIdToVersionRefsetMap.get(refsetId).containsKey(version)).collect(Collectors.toList());
                activatedVersions.addAll(results);
                activatedVersions.stream().forEach(version -> dbHandler.updateRefsetVersionStatus(refsetId, version, true));
            }

            logger.debug("fff termserverVersions: " + termserverVersions);
            logger.debug("fff inactiveDbRefsetIdToVersionRefsetMap.containsKey(refsetId): " + inactiveDbRefsetIdToVersionRefsetMap.containsKey(refsetId));

            // Inactivate active DB refsetVersions that are not in snowstorm // TODO: Define solution although for now simply inactivating
            logger.debug("fff proessing inactiveRefsetIds");
            if (activeDbRefsetIdToVersionRefsetMap.containsKey(refsetId)) {

                for (long dbVersion : activeDbRefsetIdToVersionRefsetMap.get(refsetId).keySet()) {

                    boolean matchFound = false;
                    for (long termserverVersion : termserverVersions) {
                        if (dbVersion == termserverVersion) {
                            matchFound = true;

                        }
                    }

                    if (!matchFound) {
                        inactivatedVersions.add(dbVersion);

                    }
                }

                inactivatedVersions.stream().forEach(version -> dbHandler.updateRefsetVersionStatus(refsetId, version, false));
                logger.debug("ccc Inactivated Versions size: " + inactivatedVersions.size());
                statistics.incrementRefsetVersionsInactivated(inactivatedVersions.size());

                logger.debug("ppp refsetId" + refsetId);
                logger.debug("ppp termserverVersionDataMaps.keySet()" + termserverVersionDataMaps.keySet());
                logger.debug("ppp activeDbRefsetIdToVersionRefsetMap.keySet()" + activeDbRefsetIdToVersionRefsetMap.keySet());
                logger.debug("ppp activeDbRefsetIdToVersionRefsetMap.get(refsetId).keySet()" + activeDbRefsetIdToVersionRefsetMap.get(refsetId).keySet());
                // Determine Versions that are active in DB and found in snowstorm and compare for changes

                logger.debug("ppp termserverVersionDataMaps.keySet()" + termserverVersionDataMaps.keySet());

                for (long dbVersion : activeDbRefsetIdToVersionRefsetMap.get(refsetId).keySet()) {

                    boolean matchFound = false;

                    for (long termserverVersion : termserverVersionDataMaps.keySet()) {

                        if (dbVersion == termserverVersion) {
                            matchFound = true;

                        }
                    }

                    if (matchFound) {
                        existingInBothVersions.add(dbVersion);

                    }
                }
                // Compare in termserver & active in db
                logger.debug("ppp existingInBothVersions" + existingInBothVersions);
                modifiedDBVersions.addAll(compareAndModifyRefsetVersions(refsetId, existingInBothVersions, termserverVersionDataMaps));
                unchangedVersions = existingInBothVersions.stream().filter(e -> !modifiedDBVersions.contains(e)).collect(Collectors.toList());
                // Compare refsets (based on existing list of refsetId)

                logger.debug("ccc existing Versions size: " + existingInBothVersions.size());
                logger.debug("ccc Modified Versions size: " + modifiedDBVersions.size());
                logger.debug("ccc Unchanged Versions size: " + unchangedVersions.size());
                statistics.incrementRefsetVersionsModified(modifiedDBVersions.size());
                statistics.incrementRefsetVersionsUnchanged(unchangedVersions.size());
            } else {
                statistics.incrementRefsetVersionsUnchanged(termserverVersionDataMaps.keySet().size());
            }

            // Compare in termserver & newly activated in db
            if (!activatedVersions.isEmpty()) {
                List<Long> activatedAndModifiedVersions = compareAndModifyRefsetVersions(refsetId, activatedVersions, termserverVersionDataMaps);
                statistics.incrementRefsetVersionsActivatedAndModified(activatedAndModifiedVersions.size());

                // Finalize those refset versions that were only activated (and not further modified)
                if (activatedVersions != null && !activatedVersions.isEmpty()) {

                    activatedAndModifiedVersions.stream().filter(n -> activatedVersions.contains(n)).forEach(version -> activatedVersions.remove(version));
                    statistics.incrementRefsetVersionsActivated(activatedVersions.size());
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

    // RefsetId to map of Dates to dbRefset
    private Map<String, Map<Long, Refset>> generateDatabaseRefsetIdtoRefsetVersionsMap(Set<Refset> dbRefsets) {
        Map<String, Map<Long, Refset>> generatedMap = new HashMap<>();

        for (Refset dbRefset : dbRefsets) {

            if (!generatedMap.containsKey(dbRefset.getRefsetId())) {

                generatedMap.put(dbRefset.getRefsetId(), new HashMap<>());
            }

            generatedMap.get(dbRefset.getRefsetId()).put(dbRefset.getVersionDate().getTime(), dbRefset);
        }

        return generatedMap;

    }

    private void analyzeCodeSystemBranches() throws Exception {
        try (final TerminologyService service = new TerminologyService()) {

            // Determine all version dates, per edition, and mapped with the associated publication branch
            Map<String, SortedMap<Long, String>> filteredTermserverShortNameToVersionBranchMap = determineEditionBranches(filteredCodeSystems);

            logger.info("Gather refset data for each refset available with each edition's version for: " + filteredTermserverShortNameToVersionBranchMap.keySet());
            for (String editionShortName : filteredTermserverShortNameToVersionBranchMap.keySet()) {

                Edition edition = isEditionToProcess(editionShortName);

                // Have valid edition. Filter refsets to process
                if (edition != null) {

                    filterEditionRefsetVerions(edition, filteredTermserverShortNameToVersionBranchMap.get(editionShortName));

                }
            }

            if (filteredRefsets.isEmpty()) {
                throw new Exception("termServers refsetIdToVersionDataMap should never be empty");

            }

            logger.info("Finished processing db branches across all editions with " + filteredRefsets.size() + " filtered refsets versions.");

            // Based on filteredCodeSystems which already filtered for active code systems
            termserverRefsetIdToRefsetVersionsDataMap = generateSnowstormRefsetIdtoRefsetVersionsMap();

            if (termserverRefsetIdToRefsetVersionsDataMap.isEmpty()) {
                throw new Exception("termServerRefsetIdToRefsetVersionsDataMap should never be empty ");
            }

            activeDbRefsetIdToVersionRefsetMap = generateDatabaseRefsetIdtoRefsetVersionsMap(activeDbRefsets);
            inactiveDbRefsetIdToVersionRefsetMap = generateDatabaseRefsetIdtoRefsetVersionsMap(inactiveDbRefsets);

            logger.debug("aaa termServerRefsetIdToRefsetVersionsDataMap: " + termserverRefsetIdToRefsetVersionsDataMap.keySet());
            logger.debug("aaa activeDbRefsetIdToVersionRefsetMap: " + activeDbRefsetIdToVersionRefsetMap.keySet());
            logger.debug("aaa inactiveDbRefsetIdToVersionRefsetMap: " + inactiveDbRefsetIdToVersionRefsetMap.keySet());
        }
    }

    private Edition isEditionToProcess(String editionShortName) throws Exception {

        // handle the ignoreCoreRefsets flag
        if (getIsIgnoreCoreRefsets() && utilities.isInternationalEdition(editionShortName)) {
            logger.info("Not processing CORE refsets per ignoreCoreRefsets = " + getIsIgnoreCoreRefsets());
            return null;
        }

        // determine matching editions
        final List<Edition> mathcingEditions = dbEditions.stream().filter(e -> e.getShortName().equals(editionShortName)).collect(Collectors.toList());
        utilities.validateMatches(mathcingEditions, editionShortName);

        // Matching edition
        Edition edition = mathcingEditions.iterator().next();

        // Check if should process Edition
        if (edition == null) {
            logger.error("Should this ever be null? orig msg: Not processing CORE refsets per ignoreCoreRefsets = " + getIsIgnoreCoreRefsets());

            return null;
        }

        return edition;
    }

    private void initializeSync() throws Exception {

        snowstormRefsets.clear();
        filteredRefsets.clear();
        refsetToModuleMap.clear();
        refsetEditions.clear();
        rttProjects.clear();
        activeDbRefsets.clear();
        inactiveDbRefsets.clear();
        newlyCreatedAndUnchangedRefsetToVersionsMap.clear();

        updateDatabaseCache();

        try (TerminologyService service = new TerminologyService()) {
            dbRefsets = service.getAll(Refset.class);
            dbEditions = service.getAll(Edition.class);

        }

        // Map each refsetId/version pair's SyncRefsetMetadata
        activeDbRefsets.clear();
        inactiveDbRefsets.clear();
        dbRefsets.stream().filter(r -> r.isActive()).forEach(ar -> activeDbRefsets.add(ar));
        dbRefsets.stream().filter(r -> !r.isActive()).forEach(ir -> inactiveDbRefsets.add(ir));

        RefsetMemberService.clearVersionsWithChanges();
    }

    // RefsetId to map of Dates to RefsetMetadata
    private Map<String, Map<Long, SyncRefsetMetadata>> generateSnowstormRefsetIdtoRefsetVersionsMap() {

        Map<String, Map<Long, SyncRefsetMetadata>> generatedMap = new HashMap<>();

        for (SyncRefsetMetadata snowstormRefsetData : filteredRefsets) {

            final String refsetId = snowstormRefsetData.getRefsetNode().get("conceptId").asText();

            if (!generatedMap.containsKey(refsetId)) {

                generatedMap.put(refsetId, new HashMap<>());
            }

            generatedMap.get(refsetId).put(snowstormRefsetData.getVersion(), snowstormRefsetData);
        }

        return generatedMap;
    }

    private List<Long> compareAndModifyRefsetVersions(String refsetId, List<Long> versionDatesToCompare, Map<Long, SyncRefsetMetadata> snowstormRefsetVersionDataMap) throws Exception {
        final List<Long> modifiedVersions = new ArrayList<>();
        logger.debug("ppp with versionDatesToCompare: " + versionDatesToCompare);

        final List<Refset> dbVersions = dbRefsets.stream().filter(r -> r.getRefsetId().equals(refsetId)).collect(Collectors.toList());
        logger.debug("ppp with  dbVersions: " + dbVersions.size());

        // Process one version at a time
        for (long testingVersionDate : versionDatesToCompare) {
            logger.debug("ppp testingVersionDate: " + testingVersionDate);

            // Find associated DB refset
            List<Refset> matchingVersions = dbVersions.stream().filter(dbr -> dbr.getVersionDate().getTime() == testingVersionDate).collect(Collectors.toList());
            utilities.validateMatches(matchingVersions, refsetId + " / " + testingVersionDate);
            Refset modifyingVersion = matchingVersions.iterator().next();

            // Find values for Snowstorm Refset
            logger.debug("ppp modifyingVersion: " + modifyingVersion);
            logger.debug("ppp modifyingVersion.getEditionShortName(): " + modifyingVersion.getEditionShortName());
            logger.debug("ppp modifyingVersion.getEditionId(): " + modifyingVersion.getEditionId());
            logger.debug("ppp modifyingVersion.getBranchPath(): " + modifyingVersion.getBranchPath());
            logger.debug("ppp modifyingVersion.getEditionBranch(): " + modifyingVersion.getEditionBranch());
            logger.debug("ppp modifyingVersion.getEdition().getBranch(): " + modifyingVersion.getEdition().getBranch());
            logger.debug("ppp snowstormRefsetVersionDataMap.keySet(): " + snowstormRefsetVersionDataMap.keySet());

            SyncRefsetMetadata snowstormRefsetVersionData = null;

            for (long termserverVersion : snowstormRefsetVersionDataMap.keySet()) {
                if (testingVersionDate == termserverVersion) {
                    snowstormRefsetVersionData = snowstormRefsetVersionDataMap.get(termserverVersion);

                }
            }

            if (snowstormRefsetVersionData == null) {
                throw new Exception("JESSE");
            }

            final String snowStormRefsetName = determineRefsetName(snowstormRefsetVersionData);
            final String snowStormRefsetBranch = snowstormRefsetVersionData.getBranchPath();
            final String snowStormRefsetModuleId = refsetToModuleMap.get(refsetId);

            // Start comparison
            boolean modificationMade = false;

            if (isDifferentAttribute(refsetId + " / " + testingVersionDate, "Refset name ", modifyingVersion.getName(), snowStormRefsetName)) {
                modifyingVersion.setName(snowStormRefsetName);
                logger.debug("ppp2");
                modificationMade = true;
            }

            final String a = modifyingVersion.getEditionBranch();
            final String b = snowStormRefsetBranch;
            logger.debug("ppp3 a: " + a + " and b: " + b);
            logger.debug("ppp3 a.equals(b): " + a.equals(b));

            if (isDifferentAttribute(refsetId + " / " + testingVersionDate, "Refset branch ", a, b)) {
                modifyingVersion.setBranchPath(snowStormRefsetBranch);
                logger.debug("ppp3 modifyingVersion.getBranchPath(): ***" + modifyingVersion.getBranchPath() + "***");
                logger.debug("ppp3 snowStormRefsetBranch: ***" + snowStormRefsetBranch + "***");
                modificationMade = true;
            }

            if (isDifferentAttribute(refsetId + " / " + testingVersionDate, "Refset moduleId ", modifyingVersion.getModuleId(), snowStormRefsetModuleId)) {
                modifyingVersion.setModuleId(snowStormRefsetModuleId);
                modificationMade = true;
                logger.debug("ppp4");
            }

            if (modificationMade) {
                logger.debug("ppp5");
                dbHandler.updateRefset(modifyingVersion);

                modifiedVersions.add(modifyingVersion.getVersionDate().getTime());

                // TODO: Still need to post-process?
                postRefsetProcessing(modifyingVersion, snowstormRefsetVersionData.getEdition());

            } else {
                logger.debug("ppp6");

            }
        }

        return modifiedVersions;
    }

    private String determineRefsetName(SyncRefsetMetadata refsetSnowstormData) throws Exception {

        String refsetName;
        logger.debug("ppp1 refsetSnowstormData: " + refsetSnowstormData);
        if (refsetSnowstormData.getRefsetNode().get("pt").has("term")) {

            refsetName = refsetSnowstormData.getRefsetNode().get("pt").get("term").asText();
        } else {

            refsetName = lookupRefsetName(refsetSnowstormData.getRefsetNode().get("conceptId").asText(), refsetSnowstormData.getEdition(), refsetSnowstormData.getBranchPath());
        }

        return refsetName;
    }

    private void postRefsetProcessing(Refset refset, Edition edition) {

        if (refset != null) {

            refsetEditions.put(refset.getRefsetId(), edition);

            snowstormRefsets.add(refset);
        }

    }

    private void filterEditionRefsetVerions(Edition edition, SortedMap<Long, String> termserverVersionBranchMap) throws Exception {

        for (long versionDate : termserverVersionBranchMap.keySet()) {

            Iterator<JsonNode> refsetIterator = getSnowstormRefsetVersionMembers(edition.getName(), edition.getBranch(), termserverVersionBranchMap.get(versionDate), versionDate);

            while (refsetIterator != null && refsetIterator.hasNext()) {

                JsonNode refsetNode;

                // Check if should process Refset
                if ((refsetNode = isRefsetToProcess(refsetIterator, edition.getShortName())) != null) {

                    final String refsetId = refsetNode.get("conceptId").asText();

                    if (!refsetToModuleMap.containsKey(refsetId)) {

                        String moduleId = determineConceptModuleId(refsetId, edition.getBranch());
                        refsetToModuleMap.put(refsetId, moduleId);
                    }

                    final String termserverRefsetBranchPath = termserverVersionBranchMap.get(versionDate);
                    final Set<Long> termserverEditionBranchDates = termserverVersionBranchMap.keySet();

                    // If perVersionSync, then create version per branch and return. Otherwise, determine if changes exist in this version
                    if (getIsPerVersionSync() || versionHasChanges(refsetId, versionDate, termserverRefsetBranchPath, edition.getName(), termserverEditionBranchDates)) {

                        SyncRefsetMetadata refsetMetadata = new SyncRefsetMetadata(refsetNode, edition, termserverVersionBranchMap.keySet(), versionDate, termserverRefsetBranchPath);

                        // Found a refset to process later on
                        filteredRefsets.add(refsetMetadata);

                    }
                }
            }
        }

    }

    private Iterator<JsonNode> getSnowstormRefsetVersionMembers(String editionName, String editionBranchPath, String refsetBranchPath, long branchVersion) throws Exception {

        // Process edition
        String url = SnowstormConnection.BASE_URL + "browser/{branch}/members?active=true&referenceSet=%3C" + RefsetService.SIMPLE_TYPE_REFERENCE_SET;

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
            logger.info("getUrl (edition version refsets): " + url.replace("{branch}", refsetBranchPath));

            return refsetIterator;
        }

    }

    private String determineConceptModuleId(String refsetId, String branch) throws Exception {

        String url = SnowstormConnection.BASE_URL + branch + "/concepts/" + refsetId;

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

    private boolean versionHasChanges(String refsetId, long versionDate, String termserverRefsetBranchPath, String editionName, Set<Long> termserverEditionBranchDates) throws Exception {

        /*-
         * Check new version refset version date. If none returned (null), then:
         * a) no changes to refset itself and 
         * b) thus no need to create  new version.
         * c) Move onto nex refset
         */
        logger.debug("bbb inside versionHasChanges()");
        logger.debug("bbb refsetId: " + refsetId);
        logger.debug("bbb termserverRefsetBranchPath: " + termserverRefsetBranchPath);

        Long refsetVersionDate = RefsetMemberService.getLatestChangedVersionDate(termserverRefsetBranchPath, refsetId);

        if (refsetVersionDate == null) {

            // No changes to refset so don't create a new version

            return false;
        }

        long earliestPublishedVersionDate = -1;

        if (!termserverEditionBranchDates.contains(refsetVersionDate)) {

            for (long editionDate : termserverEditionBranchDates) {

                if (refsetVersionDate > editionDate) {

                    logger.error("Ignoring this member as have bad content - Can't have refsets with a member that has an effectiveDate:  " + refsetVersionDate + " that is AFTER the editionDate: "
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

        versionDate = refsetVersionDate;

        if (!termserverEditionBranchDates.contains(versionDate)) {

            logger.info(" Don't add refset versions that don't have corresponding snowstorm -based edition versions with Refset / and VersionDate pair: " + refsetId + " / " + versionDate);

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
    private String lookupRefsetName(String refsetId, Edition edition, String branchPath) throws Exception {

        String url = SnowstormConnection.BASE_URL + "browser/" + branchPath + "/concepts/" + refsetId;

        try (final Response response = SnowstormConnection.getResponse(url)) {

            final String resultString = response.readEntity(String.class);
            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode conceptNode = mapper.readTree(resultString.toString());

            Iterator<JsonNode> descriptionIterator = conceptNode.get("descriptions").iterator();

            while (descriptionIterator.hasNext()) {

                JsonNode descriptionNode = descriptionIterator.next();
                String acceptability = null;

                if (descriptionNode.get("type").asText().equals("SYNONYM") && descriptionNode.get("lang").asText().equals(edition.getDefaultLanguageCode())) {

                    final JsonNode acceptabilityMap = descriptionNode.get("acceptabilityMap");

                    for (String langRefsetId : edition.getDefaultLanguageRefsets()) {

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

    /*
     * First checks if the refset is associated with an RTT project. If so return. If not, return the default Edition's project (creating it if not already existing)
     */
    private Project createRefsetProject(Refset refset) throws Exception {

        if (utilities.getPropertyReader().getRefsetToProjectsInfoMap().containsKey(refset.getRefsetId())) {

            // Determine project name and description from Rtt Json
            String projectInfo = utilities.getPropertyReader().getRefsetToProjectsInfoMap().get(refset.getRefsetId());
            String[] projectDetails = projectInfo.split(",");

            // Clean out project Name & Description
            for (int i = 0; i < 2; i++) {

                if (projectDetails[i].startsWith("\"")) {

                    projectDetails[i] = projectDetails[i].substring(1);
                }

                if (projectDetails[i].endsWith("\"")) {

                    projectDetails[i] = projectDetails[i].substring(0, projectDetails[i].length() - 1);
                }

            }

            return dbHandler.addProject(projectDetails[0].replaceFirst("\"", ""), projectDetails[1], refsetEditions.get(refset.getRefsetId()));
        } else {

            // No project associated with refset, so use default Edition Project
            if (!defaultEditionProjects.containsKey(refset.getEdition().getShortName())) {

                throw new Exception("Default project should have already been created of Edition: " + refset.getEdition().getName());
            }

            return defaultEditionProjects.get(refset.getEdition().getShortName());
        }

    }

    private void associateRefsetProject(Refset refset) throws Exception {
        logger.debug("aaa 1");
        Project project = null;
        // Set refset Project making sure to cache it based on refsetId
        if (utilities.getPropertyReader().getRefsetToProjectsInfoMap().containsKey(refset.getRefsetId())) {
            logger.debug("aaa 1 aaa");

            final String projectInfo = utilities.getPropertyReader().getRefsetToProjectsInfoMap().get(refset.getRefsetId());
            final String rttProjectId = projectInfo.split("\t")[0];
            logger.debug("aaa 1 projectInfo: " + projectInfo);
            logger.debug("aaa 1 rttProjectId: " + rttProjectId);

            if (!rttProjects.containsKey(rttProjectId)) {
                logger.debug("aaa 1 bbb");

                project = createRefsetProject(refset);
                logger.debug("aaa 1 project1: " + project);

                rttProjects.put(rttProjectId, project);
            }
            logger.debug("aaa 1 ccc");

            project = rttProjects.get(rttProjectId);
            logger.debug("aaa 1 project2: " + project);

        } else {
            logger.debug("aaa 1 refsetEditions: " + refsetEditions.keySet());
            logger.debug("aaa 1 refsetEditions.get(refset.getRefsetId()).getShortName(): " + refsetEditions.get(refset.getRefsetId()).getShortName());

            project = defaultEditionProjects.get(refsetEditions.get(refset.getRefsetId()).getShortName());
            logger.debug("aaa 1 project3: " + project);

        }

        if (project == null) {
            logger.debug("aaa 1 project4: " + project);

            final String message = "Must have created from RTT, already created from RTT, or found a UAT default project for this refset: " + refset.getRefsetId() + " / " + refset.getVersionDate();
            logger.error(message);

            throw new Exception(message);
        }
        logger.debug("aaa 1 project5: " + project);

        refset.setProject(project);

    }

    // Do not persist as will be done later
    private void associateRefsetClauses(final String rttId, final Refset refset) throws Exception {

        // If has ECL clauses, associate them with refset
        if (utilities.getPropertyReader().getRttRefsetToClausesMap().containsKey(rttId)) {

            Set<DefinitionClause> clauses = dbHandler.addDefinitionClauses(rttId);
            refset.getDefinitionClauses().addAll(clauses);
        }

    }

    /**
     * Update refsets with values from json and also identify latestVersion, but do not persist at this point.
     * @param dbRefsets the db refsets
     * @throws Exception
     */
    private void finalizeNewOrChangedRefsets() throws Exception {

        // TODO: Determine if need to initialize latestRefsetCache (with unchanged) prior
        Map<String, Long> latestRefsetCache = new HashMap<>();
        Set<Refset> refsetsUpdated = new HashSet<>();

        logger.debug("sss");
        dbRefsets.stream().forEach(r -> logger.debug("sss " + r.getRefsetId() + " / " + r.getVersionDate()));
        try (final TerminologyService service = new TerminologyService()) {

            for (String refsetId : newlyCreatedAndUnchangedRefsetToVersionsMap.keySet()) {
                logger.debug("sss refsetId: " + refsetId);

                for (long version : newlyCreatedAndUnchangedRefsetToVersionsMap.get(refsetId)) {

                    try {
                        logger.debug("sss version: " + version);

                        List<Refset> matchingRefsets = dbRefsets.stream().filter(r -> r.getRefsetId().equals(refsetId) && r.getVersionDate().getTime() == version).collect(Collectors.toList());

                        logger.debug("sss matchingRefsets: " + matchingRefsets);

                        Refset refset = (Refset) utilities.validateMatches(matchingRefsets, refsetId + " / " + version);
                        logger.debug("sss refset1: " + refset);

                        // Determine the corresponding project which also defines the edition
                        associateRefsetProject(refset);
                        logger.debug("sss refset2: " + refset);

                        // For now, default db refsets to PUBLIC
                        refset.setPrivateRefset(false);

                        // Update refset from JSON for Narrative, Type, tags, and ecl clauses. Project too.
                        if (utilities.getPropertyReader().getRttRefsetSctIdToRttIdMap().keySet().contains(refset.getRefsetId())) {

                            /* Refset lived in RTT as well */
                            final Set<String> rttIds = utilities.getPropertyReader().getRttRefsetSctIdToRttIdMap().get(refset.getRefsetId());

                            // Add Refset with RTT data as long as it also version resides on snowstorm. Keep track of which are added this way as to not add them from RTT as
                            // well
                            for (String rttId : rttIds) {

                                final String refsetJsonString = utilities.getPropertyReader().getRttIdToRefsetJsonMap().get(rttId);

                                final ObjectMapper mapper = new ObjectMapper();
                                final JsonNode refsetJson = mapper.readTree(refsetJsonString);

                                final long rttDataRefsetVersion = utilities.getSdf().parse(refsetJson.get("version").asText()).getTime();

                                if (rttDataRefsetVersion < 0 && rttDataRefsetVersion == refset.getVersionDate().getTime()) {

                                    // Set type & narrative
                                    refset.setType(refsetJson.get("type").asText());
                                    refset.setNarrative(refsetJson.get("narrative").asText());

                                    // Tags
                                    if (refsetJson.has("tags")) {

                                        Iterator<JsonNode> tagsIterator = refsetJson.get("tags").iterator();

                                        while (tagsIterator.hasNext()) {

                                            refset.getTags().add(tagsIterator.next().asText());
                                        }

                                    }

                                    // If has ECL clauses, create and associate with refset
                                    associateRefsetClauses(rttId, refset);

                                    // Only one will match, so no need to keep reading
                                    break;
                                }

                            }

                        } else {

                            // If JSON not available to the refset, it means it resides exclusively on Snowstorm.

                            // Set defaults for type & narrative
                            refset.setType("EXTENSIONAL");
                            refset.setNarrative("No corresponding refset information found on RTT for " + refset.getRefsetId());

                        }

                        // Keep track of the latest version per refsetId
                        if (!latestRefsetCache.containsKey(refset.getRefsetId()) || latestRefsetCache.get(refset.getRefsetId()) < refset.getVersionDate().getTime()) {

                            latestRefsetCache.put(refset.getRefsetId(), refset.getVersionDate().getTime());
                        }

                        refsetsUpdated.add(refset);
                    } catch (Exception e) {
                        logger.error("Failed on refsetVersion: " + refsetId + " / " + version + " --- with message: " + e.getMessage());
                    }
                }
            }

            // Have latest version per refset. Set the latestVersion flag to true for them
            for (Refset refset : refsetsUpdated) {

                if (latestRefsetCache.containsKey(refset.getRefsetId())) {

                    for (String refsetId : latestRefsetCache.keySet()) {

                        if (refset.getRefsetId().equals(refsetId) && refset.getVersionDate().getTime() == latestRefsetCache.get(refsetId)) {

                            refset.setLatestPublishedVersion(true);

                            break;
                        }

                    }

                }
            }

            // Persist changes
            dbHandler.updateMultipleRefsets(refsetsUpdated);

        }
    }

    protected JsonNode isRefsetToProcess(Iterator<JsonNode> refsetIterator, String shortName) throws Exception {
        final JsonNode refsetNode = refsetIterator.next();

        if (!refsetNode.has("conceptId") || !refsetNode.has("active")) {

            throw new Exception("Getting unexpected Refset info from node: " + refsetNode.toString());
        }
        String refsetId = refsetNode.get("conceptId").asText();

        if (!utilities.isInternationalEdition(shortName) && utilities.getCoreRefsets().contains(refsetId)) {

            return null;
        }

        if (utilities.getPropertyReader().getRefsetsToIgnore().contains(refsetId)) {

            logger.info("Found refsetId: " + refsetId + ", but will not add it per prop file");

            return null;
        }

        if (!isTesting() || (isTesting() && (testingRefset == null || testingRefset.isEmpty()) || refsetId.equals(testingRefset))) {

            return refsetNode;
        }

        // Testing, but current refset not one needed
        return null;
    }

    /**
     * Determine branches.
     *
     * @return the map
     * @throws Exception the exception
     */
    private Map<String, SortedMap<Long, String>> determineEditionBranches(Set<JsonNode> codeSystems) throws Exception {

        Map<String, SortedMap<Long, String>> editionToDateBranchMap = new HashMap<>();

        try (final TerminologyService service = new TerminologyService()) {

            dbEditions.stream().forEach(e -> logger.info(e.getName()));

            for (JsonNode codeSystem : codeSystems) {

                final String editionName = codeSystem.has("name") ? codeSystem.get("name").asText() : "";
                final String shortName = codeSystem.has("shortName") ? codeSystem.get("shortName").asText() : "";
                final String branch = codeSystem.has("branchPath") ? codeSystem.get("branchPath").asText() : "";

                logger.info("Identifying CodeSystem branches for: " + editionName);

                final String genericUrl = SnowstormConnection.BASE_URL + "branches/{branch}/children";

                SortedMap<Long, String> children = new TreeMap<>();
                logger.info(" Branch children Url: " + genericUrl.replace("{branch}", branch));

                try (final Response response = SnowstormConnection.getResponse(genericUrl.replace("{branch}", branch))) {

                    final String resultString = response.readEntity(String.class);
                    final ObjectMapper mapper = new ObjectMapper();
                    final JsonNode root = mapper.readTree(resultString.toString());

                    // get RefSets from edition as long as a) active & b) within
                    // edition's module
                    final Iterator<JsonNode> branchIterator = root.iterator();

                    while (branchIterator.hasNext()) {

                        JsonNode child = branchIterator.next();
                        final String childBranch = child.get("path").asText();
                        String childDate = childBranch.replace(branch, "");

                        if (childDate.startsWith("/")) {

                            childDate = childDate.substring(1);
                        }

                        // Since grabbing all children branches, avoid
                        // attempting to parse extensions i.e. MAIN/SNOMEDCT-US
                        boolean childAdded = false;

                        if (childDate.matches("^\\d{4}-\\d{2}-\\d{2}$")) {

                            long branchDate = branchDateFormatter.parse(childDate).getTime();

                            if (branchDate < new Date().getTime()) {

                                children.put(branchDate, childBranch);
                                childAdded = true;
                            }

                        } else {
                            logger.info("Ignoring branch " + childDate + " as it doesn't comply with expected format (where final item in path is a date in format yyyy-mm-dd");
                        }

                        if (!childAdded) {

                            // logger.info("Skipping over childBranch/branchDate pair " + edition.getBranch() + "/" + childDate + " as the branch isn't an official release
                            // branch");
                        }

                    }

                    logger.info("Branches filtered for edition: " + editionName);

                    for (long child : children.keySet()) {

                        logger.info("Branch: " + children.get(child));
                    }

                }

                editionToDateBranchMap.put(shortName, children);
            }

            return editionToDateBranchMap;
        }
    }

    private Refset addRefset(SyncRefsetMetadata syncRefsetMetadata) {

        try {
            final String refsetId = syncRefsetMetadata.getRefsetNode().get("conceptId").asText();
            final String moduleId = refsetToModuleMap.get(refsetId);
            final String refsetName = determineRefsetName(syncRefsetMetadata);
            final String refsetType = Refset.EXTENSIONAL; // All from Snowstorm are strictly extension
            final long version = syncRefsetMetadata.getVersion();

            Refset newRefset = dbHandler.addRefset(refsetName, refsetId, moduleId, version, refsetType);

            // TODO: Still need to post-process?
            postRefsetProcessing(newRefset, syncRefsetMetadata.getEdition());

            return newRefset;
        } catch (Exception e) {
            logger.error("Unable to send refset metadata to dbHandler for refset: " + syncRefsetMetadata);

            return null;
        }
    }
}
