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

    private Map<String, Map<Date, SyncRefsetMetadata>> snowstormRefsetIdToRefsetVersionsMap;

    private Map<String, Map<Date, Refset>> databaseRefsetIdToActiveRefsetVersionsMap;

    private Map<String, Map<Date, Refset>> databaseRefsetIdToInactiveRefsetVersionsMap;

    private Map<String, Set<Date>> newlyCreatedAndUnchangedRefsetVersionsMap = new HashMap<>();

    private List<Refset> dbRefsets;

    private List<Edition> dbEditions;

    // rttProject Id to Rt2Project
    private final static Map<String, Project> rttProjects = new HashMap<>();

    public void sync() throws Exception {

        initializeSync();

        // Determine code systems to sync
        analyzeCodeSystemBranches();

        List<String> addedInactivaedRefsetIds = analyzeFullRefsets();

        analyzeRefsetVersions(addedInactivaedRefsetIds);

        // Final step
        finalizeNewOrChangedRefsets();

    }

    private List<String> analyzeFullRefsets() throws Exception {
        final Set<Refset> dbActiveRefsets = new HashSet<>();
        final Set<Refset> dbInactiveRefsets = new HashSet<>();
        final List<String> newAndInactivatedRefsetIds = new ArrayList<>();

        try (TerminologyService service = new TerminologyService()) {

            // Determine new, inactivated, and existing refsets (Based on refsetId and version/branch info)
            dbRefsets.stream().filter(r -> r.isActive()).forEach(ar -> dbActiveRefsets.add(ar));
            dbRefsets.stream().filter(r -> !r.isActive()).forEach(ir -> dbInactiveRefsets.add(ir));

            // Based on filteredCodeSystems which already filtered for active code systems
            snowstormRefsetIdToRefsetVersionsMap = generateSnowstormRefsetIdtoRefsetVersionsMap();
            databaseRefsetIdToActiveRefsetVersionsMap = generateDatabaseRefsetIdtoRefsetVersionsMap(dbActiveRefsets);
            databaseRefsetIdToInactiveRefsetVersionsMap = generateDatabaseRefsetIdtoRefsetVersionsMap(dbInactiveRefsets);

            // Map each refsetId/version pair's SyncRefsetMetadata

            logger.debug("aaa snowstormRefsetIdToRefsetVersionsMap: " + snowstormRefsetIdToRefsetVersionsMap);
            logger.debug("aaa databaseRefsetIdToActiveRefsetVersionsMap: " + databaseRefsetIdToActiveRefsetVersionsMap);
            logger.debug("aaa databaseRefsetIdToInactiveRefsetVersionsMap: " + databaseRefsetIdToInactiveRefsetVersionsMap);

            // Determine and create new refsets (where db versions are needed). These are identified by those not in active nor in inactive DB refsets)
            List<String> addedRefsetIds = snowstormRefsetIdToRefsetVersionsMap.keySet().stream()
                    .filter(refsetId -> !databaseRefsetIdToActiveRefsetVersionsMap.containsKey(refsetId) && !databaseRefsetIdToInactiveRefsetVersionsMap.containsKey(refsetId))
                    .collect(Collectors.toList());
            addedRefsetIds.stream().forEach(
                    refsetId -> snowstormRefsetIdToRefsetVersionsMap.get(refsetId).keySet().stream().forEach(version -> addRefset(snowstormRefsetIdToRefsetVersionsMap.get(refsetId).get(version))));
            addedRefsetIds.stream().forEach(refsetId -> newlyCreatedAndUnchangedRefsetVersionsMap.put(refsetId, snowstormRefsetIdToRefsetVersionsMap.get(refsetId).keySet()));
            logger.debug("ccc New RefsetIds: " + addedRefsetIds);
            statistics.setRefsetIdsAdded(addedRefsetIds.size());

            // Activate previously inactivated refsets. Note: Will log and update stats after remove those that were activatedAndModified
            // TODO: Define solution although for now simply activating
            List<String> activatedRefsetIds =
                    snowstormRefsetIdToRefsetVersionsMap.keySet().stream().filter(c -> databaseRefsetIdToInactiveRefsetVersionsMap.containsKey(c)).collect(Collectors.toList());
            activatedRefsetIds.stream().forEach(refsetId -> dbHandler.updateRefsetIdsStatus(refsetId, true));

            // Inactivate active DB refsets that are not in snowstorm
            // TODO: Define solution although for now simply inactivating
            List<String> inactivatedRefsetIds =
                    databaseRefsetIdToActiveRefsetVersionsMap.keySet().stream().filter(c -> !snowstormRefsetIdToRefsetVersionsMap.keySet().contains(c)).collect(Collectors.toList());
            inactivatedRefsetIds.stream().forEach(refsetId -> dbHandler.updateRefsetIdsStatus(refsetId, false));
            logger.debug("ccc Inactivated RefsetIds: " + inactivatedRefsetIds);
            statistics.setRefsetIdsInactivated(inactivatedRefsetIds.size());

            // Determine refsetIds that were just activated to see if there are any other changes necessary
            List<String> activatedAndModifiedRefsetIds = new ArrayList<>();
            for (String refsetId : activatedRefsetIds) {
                List<Date> activatedVersionDates = new ArrayList<>(snowstormRefsetIdToRefsetVersionsMap.get(refsetId).keySet());
                java.util.Collections.sort(activatedVersionDates);

                List<Date> activatedAndModifiedVersionDates = compareAndModifyRefsetVersions(refsetId, activatedVersionDates);

                if (!activatedAndModifiedVersionDates.isEmpty()) {

                    activatedAndModifiedRefsetIds.add(refsetId);
                }
            }

            statistics.setRefsetIdsActivatedAndModified(activatedAndModifiedRefsetIds.size());

            // Finalize those refset versions that were only activated (and not further modified)
            activatedAndModifiedRefsetIds.stream().forEach(n -> activatedRefsetIds.remove(n));
            statistics.setRefsetIdsActivated(activatedRefsetIds.size());

            newAndInactivatedRefsetIds.addAll(addedRefsetIds);
            newAndInactivatedRefsetIds.addAll(inactivatedRefsetIds);

            return newAndInactivatedRefsetIds;
        }
    }

    private void analyzeRefsetVersions(List<String> addedInactivaedRefsetIds) throws Exception {
        // Having identified new refsets (where db versions are new), as well as activated/inactivated refsets (where once again db versions are new), now check refset
        // versions.
        // At end, also see with making newlyActivated versions as something to compare 1:1.
        // Perform analysis on one version at a time.
        for (String refsetId : snowstormRefsetIdToRefsetVersionsMap.keySet()) {

            // Ignore those that are listed in the new or inactivated refsetId list (activated will be processed for changes)
            if (addedInactivaedRefsetIds.contains(refsetId)) {
                continue;
            }

            // Determine and create new refsetVersions (not in active nor in inactive DB refsetVersions)
            List<Date> addedRefsetVersionDates = snowstormRefsetIdToRefsetVersionsMap.get(refsetId).keySet().stream()
                    .filter(date -> !databaseRefsetIdToActiveRefsetVersionsMap.get(refsetId).containsKey(date) && !databaseRefsetIdToInactiveRefsetVersionsMap.get(refsetId).containsKey(date))
                    .collect(Collectors.toList());
            addedRefsetVersionDates.stream().forEach(version -> addRefset(snowstormRefsetIdToRefsetVersionsMap.get(refsetId).get(version)));
            logger.debug("ccc New RefsetVersions size: " + addedRefsetVersionDates.size());
            statistics.incrementRefsetVersionsAdded(addedRefsetVersionDates.size());

            // Activate previously inactivated refsetVersions. Note: Will log and update stats after remove those that were activatedAndModified
            // TODO: Define solution although for now simply activating
            List<Date> activatedRefsetVersionDates = snowstormRefsetIdToRefsetVersionsMap.get(refsetId).keySet().stream()
                    .filter(c -> databaseRefsetIdToInactiveRefsetVersionsMap.get(refsetId).containsKey(c)).collect(Collectors.toList());
            activatedRefsetVersionDates.stream().forEach(version -> dbHandler.updateRefsetVersionStatus(refsetId, version, true));

            // Inactivate active DB refsetVersions that are not in snowstorm // TODO: Define solution although for now simply inactivating
            List<Date> inactivatedRefsetVersionDates = databaseRefsetIdToActiveRefsetVersionsMap.get(refsetId).keySet().stream()
                    .filter(c -> !snowstormRefsetIdToRefsetVersionsMap.get(refsetId).keySet().contains(c)).collect(Collectors.toList());
            inactivatedRefsetVersionDates.stream().forEach(version -> dbHandler.updateRefsetVersionStatus(refsetId, version, false));
            logger.debug("ccc Inactivated RefsetVersionDates size: " + inactivatedRefsetVersionDates.size());
            statistics.incrementRefsetVersionsInactivated(inactivatedRefsetVersionDates.size());

            // Determine RefsetVersionDates that are active in DB and found in snowstorm and compare for changes
            List<Date> existingInBothRefsetVersionDates = databaseRefsetIdToActiveRefsetVersionsMap.get(refsetId).keySet().stream()
                    .filter(c -> snowstormRefsetIdToRefsetVersionsMap.get(refsetId).containsKey(c)).collect(Collectors.toList());

            // Compare refsets (based on existing list of refsetId)
            List<Date> modifiedDBRefsetVersionDates = compareAndModifyRefsetVersions(refsetId, existingInBothRefsetVersionDates);
            List<Date> unchangedRefsetVersionDates = existingInBothRefsetVersionDates.stream().filter(e -> !modifiedDBRefsetVersionDates.contains(e)).collect(Collectors.toList());

            logger.debug("ccc existing RefsetVersionDates size: " + existingInBothRefsetVersionDates.size());
            logger.debug("ccc Modified RefsetVersionDates size: " + modifiedDBRefsetVersionDates.size());
            logger.debug("ccc Unchanged RefsetVersionDates size: " + unchangedRefsetVersionDates.size());
            statistics.incrementRefsetVersionsModified(modifiedDBRefsetVersionDates.size());
            statistics.incrementRefsetVersionsUnchanged(unchangedRefsetVersionDates.size());

            // Determine refsetVersions that were just activated to see if there are any other changes necessary
            List<Date> activatedAndModifiedRefsetVersionDates = compareAndModifyRefsetVersions(refsetId, activatedRefsetVersionDates);
            compareAndModifyRefsetVersions(refsetId, activatedRefsetVersionDates);
            statistics.incrementRefsetVersionsActivatedAndModified(activatedAndModifiedRefsetVersionDates.size());

            // Finalize those refset versions that were only activated (and not further modified)
            activatedAndModifiedRefsetVersionDates.stream().forEach(n -> activatedRefsetVersionDates.remove(n));
            statistics.incrementRefsetVersionsActivated(activatedRefsetVersionDates.size());

            // TODO: ccc here is wrong until we distinguish where tgings came from as a last step
            /*-
            logger.debug("ccc ActivatedAndModified RefsetIds size: " + activatedAndModifiedRefsetIds.size());
            logger.debug("ccc ActivatedAndModified RefsetVersionDates size: " + activatedAndModifiedRefsetVersionDates.size());
            newlyCreatedAndUnchangedRefsetVersionDatesMap.get(refsetId).addAll(modifiedRefsetVersionDates);
            
            // Finalize those RefsetVersionDates that were only activated (and not further modified)
            activatedAndModifiedRefsetIds.stream().forEach(r -> activatedRefsetIds.remove(r));
            activatedAndModifiedRefsetVersionDates.stream().forEach(r -> activatedRefsetVersionDates.remove(r));
            
            // add beloew
            allActivatedAndModifiedRefsetIds.addAll(activatedAndModifiedRefsetIds);
            
             */

            // populate newly CreatedAndUnchagned map to handle finalization
            if ((!addedRefsetVersionDates.isEmpty() || !unchangedRefsetVersionDates.isEmpty()) && !newlyCreatedAndUnchangedRefsetVersionsMap.containsKey(refsetId)) {
                newlyCreatedAndUnchangedRefsetVersionsMap.put(refsetId, new HashSet<>());
            }

            newlyCreatedAndUnchangedRefsetVersionsMap.get(refsetId).addAll(addedRefsetVersionDates);
            newlyCreatedAndUnchangedRefsetVersionsMap.get(refsetId).addAll(unchangedRefsetVersionDates);

        }
    }

    // RefsetId to map of Dates to dbRefset
    private Map<String, Map<Date, Refset>> generateDatabaseRefsetIdtoRefsetVersionsMap(Set<Refset> dbRefsets) {
        Map<String, Map<Date, Refset>> generatedMap = new HashMap<>();

        for (Refset dbRefset : dbRefsets) {

            if (!generatedMap.containsKey(dbRefset.getRefsetId())) {

                generatedMap.put(dbRefset.getRefsetId(), new HashMap<>());
            }

            generatedMap.get(dbRefset.getRefsetId()).put(dbRefset.getVersionDate(), dbRefset);
        }

        return generatedMap;

    }

    private void analyzeCodeSystemBranches() throws Exception {
        try (final TerminologyService service = new TerminologyService()) {

            // Determine all version dates, per edition, and mapped with the associated publication branch
            Map<String, SortedMap<Date, String>> editionToDateBranchMap = determineEditionBranches(filteredCodeSystems);

            logger.info("Gather refset data for each refset available with each edition's version for: " + editionToDateBranchMap.keySet());

            for (String editionShortName : editionToDateBranchMap.keySet()) {

                // handle the ignoreCoreRefsets flag
                if (getIsIgnoreCoreRefsets() && utilities.isInternationalEdition(editionShortName)) {

                    logger.info("Not processing CORE refsets per ignoreCoreRefsets = " + getIsIgnoreCoreRefsets());
                    continue;
                }

                // determine matching editions
                final List<Edition> mathcingEditions = dbEditions.stream().filter(e -> e.getShortName().equals(editionShortName)).collect(Collectors.toList());
                utilities.validateMatches(mathcingEditions, editionShortName);

                // Matching edition
                Edition edition = mathcingEditions.iterator().next();

                // Check if should process Edition
                if (edition == null) {
                    logger.error("Should this ever be null? orig msg: Not processing CORE refsets per ignoreCoreRefsets = " + getIsIgnoreCoreRefsets());

                    continue;
                }

                // Have valid edition. Filter refsets to process
                filterEditionRefsetVerionPairsToProcess(edition, editionToDateBranchMap.get(editionShortName));
            }

            logger.info("Finished determining date branches for db editions and have " + filteredRefsets.size() + " refsetsVersionPairs to process.");
        }
    }

    private void initializeSync() throws Exception {

        snowstormRefsets.clear();
        filteredRefsets.clear();
        refsetToModuleMap.clear();
        refsetEditions.clear();
        rttProjects.clear();

        updateDatabaseCache();

        try (TerminologyService service = new TerminologyService()) {
            dbRefsets = service.getAll(Refset.class);
            dbEditions = service.getAll(Edition.class);

        }
    }

    private int countPairs(Map<String, Set<Date>> newPairs) {
        int counter = 0;
        for (String refsetId : newPairs.keySet()) {
            counter += newPairs.get(refsetId).size();
        }
        return counter;
    }

    // RefsetId to map of Dates to RefsetMetadata
    private Map<String, Map<Date, SyncRefsetMetadata>> generateSnowstormRefsetIdtoRefsetVersionsMap() {

        Map<String, Map<Date, SyncRefsetMetadata>> generatedMap = new HashMap<>();

        for (SyncRefsetMetadata snowstormRefsetData : filteredRefsets) {

            final String refsetId = snowstormRefsetData.getRefsetNode().get("conceptId").asText();

            if (!generatedMap.containsKey(refsetId)) {

                generatedMap.put(refsetId, new HashMap<>());
            }

            generatedMap.get(refsetId).put(snowstormRefsetData.getVersion(), snowstormRefsetData);
        }

        return generatedMap;
    }

    private List<Date> compareAndModifyRefsetVersions(String refsetId, List<Date> existingDBRefsetVersionDates) {
        // TODO Auto-generated method stub
        return null;
    }

    private Refset compareAndUpdateRefsetDifferences(Refset existingRefset, SyncRefsetMetadata refsetSnowstormData) throws Exception {

        /* Found existing Edition. Compare the values to determine if something changed, and if so, update the edition accordingly */
        boolean modificationMade = false;

        final boolean isActiveSnowstormRefset = refsetSnowstormData.getRefsetNode().get("active").asBoolean();
        final String snowstormRefsetNarrative = refsetSnowstormData.getRefsetNode().has("narrative") ? refsetSnowstormData.getRefsetNode().get("narrative").asText() : "";

        // TODO: This is immutable, so nothing to check?
        /*-
        final String snowstormRefsetName = determineRefsetName(refsetSnowstormData);
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

            Refset updatedRefset = dbHandler.updateRefset(existingRefset);

            return updatedRefset;

        } else {

            return null;
        }

    }

    private String determineRefsetName(SyncRefsetMetadata refsetSnowstormData) throws Exception {

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
        }

    }

    private Set<SyncRefsetMetadata> filterEditionRefsetVerionPairsToProcess(Edition edition, SortedMap<Date, String> versionDateBranchMap) throws Exception {

        for (Date versionDate : versionDateBranchMap.keySet()) {

            Iterator<JsonNode> refsetIterator = getSnowstormRefsetVersionMembers(edition.getName(), edition.getBranch(), versionDateBranchMap.get(versionDate), versionDate);

            while (refsetIterator != null && refsetIterator.hasNext()) {

                JsonNode refsetNode;

                // Check if should process Refset
                if ((refsetNode = isRefsetToProcess(refsetIterator, edition.getShortName())) != null) {

                    final String refsetId = refsetNode.get("conceptId").asText();

                    if (!refsetToModuleMap.containsKey(refsetId)) {

                        String moduleId = determineConceptModuleId(refsetId, edition.getBranch());
                        refsetToModuleMap.put(refsetId, moduleId);
                    }

                    final String refsetBranchPath = versionDateBranchMap.get(versionDate);
                    final Set<Date> editionBranchDates = versionDateBranchMap.keySet();

                    // If perVersionSync, then create version per branch and return. Otherwise, determine if changes exist in this version
                    if (getIsPerVersionSync() || versionHasChanges(refsetId, versionDate, refsetBranchPath, edition.getName(), editionBranchDates)) {

                        SyncRefsetMetadata refsetMetadata = new SyncRefsetMetadata(refsetNode, edition, versionDateBranchMap.keySet(), versionDate, refsetBranchPath);

                        // Found a refset to process later on
                        filteredRefsets.add(refsetMetadata);

                    }
                }
            }
        }

        return filteredRefsets;

    }

    private Iterator<JsonNode> getSnowstormRefsetVersionMembers(String editionName, String editionBranchPath, String refsetBranchPath, Date branchVersion) throws Exception {

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
            logger.info("Listing refsets in " + editionName + " for version date: " + branchVersion + " via url: " + url.replace("{branch}", refsetBranchPath));

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

    private boolean versionHasChanges(String refsetId, Date versionDate, String refsetBranchPath, String editionName, Set<Date> editionBranchDates) throws Exception {

        /*-
         * Check new version refset version date. If none returned (null), then:
         * a) no changes to refset itself and 
         * b) thus no need to create  new version.
         * c) Move onto nex refset
         */
        Date refsetVersionDate = RefsetMemberService.getLatestChangedVersionDate(refsetBranchPath, refsetId);

        if (refsetVersionDate == null) {

            // No changes to refset so don't create a new version
            logger.info("No changes to refset " + refsetId + " was found in version: " + versionDate + ", so not persisting this version");

            return false;
        }

        Date earliestPublishedVersionDate = null;

        if (!editionBranchDates.contains(refsetVersionDate)) {

            for (Date editionDate : editionBranchDates) {

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

                throw new Exception("Bad content likely brought us here as unable to find a valid earliest w/ refsetId: " + refsetId + " & branchVersion: " + versionDate + " & versionDate: "
                        + versionDate + " & branchPath: " + refsetBranchPath);
            }

            refsetVersionDate = earliestPublishedVersionDate;
        }

        versionDate = refsetVersionDate;

        if (!editionBranchDates.contains(versionDate)) {

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

            Set<DefinitionClause> clauses = dbHandler.addDefinitionClauses(rttId);
            refset.getDefinitionClauses().addAll(clauses);
        }

    }

    /**
     * Update refsets with values from json and with identifying latestVersion, but do not persist at this point.
     * @param dbRefsets the db refsets
     * @throws Exception
     */
    private void finalizeNewOrChangedRefsets() throws Exception {

        // TODO: Determine if need to initialize latestRefsetCache (with unchanged) prior
        Map<String, Date> latestRefsetCache = new HashMap<>();
        Set<Refset> refsetsUpdated = new HashSet<>();

        try (final TerminologyService service = new TerminologyService()) {

            for (String refsetId : newlyCreatedAndUnchangedRefsetVersionsMap.keySet()) {

                for (Date version : newlyCreatedAndUnchangedRefsetVersionsMap.get(refsetId)) {

                    try {
                        List<Refset> matchingRefsets = dbRefsets.stream().filter(r -> r.getRefsetId().equals(refsetId) && r.getVersionDate().equals(version)).collect(Collectors.toList());
                        utilities.validateMatches(matchingRefsets, refsetId + " / " + version);

                        Refset refset = matchingRefsets.iterator().next();

                        // Determine the corresponding project which also defines the edition
                        associateRefsetProject(refset);

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

                        if (refset.getRefsetId().equals(refsetId) && refset.getVersionDate().equals(latestRefsetCache.get(refsetId))) {

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
    private Map<String, SortedMap<Date, String>> determineEditionBranches(Set<JsonNode> codeSystems) throws Exception {

        Map<String, SortedMap<Date, String>> editionToDateBranchMap = new HashMap<>();

        logger.info("Database editions already in DB at start of sync in determineEditionBranches() are: ");

        try (final TerminologyService service = new TerminologyService()) {

            dbEditions.stream().forEach(e -> logger.info(e.getName()));

            for (JsonNode codeSystem : codeSystems) {

                final String editionName = codeSystem.has("name") ? codeSystem.get("name").asText() : "";
                final String shortName = codeSystem.has("shortName") ? codeSystem.get("shortName").asText() : "";
                final String branch = codeSystem.has("branchPath") ? codeSystem.get("branchPath").asText() : "";

                logger.info("Identifying CodeSystem branches for: " + editionName);

                final String genericUrl = SnowstormConnection.BASE_URL + "branches/{branch}/children";

                SortedMap<Date, String> children = new TreeMap<>();
                logger.info(" genericUrl: " + genericUrl.replace("{branch}", branch));

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

                            Date branchDate = branchDateFormatter.parse(childDate);

                            if (branchDate.before(new Date())) {

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

                    logger.info("Branch Dates for edition: " + editionName);

                    for (Date child : children.keySet()) {

                        logger.info("Child: " + child.toString() + " with branch: " + children.get(child));
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
            final Date version = syncRefsetMetadata.getVersion();

            Refset newRefset = dbHandler.addRefset(refsetId, moduleId, refsetName, version, refsetType);

            // TODO: Still need to post-process?
            postRefsetProcessing(newRefset, syncRefsetMetadata.getEdition());

            return newRefset;
        } catch (Exception e) {
            logger.error("Unable to send refset metadata to dbHandler for refset: " + syncRefsetMetadata);

            return null;
        }
    }
}
