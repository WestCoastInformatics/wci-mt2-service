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
            Set<Edition> syncedEditions = syncEdition(codeSystem);

            if (syncedEditions != null && !syncedEditions.isEmpty()) {

                // Process only one edition.
                Organization syncedOrg = syncOrganization(codeSystem, syncedEditions.iterator().next().getId());

                /* Organization is done at this point. Check if WCI Organization */
                if (syncedOrg != null && syncedOrg.getEdition().getShortName().equals("SNOMEDCT-WCI"))

                {

                    if (wciOrganization != null) {

                        throw new Exception("Can't have two WCI Orgs");
                    }

                    // identified WCI Org
                    wciOrganization = syncedOrg;
                }

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
    private Organization syncOrganization(JsonNode codeSystem, String editionId) throws Exception {

        logger.debug(" Migrate/Sync Organization(s) for codeSystem: " + codeSystem.get("name").asText() + " using editionId: " + editionId);

        Organization organization = null;

        /* See if have organization with corresponding editionId */
        // If existingEdition is null, this is the first time we have observed this edition, so create it.
        final List<Organization> matchingOrganizations = allOrganizations.stream().filter(o -> editionId.equals(o.getEdition().getId())).collect(Collectors.toList());

        if (matchingOrganizations.size() > 1) {

            throw new Exception("Have more than one organization associated with edition. This isn't supported in RT2 at the time being");
        }

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

            try (final TerminologyService service = new TerminologyService()) {

                Edition edition = service.get(editionId, Edition.class);
                snowstormOrganizationName = edition.getName();
            }

        }

        /* Based on matching attributes: add new, ignore new but inactive, check for changes and modify if needed and ignore otherwise */
        if (matchingOrganizations == null || matchingOrganizations.isEmpty()) {

            // Handle new versus existing Organization
            if (isActiveSnowstormOrganization) {

                // Only create if it is active
                final Edition edition = allEditions.stream().filter(e -> editionId.equals(e.getId())).collect(Collectors.toList()).iterator().next();
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

            final Organization currentOrganization = matchingOrganizations.iterator().next();
            final Organization syncedOrganization = compareAndUpdateOrganizationDifferences(currentOrganization, snowstormOrganizationName, isActiveSnowstormOrganization);

            if (syncedOrganization != null) {

                // A modification was made, so updated edition
                organizationsSynced.add(syncedOrganization);
                organization = syncedOrganization;

            } else {

                // No changes, return existing
                organizationsUnchanged.add(currentOrganization);
                organization = currentOrganization;
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
    private Set<Edition> syncEdition(JsonNode codeSystem) throws Exception {

        logger.debug(" Migrate/Sync Edition(s) for codeSystem: " + codeSystem.get("name").asText());

        /* identify comparison attributes */
        final String snowstormEditionShortName = codeSystem.has("shortName") ? codeSystem.get("shortName").asText() : "";
        final String snowstormEditionName = codeSystem.has("name") ? codeSystem.get("name").asText() : "";
        final String snowstormEditionBranch = codeSystem.has("branchPath") ? codeSystem.get("branchPath").asText() : "";
        final boolean isActiveSnowstormEdition = codeSystem.has("active") ? codeSystem.get("active").asBoolean() : true;

        /* See if exists. If not return created. */

        // If existingEdition is null, this is the first time we have observed this edition, so create it.
        final List<Edition> matchingSnowstormEditions = identifyMatchingEdition(snowstormEditionShortName, snowstormEditionName, snowstormEditionBranch);

        if (matchingSnowstormEditions == null || matchingSnowstormEditions.isEmpty()) {

            // New Code System identified on Snowstorm
            if (isActiveSnowstormEdition) {

                // Only create if it is active
                Edition newEdition = utilities.addEdition(codeSystem, snowstormEditionShortName, snowstormEditionName, snowstormEditionBranch);

                editionsAdded.add(newEdition);

                return editionsAdded;
            } else {

                editionsNewAndInactive.add(snowstormEditionShortName + " / " + snowstormEditionName + " / " + snowstormEditionBranch);

                // New Code System created as inactive. Given this is being run nightly and a new org/edition that is inactive at first pass was likely made erroneously.
                // Once fixed and becomes active, we will get it at the following sync. For now, don't add to retSet
                return new HashSet<Edition>();
            }

        } else {

            Set<Edition> editions = new HashSet<>();

            for (Edition existingEdition : matchingSnowstormEditions) {

                /* Found existing Edition. Compare the values to determine if something changed, and if so, update the edition accordingly */
                boolean modificationMade = false;

                if (!existingEdition.getShortName().equals(snowstormEditionShortName)) {

                    logger.debug(" inconsistent ShortName with '" + existingEdition.getShortName() + "' and '" + snowstormEditionShortName + "'");

                    existingEdition.setShortName(snowstormEditionShortName);
                    modificationMade = true;
                }

                if (!existingEdition.getName().equals(snowstormEditionName)) {

                    logger.debug(" inconsistent name with '" + existingEdition.getName() + "' and '" + snowstormEditionName + "'");

                    existingEdition.setName(snowstormEditionName);
                    modificationMade = true;
                }

                if (!existingEdition.getBranch().equals(snowstormEditionBranch)) {

                    logger.debug(" inconsistent branch with '" + existingEdition.getBranch() + "' and '" + snowstormEditionBranch + "'");

                    existingEdition.setBranch(snowstormEditionBranch);
                    modificationMade = true;
                }

                if (existingEdition.isActive() != isActiveSnowstormEdition) {

                    logger.debug(" inconsistent active with '" + existingEdition.isActive() + "' and '" + isActiveSnowstormEdition + "'");

                    existingEdition.setActive(isActiveSnowstormEdition);
                    modificationMade = true;
                }

                final String snowstormEditionTopLevelModule = utilities.identifyTopLevelModule(snowstormEditionShortName, snowstormEditionName, snowstormEditionBranch, codeSystem);

                if (!existingEdition.getTopLevelModule().equals(snowstormEditionTopLevelModule)) {

                    logger.debug(" inconsistent topLevelModule with '" + existingEdition.getTopLevelModule() + "' and '" + snowstormEditionTopLevelModule + "'");

                    existingEdition.setTopLevelModule(snowstormEditionTopLevelModule);
                    modificationMade = true;
                }

                final String snowstormEditionDefaultLanguageCode = utilities.identifyDefaultLanguageCode(codeSystem, snowstormEditionName);

                if (!existingEdition.getDefaultLanguageCode().equals(snowstormEditionDefaultLanguageCode)) {

                    logger.debug(" inconsistent defaultLanguageCode with '" + existingEdition.getDefaultLanguageCode() + "' and '" + snowstormEditionDefaultLanguageCode + "'");

                    existingEdition.setDefaultLanguageCode(snowstormEditionDefaultLanguageCode);
                    modificationMade = true;
                }

                final Set<String> snowstormEditionDefaultLanguageRefsets = utilities.identifyDefaultLanguageRefsets(codeSystem, snowstormEditionName);

                if (!existingEdition.getDefaultLanguageRefsets().equals(snowstormEditionDefaultLanguageRefsets)) {

                    if (!existingEdition.getDefaultLanguageRefsets().isEmpty() && snowstormEditionDefaultLanguageRefsets.isEmpty()) {

                        logger.debug(
                            " False-Positive inconsistent defaultLanguageRefsets with '" + existingEdition.getDefaultLanguageRefsets() + "' and '" + snowstormEditionDefaultLanguageRefsets + "'");
                    } else {

                        logger.debug(" inconsistent defaultLanguageRefsets with '" + existingEdition.getDefaultLanguageRefsets() + "' and '" + snowstormEditionDefaultLanguageRefsets + "'");

                        existingEdition.setDefaultLanguageRefsets(snowstormEditionDefaultLanguageRefsets);
                        modificationMade = true;
                    }

                }

                if (!modificationMade) {

                    editionsUnchanged.add(existingEdition);
                    editions.add(existingEdition);

                } else {

                    // A modification was made, so updated edition
                    try (TerminologyService service = new TerminologyService()) {

                        initializeService(service);

                        Edition syncedEdition = service.update(existingEdition);
                        editionsSynced.add(syncedEdition);
                        editions.add(syncedEdition);
                    }

                }

            }

        }

        Set<Edition> retSet = new HashSet<>();
        retSet.addAll(editionsSynced);
        retSet.addAll(editionsUnchanged);

        return retSet;
    }

    private List<Edition> identifyMatchingEdition(String shortName, String editionName, String branch) throws Exception {

        List<Edition> existingEditions = null;

        existingEditions = allEditions.stream().filter(e -> e.getShortName().equals(shortName)).collect(Collectors.toList());

        if (existingEditions != null && !existingEditions.isEmpty()) {

            logger.info(" Matched Edition(s) on shortName: " + shortName);
        } else {

            existingEditions = allEditions.stream().filter(e -> e.getName().equals(editionName)).collect(Collectors.toList());

            if (existingEditions != null && !existingEditions.isEmpty()) {

                logger.info(" Matched Edition(s) on editionName: " + editionName);
            } else {

                existingEditions = allEditions.stream().filter(e -> e.getBranch().equals(branch)).collect(Collectors.toList());

                if (existingEditions != null && !existingEditions.isEmpty()) {

                    logger.info(" Matched Edition(s) on branch: " + branch);
                } else {

                    // No matching edition found
                    logger.info(" No matching Edition found");
                }

            }

        }

        return existingEditions;

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
