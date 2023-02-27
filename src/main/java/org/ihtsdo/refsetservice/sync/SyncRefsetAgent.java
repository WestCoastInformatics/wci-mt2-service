package org.ihtsdo.refsetservice.sync;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status.Family;

import org.ihtsdo.refsetservice.model.DefinitionClause;
import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.sync.util.SyncRefsetMetadata;
import org.ihtsdo.refsetservice.sync.util.SyncStatistics;
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

    public SyncRefsetAgent() throws Exception {

        snowstormRefsets.clear();
        filteredRefsets.clear();
        refsetToModuleMap.clear();
    }

    public void sync() throws Exception {

        Set<SyncRefsetMetadata> filteredRefsets = filterRefsetsToProcess();

        // Map each refsetId/version pair's SyncRefsetMetadata
        Map<String, Map<Date, SyncRefsetMetadata>> sortedPublishedSnowstormRefsetVersionPairs = sortPublishedSnowstormRefsetVersionPairs(filteredRefsets);
        Map<String, Map<Date, Refset>> sortedDatabaseRefsetVersionPairs = sortDatabaseRefsetVersionPairs();
        Map<String, Map<Date, Refset>> publishedDatabaseRefsetVersionPairs = identifyPublishedDatabaseRefsetVersionPairs(sortedDatabaseRefsetVersionPairs);

        logger.debug("sortedPublishedSnowstormRefsetVersionPairs: " + sortedPublishedSnowstormRefsetVersionPairs);
        logger.debug("publishedDatabaseRefsetVersionPairs: " + publishedDatabaseRefsetVersionPairs);
        // Identify new and missing pairs
        logger.info(
                " syncSnowstormRefsets: Examining if there are any new or missing refset version pairs in the  " + statistics.getRefsetVersionsSynced() + " refset/version pairs found on Snowstorm");
        Map<String, Set<Date>> newPairs = identifyNewSnowstormRefsetVersionPairs(sortedPublishedSnowstormRefsetVersionPairs, publishedDatabaseRefsetVersionPairs);
        Map<String, Set<Date>> missingPairs = identifyMissingSnowstormRefsetVersionPairs(sortedPublishedSnowstormRefsetVersionPairs, publishedDatabaseRefsetVersionPairs);
        statistics.setRefsetVersionsAdded(countPairs(newPairs));
        statistics.setRefsetVersionsRemoved(countPairs(missingPairs));
        logger.debug("New Pairs: " + newPairs);
        logger.debug("New refsetId Pairs size: " + newPairs.size());

        // Identify existing pairs that have been modified
        Map<String, Map<String, Set<Date>>> changedPairs = identifyChangedVersionPairs(sortedPublishedSnowstormRefsetVersionPairs, publishedDatabaseRefsetVersionPairs, newPairs, missingPairs);
        List<String> newNeedsRefsetKey = changedPairs.get(SyncStatistics.CHANGED).keySet().stream().filter(refsetId -> !newPairs.containsKey(refsetId)).collect(Collectors.toList());
        List<String> missingNeedsRefsetKey = changedPairs.get(SyncStatistics.CHANGED).keySet().stream().filter(refsetId -> !missingPairs.containsKey(refsetId)).collect(Collectors.toList());

        newNeedsRefsetKey.stream().forEach(refsetId -> newPairs.put(refsetId, new HashSet<>()));
        missingNeedsRefsetKey.stream().forEach(refsetId -> missingPairs.put(refsetId, new HashSet<>()));

        // TODO: Changed should be version/based, not all refsets entirely
        changedPairs.get(SyncStatistics.CHANGED).keySet().stream().forEach(refsetId -> newPairs.put(refsetId, changedPairs.get(SyncStatistics.CHANGED).get(refsetId)));
        changedPairs.get(SyncStatistics.CHANGED).keySet().stream().forEach(refsetId -> missingPairs.put(refsetId, changedPairs.get(SyncStatistics.CHANGED).get(refsetId)));

        // Remove existing refsets
        missingPairs.keySet().stream().forEach(refsetId -> missingPairs.get(refsetId).stream().forEach(version -> {

            try {
                utilities.removeRefsetVersionPair(publishedDatabaseRefsetVersionPairs.get(refsetId).get(version));
            } catch (Exception e) {
                logger.error("Failed in removing exiting refset version " + refsetId + " / " + version);
            }
        }));

        // Add new refsets
        Set<Refset> newlyCreatedAndUnchangedRefsetVersionPairs = new HashSet<>();
        newPairs.keySet().stream().forEach(refsetId -> newPairs.get(refsetId).stream().forEach(version -> {

            try {
                Refset newRefset = processNewRefsetVersionPair(sortedPublishedSnowstormRefsetVersionPairs.get(refsetId).get(version));
                newlyCreatedAndUnchangedRefsetVersionPairs.add(newRefset);
            } catch (Exception e) {
                logger.error("Failed in processing refset version " + refsetId + " / " + version);
            }

            // Collect newly created refsets
        }));

        // Collect unchanged refsets
        changedPairs.get(SyncStatistics.UNCHANGED).keySet().stream().forEach(refsetId -> changedPairs.get(SyncStatistics.UNCHANGED).get(refsetId).stream().forEach(version -> {
            newlyCreatedAndUnchangedRefsetVersionPairs.add(publishedDatabaseRefsetVersionPairs.get(refsetId).get(version));
        }));

        // Final step
        finalizeNewOrChangedRefsets(newlyCreatedAndUnchangedRefsetVersionPairs);

    }

    private int countPairs(Map<String, Set<Date>> newPairs) {
        int counter = 0;
        for (String refsetId : newPairs.keySet()) {
            counter += newPairs.get(refsetId).size();
        }
        return counter;
    }

    private Map<String, Map<String, Set<Date>>> identifyChangedVersionPairs(Map<String, Map<Date, SyncRefsetMetadata>> snowstormPairs, Map<String, Map<Date, Refset>> databasePairs,
        Map<String, Set<Date>> newInSnowstormPairs, Map<String, Set<Date>> missingPairs) throws Exception {

        final Map<String, Map<String, Set<Date>>> existingRefsetVersionPairs = new HashMap<>();

        existingRefsetVersionPairs.put(SyncStatistics.CHANGED, new HashMap<>());
        existingRefsetVersionPairs.put(SyncStatistics.UNCHANGED, new HashMap<>());

        for (String refsetId : snowstormPairs.keySet()) {

            Refset syncedRefsetVersionPair = null;
            for (Date version : snowstormPairs.get(refsetId).keySet()) {

                if (newInSnowstormPairs.containsKey(refsetId) && newInSnowstormPairs.get(refsetId).contains(version)) {
                    continue;
                } else if (missingPairs.containsKey(refsetId) && missingPairs.get(refsetId).contains(version)) {
                    continue;
                }

                try {
                    Refset databaseRefsetVersion = databasePairs.get(refsetId).get(version);
                    SyncRefsetMetadata snowstormRefsetVersionData = snowstormPairs.get(refsetId).get(version);

                    syncedRefsetVersionPair = syncExistingRefsetVersionPairs(snowstormRefsetVersionData, databaseRefsetVersion);
                } catch (NullPointerException e) {
                    logger.error(
                            "Failed getting the matching pairs in identifyChanged`VersionPairs for " + refsetId + " / " + version + " / " + snowstormPairs.get(refsetId).get(version).getBranchPath());
                    continue;
                }

                String analysisFinding;
                if (syncedRefsetVersionPair == null) {
                    // DB & Snowstorm are identical
                    analysisFinding = SyncStatistics.UNCHANGED;
                    statistics.incrementRefsetVersionsUnchanged();
                } else {
                    // Difference found... process
                    analysisFinding = SyncStatistics.CHANGED;
                    statistics.incrementRefsetVersionsRecreated();
                }

                // No differences found
                if (!existingRefsetVersionPairs.get(analysisFinding).containsKey(refsetId)) {

                    existingRefsetVersionPairs.get(analysisFinding).put(refsetId, new HashSet<>());
                }
                existingRefsetVersionPairs.get(analysisFinding).get(refsetId).add(version);

            }

        }

        return existingRefsetVersionPairs;
    }

    private Map<String, Set<Date>> identifyMissingSnowstormRefsetVersionPairs(Map<String, Map<Date, SyncRefsetMetadata>> snowstormPairs, Map<String, Map<Date, Refset>> databasePairs) {
        Map<String, Set<Date>> missingPairs = new HashMap<>();

        // Identify missing refsets and add them to return map
        List<String> missingRefsets = databasePairs.keySet().stream().filter(refsetId -> !snowstormPairs.keySet().contains(refsetId)).collect(Collectors.toList());
        for (String refsetId : missingRefsets) {

            missingPairs.put(refsetId, databasePairs.get(refsetId).keySet());

            statistics.setRefsetVersionsRemoved(statistics.getRefsetVersionsRemoved() + databasePairs.get(refsetId).keySet().size());
        }

        // Identify missing refset versions and add them to return map
        for (String refsetId : databasePairs.keySet()) {
            if (!missingRefsets.contains(refsetId)) {
                for (Date version : databasePairs.get(refsetId).keySet()) {
                    if (!snowstormPairs.get(refsetId).containsKey(version)) {

                        statistics.incrementRefsetVersionsRemoved();
                        missingPairs.get(refsetId).add(version);
                    }
                }
            }
        }

        return missingPairs;
    }

    private Map<String, Set<Date>> identifyNewSnowstormRefsetVersionPairs(Map<String, Map<Date, SyncRefsetMetadata>> snowstormPairs, Map<String, Map<Date, Refset>> databasePairs) {
        Map<String, Set<Date>> newPairs = new HashMap<>();

        logger.debug("AAA1 snowstormPairs = " + snowstormPairs.size());
        // Identify brand new refsets and add them to return map
        List<String> newRefsets = snowstormPairs.keySet().stream().filter(refsetId -> !databasePairs.keySet().contains(refsetId)).collect(Collectors.toList());
        logger.debug("AAA2 newRefsets = " + newRefsets.size());
        for (String refsetId : newRefsets) {
            newPairs.put(refsetId, snowstormPairs.get(refsetId).keySet());
            logger.debug("AAA3 snowstormPairs.get(refsetId).keySet().size() = " + snowstormPairs.get(refsetId).keySet().size());

            statistics.setRefsetVersionsAdded(statistics.getRefsetVersionsAdded() + snowstormPairs.get(refsetId).keySet().size());
        }
        logger.debug("AAA4 statistics.getRefsetVersionsAdded(): " + statistics.getRefsetVersionsAdded());

        // Identify new refset versions and add them to return map
        for (String refsetId : snowstormPairs.keySet()) {
            logger.debug("AAA5 refsetId: " + refsetId);

            if (!newRefsets.contains(refsetId)) {
                logger.debug("AAA6");

                for (Date version : snowstormPairs.get(refsetId).keySet()) {
                    logger.debug("AAA7 version: " + version);

                    if (!databasePairs.get(refsetId).containsKey(version)) {

                        statistics.incrementRefsetVersionsAdded();
                        newPairs.get(refsetId).add(version);
                        logger.debug("AAA8 statistics.getRefsetVersionsAdded(): " + statistics.getRefsetVersionsAdded());
                    }
                }
            }
        }

        return newPairs;
    }

    private Map<String, Map<Date, Refset>> identifyPublishedDatabaseRefsetVersionPairs(Map<String, Map<Date, Refset>> sortedDatabaseRefsetVersionPairs) {
        HashMap<String, Map<Date, Refset>> publishedDatabaseRefsetVersionPairs = new HashMap<>();

        for (String refsetId : sortedDatabaseRefsetVersionPairs.keySet()) {

            for (Date version : sortedDatabaseRefsetVersionPairs.get(refsetId).keySet()) {

                // Those published get added
                if (sortedDatabaseRefsetVersionPairs.get(refsetId).get(version).getVersionStatus().equals(Refset.PUBLISHED)) {
                    if (!publishedDatabaseRefsetVersionPairs.containsKey(refsetId)) {

                        publishedDatabaseRefsetVersionPairs.put(refsetId, new HashMap<>());
                    }

                    publishedDatabaseRefsetVersionPairs.get(refsetId).put(version, sortedDatabaseRefsetVersionPairs.get(refsetId).get(version));

                }
            }
        }

        return publishedDatabaseRefsetVersionPairs;
    }

    private Refset syncRefsetVersion(Date version, SyncRefsetMetadata snowstormRefsetVersionData, Map<Date, Refset> databaseRefsetVersionPairs) throws Exception {

        Refset syncedRefset = null;

        if (databaseRefsetVersionPairs == null || !databaseRefsetVersionPairs.containsKey(version)) {

            // First time seeing refset version pair from snowstorm
            syncedRefset = processNewRefsetVersionPair(snowstormRefsetVersionData);

        } else {

            Refset databaseRefsetVersion = databaseRefsetVersionPairs.get(version);
            syncedRefset = syncExistingRefsetVersionPairs(snowstormRefsetVersionData, databaseRefsetVersion);
        }

        return syncedRefset;

    }

    private Refset syncExistingRefsetVersionPairs(SyncRefsetMetadata snowstormRefsetData, Refset refset) throws Exception {

        Refset syncedRefset = compareAndUpdateRefsetDifferences(refset, snowstormRefsetData);

        if (syncedRefset != null) {
            // TODO: Remove?
            postRefsetProcessing(syncedRefset, snowstormRefsetData.getEdition());
        }

        return syncedRefset;
    }

    private Refset processNewRefsetVersionPair(SyncRefsetMetadata refsetData) throws Exception {

        final String refsetId = refsetData.getRefsetNode().get("conceptId").asText();
        final String moduleId = refsetToModuleMap.get(refsetId);
        final String snowstormRefsetName = identifyRefsetName(refsetData);

        Refset newRefset = utilities.addRefset(snowstormRefsetName, refsetId, moduleId, refsetData.getVersion(), Refset.EXTENSIONAL, "");

        postRefsetProcessing(newRefset, refsetData.getEdition());

        return newRefset;

    }

    private Map<String, Map<Date, Refset>> sortDatabaseRefsetVersionPairs() {

        Map<String, Map<Date, Refset>> sortedDatabaseRefsetVersionPairs = new HashMap<>();

        for (Refset dbRefset : allDatabaseRefsets) {

            if (!sortedDatabaseRefsetVersionPairs.containsKey(dbRefset.getRefsetId())) {

                sortedDatabaseRefsetVersionPairs.put(dbRefset.getRefsetId(), new HashMap<>());
            }

            sortedDatabaseRefsetVersionPairs.get(dbRefset.getRefsetId()).put(dbRefset.getVersionDate(), dbRefset);
        }

        return sortedDatabaseRefsetVersionPairs;
    }

    private Map<String, Map<Date, SyncRefsetMetadata>> sortPublishedSnowstormRefsetVersionPairs(Set<SyncRefsetMetadata> refsetsToProcess) {
        statistics.setRefsetVersionsSynced(refsetsToProcess.size());

        Map<String, Map<Date, SyncRefsetMetadata>> sortedPublishedSnowstormRefsetVersionPairs = new HashMap<>();

        int counter = 0;
        for (SyncRefsetMetadata snowstormRefsetData : refsetsToProcess) {

            final String refsetId = snowstormRefsetData.getRefsetNode().get("conceptId").asText();

            if (!sortedPublishedSnowstormRefsetVersionPairs.containsKey(refsetId)) {

                sortedPublishedSnowstormRefsetVersionPairs.put(refsetId, new HashMap<>());
            }

            counter++;
            sortedPublishedSnowstormRefsetVersionPairs.get(refsetId).put(snowstormRefsetData.getVersion(), snowstormRefsetData);
        }

        return sortedPublishedSnowstormRefsetVersionPairs;
    }

    private void finalizeNewOrChangedRefsets(Set<Refset> updatedRefsets) throws Exception {

        updateRefsetsWithRttMetadata(updatedRefsets);

        try (final TerminologyService service = new TerminologyService()) {

            utilities.initializeService(service);

            // Adding refsets identified on snowstorm
            for (Refset refset : updatedRefsets) {

                service.update(refset);
            }

        }

    }

    /*-
     *  Compare Snowstorm-defined Refset attributes. These include:
     *  - Name
     *  - RefsetId (can't change, so no need to validate)
     *  - ModuleId
     *  - isActive
     *  - Version
     *  - Narrative
     *  
     *  Note: Not supporting project updates as that should be managed in tool
     */

    private Refset compareAndUpdateRefsetDifferences(Refset existingRefset, SyncRefsetMetadata refsetSnowstormData) throws Exception {

        /* Found existing Edition. Compare the values to determine if something changed, and if so, update the edition accordingly */
        boolean modificationMade = false;

        final boolean isActiveSnowstormRefset = refsetSnowstormData.getRefsetNode().get("active").asBoolean();
        final String snowstormRefsetNarrative = refsetSnowstormData.getRefsetNode().has("narrative") ? refsetSnowstormData.getRefsetNode().get("narrative").asText() : "";

        // TODO: This is immutable, so nothing to check?
        /*-
        final String snowstormRefsetName = identifyRefsetName(refsetSnowstormData);
        if (updateAttribute("Refset name", existingRefset.getName(), snowstormRefsetName)) {
        
            existingRefset.setName(snowstormRefsetName);
            modificationMade = true;
        }
        */

        // TODO: This is immutable, so nothing to check?
        /*-
        final String snowstormModuleId = refsetSnowstormData.getRefsetNode().get("moduleId").asText();
        if (updateAttribute("Refset moduleId", existingRefset.getModuleId(), snowstormModuleId)) {
        
            existingRefset.setModuleId(snowstormModuleId);
            modificationMade = true;
        }
        */

        if (isDifferentAttribute(existingRefset.getEditionShortName(), "Refset active", existingRefset.isActive(), isActiveSnowstormRefset)) {

            existingRefset.setActive(isActiveSnowstormRefset);
            modificationMade = true;
        }

        // TODO: This gets populated from branch, so nothing to check?
        if (isDifferentAttribute(existingRefset.getEditionShortName(), "Refset version", existingRefset.getVersionDate().getTime(), refsetSnowstormData.getVersion().getTime())) {

            existingRefset.setVersionDate(refsetSnowstormData.getVersion());
            modificationMade = true;
        }

        // TODO: This comes from RTT, so nothing to check?
        // Value may come from project.txt file (Rtt), so don't overwrite if what is on Snowstorm is empty.
        if (!snowstormRefsetNarrative.isBlank() && isDifferentAttribute(existingRefset.getEditionShortName(), "Refset narrative", existingRefset.getNarrative(), snowstormRefsetNarrative)) {

            existingRefset.setNarrative(snowstormRefsetNarrative);
            modificationMade = true;
        }

        // TODO: Determine if need to review Type given managed from RT2... and if managed here, need update on Snow?
        if (modificationMade) {

            try (final TerminologyService service = new TerminologyService()) {

                utilities.initializeService(service);

                return service.update(existingRefset);
            }

        } else {

            return null;
        }

    }

    private String identifyRefsetName(SyncRefsetMetadata refsetSnowstormData) throws Exception {

        String refsetName;

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
            allDatabaseRefsets.add(refset);

            if (!uniqueRefsetIds.contains(refset.getRefsetId())) {

                uniqueRefsetIds.add(refset.getRefsetId());
            }

        }

    }

    private Set<SyncRefsetMetadata> filterRefsetsToProcess() throws Exception {
        int counter = 0;

        logger.info("About to process these branches: " + editionsToProcess.keySet());

        for (String editionShortName : editionsToProcess.keySet()) {
            Edition edition;

            // Check if should process Edition
            if ((edition = getEdition(editionShortName)) == null) {
                continue;
            }

            // Process edition
            String url = SnowstormConnection.BASE_URL + "browser/{branch}/members?active=true&referenceSet=%3C" + RefsetService.SIMPLE_TYPE_REFERENCE_SET;

            for (Date branchVersion : editionsToProcess.get(editionShortName).keySet()) {

                final String branchPath = editionsToProcess.get(editionShortName).get(branchVersion);

                try (final Response response = SnowstormConnection.getResponse(url.replace("{branch}", branchPath))) {

                    if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {

                        if (edition.getBranch().startsWith("MAIN")) {

                            throw new Exception("Unable to process edition called with: " + url.replace("{branch}", branchPath));
                        } else {

                            continue;
                        }

                    }

                    // get RefSets from edition as long as a) active & b) not a core refset
                    final String resultString = response.readEntity(String.class);
                    final ObjectMapper mapper = new ObjectMapper();
                    final JsonNode root = mapper.readTree(resultString.toString());

                    final Iterator<JsonNode> refsetIterator = root.get("referenceSets").iterator();
                    logger.info("Listing refsets in " + edition.getName() + " for version date: " + branchVersion + " via url: " + url.replace("{branch}", branchPath));

                    while (refsetIterator.hasNext()) {
                        counter++;

                        JsonNode refsetNode;

                        // Check if should process Refset
                        if ((refsetNode = isRefsetToProcess(refsetIterator, editionShortName)) != null) {
                            final String refsetId = refsetNode.get("conceptId").asText();

                            if (!refsetToModuleMap.containsKey(refsetId)) {

                                String moduleId = identifyConceptModuleId(refsetId, edition.getBranch());
                                refsetToModuleMap.put(refsetId, moduleId);
                            }

                            if (isVersionToPersist(refsetId, branchVersion, branchVersion, branchPath, edition.getName(), editionsToProcess.get(edition.getShortName()).keySet())) {

                                SyncRefsetMetadata refsetMetadata = new SyncRefsetMetadata(refsetNode, edition, editionsToProcess.get(edition.getShortName()).keySet(), branchVersion, branchPath);

                                filteredRefsets.add(refsetMetadata);

                            }
                        }
                    }
                }
            }
        }

        return filteredRefsets;
    }

    private Edition getEdition(String shortName) {
        if (getIsIgnoreCoreRefsets() && utilities.isInternationalEdition(shortName)) {

            logger.info("Not processing CORE refsets per ignoreCoreRefsets = " + getIsIgnoreCoreRefsets());
            return null;
        }

        List<Edition> editions = allDatabaseEditions.stream().filter(e -> e.getShortName().equals(shortName)).collect(Collectors.toList());

        if (editions == null || editions.size() != 1) {

            logger.error("Unable to find  editions associated with Code System: " + shortName);
            return null;

        }

        // Matching edition
        return editions.iterator().next();
    }

    private String identifyConceptModuleId(String refsetId, String branch) throws Exception {

        String url = SnowstormConnection.BASE_URL + branch + "/concepts/" + refsetId;

        try (final Response response = SnowstormConnection.getResponse(url)) {

            if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {

                throw new Exception("Unable to retrieve concept " + refsetId + " on branch " + branch + " in order to identify its moduleId");

            }

            final String resultString = response.readEntity(String.class);
            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode root = mapper.readTree(resultString.toString());

            // get RefSets from edition as long as a) active & b)
            // within edition's moduleˇ
            return root.get("moduleId").asText();

        }

    }

    private boolean isVersionToPersist(String refsetId, Date branchVersion, Date versionDate, String branchPath, String editionName, Set<Date> editionVersions) throws Exception {

        if (getIsPerVersionSync()) {

            // In this scenario, each version is persisted regardless if change found
            return true;
        }

        /*-
         * Check new version refset version date. If none returned (null), then:
         * a) no changes to refset itself and 
         * b) thus no need to create  new version.
         * c) Move onto nex refset
         */
        Date refsetVersionDate = RefsetMemberService.getLatestChangedVersionDate(branchPath, refsetId);

        if (refsetVersionDate == null) {

            // No changes to refset so don't create a new version
            logger.info("No changes to refset " + refsetId + " was found in version: " + branchVersion + ", so not persisting this version");

            return false;
        }

        Date earliestPublishedVersionDate = null;

        if (!editionVersions.contains(refsetVersionDate)) {

            for (Date editionDate : editionVersions) {

                if (refsetVersionDate.after(editionDate)) {

                    logger.error("Ignoring this member as have bad content - Can't have refsets with a member that has an effectiveDate:  " + refsetVersionDate + " that is AFTER the editionDate: "
                            + editionDate);
                    continue;
                }

                if (earliestPublishedVersionDate == null || editionDate.before(earliestPublishedVersionDate)) {

                    earliestPublishedVersionDate = editionDate;
                }

            }

            if (earliestPublishedVersionDate == null) {

                throw new Exception("Bad content likely brought us here as unable to find a valid earliest w/ refsetId: " + refsetId + " & branchVersion: " + branchVersion + " & versionDate: "
                        + versionDate + " & branchPath: " + branchPath);
            }

            refsetVersionDate = earliestPublishedVersionDate;
        }

        versionDate = refsetVersionDate;

        if (!editionVersions.contains(versionDate)) {

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

                        throw new Exception("Not able to properly identify refset name for description: " + descriptionNode);
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

            // identify project name and description from Rtt Json
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

            return utilities.addProject(projectDetails[0].replaceFirst("\"", ""), projectDetails[1], refsetEditions.get(refset.getRefsetId()));
        } else {

            // No project associated with refset, so use default Edition Project
            if (!defaultEditionProjects.containsKey(refset.getEdition().getShortName())) {

                throw new Exception("Default project should have already been created of Edition: " + refset.getEdition().getName());
            }

            return defaultEditionProjects.get(refset.getEdition().getShortName());
        }

    }

    private void associateRefsetProject(Refset refset) throws Exception {

        Project project = null;
        // Set refset Project making sure to cache it based on refsetId
        if (utilities.getPropertyReader().getRefsetToProjectsInfoMap().containsKey(refset.getRefsetId())) {

            final String projectInfo = utilities.getPropertyReader().getRefsetToProjectsInfoMap().get(refset.getRefsetId());
            final String rttProjectId = projectInfo.split("\t")[0];

            if (!rttProjects.containsKey(rttProjectId)) {

                project = createRefsetProject(refset);
                rttProjects.put(rttProjectId, project);
            }

            project = rttProjects.get(rttProjectId);
        } else {

            project = defaultEditionProjects.get(refsetEditions.get(refset.getRefsetId()).getShortName());

        }

        if (project == null) {
            final String message = "Must have created from RTT, already created from RTT, or found a UAT default project for this refset: " + refset.getRefsetId() + " / " + refset.getVersionDate();
            logger.error(message);

            throw new Exception(message);
        }

        refset.setProject(project);

    }

    // Do not persist as will be done later
    private void associateRefsetClauses(final String rttId, final Refset refset) throws Exception {

        // If has ECL clauses, associate them with refset
        if (utilities.getPropertyReader().getRttRefsetToClausesMap().containsKey(rttId)) {

            Set<DefinitionClause> clauses = utilities.getRefsetClauses(rttId);
            refset.getDefinitionClauses().addAll(clauses);
        }

    }

    /**
     * Update refsets with values from json and with identifying latestVersion, but do not persist at this point.
     * @param refsetsUpdated
     *
     * @param allRefsets the all refsets
     * @throws Exception
     */
    private void updateRefsetsWithRttMetadata(Set<Refset> refsetsUpdated) throws Exception {

        // TODO: Determine if need to initialize latestRefsetCache (with unchanged) prior
        Map<String, Date> latestRefsetCache = new HashMap<>();

        for (Refset refset : refsetsUpdated) {
            try {
                // identify the corresponding project which also defines the edition
                associateRefsetProject(refset);

                // For now, default all refsets to PUBLIC
                refset.setPrivateRefset(false);

                // Update refset from JSON for Narrative, Type, tags, and ecl clauses. Project too.
                if (utilities.getPropertyReader().getRttRefsetSctIdToRttIdMap().keySet().contains(refset.getRefsetId())) {

                    /* Refset lived in RTT as well */
                    final Set<String> rttIds = utilities.getPropertyReader().getRttRefsetSctIdToRttIdMap().get(refset.getRefsetId());

                    // Add Refset with RTT data as long as it also version resides on snowstorm. Keep track of which are added this way as to not add them from RTT as well
                    for (String rttId : rttIds) {

                        final String refsetJsonString = utilities.getPropertyReader().getRttIdToRefsetJsonMap().get(rttId);

                        final ObjectMapper mapper = new ObjectMapper();
                        final JsonNode refsetJson = mapper.readTree(refsetJsonString);

                        final Date rttDataRefsetVersion = utilities.getSdf().parse(refsetJson.get("version").asText());

                        if (rttDataRefsetVersion != null && rttDataRefsetVersion.equals(refset.getVersionDate())) {

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
                if (!latestRefsetCache.containsKey(refset.getRefsetId()) || latestRefsetCache.get(refset.getRefsetId()).before(refset.getVersionDate())) {

                    latestRefsetCache.put(refset.getRefsetId(), refset.getVersionDate());
                }

            } catch (Exception e) {
                logger.error("Failed on refsetVersion: " + refset.getRefsetId() + " / " + refset.getBranchPath() + " --- with message: " + e.getMessage());
            }

        }

        // Have latest version per refset. Set the latestVersion flag to true for them
        for (Refset refset : refsetsUpdated) {

            if (latestRefsetCache.containsKey(refset.getRefsetId())) {

                for (String refsetId : latestRefsetCache.keySet()) {

                    if (refset.getRefsetId().equals(refsetId) && refset.getVersionDate().equals(latestRefsetCache.get(refsetId))) {

                        refset.setLatestPublishedVersion(true);

                        break;
                    }

                }

            }

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
}
