package org.ihtsdo.refsetservice.migration;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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

    private static User feedbackInitiatiorUser = null;

    private static User userResponderUser = null;

    private static User wciAuthor = null;

    private static User wciReviewer = null;

    private static User wciViewer = null;

    private static User wciAdmin = null;

    private static final String WCI_TESTING_REFSET_CONCEPT_ID = "92535302004";

    private static final Object ADMIN_ROLE = "Admin";

    private static final String REFSET_DEV_USER = "refset-dev";

    private static final String ORGANIZATION_TEAM_DESCRIPTION_BASE_NAME = "Dedicated to providing tooling support for all projects";

    private static final Map<String, User> userRoleMap = new HashMap<>();

    private static final Set<User> commonWciUsers = new HashSet<>();

    private static final Set<String> allRoles = new HashSet<>();

    private static User refsetDevUser = null;

    public MigrationDataInitializer() {

        // Create 5 WCI users (1-per role and a super-user)
        try {

            feedbackInitiatiorUser = utilities.addUser("FeedbackTester1", "FeedbackTester1", "testUser1@westcoastinformatics.com", new HashSet<String>(Arrays.asList(User.ROLE_AUTHOR)));
            userResponderUser = utilities.addUser("FeedbackTester2", "FeedbackTester2", "testUser2@westcoastinformatics.com", new HashSet<String>(Arrays.asList(User.ROLE_AUTHOR)));

            wciAuthor = utilities.addUser("rt2-dev-author", "rt2-dev-author", "rt2-dev-author@westcoastinformatics.com", new HashSet<String>(Arrays.asList(User.ROLE_AUTHOR)));
            userRoleMap.put(User.ROLE_AUTHOR, wciAuthor);

            wciReviewer = utilities.addUser("rt2-dev-reviewer", "rt2-dev-reviewer", "rt2-dev-reviewer@westcoastinformatics.com", new HashSet<String>(Arrays.asList(User.ROLE_REVIEWER)));
            userRoleMap.put(User.ROLE_REVIEWER, wciReviewer);

            wciAdmin = utilities.addUser("rt2-dev-admin", "rt2-dev-admin", "rt2-dev-admin@westcoastinformatics.com", new HashSet<String>(Arrays.asList(User.ROLE_ADMIN)));
            userRoleMap.put(User.ROLE_ADMIN, wciAdmin);

            wciViewer = utilities.addUser("rt2-dev-viewer", "rt2-dev-viewer", "rt2-dev-viewer@westcoastinformatics.com", new HashSet<String>(Arrays.asList(User.ROLE_VIEWER)));
            userRoleMap.put(User.ROLE_VIEWER, wciViewer);

            allRoles.addAll(userRoleMap.keySet());
            refsetDevUser = utilities.addUser(REFSET_DEV_USER, REFSET_DEV_USER, "refset-dev@westcoastinformatics.com", allRoles);

            commonWciUsers.add(refsetDevUser);
            commonWciUsers.addAll(userRoleMap.values());
        } catch (Exception e) {

            e.printStackTrace();
        }

    }

    public Map<String, Project> createUATProjects(Organization wciOrganization, Map<String, Organization> organizationsAdded, MigrationMetadata defaultMeta) throws Exception {

        logger.info(" Create a dedicated UAT Training Project for each organization");
        Map<String, Project> uatProjects = new HashMap<>();

        for (String orgName : organizationsAdded.keySet()) {

            Organization org = organizationsAdded.get(orgName);

            if (wciOrganization != null && wciOrganization.equals(org)) {

                continue;
            }

            Project uatProject = utilities.addProject(org, org.getName() + " dedicated UAT Training Project",
                "Project is dedicated to UAT Training. Any work done here will not be available for production usages. All training users will have the author role and reviewer role in this project",
                defaultMeta);

            uatProjects.put(orgName, uatProject);
        }

        return uatProjects;
    }

    public Project createWCITestingContent(TerminologyService service, Organization wciOrganization, MigrationMetadata defaultMeta) throws Exception {

        logger.info("Adding WCI Testing Org's single project");

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

    public void createWCISupport(TerminologyService service, Map<String, Project> uatProjects) throws Exception {

        logger.info(" Create wci-support users & teams and add appropriate wci user as well as refset-dev to each.");

        // 1) SET NAMES
        // 2) Create team with memberNames and appropriate roles
        // 3) Add team to project
        // 4 Add members to org
        // 5) Update org

        List<Organization> organizations = service.getAll(Organization.class);

        // Formalize very simply for now (nothing specific about org or role in team's description)

        for (Organization organization : organizations) {

            final String organizationTeamDescription = organization.getName() + " " + ORGANIZATION_TEAM_DESCRIPTION_BASE_NAME;

            logger.debug("111 with org: " + organization.getName());

            int counter = 0;

            Project project = uatProjects.get(organization.getName());

            if (project == null) {

                logger.debug("Org is " + organization.getName() + " and it should be WCI. No need to process it in support of UAT");
                continue;
            }

            logger.debug("222 with project: " + project.getName());

            String idCounter = ((++counter < 10) ? String.valueOf(counter) : "0" + String.valueOf(counter));

            Refset refset = utilities.addRefset("WCI " + organization.getEdition().getShortName() + " base refset for project " + project.getName(), "9999999" + idCounter,
                organization.getEdition().getTopLevelModule(), new Date(), Refset.EXTENSIONAL, "");
            refset.setProject(project);
            refset = service.update(refset);

            for (String role : userRoleMap.keySet()) {

                logger.debug("333 with role: " + role);

                User roleBasedUser = userRoleMap.get(role);

                Set<String> memberNames = new HashSet<>();
                memberNames.add(roleBasedUser.getName());
                memberNames.add(refsetDevUser.getName());
                memberNames.stream().forEach(name -> logger.debug("444 with member Names: " + name));

                final Team team =
                    utilities.addTeam(organization.getName() + " dev-support-" + role + " Team", organizationTeamDescription, organization, new HashSet<String>(Arrays.asList(role)), memberNames);
                logger.debug("555 with Team: " + team);

                project.getTeams().add(team.getName());

            }

            project = service.update(project);
            logger.debug("666 with project: " + project);

            Project p2 = service.get(project.getId(), Project.class);
            p2.getTeams().stream().forEach(team -> logger.debug("777 with team Names: " + team));

            organization.getMembers().addAll(commonWciUsers);
            organization = service.update(organization);

            printAllValues(service, organization);
        }

    }

    public void createTestingFeedback(TerminologyService service, Organization wciOrganization, Project wciProject) throws Exception {

        logger.info(" Create Feedback for testing (for DEV only)");

        // create new refset with name = FeedbackTestingVersion1
        Refset refset = utilities.addRefset("WCI Testing Feeedback Refset 1", "999999991", wciOrganization.getEdition().getTopLevelModule(), new Date(), Refset.EXTENSIONAL, "");
        refset.setProject(wciProject);
        refset = service.update(refset);

        // Create users and teams, then add to org/project
        Set<String> userRole = new HashSet<>();
        userRole.add(User.ROLE_AUTHOR);
        Set<String> memberNames = new HashSet<>();
        memberNames.add(feedbackInitiatiorUser.getName());
        memberNames.add(userResponderUser.getName());
        commonWciUsers.stream().forEach(user -> memberNames.add(user.getName()));

        final Team singleFeedbackTeam = utilities.addTeam("WCI Feedback Team", "WCI Feedback Testing/Demoing Team with all roles for all WCI members", wciOrganization, allRoles, memberNames);
        logger.debug("999 a: Single Feedback Team: " + singleFeedbackTeam);
        wciProject.getTeams().add(singleFeedbackTeam.getName());
        wciProject = service.update(wciProject);

        wciOrganization.getMembers().addAll(commonWciUsers);
        wciOrganization.getMembers().add(feedbackInitiatiorUser);
        wciOrganization.getMembers().add(userResponderUser);
        wciOrganization = service.update(wciOrganization);

        logger.debug("777a with team: " + singleFeedbackTeam.getName() + " with members: " + singleFeedbackTeam.getMembers() + " with roles: " + singleFeedbackTeam.getRoles() + " with org: "
            + singleFeedbackTeam.getOrganization().getName());

        logger.debug("888a with project: " + wciProject.getName() + " with members: " + wciProject.getMemberList() + " with roles: " + wciProject.getRoles() + " with teams: " + wciProject.getTeams()
            + " in org: " + wciProject.getOrganization().getName());

        logger.debug("999a with org: " + wciOrganization.getName() + " with members: " + wciOrganization.getMembers());

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

    private void printAllValues(TerminologyService service, Organization organization) throws Exception {

        logger.debug("666");
        final List<Project> orgProjects = service.find("organization.id:" + organization.getId(), null, Project.class, null).getItems();
        final List<Team> teams = service.getAll(Team.class);

        for (Project project : orgProjects) {

            for (String teamName : project.getTeams()) {

                Team team = teams.stream().filter(t -> t.getName().equals(teamName)).findFirst().orElse(null);

                if (team == null) {

                    throw new Exception("777b - Unable to locate team in project " + project.getName() + " for team: " + teamName);
                }

                logger.debug("777b with team: " + team.getName() + " with members: " + team.getMembers() + " with roles: " + team.getRoles() + " with org: " + team.getOrganization().getName());
            }

            logger.debug("888b with project: " + project.getName() + " with members: " + project.getMemberList() + " with roles: " + project.getRoles() + " with teams: " + project.getTeams()
                + " in org: " + project.getOrganization().getName());
        }

        logger.debug("999b with org: " + organization.getName() + " with members: " + organization.getMembers());
    }

    public void addDebugAdminUser(TerminologyService service) throws Exception {

        logger.info(" Add the refset-dev user to all projects' Admin team (Creating team if not already existing)");

        final List<Team> teams = service.getAll(Team.class);

        // get refsetDev users

        Map<Organization, Set<Team>> orgTeams = new HashMap<>();

        // identify all teams with an admin role and associate them with each org
        for (Team team : teams) {

            if (team.getRoles().contains(ADMIN_ROLE)) {

                if (!orgTeams.keySet().contains(team.getOrganization())) {

                    orgTeams.put(team.getOrganization(), new HashSet<Team>());
                }

                orgTeams.get(team.getOrganization()).add(team);
            }

        }

        final List<Organization> organizations = service.getAll(Organization.class);

        // identify all orgs without admin teams `
        for (Organization organization : organizations) {

            if (!orgTeams.containsKey(organization)) {

                /* Create Admin Team TODO */
                // Team t = new Admin Team
                // orgTeams.add(org, t)
            }

        }

        for (Organization organization : orgTeams.keySet()) {

            Set<Team> adminTeams = orgTeams.get(organization);

            for (Team team : adminTeams) {

                team.getMembers().add(refsetDevUser.getUserName());
                service.update(team);
            }

        }

    }
}

/*
 * for (Organization organization : organizations) {
 * 
 * logger.debug("111 with org: " + organization.getName());
 * 
 * Set<Team> orgTeams = new HashSet<>();
 * 
 * for (String role : userRoleMap.keySet()) {
 * 
 * User roleUser = userRoleMap.get(role);
 * 
 * logger.debug("222 with role: " + role);
 * 
 * organization.getMembers().add(refsetDevUser); organization.getMembers().add(roleUser);
 * 
 * logger.debug("333 with members: " + organization.getMembers());
 * 
 * Set<String> memberNames = new HashSet<>(); memberNames.add(roleUser.getName()); memberNames.add(refsetDevUser.getName());
 * 
 * memberNames.stream().forEach(name -> logger.debug("444 with member Names: " + name));
 * 
 * final Team team = utilities.addTeam("dev-support-" + role + "-" + organization.getName(), organizationTeamDescription, organization, new
 * HashSet<String>(Arrays.asList(role)), memberNames);
 * 
 * logger.debug("555 - 999 b: Team: " + team);
 * 
 * orgTeams.add(team); }
 * 
 * logger.debug("666"); final List<Project> orgProjects = service.find("organization.id:" + organization.getId(), null, Project.class, null).getItems();
 * 
 * for (Project project : orgProjects) {
 * 
 * for (Team team : orgTeams) {
 * 
 * logger.debug("777b with team: " + team.getName() + " with members: " + team.getMembers() + " with roles: " + team.getRoles() + " with org: " +
 * team.getOrganization().getName());
 * 
 * project.getTeams().add(team.getName()); }
 * 
 * project = service.update(project); logger.debug("888b with project: " + project.getName() + " with members: " + project.getMemberList() + " with roles: " +
 * project.getRoles() + " with teams: " + project.getTeams() + " in org: " + project.getOrganization().getName()); }
 * 
 * logger.debug("999b with org: " + organization.getName() + " with members: " + organization.getMembers());
 * 
 * service.update(organization); }
 */

/*
 * 
 * for (Organization org : organizations) {
 * 
 * org.getMembers().stream().forEach(member -> logger.debug("222b with org: " + org.getName() + " and member: " + member.getName()));
 * 
 * // Add users to each organization org.getMembers().addAll(allWciUsers); service.update(org);
 * 
 * org.getMembers().stream().forEach(member -> logger.debug("222c with org: " + org.getName() + " and member: " + member.getName()));
 * 
 * }
 * 
 * organizations = service.getAll(Organization.class);
 * 
 * for (Organization org : organizations) {
 * 
 * org.getMembers().stream().forEach(member -> logger.debug("222d with org: " + org.getName() + " and member: " + member.getName()));
 * 
 * }
 * 
 */

/* Finished initializations, now begin */
/*
 * organizations.stream().forEach(organization -> {
 * 
 * 
 * logger.debug("111a with org: " + organization);
 * 
 * // Add users to each organization organization.getMembers().addAll(allWciUsers);
 * 
 * try {
 * 
 * service.update(organization); } catch (Exception e1) {
 * 
 * // TODO Auto-generated catch block e1.printStackTrace(); }
 * 
 * logger.debug("JE: Adding these users to " + organization.getName() + ": " + allWciUsers);
 * 
 * // Create a team with a dedicated role per organization userRoleMap.keySet().stream().forEach(role -> {
 * 
 * try {
 * 
 * logger.debug("111b with role: " + role);
 * 
 * // Create users and teams, then add to org/project Set<String> memberNames = new HashSet<>(); memberNames.add(userRoleMap.get(role).getName());
 * memberNames.add(refsetDevUser.getName());
 * 
 * final Team team = utilities.addTeam("dev-support-" + role + "-" + organization.getName(), organizationTeamDescription, organization, new
 * HashSet<String>(Arrays.asList(role)), memberNames);
 * 
 * logger.debug("111c with team: " + team);
 * 
 * final List<Project> orgProjects = service.find("organization.id:" + organization.getId(), null, Project.class, null).getItems();
 * 
 * logger.debug("111d with orgProjects: " + orgProjects);
 * 
 * // Assign new teams to all projects in organization orgProjects.stream().forEach(project -> {
 * 
 * logger.debug("111e with project: " + project + " and team: " + team.getName());
 * 
 * try {
 * 
 * project.getTeams().add(team.getName()); service.update(project); } catch (Exception e) {
 * 
 * // TODO Auto-generated catch block e.printStackTrace(); }
 * 
 * }); logger.debug("111f");
 * 
 * organizationProjects.addAll(orgProjects); } catch (Exception e) {
 * 
 * throw new RuntimeException(e); }
 * 
 * logger.debug("111g"); });
 * 
 * logger.debug("111h"); }); logger.debug("111i");
 * 
 * // Persist all Projects & Orgs for (Organization organization : organizations) {
 * 
 * organization.getMembers().stream().forEach(name -> logger.debug("JE: organization " + organization.getName() + " member is: " + name)); service.update(organization); }
 * 
 * for (Project project : organizationProjects) {
 * 
 * logger.debug("JE: project " + project.getName() + "'s " + project.getTeams().size() + " teams are: " + project.getTeams());
 * 
 * Project dbProject = service.get(project.getId(), Project.class);
 * 
 * logger.debug("JE: while dbProject's " + dbProject.getTeams().size() + " teams are: " + dbProject.getTeams()); }
 */
