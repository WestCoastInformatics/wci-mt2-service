package org.ihtsdo.refsetservice.sync;

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
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.terminologyservice.SnowstormConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class SyncCodeSystemAgent extends SyncService {

    private static Logger logger = LoggerFactory.getLogger(SyncCodeSystemAgent.class);

    private static final String DEVELOPER_CODE_SYSTEM_SHORTNAME = "SNOMEDCT-WCI";

    private static final Set<String> codeSystemsNewAndInactive = new HashSet<>();

    private static final SyncOperationsInitializer initializer = new SyncOperationsInitializer();

    public SyncCodeSystemAgent() throws Exception {

        codeSystemsNewAndInactive.clear();
    }

    protected Edition getDeveloperTestingEdition() {

        return developerTestingEdition;
    }

    public void syncSnowstorm() throws Exception {

        clearPreviousRun();
        updateDatabaseCache();

        final JsonNode organizationJsonRootNode = getSnowstormCodeSystems();

        final Iterator<JsonNode> organizationIterator = organizationJsonRootNode.iterator();

        int counter = 0;

        while (organizationIterator.hasNext()) {

            final Iterator<JsonNode> codeSystems = organizationIterator.next().iterator();

            while (codeSystems.hasNext()) {

                counter++;
                codeSystems.next();
            }

        }

        logger.info("Found " + counter + " + Code Systems on Snowstorm: " + organizationJsonRootNode);
        Set<JsonNode> codeSystemsToProcess = filterCodeSystems(organizationJsonRootNode);
        logger.info("Will be processing only these " + codeSystemsToProcess.size() + " Code Systems: " + organizationJsonRootNode);

        for (JsonNode codeSystem : codeSystemsToProcess) {
            // Simplified approach is to not consider at this point if new edition was created or a new one was discovered

            syncSingleSnowstormCodeSystem(codeSystem);

        }

        if (developerTestingEdition == null && !forProduction) {

            // TODO: For now, ignore this, but shouldn't ever throw exception at this point
            // throw new Exception("Must have a WCI Organization on a non-Prod instance");
        }

        updateDatabaseCache();

        // Only identify branches on filtered code systems and on runShortSync value
        Map<String, SortedMap<Date, String>> editionBranchesToProcess = identifyEditionBranches(codeSystemsToProcess);
        branchesToProcess.putAll(editionBranchesToProcess);

        logger.info("Will be processing these " + branchesToProcess.keySet() + " edition-branches for refsets: " + branchesToProcess);
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
        allDatabaseEditions.stream().forEach(e -> logger.debug(e.getName()));

        for (JsonNode codeSystem : codeSystems) {

            final String editionName = codeSystem.has("name") ? codeSystem.get("name").asText() : "";
            final String shortName = codeSystem.has("shortName") ? codeSystem.get("shortName").asText() : "";
            final String branch = codeSystem.has("branchPath") ? codeSystem.get("branchPath").asText() : "";

            logger.info("Identifying CodeSystem branches for: " + editionName);

            final String genericUrl = SnowstormConnection.BASE_URL + "branches/{branch}/children";

            SortedMap<Date, String> children = new TreeMap<>();
            logger.debug(" genericUrl: " + genericUrl.replace("{branch}", branch));

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

                    // logger.debug(" Found Snowstorm Child Branch: " + childBranch);

                    // Since grabbing all children branches, avoid
                    // attempting to parse extensions i.e. MAIN/SNOMEDCT-US
                    boolean childAdded = false;

                    if (childDate.matches(".*\\d{4}-\\d{2}-\\d{2}$")) {
//                        if (childDate.matches("^\\d{4}-\\d{2}-\\d{2}$")) {

                        Date branchDate = branchDateFormatter.parse(childDate);

                        if (branchDate.before(new Date())) {

                            children.put(branchDate, childBranch);
                            childAdded = true;
                        }

                    } else {
                        logger.info("Ignoring branch as doesn't comply with expected format (where final item in path is a date in format yyyy-mm-dd: " + childDate);
                    }


                    if (!childAdded) {

                        // logger.info("Skipping over childBranch/branchDate pair " + edition.getBranch() + "/" + childDate + " as the branch isn't an official release
                        // branch");
                    }

                }

                logger.debug("Branch Dates for edition: " + editionName);

                for (Date child : children.keySet()) {

                    logger.debug("Child: " + child.toString() + " with branch: " + children.get(child));
                }

            }

            retMap.put(shortName, children);
        }

        return retMap;
    }

    /*-
     * See if corresponding Edition exists in RT2 DB. If not, create it if it isn't inactive. 
     * 
     * 
     * Match by Organization::Edition::id to match against all Orgs in the DB. If not successful, try name, and finally try branch. If nothing found, is new Edition.
     * 
     * For now, only must identify if there) are changes to any of the following object values during sync: 
     * 1) ShortName
     * 2) Name 
     * 3) Branch
     * 4) Active/inactive status
     * 5) defaultLanguageCode
     * 6) topLevelModule
     * 7) defaultLanguageRefsets
     */
    private void syncSingleSnowstormCodeSystem(JsonNode codeSystem) {

        Edition syncedEdition = null;

        try {

            /* identify comparison attributes */
            final String snowstormEditionShortName = codeSystem.has("shortName") ? codeSystem.get("shortName").asText() : "";
            final String snowstormEditionName = codeSystem.has("name") ? codeSystem.get("name").asText() : "";
            final String snowstormEditionBranch = codeSystem.has("branchPath") ? codeSystem.get("branchPath").asText() : "";
            final boolean isActiveSnowstormEdition = codeSystem.has("active") ? codeSystem.get("active").asBoolean() : true;
            final String snowstormMaintainerType = identifyMaintainerType(codeSystem, snowstormEditionShortName);

            logger.info(" Syncing Code System: " + generateCodeSystemCoordinates(snowstormEditionShortName, snowstormEditionName, snowstormEditionBranch));

            /* See if corresponding Edition exists in RT2 DB. If not, create it. */
            List<Edition> dbEditions = allDatabaseEditions.stream().filter(e -> e != null && e.getShortName().equals(snowstormEditionShortName)).collect(Collectors.toList());

            if (dbEditions.size() > 1) {

                throw new Exception("Have encounted multiple editions with the same shortName on Snowstorm: " + dbEditions);
            } else if (dbEditions == null || dbEditions.isEmpty()) {

                // If correspondingDbEdition is null, this is the first time we have observed this edition, so create it.
                syncedEdition = handleNewCodeSystem(snowstormEditionShortName, snowstormEditionName, snowstormEditionBranch, isActiveSnowstormEdition, snowstormMaintainerType, codeSystem);

                statistics.getEditionsAdded().add(syncedEdition);
                allDatabaseEditions.add(syncedEdition);

            } else {

                // If correspondingDbEdition is not null, we are updating an existing supported edition
                final Edition correspondingDbEdition = dbEditions.iterator().next();

                syncedEdition = handleExistingCodeSystem(correspondingDbEdition, snowstormEditionShortName, snowstormEditionName, snowstormEditionBranch, isActiveSnowstormEdition, codeSystem);

            }

            // TODO: See if any persisted Editions or Orgs are not even in Snowstorm. If so, inactivate

            // Final steps whether initial or updating sync
            if (syncedEdition != null) {

                postCodeSystemProcessing(syncedEdition);
            }

        } catch (Exception e) {

            logger.error("Failed in syncing Snowstorm Code System: " + codeSystem);
            e.printStackTrace();
        }

    }

    private Edition handleExistingCodeSystem(Edition edition, String snowstormEditionShortName, String snowstormEditionName, String snowstormEditionBranch, boolean isActiveSsnowstormEdition,
        JsonNode codeSystem) {

        logger.info(" Sync existing Edition with shortName: " + snowstormEditionShortName);

        Edition retEdition = null;
        // Remove edition now and replace regardless of outcome
        allDatabaseEditions.remove(edition);

        try {

            // Process one Organization per Edition.
            final Edition syncedEdition = compareAndUpdateEditionDifferences(edition, snowstormEditionShortName, snowstormEditionName, snowstormEditionBranch, isActiveSsnowstormEdition, codeSystem);

            if (syncedEdition == null) {

                // No differences found in edition, but check Owner value as well
                retEdition = edition;
                statistics.getEditionsUnchanged().add(retEdition);

            } else {

                // Differences found in edition
                retEdition = syncedEdition;
                statistics.getEditionsSynced().add(retEdition);

            }

            logger.info("Synced " + retEdition.getShortName() + " Edition");
            allDatabaseEditions.add(retEdition);

            handleOrganizationForExistingEdition(retEdition, isActiveSsnowstormEdition, codeSystem);

            return retEdition;
        } catch (Exception e) {

            logger.error("Failed in syncing Existing Snowstorm Code System: " + codeSystem);
            e.printStackTrace();

            return null;
        }

    }

    private void handleOrganizationForExistingEdition(Edition edition, boolean isActiveSnowstormEdition, JsonNode codeSystem) throws Exception {

        /*-
         * For testing orgs
         * 
         * 
               String snowstormOrganizationName = edition.getShortName().equals(DEVELOPER_CODE_SYSTEM_SHORTNAME) ? "" : "testOrg";
         */
        setSnowstormEditionOwner(edition.getShortName(), edition.getName(), codeSystem);

        final String snowstormEditionShortName = codeSystem.has("shortName") ? codeSystem.get("shortName").asText() : "";
        final String snowstormMaintainerType = identifyMaintainerType(codeSystem, snowstormEditionShortName);

        if (snowstormEditionShortName.isBlank()) {

            statistics.getOrganizationsUnchanged().add(edition.getOrganization());
            // Nothing to change given this edition already exists.
            // In fact, don't even bother to see if editionName matches OrgName as a determination if something has changed. We will pick it up when next popualated
            return;
        }

        Organization matchingDatabaseOrganization = identifyMatchingOrganization(snowstormEditionShortName);

        if (matchingDatabaseOrganization == null) {

            // The Code System owner doesn't exist yet in system, so create org
            createOrganization(snowstormEditionShortName, snowstormMaintainerType);

        } else if (matchingDatabaseOrganization.getId() != edition.getOrganizationId()) {

            // Just reassigning org, not changing it to an another existing one. So consider org unchanged here.
            statistics.getOrganizationsUnchanged().add(matchingDatabaseOrganization);

            // Org name exists, but it's different than what it was previously
            edition.setOrganization(matchingDatabaseOrganization);

            try (final TerminologyService service = new TerminologyService()) {

                utilities.initializeService(service);

                service.update(edition);

                if (statistics.getEditionsUnchanged().contains(edition)) {

                    statistics.getEditionsSynced().add(edition);
                    statistics.getEditionsUnchanged().remove(edition);
                }

            }

        } else {

            final String snowstormOrganizationName = codeSystem.has("owner") ? codeSystem.get("owner").asText() : "";

            // Found matching org with same orgId as before. Now compare differences (although for now none exist, put in placeholder to expand as needed)
            allDatabaseOrganizations.remove(matchingDatabaseOrganization);

            // Process one Organization per Edition.
            final Organization syncedOrganization = compareAndUpdateOrganizationDifferences(matchingDatabaseOrganization, snowstormOrganizationName, isActiveSnowstormEdition);
            Organization retOrganization;

            if (syncedOrganization == null) {

                // No differences found in edition, but check Owner value as well
                retOrganization = matchingDatabaseOrganization;
                statistics.getOrganizationsUnchanged().add(retOrganization);

            } else {

                // Differences found in edition
                retOrganization = syncedOrganization;
                statistics.getOrganizationsSynced().add(retOrganization);

            }

            logger.info("Synced Org: " + retOrganization.getName());
            allDatabaseOrganizations.add(retOrganization);

        }

    }

    private Edition compareAndUpdateEditionDifferences(Edition existingEdition, String editionShortName, String editionName, String editionBranch, boolean isActiveEdition, JsonNode codeSystem)
        throws Exception {

        /* Found existing Edition. Compare the values to determine if something changed, and if so, update the edition accordingly */
        boolean modificationMade = false;

        // TODO: This is immutable, so nothing to check?
        if (updateAttribute("Edition shortName ", existingEdition.getShortName(), editionShortName)) {

            existingEdition.setShortName(editionShortName);
            modificationMade = true;
        }

        // TODO: This is immutable, so nothing to check?
        if (updateAttribute("Edition name ", existingEdition.getName(), editionName)) {

            existingEdition.setName(editionName);
            modificationMade = true;
        }

        // TODO: This is immutable, so nothing to check?
        if (updateAttribute("Edition branch ", existingEdition.getBranch(), editionBranch)) {

            existingEdition.setBranch(editionBranch);
            modificationMade = true;
        }

        if (updateAttribute("Edition active ", existingEdition.isActive(), isActiveEdition)) {

            existingEdition.setActive(isActiveEdition);
            modificationMade = true;
        }

        final Set<String> editionModules = utilities.identifyModules(editionShortName, editionName, editionBranch, codeSystem);

        // TODO: Can we remove topLevelModule?
        if (updateAttribute("Edition modules ", existingEdition.getModules(), editionModules)) {

            existingEdition.setModules(editionModules);
            modificationMade = true;
        }

        final String editionDefaultLanguageCode = utilities.identifyDefaultLanguageCode(codeSystem, editionName);

        if (updateAttribute("Edition defaultLanguageCode ", existingEdition.getDefaultLanguageCode(), editionDefaultLanguageCode)) {

            existingEdition.setDefaultLanguageCode(editionDefaultLanguageCode);
            modificationMade = true;
        }

        final Set<String> editionDefaultLanguageRefsets = utilities.identifyDefaultLanguageRefsets(codeSystem, editionShortName);

        if (!existingEdition.getDefaultLanguageRefsets().equals(editionDefaultLanguageRefsets)) {

            if (!existingEdition.getDefaultLanguageRefsets().isEmpty() && editionDefaultLanguageRefsets.isEmpty()) {

                logger.info(" False-Positive inconsistent Edition defaultLanguageRefsets with '" + existingEdition.getDefaultLanguageRefsets() + "' and '" + editionDefaultLanguageRefsets + "'");
            } else {

                logger.info(" inconsistent Edition defaultLanguageRefsets with '" + existingEdition.getDefaultLanguageRefsets() + "' and '" + editionDefaultLanguageRefsets + "'");

                existingEdition.setDefaultLanguageRefsets(editionDefaultLanguageRefsets);
                modificationMade = true;
            }

        }

        /* Update if difference identified during comparison */
        if (modificationMade) {

            // A modification was made, so updated edition
            try (TerminologyService service = new TerminologyService()) {

                utilities.initializeService(service);

                Edition syncedEdition = service.update(existingEdition);

                return syncedEdition;
            }

        } else {

            return null;
        }

    }

    private Edition handleNewCodeSystem(String newCodeSystemShortName, String newEditionName, String newEditionBranch, boolean isNewActiveEdition, String snowstormMaintainerType, JsonNode codeSystem)
        throws Exception {

        try {

            if (!isNewActiveEdition) {

                // New Code System created as inactive. Given this is being run nightly and a new org/edition that is inactive at first pass was likely made erroneously.
                // Once fixed and becomes active, we will get it at the following sync. For now, don't add to retSet
                String codeSystemCoordinates = generateCodeSystemCoordinates(newCodeSystemShortName, newEditionName, newEditionBranch);
                codeSystemsNewAndInactive.add(codeSystemCoordinates);

                return null;
            }

            // If organization doesn't already exist (based on name), create it
            setSnowstormEditionOwner(newCodeSystemShortName, newEditionName, codeSystem);

            Organization organization = identifyMatchingOrganization(newCodeSystemShortName);

            if (organization == null) {

                organization = createOrganization(newCodeSystemShortName, snowstormMaintainerType);
            }

            // Create a single Admin team per Edition when we first discover it
            initializer.createAdminOrganizationTeam(organization);

            final Edition newEdition = utilities.addEdition(newCodeSystemShortName, newEditionName, newEditionBranch, organization, codeSystem);
            utilities.printEditionValues(newEdition);

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

            return null;
        } else if (organizations.size() == 1) {

            organizations.iterator().next();
        } else {

            throw new Exception("Cannot have multiple orgs with same name: " + editionOwnerMap.get(editionShortName));
        }

        return organizations.iterator().next();
    }

    private Organization createOrganization(String editionShortName, String organizationMaintainerType) throws Exception {

        // Create new organization
        // TODO: 1 - Add a description default value or update snowstorm with value per codesystem
        final String organizationName = editionOwnerMap.get(editionShortName);
        final String organizationDescription = ownerDescriptionMap.get(organizationName);

        Organization organization = utilities.addOrganziation(organizationName, organizationDescription, organizationMaintainerType);

        statistics.getOrganizationsAdded().put(organization.getName(), organization);
        allDatabaseOrganizations.add(organization);

        logger.info("Created Organization: " + organization.getName());

        return organization;
    }

    // Organization is done at this point. Check if Developer Edition. If not, create a default UAT project

    private void postCodeSystemProcessing(Edition syncedEdition) throws Exception {

        if (DEVELOPER_CODE_SYSTEM_SHORTNAME.equalsIgnoreCase(syncedEdition.getShortName())) {

            // Support Developer Edition
            if (forProduction) {

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

    private String generateCodeSystemCoordinates(String snowstormEditionShortName, String snowstormEditionName, String snowstormEditionBranch) {

        return snowstormEditionShortName + " / " + snowstormEditionName + " / " + snowstormEditionBranch;
    }

    private void setSnowstormEditionOwner(String editionShortName, String editionName, JsonNode codeSystem) {

        /*-
         * For testing orgs
        // final String owner = codeSystem.has("owner") ? codeSystem.get("owner").asText() : "";
        String owner = "testOrg";
        
        if (editionShortName.equals(DEVELOPER_CODE_SYSTEM_SHORTNAME)) {
        
            owner = codeSystem.has("owner") ? codeSystem.get("owner").asText() : "";
        }
        */
        String owner = codeSystem.has("owner") ? codeSystem.get("owner").asText() : "";
        String description;

        // Identify Code System Owner

        if (!owner.trim().isBlank()) {

            description = "Organizational administrators can update this default description.";
        } else {

            owner = editionName;
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

    private Organization compareAndUpdateOrganizationDifferences(Organization existingOrganization, String snowstormOrganizationName, boolean isActiveSnowstormOrganization) throws Exception {

        /*
         * Found existing Organization, but for now, name will be handled as unique identifier. Compare the values to determine if something changed, and if so, update the
         * Organization accordingly
         */
        boolean modificationMade = false;

        if (!snowstormOrganizationName.isBlank() && updateAttribute("Organization name ", existingOrganization.getName(), snowstormOrganizationName)) {

            existingOrganization.setName(snowstormOrganizationName);
            modificationMade = true;
        }

        // TODO: This is associated with CodeSystem (and thus edition), so remove?
        if (updateAttribute("Organization active ", existingOrganization.isActive(), isActiveSnowstormOrganization)) {

            existingOrganization.setActive(isActiveSnowstormOrganization);
            modificationMade = true;
        }

        if (modificationMade) {

            try (final TerminologyService service = new TerminologyService()) {

                utilities.initializeService(service);

                return service.update(existingOrganization);
            }

        } else {

            return null;
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
                final String maintainerType = identifyMaintainerType(codeSystem, editionShortName);

                if (!maintainerType.equalsIgnoreCase("Managed Service")) {

                    //  For now, only supportCode Managed Service
                    logger.info("Ignoring codesystem " + editionShortName + " as is of maintainerType: " + maintainerType);
                    continue;

                } else if (utilities.getPropertyReader().getCodeSystemsToIgnore().contains(editionShortName)) {

                    // Code System has been defined as to-be-ignored (either by specifying name or shortname)
                    logger.info("Ignoring codesystem " + editionShortName + " as it's listed in ignoredCodeSystems.txt");
                    continue;
                }

                // Testing
                if (isEditionToProcess(editionShortName)) {

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
        logger.debug("getSnowstormCodeSystems url: " + url);

        try (final Response response = SnowstormConnection.getResponse(url)) {

            final String resultString = response.readEntity(String.class);

            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode organizationJsonRootNode = mapper.readTree(resultString.toString());

            identifyInternationalModules(organizationJsonRootNode);

            return organizationJsonRootNode;
        }

    }

    private static void identifyInternationalModules(JsonNode root) throws Exception {

        final Iterator<JsonNode> responseIterator = root.iterator();

        while (responseIterator.hasNext()) {

            final Iterator<JsonNode> codeSystems = responseIterator.next().iterator();

            while (codeSystems.hasNext()) {

                JsonNode codeSystem = codeSystems.next();

                if (!codeSystem.has("name")) {

                    continue;
                }

                if (utilities.isInternationalEdition(codeSystem.get("name").asText())) {

                    // At international Edition
                    Iterator<JsonNode> moduleIterator = codeSystem.get("modules").iterator();

                    while (moduleIterator.hasNext()) {

                        JsonNode module = moduleIterator.next();

                        // TODO: Is this if-statement necessary?
                        if (ignoreCoreRefsets && module.has("conceptId") && !module.get("conceptId").asText().equals("449080006"))
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

    private boolean isEditionToProcess(String codeSystem) {

        return !isTesting() || (isTesting() && (testingEditionShortName == null || testingEditionShortName.isEmpty()) || codeSystem.equalsIgnoreCase(testingEditionShortName) || utilities.isInternationalEdition(codeSystem));

    }
}