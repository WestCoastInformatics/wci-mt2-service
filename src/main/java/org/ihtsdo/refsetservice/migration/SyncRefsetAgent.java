package org.ihtsdo.refsetservice.migration;

import java.util.ArrayList;
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

    private final static Set<Refset> snowstormRefsets = new HashSet<>();

    private static final int ELASTICSEARCH_MAX_RECORD_LENGTH = 9990;

    private static Map<String, List<Date>> refsetToPublishedVersionMap = new HashMap<>();

    final private static Set<SyncRefsetMetadata> refsetsToProcess = new HashSet<>();

    protected SyncRefsetAgent() throws Exception {

        super();
    }

    public static void syncSnowstormRefsets(Map<String, SortedMap<Date, String>> branchesToProcess) throws Exception {

        logger.info(" syncSnowstormRefsets: Identifying Refsets to process on Snowstorm per edition/version pair");

        Set<SyncRefsetMetadata> refsetsToProcess = filterRefsetsToProcess(branchesToProcess);

        // Map each refsetId/version pair's SyncRefsetMetadata
        Map<String, Map<Date, SyncRefsetMetadata>> allSnowstormRefsetVersionPairs = parseSnowstormRefsetVersionPairs(refsetsToProcess);
        Map<String, Map<Date, Refset>> allDatabaseRefsetVersionPairs = parseDatabaseRefsetVersionPairs();

        for (String refsetId : allSnowstormRefsetVersionPairs.keySet()) {

            for (Date version : allSnowstormRefsetVersionPairs.get(refsetId).keySet()) {

                logger.info(" 999-a DB refset/version pair : " + refsetId + "/" + version);

                syncRefsetVersion(version, allSnowstormRefsetVersionPairs.get(refsetId).get(version), allDatabaseRefsetVersionPairs.get(refsetId));
            }

        }

        finalizeRefsets();

    }

    private static Refset syncRefsetVersion(Date version, SyncRefsetMetadata snowstormRefsetVersionData, Map<Date, Refset> databaseRefsetVersionPairs) throws Exception {

        Refset syncedRefset = null;

        if (databaseRefsetVersionPairs == null || !databaseRefsetVersionPairs.containsKey(version)) {
            logger.debug(" 999-b first time seeing refset/version pair");

            // First time seeing refset version pair from snowstorm
            syncedRefset = syncNewRefsetVersionPair(snowstormRefsetVersionData);

        } else {
            logger.debug(" 999-c refset/version pair lives on DB already. See if changed");

            Refset databaseRefsetVersion = databaseRefsetVersionPairs.get(version);
            syncedRefset = syncExistingRefsetVersionPairs(snowstormRefsetVersionData, databaseRefsetVersion);
        }

        return syncedRefset;

    }

    private static Refset syncExistingRefsetVersionPairs(SyncRefsetMetadata snowstormRefsetData, Refset refset) throws Exception {

        final Refset syncedRefset = compareAndUpdateRefsetDifferences(refset, snowstormRefsetData);

        if (syncedRefset == null) {
            logger.debug(" 999-d unchanged");
            refsetVersionsUnchanged.add(refset);
        } else {

            logger.debug(" 999-e synced");
            refsetVersionsSynced.add(syncedRefset);

            postRefsetProcessing(syncedRefset, snowstormRefsetData.getEdition());
        }

        logger.info("Synced Refset: " + syncedRefset);

        return syncedRefset;
    }

    private static Refset syncNewRefsetVersionPair(SyncRefsetMetadata refsetData) throws Exception {

        final String moduleId = refsetData.getRefsetNode().get("moduleId").asText();
        logger.debug("888-a moduleId: " + moduleId);

        final String refsetId = refsetData.getRefsetNode().get("conceptId").asText();
        final String snowstormRefsetName = identifyRefsetName(refsetData);

        Refset newRefset = utilities.addRefset(snowstormRefsetName, refsetId, moduleId, refsetData.getVersion(), Refset.EXTENSIONAL, "", null);

        snowstormRefsets.add(newRefset);

        refsetVersionsAdded.add(newRefset);

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
        refsetsUpdated.addAll(refsetVersionsAdded);
        refsetsUpdated.addAll(refsetVersionsSynced);

        int count = 0;

        // TODO: See if any persisted Refsets are not even in Snowstorm. If so, inactivate them
        updateRefsetsWithRttMetadata(refsetsUpdated);

        try (final TerminologyService service = new TerminologyService()) {

            utilities.initializeService(service);

            // Persist Projects and Organizations from Snowstorm
            logger.debug(" step - Start persisting gathered Snowstorm & RTT Supporting Objects");

            // Adding refsets identified on snowstorm
            for (Refset addedRefset : refsetsUpdated) {

                finailzeRefset(service, addedRefset, ++count);
            }

        }

    }

    private static void finailzeRefset(TerminologyService service, Refset refset, int count) throws Exception {

        // Final Persistence of refset object
        Refset finalizedRefset = service.update(refset);

        logger.debug("Updated refset: " + finalizedRefset);

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

    private static Refset compareAndUpdateRefsetDifferences(Refset existingRefset, SyncRefsetMetadata refsetSnowstormData) throws Exception {

        /* Found existing Edition. Compare the values to determine if something changed, and if so, update the edition accordingly */
        boolean modificationMade = false;

        final String snowstormRefsetName = identifyRefsetName(refsetSnowstormData);
        final String snowstormModuleId = refsetSnowstormData.getRefsetNode().get("moduleId").asText();
        final boolean isActiveSnowstormRefset = refsetSnowstormData.getRefsetNode().get("active").asBoolean();
        final String snowstormRefsetNarrative = refsetSnowstormData.getRefsetNode().has("narrative") ? refsetSnowstormData.getRefsetNode().get("narrative").asText() : "";

        if (!existingRefset.getName().equals(snowstormRefsetName)) {

            logger.debug(" inconsistent Refset name with '" + existingRefset.getName() + "' and '" + snowstormRefsetName + "'");

            existingRefset.setName(snowstormRefsetName);
            modificationMade = true;
        }

        if (!existingRefset.getModuleId().equals(snowstormModuleId)) {

            logger.debug(" inconsistent Refset moduleId with '" + existingRefset.getModuleId() + "' and '" + snowstormModuleId + "'");

            existingRefset.setModuleId(snowstormModuleId);
            modificationMade = true;
        }

        if (existingRefset.isActive() != isActiveSnowstormRefset) {

            logger.debug(" inconsistent Refset active with '" + existingRefset.isActive() + "' and '" + isActiveSnowstormRefset + "'");

            existingRefset.setActive(isActiveSnowstormRefset);
            modificationMade = true;
        }

        if (existingRefset.getVersionDate().getTime() != refsetSnowstormData.getVersion().getTime()) {

            logger.debug(" inconsistent Refset version with '" + existingRefset.getVersionDate() + "' (" + existingRefset.getVersionDate().getTime() + ") and '" + refsetSnowstormData.getVersion()
                + "' (" + existingRefset.getVersionDate().getTime() + ")");

            existingRefset.setVersionDate(refsetSnowstormData.getVersion());
            modificationMade = true;
        }

        // Value may come from project.txt file (Rtt), so don't overwrite if what is on Snowstorm is empty.
        // TODO: Handle narrative updates to Snowstorm
        if (!snowstormRefsetNarrative.isBlank() && !existingRefset.getNarrative().equals(snowstormRefsetNarrative)) {

            logger.debug(" inconsistent Refset narrative with '" + existingRefset.getNarrative() + "' and '" + snowstormRefsetNarrative + "'");

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

                logger.debug("Identifying refset (" + refset.getRefsetId() + ") for first time in this version - " + branchDateFormatter.format(refset.getVersionDate()));

                uniqueRefsetIds.add(refset.getRefsetId());
            } else {

                logger.debug("Again seeing: " + refset.getRefsetId());
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
            logger.info("Syncing refsets on Edition: " + edition.getName());

            String url = SnowstormConnection.BASE_URL + "browser/{branch}/members?active=true&referenceSet=%3C" + SIMPLE_TYPE_REFSET_SCTID + "&module=%3C%3C" + edition.getTopLevelModule();
            logger.debug(" url: " + url);

            for (Date version : branchesToProcess.get(editionId).keySet()) {

                final String branchPath = branchesToProcess.get(editionId).get(version);

                try (final Response response = SnowstormConnection.getResponse(url.replace("{branch}", branchPath))) {

                    if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {

                        if (edition.getBranch().startsWith("MAIN")) {

                            throw new Exception("Unable to process edition called with: " + url.replace("{branch}", branchPath));
                        } else {

                            logger.debug("Found that '" + edition.getName() + "' has odd branch: " + edition.getBranch());
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

        return refsetsToProcess;
    }

    protected static void populateInitialData() throws Exception {

        logger.info(" step - Populating initial data");

        MigrationDataInitializer initializer = new MigrationDataInitializer();
        initializer.initialize(develeperTestingOranization, allDatabaseOrganizations, allDatabaseRefsets, null);
        // initializer.printResults();

        logger.info(" step complete - Adding special content");
    }

    private static Date identifyNextRefsetVersion(String branch, String refsetId) throws Exception {

        // Get all members
        // EG: https://dev-integration-snowstorm.ihtsdotools.org/snowstorm/snomed-ct/browser/SNOMEDCT-BE/members?referenceSet=1235&offset=0&limit=10
        // EG: https://dev-integration-snowstorm.ihtsdotools.org/snowstorm/snomed-ct/SNOMEDCT-BE/members?referenceSet=1235&offset=0&limit=10

        int limit = ELASTICSEARCH_MAX_RECORD_LENGTH;
        String searchAfter = "";

        Date refsetLatestDate = null;
        final long start = System.currentTimeMillis();
        boolean hasMorePages = true;
        final String acceptLanguage = SnowstormConnection.DEFAULT_ACCECPT_LANGUAGES;
        int iteration = 0;

        while (hasMorePages) {

            logger.debug("Here on iteration #" + iteration + " for " + refsetId + " --- " + branch);

            String url = SnowstormConnection.BASE_URL + branch + "/members?referenceSet=" + refsetId + searchAfter + "&limit=" + limit;

            try (final Response response = SnowstormConnection.getResponse(url, acceptLanguage)) {

                if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {

                    hasMorePages = false;
                    throw new Exception("call to url '" + url + "' wasn't successful. " + response.toString());
                }

                final String resultString = response.readEntity(String.class);

                // Only process payload if Rest call is successful
                if (response.getStatus() != Response.Status.OK.getStatusCode()) {

                    throw new Exception(Integer.toString(response.getStatus()));
                }

                final ObjectMapper mapper = new ObjectMapper();
                final JsonNode root = mapper.readTree(resultString.toString());
                JsonNode conceptNodeBatch = root.get("items");

                searchAfter = (root.get("searchAfter") != null ? "&searchAfter=" + root.get("searchAfter").asText() : "");

                if (conceptNodeBatch.size() == 0 || conceptNodeBatch.size() < limit) {

                    logger.debug("Done at iteration #" + iteration);
                    hasMorePages = false;
                }

                if (System.currentTimeMillis() - start > TIMEOUT_MILLISECOND_THRESHOLD) {

                    hasMorePages = false;
                }

                Iterator<JsonNode> iterator = conceptNodeBatch.iterator();

                JsonNode memberNode = null;

                Date versionLatestDate = null;

                while (iterator.hasNext()) {

                    memberNode = iterator.next();

                    Date memberEffectiveTime = branchDateFormatter.parse(memberNode.get("releasedEffectiveTime").asText());

                    if (versionLatestDate == null || versionLatestDate.before(memberEffectiveTime)) {

                        versionLatestDate = memberEffectiveTime;
                    }

                }

                if (versionLatestDate != null || refsetLatestDate.before(versionLatestDate)) {

                    refsetLatestDate = versionLatestDate;
                }

                iteration++;

            } catch (Exception e) {

                throw new Exception("Caught during defining refset version on: " + refsetId + " --- " + branch + "\n" + e.getStackTrace().toString());
            }

        }

        // See if version already exists.
        if (!refsetToPublishedVersionMap.containsKey(refsetId)) {

            refsetToPublishedVersionMap.put(refsetId, new ArrayList<Date>());
        }

        if (refsetToPublishedVersionMap.get(refsetId).contains(refsetLatestDate)) {

            return null;
        } else {

            refsetToPublishedVersionMap.get(refsetId).add(refsetLatestDate);
            return refsetLatestDate;
        }

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
    private static Project createRefsetProject(String refsetId) throws Exception {

        logger.debug(".... Creating project for refsetId " + refsetId);

        Organization org = getOrgFromRefset(refsetId);

        logger.debug(" 555-a with org: " + org);

        if (utilities.getPropertyReader().getRefsetToProjectsInfoMap().containsKey(refsetId)) {

            logger.debug(" 555-b");

            // identify project name and description from Rtt Json
            String projectInfo = utilities.getPropertyReader().getRefsetToProjectsInfoMap().get(refsetId);
            String[] projectDetails = projectInfo.split(",");

            logger.debug("    Refset has an associated project is defined in RTT with the following: " + projectInfo);

            // Clean out project Details
            for (int i = 0; i < 2; i++) {

                if (projectDetails[i].startsWith("\"")) {

                    projectDetails[i] = projectDetails[i].substring(1);
                }

                if (projectDetails[i].endsWith("\"")) {

                    projectDetails[i] = projectDetails[i].substring(0, projectDetails[i].length() - 1);
                }

            }

            logger.debug(" 555-c with projectDetails: " + projectDetails);

            logger.info("    Creating new project based on project in RTT for " + projectDetails[0].replaceFirst("\"", ""), projectDetails[1]);
            return utilities.addProject(org, projectDetails[0].replaceFirst("\"", ""), projectDetails[1]);
        } else {

            logger.debug(" 555-d");

            logger.debug("    Refset doesn't have an associated project in RTT, so use Org's RT2-default");

            // No project associated with refset, so use default Edition Project
            if (!SyncCodeSystemAgent.getOrganizationToDefaultProjectMap().containsKey(org.getId())) {

                throw new Exception("Default project should have already been created of Org: " + org.getName());
            }

            logger.debug(" 555-e");

            return SyncCodeSystemAgent.getOrganizationToDefaultProjectMap().get(org.getId());
        }

    }

    private static void associateRefsetProject(Refset refset, Map<String, Project> rttProjects) throws Exception {

        Project project = null;

        // Set refset Project making sure to cache it based on refsetId
        if (utilities.getPropertyReader().getRefsetToProjectsInfoMap().containsKey(refset.getRefsetId())) {

            final String projectInfo = utilities.getPropertyReader().getRefsetToProjectsInfoMap().get(refset.getRefsetId());
            final String rttProjectId = projectInfo.split("\t")[0];

            if (!rttProjects.containsKey(rttProjectId)) {

                logger.info("Creating new project for refset: " + refset.getRefsetId());
                project = createRefsetProject(refset.getRefsetId());

                rttProjects.put(rttProjectId, project);
            }

            project = rttProjects.get(rttProjectId);
        } else {

            // User Org's default project
            Organization org = getOrgFromRefset(refset.getRefsetId());

            project = SyncCodeSystemAgent.getOrganizationToDefaultProjectMap().get(org.getId());
        }

        if (project == null) {

            throw new Exception("Must have created from RTT, already crearted from RTT, or found a UAT default project for this refset: " + refset.getRefsetId() + " / " + refset.getVersionDate());
        }

        logger.debug("Associating project " + project.getId() + " with refset: " + refset.getRefsetId());

        refset.setProject(project);
        logger.debug("444-z with refset.project: " + refset.getProject());

    }

    // Do not persist as will be done later
    private static void associateRefsetClauses(final String rttId, final Refset refset) throws Exception {

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
    private static void updateRefsetsWithRttMetadata(Set<Refset> refsetsUpdated) throws Exception {

        Map<String, Date> latestRefsetCache = new HashMap<>();
        Map<String, Project> rttProjects = new HashMap<>();

        logger.info("Updating " + refsetsUpdated.size() + " refsets with Project and attribute data");

        for (Refset refset : refsetsUpdated) {

            if (utilities.getPropertyReader().getRefsetToClausesInfoMap().containsKey(refset.getRefsetId())) {

                logger.info("Have clause on refset: " + refset.getRefsetId());
            }

            // identify the corresponding project which also defines the edition
            associateRefsetProject(refset, rttProjects);

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
                    } else {

                        logger.debug("333-c didn't match with rttDataRefsetVersion: " + rttDataRefsetVersion + " and refset.version: " + refset.getVersionDate());
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

}
