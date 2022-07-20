package org.ihtsdo.refsetservice.migration;

import java.text.SimpleDateFormat;
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

public class HistoricDataMigrator {

    private static final String SIMPLE_TYPE_REFSET_SCTID = "446609009";

    /** The formatter. */
    private final SimpleDateFormat branchDateFormatter = new SimpleDateFormat("yyyy-MM-dd");

    /**
     * The Enum FileProcessType.
     */
    public enum FileProcessType {

        /** The refset. */
        REFSET,
        /** The clause. */
        CLAUSE,
        /** The project. */
        PROJECT;
    }

    /** The logger. */
    private final Logger logger = LoggerFactory.getLogger(HistoricDataMigrator.class);

    MigrationUtilities utilities;

    /** The max number of record elasticsearch will return without erroring. */
    private static final int ELASTICSEARCH_MAX_RECORD_LENGTH = 9990;

    /** The number of milliseconds to stop processing records to avoid a gateway timeout. */
    public static final int TIMEOUT_MILLISECOND_THRESHOLD = 60000;

    private static final SimpleDateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat("yyyyMMdd");

    private static final String WCI_ORG_NAME = "wci";

    /** The refsets to ignore. */
    private final Set<String> refsetsToIgnore = new HashSet<>();

    /** The international refsets. */
    private final Set<String> internationalRefsets = new HashSet<>();

    /** The testing. */
    private boolean testing = true;

    private final String testingEdition = "elgia";

    private static final String testingRefset = "741000172102";

    private final Map<String, String> editionOwnerMap = new HashMap<>();

    private final Map<String, Project> defaultOrganizationProjects = new HashMap<>();

    private Set<String> rttRefsetIds = new HashSet<>();

    /** Should the migration be run adding a refset version for each branch version, which is faster than checking each refset for publication. */
    private boolean runShortMigration = false;

    /** Should the migration add projects, teams, and other testing data, which it shouldn't do for Production. Default is true. */
    private boolean forProduction = true;

    private Map<String, Edition> refsetEditions = new HashMap<>();

    private final Set<Refset> snowstormRefsets = new HashSet<>();

    private final Set<String> uniqueRefsetIds = new HashSet<>();

    private final Map<String, Organization> organizationsAdded = new HashMap<>();

    private Organization wciOrganization = null;

    private Map<String, List<Date>> refsetToPublishedVersionMap = new HashMap<>();

    /**
     * Identify branches.
     *
     * @return the map
     * @throws Exception the exception
     */
    private Map<String, SortedMap<Date, String>> identifyBranches() throws Exception {

        final String genericUrl = SnowstormConnection.BASE_URL + "branches/{branch}/children";
        final Map<String, SortedMap<Date, String>> retMap = new HashMap<>();

        try (final TerminologyService service = new TerminologyService()) {

            initializeService(service);

            List<Edition> editions = service.getAll(Edition.class);

            for (Edition edition : editions) {

                if (testing && !edition.getName().contains(testingEdition) && !edition.getName().toLowerCase().contains(WCI_ORG_NAME) && !edition.getName().contains("International")) {

                    continue;
                }

                SortedMap<Date, String> children = new TreeMap<>();

                try (final Response response = SnowstormConnection.getResponse(genericUrl.replace("{branch}", edition.getBranch()))) {

                    final String resultString = response.readEntity(String.class);
                    final ObjectMapper mapper = new ObjectMapper();
                    final JsonNode root = mapper.readTree(resultString.toString());

                    // get RefSets from edition as long as a) active & b) within
                    // edition's module
                    final Iterator<JsonNode> branchIterator = root.iterator();

                    while (branchIterator.hasNext()) {

                        JsonNode child = branchIterator.next();
                        final String childBranch = child.get("path").asText();
                        String childDate = childBranch.replace(edition.getBranch(), "");

                        if (childDate.startsWith("/")) {

                            childDate = childDate.substring(1);
                        }

                        // logger.debug(" Found Snowstorm Child Branch: " + childBranch);

                        // Since grabbing all children branches, avoid
                        // attempting to parse extensions i.e. MAIN/SNOMEDCT-US
                        boolean childAdded = false;

                        if (childDate.matches(".*\\d{4}-\\d{2}-\\d{2}$")) {

                            Date branchDate = branchDateFormatter.parse(childDate);

                            if (branchDate.before(new Date())) {

                                children.put(branchDate, childBranch);
                                childAdded = true;
                            }

                        }

                        if (!childAdded) {

                            // logger.info("Skipping over childBranch/branchDate pair " + edition.getBranch() + "/" + childDate + " as the branch isn't an official release
                            // branch");
                        }

                    }

                    logger.debug("Branch Dates for edition: " + edition.getName());

                    for (Date child : children.keySet()) {

                        logger.debug("Child: " + child.toString() + " with branch: " + children.get(child));
                    }

                    retMap.put(edition.getId(), children);
                }

            }

        }

        return retMap;
    }

    /**
     * Update refsets with values from json and with identifying latestVersion, but do not persist at this point.
     *
     * @param allRefsets the all refsets
     * @throws Exception
     */
    private void updateRefsetsWithRttMetadata() throws Exception {

        Map<String, Date> latestRefsetCache = new HashMap<>();
        Map<String, Project> rttProjects = new HashMap<>();

        for (Refset refset : snowstormRefsets) {

            if (testing && (testingRefset != null && !testingRefset.isEmpty() && refset.getRefsetId().equals(testingRefset))) {

                continue;
            }

            // No need to update refsets to be ignored
            if (refsetsToIgnore.contains(refset.getRefsetId())) {

                continue;
            }

            if (utilities.getPropertyReader().getRefsetToClausesInfoMap().containsKey(refset.getRefsetId())) {

                logger.info("Have clause on refset: " + refset.getRefsetId());
            }

            associateRefsetProject(refset, rttProjects);

            // For now, default all refsets to PUBLIC
            refset.setPrivateRefset(false);

            // Update refset from JSON for Narrative, Type, tags, and ecl clauses. Project too.
            if (rttRefsetIds.contains(refset.getRefsetId())) {

                /* Refset lived in RTT as well */
                final Set<String> rttIds = utilities.getPropertyReader().getRttRefsetSctIdToRttIdMap().get(refset.getRefsetId());

                // Add Refset. Keep track of which are added this way as to not add them from RTT as well

                for (String rttId : rttIds) {

                    final String refsetJsonString = utilities.getPropertyReader().getRttIdToRefsetJsonMap().get(rttId);

                    final ObjectMapper mapper = new ObjectMapper();
                    final JsonNode refsetJson = mapper.readTree(refsetJsonString);

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
        for (Refset refset : snowstormRefsets) {

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

    private void associateRefsetProject(Refset refset, Map<String, Project> rttProjects) throws Exception {

        Project project = null;

        // Set refset Project making sure to cache it based on refsetId
        if (utilities.getPropertyReader().getRefsetToProjectsInfoMap().containsKey(refset.getRefsetId())) {

            final String projectInfo = utilities.getPropertyReader().getRefsetToProjectsInfoMap().get(refset.getRefsetId());
            final String rttProjectId = projectInfo.split("\t")[0];

            if (!rttProjects.containsKey(rttProjectId)) {

                logger.debug("Creating new project for refset: " + refset.getRefsetId());
                project = createRefsetProject(refset.getRefsetId());

                rttProjects.put(rttProjectId, project);
            }

            project = rttProjects.get(rttProjectId);
        } else {

            // User Org's default project
            Organization org = getOrgFromRefset(refset.getRefsetId());

            project = defaultOrganizationProjects.get(org.getId());
        }

        if (project == null) {

            throw new Exception("Must have created from RTT, already crearted from RTT, or found a UAT default project for this refset: " + refset.getRefsetId() + " / " + refset.getVersionDate());
        }

        logger.debug("Associating project with refset: " + refset.getRefsetId());
        refset.setProject(project);
    }

    /**
     * Populate editions.
     *
     * @param branchChildrenByEdition the branch children
     * @param internationalModules the international modules
     * @return the sets the
     * @throws Exception the exception
     */
    private Set<Refset> createRefsetsFromSnowstorm(Map<String, SortedMap<Date, String>> branchChildrenByEdition, Set<String> internationalModules) throws Exception {

        int nonInternationalRefsetCount = 0;

        logger.debug("Num internationalModules: " + internationalModules.size());
        logger.debug("Num branches: " + branchChildrenByEdition.size());

        try (final TerminologyService service = new TerminologyService()) {

            initializeService(service);

            List<String> ignoredRefsets = utilities.getPropertyReader().readRefsetsToIgnore();

            logger.info("---> Starting to identify Refsets on Snowstorm by edition/version pair");

            for (String editionId : branchChildrenByEdition.keySet()) {

                final Edition edition = service.get(editionId, Edition.class);

                String url = SnowstormConnection.BASE_URL + "browser/{branch}/members?active=true&referenceSet=%3C" + SIMPLE_TYPE_REFSET_SCTID + "&module=%3C%3C" + edition.getTopLevelModule();

                if (editionId.equals(branchChildrenByEdition.keySet().iterator().next())) {

                    logger.debug("   URL to identify refsets and the way updated per branch: " + url + " with following code: <<url.replace(\"{branch}\", childBranch)>>\n");
                }

                logger.info("Processing Edition: " + edition.getName());

                boolean isInternationalEdition = ("international edition".equals(edition.getName().toLowerCase())) ? true : false;

                for (Date branchDate : branchChildrenByEdition.get(editionId).keySet()) {

                    final String childBranch = branchChildrenByEdition.get(editionId).get(branchDate);

                    try (final Response response = SnowstormConnection.getResponse(url.replace("{branch}", childBranch))) {

                        if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {

                            if (edition.getBranch().startsWith("MAIN")) {

                                throw new Exception("Unable to process edition called with: " + url.replace("{branch}", childBranch));
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

                            String refsetName = null;
                            Date versionDate = null;

                            final JsonNode refsetNode = refsetIterator.next();

                            if (!refsetNode.has("moduleId") || !refsetNode.has("conceptId") || !refsetNode.has("active")) {

                                throw new Exception("Getting unexpected Refset info from node: " + refsetNode.toString());
                            }

                            final String moduleId = refsetNode.get("moduleId").asText();
                            final String refsetId = refsetNode.get("conceptId").asText();

                            if (ignoredRefsets.contains(refsetId)) {

                                continue;
                            }

                            /*-
                             *  Only process refset are either
                             *  a) Listed in international edition or 
                             *  b) In a non-international module
                            
                             */
                            if (isInternationalEdition || !internationalModules.contains(moduleId)) {

                                try {

                                    if (testing && (testingRefset != null && !testingRefset.isEmpty() && refsetId.equals(testingRefset))) {

                                        logger.debug("Testing refset " + testingRefset + " with childBranch" + childBranch);
                                    }

                                    if (runShortMigration) {

                                        versionDate = branchDate;
                                    } else {

                                        /*-
                                         * Check new version refset version date. If none returned (null), then:
                                         * a) no changes to refset itself and 
                                         * b) thus no need to create  new version.
                                         * c) Move onto nex refset
                                         */
                                        Date refsetVersionDate = null;

                                        if (!testing || (testingRefset != null && !testingRefset.isEmpty() && refsetId.equals(testingRefset))) {

                                            refsetVersionDate = defineSnowstormRefsetVersionDate(childBranch, refsetId);
                                        }

                                        if (refsetVersionDate == null) {

                                            logger.debug("No changes to refset so don't create a new version");
                                            continue;
                                        }

                                        Set<Date> editionVersions = branchChildrenByEdition.get(edition.getId()).keySet();
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

                                            logger.debug(" Don't add refset versions that don't have corresponding snowstorm -based edition versions with Refset / and VersionDate pair: " + refsetId
                                                + " / " + versionDate);
                                            continue;
                                        }

                                    }

                                    // add the edition to a map with the refset ID to retrieve it later
                                    refsetEditions.put(refsetId, edition);

                                    if (refsetNode.get("pt").has("term")) {

                                        refsetName = refsetNode.get("pt").get("term").asText();
                                    } else {

                                        refsetName = lookupRefsetName(refsetId, edition, childBranch);
                                    }

                                    /* Add refset/version for later persisting */
                                    Refset refset = utilities.addRefset(refsetName, refsetId, moduleId, versionDate, Refset.EXTENSIONAL, "", null);
                                    snowstormRefsets.add(refset);

                                    if (!uniqueRefsetIds.contains(refsetId)) {

                                        /*
                                         * logger.debug("Identifying refset (" + refsetId + ") for first time in this version - " + branchDateFormatter
                                         * .format(refset.getVersionDate()));
                                         */
                                        uniqueRefsetIds.add(refsetId);
                                    } else {

                                        // logger.debug("Again seeing: " +
                                        // refsetId);
                                    }

                                } catch (Exception e) {

                                    logger.error("Failed with message: " + e.getMessage() + " for refsetNode: " + refsetNode);
                                }

                            }

                            if (isInternationalEdition) {

                                logger.debug("Identified international refsetId " + refsetId + " " + refsetName + " for " + versionDate);

                                internationalRefsets.add(refsetId);
                            } else {

                                nonInternationalRefsetCount++;
                            }

                        }

                    }

                }

            }

        }

        logger.info("Finished migrating with Snowstorm having created " + internationalRefsets.size() + " international Refsets and " + nonInternationalRefsetCount + " non-International refsets.");

        return snowstormRefsets;
    }

    private Date defineSnowstormRefsetVersionDate(String branch, String refsetId) throws Exception {

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

                    Date memberEffectiveTime = SIMPLE_DATE_FORMAT.parse(memberNode.get("releasedEffectiveTime").asText());

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
     * @param childBranch the child branch
     * @return the string
     * @throws Exception the exception
     */
    private String lookupRefsetName(String refsetId, Edition edition, String childBranch) throws Exception {

        String url = SnowstormConnection.BASE_URL + "browser/" + childBranch + "/concepts/" + refsetId;

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

    /**
     * Create supporting projects and finalize refsets.
     *
     * @param allRefsets the all refsets
     * @throws Exception the exception
     */
    private void persistRefsetObjects() throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            initializeService(service);

            // Persist Projects and Organizations from Snowstorm
            int projectCount = 0;
            int count = 0;
            int ignoreCounter = 0;

            logger.info(" step - Start persisting gathered Snowstorm & RTT Supporting Objects");

            // Adding refsets identified on snowstorm
            for (Refset snowRefset : snowstormRefsets) {

                if (testing && (testingRefset != null && !testingRefset.isEmpty() && snowRefset.getRefsetId().equals(testingRefset))) {

                    continue;
                }

                if (refsetsToIgnore.contains(snowRefset.getRefsetId())) {

                    ignoreCounter++;
                    continue;

                }

                // Final Persistance of refset object
                utilities.setMetadata(snowRefset);
                snowRefset = service.update(snowRefset);

                if (++count % 250 == 0) {

                    logger.info("Imported + " + count + " refsets thus far");
                }

            }

            logger.info(" step complete - Finish persisting gathered Snowstorm & RTT Supporting Objects");

            if (!forProduction) {

                populateInitialDate(service);
            }

            logger.info("Have imported from Snowstorm " + projectCount + " projects and " + organizationsAdded.size() + " organizations");

            logger.info("Have NOT imported anything from RTT that isn't in Snowstorm");

            logger.info("Total of " + ignoreCounter + " refsets ignored");
        } catch (Exception e) {

            logger.error("Have issue with: " + e.getMessage());
            e.printStackTrace();
        }

    }

    private void populateInitialDate(TerminologyService service) throws Exception {

        logger.info(" step - Populating initial data");

        MigrationDataInitializer initializer = new MigrationDataInitializer(utilities);
        initializer.initialize(wciOrganization, organizationsAdded, defaultOrganizationProjects);
        initializer.printResults();

        logger.info(" step complete - Adding special content");
    }

    // Do not persist as will be done later
    private void associateRefsetClauses(final String rttId, final Refset refset) throws Exception {

        // If has ECL clauses, associate them with refset
        if (utilities.getPropertyReader().getRttRefsetToClausesMap().containsKey(rttId)) {

            Set<DefinitionClause> clauses = utilities.getRefsetClauses(rttId);
            refset.getDefinitionClauses().addAll(clauses);
        }

    }

    /*
     * First checks if the refset is associated with an RTT project. If so return. If not, return the default Edition's project (creating it if not already existing)
     */
    private Project createRefsetProject(String refsetId) throws Exception {

        Organization org = getOrgFromRefset(refsetId);

        logger.debug(".... Creating project for refsetId " + refsetId);

        if (utilities.getPropertyReader().getRefsetToProjectsInfoMap().containsKey(refsetId)) {

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

            logger.info("    Creating new project based on project in RTT for " + projectDetails[0].replaceFirst("\"", ""), projectDetails[1]);
            return utilities.addProject(org, projectDetails[0].replaceFirst("\"", ""), projectDetails[1]);
        } else {

            logger.debug("    Refset doesn't have an associated project in RTT, so use Org's RT2-default");

            // No project associated with refset, so use default Edition Project
            if (!defaultOrganizationProjects.containsKey(org.getId())) {

                throw new Exception("Default project should have already been created of Org: " + org.getName());
            }

            return defaultOrganizationProjects.get(org.getId());
        }

    }

    private Organization getOrgFromRefset(String refsetId) {

        final String editionName = refsetEditions.get(refsetId).getName();
        final String editionShortName = refsetEditions.get(refsetId).getShortName();

        String orgName = editionOwnerMap.get(editionName) != null ? editionOwnerMap.get(editionName) : editionOwnerMap.get(editionShortName);
        final Organization org = organizationsAdded.get(orgName);

        
        return org;
    }

    private void initializeService(TerminologyService service) {

        service.setModifiedBy("Migration");
        service.setModifiedFlag(true);

    }

    public static String getTestingRefset() {

        return testingRefset;
    }

    /**
     * Gets the list of branch versions.
     *
     * @param runShortMigration Should the migration be run adding a refset version for each branch version, which is faster than checking each refset for publication. Default
     *            is false
     * @throws Exception the exception
     */
    public void migrate(final boolean runShortMigration, final boolean forProduction) throws Exception {

        this.runShortMigration = runShortMigration;
        utilities = new MigrationUtilities(new SyncMetadata(new Date(), MigrationUtilities.MIGRATION_USER_NAME));

        final SyncAgent agent = new SyncAgent(utilities, forProduction);

        logger.debug(" 222-a");
        final JsonNode organizationJsonRootNode = agent.getSnowstormCodeSystems();
        // logger.debug(" 222-b All CodeSystems Json: " + organizationJsonRootNode);

        Set<JsonNode> codeSystems = agent.filterCodeSystems(organizationJsonRootNode);
        logger.debug(" 222-c filtered codeSystems: " + codeSystems);
        agent.migrateEditions(codeSystems);
        logger.debug(" 222-d Finished migrating Orgs & Editions");

        /* Sync Point */

        Map<String, SortedMap<Date, String>> branches = identifyBranches();
        createRefsetsFromSnowstorm(branches, utilities.getInternationalModules());

        // Read refset metadata and associated information (projects & ECLs)
        final boolean supportRtt = false;
        rttRefsetIds = utilities.getPropertyReader().parseRttData(supportRtt);

        // With metadata from RTT project (defined in parseRTTMetadata())
        updateRefsetsWithRttMetadata();

        // Create supporting projects and finalize refsets
        persistRefsetObjects();

        logger.debug(" 222-return + ");
    }

    public void syncWithSnowstorm(boolean runForProduction) throws Exception {

        utilities = new MigrationUtilities(new SyncMetadata(new Date(), MigrationUtilities.SYNC_USER_NAME));

        final SyncAgent agent = new SyncAgent(utilities, runForProduction);

        logger.debug(" 111-a");
        final JsonNode organizationJsonRootNode = agent.getSnowstormCodeSystems();
        // logger.debug(" 111-b All CodeSystems Json: " + organizationJsonRootNode);

        Set<JsonNode> codeSystems = agent.filterCodeSystems(organizationJsonRootNode);
        logger.debug(" 111-c filtered codeSystems: " + codeSystems);

        agent.processCodeSystems(codeSystems);
        logger.debug(" 111-d Finished syncing Orgs & Editions");

        // TODO: Next is to identify Versions & refsets
        // Map<String, SortedMap<Date, String>> versions = identifyBranches();

        logger.debug(" 111-return + ");

    }
}
