package org.ihtsdo.refsetservice.migration;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.ws.rs.core.Response;

import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.Organization;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.terminologyservice.SnowstormConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class SyncAgent {

    /** The logger. */
    private final Logger logger = LoggerFactory.getLogger(SyncAgent.class);

    private static final MigrationUtilities utilities = new MigrationUtilities();

    /** The testing. */
    private boolean testing = true;

    private final String testingEdition = "elgia";

    private boolean forProduction;

    private Organization wciOrganization = null;

    private Set<Organization> organizationsAdded = new HashSet<>();

    private Set<Organization> organizationsUnchanged = new HashSet<>();

    private Set<Organization> organizationsSynced = new HashSet<>();

    private Set<Edition> editionsAdded = new HashSet<>();

    private Set<Edition> editionsUnchanged = new HashSet<>();

    private Set<Edition> editionsSynced = new HashSet<>();

    private Set<String> editionsNewAndInactive = new HashSet<>();

    private List<Edition> allEditions;

    private List<Organization> allOrganizations;

    private static final String testingRefset = "741000172102";

    private static final Set<String> internationalModules = new HashSet<>();

    private static final String WCI_ORG_NAME = "wci";

    private static final List<String> ignoredCodeSystemNames = utilities.getPropertyReader().readCodeSystemsToIgnore();

    private static final Map<String, Set<String>> undefinedDefaultLanguageRefsets = utilities.getPropertyReader().readUndefinedDefaultLanguageRefsets();

    private static final String DEFAULT_LANGUAGE_REFSET = "900000000000509007";

    public SyncAgent(boolean runForProduction) {

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
            logger.debug("createEditionsFromSnowstorm resultString: " + resultString);

            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode organizationJsonRootNode = mapper.readTree(resultString.toString());

            identifyInternationalModules(organizationJsonRootNode);

            return organizationJsonRootNode;
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
                        internationalModules.add(module.get("conceptId").asText());
                    }

                }

            }

        }

        logger.info("Identified " + internationalModules.size() + " international modules");

        if (internationalModules.isEmpty()) {

            throw new Exception("Didn't find the international modules as anticipated");

        }

    }

    public Set<String> getInternationalModules() {

        return internationalModules;
    }

    public void processCodeSystems(JsonNode organizationJsonRootNode) throws Exception {

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

                // Simplified approach is to not consider at this point if new edition was created or a new one was discovered
                Set<Edition> syncedEditions = syncEdition(codeSystem);

                if (!syncedEditions.isEmpty()) {

                    // Process only one edition.
                    Organization syncedOrg = syncOrganization(codeSystem, syncedEditions.iterator().next().getId());

                    logger.debug("*********    Results    *************");
                    logger.debug("Editions Added/Unchanged/Synced: " + editionsAdded.size() + " / " + editionsUnchanged.size() + " / " + editionsSynced.size());
                    logger.debug("Organizations Added/Unchanged/Synced: " + organizationsAdded.size() + " / " + organizationsUnchanged.size() + " / " + organizationsSynced.size());

                    /* Organization is done at this point. Check if WCI Organization */
                    if (syncedOrg.getEdition().getShortName().equals("SNOMEDCT-WCI"))

                    {

                        if (forProduction) {

                            throw new Exception("Have a forProd instance running, yet found an unexpected WCI Org");
                        }

                        if (wciOrganization != null) {

                            throw new Exception("Can't have two WCI Orgs");
                        }

                        // identified WCI Org
                        wciOrganization = syncedOrg;
                    }

                }

            }

            if (wciOrganization == null && !forProduction) {

                throw new Exception("Have a non-Prod instance running, yet didn't find the expected WCI Org");
            }

        }

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
        try (final TerminologyService service = new TerminologyService()) {

            List<Organization> allOrganizations = service.getAll(Organization.class);

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

                Edition edition = service.get(editionId, Edition.class);
                snowstormOrganizationName = edition.getName();
            }

            if (matchingOrganizations == null || matchingOrganizations.isEmpty()) {

                // Handle new versus existing Organization
                if (isActiveSnowstormOrganization) {

                    // Only create if it is active
                    final Organization newOrganization = createOrganization(snowstormOrganizationName);
                    organizationsAdded.add(newOrganization);

                    organization = newOrganization;
                } else {

                    // New Code System created as inactive. Given this is being run nightly and a new org/edition that is inactive at first pass was likely made erroneously.
                    // Once
                    // fixed and becomes active, we will get it at the following sync.
                    organization = null;
                }

            } else {

                final Organization existingOrganization = matchingOrganizations.iterator().next();

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

                    // A modification was made, so updated edition
                    initializeService(service);

                    final Organization syncedOrganization = service.update(existingOrganization);
                    organizationsSynced.add(syncedOrganization);

                    organization = syncedOrganization;

                } else {

                    // No changes, return existing
                    organizationsUnchanged.add(existingOrganization);

                    organization = existingOrganization;
                }

            }

        }

        if (organization == null) {

            return organization;
        }

        // TODO: Add a description default value or update Organization org = utilities.addOrganziation(orgName, orgDesc, edition, defaultMeta);

        return organization;
    }

    /*-
     * Match by Organization::Edition::id to match against all Orgs in the DB. If not successful, try name, and finally try branch. If nothing found, is new Edition.
     * 
     * For now, only must identify if there are changes to any of the following object values during sync: 
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

        final String snowstormEditionDefaultLanguageCode = identifyDefaultLanguageCode(codeSystem, snowstormEditionName);
        final String snowstormEditionTopLevelModule = codeSystem.has("modules") ? identifyTopLevelModule(snowstormEditionShortName, snowstormEditionName, snowstormEditionBranch, codeSystem) : "";
        final Set<String> snowstormEditionDefaultLanguageRefsets = codeSystem.has("defaultLanguageReferenceSets") ? identifyDefaultLanguageRefsets(codeSystem, snowstormEditionName) : new HashSet<>();

        /* See if exists. If not return created. */

        // If existingEdition is null, this is the first time we have observed this edition, so create it.
        final List<Edition> existingEditions = identifyMatchingEdition(snowstormEditionShortName, snowstormEditionName, snowstormEditionBranch);

        if (existingEditions == null) {

            // New Code System identified on Snowstorm
            if (isActiveSnowstormEdition) {

                // Only create if it is active
                Edition newEdition = createNewEdition(snowstormEditionShortName, snowstormEditionName, snowstormEditionBranch, snowstormEditionDefaultLanguageRefsets, snowstormEditionTopLevelModule,
                    snowstormEditionDefaultLanguageCode);

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

            for (Edition existingEdition : existingEditions) {

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

                if (!existingEdition.getTopLevelModule().equals(snowstormEditionTopLevelModule)) {

                    logger.debug(" inconsistent topLevelModule with '" + existingEdition.getTopLevelModule() + "' and '" + snowstormEditionTopLevelModule + "'");

                    existingEdition.setTopLevelModule(snowstormEditionTopLevelModule);
                    modificationMade = true;
                }

                if (!existingEdition.getDefaultLanguageCode().equals(snowstormEditionDefaultLanguageCode)) {

                    logger.debug(" inconsistent defaultLanguageCode with '" + existingEdition.getDefaultLanguageCode() + "' and '" + snowstormEditionDefaultLanguageCode + "'");

                    existingEdition.setDefaultLanguageCode(snowstormEditionDefaultLanguageCode);
                    modificationMade = true;
                }

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

    private Edition createNewEdition(String shortName, String name, String branch, Set<String> defaultLanguageRefsets, String topLevelModule, String defaultLanguageCode) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            Edition edition = new Edition();

            edition.setShortName(shortName);
            edition.setName(name);
            edition.setBranch(branch);
            edition.setDefaultLanguageRefsets(defaultLanguageRefsets);
            edition.setTopLevelModule(topLevelModule);
            edition.setDefaultLanguageCode(defaultLanguageCode);

            // New ones only created as new
            edition.setActive(true);

            Edition createdEdition = service.add(edition);

            logger.debug("Created New Edition: " + createdEdition);

            return createdEdition;
        }

    }

    private Organization createOrganization(String name) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            initializeService(service);

            Organization organization = new Organization();

            organization.setName(name);

            // New ones only created as new
            organization.setActive(true);

            Organization createdOrganization = service.add(organization);
            logger.debug("Created New Organization: " + createdOrganization);

            return createdOrganization;
        }

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
    private String identifyDefaultLanguageCode(JsonNode codeSystem, String snowstormEditionName) throws Exception {

        // Identify Edition's defaultLanguageCode - Per Kai, transform first language in set as defaultLangCode
        if (!codeSystem.has("languages")) {

            throw new Exception("All Code Systems must have lanaguages set filled in. " + snowstormEditionName + " does not");
        }

        Iterator<String> languages = codeSystem.get("languages").fieldNames();

        return languages.next();
    }

    private Set<String> identifyDefaultLanguageRefsets(JsonNode codeSystem, String snowstormEditionName) {

        Set<String> retSet = new HashSet<>();

        // Identify Edition's Default Language Refsets
        if (codeSystem.has("defaultLanguageReferenceSets")) {

            final JsonNode defaultLanguageReferenceSets = codeSystem.get("defaultLanguageReferenceSets");
            final Iterator<JsonNode> defaultLanguageReferencesSetIterator = defaultLanguageReferenceSets.iterator();

            while (defaultLanguageReferencesSetIterator.hasNext()) {

                retSet.add(defaultLanguageReferencesSetIterator.next().asText());
            }

        } else if (undefinedDefaultLanguageRefsets.containsKey(snowstormEditionName)) {

            retSet.addAll(undefinedDefaultLanguageRefsets.get(snowstormEditionName));
            logger.debug("No defined Default Language Refsets for " + snowstormEditionName + ", so adding from txt file: " + undefinedDefaultLanguageRefsets.get(snowstormEditionName));
        }

        // Ensure that DEFAULT_LANG_REFSET is always listed even if not explicitely listed
        retSet.add(DEFAULT_LANGUAGE_REFSET);

        return retSet;
    }

    private String identifyTopLevelModule(String editionName, String shortName, String editionBranch, JsonNode codeSystem) throws Exception {

        if ("international edition".equals(editionName.toLowerCase())) {

            return MigrationUtilities.MODULE_ANCESTOR_CONCEPT_SCTID;
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
                logger.info("Didn't identify dedicated module for " + editionName + ": " + editionModules.toString() + ", so using default: " + MigrationUtilities.MODULE_ANCESTOR_CONCEPT_SCTID);
                return MigrationUtilities.MODULE_ANCESTOR_CONCEPT_SCTID;
            } else if (editionModules.size() == 1) {

                // If only one non-CORE modules found, use it
                return editionModules.iterator().next();
            } else {

                logger.info("Have multiple modules identified for " + editionName + ": " + editionModules.toString());

                // If multiple non-CORE modules found, TODO: Fill in
                Set<String> childrenModules = new HashSet<>();

                Set<String> children = getModuleChildren(editionBranch);

                for (String moduleId : editionModules) {

                    if (children.contains(moduleId)) {

                        childrenModules.add(moduleId);
                    }

                }

                // TODO: Remove Hard coded solution for Netherlands and
                // Australia -> These are from previous test data and are deprecated
                if (shortName.equals("SNOMEDCT-NL")) {

                    childrenModules.remove("15561000146104"); // 15561000146104
                                                              // - Represents
                                                              // Patient
                                                              // Friendly Terms
                } else if (shortName.equals("SNOMEDCT-AU")) {

                    childrenModules.add("32570231000036109");
                }

                // TODO: Handle hard coded solution for Norway & US
                if (shortName.equals("SNOMEDCT-NO")) {

                    childrenModules.remove("57091000202101");
                    childrenModules.remove("57101000202106");
                } else if (shortName.equals("SNOMEDCT-US")) {

                    childrenModules.remove("5991000124107");
                }

                if (childrenModules.size() == 0 || childrenModules.size() > 1) {

                    String msg = "Seeing odd number of modules during secondary analysis for " + editionName + ": " + childrenModules.toString();

                    logger.info(msg);
                    throw new Exception("This situation shouldn't happen during sync: " + msg);
                } else {

                    return childrenModules.iterator().next();
                }

            }

        }

    }

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

    private Set<String> getModuleChildren(String branch) throws Exception {

        String url = SnowstormConnection.BASE_URL + "browser/" + branch + "/concepts/" + MigrationUtilities.MODULE_ANCESTOR_CONCEPT_SCTID + "/children";
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

    private void initializeService(TerminologyService service) {

        service.setModifiedBy("Migration");
        service.setModifiedFlag(true);

    }
}
