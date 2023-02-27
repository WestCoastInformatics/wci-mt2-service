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

import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.Organization;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.terminologyservice.SnowstormConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class SyncCodeSystemAgent extends SyncAgent {

    private static Logger logger = LoggerFactory.getLogger(SyncCodeSystemAgent.class);

    private static final String DEVELOPER_CODE_SYSTEM_SHORTNAME = "SNOMEDCT-WCI";

    private static final Set<String> codeSystemsNewAndInactive = new HashSet<>();

    public SyncCodeSystemAgent() throws Exception {

        codeSystemsNewAndInactive.clear();
    }

    public void sync() throws Exception {

        updateDatabaseCache();

        final JsonNode organizationJsonRootNode = getSnowstormCodeSystems();
        final Set<String> dbShortNames = new HashSet<>();
        final Set<String> activeSnowstormShortNames = new HashSet<>();

        // Count and filter code systems (filtering based on ignoredCS list and bad data)
        final Iterator<JsonNode> organizationIterator = organizationJsonRootNode.iterator();
        int codeSystemsReturned = countCodeSystems(organizationIterator);
        logger.info("Found " + codeSystemsReturned + " + Code Systems on Snowstorm: " + organizationJsonRootNode);

        Set<JsonNode> filteredCodeSystemsToProcess = filterCodeSystems(organizationJsonRootNode);
        logger.info("Will be processing only these " + filteredCodeSystemsToProcess.size() + " Code Systems: " + organizationJsonRootNode);
        statistics.setCodeSystemsSynced(codeSystemsReturned);
        statistics.setCodeSystemsFiltered(filteredCodeSystemsToProcess.size());

        // Identify new, removed, and existing codeSystems (Based on shortName)
        allDatabaseEditions.stream().forEach(e -> dbShortNames.add(e.getShortName()));
        filteredCodeSystemsToProcess.stream().filter(c -> (c.has("active") && c.get("active").asBoolean()) || !c.has("active"))
                .forEach(cs -> activeSnowstormShortNames.add(cs.get("shortName").asText()));

        List<String> newShortNames = activeSnowstormShortNames.stream().filter(c -> !dbShortNames.contains(c)).collect(Collectors.toList());
        List<String> removedShortNames = dbShortNames.stream().filter(c -> !activeSnowstormShortNames.contains(c)).collect(Collectors.toList());
        statistics.setEditionsAdded(newShortNames.size());
        statistics.setEditionsRemoved(removedShortNames.size());

        // Process each type of code system. First review existing so that anything changed will be deleted and recreated
        List<String> existingShortNames = dbShortNames.stream().filter(c -> activeSnowstormShortNames.contains(c)).collect(Collectors.toList());
        List<String> changedShortNames = reviewExistingCodeSystems(filteredCodeSystemsToProcess, existingShortNames);
        statistics.setEditionsUnchanged(existingShortNames.size() - changedShortNames.size());
        statistics.setEditionsRecreated(changedShortNames.size());

        newShortNames.addAll(changedShortNames);
        removedShortNames.addAll(changedShortNames);

        // Remove existing organizations
        filteredCodeSystemsToProcess.stream().filter(cs -> removedShortNames.contains(cs.get("shortName").asText())).forEach(matching -> {
            try {
                Edition edition = allDatabaseEditions.stream().filter(e -> e.getShortName().equals(matching)).collect(Collectors.toList()).iterator().next();
                utilities.removeEdition(edition);
            } catch (Exception e1) {
                logger.error("Failed to remove edition: " + matching);
            }
        });

        // Add new organizations via addCodeSystem()
        filteredCodeSystemsToProcess.stream().filter(cs -> newShortNames.contains(cs.get("shortName").asText())).forEach(matching -> addCodeSystem(matching));

        // TODO: For now, ignore this, but shouldn't ever throw exception at this point
        if (developerTestingEdition == null && !getIsProductionSystem()) {
            // throw new Exception("Must have a WCI Organization on a non-Prod instance");
        }

        // PostProcessing
        updateDatabaseCache();

        // Only identify branches on filtered code systems and on runShortSync value
        Map<String, SortedMap<Date, String>> editionBranchesToProcess = identifyEditionBranches(filteredCodeSystemsToProcess);
        editionsToProcess.putAll(editionBranchesToProcess);

        logger.info("Will be processing these " + editionsToProcess.keySet() + " edition-branches for refsets: " + editionsToProcess);
    }

    private int countCodeSystems(Iterator<JsonNode> organizationIterator) {
        int counter = 0;

        while (organizationIterator.hasNext()) {

            final Iterator<JsonNode> codeSystems = organizationIterator.next().iterator();

            while (codeSystems.hasNext()) {

                counter++;
                codeSystems.next();
            }

        }

        return counter;
    }

    /**
     * Identify branches.
     *
     * @return the map
     * @throws Exception the exception
     */
    private Map<String, SortedMap<Date, String>> identifyEditionBranches(Set<JsonNode> codeSystems) throws Exception {

        Map<String, SortedMap<Date, String>> retMap = new HashMap<>();
        logger.info("Database editions already in DB at start of sync in identifyEditionBranches() are: ");
        allDatabaseEditions.stream().forEach(e -> logger.info(e.getName()));

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

            retMap.put(shortName, children);
        }

        return retMap;
    }

    private List<String> reviewExistingCodeSystems(Set<JsonNode> filteredCodeSystemsToProcess, List<String> newShortNames) throws Exception {
        List<String> modifiedShortNames = new ArrayList<>();

        // syncedEdition = handleExistingCodeSystem(correspondingDbEdition, snowstormEditionShortName, snowstormEditionName, snowstormEditionBranch, isActiveSnowstormEdition,
        // codeSystem);

        // Process one Organization per Edition.
        for (JsonNode codeSystem : filteredCodeSystemsToProcess) {
            if (newShortNames.contains(codeSystem.get("shortName").asText())) {

                final String shortName = codeSystem.has("shortName") ? codeSystem.get("shortName").asText() : "";
                final String editionName = codeSystem.has("name") ? codeSystem.get("name").asText() : "";
                final String branch = codeSystem.has("branchPath") ? codeSystem.get("branchPath").asText() : "";
                final String maintainerType = identifyMaintainerType(codeSystem, editionName);
                final Set<String> editionModules = utilities.identifyModules(shortName, editionName, branch, codeSystem);

                Edition dbEdition = allDatabaseEditions.stream().filter(e -> e.getShortName().equals(codeSystem.get("shortName").asText())).collect(Collectors.toList()).iterator().next();

                if (isDifferentAttribute(dbEdition.getShortName(), "Edition name ", dbEdition.getName(), editionName)
                        || isDifferentAttribute(dbEdition.getShortName(), "Edition branch ", dbEdition.getBranch(), branch)
                        || isDifferentAttribute(dbEdition.getShortName(), "Edition modules ", dbEdition.getModules(), editionModules)
                        || isDifferentAttribute(dbEdition.getShortName(), "Edition maintainerType ", dbEdition.getOrganization().getCodeSystemType(), maintainerType) || isDifferentAttribute(
                                dbEdition.getShortName(), "Edition defaultLanguageCode ", dbEdition.getDefaultLanguageCode(), utilities.identifyDefaultLanguageCode(codeSystem, editionName))) {
                    modifiedShortNames.add(shortName);
                    continue;
                }

                final Set<String> editionDefaultLanguageRefsets = utilities.identifyDefaultLanguageRefsets(codeSystem, shortName);

                if (!dbEdition.getDefaultLanguageRefsets().equals(editionDefaultLanguageRefsets)) {

                    if (!dbEdition.getDefaultLanguageRefsets().isEmpty() && editionDefaultLanguageRefsets.isEmpty()) {

                        logger.info(" False-Positive inconsistent Edition defaultLanguageRefsets with '" + dbEdition.getDefaultLanguageRefsets() + "' and '" + editionDefaultLanguageRefsets + "'");
                    } else {

                        logger.info(" inconsistent Edition defaultLanguageRefsets with '" + dbEdition.getDefaultLanguageRefsets() + "' and '" + editionDefaultLanguageRefsets + "'");

                        modifiedShortNames.add(shortName);
                        continue;
                    }

                }
            }
        }

        modifiedShortNames.stream().forEach(n -> logger.info("Removing and re-adding edition: " + n));

        return modifiedShortNames;
    }

    private Edition addCodeSystem(JsonNode codeSystem) {
        try {
            final String shortName = codeSystem.has("shortName") ? codeSystem.get("shortName").asText() : "";
            final String editionName = codeSystem.has("name") ? codeSystem.get("name").asText() : "";
            final String branch = codeSystem.has("branchPath") ? codeSystem.get("branchPath").asText() : "";
            final String maintainerType = identifyMaintainerType(codeSystem, editionName);

            // If organization doesn't already exist (based on name), create it
            setSnowstormEditionOwner(shortName, editionName, codeSystem);

            Organization organization = identifyMatchingOrganization(shortName);
            if (organization != null) {
                statistics.incrementOrganizationsUnchanged();
            } else {
                statistics.incrementOrganizationsAdded();

                organization = createOrganization(shortName, maintainerType);
            }

            // Create a single Admin team per Edition when we first discover it
            final SyncOperationsInitializer initializer = new SyncOperationsInitializer(utilities);
            initializer.createAdminOrganizationTeam(organization);

            final Edition newEdition = utilities.addEdition(shortName, editionName, branch, organization, codeSystem);
            utilities.printEditionValues(newEdition);

            postCodeSystemProcessing(newEdition);

            return newEdition;
        } catch (Exception e) {

            logger.error("Failed in syncing New Snowstorm Code System: " + codeSystem);
            e.printStackTrace();

            return null;
        }

    }

    /*
     * See if organization with the name provided already exists. If so, return it. If not, create and then return.
     */
    private Organization identifyMatchingOrganization(String editionShortName) throws Exception {

        // Determine Owner
        List<Organization> organizations = allDatabaseOrganizations.stream().filter(o -> o.getName().equals(editionOwnerMap.get(editionShortName))).collect(Collectors.toList());

        if (organizations.isEmpty()) {
            // TODO: If was once there but not, do we remove owner?

            // First time seeing owner
            return null;
        } else if (organizations.size() > 1) {

            throw new Exception("Cannot have multiple orgs with same name: " + editionOwnerMap.get(editionShortName));
        }

        // Found existing owner
        statistics.incrementOrganizationsUnchanged();
        return organizations.iterator().next();
    }

    private Organization createOrganization(String editionShortName, String organizationMaintainerType) throws Exception {

        // Create new organization
        // TODO: 1 - Add a description default value or update snowstorm with value per codesystem
        final String organizationName = editionOwnerMap.get(editionShortName);
        final String organizationDescription = ownerDescriptionMap.get(organizationName);

        Organization organization = utilities.addOrganziation(organizationName, organizationDescription, organizationMaintainerType);

        allDatabaseOrganizations.add(organization);

        logger.info("Created Organization: " + organization.getName());

        return organization;
    }

    private void setSnowstormEditionOwner(String editionShortName, String editionName, JsonNode codeSystem) {
        String description;
        String owner;

        // Identify Code System Owner
        if (codeSystem.has("owner") && !codeSystem.get("owner").asText().trim().isBlank()) {
            owner = codeSystem.get("owner").asText();
            description = "Organizational administrators can update this edition's default description.";
        } else {
            owner = "Owner of " + editionName;
            description = "Two things to change." + System.lineSeparator()
                    + "1) Your organization name isn't defined on Snowstorm yet, so we have provided you with a temporary one that matches your edition name." + System.lineSeparator()
                    + "Have your organization's administrator(s) contact SNOMED International to have it changed." + System.lineSeparator()
                    + "2) Organizational administrator(s) can update this default description at any time";
        }

        editionOwnerMap.put(editionShortName, owner);

        if (!ownerDescriptionMap.containsKey(owner)) {

            ownerDescriptionMap.put(owner, description);
        }

    }

    private Set<JsonNode> filterCodeSystems(JsonNode organizationJsonRootNode) throws Exception {

        final Set<JsonNode> filteredCodeSystems = new HashSet<>();

        final Iterator<JsonNode> organizationIterator = organizationJsonRootNode.iterator();

        while (organizationIterator.hasNext()) {

            final Iterator<JsonNode> codeSystems = organizationIterator.next().iterator();

            while (codeSystems.hasNext()) {

                JsonNode codeSystem = codeSystems.next();

                // Check for invalid or ignored code systems
                if (!codeSystem.has("shortName")) {

                    logger.error("Encountered codeSystem without a shortName: " + codeSystem);
                    // Skipping odd code system without a shortName
                    continue;
                }

                final String editionShortName = codeSystem.get("shortName").asText();

                // Testing
                if (isEditionToProcess(editionShortName)) {
                    final String maintainerType = identifyMaintainerType(codeSystem, editionShortName);

                    if (!maintainerType.equalsIgnoreCase("Managed Service")) {

                        // For now, only supportCode Managed Service
                        logger.info("Ignoring codesystem " + editionShortName + " as is of maintainerType: " + maintainerType);
                        continue;

                    } else if (utilities.getPropertyReader().getCodeSystemsToIgnore().contains(editionShortName)) {

                        // Code System has been defined as to-be-ignored (either by specifying name or shortname)
                        logger.info("Ignoring codesystem " + editionShortName + " as it's listed in ignoredCodeSystems.txt");
                        continue;
                    }

                    filteredCodeSystems.add(codeSystem);
                } else {
                    logger.info("Ignoring codesystem " + editionShortName + " as it failed isEditionToProcess()");
                }

            }

        }

        filteredCodeSystems.stream().forEach(c -> logger.info("Will process codeSystem: " + c));

        return filteredCodeSystems;
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
        logger.info("getSnowstormCodeSystems url: " + url);

        try (final Response response = SnowstormConnection.getResponse(url)) {

            final String resultString = response.readEntity(String.class);

            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode organizationJsonRootNode = mapper.readTree(resultString.toString());

            return organizationJsonRootNode;
        }

    }

    private String identifyMaintainerType(JsonNode codeSystem, String editionShortName) throws Exception {

        String codeSystemType = codeSystem.has("maintainerType") ? codeSystem.get("maintainerType").asText() : "";

        // SNOMED Core Edition are blank in Snowstorm, but we treat them identically to the Managed Service maintainerType
        if (codeSystemType.isBlank()) {

            if (utilities.isInternationalEdition(editionShortName)) {

                codeSystemType = "Managed Service";
            } else {

                throw new Exception("Encountered non-CORE edition without a maintainerType specified in the corresponding Code System");
            }

        }

        return codeSystemType;
    }

    // Organization is done at this point. Check if Developer Edition. If not, create a default UAT project
    private void postCodeSystemProcessing(Edition syncedEdition) throws Exception {

        if (DEVELOPER_CODE_SYSTEM_SHORTNAME.equalsIgnoreCase(syncedEdition.getShortName())) {

            // Support Developer Edition
            if (getIsProductionSystem()) {

                throw new Exception("Can't have a WCI Edition on a Prod instance");
            }

            if (developerTestingEdition != null) {

                throw new Exception("Can't have two WCI Editions with new one having shortName: " + syncedEdition.getShortName());
            } else {

                // identified WCI Edition
                developerTestingEdition = syncedEdition;
            }

        } else {

            // Create a Default Project for the edition
            if (!defaultEditionProjects.containsKey(syncedEdition.getShortName())) {

                final String projectName = syncedEdition.getName() + " Default Project";
                final String projectDescription =
                        "This is a project to support all refsets not already associated with a project in the Refset & Translation Tool for " + syncedEdition.getName() + ".";

                // Create default project
                final Project project = utilities.addProject(projectName, projectDescription, syncedEdition);
                defaultEditionProjects.put(syncedEdition.getShortName(), project);
            }

        }

    }

    protected Edition getDeveloperTestingEdition() {

        return developerTestingEdition;
    }

    private boolean isEditionToProcess(String codeSystem) {

        return !isTesting() || (isTesting() && (testingEditionShortName == null || testingEditionShortName.isEmpty()) || codeSystem.equalsIgnoreCase(testingEditionShortName)
                || utilities.isInternationalEdition(codeSystem));

    }
}