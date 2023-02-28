package org.ihtsdo.refsetservice.sync;

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

public class SyncCodeSystemAgent extends SyncAgent {

    private static Logger logger = LoggerFactory.getLogger(SyncCodeSystemAgent.class);

    private static final String DEVELOPER_CODE_SYSTEM_SHORTNAME = "SNOMEDCT-WCI";

    private static final Set<String> codeSystemsNewAndInactive = new HashSet<>();

    private static final String DEFAULT_ORGANIZATION_PREFACE = "Owner of ";

    private static final Map<String, String> editionShortNameOrganizationNameMap = new HashMap<>();

    private HashMap<String, String> snowstormEditionShortNameToOrganizationNameMap;

    public void sync() throws Exception {

        initializeSync();

        // Get all code systems from Snowstorm
        final JsonNode organizationJsonRootNode = getSnowstormCodeSystems();

        // Identify code systems to sync
        analyzeCodeSystems(organizationJsonRootNode);

        // Sync Organizations reviewing which are new (creating them), missing (removing them), and unchanged.
        analyzeOrganizations();

        // Sync Editions reviewing which are new (creating them), missing (removing them), modified (removing them and then creating them), and unchanged.
        List<String> existingShortNames = analyzeEditions();
        int a = 0;
        if (a < 1) {
            printStatistics();
            throw new Exception("Failed on purpose");
        }

        compareEditionOrganizationMaps(existingShortNames);

        // TODO: For now, ignore this, but shouldn't ever throw exception at this point
        if (developerTestingEdition == null && !getIsProductionSystem()) {
            // throw new Exception("Must have a WCI Organization on a non-Prod instance");
        }
    }

    private void compareEditionOrganizationMaps(List<String> existingShortNames) throws Exception {
        try (TerminologyService service = new TerminologyService()) {

            utilities.initializeService(service);

            List<Edition> dbEditions = service.getAll(Edition.class);
            List<Organization> dbOrganizations = service.getAll(Organization.class);

            for (String shortName : existingShortNames) {

                // Prepare DB edition for analysis
                List<Edition> matchingDbEditions = dbEditions.stream().filter(e -> e.getShortName().equals(shortName)).collect(Collectors.toList());
                utilities.validateMatches(matchingDbEditions, shortName);
                Edition dbEdition = matchingDbEditions.iterator().next();
                String dbOrganizationName = dbEdition.getOrganizationName();

                // Prepare snowstorm edition for analysis
                List<JsonNode> matchingSnowstormEditions = filteredCodeSystems.stream().filter(cs -> cs.get("shortName").asText().equals(shortName)).collect(Collectors.toList());
                utilities.validateMatches(matchingSnowstormEditions, shortName);
                JsonNode snowstormEdition = matchingSnowstormEditions.iterator().next();
                String snowstormOrganizationName = identifyOrganizationName(snowstormEdition);

                // compare
                if (!dbOrganizationName.equals(snowstormOrganizationName)) {

                    List<Organization> matchedOrganizations = dbOrganizations.stream().filter(o -> o.getName().equals(snowstormOrganizationName)).collect(Collectors.toList());
                    utilities.validateMatches(matchedOrganizations, snowstormOrganizationName);

                    dbEdition.setOrganization(matchedOrganizations.iterator().next());
                    service.update(dbEdition);

                    logger.info("Updated edition's Organization: " + dbEdition.getId() + " (" + dbEdition.getName() + ") " + dbEdition);
                }
            }
        }
    }

    private void initializeSync() throws Exception {

        updateDatabaseCache();

        codeSystemsNewAndInactive.clear();
        editionShortNameOrganizationNameMap.clear();

        try (TerminologyService service = new TerminologyService()) {

            service.getAll(Edition.class).stream().forEach(e -> editionShortNameOrganizationNameMap.put(e.getShortName(), e.getOrganization().getName()));
        }
    }

    private List<String> analyzeEditions() throws Exception {
        final Set<String> dbInactiveEditionShortNames = new HashSet<>();
        final Set<String> dbActiveEditionShortNames = new HashSet<>();
        final Map<String, JsonNode> snowstormShortNameCodeSystemMap = new HashMap<>();

        try (TerminologyService service = new TerminologyService()) {
            // Identify new, inactivated, and existing codeSystems (Based on shortName)
            List<Edition> allEditions = service.getAll(Edition.class);
            allEditions.stream().filter(e -> e.isActive()).forEach(ea -> dbActiveEditionShortNames.add(ea.getShortName()));
            allEditions.stream().filter(e -> !e.isActive()).forEach(ea -> dbInactiveEditionShortNames.add(ea.getShortName()));

            // Based on filteredCodeSystems which already filtered for active code systems
            filteredCodeSystems.stream().forEach(cs -> snowstormShortNameCodeSystemMap.put(cs.get("shortName").asText(), cs));

            // Identify and create new editions (not in active nor in inactive DB editions)
            List<String> newShortNames =
                    snowstormShortNameCodeSystemMap.keySet().stream().filter(c -> !dbActiveEditionShortNames.contains(c) && !dbInactiveEditionShortNames.contains(c)).collect(Collectors.toList());
            statistics.setEditionsAdded(newShortNames.size());
            logger.debug("ccc New Editions: " + newShortNames);
            for (String shortName : newShortNames) {
                Edition newEdition = utilities.addNewEdition(snowstormShortNameCodeSystemMap.get(shortName), snowstormEditionShortNameToOrganizationNameMap.get(shortName));
                postCodeSystemProcessing(newEdition);
            }

            // Activate previously inactivated editions. Note: Will log and update stats after remove those that were activatedAndModified
            // TODO: Define solution although for now simply activating
            List<String> activatedShortNames = snowstormShortNameCodeSystemMap.keySet().stream().filter(c -> dbInactiveEditionShortNames.contains(c)).collect(Collectors.toList());
            activatedShortNames.stream().forEach(n -> utilities.updateEditionStatus(n, true));

            // Inactivate active DB editions that are not in snowstorm
            // TODO: Define solution although for now simply inactivating
            List<String> inactivatedShortNames = dbActiveEditionShortNames.stream().filter(c -> !snowstormShortNameCodeSystemMap.keySet().contains(c)).collect(Collectors.toList());
            statistics.setEditionsInactivated(inactivatedShortNames.size());
            logger.debug("ccc Inactivated Editions: : " + inactivatedShortNames);
            inactivatedShortNames.stream().forEach(n -> utilities.updateEditionStatus(n, false));

            // Identify editions that are active in DB and found in snowstorm and compare for changes
            List<String> existingShortNames = dbActiveEditionShortNames.stream().filter(c -> snowstormShortNameCodeSystemMap.keySet().contains(c)).collect(Collectors.toList());
            List<String> modifiedShortNames = compareAndModifyEditions(existingShortNames, snowstormShortNameCodeSystemMap);
            List<String> unchangedShortNames = existingShortNames.stream().filter(e -> !modifiedShortNames.contains(e)).collect(Collectors.toList());
            statistics.setEditionsUnchanged(unchangedShortNames.size());
            statistics.setEditionsModified(modifiedShortNames.size());
            logger.debug("ccc Unchanged Editions: : " + unchangedShortNames);
            logger.debug("ccc Modified Editions: " + modifiedShortNames);

            // Identify editions that were just actived to see if there are any other changes necessary
            List<String> activatedAndModifiedShortNames = compareAndModifyEditions(activatedShortNames, snowstormShortNameCodeSystemMap);
            statistics.setEditionsActivatedAndModified(activatedAndModifiedShortNames.size());
            logger.debug("ccc ActivatedAndModified Editions: " + activatedAndModifiedShortNames);

            // Finalize those editions that were only activated (and not further modified)
            activatedAndModifiedShortNames.stream().forEach(n -> activatedShortNames.remove(n));
            statistics.setEditionsActivated(activatedShortNames.size());
            logger.debug("ccc Activated Editions: " + activatedShortNames);

            return existingShortNames;
        }

    }

    private List<String> analyzeOrganizations() throws Exception {
        List<String> existingShortNames = new ArrayList<>();

        HashMap<String, Set<String>> dbActiveOrganizationNameToEditionsShortNameMap = new HashMap<>();
        HashMap<String, Set<String>> dbInactiveOrganizationNameToEditionsShortNameMap = new HashMap<>();
        HashMap<String, Set<String>> snowstormOrganizationNameToEditionsShortNameMap = new HashMap<>();
        HashMap<String, String> dbActiveEditionShortNameToOrganizationNameMap = new HashMap<>();

        snowstormEditionShortNameToOrganizationNameMap = new HashMap<>();

        try (final TerminologyService service = new TerminologyService()) {

            final List<Edition> dbEditions = service.getAll(Edition.class);

            // Identify DB edition-to-orgName bi-directional maps (separated by active/inactive editions)
            for (Edition edition : dbEditions) {
                dbActiveEditionShortNameToOrganizationNameMap.put(edition.getShortName(), edition.getOrganizationName());

                if (edition.isActive()) {

                    if (!dbActiveOrganizationNameToEditionsShortNameMap.keySet().contains(edition.getOrganizationName())) {
                        dbActiveOrganizationNameToEditionsShortNameMap.put(edition.getOrganizationName(), new HashSet<String>());
                    }

                    dbActiveOrganizationNameToEditionsShortNameMap.get(edition.getOrganizationName()).add(edition.getShortName());

                } else {

                    if (!dbInactiveOrganizationNameToEditionsShortNameMap.keySet().contains(edition.getOrganizationName())) {
                        dbInactiveOrganizationNameToEditionsShortNameMap.put(edition.getOrganizationName(), new HashSet<String>());
                    }

                    dbInactiveOrganizationNameToEditionsShortNameMap.get(edition.getOrganizationName()).add(edition.getShortName());

                }
            }
            // logger.debug("aaa with dbEditionShortNameToOrganizationNameMap: " + dbEditionShortNameToOrganizationNameMap);
            // logger.debug("aaa with dbOrganizationNameToEditionsShortNameMap: " + dbOrganizationNameToEditionsShortNameMap);
            // Identify Snow edition-to-orgName bi-directional maps
            for (JsonNode codeSystem : filteredCodeSystems) {
                String organizationName = identifyOrganizationName(codeSystem);

                snowstormEditionShortNameToOrganizationNameMap.put(codeSystem.get("shortName").asText(), organizationName);

                if (!snowstormOrganizationNameToEditionsShortNameMap.keySet().contains(organizationName)) {
                    snowstormOrganizationNameToEditionsShortNameMap.put(organizationName, new HashSet<String>());
                }

                snowstormOrganizationNameToEditionsShortNameMap.get(organizationName).add(codeSystem.get("shortName").asText());
            }

            // logger.debug("aaa with snowstormEditionShortNameToOrganizationNameMap: " + snowstormEditionShortNameToOrganizationNameMap);
            // logger.debug("aaa with snowstormOrganizationNameToEditionsShortNameMap: " + snowstormOrganizationNameToEditionsShortNameMap);

            logger.debug("ccc --- Begin execution ---");
            // See if any snowstorm organizations are new
            List<String> newOrganizations = snowstormOrganizationNameToEditionsShortNameMap.keySet().stream()
                    .filter(o -> !dbActiveOrganizationNameToEditionsShortNameMap.keySet().contains(o) && !dbInactiveOrganizationNameToEditionsShortNameMap.keySet().contains(o))
                    .collect(Collectors.toList());
            statistics.setOrganizationsAdded(newOrganizations.size());
            logger.debug("ccc New Organizations: " + newOrganizations);

            for (String organizationName : newOrganizations) {
                utilities.addOrganziation(organizationName, identifyOrganizationDescription(organizationName));
            }

            // Activate previously inactivated organizations. Note: Will log and update stats after remove those that were activatedAndModified
            // TODO: Define solution although for now simply activating
            List<String> activatedShortNames =
                    snowstormOrganizationNameToEditionsShortNameMap.keySet().stream().filter(c -> dbInactiveOrganizationNameToEditionsShortNameMap.keySet().contains(c)).collect(Collectors.toList());
            activatedShortNames.stream().forEach(n -> utilities.updateOrganizationStatus(n, true));

            // Inactivate active DB organizations that are not in snowstorm
            // TODO: Define solution although for now simply inactivating
            List<String> inactivatedShortNames =
                    dbActiveOrganizationNameToEditionsShortNameMap.keySet().stream().filter(c -> !snowstormOrganizationNameToEditionsShortNameMap.keySet().contains(c)).collect(Collectors.toList());
            statistics.setOrganizationsInactivated(inactivatedShortNames.size());
            logger.debug("ccc Inactivated Organizations: : " + inactivatedShortNames);
            inactivatedShortNames.stream().forEach(n -> utilities.updateOrganizationStatus(n, false));
            logger.debug("ccc1 dbActiveOrganizationNameToEditionsShortNameMap: " + dbActiveOrganizationNameToEditionsShortNameMap);

            // Identify organizations that are active in DB and found in snowstorm and compare for changes
            dbActiveEditionShortNameToOrganizationNameMap.keySet().stream().forEach(c -> existingShortNames.add(c));
            List<String> existingShortNamesAsList = new ArrayList<>(existingShortNames);

            logger.debug("ccc2 existingShortNamesAsList: " + existingShortNamesAsList);
            List<String> modifiedShortNames = compareAndModifyOrganizations(existingShortNamesAsList);
            logger.debug("ccc3");
            List<String> unchangedShortNames = existingShortNames.stream().filter(e -> !modifiedShortNames.contains(e)).collect(Collectors.toList());
            logger.debug("ccc4");
            statistics.setOrganizationsUnchanged(unchangedShortNames.size());
            logger.debug("ccc5");
            statistics.setOrganizationsModified(modifiedShortNames.size());
            logger.debug("ccc Unchanged Organizations: : " + unchangedShortNames);
            logger.debug("ccc Modified Organizations: " + modifiedShortNames);

            // Identify organizations that were just actived to see if there are any other changes necessary
            List<String> activatedAndModifiedShortNames = compareAndModifyOrganizations(activatedShortNames);
            statistics.setOrganizationsActivatedAndModified(activatedAndModifiedShortNames.size());
            logger.debug("ccc ActivatedAndModified Organizations: " + activatedAndModifiedShortNames);

            // Finalize those organizations that were only activated (and not further modified)
            activatedAndModifiedShortNames.stream().forEach(n -> activatedShortNames.remove(n));
            statistics.setOrganizationsActivated(activatedShortNames.size());
            logger.debug("ccc Activated Organizations: " + activatedShortNames);

            return existingShortNames;

        }
    }

    /*
     * Note -> ignore description as that is strictly defined in RT2 whereas the other organization attributes are defined on snowstorm (only name for now)
     */
    private List<String> compareAndModifyOrganizations(List<String> matchingShortNames) throws Exception {
        List<String> modifiedShortNames = new ArrayList<>();

        try (final TerminologyService service = new TerminologyService()) {

            utilities.initializeService(service);

            List<Edition> allDatabaseEditions = service.getAll(Edition.class);

            // Process one Organization per Edition.
            for (String shortName : matchingShortNames) {

                if (!snowstormEditionShortNameToOrganizationNameMap.containsKey(shortName)) {
                    logger.debug(shortName + " is not being compared for changes in Edition as not filtered in snowstorm");
                    continue;
                }

                // Find associated DB edition and get org name
                List<Edition> matchingEditions = allDatabaseEditions.stream().filter(e -> e.isActive() && e.getShortName().equals(shortName)).collect(Collectors.toList());
                utilities.validateMatches(matchingEditions, shortName);
                Organization dbOrganization = matchingEditions.iterator().next().getOrganization();

                // Grab snowstorm org name
                String snowstormOrganizationName = snowstormEditionShortNameToOrganizationNameMap.get(shortName);
                logger.debug("ddd shortName: " + shortName);
                logger.debug("ddd snowstormEditionShortNameToOrganizationNameMap: " + snowstormEditionShortNameToOrganizationNameMap);
                logger.debug("ddd snowstormOrganizationName: " + snowstormOrganizationName);

                boolean modificationMade = false;
                Organization newOrganization = new Organization(dbOrganization);

                if (isDifferentAttribute(shortName, "Organization name ", dbOrganization.getName(), snowstormOrganizationName)) {
                    logger.info(" inconsistent editionName with DB having '" + dbOrganization.getName() + "' and snowstorm with'" + snowstormOrganizationName + "'");
                    newOrganization.setName(snowstormOrganizationName);
                    modificationMade = true;
                }

                if (modificationMade) {
                    service.update(newOrganization);
                    logger.info("Updated edition: " + newOrganization.getId() + " (" + newOrganization.getName() + ") " + newOrganization);

                    modifiedShortNames.add(shortName);
                }
            }
        }

        return modifiedShortNames;
    }

    private String identifyOrganizationDescription(String organizationName) {

        if (organizationName.startsWith(DEFAULT_ORGANIZATION_PREFACE)) {

            return "Organizational administrators can update this edition's default description.";
        } else {

            return "Two things to change." + System.lineSeparator()
                    + "1) Your organization name isn't defined on Snowstorm yet, so we have provided you with a temporary one that matches your edition name." + System.lineSeparator()
                    + "Have your organization's administrator(s) contact SNOMED International to have it changed." + System.lineSeparator()
                    + "2) Organizational administrator(s) can update this default description at any time";
        }
    }

    private String identifyOrganizationName(JsonNode codeSystem) {

        // If owner defined, return it as organization name
        if (codeSystem.has("owner") && !codeSystem.get("owner").asText().trim().isBlank()) {
            return codeSystem.get("owner").asText();
        }

        // Create generic organization name
        final String editionName = codeSystem.has("name") ? codeSystem.get("name").asText() : "";
        return DEFAULT_ORGANIZATION_PREFACE + editionName;
    }

    private void analyzeCodeSystems(JsonNode organizationJsonRootNode) throws Exception {

        final Iterator<JsonNode> organizationIterator = organizationJsonRootNode.iterator();
        logger.info("Found " + countCodeSystems(organizationIterator) + " + Code Systems on Snowstorm: " + organizationJsonRootNode);

        // Filter code systems (based on active-setting, ignoredCS list, testing situation, and bad data)
        filterCodeSystems(organizationJsonRootNode);
        logger.info("Will be processing only these " + filteredCodeSystems.size() + " Code Systems: " + filteredCodeSystems);
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

    private List<String> compareAndModifyEditions(List<String> matchingEditionShortNames, Map<String, JsonNode> snowstormShortNameCodeSystemMap) throws Exception {
        List<String> modifiedShortNames = new ArrayList<>();

        try (final TerminologyService service = new TerminologyService()) {

            List<Edition> allDatabaseEditions = service.getAll(Edition.class);

            // Process one Organization per Edition.
            for (String shortName : matchingEditionShortNames) {

                if (!snowstormShortNameCodeSystemMap.containsKey(shortName)) {
                    logger.debug(shortName + " is not being compared for changes in Edition as not filtered in snowstorm");
                    continue;
                }

                // Find associated DB edition
                List<Edition> matchingEditions = allDatabaseEditions.stream().filter(e -> e.isActive() && e.getShortName().equals(shortName)).collect(Collectors.toList());
                utilities.validateMatches(matchingEditions, shortName);
                Edition dbEdition = matchingEditions.iterator().next();
                Edition newEdition = new Edition(dbEdition);

                // Find values for Snowstorm Edition
                JsonNode codeSystem = snowstormShortNameCodeSystemMap.get(shortName);
                final String editionName = codeSystem.has("name") ? codeSystem.get("name").asText() : "";
                final String branch = codeSystem.has("branchPath") ? codeSystem.get("branchPath").asText() : "";
                final String maintainerType = utilities.identifyMaintainerType(codeSystem, editionName);
                final Set<String> editionModules = utilities.identifyModules(shortName, editionName, branch, codeSystem);

                boolean modificationMade = false;

                if (isDifferentAttribute(dbEdition.getShortName(), "Edition name ", dbEdition.getName(), editionName)) {
                    logger.info(" inconsistent editionName with DB having '" + dbEdition.getName() + "' and snowstorm with'" + editionName + "'");
                    newEdition.setName(editionName);
                    modificationMade = true;
                }

                if (isDifferentAttribute(dbEdition.getShortName(), "Edition branch ", dbEdition.getBranch(), branch)) {
                    logger.info(" inconsistent branch with DB having '" + dbEdition.getBranch() + "' and snowstorm with'" + branch + "'");
                    newEdition.setBranch(branch);
                    modificationMade = true;
                }

                if (isDifferentAttribute(dbEdition.getShortName(), "Edition modules ", dbEdition.getModules(), editionModules)) {
                    logger.info(" inconsistent editionModules with DB having '" + dbEdition.getModules() + "' and snowstorm with'" + editionModules + "'");
                    newEdition.setModules(editionModules);
                    modificationMade = true;
                }

                if (isDifferentAttribute(dbEdition.getShortName(), "Edition maintainerType ", dbEdition.getMaintainerType(), maintainerType)) {
                    logger.info(" inconsistent maintainerType with DB having '" + dbEdition.getMaintainerType() + "' and snowstorm with'" + maintainerType + "'");
                    newEdition.setMaintainerType(maintainerType);
                    modificationMade = true;
                }

                final String snowstormDefaultLanguageCode = utilities.identifyDefaultLanguageCode(codeSystem, editionName);
                if (isDifferentAttribute(dbEdition.getShortName(), "Edition defaultLanguageCode ", dbEdition.getDefaultLanguageCode(),
                        utilities.identifyDefaultLanguageCode(codeSystem, editionName))) {
                    logger.info(" inconsistent defaultLanguageCode with DB having '" + dbEdition.getDefaultLanguageCode() + "' and snowstorm with'" + snowstormDefaultLanguageCode + "'");

                    newEdition.setDefaultLanguageCode(snowstormDefaultLanguageCode);
                    modificationMade = true;
                }

                final Set<String> snowstormDefaultLanguageRefsets = utilities.identifyDefaultLanguageRefsets(codeSystem, shortName);
                if (!dbEdition.getDefaultLanguageRefsets().equals(snowstormDefaultLanguageRefsets)) {

                    logger.info(" inconsistent defaultLanguageRefsets with DB having '" + dbEdition.getDefaultLanguageRefsets() + "' and snowstorm with'" + snowstormDefaultLanguageRefsets + "'");

                    newEdition.setDefaultLanguageRefsets(snowstormDefaultLanguageRefsets);
                    modificationMade = true;
                }

                if (modificationMade) {
                    service.update(newEdition);
                    logger.info("Updated edition: " + newEdition.getId() + " (" + newEdition.getName() + ") " + newEdition);

                    modifiedShortNames.add(newEdition.getShortName());
                }
            }
        }

        return modifiedShortNames;
    }

    private Set<JsonNode> filterCodeSystems(JsonNode organizationJsonRootNode) throws Exception {

        final Iterator<JsonNode> organizationIterator = organizationJsonRootNode.iterator();

        while (organizationIterator.hasNext()) {

            final Iterator<JsonNode> codeSystems = organizationIterator.next().iterator();

            while (codeSystems.hasNext()) {

                JsonNode codeSystem = codeSystems.next();

                // Check for invalid or ignored code systems
                if (!codeSystem.has("shortName")) {

                    // Skipping odd code system without a shortName
                    logger.error("Skipping codeSystem without a shortName: " + codeSystem);
                    continue;
                }

                final String editionShortName = codeSystem.get("shortName").asText();
                final String maintainerType = utilities.identifyMaintainerType(codeSystem, editionShortName);

                // If not testing, process all editions. Otherwise, check if edition to test
                if (isTesting() && !isTestingEditionToProcess(editionShortName)) {
                    logger.info("In testing, but ignoring, codesystem " + editionShortName + " as not defined as the testing edition");

                } else if (codeSystem.has("active") && !codeSystem.get("active").asBoolean()) {
                    // Skipping inactive code system
                    logger.info("Skipping inactive codeSystem: " + codeSystem);

                } else if (utilities.getPropertyReader().getCodeSystemsToIgnore().contains(editionShortName)) {

                    // Code System has been defined as to-be-ignored (either by specifying name or shortname).
                    logger.info("Skipping codesystem " + editionShortName + " as it's listed in ignoredCodeSystems.txt");

                } else if (!maintainerType.equalsIgnoreCase("Managed Service")) {

                    // TODO: Remove once have handled more than Managed Service only
                    // Ensure only processing Managed Service editions
                    logger.info("Skipping codesystem " + editionShortName + " as is of maintainerType: " + maintainerType);

                } else {
                    filteredCodeSystems.add(codeSystem);
                }

            }

        }

        filteredCodeSystems.stream().forEach(c -> logger.info("Will process codeSystem: " + c));

        statistics.setCodeSystemsSynced(countCodeSystems(organizationIterator));
        statistics.setCodeSystemsFiltered(filteredCodeSystems.size());

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

    // Organization is done at this point. Check if Developer Edition. If not, create a default UAT project
    private void postCodeSystemProcessing(Edition syncedEdition) throws Exception {

        int a = 0;
        if (a < 1) {
            return;
        }

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

    private boolean isTestingEditionToProcess(String codeSystem) {

        return ((testingEditionShortName == null || testingEditionShortName.isEmpty()) || codeSystem.equalsIgnoreCase(testingEditionShortName) || utilities.isInternationalEdition(codeSystem));

    }
}