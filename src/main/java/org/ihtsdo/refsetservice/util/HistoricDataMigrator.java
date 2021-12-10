package org.ihtsdo.refsetservice.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
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
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.terminologyservice.SnowstormConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class HistoricDataMigrator {

    private static final String SIMPLE_TYPE_REFSET_SCTID = "446609009";

    private static final String MODULE_ANCESTOR_CONCEPT_SCTID = "900000000000443000";

    private static final Object UK_LANGUAGE_REFSET_ID = "900000000000508004";

    /** The formatter. */
    private final SimpleDateFormat branchDateFormatter = new SimpleDateFormat("yyyy-MM-dd");

    /** The sdf. */
    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private final Metadata defaultMeta =
            new Metadata(sdf.format(new Date()), "System initialization");

    /**
     * The Class Metadata.
     */
    public class Metadata {

        /** The modified. */
        private Date modified;

        /** The modified by. */
        private String modifiedBy;

        /**
         * Instantiates a new metadata.
         *
         * @param modified the modified
         * @param modifiedBy the modified by
         */
        public Metadata(final String modified, final String modifiedBy) {
            String updatedModified = modified;
            try {
                if (modified == null || modified.isEmpty() || modified.equals("NULL")) {
                    updatedModified = new Date().toString();
                }

                this.modified = sdf.parse(updatedModified.replaceAll("\"", ""));
                this.modifiedBy = modifiedBy;
            } catch (Exception e) {
                logger.error("Failed with mod/modBy: " + updatedModified.replaceAll("\"", "")
                        + " / " + modifiedBy);
                e.printStackTrace();
            }
        }

        /**
         * Instantiates a new metadata.
         *
         * @param modified the modified
         * @param modifiedBy the modified by
         */
        public Metadata(final Date modified, final String modifiedBy) {
            try {
                this.modified = modified;
                this.modifiedBy = modifiedBy;
            } catch (Exception e) {
                logger.error("Failed with mod/modBy: " + modified + " / " + modifiedBy);
                e.printStackTrace();
            }
        }

        /**
         * Gets the modified.
         *
         * @return the modified
         */
        public Date getModified() {
            return modified;
        }

        /**
         * Gets the modified by.
         *
         * @return the modified by
         */
        public String getModifiedBy() {
            return modifiedBy;
        }
    }

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

    /** The Constant DEFAULT_LANGUAGE_SET_ID. */
    private final String DEFAULT_LANGUAGE_REFSET_ID = "900000000000509007";

    /** The Constant CFR_LANGUAGE_REFSET_ID. */
    private final String CFR_LANGUAGE_REFSET_ID = "21000241105";

    /** The Constant NL_LANGUAGE_REFSET_ID. */
    private final String NL_LANGUAGE_REFSET_ID = "15551000146102";

    /** The Constant SPLIT_CHARACTER. */
    private final String SPLIT_CHARACTER = "\t";

    /** The logger. */
    private final Logger logger = LoggerFactory.getLogger(HistoricDataMigrator.class);

    ClassPathResource projectsResource =
            new ClassPathResource("service/rtt-migration/projects.txt");

    ClassPathResource clausesResource = new ClassPathResource("service/rtt-migration/clauses.txt");

    ClassPathResource refsetsResource = new ClassPathResource("service/rtt-migration/refsets.txt");

    /** The metadata map. */
    private final Map<String, Metadata> metadataMap = new HashMap<>();

    /** The refset internal id map. */
    private final Map<String, String> rttIdToRefsetJsonMap = new HashMap<>();

    /** The refset sct id to internal id map. */
    private final Map<String, String> rttRefsetSctIdToRttIdMap = new HashMap<>();

    /** The rtt refset to clauses map. */
    private final Map<String, ArrayList<String>> rttRefsetToClausesMap = new HashMap<>();

    /** The projects ID-to_Jsonmap. */
    private final Map<String, String> rttIdToProjectsJsonMap = new HashMap<>();

    /** The refset to project map. */
    private final Map<String, String> rttIdToRttProjectIdMap = new HashMap<>();

    /** The refsets to ignore. */
    private final Set<String> refsetsToIgnore = new HashSet<>();

    /** The international refsets. */
    private final Set<String> internationalRefsets = new HashSet<>();

    private final Map<String, String> jsonProjectOrganziationMap = new HashMap<>();

    /** The testing. */
    private boolean testing = false;

    private final Map<String, String> editionOwnerMap = new HashMap<>();

    private Set<String> rttRefsetIds = null;

    private Map<String, Edition> refsetEditions = new HashMap<>();

    private final Set<Refset> snowstormRefsets = new HashSet<>();

    private final Set<String> uniqueRefsetIds = new HashSet<>();

    private final Map<String, Organization> organizationsAdded = new HashMap<>();

    private final Counts counts = new Counts();

    private final Set<String> debugRttOrgTranslations = new HashSet<>();

    public void migrate() throws Exception {
        Set<String> internationalModules = createEditionsFromSnowstorm();
        logger.debug("Num internationalModules: " + internationalModules.size());

        Map<String, SortedMap<Date, String>> branches = identifyBranches();
        logger.debug("Num branches: " + branches.size());

        createRefsetsFromSnowstorm(branches, internationalModules);

        // Read refset metadata and associated information (projects & ECLs)
        parseRttData();

        // Skip those refsets that live on SnowS, but are not yet in RTT DB dmp
        // file that we are using
        for (Refset refset : snowstormRefsets) {
            if (!rttRefsetSctIdToRttIdMap.keySet().contains(refset.getRefsetId())) {
                if (internationalRefsets.contains(refset.getRefsetId())) {
                    refsetsToIgnore.add(refset.getRefsetId());

                    logger.debug(
                            "Going to ignore Int'l refsets supported in " + refset.getEditionName()
                                    + " - " + refset.getRefsetId() + " - " + refset.getName());
                }
            }
        }

        // With metadata from RTT project (defined in parseRTTMetadata())
        updateRefsets();
        persistObjects();
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
                if (testing && !edition.getName().contains("Danish")) {
                    continue;
                }
                SortedMap<Date, String> children = new TreeMap<>();

                try (final Response response = SnowstormConnection
                        .getResponse(genericUrl.replace("{branch}", edition.getBranch()))) {
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

                        // Since grabbing all children branches, avoid
                        // attempting to parse extensions i.e. MAIN/SNOMEDCT-US
                        if (childDate.matches("^[0-9].*$")) {
                            Date branchDate = branchDateFormatter.parse(childDate);
                            children.put(branchDate, childBranch);
                        }
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

            // Update refset from JSON. If JSON not available to the refset, it
            // means it resides exclusively on Snowstorm. In such a case,
            // provide special default handling.
            if (rttRefsetIds.contains(refset.getRefsetId())) {
                final String rttId = rttRefsetSctIdToRttIdMap.get(refset.getRefsetId());
                final String refsetJsonString = rttIdToRefsetJsonMap.get(rttId);

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
            } else {
                /* Refsets in Snowstorm but not RTT */

                // Defaults for type & narrative
                refset.setType("EXTENSIONAL");
                refset.setNarrative("None as not from RTT");
            }

            // Keep track of the latest version per refsetId
            if (!latestRefsetCache.containsKey(refset.getRefsetId()) || latestRefsetCache
                    .get(refset.getRefsetId()).before(refset.getVersionDate())) {
                latestRefsetCache.put(refset.getRefsetId(), refset.getVersionDate());
            }
        }

        // Have latest version per refset. Set the latestVersion flag to true
        // for them
        for (Refset refset : snowstormRefsets) {
            if (latestRefsetCache.containsKey(refset.getRefsetId())) {
                for (String refsetId : latestRefsetCache.keySet()) {
                    if (refset.getRefsetId().equals(refsetId)
                            && refset.getVersionDate().equals(latestRefsetCache.get(refsetId))) {
                        refset.setLatestVersion(true);
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
    private Set<Refset> createRefsetsFromSnowstorm(
        Map<String, SortedMap<Date, String>> branchChildrenByEdition,
        Set<String> internationalModules) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {
            service.setModifiedBy("Migration");
            service.setModifiedFlag(true);

            logger.info("---> Starting to identify Refsets on Snowstorm by edition/version pair");

            for (String editionId : branchChildrenByEdition.keySet()) {
                final Edition edition = service.get(editionId, Edition.class);

                String url = SnowstormConnection.BASE_URL
                        + "browser/{branch}/members?active=true&referenceSet=%3C"
                        + SIMPLE_TYPE_REFSET_SCTID + "&module=%3C%3C" + edition.getTopLevelModule();

                if (editionId.equals(branchChildrenByEdition.keySet().iterator().next())) {
                    logger.debug("   URL to identify refsets and the way updated per branch: " + url
                            + " with following code: <<url.replace(\"{branch}\", childBranch)>>\n");
                }

                logger.info("\t*** Processing Edition: " + edition.getName());

                boolean isInternationalEdition =
                        ("international edition".equals(edition.getName().toLowerCase())) ? true
                                : false;

                for (Date branchDate : branchChildrenByEdition.get(editionId).keySet()) {
                    final String childBranch =
                            branchChildrenByEdition.get(editionId).get(branchDate);
                    if (testing && isInternationalEdition
                            && (childBranch.contains("200") || (!childBranch.endsWith("0")
                                    && !childBranch.endsWith("1") && !childBranch.endsWith("9")))) {
                        continue;
                    }

                    logger.debug("Identifying Snowstorm refsets in version " + childBranch + " of "
                            + edition.getName());

                    try (final Response response =
                            SnowstormConnection.getResponse(url.replace("{branch}", childBranch))) {
                        if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
                            if (edition.getBranch().startsWith("MAIN")) {
                                throw new Exception("Unable to process this edition: " + edition);
                            } else {
                                logger.debug("Found that '" + edition.getName()
                                        + "' has odd branch: " + edition.getBranch());
                                continue;
                            }
                        }
                        final String resultString = response.readEntity(String.class);
                        final ObjectMapper mapper = new ObjectMapper();
                        final JsonNode root = mapper.readTree(resultString.toString());

                        // get RefSets from edition as long as a) active & b)
                        // within edition's moduleˇ
                        final Iterator<JsonNode> refsetIterator =
                                root.get("referenceSets").iterator();

                        while (refsetIterator.hasNext()) {
                            final JsonNode refsetNode = refsetIterator.next();
                            if (!refsetNode.has("moduleId") || !refsetNode.has("conceptId")
                                    || !refsetNode.has("active")) {
                                throw new Exception("Getting unexpected Refset info from node: "
                                        + refsetNode.toString());
                            }
                            final String moduleId = refsetNode.get("moduleId").asText();
                            final String refsetId = refsetNode.get("conceptId").asText();

                            /*-
                             *  Only process refset are either
                             *  a) Listed in international edition or 
                             *  b) In a non-international module
                            
                             */
                            if (isInternationalEdition
                                    || !internationalModules.contains(moduleId)) {
                                try {
                                    Refset refset = new Refset();

                                    refset.setRefsetId(refsetId);
                                    refset.setModuleId(moduleId);
                                    refset.setVersionDate(branchDate);
                                    refset.setVersionStatus("PUBLISHED");
                                    refset.setWorkflowStatus("PUBLISHED");
                                    refset.setActive(true);

                                    // add the edition to a map with the refset
                                    // ID to retrieve it later
                                    refsetEditions.put(refsetId, edition);

                                    if (refsetNode.get("pt").has("term")) {
                                        refset.setName(refsetNode.get("pt").get("term").asText());
                                    } else {
                                        refset.setName(
                                                lookupRefsetName(refsetId, edition, childBranch));
                                    }

                                    snowstormRefsets.add(refset);
                                    counts.incrementRefsetVersionPairsCounts();

                                    if (!uniqueRefsetIds.contains(refsetId)) {
                                        logger.debug("Identifying refset (" + refsetId
                                                + ") for first time in this version - "
                                                + branchDateFormatter
                                                        .format(refset.getVersionDate()));

                                        uniqueRefsetIds.add(refsetId);
                                        counts.incrementUniqueRefsetsCounts();
                                    } else {
                                        logger.debug("Again seeing: " + refsetId);
                                    }
                                } catch (Exception e) {
                                    logger.error("Failed with message: " + e.getMessage()
                                            + " for refsetNode: " + refsetNode);
                                }
                            }
                            if (isInternationalEdition) {
                                internationalRefsets.add(refsetId);
                            }
                        }

                    }
                }
            }
        }

        return snowstormRefsets;
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
    private String lookupRefsetName(String refsetId, Edition edition, String childBranch)
        throws Exception {
        String url =
                SnowstormConnection.BASE_URL + "browser/" + childBranch + "/concepts/" + refsetId;

        try (final Response response = SnowstormConnection.getResponse(url)) {
            final String resultString = response.readEntity(String.class);
            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode conceptNode = mapper.readTree(resultString.toString());

            Iterator<JsonNode> descriptionIterator = conceptNode.get("descriptions").iterator();
            while (descriptionIterator.hasNext()) {
                JsonNode descriptionNode = descriptionIterator.next();
                String acceptability = null;

                if (descriptionNode.get("type").asText().equals("SYNONYM") && descriptionNode
                        .get("lang").asText().equals(edition.getDefaultLanguageCode())) {
                    final JsonNode acceptabilityMap = descriptionNode.get("acceptabilityMap");
                    for (String langRefsetId : edition.getDefaultLanguageRefsets()) {
                        if (acceptabilityMap.has(langRefsetId)) {
                            acceptability = acceptabilityMap.get(langRefsetId).asText();
                            break;
                        }
                    }

                    if (acceptability == null) {
                        throw new Exception(
                                "Not able to properly identify refset name for description: "
                                        + descriptionNode);
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

        try (final Response response = SnowstormConnection.getResponse(url)) {

            final String resultString = response.readEntity(String.class);
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

                        if (!codeSystem.has("name")) {
                            continue;
                        }

                        if (codeSystem.get("name").asText().equalsIgnoreCase("kk")) {
                            logger.info("Skipping odd code system 'Kk' as was likely for testing");
                            continue;
                        }

                        if (testing && !codeSystem.get("name").asText().contains("Danish")
                                && !codeSystem.get("name").asText().contains("Inter")) {
                            continue;
                        }

                        // Process Edition
                        Edition edition = new Edition();

                        edition.setName(codeSystem.get("name").asText());
                        edition.setShortName(codeSystem.get("shortName").asText());
                        edition.setBranch(codeSystem.get("branchPath").asText());

                        if (codeSystem.has("defaultLanguageReferenceSets")) {
                            final JsonNode defaultLanguageReferenceSets =
                                    codeSystem.get("defaultLanguageReferenceSets");
                            final Iterator<JsonNode> defaultLanguageReferencesSetIterator =
                                    defaultLanguageReferenceSets.iterator();
                            while (defaultLanguageReferencesSetIterator.hasNext()) {
                                edition.getDefaultLanguageRefsets()
                                        .add(defaultLanguageReferencesSetIterator.next().asText());
                            }

                        } else if (edition.getName().equals("Common French Translation")) {
                            edition.getDefaultLanguageRefsets().add(CFR_LANGUAGE_REFSET_ID);
                        } else if (edition.getName().equals("Netherlands Edition")) {
                            edition.getDefaultLanguageRefsets().add(NL_LANGUAGE_REFSET_ID);
                        }

                        // Add the US English as default in all cases except
                        // where candian or UK is used
                        if (!edition.getDefaultLanguageRefsets().contains(UK_LANGUAGE_REFSET_ID)) {
                            edition.getDefaultLanguageRefsets().add(DEFAULT_LANGUAGE_REFSET_ID);
                        }

                        // Identify Edition's defaultLanguageCode - Per Kai,
                        // transform
                        // first language in set as defaultLangCode
                        if (!codeSystem.has("languages")) {
                            throw new Exception(
                                    "All Code Systems must have lanaguages set filled in. "
                                            + edition.toString() + " does not");
                        }

                        Iterator<String> languages = codeSystem.get("languages").fieldNames();
                        String defaultLanguage = languages.next();
                        edition.setDefaultLanguageCode(defaultLanguage);

                        // Identify Code System Owner
                        if (codeSystem.has("owner")) {
                            editionOwnerMap.put(edition.getShortName(),
                                    codeSystem.get("owner").asText());
                            editionOwnerMap.put(edition.getName(),
                                    codeSystem.get("owner").asText());
                        } else {
                            editionOwnerMap.put(edition.getShortName(), edition.getName());
                            editionOwnerMap.put(edition.getName(), edition.getName());
                        }

                        // Identify Top Level Module
                        identifyTopLevelModule(edition, codeSystem, internationalModules);

                        setMetadata(edition, defaultMeta);
                        service.add(edition);

                        addOrganziation(editionOwnerMap.get(edition.getName()), edition,
                                defaultMeta);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return internationalModules;
    }

    private void identifyTopLevelModule(Edition edition, JsonNode codeSystem,
        Set<String> internationalModules) throws Exception {
        if ("international edition".equals(edition.getName().toLowerCase())) {
            edition.setTopLevelModule(MODULE_ANCESTOR_CONCEPT_SCTID);
        } else {
            Iterator<JsonNode> moduleIterator = codeSystem.get("modules").iterator();

            Set<String> editionModules = new HashSet<>();
            while (moduleIterator.hasNext()) {
                JsonNode module = moduleIterator.next();
                if (!internationalModules.contains(module.get("conceptId").asText())) {
                    editionModules.add(module.get("conceptId").asText());
                }
            }

            if (editionModules.size() == 0) {
                edition.setTopLevelModule(MODULE_ANCESTOR_CONCEPT_SCTID);
                logger.info("No dedicated modules identified for " + edition.getName() + ": "
                        + editionModules.toString());
            } else if (editionModules.size() > 1) {
                Set<String> childrenModules = new HashSet<>();

                Set<String> children = getModuleChildren(edition);
                for (String moduleId : editionModules) {
                    if (children.contains(moduleId)) {
                        childrenModules.add(moduleId);
                    }
                }

                if (childrenModules.size() == 0 || childrenModules.size() > 1) {
                    logger.info("Seeing odd number of modules during secondary analysis for "
                            + edition.getName() + ": " + childrenModules.toString());
                } else {
                    edition.setTopLevelModule(childrenModules.iterator().next());
                }
            } else {
                edition.setTopLevelModule(editionModules.iterator().next());
            }
        }
    }

    private Set<String> getModuleChildren(Edition edition) throws Exception {
        String url = SnowstormConnection.BASE_URL + "browser/" + edition.getBranch() + "/concepts/"
                + MODULE_ANCESTOR_CONCEPT_SCTID + "/children";
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
            throw new Exception("Failed in getting code systems (first call to Snowstorm) with: "
                    + e.getMessage(), e);
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
     * Generate json from sql file.
     *
     * @param classPathResource the input resource
     * @param processType the process type
     * @throws Exception the exception
     */
    private void populateFromFile(final ClassPathResource classPathResource,
        final FileProcessType processType) throws Exception {
        BufferedReader reader;

        try {
            reader = new BufferedReader(new InputStreamReader(classPathResource.getInputStream()));

            // Grab Header on 2nd time through
            String line = reader.readLine();
            logger.debug("FIRST LINE OF FILE: " + line);

            line = reader.readLine();
            logger.debug("Second LINE OF FILE: " + line);

            while (line != null) {
                switch (processType) {
                    case REFSET:
                        final String refsetJson = lineToRefsetJson(line);
                        rttIdToRefsetJsonMap.put(line.split(SPLIT_CHARACTER)[0], refsetJson);
                        rttRefsetSctIdToRttIdMap.put(line.split(SPLIT_CHARACTER)[8],
                                line.split(SPLIT_CHARACTER)[0]);
                        break;

                    case CLAUSE:
                        // Combine multiline clauses into one
                        while (line.indexOf("\"") >= 0
                                && line.indexOf("\"") == line.lastIndexOf("\"")) {
                            line = line + " " + reader.readLine();
                        }

                        final String clauseJson = lineToClauseJson(line);

                        // store all clauses associated wtih a given refset
                        final String rttRefsetId = line.split(SPLIT_CHARACTER)[0];
                        if (!rttRefsetToClausesMap.containsKey(rttRefsetId)) {
                            rttRefsetToClausesMap.put(rttRefsetId, new ArrayList<String>());
                        }
                        rttRefsetToClausesMap.get(rttRefsetId).add(clauseJson);
                        break;

                    case PROJECT:
                        final String projectJson = lineToProjectJson(line);
                        rttIdToProjectsJsonMap.put(line.split(SPLIT_CHARACTER)[0], projectJson);
                        break;

                    default:
                        throw new Exception(
                                "Should never reach here have processType: " + processType);
                }

                // read next line
                line = reader.readLine();
            }

            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
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

            for (Refset refset : snowstormRefsets) {

                final Edition edition = refsetEditions.get(refset.getRefsetId());

                if (refsetsToIgnore.contains(refset.getRefsetId())) {
                    ignoreCounter++;
                    continue;
                } else if (!rttRefsetIds.contains(refset.getRefsetId())) {
                    /* Refset not in RTT */
                    final String name = edition.getName();
                    final String shortName = edition.getShortName();

                    // Identify Org Name
                    if (!editionOwnerMap.containsKey(name)
                            && !editionOwnerMap.containsKey(shortName)
                            || !organizationsAdded.containsKey(editionOwnerMap.get(name))) {
                        throw new Exception("Orgnaization based on edition '" + edition
                                + "' should have been created already");
                    }

                    final String orgName = editionOwnerMap.get(name) != null
                            ? editionOwnerMap.get(name) : editionOwnerMap.get(shortName);

                    // Create edition
                    final Organization org = organizationsAdded.get(orgName);

                    if (!defaultEditionProjects.containsKey(edition.getId())) {
                        // Create default project
                        final String projectName = "Default project for " + refset.getEditionName();
                        final String projectDescription =
                                "This project was created to support non-RTT based refsets for "
                                        + refset.getEditionName() + ".";

                        final Project project =
                                addProject(org, projectName, projectDescription, defaultMeta);
                        projectCount++;

                        defaultEditionProjects.put(edition.getId(), project);
                        projectsAdded.put(project.getName(), project);
                    }

                    if (!refsetsAdded.contains(refset.getRefsetId())) {
                        refsetsAdded.add(refset.getRefsetId());
                        counts.incrementUniqueNoMetadataCount();

                        logger.debug("Persisting " + refset.getRefsetId() + " in "
                                + branchDateFormatter.format(refset.getVersionDate()) + " in "
                                + edition.getName());
                    }

                    counts.incrementNoMetadataCount();
                    refset.setProject(defaultEditionProjects.get(edition.getId()));
                    setMetadata(refset, defaultMeta);
                } else {
                    /* Refset in RTT, so pull project & Org data from there */
                    final String rttId = rttRefsetSctIdToRttIdMap.get(refset.getRefsetId());
                    final String projectId = rttIdToRttProjectIdMap.get(rttId);

                    final Project rttProject = ModelUtility
                            .fromJson(rttIdToProjectsJsonMap.get(projectId), Project.class);

                    final Organization rttOrg = rttProject.getOrganization();

                    final Metadata projectMeta =
                            new Metadata(rttProject.getModified(), rttProject.getModifiedBy());

                    final String translatedOrgName = translateRttOrg(rttOrg.getName());

                    Organization org = null;
                    if (translatedOrgName == null
                            || !organizationsAdded.containsKey(translatedOrgName)) {
                        logger.debug(
                                "    ****   Warning - Ran across an organization that doesn't reside in Snowstorm!");
                        org = addOrganziation(translatedOrgName, edition, defaultMeta);
                        organizationsAdded.put(translatedOrgName, org);
                    } else {
                        org = organizationsAdded.get(translatedOrgName);
                    }

                    if (!projectsAdded.containsKey(rttProject.getName())) {
                        final Project project = addProject(org, rttProject.getName(),
                                rttProject.getDescription(), projectMeta);
                        projectCount++;

                        projectsAdded.put(rttProject.getName(), project);
                    }

                    // If has ECL clauses, add them to db & refset
                    if (rttRefsetToClausesMap.containsKey(rttId)) {
                        for (String clauseJson : rttRefsetToClausesMap.get(rttId)) {
                            final DefinitionClause clause =
                                    ModelUtility.fromJson(clauseJson, DefinitionClause.class);

                            setMetadata(clause, metadataMap.get("refset-" + rttId));
                            service.add(clause);
                            refset.getDefinitionClauses().add(clause);
                        }
                    }

                    if (!refsetsAdded.contains(refset.getRefsetId())) {
                        refsetsAdded.add(refset.getRefsetId());
                        counts.incrementUniqueRttMetadataCount();

                        logger.debug("Persisting " + refset.getRefsetId() + " in "
                                + branchDateFormatter.format(refset.getVersionDate()) + " in "
                                + edition.getName());
                    }

                    counts.incrementRttMetadataCount();
                    refset.setProject(projectsAdded.get(rttProject.getName()));
                    setMetadata(refset, metadataMap.get("refset-" + rttId));
                }

                service.add(refset);

                if (++count % 250 == 0) {
                    logger.info("Imported + " + count + " refsets thus far");
                }

            }

            logger.info("Have imported " + projectCount + " projects and "
                    + counts.getOrgsImportedCount() + " organizations");

            // Unique Counts
            logger.info("\n*** Unique Refsets Count ***");
            logger.info("Have identified " + counts.getUniqueRefsetsCounts()
                    + " unique refsets on Snowstorm");
            logger.info("Have imported " + counts.getUniqueRttMetadataCreatedCount()
                    + " unique refsets with metadata pulled from RTT");
            logger.info("Have imported " + counts.getUniqueNoMetadataCreatedCount()
                    + " unique refsets with no metadata at all");

            // Refset/Verfsion Pair Counts
            logger.info("\n*** Refsets/Version Pair Count ***");
            logger.info("Have identified " + counts.getRefsetVersionPairsCounts()
                    + " refset/version pairs on Snowstorm");
            logger.info("Have imported " + counts.getRttMetadataCreatedCount()
                    + " refset/version pairs with metadata pulled from RTT");
            logger.info("Have imported " + counts.getNoMetadataCreatedCount()
                    + " refset/version pairs with no metadata at all");

            logger.info("Total of " + ignoreCounter + " refsets ignored");
        } catch (Exception e) {
            logger.error("Have issue with: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String translateRttOrg(String name) {
        if (!debugRttOrgTranslations.contains(name)) {
            debugRttOrgTranslations.add(name);
            logger.debug("First time seeing: " + name);
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
        }

        if (shortName != null) {
            return editionOwnerMap.get(shortName);
        } else {
            return name;
        }
    }

    private Project addProject(Organization org, String projectName, String projectDescription,
        Metadata meta) throws Exception {
        try (final TerminologyService service = new TerminologyService()) {
            service.setModifiedBy("Migration");
            service.setModifiedFlag(true);

            final Project project = new Project();
            project.setName(projectName);
            project.setDescription(projectDescription);
            project.setOrganization(org);
            project.setPrivateProject(false);

            // Persist
            setMetadata(project, meta);
            return service.add(project);
        }
    }

    private Organization addOrganziation(final String orgName, final Edition edition,
        final Metadata meta) throws Exception {
        try (final TerminologyService service = new TerminologyService()) {
            service.setModifiedBy("Migration");
            service.setModifiedFlag(true);

            Organization org = new Organization();
            org.setName(orgName);
            org.setEdition(edition);

            setMetadata(org, meta);

            org = service.add(org);

            organizationsAdded.put(orgName, org);

            counts.incrementOrgsImportedCount();

            return org;
        }
    }

    /**
     * Line to project json.
     *
     * @param line the line
     * @return the string
     * @throws Exception the exception
     */
    private String lineToProjectJson(final String line) throws Exception {
        String projectName;
        String projectDescription;
        String organizationName;
        String modified;
        String modifiedBy;

        if (line.split(SPLIT_CHARACTER)[1].startsWith("\"")) {
            // If description has commas (and some do), can't rely on splitting
            // on comma. Must identify Description and then remove from line
            // before finding other values
            final int descStartIdx = line.indexOf("\"");
            final int descEndIdx = line.substring(descStartIdx + 1).indexOf("\"");

            projectDescription = line.substring(descStartIdx + 1, descStartIdx + descEndIdx + 1);
            String[] values = line.substring(descStartIdx + descEndIdx + 3).split(SPLIT_CHARACTER);

            projectName = values[5].replaceAll("\"", "");
            organizationName = values[7].replaceAll("\"", "");
            modified = values[2];
            modifiedBy = values[3];
        } else {
            String[] values = line.split(SPLIT_CHARACTER);

            projectDescription = values[1];
            projectName = values[7].replaceAll("\"", "");
            organizationName = values[9].replaceAll("\"", "");
            modified = values[4];
            modifiedBy = values[5];
        }

        StringBuffer buf = new StringBuffer();

        buf.append("{");
        buf.append("\"name\": \"" + projectName + "\",");
        buf.append("\"description\": \"" + projectDescription + "\",");
        buf.append("\"organization\": {\"name\": \"" + organizationName + "\"}");
        buf.append("}");

        jsonProjectOrganziationMap.put("project-" + line.split(SPLIT_CHARACTER)[0],
                organizationName);
        metadataMap.put("project-" + line.split(SPLIT_CHARACTER)[0],
                new Metadata(modified, modifiedBy));

        return buf.toString();
    }

    /**
     * Line to clause json.
     *
     * @param line the line
     * @return the string
     */
    private String lineToClauseJson(final String line) {
        StringBuffer buf = new StringBuffer();
        String[] clauseValues = line.split(SPLIT_CHARACTER);
        buf.append("{ \"negated\":\"");
        buf.append(clauseValues[1].equals("0") ? "false" : "true");
        buf.append("\",");

        buf.append(
                "\"value\":\"" + clauseValues[2].replaceAll("\"", "").replaceAll("\t", "") + "\"}");
        return buf.toString();
    }

    /**
     * Line to refset json.
     *
     * @param line the line
     * @return the string
     */
    private String lineToRefsetJson(final String line) {
        String updatedLine = line;
        String narrative;

        try {
            // Clean up narrative if has commas which some do
            if (updatedLine.split(SPLIT_CHARACTER)[9].startsWith("\"")) {
                // Can't rely on splitting on comma. Must identify narrative and
                // then remove from line before finding other values
                final int descStartIdx = updatedLine.indexOf(updatedLine.split(SPLIT_CHARACTER)[9]);
                final int descEndIdx = updatedLine.substring(descStartIdx + 1).indexOf("\"");
                narrative = updatedLine.substring(descStartIdx + 1, descStartIdx + descEndIdx + 1);

                // Cleanup updateLine to remove ',' in narrative
                updatedLine = updatedLine.substring(0, descStartIdx)
                        + narrative.replaceAll(SPLIT_CHARACTER, "")
                        + updatedLine.substring(descStartIdx + descEndIdx + 2);
            } else {
                narrative = updatedLine.split(SPLIT_CHARACTER)[9];
            }

            // Clean up name if has commas (which some do)
            if (updatedLine.split(SPLIT_CHARACTER)[17].startsWith("\"")) {
                // Can't rely on splitting on comma. Must identify name portion
                // and then remove from line before finding other values
                final int descStartIdx =
                        updatedLine.indexOf(updatedLine.split(SPLIT_CHARACTER)[17]);
                final int descEndIdx = updatedLine.substring(descStartIdx + 1).indexOf("\"");

                // Cleanup line to remove ',' in narrative
                updatedLine = updatedLine.substring(0, descStartIdx) + narrative.replaceAll(",", "")
                        + updatedLine.substring(descStartIdx + descEndIdx + 2);
            }

            updatedLine = updatedLine.replace("\"", "");
            final String values[] = updatedLine.split(SPLIT_CHARACTER);
            final StringBuffer buf = new StringBuffer();
            final String rttRefsetId = values[0];

            // Begin RefsetJson
            buf.append("{");
            buf.append("\"type\": \"" + values[24] + "\",");
            buf.append("\"narrative\": \"" + narrative + "\",");
            buf.append("\"privateRefset\": " + ((values[15].equals("0")) ? "true" : "false"));

            // Tags
            if (values[28] != null && !values[28].isEmpty() && !values[28].equals("NULL")) {
                buf.append(",");
                buf.append("\"tags\": [\"" + values[28] + "\"]");
            }
            buf.append("}");
            // End RefsetJson

            // Store ability to map from RefsetId to ProjectId
            rttIdToRttProjectIdMap.put(rttRefsetId, values[27]);

            Metadata meta = new Metadata(values[3], values[4]);
            metadataMap.put("refset-" + rttRefsetId, meta);

            return buf.toString();
        } catch (Exception e) {
            logger.error("Line: " + line + " and updateLine: " + updatedLine);
            e.printStackTrace();

            throw e;
        }
    }

    /**
     * Pre-processing supporting files.
     *
     * @throws Exception the exception
     */
    private void parseRttData() throws Exception {
        populateFromFile(clausesResource, FileProcessType.CLAUSE);
        populateFromFile(projectsResource, FileProcessType.PROJECT);
        populateFromFile(refsetsResource, FileProcessType.REFSET);

        // Based on findings, define the list of refsets in RTT
        rttRefsetIds = rttRefsetSctIdToRttIdMap.keySet();
    }

    private void setMetadata(final HasModified object, final Metadata metadata) {
        object.setModified(metadata.getModified());
        object.setCreated(metadata.getModified());
        object.setModifiedBy(metadata.getModifiedBy());
    }
}
