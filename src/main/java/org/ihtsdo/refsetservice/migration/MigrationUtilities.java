package org.ihtsdo.refsetservice.migration;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import org.ihtsdo.refsetservice.model.DefinitionClause;
import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.HasModified;
import org.ihtsdo.refsetservice.model.Organization;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.model.Team;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.util.CrowdGroupNameAlgorithm;
import org.ihtsdo.refsetservice.util.ModelUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MigrationUtilities {

    private final Logger logger = LoggerFactory.getLogger(MigrationUtilities.class);

    static final String MODULE_ANCESTOR_CONCEPT_SCTID = "900000000000443000";

    private static final MigrationPropertyFileReader propertyReader = new MigrationPropertyFileReader();

    Project addProject(Organization org, String projectName, String projectDescription, MigrationMetadata meta) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            initializeService(service);

            final Project project = new Project();
            project.setName(projectName);
            project.setDescription(projectDescription);
            project.setOrganization(org);
            project.setPrivateProject(false);
            project.setCrowdProjectId(CrowdGroupNameAlgorithm.getProjectString(projectName));

            // Persist
            Project p = service.add(project);

            logger.debug("Adding new Project: " + project.getId() + " (" + project.getName() + ")");

            return p;

        }

    }

    Organization addOrganziation(final String orgName, String orgDesc, final Edition edition, final MigrationMetadata meta) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            initializeService(service);

            Organization org = new Organization();
            org.setName(orgName);
            org.setDescription(orgDesc);
            org.setEdition(edition);

            org = service.add(org);
            logger.debug("Adding new Organziation: " + org.getId() + " (" + org.getName() + ")");

            return org;
        }

    }

    public Refset addRefset(String name, String refsetId, String moduleId, Date versionDate, String type, String narrative) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            initializeService(service);

            Refset refset = new Refset();

            refset.setName(name);
            refset.setRefsetId(refsetId);
            refset.setModuleId(moduleId);
            refset.setVersionStatus("PUBLISHED");
            refset.setWorkflowStatus("PUBLISHED");
            refset.setActive(true);
            refset.setVersionDate(versionDate);
            refset.setType(type);
            refset.setNarrative(narrative);

            return service.add(refset);
        }

    }

    public User addUser(String name, String userName, String email, Set<String> roles) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            initializeService(service);

            User u = new User();

            u.setName(name);
            u.setUserName(userName);
            u.setActive(true);
            u.setEmail(email);
            u.setRoles(roles);

            return service.add(u);
        }

    }

    Team addTeam(String teamName, String teamDescription, Organization organization, Set<String> roles, Set<String> memberNames) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            initializeService(service);

            Team team = new Team();
            team.setName(teamName);
            team.setDescription(teamDescription);
            team.setOrganization(organization);
            team.setPrimaryContactEmail("support-rt2@westcoastinformatics.com");
            team.setRoles(roles);
            team.setMembers(memberNames);

            team = service.add(team);

            return team;
        }

    }

    Set<DefinitionClause> addClause(String rttId) throws Exception {

        Set<DefinitionClause> refsetClauses = new HashSet<>();
        MigrationPropertyFileReader propertyReader = new MigrationPropertyFileReader();

        try (final TerminologyService service = new TerminologyService()) {

            initializeService(service);

            for (String clauseJson : propertyReader.getRttRefsetToClausesMap().get(rttId)) {

                final DefinitionClause clause = ModelUtility.fromJson(clauseJson, DefinitionClause.class);

                setMetadata(clause, propertyReader.getMetadataMap().get("refset-" + rttId));
                DefinitionClause persistedClause = service.add(clause);
                refsetClauses.add(persistedClause);
            }

            return refsetClauses;
        }

    }

    void setMetadata(final HasModified object, final MigrationMetadata metadata) {

        object.setModified(metadata.getModified());
        object.setCreated(metadata.getModified());
        object.setModifiedBy(metadata.getModifiedBy());
    }

    private void initializeService(TerminologyService service) {

        service.setModifiedBy("Migration");
        service.setModifiedFlag(true);
    }

    MigrationPropertyFileReader getPropertyReader() {

        return propertyReader;
    }

}
