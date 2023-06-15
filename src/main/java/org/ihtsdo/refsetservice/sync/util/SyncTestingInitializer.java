package org.ihtsdo.refsetservice.sync.util;

import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.ihtsdo.refsetservice.model.DefinitionClause;
import org.ihtsdo.refsetservice.model.DiscussionPost;
import org.ihtsdo.refsetservice.model.DiscussionThread;
import org.ihtsdo.refsetservice.model.DiscussionType;
import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.model.VersionStatus;
import org.ihtsdo.refsetservice.service.SecurityService;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.terminologyservice.WorkflowService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SyncTestingInitializer {

    private final Logger LOG = LoggerFactory.getLogger(SyncTestingInitializer.class);

    private SyncUtilities utilities;

    private SyncDatabaseHandler dbHandler;

    private static User feedbackInitiatiorUser = null;

    private static User userResponderUser = null;

    static private Edition developerTestingEdition = null;

    static private Project testingProject = null;

    private static final String FEEDBACK_REFSET_NAME_BASE = "WCI Testing Feedback Refset ";

    private static final String FEEDBACK_REFSET_ID_BASE = "9999999";

    private static final String INTENSIONAL_REFSET_NAME_BASE = "WCI Testing Intensional Refset ";

    private static final String INTENSIONAL_REFSET_ID_BASE = "8888888";

    private static final String WCI_TESTING_PROJECT_NAME = "WCI Testing Project";

    public SyncTestingInitializer() {

        // Support one-off usages for specific testing cases i.e. adding an intensional refset
        try (TerminologyService service = new TerminologyService()) {
            initializeSync(service);
        } catch (Exception e) {
            LOG.error("Failed starting the testing initialization from controller other than sync with errorMessage: " + e.getMessage());
        }
    }

    private void initializeSync(final TerminologyService service) {
        service.setModifiedBy("Sync");
        service.setModifiedFlag(true);

        if (dbHandler == null) {
            dbHandler = new SyncDatabaseHandler(null);
        }
        if (utilities == null) {

            utilities = new SyncUtilities(dbHandler);
        }

        dbHandler.setUtilities(utilities);

        try {
            // For Feedback Refset
            feedbackInitiatiorUser =
                    utilities.getUser(service, "feedbackInitiator", "feedbackInitiator", "feedbackInitiator@westcoastinformatics.com", new HashSet<String>(Arrays.asList(User.ROLE_AUTHOR)));
            userResponderUser =
                    utilities.getUser(service, "feedbackResponder", "feedbackResponder", "feedbackResponder@westcoastinformatics.com", new HashSet<String>(Arrays.asList(User.ROLE_AUTHOR)));
        } catch (Exception e) {

            e.printStackTrace();
        }

    }

    /*
     * Called when adding another instance of testing-feedback refset
     */
    public Refset createTestingFeedbackRefset() throws Exception {
        try (final TerminologyService service = new TerminologyService()) {
            initializeSync(service);

            final Refset newTestingRefset = createTestingRefset(service, FEEDBACK_REFSET_NAME_BASE, FEEDBACK_REFSET_ID_BASE);

            addFeedbackContent(service, newTestingRefset);

            return newTestingRefset;
        }
    }

    /*
     * Called when adding another instance of testing-intensional refset
     */
    public Refset createTestingIntensionalRefset() throws Exception {
        try (TerminologyService service = new TerminologyService()) {
            initializeSync(service);

            final Refset newTestingRefset = createTestingRefset(service, INTENSIONAL_REFSET_NAME_BASE, INTENSIONAL_REFSET_ID_BASE);

            addIntensionalContent(service, newTestingRefset);

            return newTestingRefset;
        }
    }

    private Refset createTestingRefset(final TerminologyService service, final String testingRefsetName, final String testingRefsetId) throws Exception {

        final Project developerTestingProject = getDeveloperTestingProject(service);

        final List<Refset> projectRefsets = service.find("projectId:" + developerTestingProject.getId() + " AND active:true", null, Refset.class, null).getItems();

        int latestVersion = 0;

        for (final Refset projectRefset : projectRefsets) {

            if (projectRefset.getRefsetId().startsWith(testingRefsetId) && projectRefset.getName().startsWith(testingRefsetName)) {

                final int refsetVersion = Integer.parseInt(projectRefset.getName().substring(testingRefsetName.length()).trim());

                if (refsetVersion > latestVersion) {

                    latestVersion = refsetVersion;
                }
                // Iterate through the refsets, look at the refset name, and find the integer list after the default name.
                // if keysize = 0, this is first one. So create with RefsetId: based on the testingRefsetId and iteration.
                // else, if the refset integer is greater than the greatest one seen, make this the new refsetName & refsetId integer

            }

        }

        Refset newTestingRefset;

        if (latestVersion == 0) {

            newTestingRefset = dbHandler.addWCIRefset(service, SecurityService.getUserFromSession(), testingRefsetName + "1", testingRefsetId + "01",
                    getDeveloperTestingEdition(service).getModules().iterator().next(), new Date(), "", VersionStatus.PUBLISHED, WorkflowService.PUBLISHED, getDeveloperTestingProject(service));
        } else {

            latestVersion++;
            final String tensValue = Integer.toString(latestVersion / 10);
            final String onesValue = Integer.toString(latestVersion % 10);

            newTestingRefset = dbHandler.addWCIRefset(service, SecurityService.getUserFromSession(), testingRefsetName + latestVersion, testingRefsetId + tensValue + onesValue,
                    getDeveloperTestingEdition(service).getModules().iterator().next(), new Date(), "", VersionStatus.PUBLISHED, WorkflowService.PUBLISHED, getDeveloperTestingProject(service));
        }

        LOG.info("Creating new testing refset: newTestingRefset: " + newTestingRefset.getRefsetId() + " - " + newTestingRefset.getName());

        return newTestingRefset;
    }

    private void addIntensionalContent(final TerminologyService service, final Refset refset) throws Exception {

        // Create ecl clause
        final String testClause = "<<716186003 |No known allergy (situation)|";
        final DefinitionClause clause = new DefinitionClause();
        clause.setNegated(false);
        clause.setValue(testClause);
        final DefinitionClause persistedClause = dbHandler.addDefinitionClause(service, clause);

        // Set Intensional Refset Infromation
        refset.setType(Refset.INTENSIONAL);
        refset.getDefinitionClauses().add(persistedClause);

        dbHandler.updateRefset(service, refset);

    }

    private void addFeedbackContent(final TerminologyService service, final Refset refset) throws Exception {

        // add feedback
        final DiscussionThread thread = new DiscussionThread();
        thread.setSubject("Testing discussion thread on refset");
        thread.setType(DiscussionType.REFSET.toString());
        thread.setRefsetInternalId(refset.getId());
        thread.setStatus("OPEN");
        thread.setVisibility("VISIBLE");
        thread.setPrivateThread(false);
        final DiscussionThread newThread = service.add(thread);

        service.setTransactionPerOperation(false);
        service.beginTransaction();

        final DiscussionPost post = new DiscussionPost();
        post.setUser(feedbackInitiatiorUser);
        post.setMessage("Topic Header");
        post.setVisibility("VISIBLE");
        post.setPrivatePost(false);
        final DiscussionPost newPost = service.add(post);
        newThread.getPosts().add(newPost);

        final DiscussionPost secondPost = new DiscussionPost();
        secondPost.setUser(userResponderUser);
        secondPost.setMessage("Comment #1");
        secondPost.setVisibility("VISIBLE");
        secondPost.setPrivatePost(false);
        final DiscussionPost newSecondPost = service.add(secondPost);
        newThread.getPosts().add(newSecondPost);

        final DiscussionPost thirdPost = new DiscussionPost();
        thirdPost.setUser(feedbackInitiatiorUser);
        thirdPost.setMessage("Comment #2");
        thirdPost.setVisibility("VISIBLE");
        thirdPost.setPrivatePost(false);
        final DiscussionPost newThirdPost = service.add(thirdPost);
        newThread.getPosts().add(newThirdPost);

        // Finalize transaction
        service.update(newThread);
        service.commit();
        service.setTransactionPerOperation(true);

        // Create users for testing initial feedback
        // Create users and teams, then add to org/project
        final Set<String> userRole = new HashSet<>();
        userRole.add(User.ROLE_AUTHOR);
        final Set<String> memberIds = new HashSet<>();
        memberIds.add(feedbackInitiatiorUser.getId());
        memberIds.add(userResponderUser.getId());

        testingProject = dbHandler.updateProject(service, testingProject);

        getDeveloperTestingEdition(service).getOrganization().getMembers().add(feedbackInitiatiorUser);
        getDeveloperTestingEdition(service).getOrganization().getMembers().add(userResponderUser);
        dbHandler.updateOrganization(service, getDeveloperTestingEdition(service).getOrganization());

    }

    private Project getDeveloperTestingProject(final TerminologyService service) throws Exception {

        if (testingProject == null) {

            final List<Project> projects = service.getAll(Project.class);

            for (final Project p : projects) {

                if (p.getName().equals(WCI_TESTING_PROJECT_NAME)) {

                    testingProject = p;
                }

            }

            if (testingProject == null) {

                throw new Exception("Testing Project doesn't exist. Shouldn't be running this on a non-Production instanace of RT2");
            }

        }

        return testingProject;
    }

    private Edition getDeveloperTestingEdition(final TerminologyService service) throws Exception {

        if (developerTestingEdition == null) {

            final List<Edition> editions = service.getAll(Edition.class);

            for (final Edition e : editions) {

                if (e.getName().toLowerCase().contains("wci")) {

                    developerTestingEdition = e;
                }

            }

            if (developerTestingEdition == null) {

                throw new Exception("Testing Organization doesn't exist. Shouldn't be running this on a non-Production instance of RT2");
            }

        }

        return developerTestingEdition;
    }

}