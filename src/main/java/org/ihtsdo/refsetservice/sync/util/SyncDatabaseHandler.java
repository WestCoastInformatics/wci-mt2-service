package org.ihtsdo.refsetservice.sync.util;

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.ihtsdo.refsetservice.model.DefinitionClause;
import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.Organization;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.model.Team;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.model.UserRole;
import org.ihtsdo.refsetservice.service.SecurityService;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.terminologyservice.OrganizationService;
import org.ihtsdo.refsetservice.terminologyservice.ProjectService;
import org.ihtsdo.refsetservice.terminologyservice.RefsetService;
import org.ihtsdo.refsetservice.terminologyservice.TeamService;
import org.ihtsdo.refsetservice.util.AuditEntryHelper;
import org.ihtsdo.refsetservice.util.CrowdGroupNameAlgorithm;
import org.ihtsdo.refsetservice.util.ModelUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;

public class SyncDatabaseHandler {

    private final Logger LOG = LoggerFactory.getLogger(SyncDatabaseHandler.class);

    private SyncUtilities utilities;

    public SyncDatabaseHandler(final SyncUtilities utilities) {
        this.utilities = utilities;
    }

    public void setUtilities(final SyncUtilities utilities) {
        this.utilities = utilities;
    }

    public Edition addEdition(final TerminologyService service, final JsonNode codeSystem, final String organizationName) {

        try {
            // These have already been defined, so assume, don't check letting error handling play out instead
            final String shortName = codeSystem.get("shortName").asText();
            final String editionName = codeSystem.get("name").asText();
            final String branch = codeSystem.get("branchPath").asText();
            final String maintainerType = utilities.determineMaintainerType(codeSystem, shortName);

            // Identify Matching Organization

            final List<Organization> organizations = service.getAll(Organization.class).stream().filter(o -> o.getName().equals(organizationName)).collect(Collectors.toList());

            utilities.validateMatches(organizations, organizationName);

            final Organization organization = organizations.iterator().next();

            // Create a single Admin team per Edition when we first discover it
            createAdminOrganizationTeam(service, organization);

            final String defaultLanguageCode = utilities.identifyDefaultLanguageCode(codeSystem, editionName);

            final Set<String> defaultLanguageRefsets = utilities.identifyDefaultLanguageRefsets(codeSystem, shortName);

            // Case of no modules handled downstream
            final Set<String> editionModules = utilities.identifyModules(shortName, editionName, branch, codeSystem);

            final Edition newEdition = addEdition(service, shortName, editionName, branch, defaultLanguageRefsets, editionModules, defaultLanguageCode, maintainerType, organization);

            utilities.printEditionValues(service, newEdition);

            return newEdition;
        } catch (final Exception e) {
            final String codeSystemData = codeSystem.has("shortName") ? codeSystem.get("shortName").asText() : codeSystem.toPrettyString();
            LOG.error("Failed to add edition associated with codeSystem: " + codeSystemData + " with Exception --> " + e);

            e.printStackTrace();

            return null;
        }

    }

    public Organization addOrganziation(final TerminologyService service, final String organizationName, final String organizationDescription) {

        try {
            final Organization organization = new Organization();
            organization.setName(organizationName);
            organization.setDescription(organizationDescription);

            // Persist
            final Organization newOrganization = service.add(organization);

            LOG.info("Adding new Organziation: " + newOrganization.getId() + " (" + newOrganization.getName() + ")");

            service.add(AuditEntryHelper.addOrganizationEntry(newOrganization));

            return newOrganization;
        } catch (final Exception e) {
            LOG.error("Failed to add edition associated with codeSystem: " + organizationName + " with Exception --> " + e.getMessage());

            e.printStackTrace();

            return null;
        }

    }

    private Edition addEdition(final TerminologyService service, final String shortName, final String name, final String branch, final Set<String> defaultLanguageRefsets, final Set<String> modules,
        final String defaultLanguageCode, final String maintainerType, final Organization organization) {

        try {
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

            return newEdition;
        } catch (Exception e) {
            LOG.error("Failed to add edition associated with codeSystem: " + shortName + " with Exception --> " + e.getMessage());

            return null;
        }
    }

    public Refset addRefset(final TerminologyService service, final String name, final String refsetId, final String moduleId, long versionDate, final String type, final Project project) {

        try {
            final Refset refset = new Refset();

            refset.setName(name);
            refset.setRefsetId(refsetId);
            refset.setModuleId(moduleId);
            refset.setVersionStatus("PUBLISHED");
            refset.setWorkflowStatus("PUBLISHED");
            refset.setActive(true);
            refset.setVersionDate(new Date(versionDate));
            refset.setType(type);
            refset.setLatestPublishedVersion(false);
            refset.setProject(project);

            // Persist
            final Refset newRefset = service.add(refset);

            service.add(AuditEntryHelper.addRefsetVersionEntry(newRefset));

            LOG.info("Adding new Refset-Version Pair for : " + newRefset.getId() + " (" + newRefset.getName() + ") on: " + newRefset.getVersionDate());

            return newRefset;
        } catch (Exception e) {
            LOG.error("Failed to add refset: " + name + " (" + versionDate + ") " + " with Exception --> " + e.getMessage());

            e.printStackTrace();

            return null;
        }

    }

    public Project addProject(final TerminologyService service, final String projectName, final String projectDescription, final Edition edition) {

        try {
            final Project project = new Project();
            project.setName(projectName);
            project.setDescription(projectDescription);
            project.setPrivateProject(false);
            project.setCrowdProjectId(CrowdGroupNameAlgorithm.getProjectString(projectName));
            project.setEdition(edition);
            project.setPrimaryContactEmail(edition.getOrganization().getPrimaryContactEmail());

            if (OrganizationService.getOrganizationAdminTeam(service, edition.getOrganizationId()) != null) {
                project.getTeams().add(OrganizationService.getOrganizationAdminTeam(service, edition.getOrganizationId()).getId());
            }

            // Persist
            final Project newProject = ProjectService.addProject(SecurityService.getUserFromSession(), project);

            service.add(AuditEntryHelper.addProjectEntry(newProject));

            LOG.info("Adding new Project: " + newProject.getId() + " (" + newProject.getName() + ") ");

            return newProject;
        } catch (Exception e) {
            LOG.error("Failed to add project: " + projectName + " with Exception --> " + e.getMessage());

            e.printStackTrace();

            return null;
        }
    }

    public Refset addWCIRefset(final TerminologyService service, final User u, final String name, final String refsetId, final String moduleId, final Date versionDate, final String narrative,
        final Project project) {

        try {
            final Edition e = service.get(project.getEditionId(), Edition.class);

            if (!utilities.isDeveloperEdition(e.getShortName())) {
                throw new Exception("Cannot modify the workflow status of anything other than the developer org");
            }

            LOG.info("Adding WCI Testing Org's single project: " + project);

            final Refset refsetParameters = new Refset();

            refsetParameters.setName(name);
            refsetParameters.setRefsetId(refsetId);
            refsetParameters.setModuleId(moduleId);
            refsetParameters.setVersionStatus("PUBLISHED");
            refsetParameters.setWorkflowStatus("PUBLISHED");
            refsetParameters.setActive(true);
            refsetParameters.setVersionDate(versionDate);
            refsetParameters.setVersionNotes("");
            refsetParameters.setType(Refset.EXTENSIONAL);
            refsetParameters.setNarrative(narrative);
            refsetParameters.setParentConceptId(SyncUtilities.SIMPLE_REFSET_TYPE_CONCEPT);
            refsetParameters.setProject(project);
            refsetParameters.setLatestPublishedVersion(false);

            // Sets up completely different than normal addRefset routine
            final Object returned = RefsetService.createRefset(service, u, refsetParameters);

            if (returned instanceof String) {

                throw new Exception((String) returned);
            } else {

                final Refset refset = (Refset) returned;

                LOG.info("Added new WCI Refset - " + refset.getId() + " (" + refset.getName() + ")");

                final Refset updatedRefset = utilities.initializeWorkflowStatus(service, refset);

                LOG.info(" and then updated the new WCI refset's Workflow Status");

                service.add(AuditEntryHelper.addRefsetVersionEntry(updatedRefset));

                return updatedRefset;
            }

        } catch (Exception e) {
            LOG.error("Failed to add WCI refset: " + name + " (" + versionDate + ") with Exception --> " + e.getMessage());

            e.printStackTrace();

            return null;
        }

    }

    public Team addTeam(final TerminologyService service, final String teamName, final String teamDescription, final Organization organization) {

        try {
            final Team team = new Team();
            team.setName(teamName);
            team.setDescription(teamDescription);
            team.setOrganization(organization);
            team.setPrimaryContactEmail("support-rt2@westcoastinformatics.com");

            // Persist
            final Team newTeam = service.add(team);

            service.add(AuditEntryHelper.addTeamEntry(newTeam));

            LOG.info("Adding new Team: " + newTeam.getId() + " (" + newTeam.getName() + ") ");

            return newTeam;
        } catch (Exception e) {
            LOG.error("Failed to add team: " + teamName + " with Exception --> " + e.getMessage());

            e.printStackTrace();

            return null;
        }

    }

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
        } catch (Exception e) {
            LOG.error("Failed to add user: " + userName + " with Exception --> " + e.getMessage());

            e.printStackTrace();

            return null;
        }

    }

    public Edition updateEditionStatus(final TerminologyService service, final String shortName, boolean isActive) {

        try {
            final List<Edition> matchingEditions = service.getAll(Edition.class).stream().filter(e -> e.getShortName().equals(shortName)).collect(Collectors.toList());
            utilities.validateMatches(matchingEditions, shortName);

            final Edition edition = matchingEditions.iterator().next();

            if (isActive == edition.isActive()) {

                LOG.error("Attempting to set active status to " + isActive + " for an edition " + edition.getName() + " whose status is already that");
                return edition;
            }

            edition.setActive(isActive);

            final Edition updatedEdition = service.update(edition);

            LOG.info("Updated edition: " + updatedEdition.getId() + " to " + isActive + "  (" + updatedEdition.getName() + ") ");

            service.add(AuditEntryHelper.updateEditionEntry(updatedEdition));

            return updatedEdition;

        } catch (Exception e) {
            LOG.error("Failed to update status of edition: " + shortName + " to " + isActive + " with Exception --> " + e.getMessage());

            return null;
        }

    }

    public Organization updateOrganizationStatus(final TerminologyService service, final String organizationName, boolean isActive) {

        try {
            final List<Organization> allOrganizations = service.getAll(Organization.class);

            final List<Organization> matchingOrganizations = allOrganizations.stream().filter(o -> o.getName().equals(organizationName)).collect(Collectors.toList());
            utilities.validateMatches(matchingOrganizations, organizationName);

            final Organization organization = matchingOrganizations.iterator().next();

            if (isActive == organization.isActive()) {
                LOG.error("Attempting to set active status to " + isActive + " for an organization " + organizationName + " whose status is already that");
                return organization;
            }

            Organization updatedOrganization = null;
            if (!isActive) {
                updatedOrganization = OrganizationService.inactivateOrganization(service, SecurityService.getUserFromSession(), organization.getId());

            } else {
                // For now, just activate organization and adminTeam. Rest is up to admins
                organization.setActive(isActive);
                updatedOrganization = service.update(organization);

                final Team adminTeam = OrganizationService.getOrganizationAdminTeam(service, updatedOrganization.getId());
                adminTeam.setActive(true);
                service.update(adminTeam);
            }

            service.add(AuditEntryHelper.updateOrganizationEntry(updatedOrganization));

            LOG.info("Updated organziation: " + updatedOrganization.getId() + " to " + isActive + "  (" + updatedOrganization.getName() + ") ");

            return updatedOrganization;

        } catch (Exception e) {
            LOG.error("Failed to update status of organziation: " + organizationName + " to " + isActive + " with Exception --> " + e.getMessage());

            e.printStackTrace();

            return null;
        }
    }

    public Edition updateEdition(final TerminologyService service, final Edition dbEdition) {

        try {
            final Edition updatedEdition = service.update(dbEdition);

            service.add(AuditEntryHelper.updateEditionEntry(updatedEdition));

            LOG.info("Updated edition: " + updatedEdition.getId() + "  (" + updatedEdition.getName() + ") ");

            return updatedEdition;

        } catch (Exception e) {
            LOG.error("Failed to update edition: " + dbEdition.getName() + " with Exception --> " + e.getMessage());

            e.printStackTrace();

            return null;
        }
    }

    public Refset updateRefset(final TerminologyService service, final Refset refset) {

        try {
            final Refset updatedRefset = service.update(refset);

            service.add(AuditEntryHelper.updateRefsetVersionEntry(updatedRefset));

            LOG.info("Updated Refset-Version Pair for : " + updatedRefset.getId() + " (" + updatedRefset.getName() + ") on: " + updatedRefset.getVersionDate());

            return updatedRefset;

        } catch (Exception e) {
            LOG.error("Failed to update refset: " + refset.getName() + " (" + refset.getVersionDate() + ") with Exception --> " + e.getMessage());

            e.printStackTrace();

            return null;
        }
    }

    public Set<Refset> updateMultipleRefsets(final TerminologyService service, final Set<Refset> refsets) {

        final Set<String> refsetDbIds = new HashSet<>();

        try {
            final Set<Refset> updatedRefsets = new HashSet<>();

            for (final Refset refset : refsets) {
                refsetDbIds.add(refset.getId());
            }

            // Adding refsets identified on snowstorm
            for (final Refset refset : refsets) {

                final Refset updatedRefset = service.update(refset);

                service.add(AuditEntryHelper.updateRefsetVersionEntry(updatedRefset));

                LOG.info("Updated refset version: " + updatedRefset.getId() + "  (" + updatedRefset.getName() + ") " + updatedRefset.getVersionDate());

                updatedRefsets.add(updatedRefset);
            }

            return updatedRefsets;
        } catch (Exception e) {
            LOG.error("Failed to update refest database ids: " + refsetDbIds + " with Exception --> " + e.getMessage());

            e.printStackTrace();

            return null;
        }

    }

    public Refset updateRefsetVersionStatus(final TerminologyService service, final Refset refset, boolean isActive) {

        try {
            if (isActive == refset.isActive()) {
                LOG.error("Attempting to set active status to " + isActive + " for an refset " + refset.getName() + " whose status is already that");
                return refset;
            }

            refset.setActive(isActive);

            // Persist
            final Refset updatedRefset = service.update(refset);

            service.add(AuditEntryHelper.changeRefsetStatusEntry(updatedRefset));

            LOG.info("Updated status of refset version: " + updatedRefset.getId() + "  (" + updatedRefset.getName() + ") " + updatedRefset.getVersionDate());

            return updatedRefset;
        } catch (Exception e) {
            LOG.error("Failed to update status of refset: " + refset.getName() + " (" + refset.getVersionDate() + ") to " + isActive + " with Exception --> " + e.getMessage());

            e.printStackTrace();

            return null;
        }

    }

    public Refset updateRefsetVersionStatus(final TerminologyService service, final String refsetId, long versionDate, boolean isActive) {

        try {
            final List<Refset> allRefsets = service.getAll(Refset.class);
            final List<Refset> matchingRefsets = allRefsets.stream().filter(r -> r.getRefsetId().equals(refsetId) && r.getVersionDate().getTime() == versionDate).collect(Collectors.toList());
            utilities.validateMatches(matchingRefsets, refsetId + " / " + versionDate);

            final Refset matchingRefset = matchingRefsets.iterator().next();

            return updateRefset(service, matchingRefset);
        } catch (Exception e) {
            LOG.error("Failed to update status of refset version: " + refsetId + " (" + versionDate + ") to " + isActive + " with Exception --> " + e.getMessage());

            e.printStackTrace();

            return null;
        }
    }

    public Set<DefinitionClause> addDefinitionClauses(final TerminologyService service, final String rttId) {

        final Set<DefinitionClause> refsetClauses = new HashSet<>();

        try {
            for (final String clauseJson : utilities.getPropertyReader().getRttRefsetToClausesMap().get(rttId)) {

                final DefinitionClause clause = ModelUtility.fromJson(clauseJson, DefinitionClause.class);

                final SyncPersistenceMetadata metadata = utilities.getPropertyReader().getMetadataMap().get("refset-" + rttId);

                clause.setModified(metadata.getModified());
                clause.setCreated(metadata.getModified());
                clause.setModifiedBy(metadata.getModifiedBy());

                LOG.info("Adding new DefinitionClause: " + clause.getId() + " (" + clause + ") ");

                final DefinitionClause persistedClause = service.add(clause);

                refsetClauses.add(persistedClause);
            }
        } catch (Exception e) {
            LOG.error("Failed to read refset clauses from RTT for  rttId: " + rttId + " with Exception --> " + e.getMessage());

            e.printStackTrace();

            return null;
        }

        return refsetClauses;
    }

    public Organization updateOrganization(final TerminologyService service, final Organization organization) {

        try {
            final Organization updatedOrganization = service.update(organization);

            service.add(AuditEntryHelper.updateOrganizationEntry(updatedOrganization));

            LOG.info("Updated organization: " + updatedOrganization.getId() + "  (" + updatedOrganization.getName() + ") ");

            return updatedOrganization;

        } catch (Exception e) {
            LOG.error("Failed to update organization: " + organization.getName() + " with Exception --> " + e.getMessage());

            e.printStackTrace();

            return null;
        }
    }

    public Project updateProject(final TerminologyService service, final Project project) {

        try {
            final Project updatedProject = service.update(project);

            LOG.info("Updated project: " + updatedProject.getId() + "  (" + updatedProject.getName() + ") ");

            service.add(AuditEntryHelper.updateProjectEntry(updatedProject));

            return updatedProject;

        } catch (Exception e) {
            LOG.error("Failed to update project: " + project.getName() + " with Exception --> " + e.getMessage());

            e.printStackTrace();

            return null;
        }
    }

    public DefinitionClause addDefinitionClause(final TerminologyService service, final DefinitionClause clause) {

        try {
            // Persist
            final DefinitionClause addedClause = service.add(clause);

            LOG.info("Adding new DefinitionClause: " + addedClause.getId() + " (" + addedClause.getValue() + " / with isNegated: " + addedClause.getNegated() + ") ");

            return addedClause;
        } catch (Exception e) {
            LOG.error("Failed to add DefinitionClause: " + clause.getValue() + " / with isNegated: " + clause.getNegated() + " with Exception --> " + e.getMessage());

            e.printStackTrace();

            return null;
        }
    }

    public Set<Refset> updateRefsetStatusAllVersions(final TerminologyService service, final String refsetId, boolean isActive) {

        final Set<Refset> updatedRefsetVersions = new HashSet<>();

        try {
            final List<Refset> matchingRefsetVersions = service.getAll(Refset.class).stream().filter(r -> r.getRefsetId().equals(refsetId)).collect(Collectors.toList());

            for (final Refset refsetVersion : matchingRefsetVersions) {

                if (isActive == refsetVersion.isActive()) {

                    LOG.error("Attempting to set active status to " + isActive + " for an refset " + refsetVersion.getName() + " whose status is already that");
                    continue;
                }

                refsetVersion.setActive(isActive);

                final Refset updatedRefsetVersion = updateRefset(service, refsetVersion);

                LOG.info("Updated refset version status: " + updatedRefsetVersion.getId() + " to " + isActive + "  (" + updatedRefsetVersion.getName() + " / " + updatedRefsetVersion.getVersionDate()
                        + ") ");

                updatedRefsetVersions.add(updatedRefsetVersion);
            }
            return updatedRefsetVersions;
        } catch (Exception e) {
            LOG.error("Failed to update status of all versions of refsetId: " + refsetId + " to " + isActive + " with Exception --> " + e.getMessage());

            return null;
        }

    }

    public Team createAdminOrganizationTeam(final TerminologyService service, final Organization organization) throws Exception {

        try {
            Team adminTeam = OrganizationService.getOrganizationAdminTeam(service, organization.getId());

            if (adminTeam == null) {

                adminTeam = addTeam(service, TeamService.generateOrganizationTeamName(organization), TeamService.getOrganizationTeamDescription(organization), organization);

                for (final UserRole role : UserRole.getAllRoles()) {
                    adminTeam = TeamService.addRoleToTeam(SecurityService.getUserFromSession(), adminTeam.getId(), UserRole.getRoleString(role));
                }
            }

            return adminTeam;
        } catch (Exception e) {
            LOG.error("Failed to create admin team with Exception --> " + e.getMessage());

            return null;
        }
    }
}
