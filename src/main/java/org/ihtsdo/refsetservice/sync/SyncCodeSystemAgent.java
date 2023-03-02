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

    private HashMap<String, String> snowstormEditionShortNameToOrganizationNameMap;

    private List<Edition> dbEditions;

    private List<Organization> dbOrganizations;

    public void sync() throws Exception {

        initializeSync();

        // Get all code systems from Snowstorm
        final JsonNode organizationJsonRootNode = getSnowstormCodeSystems();

        // Determine code systems to sync
        analyzeCodeSystems(organizationJsonRootNode);

        // Sync Organizations reviewing which are new (creating them), missing (removing them), and unchanged.
        analyzeOrganizations();

        // Sync Editions reviewing which are new (creating them), missing (removing them), modified (removing them and then creating them), and unchanged.
        List<String> existingShortNames = analyzeEditions();

        // Review both DB & Snowstorm editon-to-org map to ensure consistency
        compareEditionOrganizationMaps(existingShortNames);
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

    private List<String> analyzeOrganizations() throws Exception {
        List<String> existingInBothShortNames = new ArrayList<>();

        HashMap<String, String> dbActiveEditionShortNameToOrganizationNameMap = new HashMap<>();
        HashMap<String, Set<String>> dbActiveOrganizationNameToEditionsShortNameMap = new HashMap<>();
        HashMap<String, Set<String>> dbInactiveOrganizationNameToEditionsShortNameMap = new HashMap<>();
        HashMap<String, Set<String>> snowstormOrganizationNameToEditionsShortNameMap = new HashMap<>();

        snowstormEditionShortNameToOrganizationNameMap = new HashMap<>();

        try (final TerminologyService service = new TerminologyService()) {

            // Determine DB edition-to-orgName bi-directional maps (separated by active/inactive editions)
            for (Edition edition : dbEditions) {

                if (edition.isActive()) {
                    dbActiveEditionShortNameToOrganizationNameMap.put(edition.getShortName(), edition.getOrganizationName());

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
            // Determine Snow edition-to-orgName bi-directional maps
            for (JsonNode codeSystem : filteredCodeSystems) {
                String organizationName = determineOrganizationName(codeSystem);

                snowstormEditionShortNameToOrganizationNameMap.put(codeSystem.get("shortName").asText(), organizationName);

                if (!snowstormOrganizationNameToEditionsShortNameMap.keySet().contains(organizationName)) {
                    snowstormOrganizationNameToEditionsShortNameMap.put(organizationName, new HashSet<String>());
                }

                snowstormOrganizationNameToEditionsShortNameMap.get(organizationName).add(codeSystem.get("shortName").asText());
            }

            // See if any snowstorm organizations are new
            List<String> addedOrganizations = snowstormOrganizationNameToEditionsShortNameMap.keySet().stream()
                    .filter(o -> !dbActiveOrganizationNameToEditionsShortNameMap.keySet().contains(o) && !dbInactiveOrganizationNameToEditionsShortNameMap.keySet().contains(o))
                    .collect(Collectors.toList());
            statistics.setOrganizationsAdded(addedOrganizations.size());

            for (String organizationName : addedOrganizations) {
                dbHandler.addOrganziation(organizationName, determineOrganizationDescription(organizationName));
            }

            // Activate previously inactivated organizations. Note: Will log and update stats after remove those that were activatedAndModified
            // TODO: Define solution although for now simply activating
            List<String> activatedShortNames =
                    snowstormOrganizationNameToEditionsShortNameMap.keySet().stream().filter(c -> dbInactiveOrganizationNameToEditionsShortNameMap.keySet().contains(c)).collect(Collectors.toList());
            activatedShortNames.stream().forEach(n -> dbHandler.updateOrganizationStatus(n, true));

            // Inactivate active DB organizations that are not in snowstorm
            // TODO: Define solution although for now simply inactivating
            List<String> inactivatedShortNames =
                    dbActiveOrganizationNameToEditionsShortNameMap.keySet().stream().filter(c -> !snowstormOrganizationNameToEditionsShortNameMap.keySet().contains(c)).collect(Collectors.toList());
            statistics.setOrganizationsInactivated(inactivatedShortNames.size());
            inactivatedShortNames.stream().forEach(n -> dbHandler.updateOrganizationStatus(n, false));

            // Determine organizations that are active in DB and found in snowstorm and compare for changes
            dbActiveEditionShortNameToOrganizationNameMap.keySet().stream().forEach(c -> existingInBothShortNames.add(c));

            // Compare Orgs
            List<String> modifiedShortNames = compareAndModifyOrganizations(new ArrayList<>(existingInBothShortNames));
            List<String> unchangedShortNames = existingInBothShortNames.stream().filter(e -> !modifiedShortNames.contains(e)).collect(Collectors.toList());

            // Have modified... now revisit acivated to see if they too are modified
            statistics.setOrganizationsUnchanged(unchangedShortNames.size());
            statistics.setOrganizationsModified(modifiedShortNames.size());

            // Determine organizations that were just activated to see if there are any other changes necessary
            List<String> activatedAndModifiedShortNames = compareAndModifyOrganizations(activatedShortNames);
            statistics.setOrganizationsActivatedAndModified(activatedAndModifiedShortNames.size());

            // Finalize those organizations that were only activated (and not further modified)
            activatedAndModifiedShortNames.stream().forEach(n -> activatedShortNames.remove(n));
            statistics.setOrganizationsActivated(activatedShortNames.size());

            return existingInBothShortNames;

        }
    }

    private List<String> analyzeEditions() throws Exception {
        final Set<String> dbInactiveEditionShortNames = new HashSet<>();
        final Set<String> dbActiveEditionShortNames = new HashSet<>();
        final Map<String, JsonNode> snowstormShortNameCodeSystemMap = new HashMap<>();

        try (TerminologyService service = new TerminologyService()) {

            // Determine new, inactivated, and existing codeSystems (Based on shortName)
            dbEditions.stream().filter(e -> e.isActive()).forEach(ea -> dbActiveEditionShortNames.add(ea.getShortName()));
            dbEditions.stream().filter(e -> !e.isActive()).forEach(ea -> dbInactiveEditionShortNames.add(ea.getShortName()));

            // Based on filteredCodeSystems which already filtered for active code systems
            filteredCodeSystems.stream().forEach(cs -> snowstormShortNameCodeSystemMap.put(cs.get("shortName").asText(), cs));

            // Determine and create new editions (not in active nor in inactive DB editions)
            List<String> newShortNames =
                    snowstormShortNameCodeSystemMap.keySet().stream().filter(c -> !dbActiveEditionShortNames.contains(c) && !dbInactiveEditionShortNames.contains(c)).collect(Collectors.toList());
            statistics.setEditionsAdded(newShortNames.size());
            newShortNames.stream().forEach(shortName -> dbHandler.addEdition(snowstormShortNameCodeSystemMap.get(shortName), snowstormEditionShortNameToOrganizationNameMap.get(shortName)));

            // Activate previously inactivated editions. Note: Will log and update stats after remove those that were activatedAndModified
            // TODO: Define solution although for now simply activating
            List<String> activatedShortNames = snowstormShortNameCodeSystemMap.keySet().stream().filter(c -> dbInactiveEditionShortNames.contains(c)).collect(Collectors.toList());
            activatedShortNames.stream().forEach(n -> dbHandler.updateEditionStatus(n, true));

            // Inactivate active DB editions that are not in snowstorm
            // TODO: Define solution although for now simply inactivating
            List<String> inactivatedShortNames = dbActiveEditionShortNames.stream().filter(c -> !snowstormShortNameCodeSystemMap.keySet().contains(c)).collect(Collectors.toList());
            statistics.setEditionsInactivated(inactivatedShortNames.size());
            inactivatedShortNames.stream().forEach(n -> dbHandler.updateEditionStatus(n, false));

            // Determine editions that are active in DB and found in snowstorm and compare for changes
            List<String> existingInBothShortNames = dbActiveEditionShortNames.stream().filter(c -> snowstormShortNameCodeSystemMap.keySet().contains(c)).collect(Collectors.toList());

            // Compare editions
            List<String> modifiedShortNames = compareAndModifyEditions(existingInBothShortNames, snowstormShortNameCodeSystemMap);
            List<String> unchangedShortNames = existingInBothShortNames.stream().filter(e -> !modifiedShortNames.contains(e)).collect(Collectors.toList());

            // Have modified... now revisit acivated to see if they too are modified
            statistics.setEditionsUnchanged(unchangedShortNames.size());
            statistics.setEditionsModified(modifiedShortNames.size());

            // Determine editions that were just activated to see if there are any other changes necessary
            List<String> activatedAndModifiedShortNames = compareAndModifyEditions(activatedShortNames, snowstormShortNameCodeSystemMap);
            statistics.setEditionsActivatedAndModified(activatedAndModifiedShortNames.size());

            // Finalize those editions that were only activated (and not further modified)
            activatedAndModifiedShortNames.stream().forEach(n -> activatedShortNames.remove(n));
            statistics.setEditionsActivated(activatedShortNames.size());

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

                // Prepare snowstorm edition for analysis
                List<JsonNode> matchingSnowstormEditions = filteredCodeSystems.stream().filter(cs -> cs.get("shortName").asText().equals(shortName)).collect(Collectors.toList());
                utilities.validateMatches(matchingSnowstormEditions, shortName);
                JsonNode snowstormEdition = matchingSnowstormEditions.iterator().next();
                String snowstormOrganizationName = determineOrganizationName(snowstormEdition);

                // compare
                if (!dbOrganizationName.equals(snowstormOrganizationName)) {

                    List<Organization> matchedOrganizations = dbOrganizations.stream().filter(o -> o.getName().equals(snowstormOrganizationName)).collect(Collectors.toList());
                    utilities.validateMatches(matchedOrganizations, snowstormOrganizationName);

                    dbEdition.setOrganization(matchedOrganizations.iterator().next());

                    dbHandler.updateEdition(dbEdition);
                    updatedEditionOrganizationMaps.add(shortName);

                    statistics.incrementEditionOrganizationMapChanged();
                }
            }
        }

    }

    private List<String> compareAndModifyEditions(List<String> matchingEditionShortNames, Map<String, JsonNode> snowstormShortNameCodeSystemMap) throws Exception {
        List<String> modifiedShortNames = new ArrayList<>();

        // Process one Organization per Edition.
        for (String shortName : matchingEditionShortNames) {

            // Find associated DB edition
            List<Edition> matchingEditions = dbEditions.stream().filter(e -> e.getShortName().equals(shortName)).collect(Collectors.toList());
            Edition dbEdition = (Edition) utilities.validateMatches(matchingEditions, shortName);
            Edition modifyingEdition = new Edition(dbEdition);

            // Find values for Snowstorm Edition
            final JsonNode codeSystem = snowstormShortNameCodeSystemMap.get(shortName);
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

            final String snowstormDefaultLanguageCode = utilities.identifyDefaultLanguageCode(codeSystem, snowStormEditionName);
            if (isDifferentAttribute(dbEdition.getShortName(), "Edition defaultLanguageCode ", dbEdition.getDefaultLanguageCode(), snowstormDefaultLanguageCode)) {

                modifyingEdition.setDefaultLanguageCode(snowstormDefaultLanguageCode);
                modificationMade = true;
            }

            final Set<String> snowstormDefaultLanguageRefsets = utilities.identifyDefaultLanguageRefsets(codeSystem, shortName);
            if (!dbEdition.getDefaultLanguageRefsets().equals(snowstormDefaultLanguageRefsets)) {

                modifyingEdition.setDefaultLanguageRefsets(snowstormDefaultLanguageRefsets);
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

    protected Edition getDeveloperTestingEdition() {

        return developerTestingEdition;
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