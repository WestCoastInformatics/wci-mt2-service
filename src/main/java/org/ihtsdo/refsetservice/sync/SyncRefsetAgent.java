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
import org.ihtsdo.refsetservice.terminologyservice.RefsetMemberService;
import org.ihtsdo.refsetservice.terminologyservice.SnowstormConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class SyncRefsetAgent extends SyncService {

    private final Logger logger = LoggerFactory.getLogger(SyncRefsetAgent.class);

    protected static final String SIMPLE_TYPE_REFSET_SCTID = "446609009";

    private final Set<Refset> snowstormRefsets = new HashSet<>();

    private final Set<SyncRefsetMetadata> refsetsToProcess = new HashSet<>();

    public SyncRefsetAgent(boolean perVersionCreation, boolean runForProduction) throws Exception {

        super(perVersionCreation, runForProduction);

        snowstormRefsets.clear();
        refsetsToProcess.clear();
    }

    public void syncSnowstorm() throws Exception {

        Set<SyncRefsetMetadata> filteredRefsets = filterRefsetsToProcess();

        // Map each refsetId/version pair's SyncRefsetMetadata
        Map<String, Map<Date, SyncRefsetMetadata>> allSnowstormRefsetVersionPairs = parseSnowstormRefsetVersionPairs(filteredRefsets);

        Map<String, Map<Date, Refset>> allDatabaseRefsetVersionPairs = parseDatabaseRefsetVersionPairs();

        int counter = 0;

        for (String refsetId : allSnowstormRefsetVersionPairs.keySet()) {

            counter += allSnowstormRefsetVersionPairs.get(refsetId).keySet().size();
        }

        logger.info(" syncSnowstormRefsets: Examinging if there are any new or changes to the  " + counter + " refset/version pairs found on Snowstorm");

        counter = 0;

        for (String refsetId : allSnowstormRefsetVersionPairs.keySet()) {

            for (Date version : allSnowstormRefsetVersionPairs.get(refsetId).keySet()) {

                syncRefsetVersion(version, allSnowstormRefsetVersionPairs.get(refsetId).get(version), allDatabaseRefsetVersionPairs.get(refsetId));

                if (++counter % 100 == 0) {

                    logger.info("... processed " + counter);
                }

            }

        }

        logger.info("Finished having processed " + counter);

        finalizeRefsets();

    }

    private Refset syncRefsetVersion(Date version, SyncRefsetMetadata snowstormRefsetVersionData, Map<Date, Refset> databaseRefsetVersionPairs) throws Exception {

        Refset syncedRefset = null;

        if (databaseRefsetVersionPairs == null || !databaseRefsetVersionPairs.containsKey(version)) {

            // First time seeing refset version pair from snowstorm
            syncedRefset = syncNewRefsetVersionPair(snowstormRefsetVersionData);

        } else {

            Refset databaseRefsetVersion = databaseRefsetVersionPairs.get(version);
            syncedRefset = syncExistingRefsetVersionPairs(snowstormRefsetVersionData, databaseRefsetVersion);
        }

        return syncedRefset;

    }

    private Refset syncExistingRefsetVersionPairs(SyncRefsetMetadata snowstormRefsetData, Refset refset) throws Exception {

        Refset syncedRefset = compareAndUpdateRefsetDifferences(refset, snowstormRefsetData);

        if (syncedRefset == null) {

            statistics.getRefsetVersionsUnchanged().add(refset);
            syncedRefset = refset;
        } else {

            statistics.getRefsetVersionsSynced().add(syncedRefset);

            postRefsetProcessing(syncedRefset, snowstormRefsetData.getEdition());
        }

        return syncedRefset;
    }

    private Refset syncNewRefsetVersionPair(SyncRefsetMetadata refsetData) throws Exception {

        final String moduleId = refsetData.getRefsetNode().get("moduleId").asText();
        final String refsetId = refsetData.getRefsetNode().get("conceptId").asText();
        final String snowstormRefsetName = identifyRefsetName(refsetData);

        Refset newRefset = utilities.addRefset(snowstormRefsetName, refsetId, moduleId, refsetData.getVersion(), Refset.EXTENSIONAL, "");

        postRefsetProcessing(newRefset, refsetData.getEdition());

        return newRefset;

    }

    private Map<String, Map<Date, Refset>> parseDatabaseRefsetVersionPairs() {

        Map<String, Map<Date, Refset>> retMap = new HashMap<>();

        for (Refset dbRefset : allDatabaseRefsets) {

            if (!retMap.containsKey(dbRefset.getRefsetId())) {

                retMap.put(dbRefset.getRefsetId(), new HashMap<>());
            }

            retMap.get(dbRefset.getRefsetId()).put(dbRefset.getVersionDate(), dbRefset);
        }

        return retMap;
    }

    private Map<String, Map<Date, SyncRefsetMetadata>> parseSnowstormRefsetVersionPairs(Set<SyncRefsetMetadata> refsetsToProcess) {

        Map<String, Map<Date, SyncRefsetMetadata>> retMap = new HashMap<>();

        for (SyncRefsetMetadata snowstormRefsetData : refsetsToProcess) {

            final String refsetId = snowstormRefsetData.getRefsetNode().get("conceptId").asText();

            if (!retMap.containsKey(refsetId)) {

                retMap.put(refsetId, new HashMap<>());
            }

            retMap.get(refsetId).put(snowstormRefsetData.getVersion(), snowstormRefsetData);
        }

        return retMap;
    }

    private void finalizeRefsets() throws Exception {

        Set<Refset> refsetsUpdated = new HashSet<>();
        refsetsUpdated.addAll(statistics.getRefsetVersionsAdded());
        refsetsUpdated.addAll(statistics.getRefsetVersionsSynced());

        int count = 0;

        // TODO: See if any persisted Refsets are not even in Snowstorm. If so, inactivate them
        updateRefsetsWithRttMetadata(refsetsUpdated);

        try (final TerminologyService service = new TerminologyService()) {

            utilities.initializeService(service);

            // Adding refsets identified on snowstorm
            for (Refset addedRefset : refsetsUpdated) {

                finailzeRefset(service, addedRefset, ++count);
            }

        }

    }

    private void finailzeRefset(TerminologyService service, Refset refset, int count) throws Exception {

        // Final Persistence of refset object
        service.update(refset);

        if (count % 250 == 0) {

            logger.info("Imported + " + count + " refsets thus far");
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

        final String snowstormRefsetName = identifyRefsetName(refsetSnowstormData);
        final String snowstormModuleId = refsetSnowstormData.getRefsetNode().get("moduleId").asText();
        final boolean isActiveSnowstormRefset = refsetSnowstormData.getRefsetNode().get("active").asBoolean();
        final String snowstormRefsetNarrative = refsetSnowstormData.getRefsetNode().has("narrative") ? refsetSnowstormData.getRefsetNode().get("narrative").asText() : "";

        // TODO: This is immutable, so nothing to check?
        if (updateAttribute("Refset name", existingRefset.getName(), snowstormRefsetName)) {

            existingRefset.setName(snowstormRefsetName);
            modificationMade = true;
        }

        // TODO: This is immutable, so nothing to check?
        if (updateAttribute("Refset moduleId", existingRefset.getModuleId(), snowstormModuleId)) {

            existingRefset.setModuleId(snowstormModuleId);
            modificationMade = true;
        }

        if (updateAttribute("Refset active", existingRefset.isActive(), isActiveSnowstormRefset)) {

            existingRefset.setActive(isActiveSnowstormRefset);
            modificationMade = true;
        }

        // TODO: This gets populated from branch, so nothing to check?
        if (updateAttribute("Refset version", existingRefset.getVersionDate().getTime(), refsetSnowstormData.getVersion().getTime())) {

            existingRefset.setVersionDate(refsetSnowstormData.getVersion());
            modificationMade = true;
        }

        // TODO: This comes from RTT, so nothing to check?
        // Value may come from project.txt file (Rtt), so don't overwrite if what is on Snowstorm is empty.
        if (!snowstormRefsetNarrative.isBlank() && updateAttribute("Refset narrative", existingRefset.getNarrative(), snowstormRefsetNarrative)) {

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

            statistics.getRefsetVersionsAdded().add(refset);

            if (!uniqueRefsetIds.contains(refset.getRefsetId())) {

                uniqueRefsetIds.add(refset.getRefsetId());
            }

        }

    }

    private Set<SyncRefsetMetadata> filterRefsetsToProcess() throws Exception {

        logger.info("About to process these branches: " + branchesToProcess.keySet());

        for (String editionShortName : branchesToProcess.keySet()) {

            List<Edition> editions = allDatabaseEditions.stream().filter(e -> e.getShortName().equals(editionShortName)).collect(Collectors.toList());

            if (editions == null || editions.size() != 1) {

                throw new Exception("Have unexpected editions matching with editionId '" + editionShortName + "'. Editions: " + editions);
            }

            final Edition edition = editions.iterator().next();

            for (String module : utilities.getEditionModulesMap().get(edition.getShortName())) {

                if (!utilities.isInternationalEdition(edition.getName()) && utilities.getInternationalModules().contains(module)) {

                    // Ignore non-international editions inheriting refsets from the int'l edition
                    continue;
                }

                String url = SnowstormConnection.BASE_URL + "browser/{branch}/members?active=true&referenceSet=%3C" + SIMPLE_TYPE_REFSET_SCTID + "&module=%3C%3C" + module;

                for (Date branchVersion : branchesToProcess.get(editionShortName).keySet()) {

                    final String branchPath = branchesToProcess.get(editionShortName).get(branchVersion);

                    try (final Response response = SnowstormConnection.getResponse(url.replace("{branch}", branchPath))) {

                        if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {

                            if (edition.getBranch().startsWith("MAIN")) {

                                throw new Exception("Unable to process edition called with: " + url.replace("{branch}", branchPath));
                            } else {

                                continue;
                            }

                        }

                        final String resultString = response.readEntity(String.class);
                        final ObjectMapper mapper = new ObjectMapper();
                        final JsonNode root = mapper.readTree(resultString.toString());

                        // get RefSets from edition as long as a) active & b)
                        // within edition's moduleˇ
                        final Iterator<JsonNode> refsetIterator = root.get("referenceSets").iterator();
                        logger.debug("Processing all refsets on " + edition.getName() + " on release date: " + branchVersion + " via url: " + url.replace("{branch}", branchPath));

                        while (refsetIterator.hasNext()) {

                            final JsonNode refsetNode = refsetIterator.next();

                            if (!refsetNode.has("moduleId") || !refsetNode.has("conceptId") || !refsetNode.has("active")) {

                                throw new Exception("Getting unexpected Refset info from node: " + refsetNode.toString());
                            }

                            final String moduleId = refsetNode.get("moduleId").asText();
                            final String refsetId = refsetNode.get("conceptId").asText();

                            if (utilities.getPropertyReader().getRefsetsToIgnore().contains(refsetId)) {

                                continue;
                            }

                            /*-
                             *  Only process refset are either
                             *  a) Listed in international edition or 
                             *  b) In a non-international module
                             */
                            if (utilities.isInternationalEdition(edition.getName()) || !utilities.getInternationalModules().contains(moduleId)) {

                                if (persistVersion(refsetId, branchVersion, branchVersion, branchPath, edition.getName(), branchesToProcess.get(edition.getShortName()).keySet())) {

                                    SyncRefsetMetadata refsetMetadata = new SyncRefsetMetadata(refsetNode, edition, branchesToProcess.get(edition.getShortName()).keySet(), branchVersion, branchPath);

                                    refsetsToProcess.add(refsetMetadata);

                                }

                            }

                        }

                    }

                }

            }

        }

        return refsetsToProcess;
    }

    private boolean persistVersion(String refsetId, Date branchVersion, Date versionDate, String branchPath, String editionName, Set<Date> editionVersions) throws Exception {

        if (refsetPerVersionSync) {

            return true;
        }

        /*-
         * Check new version refset version date. If none returned (null), then:
         * a) no changes to refset itself and 
         * b) thus no need to create  new version.
         * c) Move onto nex refset
         */
        Date refsetVersionDate = null;

        if (isRefsetToProcess(refsetId, editionName)) {

            refsetVersionDate = RefsetMemberService.getLatestChangedVersionDate(branchPath, refsetId);
        }

        if (refsetVersionDate == null) {

            // No changes to refset so don't create a new version
            return false;
        }

        Date earliestPublishedVersionDate = null;

        if (!editionVersions.contains(refsetVersionDate)) {

            for (Date editionDate : editionVersions) {

                if (refsetVersionDate.after(editionDate)) {

                    throw new Exception("Don't expect to be here at createRefsetsFromSnowstorm()");
                }

                if (earliestPublishedVersionDate == null || editionDate.before(earliestPublishedVersionDate)) {

                    earliestPublishedVersionDate = editionDate;
                }

            }

            if (earliestPublishedVersionDate == null) {

                throw new Exception("Shouldn't be here at createRefsetsFromSnowstorm()");
            }

            refsetVersionDate = earliestPublishedVersionDate;
        }

        versionDate = refsetVersionDate;

        if (!editionVersions.contains(versionDate)) {

            logger.debug(" Don't add refset versions that don't have corresponding snowstorm -based edition versions with Refset / and VersionDate pair: " + refsetId + " / " + versionDate);
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

            throw new Exception("Must have created from RTT, already created from RTT, or found a UAT default project for this refset: " + refset.getRefsetId() + " / " + refset.getVersionDate());
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

        Map<String, Date> latestRefsetCache = new HashMap<>();

        for (Refset refset : refsetsUpdated) {

            // identify the corresponding project which also defines the edition
            associateRefsetProject(refset);

            // For now, default all refsets to PUBLIC
            refset.setPrivateRefset(false);

            // Update refset from JSON for Narrative, Type, tags, and ecl clauses. Project too.
            if (utilities.getPropertyReader().getRttRefsetSctIdToRttIdMap().keySet().contains(refset.getRefsetId())) {

                /* Refset lived in RTT as well */
                final Set<String> rttIds = utilities.getPropertyReader().getRttRefsetSctIdToRttIdMap().get(refset.getRefsetId());

                // Add Refset. Keep track of which are added this way as to not add them from RTT as well

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

    protected boolean isRefsetToProcess(String refsetId, String editionName) {

        return !testing || (testing && (testingRefset == null || testingRefset.isEmpty()) || refsetId.equals(testingRefset));

    }
}
