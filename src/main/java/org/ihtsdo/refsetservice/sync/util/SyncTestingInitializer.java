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

import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.ihtsdo.refsetservice.model.DefinitionClause;
import org.ihtsdo.refsetservice.model.DiscussionPost;
import org.ihtsdo.refsetservice.model.DiscussionThread;
import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.model.enums.DiscussionType;
import org.ihtsdo.refsetservice.model.enums.RefsetType;
import org.ihtsdo.refsetservice.model.enums.VersionStatus;
import org.ihtsdo.refsetservice.model.enums.WorkflowStatus;
import org.ihtsdo.refsetservice.service.SecurityService;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Class SyncTestingInitializer.
 */
public class SyncTestingInitializer {

    /** The log. */
    private static final Logger LOG = LoggerFactory.getLogger(SyncTestingInitializer.class);

    /** The utilities. */
    private SyncUtilities utilities;

    /** The db handler. */
    private SyncDatabaseHandler dbHandler;

    /** The feedback initiatior user. */
    private static User feedbackInitiatiorUser = null;

    /** The user responder user. */
    private static User userResponderUser = null;

    /** The developer testing edition. */
    private static Edition developerTestingEdition = null;

    /** The testing project. */
    private static Project testingProject = null;

    /** The Constant FEEDBACK_REFSET_NAME_BASE. */
    private static final String FEEDBACK_REFSET_NAME_BASE = "WCI Testing Feedback Refset ";

    /** The Constant FEEDBACK_REFSET_ID_BASE. */
    private static final String FEEDBACK_REFSET_ID_BASE = "9999999";

    /** The Constant INTENSIONAL_REFSET_NAME_BASE. */
    private static final String INTENSIONAL_REFSET_NAME_BASE = "WCI Testing Intensional Refset ";

    /** The Constant INTENSIONAL_REFSET_ID_BASE. */
    private static final String INTENSIONAL_REFSET_ID_BASE = "8888888";

    /** The Constant WCI_TESTING_PROJECT_NAME. */
    private static final String WCI_TESTING_PROJECT_NAME = "WCI Testing Project";

    /**
     * Instantiates an empty {@link SyncTestingInitializer}.
     */
    public SyncTestingInitializer() {

        // Support one-off usages for specific testing cases i.e. adding an intensional refset
        try (TerminologyService service = new TerminologyService()) {
            initializeSync(service);
        } catch (final Exception e) {
            LOG.error("Failed starting the testing initialization from controller other than sync with errorMessage: " + e.getMessage());
        }
    }

    /**
     * Initialize sync.
     *
     * @param service the service
     */
    private void initializeSync(final TerminologyService service) {

        service.setModifiedBy("Sync");
        service.setModifiedFlag(true);

        if (dbHandler == null) {
            dbHandler = new SyncDatabaseHandler(null, new SyncStatistics());
        }
        if (utilities == null) {

            utilities = new SyncUtilities(dbHandler);
        }

        dbHandler.setUtilities(utilities);

        try {
            // For Feedback Refset
            feedbackInitiatiorUser = utilities.getUser(service, "feedbackInitiator", "feedbackInitiator", "feedbackInitiator@westcoastinformatics.com",
                new HashSet<String>(Arrays.asList(User.ROLE_AUTHOR)));
            userResponderUser = utilities.getUser(service, "feedbackResponder", "feedbackResponder", "feedbackResponder@westcoastinformatics.com",
                new HashSet<String>(Arrays.asList(User.ROLE_AUTHOR)));
        } catch (final Exception e) {

            e.printStackTrace();
        }

    }

    /**
     * Creates the testing feedback refset. Called when adding another instance of testing-feedback refset.
     *
     * @return the refset
     * @throws Exception the exception
     */
    public Refset createTestingFeedbackRefset() throws Exception {

        try (final TerminologyService service = new TerminologyService()) {
            initializeSync(service);

            final Refset newTestingRefset = createTestingRefset(service, FEEDBACK_REFSET_NAME_BASE, FEEDBACK_REFSET_ID_BASE);

            addFeedbackContent(service, newTestingRefset);

            return newTestingRefset;
        }
    }

    /**
     * Creates the testing intensional refset. Called when adding another instance of testing-intensional refset.
     *
     * @return the refset
     * @throws Exception the exception
     */
    public Refset createTestingIntensionalRefset() throws Exception {

        try (TerminologyService service = new TerminologyService()) {
            initializeSync(service);

            final Refset newTestingRefset = createTestingRefset(service, INTENSIONAL_REFSET_NAME_BASE, INTENSIONAL_REFSET_ID_BASE);

            addIntensionalContent(service, newTestingRefset);

            return newTestingRefset;
        }
    }

    /**
     * Creates the testing refset.
     *
     * @param service the service
     * @param testingRefsetName the testing refset name
     * @param testingRefsetId the testing refset id
     * @return the refset
     * @throws Exception the exception
     */
    private Refset createTestingRefset(final TerminologyService service, final String testingRefsetName, final String testingRefsetId) throws Exception {

        final Project developerTestingProject = getDeveloperTestingProject(service);

        final List<Refset> projectRefsets =
            service.find("projectId:" + developerTestingProject.getId() + " AND active:true", null, Refset.class, null).getItems();

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
                getDeveloperTestingEdition(service).getModules(), new Date(), "", VersionStatus.PUBLISHED, WorkflowStatus.PUBLISHED,
                getDeveloperTestingProject(service));
        } else {

            latestVersion++;
            final String tensValue = Integer.toString(latestVersion / 10);
            final String onesValue = Integer.toString(latestVersion % 10);

            newTestingRefset = dbHandler.addWCIRefset(service, SecurityService.getUserFromSession(), testingRefsetName + latestVersion,
                testingRefsetId + tensValue + onesValue, getDeveloperTestingEdition(service).getModules(), new Date(), "", VersionStatus.PUBLISHED,
                WorkflowStatus.PUBLISHED, getDeveloperTestingProject(service));
        }

        LOG.info("Creating new testing refset: newTestingRefset: " + newTestingRefset.getRefsetId() + " - " + newTestingRefset.getName());

        return newTestingRefset;
    }

    /**
     * Adds the intensional content.
     *
     * @param service the service
     * @param refset the refset
     * @throws Exception the exception
     */
    private void addIntensionalContent(final TerminologyService service, final Refset refset) throws Exception {

        // Create ecl clause
        final String testClause = "<<716186003 |No known allergy (situation)|";
        final DefinitionClause clause = new DefinitionClause();
        clause.setNegated(false);
        clause.setValue(testClause);
        final DefinitionClause persistedClause = dbHandler.addDefinitionClause(service, clause);

        // Set Intensional Refset Infromation
        refset.setType(RefsetType.INTENSIONAL);
        refset.getDefinitionClauses().add(persistedClause);

        dbHandler.updateRefset(service, refset);

    }

    /**
     * Adds the feedback content.
     *
     * @param service the service
     * @param refset the refset
     * @throws Exception the exception
     */
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

    /**
     * Returns the developer testing project.
     *
     * @param service the service
     * @return the developer testing project
     * @throws Exception the exception
     */
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

    /**
     * Returns the developer testing edition.
     *
     * @param service the service
     * @return the developer testing edition
     * @throws Exception the exception
     */
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
