package org.ihtsdo.refsetservice.sync;

import java.util.ArrayList;
import java.util.EnumMap;
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

    private static final Logger LOG = LoggerFactory.getLogger(SyncCodeSystemAgent.class);

    private static final HashMap<String, String> termserverEditionToOrganizationMap = new HashMap<>();

    private static final String DEFAULT_ORGANIZATION_PREFACE = "Owner of ";

    public enum ReasonEditionSkipped {
        WRONG_TESTING_EDITION, INACTIVE_EDITION, IGNORED_PER_FILE_EDITION, TYPE_THREE_EDITION
    };

    @Override
    public void syncComponent(final TerminologyService service) throws Exception {
        LOG.info("Starting sync of CodeSystemAgent");

        initializeSync();

        // Get all code systems from Snowstorm
        final JsonNode organizationJsonRootNode = getSnowstormCodeSystems();

        // Determine code systems to sync
        analyzeTermServerCodeSystems(organizationJsonRootNode);

        // Sync Organizations reviewing which are new (creating them), missing (removing them), and unchanged.
        syncOrganizations(service);

        // Sync Editions reviewing which are new (creating them), missing (removing them), modified (removing them and then creating them), and unchanged.
        List<String> existingInBothShortNames = syncEditions(service);

        // Review both DB & Snowstorm editon-to-org map to ensure consistency
        syncEditionOrganizationMaps(service, existingInBothShortNames);
    }

    private void analyzeTermServerCodeSystems(final JsonNode organizationJsonRootNode) throws Exception {

        final Iterator<JsonNode> organizationIterator = organizationJsonRootNode.iterator();
        LOG.info("Found " + countCodeSystems(organizationIterator) + " + Code Systems on Snowstorm: ");

        // Filter code systems (based on active-setting, ignoredCS list, testing situation, and bad data)
        final Map<ReasonEditionSkipped, Set<String>> ignoredReasonsMap = filterCodeSystems(organizationJsonRootNode);
        LOG.info("Will be processing these " + filteredCodeSystems.size() + " Code Systems: ");

        filteredCodeSystems.stream().forEach(c -> LOG.info(c.get("shortName").asText()));

        // Determine Snow edition-to-orgName map
        for (final JsonNode codeSystem : filteredCodeSystems) {
            final String shortName = codeSystem.get("shortName").asText();
            final String organizationName = determineOrganizationName(codeSystem);

            termserverEditionToOrganizationMap.put(shortName, organizationName);
        }

        // Log why each edition that isn't being processed is being skipped
        for (final ReasonEditionSkipped reason : ignoredReasonsMap.keySet()) {

            if (!ignoredReasonsMap.get(reason).isEmpty()) {
                final StringBuffer s = new StringBuffer("Ignoring these editions as they are: ");
                s.append(System.lineSeparator());

                switch (reason) {
                    case WRONG_TESTING_EDITION:
                        s.append("not the testing edition specified");
                        break;
                    case INACTIVE_EDITION:
                        s.append("inactive");
                        break;
                    case IGNORED_PER_FILE_EDITION:
                        s.append("listed in ignoredCodeSystems.txt");
                        break;
                    case TYPE_THREE_EDITION:
                        s.append("Type-3");
                        break;
                }

                s.append(": ");
                ignoredReasonsMap.get(reason).stream().forEach(edition -> s.append(edition + ","));
                LOG.info(s.substring(0, s.toString().length()));
            }
        }

        // Stats
        statistics.setCodeSystemsSynced(countCodeSystems(organizationIterator));
        statistics.setCodeSystemsFiltered(filteredCodeSystems.size());
    }

    private void syncOrganizations(final TerminologyService service) throws Exception {

        final Set<String> dbActiveOrganizationNames = new HashSet<>();
        final Set<String> dbInactiveOrganizationNames = new HashSet<>();
        final Set<String> termserverOrganizationNames = new HashSet<>();

        // Populate organization names lists of a) termserver code system names, b) rt2 database active organization names, and c) rt2 database inactive organization names
        final List<Organization> dbOrganizations = service.getAll(Organization.class);

        dbOrganizations.stream().filter(o -> o.isActive()).forEach(o -> dbActiveOrganizationNames.add(o.getName()));
        dbOrganizations.stream().filter(o -> !o.isActive()).forEach(o -> dbInactiveOrganizationNames.add(o.getName()));

        filteredCodeSystems.stream().forEach(codeSystem -> termserverOrganizationNames.add(determineOrganizationName(codeSystem)));

        // See if any termserver organizations are new
        final List<String> addedOrganizations =
                termserverOrganizationNames.stream().filter(o -> !dbActiveOrganizationNames.contains(o)).filter(o -> !dbInactiveOrganizationNames.contains(o)).collect(Collectors.toList());
        addedOrganizations.stream().forEach(name -> dbHandler.addOrganziation(service, name, determineOrganizationDescription(name)));
        statistics.setOrganizationsAdded(addedOrganizations.size());

        // Activate previously inactivated organizations. Note: Will log and update stats after remove those that were activatedAndModified
        final List<String> activatedOrganizations = termserverOrganizationNames.stream().filter(c -> dbInactiveOrganizationNames.contains(c)).collect(Collectors.toList());
        activatedOrganizations.stream().forEach(n -> dbHandler.updateOrganizationStatus(service, n, true));

        // If have active organizations inactivate any active DB organizations that are not returned from termserver
        // Note: No need for 'existing in both' case as only value to compare against termserver (owner) is also the primary key. Thus activating/inactivating is
        // sufficient
        final List<String> inactivatedOrganizations =
                dbActiveOrganizationNames.stream().filter(c -> !termserverOrganizationNames.contains(c)).filter(c -> !dbInactiveOrganizationNames.contains(c)).collect(Collectors.toList());

        inactivatedOrganizations.stream().forEach(n -> dbHandler.updateOrganizationStatus(service, n, false));
        statistics.setOrganizationsInactivated(inactivatedOrganizations.size());

    }

    private List<String> syncEditions(final TerminologyService service) throws Exception {
        final Map<String, JsonNode> termserverShortNameCodeSystemMap = new HashMap<>();
        final List<String> existingInBothShortNames = new ArrayList<>();
        final Set<String> dbInactiveEditionShortNames = new HashSet<>();
        final Set<String> dbActiveEditionShortNames = new HashSet<>();
        final Set<String> termserverShortNames = new HashSet<>();

        final List<Edition> dbEditions = service.getAll(Edition.class);
        dbEditions.stream().filter(e -> e.isActive()).forEach(e -> dbActiveEditionShortNames.add(e.getShortName()));
        dbEditions.stream().filter(e -> !e.isActive()).forEach(e -> dbInactiveEditionShortNames.add(e.getShortName()));

        // Based on filteredCodeSystems which already filtered for active code systems
        filteredCodeSystems.stream().forEach(cs -> termserverShortNameCodeSystemMap.put(cs.get("shortName").asText(), cs));
        termserverShortNames.addAll(termserverShortNameCodeSystemMap.keySet());
        termserverShortNames.stream().filter(shortName -> DEVELOPER_CODE_SYSTEM_SHORTNAME.equalsIgnoreCase(shortName)).forEach(shortName -> developerTestingEditionShortName = shortName);

        // Determine and create new editions (not in active nor in inactive DB editions)
        final List<String> addedShortNames =
                termserverShortNames.stream().filter(c -> !dbActiveEditionShortNames.contains(c)).filter(c -> !dbInactiveEditionShortNames.contains(c)).collect(Collectors.toList());
        statistics.setEditionsAdded(addedShortNames.size());
        addedShortNames.stream().filter(shortName -> termserverShortNameCodeSystemMap.containsKey(shortName))
                .forEach(shortName -> dbHandler.addEdition(service, termserverShortNameCodeSystemMap.get(shortName), termserverEditionToOrganizationMap.get(shortName)));

        // Create a Default Project for the edition if no projects already exist from Crowd
        statistics.setEditionsAdded(addedShortNames.size());

        // Activate previously inactivated editions. Note: Will log and update stats after remove those that were activatedAndModified
        final List<String> activatedShortNames = termserverShortNames.stream().filter(c -> dbInactiveEditionShortNames.contains(c)).collect(Collectors.toList());
        activatedShortNames.stream().forEach(n -> dbHandler.updateEditionStatus(service, n, true));

        // If have active editions:
        // 1) Inactivate any active DB editions that are not in termserver
        // 2) Compare against termserver to identify any changes in attributes defined on term server
        if (!dbActiveEditionShortNames.isEmpty()) {
            final List<String> inactivatedShortNames =
                    dbActiveEditionShortNames.stream().filter(c -> !termserverShortNames.contains(c)).filter(c -> dbActiveEditionShortNames.contains(c)).collect(Collectors.toList());

            inactivatedShortNames.stream().forEach(n -> dbHandler.updateEditionStatus(service, n, false));
            statistics.setEditionsInactivated(inactivatedShortNames.size());

            // Determine editions that are active in DB and found in termserver and compare for changes
            existingInBothShortNames.addAll(dbActiveEditionShortNames.stream().filter(c -> termserverShortNames.contains(c)).collect(Collectors.toList()));

            // Compare editions in termserver & active in db
            if (!existingInBothShortNames.isEmpty()) {
                final List<String> modifiedShortNames = compareAndModifyEditions(service, existingInBothShortNames, termserverShortNameCodeSystemMap);
                final List<String> unchangedShortNames = existingInBothShortNames.stream().filter(e -> !modifiedShortNames.contains(e)).collect(Collectors.toList());

                // Have modified... now revisit acivated to see if they too are modified
                statistics.setEditionsUnchanged(unchangedShortNames.size());
                statistics.setEditionsModified(modifiedShortNames.size());
            }
        }

        // Compare editions in termserver & newly actived in db
        if (!activatedShortNames.isEmpty()) {
            final List<String> activatedAndModifiedShortNames = compareAndModifyEditions(service, activatedShortNames, termserverShortNameCodeSystemMap);
            statistics.setEditionsActivatedAndModified(activatedAndModifiedShortNames.size());

            // Finalize those editions that were only activated (and not further modified)
            activatedAndModifiedShortNames.stream().forEach(n -> activatedShortNames.remove(n));
            statistics.setEditionsActivated(activatedShortNames.size());
        }

        return existingInBothShortNames;

    }

    private void syncEditionOrganizationMaps(final TerminologyService service, final List<String> existingShortNames) throws Exception {
        final List<String> updatedEditionOrganizationMaps = new ArrayList<>();

        // TODO: Not using termserverEditionToOrganizationMap... why?

        for (final String shortName : existingShortNames) {

            // Prepare DB edition for analysis
            final List<Edition> matchingDbEditions = readDbActiveEditions(service).stream().filter(e -> e.getShortName().equals(shortName)).collect(Collectors.toList());
            utilities.validateMatches(matchingDbEditions, shortName);
            final Edition dbEdition = matchingDbEditions.iterator().next();
            final String dbOrganizationName = dbEdition.getOrganizationName();

            // Prepare termserver edition for analysis
            final List<JsonNode> matchingSnowstormEditions = filteredCodeSystems.stream().filter(cs -> cs.get("shortName").asText().equals(shortName)).collect(Collectors.toList());
            utilities.validateMatches(matchingSnowstormEditions, shortName);
            final JsonNode termserverEdition = matchingSnowstormEditions.iterator().next();
            final String termserverOrganizationName = determineOrganizationName(termserverEdition);

            // compare
            if (!dbOrganizationName.equals(termserverOrganizationName)) {

                final List<Organization> matchedOrganizations = service.getAll(Organization.class).stream().filter(o -> o.getName().equals(termserverOrganizationName)).collect(Collectors.toList());
                utilities.validateMatches(matchedOrganizations, termserverOrganizationName);

                dbEdition.setOrganization(matchedOrganizations.iterator().next());

                dbHandler.updateEdition(service, dbEdition);
                updatedEditionOrganizationMaps.add(shortName);

                statistics.incrementEditionOrganizationMapChanged();
            }
        }

    }

    private List<String> compareAndModifyEditions(final TerminologyService service, final List<String> matchingEditionShortNames, final Map<String, JsonNode> termserverShortNameCodeSystemMap)
        throws Exception {
        final List<String> modifiedShortNames = new ArrayList<>();

        // Process one Organization per Edition.
        for (final String shortName : matchingEditionShortNames) {

            if (termserverShortNameCodeSystemMap.containsKey(shortName)) {

                // Find associated DB edition
                final List<Edition> matchingEditions = readDbActiveEditions(service).stream().filter(e -> e.getShortName().equals(shortName)).collect(Collectors.toList());
                final Edition dbEdition = (Edition) utilities.validateMatches(matchingEditions, shortName);
                final Edition modifyingEdition = new Edition(dbEdition);

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
                    dbHandler.updateEdition(service, modifyingEdition);

                    modifiedShortNames.add(modifyingEdition.getShortName());
                }
            }
        }

        return modifiedShortNames;
    }

    private String determineOrganizationDescription(final String organizationName) {

        if (organizationName.startsWith(DEFAULT_ORGANIZATION_PREFACE)) {

            return "Organizational administrators can update this edition's default description.";
        } else {

            return "Two things to change." + System.lineSeparator()
                    + "1) Your organization name isn't defined on Snowstorm yet, so we have provided you with a temporary one that matches your edition name." + System.lineSeparator()
                    + "Have your organization's administrator(s) contact SNOMED International to have it changed." + System.lineSeparator()
                    + "2) Organizational administrator(s) can update this default description at any time";
        }
    }

    private String determineOrganizationName(final JsonNode codeSystem) {

        // If owner defined, return it as organization name
        if (codeSystem.has("owner") && !codeSystem.get("owner").asText().trim().isBlank()) {
            return codeSystem.get("owner").asText();
        }

        // Create generic organization name
        final String editionName = codeSystem.has("name") ? codeSystem.get("name").asText() : "";
        return DEFAULT_ORGANIZATION_PREFACE + editionName;
    }

    private Map<ReasonEditionSkipped, Set<String>> filterCodeSystems(final JsonNode organizationJsonRootNode) throws Exception {

        final Iterator<JsonNode> organizationIterator = organizationJsonRootNode.iterator();
        final Map<ReasonEditionSkipped, Set<String>> ignoredReasonMap = new EnumMap<>(ReasonEditionSkipped.class);

        ignoredReasonMap.put(ReasonEditionSkipped.WRONG_TESTING_EDITION, new HashSet<>());
        ignoredReasonMap.put(ReasonEditionSkipped.INACTIVE_EDITION, new HashSet<>());
        ignoredReasonMap.put(ReasonEditionSkipped.IGNORED_PER_FILE_EDITION, new HashSet<>());
        ignoredReasonMap.put(ReasonEditionSkipped.TYPE_THREE_EDITION, new HashSet<>());

        while (organizationIterator.hasNext()) {

            final Iterator<JsonNode> codeSystems = organizationIterator.next().iterator();

            while (codeSystems.hasNext()) {

                final JsonNode codeSystem = codeSystems.next();

                // Check for invalid or ignored code systems
                if (!codeSystem.has("shortName")) {

                    // Skipping odd code system without a shortName
                    LOG.error("Skipping codeSystem without a shortName: " + codeSystem);
                    continue;
                }

                final String editionShortName = codeSystem.get("shortName").asText();
                final String maintainerType = utilities.determineMaintainerType(codeSystem, editionShortName);

                // If not testing, process all editions. Otherwise, check if edition to test
                if (isTesting() && !isTestingEditionToProcess(editionShortName)) {
                    ignoredReasonMap.get(ReasonEditionSkipped.WRONG_TESTING_EDITION).add(editionShortName);

                } else if (codeSystem.has("active") && !codeSystem.get("active").asBoolean()) {
                    // Skipping inactive code system
                    ignoredReasonMap.get(ReasonEditionSkipped.INACTIVE_EDITION).add(editionShortName);

                } else if (utilities.getPropertyReader().getCodeSystemsToIgnore().contains(editionShortName)) {
                    // Code System has been defined as to-be-ignored (either by specifying name or shortname).
                    ignoredReasonMap.get(ReasonEditionSkipped.IGNORED_PER_FILE_EDITION).add(editionShortName);

                } else if (!maintainerType.equalsIgnoreCase("Managed Service")) {
                    // TODO: Handle Type-3 (non-Managed Service only)
                    ignoredReasonMap.get(ReasonEditionSkipped.TYPE_THREE_EDITION).add(editionShortName);

                } else {
                    filteredCodeSystems.add(codeSystem);
                }

            }

        }

        return ignoredReasonMap;
    }

    private void initializeSync() throws Exception {

        termserverEditionToOrganizationMap.clear();
    }

    private int countCodeSystems(final Iterator<JsonNode> organizationIterator) {
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
     * @param service
     * @return
     * @throws Exception
     */
    private JsonNode getSnowstormCodeSystems() throws Exception {

        final String url = SnowstormConnection.getBaseUrl() + "codesystems";
        LOG.info("getSnowstormCodeSystems url: " + url);

        try (final Response response = SnowstormConnection.getResponse(url)) {

            final String resultString = response.readEntity(String.class);

            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode organizationJsonRootNode = mapper.readTree(resultString.toString());

            return organizationJsonRootNode;
        }

    }

    private boolean isTestingEditionToProcess(String codeSystem) {

        return ((TESTING_EDITION_SHORT_NAME == null || TESTING_EDITION_SHORT_NAME.isEmpty()) || codeSystem.equalsIgnoreCase(TESTING_EDITION_SHORT_NAME)
                || utilities.isInternationalEdition(codeSystem));

    }

}