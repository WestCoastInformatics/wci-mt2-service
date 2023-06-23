/*
 * Copyright 2023 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
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
import java.util.stream.Stream;

import javax.ws.rs.core.Response;

import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.Organization;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.terminologyservice.SnowstormConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The Class SyncCodeSystemAgent.
 */
public class SyncCodeSystemAgent extends SyncAgent {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(SyncCodeSystemAgent.class);

    /** The Constant TERMSERVER_EDITION_TO_ORGANIZATION_MAP. */
    private static final HashMap<String, String> TERMSERVER_EDITION_TO_ORGANIZATION_MAP = new HashMap<>();

    /** The Constant DEFAULT_ORGANIZATION_PREFACE. */
    private static final String DEFAULT_ORGANIZATION_PREFACE = "Owner of ";

    /**
     * The Enum ReasonEditionSkipped.
     */
    public enum ReasonEditionSkipped {

        /** The wrong testing edition. */
        WRONG_TESTING_EDITION,
        /** The inactive edition. */
        INACTIVE_EDITION,
        /** The ignored per file edition. */
        IGNORED_PER_FILE_EDITION,
        /** The type three edition. */
        TYPE_THREE_EDITION
    };

    /* see superclass */
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

    /**
     * Analyze term server code systems.
     *
     * @param organizationJsonRootNode the organization json root node
     * @throws Exception the exception
     */
    private void analyzeTermServerCodeSystems(final JsonNode organizationJsonRootNode) throws Exception {

        final Iterator<JsonNode> organizationIterator = organizationJsonRootNode.iterator();
        LOG.info("Found " + countCodeSystems(organizationIterator) + " + Code Systems on term server: ");

        // Filter code systems (based on active-setting, ignoredCS list, testing situation, and bad data)
        final Map<ReasonEditionSkipped, Set<String>> ignoredReasonsMap = filterCodeSystems(organizationJsonRootNode);
        LOG.info("Will be processing these " + FILTERED_CODE_SYSTEMS.size() + " Code Systems found on the term server: ");

        FILTERED_CODE_SYSTEMS.stream().forEach(c -> LOG.info(c.get("shortName").asText()));

        // Determine Snow edition-to-orgName map
        for (final JsonNode codeSystem : FILTERED_CODE_SYSTEMS) {
            final String shortName = codeSystem.get("shortName").asText();
            final String organizationName = determineOrganizationName(codeSystem);

            TERMSERVER_EDITION_TO_ORGANIZATION_MAP.put(shortName, organizationName);
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
                    default:
                        break;
                }

                s.append(": ");
                ignoredReasonsMap.get(reason).stream().forEach(edition -> s.append(edition + ","));
                LOG.info(s.substring(0, s.toString().length()));
            }
        }

        // Stats
        STATISTICS.setCodeSystemsSynced(countCodeSystems(organizationIterator));
        STATISTICS.setCodeSystemsFiltered(FILTERED_CODE_SYSTEMS.size());
    }

    /**
     * Sync organizations.
     *
     * @param service the service
     * @throws Exception the exception
     */
    private void syncOrganizations(final TerminologyService service) throws Exception {

        final Set<String> dbActiveOrganizationNames = new HashSet<>();
        final Set<String> dbInactiveOrganizationNames = new HashSet<>();
        final Set<String> termserverOrganizationNames = new HashSet<>();

        // Populate organization names lists of a) termserver code system names, b) rt2 database active organization names, and c) rt2 database inactive
        // organization names
        final List<Organization> dbOrganizations = service.getAll(Organization.class);

        dbOrganizations.stream().filter(o -> o.isActive()).forEach(o -> dbActiveOrganizationNames.add(o.getName()));
        dbOrganizations.stream().filter(o -> !o.isActive()).forEach(o -> dbInactiveOrganizationNames.add(o.getName()));

        FILTERED_CODE_SYSTEMS.stream().forEach(codeSystem -> termserverOrganizationNames.add(determineOrganizationName(codeSystem)));

        // See if any termserver organizations are new
        final List<String> addedOrganizations =
                termserverOrganizationNames.stream().filter(o -> !dbActiveOrganizationNames.contains(o)).filter(o -> !dbInactiveOrganizationNames.contains(o)).collect(Collectors.toList());
        addedOrganizations.stream().forEach(name -> getDbHandler().addOrganziation(service, name, determineOrganizationDescription(name)));
        STATISTICS.setOrganizationsAdded(addedOrganizations.size());

        // Activate previously inactivated organizations. Note: Will log and update stats after remove those that were activatedAndModified
        final List<String> activatedOrganizations = termserverOrganizationNames.stream().filter(c -> dbInactiveOrganizationNames.contains(c)).collect(Collectors.toList());
        activatedOrganizations.stream().forEach(n -> getDbHandler().updateOrganizationStatus(service, n, true));

        // If have active organizations inactivate any active DB organizations that are not returned from termserver
        // Note: No need for 'existing in both' case as only value to compare against termserver (owner) is also the primary key. Thus activating/inactivating
        // is
        // sufficient
        final List<String> inactivatedOrganizations =
                dbActiveOrganizationNames.stream().filter(c -> !termserverOrganizationNames.contains(c)).filter(c -> !dbInactiveOrganizationNames.contains(c)).collect(Collectors.toList());

        inactivatedOrganizations.stream().forEach(n -> getDbHandler().updateOrganizationStatus(service, n, false));
        STATISTICS.setOrganizationsInactivated(inactivatedOrganizations.size());

    }

    /**
     * Sync editions.
     *
     * @param service the service
     * @return the list
     * @throws Exception the exception
     */
    private List<String> syncEditions(final TerminologyService service) throws Exception {

        final Map<String, JsonNode> termserverShortNameCodeSystemMap = new HashMap<>();
        final List<String> existingInBothShortNames = new ArrayList<>();
        final Set<String> dbInactiveEditionShortNames = new HashSet<>();
        final Set<String> dbActiveEditionShortNames = new HashSet<>();
        final Set<String> termserverShortNames = new HashSet<>();

        final List<Edition> dbEditions = service.getAll(Edition.class);
        dbEditions.stream().filter(e -> e.isActive()).forEach(e -> dbActiveEditionShortNames.add(e.getShortName()));
        dbEditions.stream().filter(e -> !e.isActive()).forEach(e -> dbInactiveEditionShortNames.add(e.getShortName()));

        // Based on FILTERED_CODE_SYSTEMS which already filtered for active code systems
        FILTERED_CODE_SYSTEMS.stream().forEach(cs -> termserverShortNameCodeSystemMap.put(cs.get("shortName").asText(), cs));
        termserverShortNames.addAll(termserverShortNameCodeSystemMap.keySet());
        termserverShortNames.stream().filter(shortName -> DEVELOPER_CODE_SYSTEM_SHORTNAME.equalsIgnoreCase(shortName)).forEach(shortName -> setDeveloperTestingEditionShortName(shortName));

        // Determine and create new editions (not in active nor in inactive DB editions)
        final List<String> addedShortNames =
                termserverShortNames.stream().filter(c -> !dbActiveEditionShortNames.contains(c)).filter(c -> !dbInactiveEditionShortNames.contains(c)).collect(Collectors.toList());

        STATISTICS.setEditionsAdded(addedShortNames.size());
        addedShortNames.stream().filter(shortName -> termserverShortNameCodeSystemMap.containsKey(shortName))
                .forEach(shortName -> getDbHandler().addEdition(service, termserverShortNameCodeSystemMap.get(shortName), TERMSERVER_EDITION_TO_ORGANIZATION_MAP.get(shortName)));

        // Activate previously inactivated editions. Note: Will log and update stats after remove those that were activatedAndModified
        final List<String> activatedShortNames = termserverShortNames.stream().filter(c -> dbInactiveEditionShortNames.contains(c)).collect(Collectors.toList());
        activatedShortNames.stream().forEach(n -> getDbHandler().updateEditionStatus(service, n, true));

        // If have active editions:
        // 1) Inactivate any active DB editions that are not in termserver
        // 2) Compare against termserver to identify any changes in attributes defined on term server
        if (!dbActiveEditionShortNames.isEmpty()) {
            final List<String> inactivatedShortNames =
                    dbActiveEditionShortNames.stream().filter(c -> !termserverShortNames.contains(c)).filter(c -> dbActiveEditionShortNames.contains(c)).collect(Collectors.toList());

            inactivatedShortNames.stream().forEach(n -> getDbHandler().updateEditionStatus(service, n, false));
            STATISTICS.setEditionsInactivated(inactivatedShortNames.size());

            // Determine editions that are active in DB and found in termserver and compare for changes
            existingInBothShortNames.addAll(dbActiveEditionShortNames.stream().filter(c -> termserverShortNames.contains(c)).collect(Collectors.toList()));

            // Compare editions in termserver & active in db
            if (!existingInBothShortNames.isEmpty()) {
                final List<String> modifiedShortNames = compareAndModifyEditions(service, existingInBothShortNames, termserverShortNameCodeSystemMap);
                final List<String> unchangedShortNames = existingInBothShortNames.stream().filter(e -> !modifiedShortNames.contains(e)).collect(Collectors.toList());

                // Have modified... now revisit acivated to see if they too are modified
                STATISTICS.setEditionsUnchanged(unchangedShortNames.size());
                STATISTICS.setEditionsModified(modifiedShortNames.size());
            }
        }

        // Compare editions in termserver & newly actived in db
        if (!activatedShortNames.isEmpty()) {
            final List<String> activatedAndModifiedShortNames = compareAndModifyEditions(service, activatedShortNames, termserverShortNameCodeSystemMap);
            STATISTICS.setEditionsActivatedAndModified(activatedAndModifiedShortNames.size());

            // Finalize those editions that were only activated (and not further modified)
            activatedAndModifiedShortNames.stream().forEach(n -> activatedShortNames.remove(n));
            STATISTICS.setEditionsActivated(activatedShortNames.size());
        }

        return existingInBothShortNames;

    }

    /**
     * Sync edition organization maps.
     *
     * @param service the service
     * @param existingShortNames the existing short names
     * @throws Exception the exception
     */
    private void syncEditionOrganizationMaps(final TerminologyService service, final List<String> existingShortNames) throws Exception {

        final List<String> updatedEditionOrganizationMaps = new ArrayList<>();

        service.setTransactionPerOperation(false);
        service.beginTransaction();

        List<Edition> activeDbRefsets = readDbActiveEditions(service);
        List<Organization> allDbOrganizations = service.getAll(Organization.class);

        for (final String shortName : existingShortNames) {

            // Prepare DB edition for analysis
            final Stream<Edition> editionStream = activeDbRefsets.stream().filter(e -> e.getShortName().equals(shortName));
            final Edition dbEdition = (Edition) getUtilities().validateMatches(editionStream, shortName);
            final String dbOrganizationName = dbEdition.getOrganizationName();

            // Prepare termserver edition for analysis
            String termserverOrganizationName = TERMSERVER_EDITION_TO_ORGANIZATION_MAP.get(shortName);

            // compare and update if needed
            if (!dbOrganizationName.equals(termserverOrganizationName)) {

                final Stream<Organization> organizationStream = allDbOrganizations.stream().filter(o -> o.getName().equals(termserverOrganizationName));
                final Organization dbOrganization = (Organization) getUtilities().validateMatches(organizationStream, termserverOrganizationName);

                dbEdition.setOrganization(dbOrganization);

                getDbHandler().updateEdition(service, dbEdition);
                updatedEditionOrganizationMaps.add(shortName);

                STATISTICS.incrementEditionOrganizationMapChanged();
            }
        }

        service.commit();
        service.setTransactionPerOperation(true);

    }

    /**
     * Compare and modify editions.
     *
     * @param service the service
     * @param matchingEditionShortNames the matching edition short names
     * @param termserverShortNameCodeSystemMap the termserver short name code system map
     * @return the list
     * @throws Exception the exception
     */
    private List<String> compareAndModifyEditions(final TerminologyService service, final List<String> matchingEditionShortNames, final Map<String, JsonNode> termserverShortNameCodeSystemMap)
        throws Exception {

        final List<String> modifiedShortNames = new ArrayList<>();

        // Process one Organization per Edition.
        for (final String shortName : matchingEditionShortNames) {

            if (termserverShortNameCodeSystemMap.containsKey(shortName)) {

                // Find associated DB edition
                final Stream<Edition> editionStream = readDbActiveEditions(service).stream().filter(e -> e.getShortName().equals(shortName));
                final Edition dbEdition = (Edition) getUtilities().validateMatches(editionStream, shortName);
                final Edition modifyingEdition = new Edition(dbEdition);

                // Find values for Snowstorm Edition
                final JsonNode codeSystem = termserverShortNameCodeSystemMap.get(shortName);
                final String snowStormEditionName = codeSystem.has("name") ? codeSystem.get("name").asText() : "";
                final String snowStormBranch = codeSystem.has("branchPath") ? codeSystem.get("branchPath").asText() : "";
                final String snowStormMaintainerType = getUtilities().identifyMaintainerType(codeSystem, snowStormEditionName);
                final Set<String> snowStormEditionModules = getUtilities().identifyModules(shortName, snowStormEditionName, snowStormBranch, codeSystem);

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

                final String termserverDefaultLanguageCode = getUtilities().identifyDefaultLanguageCode(codeSystem, snowStormEditionName);
                if (isDifferentAttribute(dbEdition.getShortName(), "Edition defaultLanguageCode ", dbEdition.getDefaultLanguageCode(), termserverDefaultLanguageCode)) {

                    modifyingEdition.setDefaultLanguageCode(termserverDefaultLanguageCode);
                    modificationMade = true;
                }

                final Set<String> termserverDefaultLanguageRefsets = getUtilities().identifyDefaultLanguageRefsets(codeSystem, shortName, modifyingEdition.getBranch());
                if (!dbEdition.getDefaultLanguageRefsets().equals(termserverDefaultLanguageRefsets)) {

                    modifyingEdition.setDefaultLanguageRefsets(termserverDefaultLanguageRefsets);
                    modificationMade = true;
                }

                if (modificationMade) {
                    getDbHandler().updateEdition(service, modifyingEdition);

                    modifiedShortNames.add(modifyingEdition.getShortName());
                }
            }
        }

        return modifiedShortNames;
    }

    /**
     * Determine organization description.
     *
     * @param organizationName the organization name
     * @return the string
     */
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

    /**
     * Determine organization name.
     *
     * @param codeSystem the code system
     * @return the string
     */
    private String determineOrganizationName(final JsonNode codeSystem) {

        // If owner defined, return it as organization name
        if (codeSystem.has("owner") && !codeSystem.get("owner").asText().trim().isBlank()) {
            return codeSystem.get("owner").asText();
        }

        // Create generic organization name
        final String editionName = codeSystem.has("name") ? codeSystem.get("name").asText() : "";
        return DEFAULT_ORGANIZATION_PREFACE + editionName;
    }

    /**
     * Filter code systems.
     *
     * @param organizationJsonRootNode the organization json root node
     * @return the map
     * @throws Exception the exception
     */
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
                final String maintainerType = getUtilities().identifyMaintainerType(codeSystem, editionShortName);

                // If not testing, process all editions. Otherwise, check if edition to test
                if (isTesting() && !isTestingEditionToProcess(editionShortName)) {
                    ignoredReasonMap.get(ReasonEditionSkipped.WRONG_TESTING_EDITION).add(editionShortName);

                } else if (codeSystem.has("active") && !codeSystem.get("active").asBoolean()) {
                    // Skipping inactive code system
                    ignoredReasonMap.get(ReasonEditionSkipped.INACTIVE_EDITION).add(editionShortName);

                } else if (getUtilities().getPropertyReader().getCodeSystemsToIgnore().contains(editionShortName)) {
                    // Code System has been defined as to-be-ignored (either by specifying name or shortname).
                    ignoredReasonMap.get(ReasonEditionSkipped.IGNORED_PER_FILE_EDITION).add(editionShortName);

                } else if (!maintainerType.equalsIgnoreCase("Managed Service")) {
                    // TODO: Handle Type-3 (non-Managed Service only)
                    ignoredReasonMap.get(ReasonEditionSkipped.TYPE_THREE_EDITION).add(editionShortName);

                } else {
                    FILTERED_CODE_SYSTEMS.add(codeSystem);
                }

            }

        }

        return ignoredReasonMap;
    }

    /**
     * Initialize sync.
     *
     * @throws Exception the exception
     */
    private void initializeSync() throws Exception {

        TERMSERVER_EDITION_TO_ORGANIZATION_MAP.clear();
    }

    /**
     * Count code systems.
     *
     * @param organizationIterator the organization iterator
     * @return the int
     */
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
     * @return the sets the
     * @throws Exception the exception
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

    /**
     * Indicates whether or not testing edition to process is the case.
     *
     * @param codeSystem the code system
     * @return <code>true</code> if so, <code>false</code> otherwise
     */
    private boolean isTestingEditionToProcess(final String codeSystem) {

        return ((getDeveloperTestingEditionShortName() == null || getDeveloperTestingEditionShortName().isEmpty()) || codeSystem.equalsIgnoreCase(getDeveloperTestingEditionShortName())
                || getUtilities().isInternationalEdition(codeSystem));

    }

}
