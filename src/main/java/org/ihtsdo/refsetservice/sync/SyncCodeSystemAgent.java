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
import org.ihtsdo.refsetservice.model.TeamType;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.model.UserRole;
import org.ihtsdo.refsetservice.service.SecurityService;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.terminologyservice.OrganizationService;
import org.ihtsdo.refsetservice.terminologyservice.TeamService;
import org.ihtsdo.refsetservice.util.AuditEntryHelper;
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

    final private Set<JsonNode> FILTERED_CODE_SYSTEMS;

    final private HashMap<String, String> TERM_SERVER_EDITION_TO_ORGANIZATION_MAP;

    private Set<User> systemAdminUsers = new HashSet<>();

    public SyncCodeSystemAgent(TerminologyService service, Set<JsonNode> filteredCodeSystems, HashMap<String, String> termServerEditionToOrganizationMap) throws Exception {

        this.FILTERED_CODE_SYSTEMS = filteredCodeSystems;
        this.TERM_SERVER_EDITION_TO_ORGANIZATION_MAP = termServerEditionToOrganizationMap;

        // TODO: DOn't clear admin users nor define them from config file
        systemAdminUsers.clear();

        for (String username : SyncAgent.getAdminUsernames()) {

            User user = getUtilities().getUser(service, username);

            if (user != null) {

                systemAdminUsers.add(user);
            }

        }

    }

    /* see superclass */
    @Override
    public void syncComponent(final TerminologyService service) throws Exception {

        LOG.info("Starting sync of CodeSystemAgent");

        // Sync Organizations reviewing which are new (creating them), missing (removing them), and unchanged.
        Map<Boolean, List<String>> migrationActivationMap = syncOrganizations(service);

        // Sync Editions reviewing which are new (creating them), missing (removing them), modified (removing them and then creating them), and unchanged.
        List<String> existingInBothShortNames = syncEditions(service, migrationActivationMap.get(false));

        // Review both DB & Snowstorm editon-to-org map to ensure consistency
        syncEditionOrganizationAssociations(service, existingInBothShortNames);

        toggleOrganizationStatus(service, migrationActivationMap);

    }

    private void toggleOrganizationStatus(TerminologyService service, Map<Boolean, List<String>> migrationActivationMap) throws Exception {

        List<Organization> activeDbOrganizations = readDbOrganizations(service).stream().filter(o -> o.isActive()).collect(Collectors.toList());

        for (boolean migrationDirection : migrationActivationMap.keySet()) {

            for (String organizationName : migrationActivationMap.get(migrationDirection)) {

                Stream<Organization> matchingOrganizationsStream = activeDbOrganizations.stream().filter(o -> o.getName().equals(organizationName));

                Organization organizationToMigrate = (Organization) getUtilities().validateMatches(matchingOrganizationsStream, organizationName);

                // Only inactivate those organizations that aren't pointing to an edition anymore
                if ((migrationDirection && !organizationToMigrate.isActive())
                    || (!migrationDirection && OrganizationService.getOrganizationEditions(service, organizationToMigrate.getId()).getTotal() == 0)) {

                    // TODO: Add (in migrationOrganization) the removal of users from crowd groups (check with Tim on timing)
                    OrganizationService.updateOrganizationStatus(service, SecurityService.getUserFromSession(), organizationToMigrate.getId(), migrationDirection);
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
        final Map<String, String> termserverOrganizationNameToEditionShortNameMap = new HashMap<>();

        // Populate organization names lists of a) termserver code system names, b) rt2 database active organization names, and c) rt2 database inactive
        // organization names
        final List<Organization> dbOrganizations = service.getAll(Organization.class);
        final List<Edition> dbEditions = service.getAll(Edition.class);

        dbOrganizations.stream().filter(o -> o.isActive()).forEach(o -> dbActiveOrganizationNameIdMaps.put(o.getName(), o.getId()));
        dbOrganizations.stream().filter(o -> !o.isActive()).forEach(o -> dbInactiveOrganizationNameIdMaps.put(o.getName(), o.getId()));

        for (JsonNode codeSystem : FILTERED_CODE_SYSTEMS) {

            termserverOrganizationNameToEditionShortNameMap.put(getUtilities().determineOrganizationName(codeSystem), getUtilities().determineEditionShortName(codeSystem));
        }

        // See if any termserver organizations are new
        final List<String> addedOrganizations = termserverOrganizationNameToEditionShortNameMap.keySet().stream()
            .filter(orgName -> !isTesting() || (isTesting() && termserverOrganizationNameToEditionShortNameMap.get(orgName).equals(testingEditionShortName)))
            .filter(orgName -> !dbActiveOrganizationNameIdMaps.keySet().contains(orgName)).filter(orgName -> !dbInactiveOrganizationNameIdMaps.keySet().contains(orgName)).collect(Collectors.toList());

        // Create new org
        addedOrganizations.stream().forEach(orgName -> getDbHandler().addOrganziation(service, orgName, getUtilities().determineOrganizationDescription(orgName)));

        // Activate previously inactivated organizations. Note: Will log and update stats after remove those that were activatedAndModified
        final List<String> organizationsToActivate = termserverOrganizationNameToEditionShortNameMap.keySet().stream()
            .filter(orgName -> !isTesting() || (isTesting() && termserverOrganizationNameToEditionShortNameMap.get(orgName).equals(testingEditionShortName)))
            .filter(c -> dbInactiveOrganizationNameIdMaps.keySet().contains(c)).collect(Collectors.toList());

        // Inactivate any active DB organizations that are not returned from termserver.
        // Note: No need for 'existing in both' case as only value to compare against termserver (owner) is also the primary key. Thus activating/inactivating is sufficient
        final List<String> organizationsToInactivate =
            dbActiveOrganizationNameIdMaps.keySet().stream().filter(orgName -> !termserverOrganizationNameToEditionShortNameMap.keySet().contains(orgName)).collect(Collectors.toList());

        LOG.info("Term Server orgs: {}", termserverOrganizationNameToEditionShortNameMap.size(), termserverOrganizationNameToEditionShortNameMap);
        LOG.info("DB active orgs: {}", dbActiveOrganizationNameIdMaps.keySet().size(), dbActiveOrganizationNameIdMaps.keySet());
        LOG.info("DB inactive orgs: {}", dbInactiveOrganizationNameIdMaps.keySet().size(), dbInactiveOrganizationNameIdMaps.keySet());
        LOG.info("{} added orgs: {}", addedOrganizations.size(), addedOrganizations);
        LOG.info("{} reactivated orgs: {}", organizationsToActivate.size(), organizationsToActivate);
        LOG.info("{} inactivated orgs: {}", organizationsToInactivate.size(), organizationsToInactivate);

        Map<Boolean, List<String>> migrationActivationMap = new HashMap<>();
        migrationActivationMap.put(true, organizationsToActivate);
        migrationActivationMap.put(false, organizationsToInactivate);

        return migrationActivationMap;
    }

    /**
     * Sync editions.
     *
     * @param service the service
     * @param organizationNamesToInactivate
     * @return the list
     * @throws Exception the exception
     */
    private List<String> syncEditions(final TerminologyService service, List<String> organizationNamesToInactivate) throws Exception {

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
        final List<String> addedShortNames = termserverShortNames.stream().filter(c -> !isTesting() || (isTesting() && c.equals(testingEditionShortName)))
            .filter(c -> !dbActiveEditionShortNames.contains(c)).filter(c -> !dbInactiveEditionShortNames.contains(c)).collect(Collectors.toList());

        addedShortNames.stream().forEach(shortName -> getDbHandler().addEdition(service, termserverShortNameCodeSystemMap.get(shortName), TERM_SERVER_EDITION_TO_ORGANIZATION_MAP.get(shortName)));

        // Activate previously inactivated editions. Note: Will log and update stats after remove those that were activatedAndModified
        final List<String> activatedShortNames = termserverShortNames.stream().filter(c -> !isTesting() || (isTesting() && c.equals(testingEditionShortName)))
            .filter(c -> dbInactiveEditionShortNames.contains(c)).collect(Collectors.toList());

        activatedShortNames.stream().forEach(n -> getDbHandler().updateEditionStatus(service, n, true));

        List<String> inactivatedShortNames = new ArrayList<>();
        List<String> modifiedShortNames = new ArrayList<>();
        List<String> activatedAndModifiedShortNames = new ArrayList<>();

        // If have active editions:
        // 1) Inactivate any active DB editions that are not in termserver
        // 2) Compare against termserver to identify any changes in attributes defined on term server
        if (!dbActiveEditionShortNames.isEmpty()) {

            inactivatedShortNames = dbActiveEditionShortNames.stream().filter(c -> !isTesting() || (isTesting() && c.equals(testingEditionShortName))).filter(c -> !termserverShortNames.contains(c))
                .collect(Collectors.toList());

            inactivatedShortNames.stream().forEach(n -> getDbHandler().updateEditionStatus(service, n, false));

            // Determine editions that are active in DB and found in termserver and compare for changes
            existingInBothShortNames.addAll(dbActiveEditionShortNames.stream().filter(c -> termserverShortNames.contains(c)).collect(Collectors.toList()));

            // Compare editions in termserver & active in db
            if (!existingInBothShortNames.isEmpty()) {

                modifiedShortNames = compareAndModifyEditions(service, existingInBothShortNames, termserverShortNameCodeSystemMap, organizationNamesToInactivate);
                STATISTICS.setEditionsModified(modifiedShortNames.size());
            }

        }

        // Now review just-activated editions to see if they need modification
        if (!activatedShortNames.isEmpty()) {

            activatedAndModifiedShortNames = compareAndModifyEditions(service, activatedShortNames, termserverShortNameCodeSystemMap, organizationNamesToInactivate);

            // Finalize those editions that were only activated (and not further modified)
            activatedAndModifiedShortNames.stream().forEach(n -> activatedShortNames.remove(n));
            STATISTICS.setEditionsModified(STATISTICS.getEditionsModified() + activatedAndModifiedShortNames.size());

        }

        LOG.info("{} Term Server shortNames: {}", termserverShortNameCodeSystemMap.keySet().size(), termserverShortNameCodeSystemMap.keySet());
        LOG.info("{} Term Server editionToOrganizations {}", TERM_SERVER_EDITION_TO_ORGANIZATION_MAP.size(), TERM_SERVER_EDITION_TO_ORGANIZATION_MAP);
        LOG.info("{} DB active editions: {}", dbActiveEditionShortNames.size(), dbActiveEditionShortNames);
        LOG.info("{} DB inactive editions: {}", inactivatedShortNames.size(), inactivatedShortNames);
        LOG.info("{} added editions: {}", addedShortNames.size(), addedShortNames);
        LOG.info("{} reactivated editions: {}", activatedShortNames.size(), activatedShortNames);
        LOG.info("{} inactivated editions: {}", inactivatedShortNames.size(), inactivatedShortNames);
        LOG.info("{} modified editions: {}", modifiedShortNames.size(), modifiedShortNames);
        LOG.info("{} activated & modified editions: {}", activatedAndModifiedShortNames.size(), activatedAndModifiedShortNames);

        return existingInBothShortNames;

    }

    /**
     * Sync edition organization maps.
     *
     * @param service the service
     * @param existingShortNames the existing short names
     * @throws Exception the exception
     */
    private void syncEditionOrganizationAssociations(final TerminologyService service, final List<String> existingShortNames) throws Exception {

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

        OrganizationService.checkEditPermissions(SecurityService.getUserFromSession(), targetOrganization);

        final List<User> existingUsers = OrganizationService.getOrganizationUsers(service, editionToMove.getOrganization(), false).getItems();
        final List<Team> existingOrganizationTeams = OrganizationService.getOrganizationTeams(service, editionToMove.getOrganization()).getItems();

        Organization newOrganization = targetOrganization;

        LOG.info("Start migrating edition " + editionToMove.getName() + " from org: " + editionToMove.getOrganizationName() + "(" + editionToMove.getOrganizationId() + ") to org: "
            + newOrganization.getName() + "(" + newOrganization.getId());

        // Ensure target organization (and it's admin team) are active before proceeding
        if (!targetOrganization.isActive()) {

            targetOrganization.setActive(true);

            newOrganization = service.update(targetOrganization);
            AuditEntryHelper.changeOrganizationStatusEntry(newOrganization);

            final Team adminTeam = OrganizationService.getOrganizationAdminTeam(service, targetOrganization.getId());

            if (!adminTeam.isActive()) {

                adminTeam.setActive(true);

                service.update(adminTeam);
            }

        }

        /* Add users to new organization */

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
        for (User adminUser : systemAdminUsers) {

            if (newOrganization.getMembers().stream().noneMatch(u -> u.getId().equals(adminUser.getId()))) {

                newOrganization = OrganizationService.addUserToOrganization(service, SecurityService.getUserFromSession(), newOrganization.getId(), adminUser);
            }

        }

        /* Move the existing organization's teams */
        final Set<Team> updatedTeams = new HashSet<>();
        final Map<String, List<Project>> existingTeamToProjectsMap = new HashMap<>();

        for (final Team team : existingOrganizationTeams) {

            // Move all but admin team
            if (!TeamType.ORGANIZATION.getText().equals(team.getType())) {

                final List<Project> projects = TeamService.getTeamProjects(team);
                existingTeamToProjectsMap.put(team.getId(), projects);

                team.setOrganization(newOrganization);

                final Team updatedTeam = service.update(team);
                updatedTeams.add(updatedTeam);

            } else {

                // Inactivate existing admin team prior to inactivating organization
                team.setActive(false);

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
     * @param organizationNamesToInactivate
     * @return the list
     * @throws Exception the exception
     */
    private List<String> compareAndModifyEditions(final TerminologyService service, final List<String> matchingEditionShortNames, final Map<String, JsonNode> termserverShortNameCodeSystemMap,
        List<String> organizationNamesToInactivate) throws Exception {

        final List<String> modifiedShortNames = new ArrayList<>();

        // Process one Organization per Edition.
        for (final String shortName : matchingEditionShortNames) {

            if (termserverShortNameCodeSystemMap.containsKey(shortName)) {

                List<Edition> dbEditions = readDbActiveEditions(service);

                // Find associated DB edition
                final List<Edition> matchingEditions = new ArrayList<>();

                // If have multiple editions that are ALL inactive, ignore issue entirely as will inactivate these later anyway
                for (Edition edition : dbEditions) {

                    if (edition.getShortName().equals(shortName)) {

                        matchingEditions.add(edition);
                    }

                }

                // See if the edition's organization is set to be inactivated. If so, no need to review the edition now as its fields will be updated if/when reactivated
                if (matchingEditions.size() > 1) {

                    throw new Exception("May only have a single edition per shortName " + shortName + ", but have multiple: " + matchingEditions);
                }

                final Edition dbEdition = matchingEditions.iterator().next();
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
