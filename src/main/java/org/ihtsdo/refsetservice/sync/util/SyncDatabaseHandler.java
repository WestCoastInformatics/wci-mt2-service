/*
 * Copyright 2023 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.sync.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.ihtsdo.refsetservice.handler.snowstorm.SnomedConstants;
import org.ihtsdo.refsetservice.model.DefinitionClause;
import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.Organization;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.model.Team;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.model.enums.RefsetType;
import org.ihtsdo.refsetservice.model.enums.VersionStatus;
import org.ihtsdo.refsetservice.model.enums.WorkflowStatus;
import org.ihtsdo.refsetservice.service.SecurityService;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.terminologyservice.EditionService;
import org.ihtsdo.refsetservice.terminologyservice.OrganizationService;
import org.ihtsdo.refsetservice.terminologyservice.ProjectService;
import org.ihtsdo.refsetservice.util.AuditEntryHelper;
import org.ihtsdo.refsetservice.util.CrowdGroupNameAlgorithm;
import org.ihtsdo.refsetservice.util.ModelUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * The Class SyncDatabaseHandler.
 */
public class SyncDatabaseHandler {

    /** The log. */
    private static final Logger LOG = LoggerFactory.getLogger(SyncDatabaseHandler.class);

    /** The utilities. */
    private SyncUtilities utilities;

    /** The statistics. */
    private final SyncStatistics statistics;

    /** The sdf print date. */
    private final SimpleDateFormat sdfPrintDate = new SimpleDateFormat(utilities.getIsoDateTimeFormat());

    /**
     * Instantiates a {@link SyncDatabaseHandler} from the specified parameters.
     *
     * @param utilities the utilities
     * @param statistics the statistics
     */
    public SyncDatabaseHandler(final SyncUtilities utilities, final SyncStatistics statistics) {

        this.utilities = utilities;
        this.statistics = statistics;
    }

    /**
     * Sets the utilities.
     *
     * @param utilities the utilities
     */
    public void setUtilities(final SyncUtilities utilities) {

        this.utilities = utilities;
    }

    /**
     * Gets the utilities.
     *
     * @return the utilities
     */
    public SyncUtilities getUtilities() {

        return this.utilities;
    }

    /**
     * Adds the organziation.
     *
     * @param service the service
     * @param organizationName the organization name
     * @param organizationDescription the organization description
     * @return the organization
     */
    public Organization addOrganziation(final TerminologyService service, final String organizationName, final String countryCode) {

        final String organizationDescription = getUtilities().determineOrganizationDescription(organizationName);

        try {

            final Organization organization = new Organization();
            organization.setName(organizationName);
            organization.setDescription(organizationDescription);
            organization.setCountryCode(countryCode);
            organization.setCrowdId(CrowdGroupNameAlgorithm.getCrowdIdFromOrganizationName(organizationName));

            // Persist
            final Organization newOrganization = service.add(organization);

            LOG.info("Adding new Organziation: " + newOrganization.getId() + " (" + newOrganization.getName() + ")");

            service.add(AuditEntryHelper.addOrganizationEntry(newOrganization));

            statistics.incrementOrganizationsAdded();

            return newOrganization;
        } catch (final Exception e) {

            LOG.error("Failed to add edition associated with codeSystem: " + organizationName + " with Exception --> " + e.getMessage());

            e.printStackTrace();

            return null;
        }

    }

    /**
     * Adds the edition.
     *
     * @param service the service
     * @param codeSystem the code system
     * @param organizationName the organization name
     * @return the edition
     * @throws Exception
     */
    public Edition addEdition(final TerminologyService service, final JsonNode codeSystem, final String organizationName) throws Exception {

        // These have already been defined, so assume, don't check letting error handling play out instead
        final String shortName = codeSystem.get("shortName").asText();
        final String editionName = codeSystem.get("name").asText();
        final String branch = codeSystem.get("branchPath").asText();
        final String maintainerType = utilities.identifyMaintainerType(codeSystem, shortName);

        // Identify Matching Organization
        final List<Organization> dbOrganizations = service.getAll(Organization.class);
        final Stream<Organization> organizationStream = dbOrganizations.stream().filter(o -> o.getName().equals(organizationName));
        final Organization organization = (Organization) utilities.validateMatches(organizationStream, organizationName);

        // Create a single Admin team per Edition when we first discover it
        utilities.getOrCreateAdminOrganizationTeam(service, organization);

        final String defaultLanguageCode = utilities.identifyDefaultLanguageCode(codeSystem, editionName);

        final Set<String> defaultLanguageRefsets = utilities.identifyEditionLanguageRefsets(codeSystem, shortName, branch);

        getUtilities();
        // Case of no modules handled downstream
        final Set<String> editionModules = SyncUtilities.determineExtendedModules(branch);

        final Edition newEdition =
            addEdition(service, shortName, editionName, branch, defaultLanguageRefsets, editionModules, defaultLanguageCode, maintainerType, organization);

        utilities.printEditionValues(service, newEdition);

        return newEdition;

    }

    /**
     * Adds the edition.
     *
     * @param service the service
     * @param shortName the short name
     * @param name the name
     * @param branch the branch
     * @param defaultLanguageRefsets the default language refsets
     * @param modules the modules
     * @param defaultLanguageCode the default language code
     * @param maintainerType the maintainer type
     * @param organization the organization
     * @return the edition
     * @throws Exception
     */
    private Edition addEdition(final TerminologyService service, final String shortName, final String name, final String branch,
        final Set<String> defaultLanguageRefsets, final Set<String> modules, final String defaultLanguageCode, final String maintainerType,
        final Organization organization) throws Exception {

        final Edition edition = new Edition();

        edition.setShortName(shortName);
        edition.setName(name);
        edition.setBranch(branch);
        edition.setDefaultLanguageRefsets(defaultLanguageRefsets);
        edition.setModules(modules);
        edition.setDefaultLanguageCode(defaultLanguageCode);
        edition.setOrganization(organization);
        edition.setMaintainerType(maintainerType);

        // New ones only created as new
        edition.setActive(true);

        final Edition newEdition = service.add(edition);

        service.add(AuditEntryHelper.addEditionEntry(newEdition));

        LOG.info("Adding new Edition: " + newEdition.getId() + " (" + newEdition.getName() + ")");

        statistics.incrementEditionsAdded();

        return newEdition;
    }

    /**
     * Adds the refset.
     *
     * @param service the service
     * @param name the name
     * @param refsetId the refset id
     * @param moduleId the module id
     * @param versionDate the version date
     * @param type the type
     * @param versionStatus the version status
     * @param worfklowStatus the worfklow status
     * @param project the project
     * @return the refset
     */
    public Refset addRefset(final TerminologyService service, final String name, final String refsetId, final String moduleId, final long versionDate,
        final RefsetType type, final String branchPath, final VersionStatus versionStatus, final WorkflowStatus worfklowStatus, final Project project) {

        try {

            final Refset refset = new Refset();

            refset.setName(name);
            refset.setRefsetId(refsetId);
            refset.setModuleId(moduleId);
            refset.setVersionStatus(versionStatus);
            refset.setWorkflowStatus(worfklowStatus);
            refset.setActive(true);
            refset.setVersionDate(new Date(versionDate));
            refset.setType(type);
            refset.setLatestPublishedVersion(false);
            refset.setProject(project);
            refset.setPrivateRefset(false);
            refset.setBranchPath(branchPath);
            refset.setInUpgrade(false);
            refset.setInInactivate(false);

            // Set content labels. Both simply use latest
            refset.setBaseContentVersion(SnomedConstants.BRANCH_DATE_FORMAT.format(new Date(versionDate)) + " " + project.getEdition().getShortName());
            final String internationalContentVersion = EditionService.getDependencyModuleNameFromModuleDependency(refset);

            refset.setInternationalContentVersion(internationalContentVersion);

            // Persist
            final Refset newRefset = service.add(refset);

            service.add(AuditEntryHelper.addRefsetVersionEntry(newRefset));

            statistics.incrementRefsetVersionsAdded();

            LOG.info("Adding new Refset-Version Pair for : " + newRefset.getId() + " (" + newRefset.getName() + ") on: "
                + sdfPrintDate.format(newRefset.getVersionDate()));

            return newRefset;
        } catch (final Exception e) {

            LOG.error("Failed to add refset: " + name + " (" + sdfPrintDate.format(versionDate) + ") " + " with Exception --> " + e.getMessage());

            e.printStackTrace();

            return null;
        }

    }

    /**
     * Adds the project.
     *
     * @param service the service
     * @param projectName the project name
     * @param projectDescription the project description
     * @param edition the edition
     * @param crowdProjectId the crowd project id
     * @return the project
     */
    public Project addProject(final TerminologyService service, final String projectName, final String projectDescription, final Edition edition,
        final String crowdProjectId) {

        try {

            final Project project = new Project();
            project.setName(projectName);
            project.setDescription(projectDescription);
            project.setPrivateProject(false);
            project.setCrowdProjectId(crowdProjectId);
            project.setEdition(edition);
            project.setPrimaryContactEmail(edition.getOrganization().getPrimaryContactEmail());

            if (OrganizationService.getActiveOrganizationAdminTeam(service, edition.getOrganizationId()) != null) {

                project.getTeams().add(OrganizationService.getActiveOrganizationAdminTeam(service, edition.getOrganizationId()).getId());
            }

            // Persist
            final Project newProject = ProjectService.addProject(service, SecurityService.getUserFromSession(), project);

            service.add(AuditEntryHelper.addProjectEntry(newProject));

            statistics.incrementProjectsAdded();

            LOG.info("Adding new Project: " + newProject.getId() + " (" + newProject.getName() + ") ");

            return newProject;
        } catch (final Exception e) {

            LOG.error("Failed to add project: " + projectName + " with Exception --> " + e.getMessage());

            e.printStackTrace();

            return null;
        }

    }

    /**
     * Adds the WCI refset.
     *
     * @param service the service
     * @param u the u
     * @param name the name
     * @param refsetId the refset id
     * @param moduleId the module id
     * @param versionDate the version date
     * @param narrative the narrative
     * @param versionStatus the version status
     * @param worfklowStatus the worfklow status
     * @param project the project
     * @return the refset
     * @throws Exception
     */
    @Deprecated
    public Refset addWCIRefset(final TerminologyService service, final User u, final String name, final String refsetId, final Set<String> moduleId,
        final Date versionDate, final String narrative, final VersionStatus versionStatus, final WorkflowStatus worfklowStatus, final Project project)
        throws Exception {

        // Deprecated
        throw new Exception("Shoudln't be calling this as will be reomoved");

    }

    /**
     * Adds the team.
     *
     * @param service the service
     * @param teamName the team name
     * @param teamDescription the team description
     * @param organization the organization
     * @param teamType the team type
     * @return the team
     */
    public Team addTeam(final TerminologyService service, final String teamName, final String teamDescription, final Organization organization,
        final String teamType) {

        try {

            final Team team = new Team();
            team.setName(teamName);
            team.setDescription(teamDescription);
            team.setOrganization(organization);
            team.setPrimaryContactEmail("support-rt2@westcoastinformatics.com");
            team.setType(teamType);

            // Persist
            final Team newTeam = service.add(team);

            service.add(AuditEntryHelper.addTeamEntry(newTeam));

            LOG.info("Adding new Team: " + newTeam.getId() + " (" + newTeam.getName() + ") ");
            statistics.incrementTeamsAdded();

            return newTeam;
        } catch (final Exception e) {

            LOG.error("Failed to add team: " + teamName + " to " + organization.getName() + " with Exception --> " + e.getMessage());

            e.printStackTrace();

            return null;
        }

    }

    /**
     * Adds the user.
     *
     * @param service the service
     * @param name the name
     * @param userName the user name
     * @param email the email
     * @return the user
     */
    public User addUser(final TerminologyService service, final String name, final String userName, final String email) {

        try {

            final User user = new User();

            user.setName(name);
            user.setUserName(userName);
            user.setActive(true);
            user.setEmail(email);

            // Persist
            final User newUser = service.add(user);

            service.add(AuditEntryHelper.addUserEntry(newUser));

            LOG.info("Adding new User: " + newUser.getId() + " (" + newUser.getName() + ") ");

            return newUser;
        } catch (final Exception e) {

            LOG.error("Failed to add user: " + userName + " with Exception --> " + e.getMessage());

            e.printStackTrace();

            return null;
        }

    }

    /**
     * Update edition status.
     *
     * @param service the service
     * @param shortName the short name
     * @param isActive the is active
     * @return the edition
     */
    public Edition updateEditionStatus(final TerminologyService service, final String shortName, final boolean isActive) {

        try {

            final Stream<Edition> editionStream = service.getAll(Edition.class).stream().filter(e -> e.getShortName().equals(shortName));
            final Edition edition = (Edition) utilities.validateMatches(editionStream, shortName);

            if (isActive == edition.isActive()) {

                LOG.error("Attempting to set active status to " + isActive + " for an edition " + edition.getName() + " whose status is already that");
                return edition;
            }

            edition.setActive(isActive);

            final Edition updatedEdition = service.update(edition);

            LOG.info("Updated edition: " + updatedEdition.getId() + " to " + isActive + "  (" + updatedEdition.getName() + ") ");

            service.add(AuditEntryHelper.updateEditionEntry(updatedEdition));

            return updatedEdition;

        } catch (final Exception e) {

            LOG.error("Failed to update status of edition: " + shortName + " to " + isActive + " with Exception --> " + e.getMessage());

            return null;
        }

    }

    /**
     * Update organization status.
     *
     * @param service the service
     * @param organizationId the organization id
     * @param isActive the is active
     * @return the organization
     */
    public Organization updateOrganizationStatus(final TerminologyService service, final String organizationId, final boolean isActive) {

        try {

            final Organization organization = service.get(organizationId, Organization.class);

            if (isActive == organization.isActive()) {

                LOG.error("Attempting to set active status to " + isActive + " for an organization " + organizationId + " whose status is already that");
                return organization;
            }

            Organization updatedOrganization = null;

            updatedOrganization = OrganizationService.updateOrganizationStatus(service, SecurityService.getUserFromSession(), organization.getId(), isActive);

            service.add(AuditEntryHelper.updateOrganizationEntry(updatedOrganization));

            LOG.info("Updated organziation: " + updatedOrganization.getId() + " to " + isActive + "  (" + updatedOrganization.getName() + ") ");

            return updatedOrganization;

        } catch (final Exception e) {

            LOG.error("Failed to update status of organziation: " + organizationId + " to " + isActive + " with Exception --> " + e.getMessage());

            e.printStackTrace();

            return null;
        }

    }

    /**
     * Update edition.
     *
     * @param service the service
     * @param dbEdition the db edition
     * @return the edition
     */
    public Edition updateEdition(final TerminologyService service, final Edition dbEdition) {

        try {

            final Edition updatedEdition = service.update(dbEdition);

            service.add(AuditEntryHelper.updateEditionEntry(updatedEdition));

            statistics.incrementEditionsModified();

            LOG.info("Updated edition: " + updatedEdition.getId() + "  (" + updatedEdition.getName() + ") ");

            return updatedEdition;

        } catch (final Exception e) {

            LOG.error("Failed to update edition: " + dbEdition.getName() + " with Exception --> " + e.getMessage());

            e.printStackTrace();

            return null;
        }

    }

    /**
     * Update refset.
     *
     * @param service the service
     * @param refset the refset
     * @return the refset
     */
    public Refset updateRefset(final TerminologyService service, final Refset refset) {

        try {

            final Refset updatedRefset = service.update(refset);

            statistics.incrementRefsetVersionsModified();

            service.add(AuditEntryHelper.updateRefsetVersionEntry(updatedRefset));

            LOG.info("Updated Refset-Version Pair for : " + updatedRefset.getId() + " (" + updatedRefset.getName() + ") on: "
                + sdfPrintDate.format(updatedRefset.getVersionDate()));

            return updatedRefset;

        } catch (final Exception e) {

            LOG.error("Failed to update refset: " + refset.getName() + " ("
                + sdfPrintDate.format(refset.getVersionDate() + ") with Exception --> " + e.getMessage()));

            e.printStackTrace();

            return null;
        }

    }

    /**
     * Update multiple refsets.
     *
     * @param service the service
     * @param refsets the refsets
     * @return the sets the
     */
    public Set<Refset> updateMultipleRefsets(final TerminologyService service, final Set<Refset> refsets) {

        Refset refsetToPersist = null;

        try {

            final Set<Refset> updatedRefsets = new HashSet<>();

            // Adding refsets identified on termserver
            for (final Refset refset : refsets) {

                refsetToPersist = refset;

                final Refset updatedRefset = service.update(refsetToPersist);

                updatedRefsets.add(updatedRefset);
            }

            final StringBuffer updatedRefsetInfo = new StringBuffer();
            updatedRefsets.stream()
                .forEach(r -> updatedRefsetInfo.append("Pair Added: " + r.getName() + " - " + sdfPrintDate.format(r.getVersionDate()) + ", "));

            LOG.info("Updated multiple refset versions: " + updatedRefsetInfo.toString());
            service.add(AuditEntryHelper.updateMultipleRefsetVersionsEntry("updated " + refsets.size() + " refset/version pairs."));

            return updatedRefsets;
        } catch (final Exception e) {

            LOG.error("Failed to update multiple refests failing on : " + refsets.size() + " refsets with Exception --> " + e.getMessage());

            e.printStackTrace();

            return null;
        }

    }

    /**
     * Update refset version status.
     *
     * @param service the service
     * @param refsetId the refset id
     * @param versionDate the version date
     * @param isActive the is active
     * @return the refset
     */
    public Refset updateRefsetVersionStatus(final TerminologyService service, final String refsetId, final long versionDate, final boolean isActive) {

        try {

            final List<Refset> allRefsets = service.getAll(Refset.class);

            final Stream<Refset> refsetStream =
                allRefsets.stream().filter(r -> r.getRefsetId().equals(refsetId) && r.getVersionDate().getTime() == versionDate);
            final Refset matchingRefset = (Refset) utilities.validateMatches(refsetStream, refsetId + " / " + sdfPrintDate.format(versionDate));

            return updateRefset(service, matchingRefset);
        } catch (final Exception e) {

            LOG.error("Failed to update status of refset version: " + refsetId + " (" + sdfPrintDate.format(new Date(versionDate)) + ") to " + isActive
                + " with Exception --> " + e.getMessage());

            e.printStackTrace();

            return null;
        }

    }

    /**
     * Adds the definition clauses.
     *
     * @param service the service
     * @param rttId the rtt id
     * @return the sets the
     */
    public Set<DefinitionClause> addDefinitionClauses(final TerminologyService service, final String rttId) {

        final Set<DefinitionClause> refsetClauses = new HashSet<>();

        try {

            for (final String clauseJson : utilities.getPropertyReader().getRefsetSctToClausesMap().get(rttId)) {
                // ??FAILING HERE NOW???

                final DefinitionClause clause = ModelUtility.fromJson(clauseJson, DefinitionClause.class);

                final DefinitionClause persistedClause = service.add(clause);

                refsetClauses.add(persistedClause);
            }

            LOG.info("Added new DefinitionClauses for {} with clauses {}: ", rttId, refsetClauses);

        } catch (final Exception e) {

            LOG.error("Failed to read refset clauses from RTT for  rttId: " + rttId + " with Exception --> " + e.getMessage());

            e.printStackTrace();

            return null;
        }

        return refsetClauses;
    }

    /**
     * Update organization.
     *
     * @param service the service
     * @param organization the organization
     * @return the organization
     */
    public Organization updateOrganization(final TerminologyService service, final Organization organization) {

        try {

            final Organization updatedOrganization = service.update(organization);

            service.add(AuditEntryHelper.updateOrganizationEntry(updatedOrganization));

            LOG.info("Updated organization: " + updatedOrganization.getId() + "  (" + updatedOrganization.getName() + ") ");

            return updatedOrganization;

        } catch (final Exception e) {

            LOG.error("Failed to update organization: " + organization.getName() + " with Exception --> " + e.getMessage());

            e.printStackTrace();

            return null;
        }

    }

    /**
     * Update project.
     *
     * @param service the service
     * @param project the project
     * @return the project
     */
    public Project updateProject(final TerminologyService service, final Project project) {

        try {

            final Project updatedProject = service.update(project);

            LOG.info("Updated project: " + updatedProject.getId() + "  (" + updatedProject.getName() + ") ");

            statistics.incrementProjectsModified();

            service.add(AuditEntryHelper.updateProjectEntry(updatedProject));

            return updatedProject;

        } catch (final Exception e) {

            LOG.error("Failed to update project: " + project.getName() + " with Exception --> " + e.getMessage());

            e.printStackTrace();

            return null;
        }

    }

    /**
     * Adds the definition clause.
     *
     * @param service the service
     * @param clause the clause
     * @return the definition clause
     */
    public DefinitionClause addDefinitionClause(final TerminologyService service, final DefinitionClause clause) {

        try {

            // Persist
            final DefinitionClause addedClause = service.add(clause);

            LOG.info("Adding new DefinitionClause: " + addedClause.getId() + " (" + addedClause.getValue() + " / with isNegated: " + addedClause.getNegated()
                + ") ");

            return addedClause;
        } catch (final Exception e) {

            LOG.error(
                "Failed to add DefinitionClause: " + clause.getValue() + " / with isNegated: " + clause.getNegated() + " with Exception --> " + e.getMessage());

            e.printStackTrace();

            return null;
        }

    }
}
