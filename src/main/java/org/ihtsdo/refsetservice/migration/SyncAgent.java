package org.ihtsdo.refsetservice.migration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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

public class SyncAgent {

    /** The logger. */
    private final Logger logger = LoggerFactory.getLogger(SyncAgent.class);

    private boolean forProduction;

    private static MigrationUtilities utilities;

    private Organization wciOrganization = null;

    private Map<String, Organization> organizationsAdded = new HashMap<>();

    private Set<Organization> organizationsUnchanged = new HashSet<>();

    private Set<Organization> organizationsSynced = new HashSet<>();

    private Set<Edition> editionsAdded = new HashSet<>();

    private Set<Edition> editionsUnchanged = new HashSet<>();

    private Set<Edition> editionsSynced = new HashSet<>();

    private Set<String> editionsNewAndInactive = new HashSet<>();

    private List<Edition> allEditions;

    private List<Organization> allOrganizations;

    private final Map<String, String> editionOwnerMap = new HashMap<>();

    private final Map<String, Project> defaultOrganizationProjects = new HashMap<>();

    private static final String WCI_ORG_NAME = "wci";

    private static final List<String> ignoredCodeSystemNames = new ArrayList<>();

    /** The testing. */
    private boolean testing = true;

    private final String testingEdition = "elgia";

    public SyncAgent(MigrationUtilities utilities, boolean runForProduction) {

        SyncAgent.utilities = utilities;

        ignoredCodeSystemNames.addAll(utilities.getPropertyReader().readCodeSystemsToIgnore());

        this.forProduction = runForProduction;

        try {

            getDBContent();
        } catch (Exception e) {

            e.printStackTrace();
        }

    }

    private void getDBContent() throws Exception {

        try (TerminologyService service = new TerminologyService()) {

            allEditions = service.getAll(Edition.class);
            allOrganizations = service.getAll(Organization.class);

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
    public JsonNode getSnowstormCodeSystems() throws Exception {

        final String url = SnowstormConnection.BASE_URL + "codesystems";
        logger.debug("getSnowstormCodeSystems url: " + url);

        try (final Response response = SnowstormConnection.getResponse(url)) {

            final String resultString = response.readEntity(String.class);
            // logger.debug("Code Systems from Snowstorm: " + resultString);

            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode organizationJsonRootNode = mapper.readTree(resultString.toString());

            identifyInternationalModules(organizationJsonRootNode);

            return organizationJsonRootNode;
        }

    }

    void identifyInternationalModules(JsonNode root) throws Exception {

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

    public Set<JsonNode> filterCodeSystems(JsonNode organizationJsonRootNode) throws Exception {

        final Set<JsonNode> filteredCodeSystems = new HashSet<>();

        final Iterator<JsonNode> organizationIterator = organizationJsonRootNode.iterator();

        while (organizationIterator.hasNext()) {

            final Iterator<JsonNode> codeSystems = organizationIterator.next().iterator();

            while (codeSystems.hasNext()) {

                JsonNode codeSystem = codeSystems.next();
                logger.debug(" Migrate/Sync codeSystem: " + codeSystem.get("name").asText());

                // Check for invalid or ignored code systems
                if (!codeSystem.has("name")) {

                    logger.info("Skipping odd code system without a name'" + codeSystem.asText());
                    continue;
                } else if (ignoredCodeSystemNames.contains(codeSystem.get("name").asText().toLowerCase())) {

                    logger.info("Code System '" + codeSystem.get("name") + "' is defined as to-be-ignored");
                    continue;
                }

                // Testing
                if (testing && !codeSystem.get("name").asText().contains(testingEdition) && !codeSystem.get("name").asText().toLowerCase().contains(WCI_ORG_NAME)
                    && !codeSystem.get("name").asText().contains("Inter")) {

                    continue;
                }

                filteredCodeSystems.add(codeSystem);
            }

        }

        return filteredCodeSystems;
    }

    public void processCodeSystems(Set<JsonNode> codeSystems) throws Exception {

        for (JsonNode codeSystem : codeSystems) {

            // Simplified approach is to not consider at this point if new edition was created or a new one was discovered
            Edition syncedEdition = syncEdition(codeSystem);

            if (syncedEdition != null) {

                // Process only one edition.
                Organization syncedOrg = syncOrganization(codeSystem, syncedEdition);
            }

            logger.debug("*********    Results    *************");
            logger.debug("Editions Added/Unchanged/Synced: " + editionsAdded.size() + " / " + editionsUnchanged.size() + " / " + editionsSynced.size());
            logger.debug("Organizations Added/Unchanged/Synced: " + organizationsAdded.size() + " / " + organizationsUnchanged.size() + " / " + organizationsSynced.size());
        }

        if (wciOrganization != null && forProduction) {

            throw new Exception("May not have a WCI Organization on a forProd instance");
        } else if (wciOrganization == null && !forProduction) {

            throw new Exception("Must have a WCI Organization on a non-Prod instance");
        }

    }

    void migrateEditions(Set<JsonNode> codeSystems) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            initializeService(service);

            for (JsonNode codeSystem : codeSystems) {

                final String editionName = codeSystem.get("name").asText();
                final String shortName = codeSystem.get("shortName").asText();
                final String branch = codeSystem.get("branchPath").asText();
                final String owner = codeSystem.has("owner") ? codeSystem.get("owner").asText() : "";

                logger.info("Processing CodeSystem: " + editionName);

                // Process Edition
                final Edition edition = utilities.addEdition(codeSystem, shortName, editionName, branch);

                // Identify Code System Owner
                if (!owner.trim().isBlank()) {

                    editionOwnerMap.put(edition.getShortName(), owner);
                    editionOwnerMap.put(edition.getName(), owner);
                } else {

                    editionOwnerMap.put(edition.getShortName(), edition.getName());
                    editionOwnerMap.put(edition.getName(), edition.getName());
                }

                // TODO: Add a description default value or update snowstorm with value per codesystem
                String organizationDescription = "";
                Organization org = utilities.addOrganziation(editionOwnerMap.get(edition.getName()), organizationDescription, edition);

                organizationsAdded.put(org.getName(), org);

                if (org.getEdition().getShortName().equals("SNOMEDCT-WCI")) {

                    if (forProduction) {

                        throw new Exception("Have a forProd instance running, yet found an unexpected WCI Org");
                    }

                    wciOrganization = org;
                } else {

                    // Finally, create a Default Project for the edition
                    if (!defaultOrganizationProjects.containsKey(org.getId())) {

                        // Create default project
                        final String projectName = org.getName() + " Default Project";
                        final String projectDescription = "This is a project to support all refsets not already associated with a project in the Refset & Translation Tool for " + org.getName() + ".";

                        final Project project = utilities.addProject(org, projectName, projectDescription);

                        defaultOrganizationProjects.put(org.getId(), project);
                    }

                }

            }

            if (wciOrganization == null && !forProduction)

            {

                throw new Exception("Have a non-Prod instance running, yet didn't find the expected WCI Org");
            }

        } catch (

        Exception e) {

            e.printStackTrace();
        }

    }

    Map<String, Organization> getOrganizationsAdded() {

        return organizationsAdded;
    }

    /*-
     * Match by Organization::Edition::id to match against all Orgs in the DB. If not successful, try name, and finally try branch. If nothing found, is new Edition.
     * 
     * For now, only must identify if there are changes to any of the following object values during sync: 
     * 1) Name
     * 2) Became Inactive 
     * 3) Branch
     * 4) Active/inactive status
     * 5) defaultLanguageCode
     * 6) topLevelModule
     * 7) defaultLanguageRefsets
     */
    private Organization syncOrganization(JsonNode codeSystem, Edition edition) throws Exception {

        logger.debug(" Migrate/Sync Organization(s) for codeSystem: " + codeSystem.get("name").asText() + " using edition: " + edition);

        Organization organization = null;

        /* See if have organization with corresponding editionId */
        // If existingEdition is null, this is the first time we have observed this edition, so create it.
        final Organization matchingOrganization = allOrganizations.stream().filter(o -> edition.getId().equals(o.getEdition().getId())).collect(Collectors.toList()).iterator().next();

        /* identify comparison attributes */
        boolean isActiveSnowstormOrganization = true;

        if (codeSystem.has("active")) {

            isActiveSnowstormOrganization = codeSystem.get("active").asBoolean();
        }

        // owner generally not populated at this time, so provide backup plan
        String snowstormOrganizationName;

        if (codeSystem.has("owner") && !codeSystem.get("owner").asText().trim().isBlank()) {

            snowstormOrganizationName = codeSystem.get("owner").asText();
        } else {

            snowstormOrganizationName = edition.getName();
        }

        /* Based on matching attributes: add new, ignore new but inactive, check for changes and modify if needed and ignore otherwise */
        if (matchingOrganization == null) {

            // Handle new versus existing Organization
            if (isActiveSnowstormOrganization) {

                // Only create if it is active
                final Organization newOrganization = utilities.addOrganziation(snowstormOrganizationName, "", edition);

                // TODO: Add a description default value or update Organization org = utilities.addOrganziation(orgName, orgDesc, edition, defaultMeta);
                organizationsAdded.put(newOrganization.getName(), newOrganization);
                organization = newOrganization;
            } else {

                // New Code System created as inactive. Given this is being run nightly and a new org/edition that is inactive at first pass was likely made erroneously.
                // Once
                // fixed and becomes active, we will get it at the following sync.
                organization = null;
            }

        } else {

            final Organization syncedOrganization = compareAndUpdateOrganizationDifferences(matchingOrganization, snowstormOrganizationName, isActiveSnowstormOrganization);

            if (syncedOrganization != null) {

                // A modification was made, so updated edition
                organizationsSynced.add(syncedOrganization);
                organization = syncedOrganization;

            } else {

                // No changes, return existing
                organizationsUnchanged.add(matchingOrganization);
                organization = matchingOrganization;
            }

        }

        logger.info("Synced " + organization.getName() + " Organization");

        /* Organization is done at this point. Check if WCI Organization */
        if (organization != null && organization.getEdition().getShortName().equals("SNOMEDCT-WCI")) {

            if (wciOrganization != null) {

                throw new Exception("Can't have two WCI Orgs with new one having shortName: " + organization.getEdition().getShortName());
            } else {

                // identified WCI Org
                wciOrganization = organization;
            }

        }

        return organization;
    }

    private Organization compareAndUpdateOrganizationDifferences(Organization existingOrganization, String snowstormOrganizationName, boolean isActiveSnowstormOrganization) throws Exception {

        /* Found existing Edition. Compare the values to determine if something changed, and if so, update the edition accordingly */
        boolean modificationMade = false;

        if (!existingOrganization.getName().equals(snowstormOrganizationName)) {

            logger.debug(" inconsistent name with '" + existingOrganization.getName() + "' and '" + snowstormOrganizationName + "'");

            existingOrganization.setName(snowstormOrganizationName);
            modificationMade = true;
        }

        if (existingOrganization.isActive() != isActiveSnowstormOrganization) {

            logger.debug(" inconsistent active with '" + existingOrganization.isActive() + "' and '" + isActiveSnowstormOrganization + "'");

            existingOrganization.setActive(isActiveSnowstormOrganization);
            modificationMade = true;
        }

        if (modificationMade) {

            try (final TerminologyService service = new TerminologyService()) {

                initializeService(service);

                return service.update(existingOrganization);
            }

        } else {

            return null;
        }

    }

    /*-
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
    private Edition syncEdition(JsonNode codeSystem) throws Exception {

        logger.debug(" Migrate/Sync Edition(s) for codeSystem: " + codeSystem.get("name").asText());

        /* identify comparison attributes */
        final String snowstormEditionShortName = codeSystem.has("shortName") ? codeSystem.get("shortName").asText() : "";
        final String snowstormEditionName = codeSystem.has("name") ? codeSystem.get("name").asText() : "";
        final String snowstormEditionBranch = codeSystem.has("branchPath") ? codeSystem.get("branchPath").asText() : "";
        final boolean isActiveSnowstormEdition = codeSystem.has("active") ? codeSystem.get("active").asBoolean() : true;

        Edition returnedEdition = null;

        /* See if exists. If not return created. */

        // If existingEdition is null, this is the first time we have observed this edition, so create it.
        final Edition matchingSnowstormEdition = identifyMatchingEdition(snowstormEditionShortName);

        if (matchingSnowstormEdition == null) {

            // New Code System identified on Snowstorm
            if (isActiveSnowstormEdition) {

                // Only create if it is active
                Edition newEdition = utilities.addEdition(codeSystem, snowstormEditionShortName, snowstormEditionName, snowstormEditionBranch);

                editionsAdded.add(newEdition);

                returnedEdition = newEdition;

            } else {

                editionsNewAndInactive.add(snowstormEditionShortName + " / " + snowstormEditionName + " / " + snowstormEditionBranch);

                // New Code System created as inactive. Given this is being run nightly and a new org/edition that is inactive at first pass was likely made erroneously.
                // Once fixed and becomes active, we will get it at the following sync. For now, don't add to retSet
            }

        } else {

            /* Found existing Edition. Compare the values to determine if something changed, and if so, update the edition accordingly */
            boolean modificationMade = false;

            if (!matchingSnowstormEdition.getShortName().equals(snowstormEditionShortName)) {

                logger.debug(" inconsistent ShortName with '" + matchingSnowstormEdition.getShortName() + "' and '" + snowstormEditionShortName + "'");

                matchingSnowstormEdition.setShortName(snowstormEditionShortName);
                modificationMade = true;
            }

            if (!matchingSnowstormEdition.getName().equals(snowstormEditionName)) {

                logger.debug(" inconsistent name with '" + matchingSnowstormEdition.getName() + "' and '" + snowstormEditionName + "'");

                matchingSnowstormEdition.setName(snowstormEditionName);
                modificationMade = true;
            }

            if (!matchingSnowstormEdition.getBranch().equals(snowstormEditionBranch)) {

                logger.debug(" inconsistent branch with '" + matchingSnowstormEdition.getBranch() + "' and '" + snowstormEditionBranch + "'");

                matchingSnowstormEdition.setBranch(snowstormEditionBranch);
                modificationMade = true;
            }

            if (matchingSnowstormEdition.isActive() != isActiveSnowstormEdition) {

                logger.debug(" inconsistent active with '" + matchingSnowstormEdition.isActive() + "' and '" + isActiveSnowstormEdition + "'");

                matchingSnowstormEdition.setActive(isActiveSnowstormEdition);
                modificationMade = true;
            }

            final String snowstormEditionTopLevelModule = utilities.identifyTopLevelModule(snowstormEditionShortName, snowstormEditionName, snowstormEditionBranch, codeSystem);

            if (!matchingSnowstormEdition.getTopLevelModule().equals(snowstormEditionTopLevelModule)) {

                logger.debug(" inconsistent topLevelModule with '" + matchingSnowstormEdition.getTopLevelModule() + "' and '" + snowstormEditionTopLevelModule + "'");

                matchingSnowstormEdition.setTopLevelModule(snowstormEditionTopLevelModule);
                modificationMade = true;
            }

            final String snowstormEditionDefaultLanguageCode = utilities.identifyDefaultLanguageCode(codeSystem, snowstormEditionName);

            if (!matchingSnowstormEdition.getDefaultLanguageCode().equals(snowstormEditionDefaultLanguageCode)) {

                logger.debug(" inconsistent defaultLanguageCode with '" + matchingSnowstormEdition.getDefaultLanguageCode() + "' and '" + snowstormEditionDefaultLanguageCode + "'");

                matchingSnowstormEdition.setDefaultLanguageCode(snowstormEditionDefaultLanguageCode);
                modificationMade = true;
            }

            final Set<String> snowstormEditionDefaultLanguageRefsets = utilities.identifyDefaultLanguageRefsets(codeSystem, snowstormEditionName);

            if (!matchingSnowstormEdition.getDefaultLanguageRefsets().equals(snowstormEditionDefaultLanguageRefsets)) {

                if (!matchingSnowstormEdition.getDefaultLanguageRefsets().isEmpty() && snowstormEditionDefaultLanguageRefsets.isEmpty()) {

                    logger.debug(
                        " False-Positive inconsistent defaultLanguageRefsets with '" + matchingSnowstormEdition.getDefaultLanguageRefsets() + "' and '" + snowstormEditionDefaultLanguageRefsets + "'");
                } else {

                    logger.debug(" inconsistent defaultLanguageRefsets with '" + matchingSnowstormEdition.getDefaultLanguageRefsets() + "' and '" + snowstormEditionDefaultLanguageRefsets + "'");

                    matchingSnowstormEdition.setDefaultLanguageRefsets(snowstormEditionDefaultLanguageRefsets);
                    modificationMade = true;
                }

            }

            if (!modificationMade) {

                editionsUnchanged.add(matchingSnowstormEdition);
                returnedEdition = matchingSnowstormEdition;

            } else {

                // A modification was made, so updated edition
                try (TerminologyService service = new TerminologyService()) {

                    initializeService(service);

                    Edition syncedEdition = service.update(matchingSnowstormEdition);
                    editionsSynced.add(syncedEdition);
                    returnedEdition = syncedEdition;
                }

            }

        }

        logger.info("Synced following Edition: " + returnedEdition.getName());

        return returnedEdition;

    }

    private Edition identifyMatchingEdition(String shortName) throws Exception {

        Edition existingEdition = null;

        existingEdition = allEditions.stream().filter(e -> e.getShortName().equals(shortName)).collect(Collectors.toList()).iterator().next();

        if (existingEdition != null) {

            logger.info(" Matched Edition(s) on shortName: " + shortName);
        }

        return existingEdition;

    }

    /*-
     * Match by Edition::shortName to match within the DB, and then compare against the associated Organization
     * 
     * For now, only must identify if there are changes to any of the following object values during sync: 
     * 1) Name 
     * 2) Active/inactive status
     * 
     */

    private void processProjects(Organization organization) {

        /*-
        if (!edition.getShortName().equals("SNOMEDCT-WCI")) {
        
            // Finally, create a Default Project for the edition
            if (!defaultOrganizationProjects.containsKey(org.getId())) {
        
                // Create default project
                final String projectName = orgName + " Default Project";
                final String projectDescription = "This is a project to support all refsets not already associated with a project in the Refset & Translation Tool for " + orgName + ".";
        
                final Project project = utilities.addProject(org, projectName, projectDescription, defaultMeta);
        
                defaultOrganizationProjects.put(org.getId(), project);
            }
        
        }
        */
    }

    private void initializeService(TerminologyService service) {

        service.setModifiedBy("Migration");
        service.setModifiedFlag(true);

    }
}
