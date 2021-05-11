package org.ihtsdo.refsetservice.util.test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
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
import org.ihtsdo.refsetservice.test.BaseTest;
import org.ihtsdo.refsetservice.util.ModelUtility;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Validates the code-system-* files in the "src/resources" folder.
 */
public class RefsetMetadataMigrationTest extends BaseTest {

    /** The formatter. */
    private final SimpleDateFormat branchDateFormatter = new SimpleDateFormat("yyyy-MM-dd");

    /** The sdf. */
    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    /**
     * The Class Metadata.
     */
    private class Metadata {

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
     * The Enum FileProcessType.
     */
    private enum FileProcessType {

        /** The refset. */
        REFSET,
        /** The clause. */
        CLAUSE,
        /** The project. */
        PROJECT;
    }

    /** The Constant DEFAULT_LANGUAGE_SET_ID. */
    private static final String DEFAULT_LANGUAGE_REFSET_ID = "900000000000509007";

    /** The Constant CFR_LANGUAGE_REFSET_ID. */
    private static final String CFR_LANGUAGE_REFSET_ID = "21000241105";

    /** The Constant NL_LANGUAGE_REFSET_ID. */
    private static final String NL_LANGUAGE_REFSET_ID = "15551000146102";

    /** The Constant SPLIT_CHARACTER. */
    private static final String SPLIT_CHARACTER = "\t";

    /** The logger. */
    private final Logger logger = LoggerFactory.getLogger(RefsetMetadataMigrationTest.class);

    /** The projects file. */
    private final String projectsFile = "src/test/resources/migration/refsetsToImport/projects.txt";

    /** The clauses file. */
    private final String clausesFile = "src/test/resources/migration/refsetsToImport/clauses.txt";

    /** The clauses file. */
    private final String branchToOrgFile =
            "src/test/resources/migration/refsetsToImport/BranchToOrganizationMap.txt";

    /** The all refsets file path. */
    private final String allRefsetsFilePath =
            "src/test/resources/migration/refsetsToImport/AllFromRTT.txt";

    /** The metadata map. */
    private final Map<String, Metadata> metadataMap = new HashMap<>();

    /** The refset internal id map. */
    private final Map<String, String> refsetInternalIdMap = new HashMap<>();

    /** The refset sct id to internal id map. */
    private final Map<String, String> refsetSctIdToInternalIdMap = new HashMap<>();

    /** The rtt refset to clauses map. */
    private final Map<String, ArrayList<String>> rttRefsetToClausesMap = new HashMap<>();

    /** The projects map. */
    private final Map<String, String> projectsMap = new HashMap<>();

    /** The refset to project map. */
    private final Map<String, String> refsetToProjectMap = new HashMap<>();

    /** The refsets to ignore. */
    private final Set<String> refsetsToIgnore = new HashSet<>();

    /** The international refsets. */
    private final Set<String> internationalRefsets = new HashSet<>();

    /** The not from RTT map. */
    private final Map<String, Refset> notFromRTTMap = new HashMap<>();

    /** The testing. */
    private boolean testing = false;

    /** The Constant TESTING_EDITION. */
    private static final String TESTING_EDITION = "Swed";

    /** The Constant TESTING_REFSET. */
    private static final String TESTING_REFSET = "46011000052107";

    /**
     * Import all refsets.
     *
     * @throws Exception the exception
     */
    @Test
    public void testAllRefsets() throws Exception {
        HashSet<String> internationalModules = createEditionsFromSnowstorm();
        Map<String, SortedMap<Date, String>> branches = identifyBranches();

        Set<Refset> allRefsets = createRefsetsFromSnowstorm(branches, internationalModules);
        // Read refset metadata and associated information (projects & ECLs)
        parseRTTMetadata();

        // Skip those refsets that live on SnowS, but I don't have a dmp of yet
        for (Refset refset : allRefsets) {
            if (!refsetSctIdToInternalIdMap.keySet().contains(refset.getRefsetId())) {
                if (internationalRefsets.contains(refset.getRefsetId())) {
                    refsetsToIgnore.add(refset.getRefsetId());
                    logger.debug("Int'l refsets supported by extensions for: "
                            + refset.getEditionName() + " refset: " + refset.getName() + " ("
                            + refset.getRefsetId() + ")");
                }
            }
        }

        // With metadata from RTT project (defined in parseRTTMetadata())
        updateRefsets(allRefsets);
        removeUnnecessaryProjects(allRefsets);
        persistObjects(allRefsets);
    }

    /**
     * Removes the unnecessary projects.
     *
     * @param allRefsets the all refsets
     */
    private void removeUnnecessaryProjects(Set<Refset> allRefsets) {
        Set<String> projectsWithRefsets = new HashSet<>();
        Set<String> projectsToRemove = new HashSet<>();

        for (Refset refset : allRefsets) {
            final String rttId = refsetSctIdToInternalIdMap.get(refset.getRefsetId());
            String projectId = refsetToProjectMap.get(rttId);

            projectsWithRefsets.add(projectId);
        }

        for (String projectId : projectsMap.keySet()) {
            if (!projectsWithRefsets.contains(projectId)) {
                projectsToRemove.add(projectId);
            }
        }

        for (String projectId : projectsToRemove) {
            projectsMap.remove(projectId);
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
                if (testing && !edition.getName().contains(TESTING_EDITION)) {
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
     * Update refsets.
     *
     * @param allRefsets the all refsets
     * @throws JsonMappingException the json mapping exception
     * @throws JsonProcessingException the json processing exception
     */
    private void updateRefsets(Set<Refset> allRefsets)
        throws JsonMappingException, JsonProcessingException {
        for (Refset refset : allRefsets) {
            if (refsetsToIgnore.contains(refset.getRefsetId())) {
                continue;
            }

            if (refsetSctIdToInternalIdMap.keySet().contains(refset.getRefsetId())) {
                final String rttId = refsetSctIdToInternalIdMap.get(refset.getRefsetId());
                final String refsetJsonString = refsetInternalIdMap.get(rttId);

                final ObjectMapper mapper = new ObjectMapper();
                final JsonNode refsetJson = mapper.readTree(refsetJsonString);

                refset.setType(refsetJson.get("type").asText());
                refset.setNarrative(refsetJson.get("narrative").asText());
                refset.setPrivateRefset(refsetJson.get("privateRefset").asBoolean());

                // Tags
                if (refsetJson.has("tags")) {
                    Iterator<JsonNode> tagsIterator = refsetJson.get("tags").iterator();
                    while (tagsIterator.hasNext()) {
                        refset.getTags().add(tagsIterator.next().asText());
                    }
                }
            } else {
                // Refsets in Snowstorm but not RT2
                refset.setType("EXTENSIONAL");
                refset.setNarrative("None as not from RTT");
                refset.setPrivateRefset(true);
                refset.getEdition().setDefaultLanguageRefsets(new HashSet<String>());

                notFromRTTMap.put(refset.getRefsetId(), refset);
            }
        }

    }

    /**
     * Populate editions.
     *
     * @param branchChildren the branch children
     * @param internationalModules the international modules
     * @return the sets the
     * @throws Exception the exception
     */
    private Set<Refset> createRefsetsFromSnowstorm(
        Map<String, SortedMap<Date, String>> branchChildren, HashSet<String> internationalModules)
        throws Exception {
        Set<Refset> allRefsets = new HashSet<>();

        String url = SnowstormConnection.BASE_URL + "browser/{branch}/members";

        try (final TerminologyService service = new TerminologyService()) {
            service.setModifiedBy("Migration");
            service.setModifiedFlag(true);

            BufferedWriter writer = new BufferedWriter(new FileWriter("RefsetsAdded.txt"));

            for (String editionId : branchChildren.keySet()) {
                final Edition edition = service.get(editionId, Edition.class);

                logger.info("Processing Edition: " + edition.getName());
                writer.append("\n\n\nProcessing Edition: " + edition.getName() + "\n");

                for (Date branchDate : branchChildren.get(editionId).keySet()) {
                    final String childBranch = branchChildren.get(editionId).get(branchDate);
                    writer.append("\n\n\nProcessing Branch: " + branchDate + "\n");

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

                        // get RefSets from edition as long as a) active &
                        // b) within
                        // edition's module
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
                            final Boolean isActive = refsetNode.get("active").asBoolean();

                            if (testing && !refsetId.equals(TESTING_REFSET)) {
                                continue;
                            }

                            if (!internationalModules.contains(moduleId) && isActive) {
                                // Process Valid Refset
                                try {
                                    Refset refset = new Refset();

                                    refset.setRefsetId(refsetId);
                                    refset.setModuleId(moduleId);
                                    refset.setVersionDate(branchDate);
                                    refset.setVersionStatus("PUBLISHED");
                                    refset.setEdition(edition);
                                    refset.setActive(true);

                                    if (refsetNode.get("pt").has("term")) {
                                        refset.setName(refsetNode.get("pt").get("term").asText());
                                    } else {
                                        refset.setName(
                                                lookupRefsetName(refsetId, edition, childBranch));
                                    }

                                    writer.write("Adding refset: " + refsetId);
                                    allRefsets.add(refset);

                                    writer.write("\n");
                                } catch (Exception e) {
                                    logger.error("Failed with refsetNode: " + refsetNode);
                                }
                            }
                            if (edition.getName().equals("International Edition")) {
                                internationalRefsets.add(refsetId);
                            }
                        }

                    }
                }
            }
            writer.close();
        }

        return allRefsets;
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
     * @return the sets the
     * @throws Exception the exception
     */
    private HashSet<String> createEditionsFromSnowstorm() throws Exception {
        // SHould have 3 results
        String url = SnowstormConnection.BASE_URL + "/codesystems";
        HashSet<String> internationalModules = new HashSet<>();

        try (final Response response = SnowstormConnection.getResponse(url)) {

            final String resultString = response.readEntity(String.class);
            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode root = mapper.readTree(resultString.toString());

            try (final TerminologyService service = new TerminologyService()) {
                service.setModifiedBy("Migration");
                service.setModifiedFlag(true);

                final Iterator<JsonNode> responseIterator = root.iterator();

                while (responseIterator.hasNext()) {

                    final Iterator<JsonNode> codeSystems = responseIterator.next().iterator();

                    while (codeSystems.hasNext()) {
                        JsonNode codeSystem = codeSystems.next();

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
                        } else {
                            edition.getDefaultLanguageRefsets().add(DEFAULT_LANGUAGE_REFSET_ID);
                        }

                        if (codeSystem.has("defaultLanguageCode")) {
                            edition.setDefaultLanguageCode(
                                    codeSystem.get("defaultLanguageCode").asText());
                        } else if (codeSystem.has("languages")) {
                            edition.setDefaultLanguageCode(
                                    codeSystem.get("languages").fieldNames().next());
                        } else {
                            throw new Exception("No langauages for edition: " + edition.toString());
                        }

                        service.add(edition);

                        if (edition.getName().equals("International Edition")) {
                            Iterator<JsonNode> moduleIterator =
                                    codeSystem.get("modules").iterator();

                            while (moduleIterator.hasNext()) {
                                JsonNode module = moduleIterator.next();
                                internationalModules.add(module.asText());
                            }
                        }
                    }
                }
            }
        }

        return internationalModules;
    }

    /**
     * Generate json from sql file.
     *
     * @param inputFile the input file
     * @param processType the process type
     * @throws Exception the exception
     */
    private void populateFromFile(final String inputFile, final FileProcessType processType)
        throws Exception {
        BufferedReader reader;

        try {
            reader = new BufferedReader(new FileReader(inputFile));

            // Grab Header on 2nd time through
            String line = reader.readLine();
            line = reader.readLine();

            while (line != null) {
                switch (processType) {
                    case REFSET:
                        final String refsetJson = lineToRefsetJson(line);
                        refsetInternalIdMap.put(line.split(SPLIT_CHARACTER)[0], refsetJson);
                        refsetSctIdToInternalIdMap.put(line.split(SPLIT_CHARACTER)[8],
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
                        projectsMap.put(line.split(SPLIT_CHARACTER)[0], projectJson);
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
    private void persistObjects(Set<Refset> allRefsets) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {
            service.setModifiedFlag(false);

            // Persist Projects and Organizations
            int projectCount = 0;
            int organizationCount = 0;
            final Map<String, Project> projectIdToClassMap = new HashMap<>();
            final Map<String, Organization> organizationsAdded = new HashMap<>();

            for (String index : projectsMap.keySet()) {
                final Project project =
                        ModelUtility.fromJson(projectsMap.get(index), Project.class);

                if (!organizationsAdded.containsKey(project.getOrganization().getName())) {
                    setMetadata(project.getOrganization(), metadataMap.get("project-" + index));
                    service.add(project.getOrganization());

                    organizationsAdded.put(project.getOrganization().getName(),
                            project.getOrganization());
                    organizationCount++;
                } else {
                    project.setOrganization(
                            organizationsAdded.get(project.getOrganization().getName()));
                }

                setMetadata(project, metadataMap.get("project-" + index));
                service.add(project);
                projectIdToClassMap.put(index, project);
                projectCount++;
            }

            logger.info("Have imported " + projectCount + " projects and " + organizationCount
                    + " organizations");

            // Handle refsets not in RTT
            Set<String> editionsProcessed = new HashSet<>();
            Map<String, Project> editionToProjects = new HashMap<>();
            Map<String, String> branchOrganizationMap = identifyExistingOrganizations();
            projectCount = 0;
            organizationCount = 0;
            Metadata meta = new Metadata(sdf.format(new Date()), "System initialization");

            for (String id : notFromRTTMap.keySet()) {
                Refset refset = notFromRTTMap.get(id);

                if (!editionsProcessed.contains(refset.getEdition().getId())) {
                    editionsProcessed.add(refset.getEdition().getId());

                    // Create Organization for non-RTT based refsets
                    Organization org = new Organization();
                    String branchName = refset.getEdition().getBranch()
                            .substring(refset.getEdition().getBranch().indexOf("SNOMEDCT"));
                    String orgName = branchOrganizationMap.get(branchName);
                    if (orgName == null) {
                        orgName = "Organization responsible for " + refset.getEditionName();
                    }
                    org.setName(orgName);
                    org.setDescription(
                            "This organization was created to support non-RTT based refsets.");

                    setMetadata(org, meta);
                    service.add(org);
                    organizationCount++;

                    // Create Project for non-RTT based refsets
                    Project project = new Project();
                    project.setName("Default project for " + refset.getEditionName());
                    project.setDescription(project.getName()
                            + ". This project was created to support non-RTT based refsets.");
                    project.setOrganization(org);
                    editionToProjects.put(refset.getEdition().getId(), project);

                    setMetadata(project, meta);
                    service.add(project);
                    projectCount++;
                }
            }

            logger.info("Have created " + projectCount + " projects and " + organizationCount
                    + " organizations to support refsets not found in RTT");

            // Persist Refsets & ECL Definition Clauses
            int count = 0;
            logger.info("About to import " + allRefsets.size()
                    + " refsets (which list multiple versions separately) and their respsective clauses");

            BufferedWriter writer = new BufferedWriter(new FileWriter("RefsetsIgnored.txt"));
            int ignoreCounter = 0;

            for (Refset refset : allRefsets) {
                if (refsetsToIgnore.contains(refset.getRefsetId())) {
                    ignoreCounter++;
                    writer.append("From " + refset.getEditionName() + " ignoring refset:\t"
                            + refset.getName() + "\t(" + refset.getRefsetId() + ")\n");
                    continue;
                } else if (notFromRTTMap.keySet().contains(refset.getRefsetId())) {
                    // Handle refsets not in RTT
                    refset.setProject(editionToProjects.get(refset.getEdition().getId()));
                    setMetadata(refset, meta);
                    service.add(refset);
                } else {

                    final String rttId = refsetSctIdToInternalIdMap.get(refset.getRefsetId());
                    String projectId = refsetToProjectMap.get(rttId);
                    refset.setProject(projectIdToClassMap.get(projectId));

                    setMetadata(refset, metadataMap.get("refset-" + rttId));
                    service.add(refset);

                    if (rttRefsetToClausesMap.containsKey(rttId)) {
                        for (String clauseJson : rttRefsetToClausesMap.get(rttId)) {
                            final DefinitionClause clause =
                                    ModelUtility.fromJson(clauseJson, DefinitionClause.class);

                            setMetadata(clause, metadataMap.get("refset-" + rttId));
                            service.add(clause);
                            refset.getDefinitionClauses().add(clause);
                        }

                        service.update(refset);
                    }
                }

                count++;

                if (count % 250 == 0) {
                    logger.info("Imported + " + count + " refsets thus far");
                }
            }

            writer.close();
            logger.info("Total of " + count + " refsets successfully added and " + ignoreCounter
                    + " refsets ignored");
        } catch (Exception e) {
            logger.error("Have issue with: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Identify existing organizations.
     *
     * @return the map
     */
    private Map<String, String> identifyExistingOrganizations() {
        BufferedReader reader;
        Map<String, String> retMap = new HashMap<>();
        String line = null;

        try {
            reader = new BufferedReader(new FileReader(branchToOrgFile));

            // Grab Header on 2nd time through
            line = reader.readLine();

            while (line != null && !line.trim().isEmpty()) {
                retMap.put(line.split("\t")[0], line.split("\t")[1]);
                line = reader.readLine();
            }

            reader.close();
        } catch (Exception e) {
            logger.error("Failed on line: " + line);
            e.printStackTrace();
        }

        return retMap;
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
            refsetToProjectMap.put(rttRefsetId, values[27]);

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
    private void parseRTTMetadata() throws Exception {
        populateFromFile(clausesFile, FileProcessType.CLAUSE);
        populateFromFile(projectsFile, FileProcessType.PROJECT);
        populateFromFile(allRefsetsFilePath, FileProcessType.REFSET);
    }

    /**
     * Sets the metadata.
     *
     * @param object the object
     * @param metadata the metadata
     */
    private void setMetadata(final HasModified object, final Metadata metadata) {
        object.setModified(metadata.getModified());
        object.setCreated(metadata.getModified());
        object.setModifiedBy(metadata.getModifiedBy());
    }
}
