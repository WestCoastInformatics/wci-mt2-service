package org.ihtsdo.refsetservice.sync;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
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

public class SyncCodeSystemAgent extends SyncAgent {

    private static Logger logger = LoggerFactory.getLogger(SyncCodeSystemAgent.class);

    private static final String DEVELOPER_CODE_SYSTEM_SHORTNAME = "SNOMEDCT-WCI";

    private static final Set<String> codeSystemsNewAndInactive = new HashSet<>();

    protected SyncCodeSystemAgent() throws Exception {

        super();

        codeSystemsNewAndInactive.clear();
    }

    protected static Edition getDeveloperTestingEdition() {

        return develeperTestingEdition;
    }

    protected static Set<JsonNode> syncSnowstormCodeSystems() throws Exception {

        Set<JsonNode> codeSystemsToProcess = filterCodeSystems();

        // Clear this out to validate the developer code system
        develeperTestingEdition = null;

        for (JsonNode codeSystem : codeSystemsToProcess) {

            // Simplified approach is to not consider at this point if new edition was created or a new one was discovered

            syncSingleSnowstormCodeSystem(codeSystem);

        }

        if (develeperTestingEdition == null && !forProduction) {

            // TODO: For now, ignore this, but shuolldn't ever throw exception at this point
            // throw new Exception("Must have a WCI Organization on a non-Prod instance");
        }

        updateDatabaseCache();

        return codeSystemsToProcess;
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
    private static void syncSingleSnowstormCodeSystem(JsonNode codeSystem) {

        Edition syncedEdition = null;

        try {

            /* identify comparison attributes */
            final String snowstormEditionShortName = codeSystem.has("shortName") ? codeSystem.get("shortName").asText() : "";
            final String snowstormEditionName = codeSystem.has("name") ? codeSystem.get("name").asText() : "";
            final String snowstormEditionBranch = codeSystem.has("branchPath") ? codeSystem.get("branchPath").asText() : "";
            final boolean isActiveSnowstormEdition = codeSystem.has("active") ? codeSystem.get("active").asBoolean() : true;

            logger.info(" Syncing Code System: " + generateCodeSystemCoordinates(snowstormEditionShortName, snowstormEditionName, snowstormEditionBranch));

            /* See if corresponding Edition exists in RT2 DB. If not, create it. */
            List<Edition> dbEditions = allDatabaseEditions.stream().filter(e -> e.getShortName().equals(snowstormEditionShortName)).collect(Collectors.toList());

            if (dbEditions == null || dbEditions.isEmpty()) {

                // If correspondingDbEdition is not null, we are updating an existing supported edition
                syncedEdition = handleNewCodeSystem(snowstormEditionShortName, snowstormEditionName, snowstormEditionBranch, isActiveSnowstormEdition, codeSystem);

            } else {

                if (dbEditions.size() != 1) {

                    throw new Exception("Have encounted two editions with the same shortName on Snowstorm: " + dbEditions);
                }

                final Edition correspondingDbEdition = dbEditions.iterator().next();

                // If correspondingDbEdition is null, this is the first time we have observed this edition, so create it.
                syncedEdition = handleExistingCodeSystem(correspondingDbEdition, snowstormEditionShortName, snowstormEditionName, snowstormEditionBranch, isActiveSnowstormEdition, codeSystem);

            }

            // TODO: See if any persisted Editions or Orgs are not even in Snowstorm. If so, inactivate

            // Final steps whether initial or updating sync
            postCodeSystemProcessing(snowstormEditionShortName, syncedEdition);

        } catch (Exception e) {

            logger.error("Failed in syncing Snowstorm Code System: " + codeSystem);
            e.printStackTrace();
        }

    }

    private static Edition handleExistingCodeSystem(Edition edition, String snowstormEditionShortName, String snowstormEditionName, String snowstormEditionBranch, boolean isActiveSsnowstormEdition,
        JsonNode codeSystem) {

        logger.info(" Sync existing Edition with shortName: " + snowstormEditionShortName);

        Edition retEdition = null;

        try {

            // Process one Organization per Edition.
            final Edition syncedEdition = handleExistingEdition(edition, snowstormEditionShortName, snowstormEditionName, snowstormEditionBranch, isActiveSsnowstormEdition, codeSystem);

            if (syncedEdition != null) {

                // Differences found in edition
                retEdition = syncedEdition;
            } else {

                // No differences found in edition, but check Owner value as well
                retEdition = edition;
            }

            setSnowstormEditionOwner(retEdition.getShortName(), retEdition.getName(), codeSystem);

            handleOrganizationForExistingEdition(retEdition, isActiveSsnowstormEdition, codeSystem);

            return retEdition;
        } catch (Exception e) {

            logger.error("Failed in syncing Existing Snowstorm Code System: " + codeSystem);
            e.printStackTrace();

            return null;
        }

    }

    private static void handleOrganizationForExistingEdition(Edition edition, boolean isActiveSnowstormEdition, JsonNode codeSystem) throws Exception {
        /*-
         * For testing orgs
         * 
         * 
               String snowstormOrganizationName = edition.getShortName().equals(DEVELOPER_CODE_SYSTEM_SHORTNAME) ? "" : "testOrg";
         */

        String snowstormOrganizationName = codeSystem.has("owner") && !codeSystem.get("owner").asText().trim().isBlank() ? codeSystem.get("owner").asText() : "";

        if (snowstormOrganizationName.isBlank()) {

            // Nothing to change given this edition already exists.
            // In fact, don't even bother to see if editionName matches OrgName as a determination if something has changed. We will pick it up when next popualated
            return;
        }

        // Can only match on name attribute as no other field in Snowstorm.CodeSystem as of yet
        List<Organization> organizations = allDatabaseOrganizations.stream().filter(o -> o.getName().equals(snowstormOrganizationName)).collect(Collectors.toList());

        if (organizations.size() > 1) {

            throw new Exception("Cannot have multiple orgs with same name: " + snowstormOrganizationName);
        }

        Organization matchingDatabaseOrganization = organizations.iterator().next();

        if (matchingDatabaseOrganization == null) {

            // Code System has new name associated with it. Thus create a new Organziation
            // TODO: Ask Rory what happens if this is a shared org. I imagine create new one rather than update across board? Implications here either way
            identifyOrganization(edition.getShortName(), edition.getName(), codeSystem);
        } else {

            // Found corresponding Organization based on snowstorm owner. Now determine if that is a different Org than currently defined in Edition.
            if (updateAttribute("Organization ", matchingDatabaseOrganization.getId(), edition.getOrganizationId())) {

                edition.setOrganization(matchingDatabaseOrganization);

                try (final TerminologyService service = new TerminologyService()) {

                    utilities.initializeService(service);

                    service.update(edition);
                    statistics.getOrganizationsSynced().add(matchingDatabaseOrganization);
                }

            } else {

                statistics.getOrganizationsUnchanged().add(matchingDatabaseOrganization);
            }

        }

    }

    private static Edition handleExistingEdition(Edition edition, String editionShortName, String editionName, String editionBranch, boolean isActiveEdition, JsonNode codeSystem) throws Exception {

        final Edition syncedEdition = compareAndUpdateEditionDifferences(edition, editionShortName, editionName, editionBranch, isActiveEdition, codeSystem);

        if (syncedEdition == null) {

            statistics.getEditionsUnchanged().add(edition);
        } else {

            statistics.getEditionsSynced().add(syncedEdition);
        }

        logger.info("Synced " + edition.getShortName() + " Edition");

        return syncedEdition;

    }

    private static Edition compareAndUpdateEditionDifferences(Edition existingEdition, String editionShortName, String editionName, String editionBranch, boolean isActiveEdition, JsonNode codeSystem)
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

        final String editionTopLevelModule = utilities.identifyTopLevelModule(editionShortName, editionName, editionBranch, codeSystem);

        // TODO: Can we remove topLevelModule?
        if (updateAttribute("Edition topLevelModule ", existingEdition.getTopLevelModule(), editionTopLevelModule)) {

            existingEdition.setTopLevelModule(editionTopLevelModule);
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

    private static Edition handleNewCodeSystem(String newEditionShortName, String newEditionName, String newEditionBranch, boolean isNewActiveEdition, JsonNode codeSystem) throws Exception {

        try {

            if (!isNewActiveEdition) {

                // New Code System created as inactive. Given this is being run nightly and a new org/edition that is inactive at first pass was likely made erroneously.
                // Once fixed and becomes active, we will get it at the following sync. For now, don't add to retSet
                String codeSystemCoordinates = generateCodeSystemCoordinates(newEditionShortName, newEditionName, newEditionBranch);
                codeSystemsNewAndInactive.add(codeSystemCoordinates);

                return null;
            }

            Organization organization = identifyOrganization(newEditionShortName, newEditionName, codeSystem);

            final Edition newEdition = utilities.addEdition(newEditionShortName, newEditionName, newEditionBranch, organization, codeSystem);
            statistics.getEditionsAdded().add(newEdition);
            allDatabaseEditions.add(newEdition);

            return newEdition;
        } catch (Exception e) {

            logger.error("Failed in syncing New Snowstorm Code System: " + codeSystem);
            e.printStackTrace();

            return null;
        }

    }

    private static Organization identifyOrganization(String newEditionShortName, String newEditionName, JsonNode codeSystem) throws Exception {

        // Create new Organization
        // TODO: 1 - Add a description default value or update snowstorm with value per codesystem
        setSnowstormEditionOwner(newEditionShortName, newEditionName, codeSystem);

        // Determine Owner
        List<Organization> organizations = allDatabaseOrganizations.stream().filter(o -> o.getName().equals(editionOwnerMap.get(newEditionName))).collect(Collectors.toList());

        if (organizations.size() > 1) {

            throw new Exception("Cannot have multiple orgs with same name: " + editionOwnerMap.get(newEditionName));
        }

        Organization organization = null;

        if (organizations.isEmpty()) {

            // Create new organization
            final String organizationDescription = "";

            organization = utilities.addOrganziation(editionOwnerMap.get(newEditionName), organizationDescription);

            statistics.getOrganizationsAdded().put(organization.getName(), organization);
            allDatabaseOrganizations.add(organization);
        } else {

            // Org already exists
            organization = organizations.iterator().next();
        }

        return organization;
    }

    // Organization is done at this point. Check if Developer Edition. If not, create a default UAT project

    private static void postCodeSystemProcessing(String snowstormEditionShortName, Edition syncedEdition) throws Exception {

        if (snowstormEditionShortName.equals(DEVELOPER_CODE_SYSTEM_SHORTNAME)) {

            // Support Developer Edition
            if (forProduction) {

                throw new Exception("Can't have a WCI Edition on a Prod instance");
            }

            if (develeperTestingEdition != null) {

                throw new Exception("Can't have two WCI Editions with new one having shortName: " + snowstormEditionShortName);
            } else {

                // identified WCI Edition
                develeperTestingEdition = syncedEdition;
            }

        } else {

            // Create a Default Project for the edition
            if (syncedEdition != null && !defaultEditionProjects.containsKey(syncedEdition.getId())) {

                Project project = null;
                final String projectName = syncedEdition.getName() + " Default Project";
                final String projectDescription =
                    "This is a project to support all refsets not already associated with a project in the Refset & Translation Tool for " + syncedEdition.getName() + ".";

                // Create default project
                project = utilities.addProject(projectName, projectDescription, syncedEdition);

                defaultEditionProjects.put(syncedEdition.getId(), project);
            }

        }

    }

    private static String generateCodeSystemCoordinates(String snowstormEditionShortName, String snowstormEditionName, String snowstormEditionBranch) {

        return snowstormEditionShortName + " / " + snowstormEditionName + " / " + snowstormEditionBranch;
    }

    private static void setSnowstormEditionOwner(String editionShortName, String editionName, JsonNode codeSystem) {

        /*-
         * For testing orgs
        // final String owner = codeSystem.has("owner") ? codeSystem.get("owner").asText() : "";
        String owner = "testOrg";
        
        if (editionShortName.equals(DEVELOPER_CODE_SYSTEM_SHORTNAME)) {
        
            owner = codeSystem.has("owner") ? codeSystem.get("owner").asText() : "";
        }
        */
        final String owner = codeSystem.has("owner") ? codeSystem.get("owner").asText() : "";

        // Identify Code System Owner
        if (!owner.trim().isBlank()) {

            editionOwnerMap.put(editionShortName, owner);
            editionOwnerMap.put(editionName, owner);
        } else {

            editionOwnerMap.put(editionShortName, editionName);
            editionOwnerMap.put(editionName, editionName);
        }

    }

    private static Organization compareAndUpdateOrganizationDifferences(Organization existingOrganization, String snowstormOrganizationName, boolean isActiveSnowstormOrganization) throws Exception {

        /* Found existing Edition. Compare the values to determine if something changed, and if so, update the edition accordingly */
        boolean modificationMade = false;

        if (updateAttribute("Organization name ", existingOrganization.getName(), snowstormOrganizationName)) {

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

    private static Set<JsonNode> filterCodeSystems() throws Exception {

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
    private static JsonNode getSnowstormCodeSystems() throws Exception {

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

                if (SyncAgentUtilities.isInternationalEdition(codeSystem.get("name").asText())) {

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

}
