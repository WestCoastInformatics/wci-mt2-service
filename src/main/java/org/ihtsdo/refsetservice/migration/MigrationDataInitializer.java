package org.ihtsdo.refsetservice.migration;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
import org.ihtsdo.refsetservice.terminologyservice.TeamService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MigrationDataInitializer {

    private final Logger logger = LoggerFactory.getLogger(MigrationDataInitializer.class);

    private MigrationUtilities utilities = new MigrationUtilities();

    private static User migrationUser = null;

    private static User feedbackInitiatiorUser = null;

    private static User userResponderUser = null;

    private static User wciAdmin = null;

    private static User superUser = null;

    private static final Set<String> allRoles = new HashSet<>();

    private static final String WCI_TESTING_REFSET_CONCEPT_ID = "92535302004";

    private static final String WCI_TESTING_REFSET_NAME = "Default Single WCI Testing Refset";

    private static final String SUPER_USER_NAME = "refset-dev";

    private static final Set<User> adminUsers = new HashSet<>();

    static private Organization testingOrganization = null;

    static private Project testingProject = null;

    private static final String FEEDBACK_REFSET_NAME_BASE = "WCI Testing Feedback Refset ";

    private static final String FEEDBACK_REFSET_ID_BASE = "9999999";

    private static final String WCI_TESTING_PROJECT_NAME = "WCI Testing Project";

    private static final String WCI_TESTING_PROJECT_DESCRIPTION = "The single project for all WCI testing refsets";

    public MigrationDataInitializer() {

        // Grab wci users or create during first migration. Two types:
        // a) 5 WCI common users to be added to all orgs (1-per role and a super-user)
        // b) 2 WCI users specifically for generating a new feedback refset for testing
        try {

            wciAdmin = utilities.getUser("rt2-dev-admin", "rt2-dev-admin", "rt2-dev-admin@westcoastinformatics.com", new HashSet<String>(Arrays.asList(User.ROLE_ADMIN)));
            superUser = utilities.getUser(SUPER_USER_NAME, SUPER_USER_NAME, "refset-dev@westcoastinformatics.com", allRoles);

            adminUsers.add(wciAdmin);
            adminUsers.add(superUser);

            // For Feedback Refset
            feedbackInitiatiorUser = utilities.getUser("feedbackInitiator", "feedbackInitiator", "feedbackInitiator@westcoastinformatics.com", new HashSet<String>(Arrays.asList(User.ROLE_AUTHOR)));
            userResponderUser = utilities.getUser("feedbackResponder", "feedbackResponder", "feedbackResponder@westcoastinformatics.com", new HashSet<String>(Arrays.asList(User.ROLE_AUTHOR)));

        } catch (Exception e) {

            e.printStackTrace();
        }

    }

    public void initialize(Organization wciOrganization, Map<String, Organization> organizationsAdded, Map<String, Project> defaultOrganizationProjects, MigrationMetadata defaultMeta)
        throws Exception {

        // Create a dedicated UAT Training Project for each organization
        createUATProjects(wciOrganization, organizationsAdded, defaultMeta);

        // wciOrg only exists in DEV & UAT, so is a useful way to determine environments
        if (wciOrganization != null) {

            // Create wci-project (for DEV only)
            createWCITestingContent(wciOrganization, defaultMeta);

            // Create wci-feedback-testing refset(for DEV only)
            createTestingFeedback(wciOrganization);

            // Create a single Admin team per Org
            createAdminOrganizationTeams(organizationsAdded);

        }

    }

    private void createAdminOrganizationTeams(Map<String, Organization> organizationsAdded) throws Exception {

        try (TerminologyService service = new TerminologyService()) {

            initializeService(service);

            for (String orgName : organizationsAdded.keySet()) {

                final Organization organization = organizationsAdded.get(orgName);

                Set<String> memberIds = new HashSet<>();
                memberIds.addAll(adminUsers.stream().map(User::getId).collect(Collectors.toList()));

                utilities.addTeam(TeamService.generateOrgTeamName(organization), TeamService.getOrgTeamDescription(organization), organization, new HashSet<String>(Arrays.asList(User.ROLE_ADMIN)),
                    memberIds);

                // Finally, add the users to the organizaiton
                organization.getMembers().addAll(adminUsers);
                Organization updatedOrganization = service.update(organization);

                printAllValues(updatedOrganization);
            }

        }

    }

    public void printResults() throws Exception {

        try (TerminologyService service = new TerminologyService()) {

            // Print out orgs & projects
            final List<Organization> organizations = service.getAll(Organization.class);
            final List<Project> projects = service.getAll(Project.class);

            for (Organization organization : organizations) {

                logger.info("Have Out with org: " + organization.getId() + " (" + organization.getName() + ") with members: ");
                organization.getMembers().stream().forEach(member -> logger.info("   Member: " + member.getName()));
            }

            for (Project project : projects) {

                logger.info("Out with project: " + project.getId() + " (" + project.getName() + ") with teams: ");
                project.getTeams().stream().forEach(team -> logger.info("   Team: " + team));
            }

        }

    }

    private Map<String, Project> createUATProjects(Organization wciOrganization, Map<String, Organization> organizationsAdded, MigrationMetadata defaultMeta) throws Exception {

        logger.info(" Create a dedicated UAT Training Project for each organization");
        Map<String, Project> uatProjects = new HashMap<>();

        for (String orgName : organizationsAdded.keySet()) {

            Organization org = organizationsAdded.get(orgName);

            if (wciOrganization != null && wciOrganization.equals(org)) {

                continue;
            }

            Project uatProject = utilities.addProject(org, org.getName() + " UAT Training Project",
                "Project is dedicated to UAT Training. Any work done here will not be available for production usages. All training users will have the author role and reviewer role in this project",
                defaultMeta);

            uatProjects.put(orgName, uatProject);
        }

        return uatProjects;
    }

    private void createWCITestingContent(Organization wciOrganization, MigrationMetadata defaultMeta) throws Exception {

        try (TerminologyService service = new TerminologyService()) {

            initializeService(service);

            logger.info("Adding WCI Testing Org's single project");

            testingProject = utilities.addProject(wciOrganization, WCI_TESTING_PROJECT_NAME, WCI_TESTING_PROJECT_DESCRIPTION, defaultMeta);

            utilities.addWCIRefset(getMigrationUser(), WCI_TESTING_REFSET_NAME, WCI_TESTING_REFSET_CONCEPT_ID, wciOrganization.getEdition().getTopLevelModule(),
                utilities.getSdf().parse("2021-07-31 07:00:00.000000"), Refset.EXTENSIONAL, "", testingProject);
        }

    }

    /*
     * Called when creating the first instance of testing-feedback refset
     */
    public Refset createTestingFeedback(Organization wciOrganization) throws Exception {

        logger.info(" Create Feedback for testing (for DEV only)");

        // create new refset with name = FeedbackTestingVersion1 with July 31 2022 version off International Edition
        Refset refset = utilities.addWCIRefset(getMigrationUser(), "WCI Testing Feedback Refset 1", "999999901", wciOrganization.getEdition().getTopLevelModule(),
            utilities.getSdf().parse("2021-07-31 07:00:00.000000"), Refset.EXTENSIONAL, "", testingProject);

        try (TerminologyService service = new TerminologyService()) {

            initializeService(service);

            // Create users and teams, then add to org/project
            Set<String> userRole = new HashSet<>();
            userRole.add(User.ROLE_AUTHOR);
            Set<String> memberIds = new HashSet<>();
            memberIds.add(feedbackInitiatiorUser.getId());
            memberIds.add(userResponderUser.getId());
            adminUsers.stream().forEach(user -> memberIds.add(user.getId()));

            final Team singleFeedbackTeam = utilities.addTeam("WCI Feedback Team", "WCI Feedback Testing/Demoing Team with all roles for all WCI members", wciOrganization, allRoles, memberIds);

            testingProject.getTeams().add(singleFeedbackTeam.getId());
            testingProject = service.update(testingProject);

            wciOrganization.getMembers().addAll(adminUsers);
            wciOrganization.getMembers().add(feedbackInitiatiorUser);
            wciOrganization.getMembers().add(userResponderUser);
            wciOrganization = service.update(wciOrganization);

            addFeedbackContent(refset);

            return refset;
        }

    }

    /*
     * Called when adding another instance of testing-feedback refset
     */
    public Refset createTestingFeedback() throws Exception {

        final Project wciProject = getTestingProject();
        final Organization wciOrganization = getTestingOrganization();

        try (TerminologyService service = new TerminologyService()) {

            initializeService(service);
            final List<Refset> projectRefsets = service.find("projectId:" + wciProject.getId() + " AND active:true", null, Refset.class, null).getItems();

            int latestVersion = 0;

            for (Refset projectRefset : projectRefsets) {

                if (projectRefset.getRefsetId().startsWith(FEEDBACK_REFSET_ID_BASE) && projectRefset.getName().startsWith(FEEDBACK_REFSET_NAME_BASE)) {

                    final int refsetVersion = Integer.parseInt(projectRefset.getName().substring(FEEDBACK_REFSET_NAME_BASE.length()).trim());

                    if (refsetVersion > latestVersion) {

                        latestVersion = refsetVersion;
                    }
                    // Iterate through the refsets, look at the refset name, and find the integer list after the default name.
                    // if keysize = 0, this is first one. So create with RefsetId: based on the FEEDBACK_REFSET_ID_BASE and iteration.
                    // else, if the refset integer is greater than the greatest one seen, make this the new refsetName & refsetId integer

                }

            }

            Refset newTestingRefset;

            if (latestVersion == 0) {

                newTestingRefset = utilities.addWCIRefset(getMigrationUser(), FEEDBACK_REFSET_NAME_BASE + "1", FEEDBACK_REFSET_ID_BASE + "01", wciOrganization.getEdition().getTopLevelModule(),
                    new Date(), Refset.EXTENSIONAL, "", wciProject);
            } else {

                latestVersion++;
                String tensValue = Integer.toString(latestVersion / 10);
                String onesValue = Integer.toString(latestVersion % 10);

                newTestingRefset = utilities.addWCIRefset(getMigrationUser(), FEEDBACK_REFSET_NAME_BASE + latestVersion, FEEDBACK_REFSET_ID_BASE + tensValue + onesValue,
                    wciOrganization.getEdition().getTopLevelModule(), new Date(), Refset.EXTENSIONAL, "", wciProject);
            }

            addFeedbackContent(newTestingRefset);

            return newTestingRefset;
        }

    }

    private void addFeedbackContent(Refset refset) throws Exception {

        try (TerminologyService service = new TerminologyService()) {

            initializeService(service);

            // add feedback
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
            post.setUser(feedbackInitiatiorUser);
            post.setMessage("Topic Header");
            post.setVisibility("VISIBLE");
            post.setPrivatePost(false);
            post = service.add(post);
            thread.getPosts().add(post);

            post = new DiscussionPost();
            post.setUser(userResponderUser);
            post.setMessage("Comment #1");
            post.setVisibility("VISIBLE");
            post.setPrivatePost(false);
            post = service.add(post);
            thread.getPosts().add(post);

            post = new DiscussionPost();
            post.setUser(feedbackInitiatiorUser);
            post.setMessage("Comment #2");
            post.setVisibility("VISIBLE");
            post.setPrivatePost(false);
            post = service.add(post);
            thread.getPosts().add(post);

            // Finalize transaction
            service.update(thread);
            service.commit();
            service.setTransactionPerOperation(true);
        }

    }

    private void printAllValues(Organization organization) throws Exception {

        try (TerminologyService service = new TerminologyService()) {

            final List<Project> orgProjects = service.find("organization.id:" + organization.getId(), null, Project.class, null).getItems();
            final List<Team> teams = service.getAll(Team.class);

            for (Project project : orgProjects) {

                for (String teamId : project.getTeams()) {

                    Team team = teams.stream().filter(t -> t.getId().equals(teamId)).findFirst().orElse(null);

                    if (team == null) {

                        throw new Exception("  Unable to locate team in project " + project.getName() + " for team: " + teamId);
                    }

                }

            }

        }

    }

    private Project getTestingProject() throws Exception {

        if (testingProject == null) {

            try (TerminologyService service = new TerminologyService()) {

                List<Project> projects = service.getAll(Project.class);

                for (Project p : projects) {

                    if (p.getName().equals(WCI_TESTING_PROJECT_NAME)) {

                        testingProject = p;
                    }

                }

            }

            if (testingProject == null) {

                throw new Exception("Testing Project doesn't exist. Shouldn't be running this on a non-Production instanace of RT2");
            }

        }

        return testingProject;
    }

    private Organization getTestingOrganization() throws Exception {

        if (testingOrganization == null) {

            try (TerminologyService service = new TerminologyService()) {

                List<Organization> orgs = service.getAll(Organization.class);

                for (Organization o : orgs) {

                    if (o.getName().toLowerCase().contains("wci")) {

                        testingOrganization = o;
                    }

                }

            }

            if (testingOrganization == null) {

                throw new Exception("Testing Organization doesn't exist. Shouldn't be running this on a non-Production instanace of RT2");
            }

        }

        return testingOrganization;
    }

    private void initializeService(TerminologyService service) {

        service.setModifiedBy("Migration");
        service.setModifiedFlag(true);
    }

    static User getMigrationUser() {

        if (migrationUser == null) {

            migrationUser = new User();
            migrationUser.setName("Migrator");
            migrationUser.setUserName("Migrator");
            migrationUser.setActive(true);
            migrationUser.setEmail("test@wci.com");

            Set<String> roles = new HashSet<>();
            roles.add("all-all-all");
            migrationUser.setRoles(roles);
        }

        return migrationUser;
    }

    public static Set<User> getAdminUsers() {

        return adminUsers;
    }

    public static List<String> getAdminUserIds() {

        return adminUsers.stream().map(User::getId).collect(Collectors.toList());
    }

}