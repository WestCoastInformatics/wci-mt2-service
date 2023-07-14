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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.Organization;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.Team;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.model.UserRole;
import org.ihtsdo.refsetservice.service.SecurityService;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.terminologyservice.OrganizationService;
import org.ihtsdo.refsetservice.terminologyservice.TeamService;
import org.ihtsdo.refsetservice.util.CrowdGroupNameAlgorithm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * The Class SyncCodeSystemAgent.
 */
public class SyncCodeSystemAgent extends SyncAgent {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(SyncCodeSystemAgent.class);

    private Set<JsonNode> FILTERED_CODE_SYSTEMS;

    private HashMap<String, String> TERM_SERVER_EDITION_TO_ORGANIZATION_MAP;

    public SyncCodeSystemAgent(Set<JsonNode> filteredCodeSystems, HashMap<String, String> termServerEditionToOrganizationMap) {

        this.FILTERED_CODE_SYSTEMS = filteredCodeSystems;
        this.TERM_SERVER_EDITION_TO_ORGANIZATION_MAP = termServerEditionToOrganizationMap;

    }

    /* see superclass */
    @Override
    public void syncComponent(final TerminologyService service) throws Exception {

        LOG.info("Starting sync of CodeSystemAgent");

        // Sync Organizations reviewing which are new (creating them), missing (removing them), and unchanged.
        Map<Boolean, List<String>> migrationActivationMap = syncOrganizations(service);

        // Sync Editions reviewing which are new (creating them), missing (removing them), modified (removing them and then creating them), and unchanged.
        List<String> existingInBothShortNames = syncEditions(service);

        // Review both DB & Snowstorm editon-to-org map to ensure consistency
        syncEditionOrganizationAssociation(service, existingInBothShortNames);

        inactivateOrganizations(service, migrationActivationMap.get(false));

        // TODO: MigrateToActive ultimately using this call as well:
        // activatedOrganizations.stream().forEach(n -> getDbHandler().updateOrganizationStatus(service, dbInactiveOrganizationNameIdMaps.get(n), true));

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
     * Sync organizations.
     *
     * @param service the service
     * @return
     * @throws Exception the exception
     */
    private Map<Boolean, List<String>> syncOrganizations(final TerminologyService service) throws Exception {

        final Map<String, String> dbActiveOrganizationNameIdMaps = new HashMap<>();
        final Map<String, String> dbInactiveOrganizationNameIdMaps = new HashMap<>();
        final Set<String> termserverOrganizationNames = new HashSet<>();

        // Populate organization names lists of a) termserver code system names, b) rt2 database active organization names, and c) rt2 database inactive
        // organization names
        final List<Organization> dbOrganizations = service.getAll(Organization.class);

        dbOrganizations.stream().filter(o -> o.isActive()).forEach(o -> dbActiveOrganizationNameIdMaps.put(o.getName(), o.getId()));
        dbOrganizations.stream().filter(o -> !o.isActive()).forEach(o -> dbInactiveOrganizationNameIdMaps.put(o.getName(), o.getId()));

        FILTERED_CODE_SYSTEMS.stream().forEach(codeSystem -> {

            termserverOrganizationNames.add(getUtilities().determineOrganizationName(codeSystem));
        });

        // See if any termserver organizations are new
        final List<String> addedOrganizations = termserverOrganizationNames.stream().filter(o -> !dbActiveOrganizationNameIdMaps.keySet().contains(o))
            .filter(o -> !dbInactiveOrganizationNameIdMaps.keySet().contains(o)).collect(Collectors.toList());
        addedOrganizations.stream().forEach(name -> getDbHandler().addOrganziation(service, name, getUtilities().determineOrganizationDescription(name)));

        // Activate previously inactivated organizations. Note: Will log and update stats after remove those that were activatedAndModified
        final List<String> activatedOrganizations = termserverOrganizationNames.stream().filter(c -> dbInactiveOrganizationNameIdMaps.keySet().contains(c)).collect(Collectors.toList());

        // If have active organizations inactivate any active DB organizations that are not returned from termserver.
        // Note: No need for 'existing in both' case as only value to compare against termserver (owner) is also the primary key. Thus activating/inactivating is sufficient
        final List<String> inactivatedOrganizations = dbActiveOrganizationNameIdMaps.keySet().stream().filter(c -> !termserverOrganizationNames.contains(c))
            .filter(c -> !dbInactiveOrganizationNameIdMaps.keySet().contains(c)).collect(Collectors.toList());

        LOG.debug("AAA1 ( {} active orgs) {}", dbActiveOrganizationNameIdMaps.keySet().size(), dbActiveOrganizationNameIdMaps.keySet());
        LOG.debug("AAA2 ( {} inactive orgs) {}", dbInactiveOrganizationNameIdMaps.keySet().size(), dbInactiveOrganizationNameIdMaps.keySet());
        LOG.debug("AAA3 ( {} Term Server orgs) {}", termserverOrganizationNames.size(), termserverOrganizationNames);
        LOG.debug("AAA4 ( {} added orgs) {}", addedOrganizations.size(), addedOrganizations);
        LOG.debug("AAA5 ( {} reactivated orgs) {}", activatedOrganizations.size(), activatedOrganizations);
        LOG.debug("AAA6 {}", inactivatedOrganizations);

        Map<Boolean, List<String>> migrationActivationMap = new HashMap<>();
        migrationActivationMap.put(true, activatedOrganizations);
        migrationActivationMap.put(false, inactivatedOrganizations);

        return migrationActivationMap;
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
            .forEach(shortName -> getDbHandler().addEdition(service, termserverShortNameCodeSystemMap.get(shortName), TERM_SERVER_EDITION_TO_ORGANIZATION_MAP.get(shortName)));

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
            String termserverOrganizationName = TERM_SERVER_EDITION_TO_ORGANIZATION_MAP.get(shortName);

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

        if (!newOrganization.isActive()) {

            newOrganization = getDbHandler().updateOrganizationStatus(service, newOrganization.getId(), true);
        }

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

}
