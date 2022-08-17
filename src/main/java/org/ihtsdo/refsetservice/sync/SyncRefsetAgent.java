package org.ihtsdo.refsetservice.sync;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.stream.Collectors;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status.Family;

import org.ihtsdo.refsetservice.model.DefinitionClause;
import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.Organization;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.terminologyservice.SnowstormConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class SyncRefsetAgent extends SyncAgent {

    private static Logger logger = LoggerFactory.getLogger(SyncRefsetAgent.class);

    private static final Set<Refset> snowstormRefsets = new HashSet<>();

    private static final Map<String, Edition> refsetEditions = new HashMap<>();

    private static final Set<SyncRefsetMetadata> refsetsToProcess = new HashSet<>();

    protected SyncRefsetAgent() throws Exception {

        super();

        snowstormRefsets.clear();
        refsetsToProcess.clear();
        refsetEditions.clear();
    }

    public static void syncSnowstormRefsets(Map<String, SortedMap<Date, String>> branchesToProcess) throws Exception {

        Set<SyncRefsetMetadata> refsetsToProcess = filterRefsetsToProcess(branchesToProcess);

        // Map each refsetId/version pair's SyncRefsetMetadata
        Map<String, Map<Date, SyncRefsetMetadata>> allSnowstormRefsetVersionPairs = parseSnowstormRefsetVersionPairs(refsetsToProcess);
        Map<String, Map<Date, Refset>> allDatabaseRefsetVersionPairs = parseDatabaseRefsetVersionPairs();

        int counter = 0;

        for (String refsetId : allSnowstormRefsetVersionPairs.keySet()) {

            counter += allSnowstormRefsetVersionPairs.get(refsetId).size();
        }

        logger.info(" syncSnowstormRefsets: Examinging if there are any new or changes to the  " + counter + " refset/version pairs found on Snowstorm");

        counter = 0;

        for (String refsetId : allSnowstormRefsetVersionPairs.keySet()) {

            for (Date version : allSnowstormRefsetVersionPairs.get(refsetId).keySet()) {

                syncSingleRefsetVersion(version, allSnowstormRefsetVersionPairs.get(refsetId).get(version), allDatabaseRefsetVersionPairs.get(refsetId));

                if (++counter % 100 == 0) {

                    logger.info("... processed " + counter);
                }

            }

        }

        finalizeRefsets();

    }

    private static Refset syncSingleRefsetVersion(Date version, SyncRefsetMetadata snowstormRefsetVersionData, Map<Date, Refset> databaseRefsetVersionPairs) throws Exception {

        Refset syncedRefset = null;

        if (databaseRefsetVersionPairs == null || !databaseRefsetVersionPairs.containsKey(version)) {

            // First time seeing refset version pair from snowstorm
            syncedRefset = handleNewRefsetVersionPair(snowstormRefsetVersionData);

        } else {

            Refset databaseRefsetVersion = databaseRefsetVersionPairs.get(version);
            syncedRefset = handleExistingRefsetVersionPairs(snowstormRefsetVersionData, databaseRefsetVersion);
        }

        return syncedRefset;

    }

    private static Refset handleExistingRefsetVersionPairs(SyncRefsetMetadata snowstormRefsetData, Refset refset) throws Exception {

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

    private static Refset handleNewRefsetVersionPair(SyncRefsetMetadata refsetData) throws Exception {

        final String moduleId = refsetData.getRefsetNode().get("moduleId").asText();
        final String refsetId = refsetData.getRefsetNode().get("conceptId").asText();
        final String snowstormRefsetName = identifyRefsetName(refsetData);

        Refset newRefset = utilities.addRefset(snowstormRefsetName, refsetId, moduleId, refsetData.getVersion(), Refset.EXTENSIONAL, "", null);

        snowstormRefsets.add(newRefset);

        statistics.getRefsetVersionsAdded().add(newRefset);

        postRefsetProcessing(newRefset, refsetData.getEdition());

        return newRefset;

    }

    private static Map<String, Map<Date, Refset>> parseDatabaseRefsetVersionPairs() {

        Map<String, Map<Date, Refset>> retMap = new HashMap<>();

        for (Refset dbRefset : allDatabaseRefsets) {

            if (!retMap.containsKey(dbRefset.getRefsetId())) {

                retMap.put(dbRefset.getRefsetId(), new HashMap<>());
            }

            retMap.get(dbRefset.getRefsetId()).put(dbRefset.getVersionDate(), dbRefset);
        }

        return retMap;
    }

    private static Map<String, Map<Date, SyncRefsetMetadata>> parseSnowstormRefsetVersionPairs(Set<SyncRefsetMetadata> refsetsToProcess) {

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

    private static void finalizeRefsets() throws Exception {

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

    private static void finailzeRefset(TerminologyService service, Refset refset, int count) throws Exception {

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
     *  Note: Not supporting project updates or changes in RTT (such as ECL clauses) as that should be managed in tool
     */

    private static Refset compareAndUpdateRefsetDifferences(Refset existingRefset, SyncRefsetMetadata refsetSnowstormData) throws Exception {

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

    private static String identifyRefsetName(SyncRefsetMetadata refsetSnowstormData) throws Exception {

        String refsetName;

        if (refsetSnowstormData.getRefsetNode().get("pt").has("term")) {

            refsetName = refsetSnowstormData.getRefsetNode().get("pt").get("term").asText();
        } else {

            refsetName = lookupRefsetName(refsetSnowstormData.getRefsetNode().get("conceptId").asText(), refsetSnowstormData.getEdition(), refsetSnowstormData.getBranchPath());
        }

        return refsetName;
    }

    private static void postRefsetProcessing(Refset refset, Edition edition) {

        if (refset != null) {

            refsetEditions.put(refset.getRefsetId(), edition);

            snowstormRefsets.add(refset);

            if (!uniqueRefsetIds.contains(refset.getRefsetId())) {

                uniqueRefsetIds.add(refset.getRefsetId());
            }

        }

    }

    private static Set<SyncRefsetMetadata> filterRefsetsToProcess(Map<String, SortedMap<Date, String>> branchesToProcess) throws Exception {

        for (String editionId : branchesToProcess.keySet()) {

            List<Edition> editions = allDatabaseEditions.stream().filter(e -> e.getId().equals(editionId)).collect(Collectors.toList());

            if (editions == null || editions.size() != 1) {

                throw new Exception("Have unexpected editions matching with editionId '" + editionId + "'. Editions: " + editions);
            }

            final Edition edition = editions.iterator().next();

            for (String module : utilities.getEditionModulesMap().get(edition.getShortName())) {

                String url = SnowstormConnection.BASE_URL + "browser/{branch}/members?active=true&referenceSet=%3C" + SIMPLE_TYPE_REFSET_SCTID + "&module=%3C%3C" + module;

                for (Date version : branchesToProcess.get(editionId).keySet()) {

                    final String branchPath = branchesToProcess.get(editionId).get(version);

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

                        while (refsetIterator.hasNext()) {

                            final JsonNode refsetNode = refsetIterator.next();

                            if (!refsetNode.has("moduleId") || !refsetNode.has("conceptId") || !refsetNode.has("active")) {

                                throw new Exception("Getting unexpected Refset info from node: " + refsetNode.toString());
                            }

                            final String moduleId = refsetNode.get("moduleId").asText();
                            final String refsetId = refsetNode.get("conceptId").asText();
                            boolean isInternationalEdition = ("international edition".equals(edition.getName().toLowerCase())) ? true : false;

                            if (utilities.getPropertyReader().getRefsetsToIgnore().contains(refsetId)) {

                                continue;
                            }

                            /*-
                             *  Only process refset are either
                             *  a) Listed in international edition or 
                             *  b) In a non-international module
                             */
                            if (isInternationalEdition || !utilities.getInternationalModules().contains(moduleId)) {

                                if (isRefsetToProcess(refsetId)) {

                                    SyncRefsetMetadata refsetMetadata = new SyncRefsetMetadata(refsetNode, edition, branchesToProcess.get(edition.getId()).keySet(), version, branchPath);

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

    protected static void populateInitialData() throws Exception {

        logger.info(" step - Populating initial data");

        SyncDataInitializer initializer = new SyncDataInitializer();
        initializer.initialize(develeperTestingEdition, allDatabaseEditions, allDatabaseRefsets, null);

        // initializer.printResults();

        logger.info(" step complete - Adding special content");
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
    private static String lookupRefsetName(String refsetId, Edition edition, String branchPath) throws Exception {

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
    private static Project createRefsetProject(Refset refset) throws Exception {

        Edition edition = refsetEditions.get(refset.getRefsetId());

        if (utilities.getPropertyReader().getRefsetToProjectsInfoMap().containsKey(refset)) {

            // identify project name and description from Rtt Json
            String projectInfo = utilities.getPropertyReader().getRefsetToProjectsInfoMap().get(refset);
            String[] projectDetails = projectInfo.split(",");

            // Clean out project Details
            for (int i = 0; i < 2; i++) {

                if (projectDetails[i].startsWith("\"")) {

                    projectDetails[i] = projectDetails[i].substring(1);
                }

                if (projectDetails[i].endsWith("\"")) {

                    projectDetails[i] = projectDetails[i].substring(0, projectDetails[i].length() - 1);
                }

            }

            return utilities.addProject(edition, projectDetails[0].replaceFirst("\"", ""), projectDetails[1]);
        } else {

            // No project associated with refset, so use default Edition Project
            if (!defaultOrganizationProjects.containsKey(edition.getId())) {

                throw new Exception("Default project should have already been created of Org: " + edition.getName());
            }

            return defaultOrganizationProjects.get(edition.getId());
        }

    }

    private static void matchRefsetToProject(Refset refset, Map<String, Project> rttProjects) throws Exception {

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

            // User Org's default project
            Edition edition = refsetEditions.get(refset.getRefsetId());

            project = defaultOrganizationProjects.get(edition.getId());

        }

        if (project == null) {

            throw new Exception("Must have created from RTT, already crearted from RTT, or found a UAT default project for this refset: " + refset.getRefsetId() + " / " + refset.getVersionDate());
        }

        refset.setProject(project);

    }

    /**
     * Update refsets with values from json and with identifying latestVersion, but do not persist at this point.
     * @param refsetsUpdated
     *
     * @param allRefsets the all refsets
     * @throws Exception
     */
    private static void updateRefsetsWithRttMetadata(Set<Refset> refsetsUpdated) throws Exception {

        Map<String, Date> latestRefsetCache = new HashMap<>();
        Map<String, Project> rttProjects = new HashMap<>();

        for (Refset refset : refsetsUpdated) {

            // identify the corresponding project which also defines the edition
            matchRefsetToProject(refset, rttProjects);

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

                        // Foundmatch, set attributes
                        setRefsetRttAttributes(rttId, refset, refsetJson);

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

        // Have latest version per refset. Set the latestVersion flag to true
        // for them
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

    private static void setRefsetRttAttributes(String rttId, Refset refset, JsonNode refsetJson) throws Exception {

        // Set type & narrative
        refset.setType(refsetJson.get("type").asText());
        refset.setNarrative(refsetJson.get("narrative").asText());

        // Tags
        if (refsetJson.has("tags")) {

            Iterator<JsonNode> tagsIterator = refsetJson.get("tags").iterator();

            while (tagsIterator.hasNext()) {

                String tag = tagsIterator.next().asText();

                refset.getTags().add(tag);
            }

        }

        // If has ECL clauses, create and associate with refset
        if (utilities.getPropertyReader().getRttRefsetToClausesMap().containsKey(rttId)) {

            Set<DefinitionClause> clauses = utilities.getRefsetClauses(rttId);

            refset.getDefinitionClauses().addAll(clauses);
        }

        // Do not persist as will be done later
    }

    protected static Organization getOrgFromRefset(String refsetId) {

        final String editionName = refsetEditions.get(refsetId).getName();
        final String editionShortName = refsetEditions.get(refsetId).getShortName();

        String orgName = editionOwnerMap.get(editionName) != null ? editionOwnerMap.get(editionName) : editionOwnerMap.get(editionShortName);
        final Organization org = statistics.getOrganizationsAdded().get(orgName);

        return org;
    }

    protected static boolean isRefsetToProcess(String refsetId) {

        return !testing || (testing && ((testingRefset == null || testingRefset.isEmpty()) || refsetId.equals(testingRefset)));
    }

}
