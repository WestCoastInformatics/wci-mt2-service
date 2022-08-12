package org.ihtsdo.refsetservice.sync;

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
import java.util.stream.Collectors;

import javax.ws.rs.core.Response;

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

public class SyncAgent {

    /** The logger. */
    private static Logger logger = LoggerFactory.getLogger(SyncAgent.class);

    protected static boolean runShortSync;

    protected static boolean forProduction;

    protected static SyncUtilities utilities = null;

    protected static List<Edition> allDatabaseEditions = null;

    protected static List<Organization> allDatabaseOrganizations = null;

    protected static List<Refset> allDatabaseRefsets = null;

    protected static Organization develeperTestingOranization = null;

    protected static final Map<String, Organization> organizationsAdded = new HashMap<>();

    protected static final Set<Organization> organizationsUnchanged = new HashSet<>();

    protected static final Set<Organization> organizationsSynced = new HashSet<>();

    protected static final Map<String, Project> defaultOrganizationProjects = new HashMap<>();

    protected static final Set<Edition> editionsAdded = new HashSet<>();

    protected static final Set<Edition> editionsUnchanged = new HashSet<>();

    protected static final Set<Edition> editionsSynced = new HashSet<>();

    protected static final Set<Refset> refsetVersionsAdded = new HashSet<>();

    protected static final Set<Refset> refsetVersionsUnchanged = new HashSet<>();

    protected static final Set<Refset> refsetVersionsSynced = new HashSet<>();

    protected static final Map<String, Edition> refsetEditions = new HashMap<>();

    protected static final Set<String> uniqueRefsetIds = new HashSet<>();

    private static final List<String> ignoredCodeSystemNames = new ArrayList<>();

    /** The testing. */
    protected static final boolean testing = false;

    protected static final String testingEdition = "elgia";

    protected static final String testingRefset = "741000172102";

    protected static final String DEVELOPER_ORGANIZATION_NAME_KEYWORD = "wci";

    // The max number of record elasticsearch will return without erroring.

    /* Constants */
    protected static final SimpleDateFormat branchDateFormatter = new SimpleDateFormat("yyyy-MM-dd");

    protected final Set<Refset> snowstormRefsets = new HashSet<>();

    protected Set<String> internationalModuleRefsets = new HashSet<>();

    protected static final String SIMPLE_TYPE_REFSET_SCTID = "446609009";

    public static final int TIMEOUT_MILLISECOND_THRESHOLD = 60000;

    protected static final Map<String, String> editionOwnerMap = new HashMap<>();

    public SyncAgent(boolean runShortSync, boolean runForProduction) {

        if (utilities == null) {

            SyncAgent.utilities = new SyncUtilities();

            ignoredCodeSystemNames.addAll(SyncAgent.utilities.getPropertyReader().readCodeSystemsToIgnore());

            SyncAgent.runShortSync = runShortSync;
            SyncAgent.forProduction = runForProduction;

            try {

                updateDatabaseCache();

            } catch (Exception e) {

                e.printStackTrace();
            }

        }

    }

    protected SyncAgent() throws Exception {

        if (SyncAgent.utilities == null) {

            throw new Exception("How create a supporting agent without creating SyncAgent?");
        }

    }

    public void sync() {

        try {

            clearPreviousRun();

            Set<JsonNode> codeSystemsToProcess = filterCodeSystems();

            SyncCodeSystemAgent.syncSnowstormCodeSystems(codeSystemsToProcess);

            // Only identify branches on filtered code systems and on runShortSync value
            Map<String, SortedMap<Date, String>> branchesToProcess = identifyEditionBranches(codeSystemsToProcess);

            // Identify all refset metadata, any refsets' ECL definitions, and project metadata from RTT files manually sync'd over
            // TODO: Add a automated pull of the data off of RTT?
            utilities.getPropertyReader().parseRttData();

            // Find all refsets from filtered branches
            SyncRefsetAgent.syncSnowstormRefsets(branchesToProcess);

            // Update imported refsets with RTT-based metadata (as defined in parseRttData())
            if (!forProduction) {

                SyncRefsetAgent.populateInitialData();
            }

        } catch (Exception e) {

            logger.error("Failed during sync");
            e.printStackTrace();
        } finally {

            printSyncResults();
        }

    }

    private void identifyInternationalModules(JsonNode root) throws Exception {

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
                        utilities.getInternationalModules().add(module.get("conceptId").asText());
                    }

                }

            }

        }

        logger.info("Identified " + utilities.getInternationalModules().size() + " international modules");

        if (utilities.getInternationalModules().isEmpty()) {

            throw new Exception("Didn't find the international modules as anticipated");

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
    /**
     * @return
     * @throws Exception
     */
    private JsonNode getSnowstormCodeSystems() throws Exception {

        final String url = SnowstormConnection.BASE_URL + "codesystems";
        logger.debug("getSnowstormCodeSystems url: " + url);

        try (final Response response = SnowstormConnection.getResponse(url)) {

            final String resultString = response.readEntity(String.class);

            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode organizationJsonRootNode = mapper.readTree(resultString.toString());

            identifyInternationalModules(organizationJsonRootNode);

            return organizationJsonRootNode;
        }

    }

    private Set<JsonNode> filterCodeSystems() throws Exception {

        final JsonNode organizationJsonRootNode = getSnowstormCodeSystems();

        final Set<JsonNode> filteredCodeSystems = new HashSet<>();

        final Iterator<JsonNode> organizationIterator = organizationJsonRootNode.iterator();

        while (organizationIterator.hasNext()) {

            final Iterator<JsonNode> codeSystems = organizationIterator.next().iterator();

            while (codeSystems.hasNext()) {

                JsonNode codeSystem = codeSystems.next();

                // Check for invalid or ignored code systems
                if (!codeSystem.has("name")) {

                    // Skipping odd code system without a name
                    continue;
                } else if (ignoredCodeSystemNames.contains(codeSystem.get("name").asText().toLowerCase())) {

                    // Code System is defined as to-be-ignored
                    continue;
                }

                // Testing
                if (testing && !codeSystem.get("name").asText().contains(testingEdition) && !codeSystem.get("name").asText().toLowerCase().contains(DEVELOPER_ORGANIZATION_NAME_KEYWORD)
                    && !codeSystem.get("name").asText().contains("Inter")) {

                    continue;
                }

                filteredCodeSystems.add(codeSystem);
            }

        }

        return filteredCodeSystems;
    }

    private void clearPreviousRun() {

        develeperTestingOranization = null;

        organizationsAdded.clear();
        organizationsUnchanged.clear();
        organizationsSynced.clear();
        defaultOrganizationProjects.clear();

        editionsAdded.clear();
        editionsUnchanged.clear();
        editionsSynced.clear();

        refsetVersionsAdded.clear();
        refsetVersionsSynced.clear();
        refsetVersionsUnchanged.clear();
        refsetEditions.clear();

        uniqueRefsetIds.clear();
        ignoredCodeSystemNames.clear();

    }

    /**
     * Identify branches.
     *
     * @return the map
     * @throws Exception the exception
     */
    private Map<String, SortedMap<Date, String>> identifyEditionBranches(Set<JsonNode> codeSystems) throws Exception {

        Map<String, SortedMap<Date, String>> retMap = new HashMap<>();

        for (JsonNode codeSystem : codeSystems) {

            final String editionName = codeSystem.get("name").asText();
            final String shortName = codeSystem.get("shortName").asText();

            logger.info("Identifying CodeSystem branches for: " + editionName);

            if (testing && !editionName.contains(testingEdition) && !editionName.toLowerCase().contains(DEVELOPER_ORGANIZATION_NAME_KEYWORD) && !editionName.contains("International")) {

                continue;
            }

            Edition edition = allDatabaseEditions.stream().filter(e -> shortName.equals(e.getShortName())).collect(Collectors.toList()).iterator().next();

            final String genericUrl = SnowstormConnection.BASE_URL + "branches/{branch}/children";

            SortedMap<Date, String> children = new TreeMap<>();
            logger.debug(" genericUrl: " + genericUrl.replace("{branch}", edition.getBranch()));

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

            }

            retMap.put(edition.getId(), children);
        }

        return retMap;
    }

    protected static void updateDatabaseCache() throws Exception {

        try (TerminologyService service = new TerminologyService()) {

            allDatabaseEditions = service.getAll(Edition.class);
            // logger.debug(" All Editions: " + allDatabaseEditions);

            allDatabaseOrganizations = service.getAll(Organization.class);
            // logger.debug(" All Organizations: " + allDatabaseOrganizations);

            allDatabaseRefsets = service.getAll(Refset.class);
            // logger.debug(" All Refsets: " + allDatabaseRefsets);
        }

    }

    private void printSyncResults() {

        logger.info("*********    Syncing Results (Added/Unchanged/Synced)    *************");
        logger.info("Editions: " + editionsAdded.size() + " / " + editionsUnchanged.size() + " / " + editionsSynced.size());
        logger.info("Organizations: " + organizationsAdded.size() + " / " + organizationsUnchanged.size() + " / " + organizationsSynced.size());
        logger.info("Refsets: " + refsetVersionsAdded.size() + " / " + refsetVersionsUnchanged.size() + " / " + refsetVersionsSynced.size());

    }

    protected static boolean updateAttribute(String attributeName, Object databaseAttribute, Object snowstormAttribute) {

        if (snowstormAttribute == null) {

            // Nothing to update if snowstorm is null
            return false;
        } else if (databaseAttribute == null) {

            // Handle inconsistent NULL
            logger.info(" inconsistent " + attributeName + " with DB value '" + databaseAttribute + "' and Snowstorm value '" + snowstormAttribute + "'");

            return true;

        }

        // Both have values, so compare
        if (databaseAttribute.equals(snowstormAttribute)) {

            return false;
        } else {

            if (databaseAttribute instanceof Long) {

                logger.info(" inconsistent " + attributeName + " with DB value '" + new Date((Long) databaseAttribute) + "' (" + databaseAttribute + ") and Snowstorm value '"
                    + new Date((Long) snowstormAttribute) + "' (" + snowstormAttribute + ")");
            } else {

                logger.info(" inconsistent " + attributeName + " with DB value '" + databaseAttribute + "' and Snowstorm value '" + snowstormAttribute + "'");
            }

            return true;
        }

    }

}