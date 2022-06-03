package org.ihtsdo.refsetservice.migration;

import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.ihtsdo.refsetservice.model.DiscussionPost;
import org.ihtsdo.refsetservice.model.DiscussionThread;
import org.ihtsdo.refsetservice.model.DiscussionType;
import org.ihtsdo.refsetservice.model.Organization;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.model.Team;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.service.SecurityService;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MigrationDataInitializer {

    private final Logger logger = LoggerFactory.getLogger(MigrationDataInitializer.class);

    private MigrationUtilities utilities = new MigrationUtilities();

    private static final String WCI_TESTING_REFSET_CONCEPT_ID = "92535302004";

    public Set<Project> createUATProjects(Organization wciOrganization, Map<String, Organization> organizationsAdded, MigrationMetadata defaultMeta) throws Exception {
        logger.debug("222c1 creating UAT projects from orgsAdded w/count: " + organizationsAdded.size());

        logger.info(" Create a dedicated UAT Training Project for each organization");
        Set<Project> uatProjects = new HashSet<>();

        for (String orgName : organizationsAdded.keySet()) {

            Organization org = organizationsAdded.get(orgName);

            if (wciOrganization != null && wciOrganization.equals(org)) {

                continue;
            }

            Project uatProject = utilities.addProject(org, org.getName() + " dedicated UAT Training Project",
                "Project is dedicated to UAT Training. Any work done here will not be available for production usages. All training users will have the author role and reviewer role in this project",
                defaultMeta);
            logger.debug("222c2 uatProj Created");

            uatProjects.add(uatProject);
        }

        return uatProjects;
    }

    public Project createWCITestingContent(TerminologyService service, Organization wciOrganization, MigrationMetadata defaultMeta) throws Exception {
        logger.debug("222d1 creating testing project");

        Project wciProject = utilities.addProject(wciOrganization, "WCI Testing Project", "The single project for all WCI testing refsets", defaultMeta);

        Refset wciTestingRefset = new Refset();

        // TODO: Change this to have actual release date created/new Refset
        wciTestingRefset.setVersionDate(new Date());
        wciTestingRefset.setRefsetId(WCI_TESTING_REFSET_CONCEPT_ID);
        wciTestingRefset.setModuleId(MigrationUtilities.MODULE_ANCESTOR_CONCEPT_SCTID);
        wciTestingRefset.setVersionStatus("PUBLISHED");
        wciTestingRefset.setWorkflowStatus("PUBLISHED");
        wciTestingRefset.setActive(true);
        wciTestingRefset.setType("EXTENSIONAL");
        wciTestingRefset.setProject(wciProject);
        wciTestingRefset.setName("Base WCI Refset");

        service.add(wciTestingRefset);
        
        return wciProject;
    }

    public void createWCITeams(TerminologyService service, Set<Project> uatProjects) throws Exception {
        logger.debug("222e1 creating WCI Teams");

        // Create wci-developer teams for each extensions's UAT Training project (for DEV only)
        for (Project uatProject : uatProjects) {

            Organization org = uatProject.getOrganization();

            logger.debug("Trying with: " + org.getName());
            logger.debug("then with: " + org.getEdition().getName());

            Map<String, Set<String>> organizationTeamInfo = null;
            organizationTeamInfo = utilities.getPropertyReader().getTeamCreation().get(org.getName());

            if (organizationTeamInfo == null) {

                organizationTeamInfo = utilities.getPropertyReader().getTeamCreation().get(org.getName());

                if (organizationTeamInfo == null) {

                    logger.debug("error 444 - with " + utilities.getPropertyReader().getTeamCreation().get(org.getName()) + " -- and -- "
                        + utilities.getPropertyReader().getTeamCreation().get(org.getEdition().getName()));
                    continue;
                }

            }

            Set<String> projectTeams = new HashSet<>();

            for (String teamToCreate : organizationTeamInfo.keySet()) {

                Team team = new Team(teamToCreate);
                team.setDescription("Providing support for all UAT Extension Training Projects");
                team.setOrganization(org);
                team.setPrimaryContactEmail("support-rt2@westcoastinformatics.com");
                team.setRoles(organizationTeamInfo.get(teamToCreate));
                team.setMembers(utilities.getPropertyReader().getTeamMembership().get(teamToCreate));

                team = service.add(team);
                projectTeams.add(team.getName());
            }

            uatProject.setTeams(projectTeams);
        }

    }

    public void createTestingFeedback(TerminologyService service, Organization wciOrganization, Project wciProject) throws Exception {
        logger.debug("222e1 creating Testing Feedback");

        // create new refset with name = FeedbackTestingVersion1
        Refset refset = utilities.addRefset("WCI Testing Feeedback Refset 1", "999999991", wciOrganization.getEdition().getTopLevelModule(), new Date(), Refset.EXTENSIONAL, "");
        refset.setProject(wciProject);
        refset = service.update(refset);
        
        // add feedback
        User u = new User();
        u.setName("FeedbackTesting #1");
        u.setUserName("feedbackTester1");
        u.setActive(true);
        u.setEmail("111@westcoastinformatics.com");
        final User userInitiator = service.add(u);

        u = new User();
        u.setName("FeedbackTesting #2");
        u.setUserName("feedbackTester2");
        u.setActive(true);
        u.setEmail("222@westcoastinformatics.com");
        final User userResponder = service.add(u);

        DiscussionThread thread = new DiscussionThread();
        thread.setSubject("Testing discussion thread on refset");
        thread.setType(DiscussionType.REFSET.toString());
        thread.setRefsetInternalId(refset.getId());
        thread.setStatus("OPEN");
        thread.setVisibility("VISIBLE");
        thread.setPrivateThread(false);
        thread = service.add(thread);

        service.setModifiedBy(SecurityService.getUserFromSession().getUserName());
        service.setModifiedFlag(true);
        service.setTransactionPerOperation(false);
        service.beginTransaction();

        DiscussionPost post = new DiscussionPost();
        post.setUser(userInitiator);
        post.setMessage("Topic Header");
        post.setVisibility("VISIBLE");
        post.setPrivatePost(false);
        post = service.add(post);
        thread.getPosts().add(post);

        post = new DiscussionPost();
        post.setUser(userResponder);
        post.setMessage("Comment #1");
        post.setVisibility("VISIBLE");
        post.setPrivatePost(false);
        post = service.add(post);
        thread.getPosts().add(post);

        post = new DiscussionPost();
        post.setUser(userInitiator);
        post.setMessage("Comment #2");
        post.setVisibility("VISIBLE");
        post.setPrivatePost(false);
        post = service.add(post);
        thread.getPosts().add(post);

        // Finalize transaction
        service.update(thread);
        service.commit();
    }
}
