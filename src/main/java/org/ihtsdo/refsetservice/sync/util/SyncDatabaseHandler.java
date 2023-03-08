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
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.sync.SyncOperationsInitializer;
import org.ihtsdo.refsetservice.terminologyservice.OrganizationService;
import org.ihtsdo.refsetservice.terminologyservice.RefsetService;
import org.ihtsdo.refsetservice.util.AuditEntryHelper;
import org.ihtsdo.refsetservice.util.CrowdGroupNameAlgorithm;
import org.ihtsdo.refsetservice.util.ModelUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;

public class SyncDatabaseHandler {

    private final Logger logger = LoggerFactory.getLogger(SyncDatabaseHandler.class);

    private SyncUtilities utilities;

    public SyncDatabaseHandler(SyncUtilities utilities) {
        this.utilities = utilities;
    }

    public void setUtilities(SyncUtilities utilities) {
        this.utilities = utilities;
    }

    public Edition addEdition(JsonNode codeSystem, String organizationName) {
        try (TerminologyService service = new TerminologyService()) {

            // These have already been defined, so assume, don't check letting error handling play out instead
            final String shortName = codeSystem.get("shortName").asText();
            final String editionName = codeSystem.get("name").asText();
            final String branch = codeSystem.get("branchPath").asText();
            final String maintainerType = utilities.determineMaintainerType(codeSystem, shortName);

            // Identify Matching Organization

            List<Organization> organizations = service.getAll(Organization.class).stream().filter(o -> o.getName().equals(organizationName)).collect(Collectors.toList());

            utilities.validateMatches(organizations, organizationName);

            Organization organization = organizations.iterator().next();

            // Create a single Admin team per Edition w hen we first discover it
            final SyncOperationsInitializer initializer = new SyncOperationsInitializer(utilities);
            initializer.createAdminOrganizationTeam(organization);

            final String defaultLanguageCode = utilities.identifyDefaultLanguageCode(codeSystem, editionName);

            final Set<String> defaultLanguageRefsets = utilities.identifyDefaultLanguageRefsets(codeSystem, shortName);

            // Case of no modules handled downstream
            final Set<String> editionModules = utilities.identifyModules(shortName, editionName, branch, codeSystem);

            Edition newEdition = addEdition(shortName, editionName, branch, defaultLanguageRefsets, editionModules, defaultLanguageCode, maintainerType, organization);

            utilities.printEditionValues(newEdition);

            return newEdition;
        } catch (Exception e) {
            String codeSystemData = codeSystem.has("shortName") ? codeSystem.get("shortName").asText() : codeSystem.toPrettyString();
            logger.error("Failed to add edition associated with codeSystem: " + codeSystemData + " with Exception --> " + e);

            e.printStackTrace();

            return null;
        }

    }

    public Organization addOrganziation(final String orgName, String orgDesc) {

        try (final TerminologyService service = new TerminologyService()) {

            initializeService(service);

            final Organization org = new Organization();
            org.setName(orgName);
            org.setDescription(orgDesc);

            // Persist
            final Organization o = service.add(org);
            service.add(AuditEntryHelper.syncEntry(new Date()));

            logger.info("Adding new Organziation: " + o.getId() + " (" + o.getName() + ")");

            return o;
        } catch (Exception e) {
            logger.error("Failed to add edition associated with codeSystem: " + orgName + " with Exception --> " + e.getMessage());

            e.printStackTrace();

            return null;
        }

    }

    private Edition addEdition(String shortName, String name, String branch, Set<String> defaultLanguageRefsets, Set<String> modules, String defaultLanguageCode, String maintainerType,
        Organization organization) {

        try (final TerminologyService service = new TerminologyService()) {

            initializeService(service);

            Edition edition = new Edition();

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

            Edition e = service.add(edition);

            logger.info("Adding new Edition: " + e.getId() + " (" + e.getName() + ")");

            return e;
        } catch (Exception e) {
            logger.error("Failed to add edition associated with codeSystem: " + shortName);

            return null;
        }
    }

    public void initializeService(TerminologyService service) {

        service.setModifiedBy("Sync");
        service.setModifiedFlag(true);

    }

    public Refset addRefset(String name, String refsetId, String moduleId, long versionDate, String type, Project project) {
        try (final TerminologyService service = new TerminologyService()) {

            initializeService(service);

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
            final Refset r = service.add(refset);

            logger.info("Adding new Refset and/or Version for : " + r.getId() + " (" + r.getName() + ") on: " + r.getVersionDate());

            return r;
        } catch (Exception e) {
            logger.error("Failed to add refset: " + name + " (" + versionDate + ") " + " with Exception --> " + e.getMessage());

            e.printStackTrace();

            return null;
        }

    }

    public Project addProject(String projectName, String projectDescription, Edition edition) {

        try (final TerminologyService service = new TerminologyService()) {

            initializeService(service);

            final Project project = new Project();
            project.setName(projectName);
            project.setDescription(projectDescription);
            project.setPrivateProject(false);
            project.setCrowdProjectId(CrowdGroupNameAlgorithm.getProjectString(projectName));
            project.setEdition(edition);
            project.getTeams().add(OrganizationService.getOrganizationAdminTeam(service, edition.getOrganizationId()).getId());

            // Persist
            final Project p = service.add(project);

            logger.info("Adding new Project: " + p.getId() + " (" + p.getName() + ") ");

            return p;
        } catch (Exception e) {
            logger.error("Failed to add project: " + projectName + " with Exception --> " + e.getMessage());

            e.printStackTrace();

            return null;
        }
    }

    public Refset addWCIRefset(User u, String name, String refsetId, String moduleId, Date versionDate, String narrative, Project project) {

        try (TerminologyService service = new TerminologyService()) {

            Edition e = service.get(project.getEditionId(), Edition.class);

            if (!utilities.isDeveloperEdition(e.getShortName())) {
                throw new Exception("Cannot modify the workflow status of anything other than the developer org");
            }

            logger.info("Adding WCI Testing Org's single project: " + project);

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

            initializeService(service);

            // Sets up completely different than normal addRefset routine
            final Object returned = RefsetService.createRefset(service, u, refsetParameters);

            if (returned instanceof String) {

                throw new Exception((String) returned);
            } else {

                final Refset refset = (Refset) returned;

                logger.info("Added new WCI Refset - " + refset.getId() + " (" + refset.getName() + ")");

                Refset updatedRefset = utilities.initializeWorkflowStatus(refset);

                logger.info(" and then updated the new WCI refset's Workflow Status");

                return updatedRefset;
            }

        } catch (Exception e) {
            logger.error("Failed to add WCI refset: " + name + " (" + versionDate + ") with Exception --> " + e.getMessage());

            e.printStackTrace();

            return null;
        }

    }

    public Team addTeam(String teamName, String teamDescription, Organization organization, Set<String> roles, Set<String> memberIds) {

        try (final TerminologyService service = new TerminologyService()) {

            initializeService(service);

            Team team = new Team();
            team.setName(teamName);
            team.setDescription(teamDescription);
            team.setOrganization(organization);
            team.setPrimaryContactEmail("support-rt2@westcoastinformatics.com");
            team.setRoles(roles);
            team.setMembers(memberIds);

            // Persist
            final Team t = service.add(team);

            logger.info("Adding new Team: " + t.getId() + " (" + t.getName() + ") ");

            return t;
        } catch (Exception e) {
            logger.error("Failed to add team: " + teamName + " with Exception --> " + e.getMessage());

            e.printStackTrace();

            return null;
        }

    }

    public User addUser(String name, String userName, String email, Set<String> roles) {

        try (final TerminologyService service = new TerminologyService()) {

            initializeService(service);

            final User user = new User();

            user.setName(name);
            user.setUserName(userName);
            user.setActive(true);
            user.setEmail(email);
            user.setRoles(roles);

            // Persist
            final User u = service.add(user);

            logger.info("Adding new User: " + u.getId() + " (" + u.getName() + ") ");

            return u;
        } catch (Exception e) {
            logger.error("Failed to add user: " + userName + " with Exception --> " + e.getMessage());

            e.printStackTrace();

            return null;
        }

    }

    public Edition updateEditionStatus(String shortName, boolean isActive) {
        List<Edition> matchingEditions = null;

        try (final TerminologyService service = new TerminologyService()) {

            matchingEditions = service.getAll(Edition.class).stream().filter(e -> e.getShortName().equals(shortName)).collect(Collectors.toList());
            utilities.validateMatches(matchingEditions, shortName);

            final Edition edition = matchingEditions.iterator().next();
            edition.setActive(isActive);

            Edition updatedEdition = updateEdition(edition);

            logger.info("Updated edition: " + updatedEdition.getId() + " to " + isActive + "  (" + updatedEdition.getName() + ") ");

            return updatedEdition;
        } catch (Exception e) {
            logger.error("Failed to update status of edition: " + shortName + " to " + isActive);

            return null;
        }

    }

    public Organization updateOrganizationStatus(String organizationName, boolean isActive) {
        try (final TerminologyService service = new TerminologyService()) {

            initializeService(service);

            final List<Organization> allOrganizations = service.getAll(Organization.class);

            List<Organization> matchingOrganizations = allOrganizations.stream().filter(o -> o.getName().equals(organizationName)).collect(Collectors.toList());
            utilities.validateMatches(matchingOrganizations, organizationName);

            final Organization org = matchingOrganizations.iterator().next();
            org.setActive(isActive);

            // Persist
            final Organization o = service.update(org);

            logger.info("Inactivated organziation: " + o.getId() + " (" + o.getName() + ") ");

            return o;
        } catch (Exception e) {
            logger.error("Failed to update status of organziation: " + organizationName + " to " + isActive);

            e.printStackTrace();

            // TODO: Determine how to handle Updates
            return null;
        }
    }

    public Edition updateEdition(Edition dbEdition) {
        try (TerminologyService service = new TerminologyService()) {

            initializeService(service);

            // TODO: Logging should be done by callers as know what changed
            return service.update(dbEdition);

        } catch (Exception e) {
            logger.error("Failed to update edition: " + dbEdition.getName() + " with Exception --> " + e.getMessage());

            e.printStackTrace();

            // TODO: Determine how to handle Updates
            return null;
        }
    }

    public Refset updateRefset(Refset refset) {
        try (final TerminologyService service = new TerminologyService()) {

            initializeService(service);

            // TODO: Logging should be done by callers as know what changed
            return service.update(refset);
        } catch (Exception e) {
            logger.error("Failed to update refset: " + refset.getName() + " (" + refset.getVersionDate() + ") with Exception --> " + e.getMessage());

            e.printStackTrace();

            // TODO: Determine how to handle Updates
            return null;
        }
    }

    public Set<Refset> updateMultipleRefsets(Set<Refset> refsets) {

        Set<String> refsetDbIds = new HashSet<>();

        try (final TerminologyService service = new TerminologyService()) {

            initializeService(service);

            Set<Refset> updatedRefsets = new HashSet<>();

            // TODO: Logging should be done by callers as know what changed
            for (Refset refset : refsets) {
                refsetDbIds.add(refset.getId());
            }

            // Adding refsets identified on snowstorm
            for (Refset refset : refsets) {

                Refset updatedRefset = service.update(refset);

                updatedRefsets.add(updatedRefset);
            }

            return updatedRefsets;
        } catch (Exception e) {
            logger.error("Failed to update refest database ids: " + refsetDbIds + " with Exception --> " + e.getMessage());

            e.printStackTrace();

            // TODO: Determine how to handle Updates
            return null;
        }

    }

    public Refset updateRefsetVersionStatus(Refset refset, boolean isActive) {
        try (final TerminologyService service = new TerminologyService()) {

            initializeService(service);

            refset.setActive(isActive);

            // Persist
            final Refset updatedRefset = service.update(refset);

            logger.info("Update status of refset: " + refset.getName() + " (" + refset.getVersionDate() + ") to " + isActive);

            return updatedRefset;
        } catch (Exception e) {
            logger.error("Failed to update status of refset: " + refset.getName() + " (" + refset.getVersionDate() + ") to " + isActive);

            e.printStackTrace();

            // TODO: Determine how to handle Updates
            return null;
        }

    }

    public Refset updateRefsetVersionStatus(String refsetId, long versionDate, boolean isActive) {

        try (final TerminologyService service = new TerminologyService()) {

            List<Refset> allRefsets = service.getAll(Refset.class);
            List<Refset> matchingRefsets = allRefsets.stream().filter(r -> r.getRefsetId().equals(refsetId) && r.getVersionDate().equals(versionDate)).collect(Collectors.toList());
            utilities.validateMatches(matchingRefsets, refsetId + " / " + versionDate);

            Refset matchingRefset = matchingRefsets.iterator().next();
            return updateRefsetVersionStatus(matchingRefset, isActive);
        } catch (Exception e) {
            logger.error("Failed to update status of refset: " + refsetId + " (" + versionDate + ") to " + isActive);

            e.printStackTrace();

            // TODO: Determine how to handle Updates
            return null;
        }
    }

    public Set<DefinitionClause> addDefinitionClauses(String rttId) {

        Set<DefinitionClause> refsetClauses = new HashSet<>();

        try (final TerminologyService service = new TerminologyService()) {

            initializeService(service);

            for (String clauseJson : utilities.getPropertyReader().getRttRefsetToClausesMap().get(rttId)) {

                final DefinitionClause clause = ModelUtility.fromJson(clauseJson, DefinitionClause.class);

                SyncPersistenceMetadata metadata = utilities.getPropertyReader().getMetadataMap().get("refset-" + rttId);

                clause.setModified(metadata.getModified());
                clause.setCreated(metadata.getModified());
                clause.setModifiedBy(metadata.getModifiedBy());

                logger.info("Adding new DefinitionClause: " + clause.getId() + " (" + clause + ") ");

                DefinitionClause persistedClause = service.add(clause);

                refsetClauses.add(persistedClause);
            }
        } catch (Exception e) {
            logger.error("Failed to read refset clauses from RTT for  rttId: " + rttId + " with Exception --> " + e.getMessage());

            e.printStackTrace();

            return null;
        }

        return refsetClauses;
    }

    public Organization updateOrganization(Organization organization) {
        try (TerminologyService service = new TerminologyService()) {

            initializeService(service);

            // TODO: Logging should be done by callers as know what changed
            return service.update(organization);

        } catch (Exception e) {
            logger.error("Failed to update organization: " + organization.getName() + " with Exception --> " + e.getMessage());

            e.printStackTrace();

            // TODO: Determine how to handle Updates
            return null;
        }
    }

    public Project updateProject(Project project) {
        try (TerminologyService service = new TerminologyService()) {

            initializeService(service);

            // TODO: Logging should be done by callers as know what changed
            return service.update(project);

        } catch (Exception e) {
            logger.error("Failed to update project: " + project.getName() + " with Exception --> " + e.getMessage());

            e.printStackTrace();

            // TODO: Determine how to handle Updates
            return null;
        }
    }

    public DefinitionClause addDefinitionClause(DefinitionClause clause) {
        try (final TerminologyService service = new TerminologyService()) {

            initializeService(service);

            // Persist
            final DefinitionClause addedClause = service.add(clause);

            logger.info("Adding new DefinitionClause: " + addedClause.getId() + " (" + addedClause.getValue() + " / with isNegated: " + addedClause.getNegated() + ") ");

            return addedClause;
        } catch (Exception e) {
            logger.error("Failed to add DefinitionClause: " + clause.getValue() + " / with isNegated: " + clause.getNegated() + " with Exception --> " + e.getMessage());

            e.printStackTrace();

            return null;
        }
    }

    public Set<Refset> updateRefsetIdsStatus(String refsetId, boolean isActive) {

        Set<Refset> updatedRefsetVersions = new HashSet<>();
        ;

        try (final TerminologyService service = new TerminologyService()) {

            List<Refset> matchingRefsetVersions = service.getAll(Refset.class).stream().filter(r -> r.getRefsetId().equals(refsetId)).collect(Collectors.toList());

            for (Refset refsetVersion : matchingRefsetVersions) {
                refsetVersion.setActive(isActive);

                Refset updatedRefsetVersion = updateRefset(refsetVersion);

                logger.info("Updated refset version status: " + updatedRefsetVersion.getId() + " to " + isActive + "  (" + updatedRefsetVersion.getRefsetId() + " / "
                        + updatedRefsetVersion.getVersionDate() + ") ");

                updatedRefsetVersions.add(updatedRefsetVersion);
            }
            return updatedRefsetVersions;
        } catch (Exception e) {
            logger.error("Failed to update status of all resfsets with refsetId: " + refsetId + " to " + isActive);

            return null;
        }

    }

}
