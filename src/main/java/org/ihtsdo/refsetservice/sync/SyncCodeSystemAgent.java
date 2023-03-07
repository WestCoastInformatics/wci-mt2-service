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

    private static final Set<String> codeSystemsNewAndInactive = new HashSet<>();

    private static final String DEFAULT_ORGANIZATION_PREFACE = "Owner of ";

    private static final Map<String, String> editionShortNameOrganizationNameMap = new HashMap<>();

    private HashMap<String, String> termserverEditionShortNameToOrganizationNameMap;

    private List<Edition> dbEditions;

    private List<Organization> dbOrganizations;

    public void sync() throws Exception {

        initializeSync();

        // Get all code systems from Snowstorm
        final JsonNode organizationJsonRootNode = getSnowstormCodeSystems();

        // Determine code systems to sync
        analyzeCodeSystems(organizationJsonRootNode);

        // Sync Organizations reviewing which are new (creating them), missing (removing them), and unchanged.
        analyzeOrganizationsAndEditions();

        // Sync Editions reviewing which are new (creating them), missing (removing them), modified (removing them and then creating them), and unchanged.
        List<String> existingInBothShortNames = analyzeEditions();

        // Review both DB & Snowstorm editon-to-org map to ensure consistency
        compareEditionOrganizationMaps(existingInBothShortNames);
    }

    private void analyzeCodeSystems(JsonNode organizationJsonRootNode) throws Exception {

        final Iterator<JsonNode> organizationIterator = organizationJsonRootNode.iterator();
        logger.info("Found " + countCodeSystems(organizationIterator) + " + Code Systems on Snowstorm: ");

        // Filter code systems (based on active-setting, ignoredCS list, testing situation, and bad data)
        filterCodeSystems(organizationJsonRootNode);
        logger.info("Will be processing only these " + filteredCodeSystems.size() + " Code Systems: ");

        filteredCodeSystems.stream().forEach(c -> logger.info("Will process codeSystem: " + c.get("shortName").asText()));

        statistics.setCodeSystemsSynced(countCodeSystems(organizationIterator));
        statistics.setCodeSystemsFiltered(filteredCodeSystems.size());
    }

    private List<String> analyzeOrganizationsAndEditions() throws Exception {
        List<String> existingInBothShortNames = new ArrayList<>();

        final Map<String, String> dbActiveEditionShortNameToOrganizationNameMap = new HashMap<>();
        final Map<String, Set<String>> activeDbOrganizationNameToEditionsShortNameMap = new HashMap<>();
        final Map<String, Set<String>> inactiveDbOrganizationNameToEditionsShortNameMap = new HashMap<>();
        final Map<String, Set<String>> termserverOrganizationNameToEditionsShortNameMap = new HashMap<>();
        final Set<String> activeDbOrganizationNames = new HashSet<>();
        final Set<String> inactiveDbOrganizationNames = new HashSet<>();
        final Set<String> termserverOrganizationNames = new HashSet<>();

        termserverEditionShortNameToOrganizationNameMap = new HashMap<>();

        try (final TerminologyService service = new TerminologyService()) {

            // Determine DB edition-to-orgName bi-directional maps (separated by active/inactive editions)
            for (Edition dbEdition : dbEditions) {

                if (dbEdition.isActive()) {
                    dbActiveEditionShortNameToOrganizationNameMap.put(dbEdition.getShortName(), dbEdition.getOrganizationName());

                    if (!activeDbOrganizationNameToEditionsShortNameMap.keySet().contains(dbEdition.getOrganizationName())) {
                        activeDbOrganizationNameToEditionsShortNameMap.put(dbEdition.getOrganizationName(), new HashSet<String>());
                    }

                    activeDbOrganizationNameToEditionsShortNameMap.get(dbEdition.getOrganizationName()).add(dbEdition.getShortName());

                    // Create a Default Project for the edition if doesnt' already exist
                    if (!defaultEditionProjects.containsKey(dbEdition.getShortName())) {

                        final String projectName = dbEdition.getName() + " Default Project";
                        final String projectDescription =
                                "This is a project to support all refsets not already associated with a project in the Refset & Translation Tool for " + dbEdition.getName() + ".";

                        // Create default project
                        final Project project = dbHandler.addProject(projectName, projectDescription, dbEdition);
                        defaultEditionProjects.put(dbEdition.getShortName(), project);
                    }

                } else {

                    if (!inactiveDbOrganizationNameToEditionsShortNameMap.keySet().contains(dbEdition.getOrganizationName())) {
                        inactiveDbOrganizationNameToEditionsShortNameMap.put(dbEdition.getOrganizationName(), new HashSet<String>());
                    }

                    inactiveDbOrganizationNameToEditionsShortNameMap.get(dbEdition.getOrganizationName()).add(dbEdition.getShortName());

                }

            }
            // Determine Snow edition-to-orgName bi-directional maps
            for (JsonNode codeSystem : filteredCodeSystems) {
                String organizationName = determineOrganizationName(codeSystem);

                termserverEditionShortNameToOrganizationNameMap.put(codeSystem.get("shortName").asText(), organizationName);

                if (!termserverOrganizationNameToEditionsShortNameMap.keySet().contains(organizationName)) {
                    termserverOrganizationNameToEditionsShortNameMap.put(organizationName, new HashSet<String>());
                }

                termserverOrganizationNameToEditionsShortNameMap.get(organizationName).add(codeSystem.get("shortName").asText());
            }

            // To simplify, create meaningfully named collections
            activeDbOrganizationNames.addAll(activeDbOrganizationNameToEditionsShortNameMap.keySet());
            inactiveDbOrganizationNames.addAll(inactiveDbOrganizationNameToEditionsShortNameMap.keySet());
            termserverOrganizationNames.addAll(termserverOrganizationNameToEditionsShortNameMap.keySet());

            // See if any termserver organizations are new
            List<String> addedOrganizations =
                    termserverOrganizationNames.stream().filter(o -> !activeDbOrganizationNames.contains(o) && !inactiveDbOrganizationNames.contains(o)).collect(Collectors.toList());
            addedOrganizations.stream().forEach(name -> dbHandler.addOrganziation(name, determineOrganizationDescription(name)));
            statistics.setOrganizationsAdded(addedOrganizations.size());

            // Activate previously inactivated organizations. Note: Will log and update stats after remove those that were activatedAndModified
            // TODO: Define solution although for now simply activating
            List<String> activatedShortNames = termserverOrganizationNames.stream().filter(c -> inactiveDbOrganizationNames.contains(c)).collect(Collectors.toList());
            activatedShortNames.stream().forEach(n -> dbHandler.updateOrganizationStatus(n, true));

            // If have active organizations:
            // 1) Inactivate any active DB organizations that are not in termserver
            // 2) Compare against termserver
            // TODO: Define solution although for now simply inactivating
            if (!activeDbOrganizationNames.isEmpty()) {

                List<String> inactivatedShortNames = activeDbOrganizationNames.stream().filter(c -> !termserverOrganizationNames.contains(c)).collect(Collectors.toList());
                inactivatedShortNames.stream().forEach(n -> dbHandler.updateOrganizationStatus(n, false));
                statistics.setOrganizationsInactivated(inactivatedShortNames.size());

                // Determine organizations that are active in DB and found in termserver and compare for changes
                dbActiveEditionShortNameToOrganizationNameMap.keySet().stream().forEach(sn -> existingInBothShortNames.add(sn));

                // Compare organizations in termserver & active in db
                if (!existingInBothShortNames.isEmpty()) {
                    List<String> modifiedShortNames = compareAndModifyOrganizations(new ArrayList<>(existingInBothShortNames));
                    List<String> unchangedShortNames = existingInBothShortNames.stream().filter(e -> !modifiedShortNames.contains(e)).collect(Collectors.toList());

                    // Have modified... now revisit acivated to see if they too are modified
                    statistics.setOrganizationsUnchanged(unchangedShortNames.size());
                    statistics.setOrganizationsModified(modifiedShortNames.size());
                }
            }

            // Compare organizations in termserver & newly actived in db
            if (!activatedShortNames.isEmpty()) {
                // Determine organizations that were just activated to see if there are any other changes necessary
                List<String> activatedAndModifiedShortNames = compareAndModifyOrganizations(activatedShortNames);
                statistics.setOrganizationsActivatedAndModified(activatedAndModifiedShortNames.size());

                // Finalize those organizations that were only activated (and not further modified)
                activatedAndModifiedShortNames.stream().forEach(n -> activatedShortNames.remove(n));
                statistics.setOrganizationsActivated(activatedShortNames.size());
            }

            return existingInBothShortNames;

        }
    }

    private List<String> analyzeEditions() throws Exception {
        final Map<String, JsonNode> termserverShortNameCodeSystemMap = new HashMap<>();
        final List<String> existingInBothShortNames = new ArrayList<>();
        final Set<String> inactiveDbEditionShortNames = new HashSet<>();
        final Set<String> activeDbEditionShortNames = new HashSet<>();
        final Set<String> termserverShortNames = new HashSet<>();

        try (TerminologyService service = new TerminologyService()) {

            // Determine new, inactivated, and existing codeSystems (Based on shortName)
            dbEditions.stream().filter(e -> e.isActive()).forEach(ea -> activeDbEditionShortNames.add(ea.getShortName()));
            dbEditions.stream().filter(e -> !e.isActive()).forEach(ea -> inactiveDbEditionShortNames.add(ea.getShortName()));

            // Based on filteredCodeSystems which already filtered for active code systems
            filteredCodeSystems.stream().forEach(cs -> termserverShortNameCodeSystemMap.put(cs.get("shortName").asText(), cs));
            termserverShortNames.addAll(termserverShortNameCodeSystemMap.keySet());
            termserverShortNames.stream().filter(shortName -> DEVELOPER_CODE_SYSTEM_SHORTNAME.equalsIgnoreCase(shortName)).forEach(shortName -> developerTestingEditionShortName = shortName);

            // Determine and create new editions (not in active nor in inactive DB editions)
            List<String> newShortNames = termserverShortNames.stream().filter(c -> !activeDbEditionShortNames.contains(c) && !inactiveDbEditionShortNames.contains(c)).collect(Collectors.toList());
            statistics.setEditionsAdded(newShortNames.size());
            newShortNames.stream().forEach(shortName -> dbHandler.addEdition(termserverShortNameCodeSystemMap.get(shortName), termserverEditionShortNameToOrganizationNameMap.get(shortName)));

            // Activate previously inactivated editions. Note: Will log and update stats after remove those that were activatedAndModified
            // TODO: Define solution although for now simply activating
            List<String> activatedShortNames = termserverShortNames.stream().filter(c -> inactiveDbEditionShortNames.contains(c)).collect(Collectors.toList());
            activatedShortNames.stream().forEach(n -> dbHandler.updateEditionStatus(n, true));

            // TODO: Define solution although for now simply inactivating
            // If have active editions:
            // 1) Inactivate any active DB editions that are not in termserver
            // 2) Compare against termserver
            if (!activeDbEditionShortNames.isEmpty()) {
                List<String> inactivatedShortNames = activeDbEditionShortNames.stream().filter(c -> !termserverShortNames.contains(c)).collect(Collectors.toList());
                inactivatedShortNames.stream().forEach(n -> dbHandler.updateEditionStatus(n, false));
                statistics.setEditionsInactivated(inactivatedShortNames.size());

                // Determine editions that are active in DB and found in termserver and compare for changes
                existingInBothShortNames.addAll(activeDbEditionShortNames.stream().filter(c -> termserverShortNames.contains(c)).collect(Collectors.toList()));

                // Compare editions in termserver & active in db
                if (!existingInBothShortNames.isEmpty()) {
                    List<String> modifiedShortNames = compareAndModifyEditions(existingInBothShortNames, termserverShortNameCodeSystemMap);
                    List<String> unchangedShortNames = existingInBothShortNames.stream().filter(e -> !modifiedShortNames.contains(e)).collect(Collectors.toList());

                    // Have modified... now revisit acivated to see if they too are modified
                    statistics.setEditionsUnchanged(unchangedShortNames.size());
                    statistics.setEditionsModified(modifiedShortNames.size());
                }
            }

            // Compare editions in termserver & newly actived in db
            if (!activatedShortNames.isEmpty()) {
                List<String> activatedAndModifiedShortNames = compareAndModifyEditions(activatedShortNames, termserverShortNameCodeSystemMap);
                statistics.setEditionsActivatedAndModified(activatedAndModifiedShortNames.size());

                // Finalize those editions that were only activated (and not further modified)
                activatedAndModifiedShortNames.stream().forEach(n -> activatedShortNames.remove(n));
                statistics.setEditionsActivated(activatedShortNames.size());
            }

            return existingInBothShortNames;
        }

    }

    private void compareEditionOrganizationMaps(List<String> existingShortNames) throws Exception {
        List<String> updatedEditionOrganizationMaps = new ArrayList<>();

        try (TerminologyService service = new TerminologyService()) {

            for (String shortName : existingShortNames) {

                // Prepare DB edition for analysis
                List<Edition> matchingDbEditions = dbEditions.stream().filter(e -> e.getShortName().equals(shortName)).collect(Collectors.toList());
                utilities.validateMatches(matchingDbEditions, shortName);
                Edition dbEdition = matchingDbEditions.iterator().next();
                String dbOrganizationName = dbEdition.getOrganizationName();

                // Prepare termserver edition for analysis
                List<JsonNode> matchingSnowstormEditions = filteredCodeSystems.stream().filter(cs -> cs.get("shortName").asText().equals(shortName)).collect(Collectors.toList());
                utilities.validateMatches(matchingSnowstormEditions, shortName);
                JsonNode termserverEdition = matchingSnowstormEditions.iterator().next();
                String termserverOrganizationName = determineOrganizationName(termserverEdition);

                // compare
                if (!dbOrganizationName.equals(termserverOrganizationName)) {

                    List<Organization> matchedOrganizations = dbOrganizations.stream().filter(o -> o.getName().equals(termserverOrganizationName)).collect(Collectors.toList());
                    utilities.validateMatches(matchedOrganizations, termserverOrganizationName);

                    dbEdition.setOrganization(matchedOrganizations.iterator().next());

                    dbHandler.updateEdition(dbEdition);
                    updatedEditionOrganizationMaps.add(shortName);

                    statistics.incrementEditionOrganizationMapChanged();
                }
            }
        }

    }

    private List<String> compareAndModifyEditions(List<String> matchingEditionShortNames, Map<String, JsonNode> termserverShortNameCodeSystemMap) throws Exception {
        List<String> modifiedShortNames = new ArrayList<>();

        // Process one Organization per Edition.
        for (String shortName : matchingEditionShortNames) {

            // Find associated DB edition
            List<Edition> matchingEditions = dbEditions.stream().filter(e -> e.getShortName().equals(shortName)).collect(Collectors.toList());
            Edition dbEdition = (Edition) utilities.validateMatches(matchingEditions, shortName);
            Edition modifyingEdition = new Edition(dbEdition);

            // Find values for Snowstorm Edition
            final JsonNode codeSystem = termserverShortNameCodeSystemMap.get(shortName);
            final String snowStormEditionName = codeSystem.has("name") ? codeSystem.get("name").asText() : "";
            final String snowStormBranch = codeSystem.has("branchPath") ? codeSystem.get("branchPath").asText() : "";
            final String snowStormMaintainerType = utilities.determineMaintainerType(codeSystem, snowStormEditionName);
            final Set<String> snowStormEditionModules = utilities.identifyModules(shortName, snowStormEditionName, snowStormBranch, codeSystem);

            // start comparison
            boolean modificationMade = false;

            if (isDifferentAttribute(dbEdition.getShortName(), "Edition name ", dbEdition.getName(), snowStormEditionName)) {
                modifyingEdition.setName(snowStormEditionName);
                modificationMade = true;
            }

            if (isDifferentAttribute(dbEdition.getShortName(), "Edition branch ", dbEdition.getBranch(), snowStormBranch)) {
                modifyingEdition.setBranch(snowStormBranch);
                modificationMade = true;
            }

            if (isDifferentAttribute(dbEdition.getShortName(), "Edition modules ", dbEdition.getModules(), snowStormEditionModules)) {
                modifyingEdition.setModules(snowStormEditionModules);
                modificationMade = true;
            }

            if (isDifferentAttribute(dbEdition.getShortName(), "Edition maintainerType ", dbEdition.getMaintainerType(), snowStormMaintainerType)) {
                modifyingEdition.setMaintainerType(snowStormMaintainerType);
                modificationMade = true;
            }

            final String termserverDefaultLanguageCode = utilities.identifyDefaultLanguageCode(codeSystem, snowStormEditionName);
            if (isDifferentAttribute(dbEdition.getShortName(), "Edition defaultLanguageCode ", dbEdition.getDefaultLanguageCode(), termserverDefaultLanguageCode)) {

                modifyingEdition.setDefaultLanguageCode(termserverDefaultLanguageCode);
                modificationMade = true;
            }

            final Set<String> termserverDefaultLanguageRefsets = utilities.identifyDefaultLanguageRefsets(codeSystem, shortName);
            if (!dbEdition.getDefaultLanguageRefsets().equals(termserverDefaultLanguageRefsets)) {

                modifyingEdition.setDefaultLanguageRefsets(termserverDefaultLanguageRefsets);
                modificationMade = true;
            }

            if (modificationMade) {
                dbHandler.updateEdition(modifyingEdition);

                modifiedShortNames.add(modifyingEdition.getShortName());
            }
        }

        return modifiedShortNames;
    }

    /*-
     * Note -> Currently only sync-based attribute is organizationName. Yet, this is also the primary key for organizations. Thus nothing to do as:
     * 1) name being changed is found with previous organization add/activate/inactive analysis. 
     * 2) Description is defined on RT2, not on Snowstorm (as is primaryEmail & iconUrl)
     * 
     * If new values ever provided, then this method should be updated
     */
    private List<String> compareAndModifyOrganizations(List<String> matchingShortNames) throws Exception {
        List<String> modifiedShortNames = new ArrayList<>();

        return modifiedShortNames;
    }

    private String determineOrganizationDescription(String organizationName) {

        if (organizationName.startsWith(DEFAULT_ORGANIZATION_PREFACE)) {

            return "Organizational administrators can update this edition's default description.";
        } else {

            return "Two things to change." + System.lineSeparator()
                    + "1) Your organization name isn't defined on Snowstorm yet, so we have provided you with a temporary one that matches your edition name." + System.lineSeparator()
                    + "Have your organization's administrator(s) contact SNOMED International to have it changed." + System.lineSeparator()
                    + "2) Organizational administrator(s) can update this default description at any time";
        }
    }

    private String determineOrganizationName(JsonNode codeSystem) {

        // If owner defined, return it as organization name
        if (codeSystem.has("owner") && !codeSystem.get("owner").asText().trim().isBlank()) {
            return codeSystem.get("owner").asText();
        }

        // Create generic organization name
        final String editionName = codeSystem.has("name") ? codeSystem.get("name").asText() : "";
        return DEFAULT_ORGANIZATION_PREFACE + editionName;
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
                final String maintainerType = utilities.determineMaintainerType(codeSystem, editionShortName);

                // If not testing, process all editions. Otherwise, check if edition to test
                if (isTesting() && !isTestingEditionToProcess(editionShortName)) {
                    logger.info("In testing, but ignoring, codesystem " + editionShortName + " as not defined as the testing edition");

                } else if (codeSystem.has("active") && !codeSystem.get("active").asBoolean()) {
                    // Skipping inactive code system
                    logger.info("Skipping inactive codeSystem: " + editionShortName);

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

        return filteredCodeSystems;
    }

    private void initializeSync() throws Exception {

        updateDatabaseCache();

        codeSystemsNewAndInactive.clear();
        editionShortNameOrganizationNameMap.clear();

        try (TerminologyService service = new TerminologyService()) {
            dbEditions = service.getAll(Edition.class);
            dbOrganizations = service.getAll(Organization.class);

            dbEditions.stream().forEach(e -> editionShortNameOrganizationNameMap.put(e.getShortName(), e.getOrganization().getName()));
        }
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

    private boolean isTestingEditionToProcess(String codeSystem) {

        return ((testingEditionShortName == null || testingEditionShortName.isEmpty()) || codeSystem.equalsIgnoreCase(testingEditionShortName) || utilities.isInternationalEdition(codeSystem));

    }

}