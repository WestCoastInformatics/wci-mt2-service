package org.ihtsdo.refsetservice.migration;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.ihtsdo.refsetservice.model.DefinitionClause;
import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.HasModified;
import org.ihtsdo.refsetservice.model.Organization;
import org.ihtsdo.refsetservice.model.PfsParameter;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.QueryParameter;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.model.Team;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.util.CrowdGroupNameAlgorithm;
import org.ihtsdo.refsetservice.util.ModelUtility;
import org.ihtsdo.refsetservice.util.ResultList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MigrationUtilities {

    private final Logger logger = LoggerFactory.getLogger(MigrationUtilities.class);

    /** The sdf. */
    private final static SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

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
            final Project p = service.add(project);

            logger.debug("Adding new Project: " + project.getId() + " (" + project.getName() + ") " + project);

            return p;

        }

    }

    Organization addOrganziation(final String orgName, String orgDesc, final Edition edition, final MigrationMetadata meta) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            initializeService(service);

            final Organization org = new Organization();
            org.setName(orgName);
            org.setDescription(orgDesc);
            org.setEdition(edition);

            // Persist
            final Organization o = service.add(org);

            logger.debug("Adding new Organziation: " + o.getId() + " (" + o.getName() + ") " + org);

            return o;
        }

    }

    public Refset addRefset(String name, String refsetId, String moduleId, Date versionDate, String type, String narrative, Project project) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            initializeService(service);

            final Refset refset = new Refset();

            refset.setName(name);
            refset.setRefsetId(refsetId);
            refset.setModuleId(moduleId);
            refset.setVersionStatus("PUBLISHED");
            refset.setWorkflowStatus("PUBLISHED");
            refset.setActive(true);
            refset.setVersionDate(versionDate);
            refset.setType(type);
            refset.setNarrative(narrative);
            refset.setProject(project);
            refset.setLatestPublishedVersion(true);

            // Persist
            final Refset r = service.add(refset);

            logger.debug("Adding new Refset: " + r.getId() + " (" + r.getName() + ") " + r);

            return r;
        }

    }

    public User addUser(String name, String userName, String email, Set<String> roles) throws Exception {

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

            logger.debug("Adding new User: " + u.getId() + " (" + u.getName() + ") " + u);

            return u;
        }

    }

    public User getUser(String name, String userName, String email, Set<String> roles) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            final PfsParameter pfs = new PfsParameter();
            final QueryParameter query = new QueryParameter();
            query.setQuery("name:" + name + " AND active:true");
            logger.debug("  user search query: " + query);

            ResultList<User> results = service.find(query, pfs, User.class, null);

            if (results.getItems() != null && results.getItems().size() == 1) {

                // User already exists
                return results.getItems().iterator().next();
            } else {

                List<User> results2 = service.getAll(User.class);

                // User already exist, but found otherwise
                for (User existingUser : results2) {

                    if (existingUser.getName().equals(name) && existingUser.getUserName().equals(userName) && existingUser.getEmail().equals(email) && existingUser.getRoles().equals(roles)) {

                        return existingUser;
                    }

                }

                // Need to create user
                return addUser(name, userName, email, roles);
            }

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

            // Persist
            final Team t = service.add(team);

            logger.debug("Adding new Team: " + t.getId() + " (" + t.getName() + ") " + t);

            return t;
        }

    }

    Set<DefinitionClause> getRefsetClauses(String rttId) throws Exception {

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

    public SimpleDateFormat getSdf() {

        return sdf;
    }

}
