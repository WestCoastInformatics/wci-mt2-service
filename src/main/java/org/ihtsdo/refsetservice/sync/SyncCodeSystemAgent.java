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
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.Team;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.model.UserRole;
import org.ihtsdo.refsetservice.service.SecurityService;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.terminologyservice.OrganizationService;
import org.ihtsdo.refsetservice.terminologyservice.SnowstormConnection;
import org.ihtsdo.refsetservice.terminologyservice.TeamService;
import org.ihtsdo.refsetservice.util.CrowdGroupNameAlgorithm;
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

    private static final String MANAGED_SERVICE_CONTAINER_TYPE = "Managed Service";

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
        TYPE_THREE_EDITION,
        /** A non-managed service maintainer type. */
        NON_MANAGED_SERVICE
    };

    /* see superclass */
    @Override
    public void syncComponent(final TerminologyService service) throws Exception {

        LOG.info("Starting sync of CodeSystemAgent");

        initializeSync();

        // Get all code systems from Snowstorm
        final JsonNode organizationJsonRootNode = getSnowstormCodeSystems();

        // Determine code systems to sync
        analyzeTermServerCodeSystems(service, organizationJsonRootNode);

        // Sync Organizations reviewing which are new (creating them), missing (removing them), and unchanged.
        List<String> organizationsToInactivate = syncOrganizations(service);

        // Sync Editions reviewing which are new (creating them), missing (removing them), modified (removing them and then creating them), and unchanged.
        List<String> existingInBothShortNames = syncEditions(service);

        // Review both DB & Snowstorm editon-to-org map to ensure consistency
        syncEditionOrganizationAssociation(service, existingInBothShortNames);

        inactivateOrganizations(service, organizationsToInactivate);

    }

    private void inactivateOrganizations(TerminologyService service, List<String> organizationsToInactivate) throws Exception {
        List<Organization> activeDbOrganizations = readDbOrganizations(service).stream().filter(o -> o.isActive()).collect(Collectors.toList());

        for (String organizationName : organizationsToInactivate) {

            Stream<Organization> matchingOrganizationsStream = activeDbOrganizations.stream().filter(o -> o.getName().equals(organizationName));

            Organization organizationToInactivate = (Organization) getUtilities().validateMatches(matchingOrganizationsStream, organizationName);

            // Only inactivate those organizations that aren't pointing to an edition anymore
            if (OrganizationService.getOrganizationEditions(service, organizationToInactivate.getId()).getTotal() == 0) {

                OrganizationService.inactivateOrganization(service, SecurityService.getUserFromSession(), organizationToInactivate.getId());

                // TODO: SHouldnt' the below be moved to ORgService.inactivateOrg()?
                List<User> users = OrganizationService.getOrganizationUsers(service, organizationToInactivate, false).getItems();

                for (User organizationUser : users) {
                    organizationToInactivate =
                            OrganizationService.removeUserFromOrganization(service, SecurityService.getUserFromSession(), organizationUser.getId(), organizationToInactivate.getId());
                }

            }
        }
    }

    /**
     * Analyze term server code systems.
     * @param service
     *
     * @param organizationJsonRootNode the organization json root node
     * @throws Exception the exception
     */
    private void analyzeTermServerCodeSystems(final TerminologyService service, final JsonNode organizationJsonRootNode) throws Exception {

        final Iterator<JsonNode> organizationIterator = organizationJsonRootNode.iterator();
        LOG.info("Found " + countCodeSystems(organizationIterator) + " + Code Systems on term server: ");

        // Filter code systems (based on active-setting, ignoredCS list, testing situation, and bad data)
        final Map<ReasonEditionSkipped, Set<String>> ignoredReasonsMap = filterCodeSystems(service, organizationJsonRootNode);
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
                        // TODO: Update to be based on maintainerType
                        s.append("listed in ignoredCodeSystems.txt");
                        break;
                    case TYPE_THREE_EDITION:
                        s.append("Type-3");
                        break;
                    case NON_MANAGED_SERVICE:
                        s.append("not managed service");
                        break;
                    default:
                        break;
                }

                s.append(": ");
                ignoredReasonsMap.get(reason).stream().forEach(editionShortName -> s.append(editionShortName + ","));
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
     * @return
     * @throws Exception the exception
     */
    private List<String> syncOrganizations(final TerminologyService service) throws Exception {

        final Map<String, String> dbActiveOrganizationNameIdMaps = new HashMap<>();
        final Map<String, String> dbInactiveOrganizationNameIdMaps = new HashMap<>();
        final Set<String> termserverOrganizationNames = new HashSet<>();

        // Populate organization names lists of a) termserver code system names, b) rt2 database active organization names, and c) rt2 database inactive
        // organization names
        final List<Organization> dbOrganizations = service.getAll(Organization.class);

        dbOrganizations.stream().filter(o -> o.isActive()).forEach(o -> dbActiveOrganizationNameIdMaps.put(o.getName(), o.getId()));
        dbOrganizations.stream().filter(o -> !o.isActive()).forEach(o -> dbInactiveOrganizationNameIdMaps.put(o.getName(), o.getId()));

        LOG.debug("AAA1 {}", dbActiveOrganizationNameIdMaps.keySet());
        LOG.debug("AAA2 {}", dbInactiveOrganizationNameIdMaps.keySet());

        FILTERED_CODE_SYSTEMS.stream().forEach(codeSystem -> {
            termserverOrganizationNames.add(determineOrganizationName(codeSystem));
        });

        // See if any termserver organizations are new
        final List<String> addedOrganizations = termserverOrganizationNames.stream().filter(o -> !dbActiveOrganizationNameIdMaps.keySet().contains(o))
                .filter(o -> !dbInactiveOrganizationNameIdMaps.keySet().contains(o)).collect(Collectors.toList());
        LOG.debug("AAA3 {}", addedOrganizations);
        addedOrganizations.stream().forEach(name -> getDbHandler().addOrganziation(service, name, determineOrganizationDescription(name)));

        // Activate previously inactivated organizations. Note: Will log and update stats after remove those that were activatedAndModified
        final List<String> activatedOrganizations = termserverOrganizationNames.stream().filter(c -> dbInactiveOrganizationNameIdMaps.keySet().contains(c)).collect(Collectors.toList());
        LOG.debug("AAA4 {}", activatedOrganizations);
        activatedOrganizations.stream().forEach(n -> getDbHandler().updateOrganizationStatus(service, dbInactiveOrganizationNameIdMaps.get(n), true));

        // If have active organizations inactivate any active DB organizations that are not returned from termserver.
        // Note: No need for 'existing in both' case as only value to compare against termserver (owner) is also the primary key. Thus activating/inactivating is sufficient
        final List<String> inactivatedOrganizations = dbActiveOrganizationNameIdMaps.keySet().stream().filter(c -> !termserverOrganizationNames.contains(c))
                .filter(c -> !dbInactiveOrganizationNameIdMaps.keySet().contains(c)).collect(Collectors.toList());
        LOG.debug("AAA5 {}", inactivatedOrganizations);

        return inactivatedOrganizations;
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

            // Determine editions that are active in DB and found in termserver and compare for changes
            existingInBothShortNames.addAll(dbActiveEditionShortNames.stream().filter(c -> termserverShortNames.contains(c)).collect(Collectors.toList()));

            // Compare editions in termserver & active in db
            if (!existingInBothShortNames.isEmpty()) {
                final List<String> modifiedShortNames = compareAndModifyEditions(service, existingInBothShortNames, termserverShortNameCodeSystemMap);
                final List<String> unchangedShortNames = existingInBothShortNames.stream().filter(e -> !modifiedShortNames.contains(e)).collect(Collectors.toList());

                // Have modified... now revisit acivated to see if they too are modified
            }
        }

        // Compare editions in termserver & newly actived in db
        if (!activatedShortNames.isEmpty()) {
            final List<String> activatedAndModifiedShortNames = compareAndModifyEditions(service, activatedShortNames, termserverShortNameCodeSystemMap);

            // Finalize those editions that were only activated (and not further modified)
            activatedAndModifiedShortNames.stream().forEach(n -> activatedShortNames.remove(n));
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
    private void syncEditionOrganizationAssociation(final TerminologyService service, final List<String> existingShortNames) throws Exception {

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

                // Edition pointing to a different org. Update edition and udpate CROWD
                final List<Organization> termServerOrganizations = allDbOrganizations.stream().filter(o -> o.getName().equals(termserverOrganizationName)).collect(Collectors.toList());

                if (termServerOrganizations.isEmpty() || termServerOrganizations.size() > 1) {
                    throw new Exception("Can't be empty or with multiple with same name (" + termServerOrganizations + "), nor can it be the case that the organziation wasn't already created");
                } else {
                    // Existing org associated with edition
                    final Organization termServerOrganization = termServerOrganizations.iterator().next();

                    // Update CROWD with new rules
                    migrateOrganization(service, dbEdition, termServerOrganization);

                    STATISTICS.incrementEditionOrganizationMapChanged();
                }
            }
        }

    }
    // Move edition to new org. Projects will move with edition, but users and teams won't.
    // So need to see if they exist in new org, and if not create teams and add users

    private void migrateOrganization(final TerminologyService service, final Edition editionToMove, final Organization targetOrganization) throws Exception {
        final List<User> existingUsers = OrganizationService.getOrganizationUsers(service, editionToMove.getOrganization(), false).getItems();
        final List<Team> existingTeams = OrganizationService.getActiveOrganizationTeams(service, editionToMove.getOrganizationId()).getItems();

        Organization newOrganization = targetOrganization;

        LOG.info("Start migrating edition " + editionToMove.getName() + " from org: " + editionToMove.getOrganizationName() + "(" + editionToMove.getOrganizationId() + ") to org: "
                + newOrganization.getName() + "(" + newOrganization.getId());

        final Map<String, List<Project>> existingTeamToProjectsMap = new HashMap<>();
        for (Team team : existingTeams) {
            List<Project> projects = TeamService.getTeamProjects(team);

            existingTeamToProjectsMap.put(team.getId(), projects);
        }

        // Ensure all users in existing organization are also in target organization
        for (final User user : existingUsers) {

            if (newOrganization.getMembers().stream().noneMatch(u -> u.getId().equals(user.getId()))) {

                try {
                    newOrganization = OrganizationService.addUserToOrganization(service, SecurityService.getUserFromSession(), newOrganization.getId(), user);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                // TODO: SNOMED International doesn't ahve an owner, so a deafulat one is created. Add special handling on SI to avoid this nonesense and
                // move forward.
            }
        }

        // Ensure admin users are also in target organization
        for (String username : SyncAgent.getAdminUsernames()) {

            User user = getUtilities().getUser(service, username);

            if (user != null && (newOrganization.getMembers().stream().noneMatch(u -> u.getId().equals(user.getId())))) {
                newOrganization = OrganizationService.addUserToOrganization(service, SecurityService.getUserFromSession(), newOrganization.getId(), user);
            }
        }

        // Move the existing edition's teams
        Set<Team> updatedTeams = new HashSet<>();
        for (final Team team : existingTeams) {
            if (newOrganization.getMembers().stream().noneMatch(t -> t.getId().equals(team.getId()))) {
                team.setOrganization(newOrganization);
                final Team updatedTeam = service.update(team);
                updatedTeams.add(updatedTeam);
            }
        }

        // Remove all users from Org's CROWD to ensure don't clog up crowd entries for a given user
        Set<Project> organizationProjects = new HashSet<>();
        existingTeamToProjectsMap.values().stream().forEach(projectList -> organizationProjects.addAll(projectList));

        for (Project organizationProject : organizationProjects) {
            for (UserRole role : UserRole.getAllRoles()) {

                String groupName = CrowdGroupNameAlgorithm.generateCrowdGroupName(editionToMove.getOrganizationName(), organizationProject.getEdition().getName(), organizationProject.getName(),
                        role.getValue().toUpperCase(), true);

                for (User user : existingUsers) {
                    LOG.info("Would be deleting membership for user {} on group {}, but will have unintended consiquences if I do", user.getUserName(), groupName);
                    // TODO: actually call deleteMembership when this works
                    // CrowdAPIClient.deleteMembership(groupName,user.getUserName());
                }
            }

        }

        // TODO: add Teams to org based on project.getTeams() and add members/roles

        // TODO: Remove crowd membership in groups where org made inactive
        editionToMove.setOrganization(newOrganization);
        final Edition migratedEdition = service.update(editionToMove);

        LOG.info("Finished migrating edition: " + migratedEdition.getName());

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
     * @param service
     *
     * @param organizationJsonRootNode the organization json root node
     * @return the map
     * @throws Exception the exception
     */
    private Map<ReasonEditionSkipped, Set<String>> filterCodeSystems(TerminologyService service, final JsonNode organizationJsonRootNode) throws Exception {

        final Iterator<JsonNode> organizationIterator = organizationJsonRootNode.iterator();
        final Map<ReasonEditionSkipped, Set<String>> ignoredReasonMap = new EnumMap<>(ReasonEditionSkipped.class);

        ignoredReasonMap.put(ReasonEditionSkipped.WRONG_TESTING_EDITION, new HashSet<>());
        ignoredReasonMap.put(ReasonEditionSkipped.INACTIVE_EDITION, new HashSet<>());
        ignoredReasonMap.put(ReasonEditionSkipped.IGNORED_PER_FILE_EDITION, new HashSet<>());
        ignoredReasonMap.put(ReasonEditionSkipped.TYPE_THREE_EDITION, new HashSet<>());
        ignoredReasonMap.put(ReasonEditionSkipped.NON_MANAGED_SERVICE, new HashSet<>());

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
                if (getUtilities().isInternationalEdition(editionShortName)) {
                    FILTERED_CODE_SYSTEMS.add(codeSystem);
                } else {
                    if (isTesting() && !isTestingEditionToProcess(editionShortName)) {
                        ignoredReasonMap.get(ReasonEditionSkipped.WRONG_TESTING_EDITION).add(editionShortName);

                    } else if (codeSystem.has("active") && !codeSystem.get("active").asBoolean()) {
                        // Skipping inactive code system
                        ignoredReasonMap.get(ReasonEditionSkipped.INACTIVE_EDITION).add(editionShortName);

                    } else if (!codeSystem.has("maintainerType") || !MANAGED_SERVICE_CONTAINER_TYPE.equals(codeSystem.get("maintainerType").asText())) {
                        // Skipping inactive code system
                        ignoredReasonMap.get(ReasonEditionSkipped.NON_MANAGED_SERVICE).add(editionShortName);

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
