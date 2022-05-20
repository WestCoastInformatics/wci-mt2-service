package org.ihtsdo.refsetservice.util;

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
import org.ihtsdo.refsetservice.model.HasModified;
import org.ihtsdo.refsetservice.model.Organization;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.model.Team;
import org.ihtsdo.refsetservice.service.SecurityService;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.terminologyservice.RefsetMemberService;
import org.ihtsdo.refsetservice.terminologyservice.SnowstormConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class HistoricDataMigrator {

    private static final String SIMPLE_TYPE_REFSET_SCTID = "446609009";

    private static final String MODULE_ANCESTOR_CONCEPT_SCTID = "900000000000443000";

    private static final String DEFAULT_LANGUAGE_REFSET = "900000000000509007";

    private static final String WCI_TESTING_REFSET_CONCEPT_ID = "92535302004";

    /** The formatter. */
    private final SimpleDateFormat branchDateFormatter = new SimpleDateFormat("yyyy-MM-dd");

    private final Metadata defaultMeta = new Metadata(Metadata.getSdf().format(new Date()), "System initialization");

    /**
     * The Class Metadata.
     */
    public class Counts {

        private int refsetsVersionPairsOnSnowstorm;

        private int uniquRefsetsOnSnowstorm;

        private int rttMetadataRefsets;

        private int uniqueRttMetadataRefsets;

        private int noMetadataRefsets;

        private int uniqueNoMetadataRefsets;

        private int orgsImported;

        public void incrementRefsetVersionPairsCounts() {

            refsetsVersionPairsOnSnowstorm++;
        }

        public void incrementUniqueRefsetsCounts() {

            uniquRefsetsOnSnowstorm++;
        }

        public void incrementRttMetadataCount() {

            rttMetadataRefsets++;
        }

        public void incrementUniqueRttMetadataCount() {

            uniqueRttMetadataRefsets++;
        }

        public void incrementNoMetadataCount() {

            noMetadataRefsets++;
        }

        public void incrementUniqueNoMetadataCount() {

            uniqueNoMetadataRefsets++;
        }

        public void incrementOrgsImportedCount() {

            orgsImported++;
        }

        public int getRefsetVersionPairsCounts() {

            return refsetsVersionPairsOnSnowstorm;
        }

        public int getUniqueRefsetsCounts() {

            return uniquRefsetsOnSnowstorm;
        }

        public int getRttMetadataCreatedCount() {

            return rttMetadataRefsets;
        }

        public int getUniqueRttMetadataCreatedCount() {

            return uniqueRttMetadataRefsets;
        }

        public int getNoMetadataCreatedCount() {

            return noMetadataRefsets;
        }

        public int getUniqueNoMetadataCreatedCount() {

            return uniqueNoMetadataRefsets;
        }

        public int getOrgsImportedCount() {

            return orgsImported;
        }
    }

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

    MigrationPropertyFileReader propertyReader = new MigrationPropertyFileReader();

    /** The max number of record elasticsearch will return without erroring. */
    private static final int ELASTICSEARCH_MAX_RECORD_LENGTH = 9990;

    /** The number of milliseconds to stop processing records to avoid a gateway timeout. */
    public static final int TIMEOUT_MILLISECOND_THRESHOLD = 60000;

    private static final SimpleDateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat("yyyyMMdd");

    /** The refsets to ignore. */
    private final Set<String> refsetsToIgnore = new HashSet<>();

    /** The international refsets. */
    private final Set<String> internationalRefsets = new HashSet<>();

    /** The testing. */
    private boolean testing = false;

    private final String testingEdition = "elgi";

    private final String testingRefset = "741000172102";

    private final Map<String, String> editionOwnerMap = new HashMap<>();

    private Set<String> rttRefsetIds = new HashSet<>();

    private boolean supportRtt = false;

    /** Should the migration be run adding a refset version for each branch version, which is faster than checking each refset for publication. */
    private boolean runShortMigration = false;

    private Map<String, Edition> refsetEditions = new HashMap<>();

    private final Set<Refset> snowstormRefsets = new HashSet<>();

    private final Set<String> uniqueRefsetIds = new HashSet<>();

    private final Map<String, Organization> organizationsAdded = new HashMap<>();

    private final Counts counts = new Counts();

    private final Set<String> debugRttOrgTranslations = new HashSet<>();

    private Map<DefinitionClause, Refset> clausesRefsetMap = new HashMap<>();

    private Organization wciOrganization = null;

    private Map<String, List<Date>> refsetToPublishedVersionMap = new HashMap<>();

    /**
     * Gets the list of branch versions.
     *
     * @param runShortMigration Should the migration be run adding a refset version for each branch version, which is faster than checking each refset for publication. Default
     *            is false
     * @throws Exception the exception
     */
    public void migrate(final boolean runShortMigration) throws Exception {

        this.runShortMigration = runShortMigration;

        Set<String> internationalModules = createEditionsFromSnowstorm();
        Map<String, SortedMap<Date, String>> branches = identifyBranches();

        createRefsetsFromSnowstorm(branches, internationalModules);

        // Read refset metadata and associated information (projects & ECLs)
        rttRefsetIds = propertyReader.parseRttData(supportRtt);

        processRttRefsets();

        // With metadata from RTT project (defined in parseRTTMetadata())
        updateRefsets();
        persistObjects();
    }

    private void processRttRefsets() {

        if (!supportRtt) {

            logger.info("Nothing to do in processRttRefses() as we are nNot pulling refsets from RTT (due to supportRtt value of: " + supportRtt + ")");
        } else {

            // Skip those refsets that live on SnowS, but are not yet in RTT DB dmp
            // file that we are using
            for (Refset refset : snowstormRefsets) {

                if (!propertyReader.getRttRefsetSctIdToRttIdMap().keySet().contains(refset.getRefsetId())) {

                    if (internationalRefsets.contains(refset.getRefsetId())) {

                        refsetsToIgnore.add(refset.getRefsetId());

                        logger.debug("Going to ignore Int'l refsets supported in " + refset.getEditionName() + " - " + refset.getRefsetId() + " - " + refset.getName());
                    }

                }

            }

        }

    }

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

            List<Edition> editions = service.getAll(Edition.class);

            for (Edition edition : editions) {

                if (testing && !edition.getName().contains(testingEdition) && !edition.getName().contains("International")) {

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
     * Update refsets with values from json and with identifying latestVersion
     *
     * @param allRefsets the all refsets
     * @throws Exception
     */
    private void updateRefsets() throws Exception {

        Map<String, Date> latestRefsetCache = new HashMap<>();

        for (Refset refset : snowstormRefsets) {

            // No need to update refsets to be ignored
            if (refsetsToIgnore.contains(refset.getRefsetId())) {

                continue;
            }

            // For now, default all refsets to PUBLIC
            refset.setPrivateRefset(false);

            if (!supportRtt) {

                // Defaults for type (extensional) & narrative (blank)
                refset.setType("EXTENSIONAL");
                refset.setNarrative("");

            } else {

                // Update refset from JSON. If JSON not available to the refset, it
                // means it resides exclusively on Snowstorm.
                if (rttRefsetIds.contains(refset.getRefsetId())) {

                    /* Refset lived in RTT as well */
                    final Set<String> rttIds = propertyReader.getRttRefsetSctIdToRttIdMap().get(refset.getRefsetId());

                    for (String rttId : rttIds) {

                        final String refsetJsonString = propertyReader.getRttIdToRefsetJsonMap().get(rttId);

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

                        // If has ECL clauses, create and associate with refset (but
                        // don't persist)
                        if (propertyReader.getRttRefsetToClausesMap().containsKey(rttId)) {

                            for (String clauseJson : propertyReader.getRttRefsetToClausesMap().get(rttId)) {

                                final DefinitionClause clause = ModelUtility.fromJson(clauseJson, DefinitionClause.class);

                                clausesRefsetMap.put(clause, refset);
                            }

                        }

                    }

                } else {

                    /* Refsets in Snowstorm but not RTT */
                    // Defaults for type & narrative
                    refset.setType("EXTENSIONAL");
                    refset.setNarrative("None as refset lives on Snowstorm, but not in RTT");
                }

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

    /**
     * Populate editions.
     *
     * @param branchChildrenByEdition the branch children
     * @param internationalModules the international modules
     * @return the sets the
     * @throws Exception the exception
     */
    private Set<Refset> createRefsetsFromSnowstorm(Map<String, SortedMap<Date, String>> branchChildrenByEdition, Set<String> internationalModules) throws Exception {

        logger.debug("Num internationalModules: " + internationalModules.size());
        logger.debug("Num branches: " + branchChildrenByEdition.size());

        try (final TerminologyService service = new TerminologyService()) {

            service.setModifiedBy("Migration");
            service.setModifiedFlag(true);

            List<String> ignoredRefsets = propertyReader.readRefsetsToIgnore();

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

                                    Refset refset = new Refset();

                                    if (testing && refsetId.equals(testingRefset)) {

                                        logger.debug(testingRefset + " - xxx - here with childBranch" + childBranch);
                                    }

                                    refset.setRefsetId(refsetId);
                                    refset.setModuleId(moduleId);
                                    refset.setVersionStatus("PUBLISHED");
                                    refset.setWorkflowStatus("PUBLISHED");
                                    refset.setActive(true);

                                    if (runShortMigration) {

                                        refset.setVersionDate(branchDate);

                                    } else {

                                        // if (refsetId.equals("723264001") || refsetId.equals("721144007")) {

                                        /*-
                                         * Check new version refset version date. If none returned (null), then:
                                         * a) no changes to refset itself and 
                                         * b) thus no need to create  new version.
                                         * c) Move onto nex refset
                                         */
                                        Date refsetVersionDate = null;

                                        if (!testing || refsetId.equals(testingRefset)) {

                                            refsetVersionDate = defineSnowstormRefsetVersionDate(childBranch, refsetId);
                                        }

                                        if (refsetVersionDate == null) {

                                            if (testing && refsetId.equals(testingRefset)) {

                                                logger.debug(testingRefset + " - qqq - not adding anything on this branch for " + childBranch);
                                            }

                                            // No changes to refset so don't create a new version
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

                                        refset.setVersionDate(refsetVersionDate);

                                        if (editionVersions.contains(refset.getVersionDate())) {

                                            logger.debug(" yyy - edition supports refset: " + refsetId + " === " + refset.getVersionDate());

                                        } else {

                                            logger.debug(" zzz - would fail so need to filter: " + refsetId + " === " + refset.getVersionDate());
                                            // logger.debug(" zzz2b - with edition ' " + edition.getName() + "' version dates: " + editionVersions.toString());

                                            // Don't add refset versions that don't have corresponding snowstorm -based edition versions
                                            continue;
                                        }

                                    }

                                    // add the edition to a map with the refset ID to retrieve it later
                                    refsetEditions.put(refsetId, edition);

                                    if (refsetNode.get("pt").has("term")) {

                                        refset.setName(refsetNode.get("pt").get("term").asText());
                                    } else {

                                        refset.setName(lookupRefsetName(refsetId, edition, childBranch));
                                    }

                                    /* Add refset for later persisting */
                                    snowstormRefsets.add(refset);
                                    counts.incrementRefsetVersionPairsCounts();

                                    if (!uniqueRefsetIds.contains(refsetId)) {

                                        /*
                                         * logger.debug("Identifying refset (" + refsetId + ") for first time in this version - " + branchDateFormatter
                                         * .format(refset.getVersionDate()));
                                         */
                                        uniqueRefsetIds.add(refsetId);
                                        counts.incrementUniqueRefsetsCounts();
                                    } else {

                                        // logger.debug("Again seeing: " +
                                        // refsetId);
                                    }

                                } catch (Exception e) {

                                    logger.error("Failed with message: " + e.getMessage() + " for refsetNode: " + refsetNode);
                                }

                            }

                            if (isInternationalEdition) {

                                logger.debug("Adding international refsetId " + refsetId + " refsets identified");

                                internationalRefsets.add(refsetId);
                            }

                        }

                    }

                }

            }

        }

        logger.info("Finished processing CodeSystems in Snowstorm with " + snowstormRefsets.size() + " refsets identified out of which " + internationalRefsets.size() + " are international Refsets");

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
     * Populate editions.
     * 
     * @param codeSystemsNode
     *
     * @return the sets the
     * @throws Exception the exception
     */
    private Set<String> createEditionsFromSnowstorm() throws Exception {

        Set<String> internationalModules = null;
        final String url = SnowstormConnection.BASE_URL + "codesystems";
        logger.debug("createEditionsFromSnowstorm url: " + url);

        List<String> ignoredCodeSystemNames = propertyReader.readCodeSystemsToIgnore();
        Map<String, Set<String>> undefinedDefaultLanguageRefsets = propertyReader.readUndefinedDefaultLanguageRefsets();

        try (final Response response = SnowstormConnection.getResponse(url)) {

            final String resultString = response.readEntity(String.class);
            // logger.debug("createEditionsFromSnowstorm resultString: " + resultString);

            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode root = mapper.readTree(resultString.toString());

            internationalModules = identifyInternationalModules(root);

            try (final TerminologyService service = new TerminologyService()) {

                service.setModifiedBy("Migration");
                service.setModifiedFlag(true);

                final Iterator<JsonNode> responseIterator = root.iterator();

                while (responseIterator.hasNext()) {

                    final Iterator<JsonNode> codeSystems = responseIterator.next().iterator();

                    while (codeSystems.hasNext()) {

                        JsonNode codeSystem = codeSystems.next();

                        // Check for invalid or ignored code systems
                        if (!codeSystem.has("name")) {

                            logger.info("Skipping odd code system without a name'" + codeSystem.asText());
                            continue;
                        } else if (ignoredCodeSystemNames.contains(codeSystem.get("name").asText().toLowerCase())) {

                            logger.info("Code System '" + codeSystem.get("name") + "' is defined as to-be-ignored");
                            continue;
                        }

                        // Testing
                        if (testing && !codeSystem.get("name").asText().contains(testingEdition) && !codeSystem.get("name").asText().contains("Inter")) {

                            continue;
                        }

                        // Process Edition
                        Edition edition = new Edition();

                        edition.setName(codeSystem.get("name").asText());
                        edition.setShortName(codeSystem.get("shortName").asText());
                        edition.setBranch(codeSystem.get("branchPath").asText());

                        // Identify Edition's Default Language Refsets
                        if (codeSystem.has("defaultLanguageReferenceSets")) {

                            final JsonNode defaultLanguageReferenceSets = codeSystem.get("defaultLanguageReferenceSets");
                            final Iterator<JsonNode> defaultLanguageReferencesSetIterator = defaultLanguageReferenceSets.iterator();

                            while (defaultLanguageReferencesSetIterator.hasNext()) {

                                edition.getDefaultLanguageRefsets().add(defaultLanguageReferencesSetIterator.next().asText());
                            }

                        } else if (undefinedDefaultLanguageRefsets.containsKey(edition.getName())) {

                            edition.getDefaultLanguageRefsets().addAll(undefinedDefaultLanguageRefsets.get(edition.getName()));
                            logger.debug("No defined Default Language Refsets for " + edition.getName() + ", so adding from txt file: " + undefinedDefaultLanguageRefsets.get(edition.getName()));
                        }

                        // Ensure that DEFAULT_LANG_REFSET is always listed even if not explicitely listed
                        edition.getDefaultLanguageRefsets().add(DEFAULT_LANGUAGE_REFSET);

                        // Identify Edition's defaultLanguageCode - Per Kai, transform first language in set as defaultLangCode
                        if (!codeSystem.has("languages")) {

                            throw new Exception("All Code Systems must have lanaguages set filled in. " + edition.toString() + " does not");
                        }

                        Iterator<String> languages = codeSystem.get("languages").fieldNames();
                        String defaultLanguage = languages.next();
                        edition.setDefaultLanguageCode(defaultLanguage);

                        // Identify Code System Owner
                        if (codeSystem.has("owner")) {

                            editionOwnerMap.put(edition.getShortName(), codeSystem.get("owner").asText());
                            editionOwnerMap.put(edition.getName(), codeSystem.get("owner").asText());
                        } else {

                            editionOwnerMap.put(edition.getShortName(), edition.getName());
                            editionOwnerMap.put(edition.getName(), edition.getName());
                        }

                        // Identify Top Level Module
                        identifyTopLevelModule(edition, codeSystem, internationalModules);

                        setMetadata(edition, defaultMeta);
                        service.add(edition);

                        // TODO: Add a description default value or update
                        // snowstorm with value per codesystem
                        final String orgDesc = "";

                        Organization org = addOrganziation(editionOwnerMap.get(edition.getName()), orgDesc, edition, defaultMeta);

                        if (org.getEdition().getShortName().equals("SNOMEDCT-WCI")) {

                            wciOrganization = org;
                        }

                    }

                }

            }

        } catch (Exception e) {

            e.printStackTrace();
        }

        return internationalModules;
    }

    private void identifyTopLevelModule(Edition edition, JsonNode codeSystem, Set<String> internationalModules) throws Exception {

        if ("international edition".equals(edition.getName().toLowerCase())) {

            edition.setTopLevelModule(MODULE_ANCESTOR_CONCEPT_SCTID);
        } else {

            Iterator<JsonNode> moduleIterator = codeSystem.get("modules").iterator();

            // Ignore CORE Modules
            Set<String> editionModules = new HashSet<>();

            while (moduleIterator.hasNext()) {

                JsonNode module = moduleIterator.next();

                if (!internationalModules.contains(module.get("conceptId").asText()) && !module.get("moduleId").asText().equals("900000000000012004")) {

                    editionModules.add(module.get("conceptId").asText());
                }

            }

            if (editionModules.size() == 0) {

                // If no non-CORE modules found, use the default Module
                edition.setTopLevelModule(MODULE_ANCESTOR_CONCEPT_SCTID);
                logger.debug("No dedicated modules identified for " + edition.getName() + ": " + editionModules.toString() + ", so adding default: " + MODULE_ANCESTOR_CONCEPT_SCTID);
            } else if (editionModules.size() == 1) {

                // If only one non-CORE modules found, use it
                edition.setTopLevelModule(editionModules.iterator().next());
            } else {

                logger.debug("Have multiple modules identified for " + edition.getName() + ": " + editionModules.toString());

                // If multiple non-CORE modules found, TODO: Fill in
                Set<String> childrenModules = new HashSet<>();

                Set<String> children = getModuleChildren(edition);

                for (String moduleId : editionModules) {

                    if (children.contains(moduleId)) {

                        childrenModules.add(moduleId);
                    }

                }

                // TODO: Remove Hard coded solution for Netherlands and
                // Australia -> These are from previous test data and are deprecated
                if (edition.getShortName().equals("SNOMEDCT-NL")) {

                    childrenModules.remove("15561000146104"); // 15561000146104
                                                              // - Represents
                                                              // Patient
                                                              // Friendly Terms
                } else if (edition.getShortName().equals("SNOMEDCT-AU")) {

                    childrenModules.add("32570231000036109");
                }

                // TODO: Handle hard coded solution for Norway & US
                if (edition.getShortName().equals("SNOMEDCT-NO")) {

                    childrenModules.remove("57091000202101");
                    childrenModules.remove("57101000202106");
                } else if (edition.getShortName().equals("SNOMEDCT-US")) {

                    childrenModules.remove("5991000124107");
                }

                if (childrenModules.size() == 0 || childrenModules.size() > 1) {

                    logger.info("Seeing odd number of modules during secondary analysis for " + edition.getName() + ": " + childrenModules.toString());
                } else {

                    edition.setTopLevelModule(childrenModules.iterator().next());
                }

            }

        }

    }

    private Set<String> getModuleChildren(Edition edition) throws Exception {

        String url = SnowstormConnection.BASE_URL + "browser/" + edition.getBranch() + "/concepts/" + MODULE_ANCESTOR_CONCEPT_SCTID + "/children";
        Set<String> childrenSctIds = new HashSet<>();

        try (final Response response = SnowstormConnection.getResponse(url)) {

            final String resultString = response.readEntity(String.class);
            final ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(resultString.toString());

            Iterator<JsonNode> conceptIterator = root.iterator();

            while (conceptIterator.hasNext()) {

                JsonNode node = conceptIterator.next();
                childrenSctIds.add(node.get("conceptId").asText());
            }

        } catch (Exception e) {

            throw new Exception("Failed in getting code systems (first call to Snowstorm) with: " + e.getMessage(), e);
        }

        return childrenSctIds;
    }

    private Set<String> identifyInternationalModules(JsonNode root) throws Exception {

        Set<String> retSet = new HashSet<>();

        final Iterator<JsonNode> responseIterator = root.iterator();

        while (responseIterator.hasNext()) {

            final Iterator<JsonNode> codeSystems = responseIterator.next().iterator();

            while (codeSystems.hasNext()) {

                JsonNode codeSystem = codeSystems.next();

                if (!codeSystem.has("name")) {

                    continue;
                }

                if ("international edition".equals(codeSystem.get("name").asText().toLowerCase())) {

                    // At international Edition
                    Iterator<JsonNode> moduleIterator = codeSystem.get("modules").iterator();

                    while (moduleIterator.hasNext()) {

                        JsonNode module = moduleIterator.next();
                        retSet.add(module.get("conceptId").asText());
                    }

                    return retSet;
                }

            }

        }

        throw new Exception("Didn't find the international modules as anticipated");
    }

    /**
     * Import refset json.
     *
     * @param allRefsets the all refsets
     * @throws Exception the exception
     */
    private void persistObjects() throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            service.setModifiedBy("Migration");
            service.setModifiedFlag(true);

            // Persist Projects and Organizations from Snowstorm
            Map<String, Project> defaultEditionProjects = new HashMap<>();
            int projectCount = 0;
            final Set<String> refsetsAdded = new HashSet<>();
            final Map<String, Project> projectsAdded = new HashMap<>();
            int count = 0;
            int ignoreCounter = 0;

            Map<String, Set<Refset>> refsetVersionsPreProcessed = new HashMap<>();
            Map<String, Set<Date>> refsetVersionsProcessed = new HashMap<>();

            // Preprocess Snowstorm refsets for analysis purposes
            // Adding refsets identified on snowstorm
            logger.debug(" step - Start persisting gathered Snowstorm & RTT Supporting Objects");

            for (Refset snowRefset : snowstormRefsets) {

                if (testing && testingRefset != null && !testingRefset.equals(snowRefset.getRefsetId())) {

                    continue;
                }

                if (!refsetVersionsPreProcessed.containsKey(snowRefset.getRefsetId())) {

                    refsetVersionsPreProcessed.put(snowRefset.getRefsetId(), new HashSet<Refset>());
                }

                refsetVersionsPreProcessed.get(snowRefset.getRefsetId()).add(snowRefset);

            }

            // Adding refsets identified on snowstorm
            for (String refsetSctId : refsetVersionsPreProcessed.keySet()) {

                if (propertyReader.getRefsetToClausesInfoMap().containsKey(refsetSctId)) {

                    logger.debug("LLL - Have clause on refset: " + refsetSctId);
                }

                String narrative = null;
                Set<String> tags = null;

                if (propertyReader.getRefsetToDescriptionMap().containsKey(refsetSctId)) {

                    narrative = propertyReader.getRefsetToDescriptionMap().get(refsetSctId);
                }

                if (propertyReader.getRefsetToTagsMap().containsKey(refsetSctId)) {

                    tags = propertyReader.getRefsetToTagsMap().get(refsetSctId);
                }

                for (Refset snowRefset : refsetVersionsPreProcessed.get(refsetSctId)) {

                    String rttId = null;
                    final Edition edition = refsetEditions.get(snowRefset.getRefsetId());

                    if (refsetsToIgnore.contains(snowRefset.getRefsetId())) {

                        ignoreCounter++;
                        continue;

                    } else {

                        // Add Refset. Keep track of which are added this way as to not add them from RTT as well
                        snowRefset.setNarrative(narrative);
                        snowRefset.setTags(tags);
                        projectCount = processSnowstormRefset(snowRefset, edition, refsetsAdded, projectsAdded, defaultEditionProjects, propertyReader.getRefsetToProjectsInfoMap(), projectCount);

                        service.add(snowRefset);

                        /* Don't need member count anymore */
                        // identifyMemberCount(snowRefset);

                        processClauses(rttId, snowRefset);

                        if (!refsetVersionsProcessed.containsKey(snowRefset.getRefsetId())) {

                            refsetVersionsProcessed.put(snowRefset.getRefsetId(), new HashSet<Date>());
                        }

                        refsetVersionsProcessed.get(snowRefset.getRefsetId()).add(snowRefset.getVersionDate());

                    }

                    if (++count % 250 == 0) {

                        logger.info("Imported + " + count + " refsets thus far");
                    }

                }

            }

            if (supportRtt) {

                // Adding refsets from RTT
                for (String refsetId : rttRefsetIds) {

                    if (testing && testingRefset != null && !testingRefset.equals(refsetId)) {

                        continue;
                    }

                    final Edition edition = refsetEditions.get(refsetId);

                    final Set<String> rttIds = propertyReader.getRttRefsetSctIdToRttIdMap().get(refsetId);

                    for (String rttId : rttIds) {

                        final String refsetJsonString = propertyReader.getRttIdToRefsetJsonMap().get(rttId);
                        final String projectId = propertyReader.getRttIdToProjectsJsonMap().get(rttId);

                        final Refset rttRefset = ModelUtility.fromJson(refsetJsonString, Refset.class);
                        final Date versionDate = Metadata.getSdf().parse(propertyReader.getRttRefsetToEffectiveDateMap().get(rttId));

                        if (refsetVersionsProcessed.containsKey(rttRefset.getRefsetId()) && refsetVersionsProcessed.get(rttRefset.getRefsetId()).contains(versionDate)) {

                            continue;
                        }

                        // only process those refsets that aren't in Snowstorm

                        // TODO Temp fix so there are no refsets or orgs without editions
                        if (edition == null || edition.getId() == null || edition.getId().equals("")) {

                            logger.debug("Skipping refset with no Edition: " + rttRefset.getRefsetId());
                            continue;
                        }

                        processRefsetInRTT(rttId, projectId, rttRefset, edition, refsetsAdded, projectsAdded, defaultEditionProjects, projectCount);
                    }

                }

            }

            logger.info(" step complete - Finish persisting gathered Snowstorm & RTT Supporting Objects");

            populateInitialDate(service);

            logger.info("Have imported from Snowstorm " + projectCount + " projects and " + counts.getOrgsImportedCount() + " organizations");

            logger.info("Have NOT imported anything from RTT that isn't in Snowstorm");

            // Unique Counts
            logger.info("\n*** Unique Refsets Count ***");
            logger.info("Have identified " + counts.getUniqueRefsetsCounts() + " unique refsets on Snowstorm");
            logger.info("Have imported " + counts.getUniqueRttMetadataCreatedCount() + " unique refsets with metadata pulled from RTT");
            logger.info("Have imported " + counts.getUniqueNoMetadataCreatedCount() + " unique refsets with no metadata at all");

            // Refset/Verfsion Pair Counts
            logger.info("\n*** Refsets/Version Pair Count ***");
            logger.info("Have identified " + counts.getRefsetVersionPairsCounts() + " refset/version pairs on Snowstorm");
            logger.info("Have imported " + counts.getRttMetadataCreatedCount() + " refset/version pairs with metadata pulled from RTT");
            logger.info("Have imported " + counts.getNoMetadataCreatedCount() + " refset/version pairs with no metadata at all");

            logger.info("Total of " + ignoreCounter + " refsets ignored");
        } catch (Exception e) {

            logger.error("Have issue with: " + e.getMessage());
            e.printStackTrace();
        }

    }

    private void populateInitialDate(TerminologyService service) throws Exception {

        logger.info(" step - Populating initial data");

        // Create a dedicated UAT Training Project for each organization
        logger.info(" Create a dedicated UAT Training Project for each organization");
        Set<Project> uatProjects = new HashSet<>();

        for (String orgName : organizationsAdded.keySet()) {

            Organization org = organizationsAdded.get(orgName);

            if (wciOrganization != null && wciOrganization.equals(org)) {

                continue;
            }

            Project uatProject = addProject(org, org.getName() + " dedicated UAT Training Project",
                "Project is dedicated to UAT Training. Any work done here will not be available for production usages. All training users will have the author role and reviewer role in this project",
                defaultMeta);

            uatProjects.add(uatProject);
        }

        // Create wci-project (for DEV only)
        logger.info(" Create wci-project (for DEV only)");

        if (wciOrganization != null) {

            logger.info("Adding WCI Testing Org's single project");
            Project wciProject = addProject(wciOrganization, "WCI Testing Project", "The single project for all WCI testing refsets", defaultMeta);

            Refset wciTestingRefset = new Refset();

            // TODO: Change this to have actual release date created/new Refset
            wciTestingRefset.setVersionDate(new Date());
            wciTestingRefset.setRefsetId(WCI_TESTING_REFSET_CONCEPT_ID);
            wciTestingRefset.setModuleId(MODULE_ANCESTOR_CONCEPT_SCTID);
            wciTestingRefset.setVersionStatus("PUBLISHED");
            wciTestingRefset.setWorkflowStatus("PUBLISHED");
            wciTestingRefset.setActive(true);
            wciTestingRefset.setType("EXTENSIONAL");
            wciTestingRefset.setProject(wciProject);
            wciTestingRefset.setName("Base WCI Refset");

            service.add(wciTestingRefset);

            logger.info(" Create wci-developer teams for each extensions's UAT Training project (for DEV only)");

            // Create wci-developer teams for each extensions's UAT Training project (for DEV only)
            for (Project uatProject : uatProjects) {

                Organization org = uatProject.getOrganization();

                logger.debug("Trying with: " + org.getName());
                logger.debug("then with: " + org.getEdition().getName());

                Map<String, Set<String>> organizationTeamInfo = null;
                organizationTeamInfo = propertyReader.getTeamCreation().get(org.getName());

                if (organizationTeamInfo == null) {

                    organizationTeamInfo = propertyReader.getTeamCreation().get(org.getName());

                    if (organizationTeamInfo == null) {

                        logger.debug("error 444 - with " + propertyReader.getTeamCreation().get(org.getName()) + " -- and -- " + propertyReader.getTeamCreation().get(org.getEdition().getName()));
                        continue;
                    }

                }

                Set<String> projectTeams = new HashSet<>();

                for (String teamToCreate : organizationTeamInfo.keySet()) {

                    Team team = new Team(teamToCreate);
                    team.setDescription("Providing support for all UAT Extension Training Projects");
                    team.setOrganization(org);
                    team.setPrimaryContactEmail("support-rt2@westcoastinformatics.com");
                    team.setRoles(organizationTeamInfo.get(teamToCreate));
                    team.setMembers(propertyReader.getTeamMembership().get(teamToCreate));

                    service.add(team);
                    projectTeams.add(team.getName());
                }

                uatProject.setTeams(projectTeams);
            }

        }

        logger.info(" step complete - Adding special content");
    }

    private void processClauses(final String rttId, final Refset refset) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            service.setModifiedBy("Migration");
            service.setModifiedFlag(true);

            // If has ECL clauses, add them to db & refset
            if (propertyReader.getRttRefsetToClausesMap().containsKey(rttId)) {

                Set<DefinitionClause> clauses = addClause(rttId);
                refset.getDefinitionClauses().addAll(clauses);
                service.update(refset);
            }

        }

    }

    private void processRefsetInRTT(String rttId, String rttProjectId, Refset rttRefset, Edition edition, Set<String> refsetsAdded, Map<String, Project> projectsAdded,
        Map<String, Project> defaultEditionProjects, int projectCount) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            service.setModifiedBy("Migration");
            service.setModifiedFlag(true);

            final String projectId = propertyReader.getRttIdToProjectsJsonMap().get(rttId);

            final Project rttProject = ModelUtility.fromJson(propertyReader.getRttIdToProjectsJsonMap().get(projectId), Project.class);

            final Organization rttOrg = rttProject.getOrganization();

            final Metadata projectMeta = new Metadata(rttProject.getModified(), rttProject.getModifiedBy());

            final String translatedOrgName = translateRttOrg(rttOrg.getName());

            if (translatedOrgName == null) {

                // Only supporting those a) whose org name is defined in RTT, b) Is not a training project in RTT, and c) has a corresponding Snowstorm Code System (and
                // ignoring those not)
                return;
            }

            Organization org = null;

            if (!organizationsAdded.containsKey(translatedOrgName)) {

                org = addOrganziation(translatedOrgName, null, edition, defaultMeta);
                organizationsAdded.put(translatedOrgName, org);
            } else {

                org = organizationsAdded.get(translatedOrgName);
            }

            if (!projectsAdded.containsKey(rttProject.getName())) {

                final Project project = addProject(org, rttProject.getName(), rttProject.getDescription(), projectMeta);
                projectCount++;

                projectsAdded.put(rttProject.getName(), project);
            }

            if (!refsetsAdded.contains(rttRefset.getRefsetId())) {

                refsetsAdded.add(rttRefset.getRefsetId());
                counts.incrementUniqueRttMetadataCount();

            }

            counts.incrementRttMetadataCount();
            rttRefset.setProject(projectsAdded.get(rttProject.getName()));
            setMetadata(rttRefset, propertyReader.getMetadataMap().get("refset-" + rttId));

            rttRefset.setVersionStatus("PUBLISHED");
            rttRefset.setWorkflowStatus("PUBLISHED");
            rttRefset.setVersionDate(Metadata.getSdf().parse(propertyReader.getRttRefsetToEffectiveDateMap().get(rttId)));

            service.add(rttRefset);
            processClauses(rttId, rttRefset);
        }

    }

    private void identifyMemberCount(Refset rttRefset) {

        ConceptResultList members;

        try {

            members = RefsetMemberService.getRefsetMembers(SecurityService.getUserFromSession(), rttRefset.getId(), new SearchParameters(), "list", null);
        } catch (Exception e) {

            logger.error("Failed calling RefsetMemberService.getRefsetMembers()");
            e.printStackTrace();
        }

    }

    private int processSnowstormRefset(Refset refset, Edition edition, Set<String> refsetsAdded, Map<String, Project> projectsAdded, Map<String, Project> defaultEditionProjects,
        Map<String, String> refsetToProjectsInfoMap, int projectCount) throws Exception {

        final String editionName = edition.getName();
        final String editionShortName = edition.getShortName();

        // Identify Org Name
        if (!editionOwnerMap.containsKey(editionName) && !editionOwnerMap.containsKey(editionShortName) || !organizationsAdded.containsKey(editionOwnerMap.get(editionName))) {

            throw new Exception("Orgnaization based on edition '" + edition + "' should have been created already");
        }

        Project project = defineRefsetProject(refset, edition, projectsAdded, defaultEditionProjects, refsetToProjectsInfoMap, editionName, editionName);

        if (!refsetsAdded.contains(refset.getRefsetId())) {

            refsetsAdded.add(refset.getRefsetId());
            counts.incrementUniqueNoMetadataCount();

        }

        counts.incrementNoMetadataCount();
        refset.setProject(project);
        setMetadata(refset, defaultMeta);

        if (!projectsAdded.containsKey(project.getName())) {

            projectsAdded.put(project.getName(), project);
            projectCount++;
        }

        return projectCount;
    }

    private Project defineRefsetProject(Refset refset, Edition edition, Map<String, Project> projectsAdded, Map<String, Project> defaultEditionProjects, Map<String, String> refsetToProjectsInfoMap,
        String editionName, String ShortName) throws Exception {

        String orgName = editionOwnerMap.get(editionName) != null ? editionOwnerMap.get(editionName) : editionOwnerMap.get(ShortName);
        final Organization org = organizationsAdded.get(orgName);

        // Was part of project on RTT, so pull in project information
        if (refsetToProjectsInfoMap.containsKey(refset.getRefsetId())) {

            String projectInfo = refsetToProjectsInfoMap.get(refset.getRefsetId());

            String[] projectDetails = projectInfo.split(",");

            for (int i = 0; i < 2; i++) {

                if (projectDetails[i].startsWith("\"")) {

                    projectDetails[i] = projectDetails[i].substring(1);
                }

                if (projectDetails[i].endsWith("\"")) {

                    projectDetails[i] = projectDetails[i].substring(0, projectDetails[i].length() - 1);
                }

            }

            if (projectsAdded.containsKey(projectDetails[0])) {

                // Already added project, so just return
                return projectsAdded.get(projectDetails[0]);
            }

            final Project project = addProject(org, projectDetails[0].replaceFirst("\"", ""), projectDetails[1], defaultMeta);

            return project;
        }

        // No project associated with refset, so use default Edition Project

        // Create edition
        if (!defaultEditionProjects.containsKey(edition.getId())) {

            // Create default project
            final String projectName = "Default project for " + orgName;
            final String projectDescription = "This is a default project to support initial Snowstorm-based refsets for " + orgName + ".";

            final Project project = addProject(org, projectName, projectDescription, defaultMeta);

            defaultEditionProjects.put(edition.getId(), project);
        }

        return defaultEditionProjects.get(edition.getId());
    }

    private String translateRttOrg(String name) {

        if (name == null || name.isEmpty()) {

        }

        if (!debugRttOrgTranslations.contains(name)) {

            debugRttOrgTranslations.add(name);
        }

        String shortName = null;

        if (name.equals("Swedish NRC")) {

            shortName = "SNOMEDCT-SE";
        } else if (name.equals("New Zealand Ministry of Health")) {

            shortName = "SNOMEDCT-NZ";
        } else if (name.equals("BE NRC")) {

            shortName = "SNOMEDCT-BE";
        } else if (name.equals("IHTSDO")) {

            shortName = "SNOMEDCT";
        } else if (name.equals("TEHIK")) {

            shortName = "SNOMEDCT-EE";
        } else if (name.equals("NLM")) {

            shortName = "SNOMEDCT-US";
        } else if (name.equals("Norway") || name.equals("Direktoratet for e-helse") || name.equals("Helsedirektoratet")) {

            shortName = "SNOMEDCT-NO";
        } else if (name.equals("HSE")) {

            shortName = "SNOMEDCT-IE";
        }

        if (shortName != null) {

            return editionOwnerMap.get(shortName);

        } else if (name.toLowerCase().contains("india") || name.toLowerCase().contains("canad") || name.toLowerCase().contains("conteir")) {

            return null;
        } else {

            return name;
        }

    }

    private Project addProject(Organization org, String projectName, String projectDescription, Metadata meta) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            service.setModifiedBy("Migration");
            service.setModifiedFlag(true);

            final Project project = new Project();
            project.setName(projectName);
            project.setDescription(projectDescription);
            project.setOrganization(org);
            project.setPrivateProject(false);
            project.setCrowdProjectId(CrowdGroupNameAlgorithm.getProjectString(projectName));

            // Persist
            setMetadata(project, meta);
            return service.add(project);
        }

    }

    private Organization addOrganziation(final String orgName, String orgDesc, final Edition edition, final Metadata meta) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            service.setModifiedBy("Migration");
            service.setModifiedFlag(true);

            Organization org = new Organization();
            org.setName(orgName);
            org.setDescription(orgDesc);
            org.setEdition(edition);

            setMetadata(org, meta);

            org = service.add(org);
            organizationsAdded.put(orgName, org);

            counts.incrementOrgsImportedCount();

            return org;
        }

    }

    private Set<DefinitionClause> addClause(String rttId) throws Exception {

        Set<DefinitionClause> refsetClauses = new HashSet<>();

        try (final TerminologyService service = new TerminologyService()) {

            service.setModifiedBy("Migration");
            service.setModifiedFlag(true);

            for (String clauseJson : propertyReader.getRttRefsetToClausesMap().get(rttId)) {

                final DefinitionClause clause = ModelUtility.fromJson(clauseJson, DefinitionClause.class);

                setMetadata(clause, propertyReader.getMetadataMap().get("refset-" + rttId));
                DefinitionClause persistedClause = service.add(clause);
                refsetClauses.add(persistedClause);
            }

            return refsetClauses;
        }

    }

    void setMetadata(final HasModified object, final Metadata metadata) {

        object.setModified(metadata.getModified());
        object.setCreated(metadata.getModified());
        object.setModifiedBy(metadata.getModifiedBy());
    }

}
