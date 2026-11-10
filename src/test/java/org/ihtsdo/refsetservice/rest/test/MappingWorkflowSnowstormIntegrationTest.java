package org.ihtsdo.refsetservice.rest.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.ihtsdo.refsetservice.model.MapWorkflowStatus;
import org.ihtsdo.refsetservice.model.MappingWorkflow;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.model.enums.MappingWorkflowAction;
import org.ihtsdo.refsetservice.rest.test.util.MappingWorkflowSnowstormSupport;
import org.ihtsdo.refsetservice.rest.test.util.MappingWorkflowUnitTestUtilities;
import org.ihtsdo.refsetservice.terminologyservice.MappingWorkflowTestFixtures;
import org.ihtsdo.refsetservice.terminologyservice.MappingWorkflowTestFixtures.Context;
import org.ihtsdo.refsetservice.test.BaseTest;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Live Snowstorm integration coverage for mapping workflow + concept branches. Make sure environment variables are set for Snowstorm
 * 
 * <pre>
 * export SNOMED_SNOWSTORM_USERNAME=
 * export SNOMED_SNOWSTORM_PASSWORD=
 * export SNOMED_SNOWSTORM_AUTH_URL=
 * export SNOMED_SNOWSTORM_AUTH_TYPE=basic
 * export SNOMED_SNOWSTORM_REST_BASE_URL=
 * </pre>
 * <p>
 * Excluded from default {@code ./gradlew test} (see {@code *IntegrationTest*}). Runs only when you remove that exclude or run this class explicitly. Fails hard
 * when Snowstorm is not configured or unreachable.
 * </p>
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "auth.dev.bypass=false", "mapping.workflow.concept.branch.enabled=true", "mapset.use.manage.service.initials=true", "terminology.handler=SNOMED_SNOWSTORM"
})
public class MappingWorkflowSnowstormIntegrationTest extends BaseTest {

    /** The Constant CONCEPT. */
    private static final String CONCEPT = MappingWorkflowTestFixtures.SOURCE_CONCEPT_CODE;

    /** The Constant USERNAME_PROP. */
    private static final String USERNAME_PROP = "terminology.handler.SNOMED_SNOWSTORM.username";

    /** The Constant PASSWORD_PROP. */
    private static final String PASSWORD_PROP = "terminology.handler.SNOMED_SNOWSTORM.password";

    /** The Constant AUTH_URL_PROP. */
    private static final String AUTH_URL_PROP = "terminology.handler.SNOMED_SNOWSTORM.authUrl";

    /** The Constant AUTH_TYPE_PROP. */
    private static final String AUTH_TYPE_PROP = "terminology.handler.SNOMED_SNOWSTORM.authType";

    /** The Constant REST_BASE_PROP. */
    private static final String REST_BASE_PROP = "terminology.handler.SNOMED_SNOWSTORM.restBaseUrl";

    /** The mvc. */
    @Autowired
    private MockMvc mvc;

    /** The workflow util. */
    private MappingWorkflowUnitTestUtilities workflowUtil;

    /**
     * Require live snowstorm.
     *
     * @throws Exception the exception
     */
    @BeforeAll
    public static void requireLiveSnowstorm() throws Exception {

        MappingWorkflowSnowstormSupport.loadDevEnvLocalIfNeeded();
        syncSnowstormPropertiesFromEnvironment();
        PropertyUtility.setProperty("mapping.workflow.concept.branch.enabled", "true");
        PropertyUtility.setProperty("terminology.handler", "SNOMED_SNOWSTORM");
        MappingWorkflowSnowstormSupport.requireSnowstorm();
    }

    /**
     * Sync snowstorm properties from environment.
     */
    private static void syncSnowstormPropertiesFromEnvironment() {

        putIfPresent("SNOMED_SNOWSTORM_USERNAME", USERNAME_PROP);
        putIfPresent("SNOMED_SNOWSTORM_PASSWORD", PASSWORD_PROP);
        putIfPresent("SNOMED_SNOWSTORM_AUTH_URL", AUTH_URL_PROP);
        putIfPresent("SNOMED_SNOWSTORM_AUTH_TYPE", AUTH_TYPE_PROP);
        final String restBase = firstNonBlank(System.getenv("SNOMED_SNOWSTORM_REST_BASE_URL"), System.getenv("SNOMED_SNOWSTORM_BASE_URL"),
            MappingWorkflowSnowstormSupport.envLocalValue("SNOMED_SNOWSTORM_REST_BASE_URL"),
            MappingWorkflowSnowstormSupport.envLocalValue("SNOMED_SNOWSTORM_BASE_URL"));
        if (restBase != null) {
            PropertyUtility.setProperty(REST_BASE_PROP, restBase);
        }
    }

    /**
     * Put if present.
     *
     * @param envName the env name
     * @param propertyName the property name
     */
    private static void putIfPresent(final String envName, final String propertyName) {

        final String value = firstNonBlank(System.getenv(envName), MappingWorkflowSnowstormSupport.envLocalValue(envName));
        if (value != null) {
            PropertyUtility.setProperty(propertyName, value);
        }
    }

    /**
     * First non blank.
     *
     * @param values the values
     * @return the string
     */
    private static String firstNonBlank(final String... values) {

        if (values == null) {
            return null;
        }
        for (final String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    /**
     * Setup.
     */
    @BeforeEach
    public void setup() {

        PropertyUtility.setProperty("mapping.workflow.concept.branch.enabled", "true");
        workflowUtil = new MappingWorkflowUnitTestUtilities(mvc, "/mapset");
    }

    /**
     * Specialist assign creates concept branch.
     *
     * @throws Exception the exception
     */
    @Test
    public void specialistAssignCreatesConceptBranch() throws Exception {

        try (Context context = MappingWorkflowTestFixtures.Context.createReviewProjectInEdit()) {
            MappingWorkflowSnowstormSupport.wireSnowstormBranches(context, false);
            try {
                final MappingWorkflow assigned = act(context, MappingWorkflowAction.ASSIGN, "assign", context.getSpecialistUser());
                assertEquals(MapWorkflowStatus.EDITING_IN_PROGRESS, assigned.getWorkflowStatus());
                assertEquals(context.getSpecialistUser().getUserName(), assigned.getAssignedUser());
                MappingWorkflowSnowstormSupport.assertConceptBranch(context, CONCEPT, true);
            } finally {
                MappingWorkflowSnowstormSupport.cleanupBranches(context, CONCEPT);
            }
        }
    }

    /**
     * Specialist assign then release deletes concept branch.
     *
     * @throws Exception the exception
     */
    @Test
    public void specialistAssignThenReleaseDeletesConceptBranch() throws Exception {

        try (Context context = MappingWorkflowTestFixtures.Context.createReviewProjectInEdit()) {
            MappingWorkflowSnowstormSupport.wireSnowstormBranches(context, false);
            try {
                act(context, MappingWorkflowAction.ASSIGN, "assign", context.getSpecialistUser());
                MappingWorkflowSnowstormSupport.assertConceptBranch(context, CONCEPT, true);

                final MappingWorkflow released = act(context, MappingWorkflowAction.RELEASE, "unassign", context.getSpecialistUser());
                assertEquals(MapWorkflowStatus.NEW, released.getWorkflowStatus());
                assertNull(released.getAssignedUser());
                MappingWorkflowSnowstormSupport.assertConceptBranch(context, CONCEPT, false);
            } finally {
                MappingWorkflowSnowstormSupport.cleanupBranches(context, CONCEPT);
            }
        }
    }

    /**
     * Specialist finish editing merges and deletes concept branch.
     *
     * @throws Exception the exception
     */
    @Test
    public void specialistFinishEditingMergesAndDeletesConceptBranch() throws Exception {

        try (Context context = MappingWorkflowTestFixtures.Context.createReviewProjectInEdit()) {
            MappingWorkflowSnowstormSupport.wireSnowstormBranches(context, false);
            try {
                act(context, MappingWorkflowAction.ASSIGN, "assign", context.getSpecialistUser());
                MappingWorkflowSnowstormSupport.assertConceptBranch(context, CONCEPT, true);

                final MappingWorkflow finished = act(context, MappingWorkflowAction.FINISH_EDITING, "TARGET:111111", context.getSpecialistUser());
                assertEquals(MapWorkflowStatus.REVIEW_NEEDED, finished.getWorkflowStatus());
                assertNull(finished.getAssignedUser());
                MappingWorkflowSnowstormSupport.assertConceptBranch(context, CONCEPT, false);
            } finally {
                MappingWorkflowSnowstormSupport.cleanupBranches(context, CONCEPT);
            }
        }
    }

    /**
     * Review path accept pass.
     *
     * @throws Exception the exception
     */
    @Test
    public void reviewPathAcceptPass() throws Exception {

        try (Context context = MappingWorkflowTestFixtures.Context.createReviewProjectInEdit()) {
            MappingWorkflowSnowstormSupport.wireSnowstormBranches(context, false);
            try {
                act(context, MappingWorkflowAction.ASSIGN, "assign", context.getSpecialistUser());
                act(context, MappingWorkflowAction.FINISH_EDITING, "done", context.getSpecialistUser());

                final MappingWorkflow reviewing = act(context, MappingWorkflowAction.START_REVIEW, "review", context.getLeadUser());
                assertEquals(MapWorkflowStatus.REVIEW_IN_PROGRESS, reviewing.getWorkflowStatus());
                assertEquals(context.getLeadUser().getUserName(), reviewing.getAssignedUser());

                final MappingWorkflow accepted = act(context, MappingWorkflowAction.ACCEPT_REVIEW, "accepted", context.getLeadUser());
                assertEquals(MapWorkflowStatus.READY_FOR_PUBLICATION, accepted.getWorkflowStatus());
                assertNull(accepted.getAssignedUser());
            } finally {
                MappingWorkflowSnowstormSupport.cleanupBranches(context, CONCEPT);
            }
        }
    }

    /**
     * Review path reject returns to new.
     *
     * @throws Exception the exception
     */
    @Test
    public void reviewPathRejectReturnsToNew() throws Exception {

        try (Context context = MappingWorkflowTestFixtures.Context.createReviewProjectInEdit()) {
            MappingWorkflowSnowstormSupport.wireSnowstormBranches(context, false);
            try {
                act(context, MappingWorkflowAction.ASSIGN, "assign", context.getSpecialistUser());
                act(context, MappingWorkflowAction.FINISH_EDITING, "done", context.getSpecialistUser());
                act(context, MappingWorkflowAction.START_REVIEW, "review", context.getLeadUser());

                final MappingWorkflow rejected = act(context, MappingWorkflowAction.REJECT_REVIEW, "reject", context.getLeadUser());
                assertEquals(MapWorkflowStatus.NEW, rejected.getWorkflowStatus());
                assertNull(rejected.getAssignedUser());

                final MappingWorkflow reassigned = act(context, MappingWorkflowAction.ASSIGN, "reassign-after-reject", context.getSpecialistUser());
                assertEquals(MapWorkflowStatus.EDITING_IN_PROGRESS, reassigned.getWorkflowStatus());
                MappingWorkflowSnowstormSupport.assertConceptBranch(context, CONCEPT, true);
            } finally {
                MappingWorkflowSnowstormSupport.cleanupBranches(context, CONCEPT);
            }
        }
    }

    /**
     * Review path request revision then reassign.
     *
     * @throws Exception the exception
     */
    @Test
    public void reviewPathRequestRevisionThenReassign() throws Exception {

        try (Context context = MappingWorkflowTestFixtures.Context.createReviewProjectInEdit()) {
            MappingWorkflowSnowstormSupport.wireSnowstormBranches(context, false);
            try {
                act(context, MappingWorkflowAction.ASSIGN, "assign", context.getSpecialistUser());
                act(context, MappingWorkflowAction.FINISH_EDITING, "done", context.getSpecialistUser());
                act(context, MappingWorkflowAction.START_REVIEW, "review", context.getLeadUser());

                final MappingWorkflow revised = act(context, MappingWorkflowAction.REQUEST_REVISION, "needs-work", context.getLeadUser());
                assertEquals(MapWorkflowStatus.EDITING_IN_PROGRESS, revised.getWorkflowStatus());
                assertNull(revised.getAssignedUser());

                final MappingWorkflow reassigned =
                    act(context, MappingWorkflowAction.REASSIGN, "back-to-specialist", context.getLeadUser(), context.getSpecialistUser().getUserName());
                assertEquals(MapWorkflowStatus.EDITING_IN_PROGRESS, reassigned.getWorkflowStatus());
                assertEquals(context.getSpecialistUser().getUserName(), reassigned.getAssignedUser());
            } finally {
                MappingWorkflowSnowstormSupport.cleanupBranches(context, CONCEPT);
            }
        }
    }

    /**
     * Lead start review assigns self.
     *
     * @throws Exception the exception
     */
    @Test
    public void leadStartReviewAssignsSelf() throws Exception {

        try (Context context = MappingWorkflowTestFixtures.Context.createReviewProjectInEdit()) {
            MappingWorkflowSnowstormSupport.wireSnowstormBranches(context, false);
            try {
                act(context, MappingWorkflowAction.ASSIGN, "assign", context.getSpecialistUser());
                act(context, MappingWorkflowAction.FINISH_EDITING, "done", context.getSpecialistUser());

                final MappingWorkflow reviewing = act(context, MappingWorkflowAction.START_REVIEW, "mine", context.getLeadUser());
                assertEquals(MapWorkflowStatus.REVIEW_IN_PROGRESS, reviewing.getWorkflowStatus());
                assertEquals(context.getLeadUser().getUserName(), reviewing.getAssignedUser());
            } finally {
                MappingWorkflowSnowstormSupport.cleanupBranches(context, CONCEPT);
            }
        }
    }

    /**
     * Lead reassign to other specialist.
     *
     * @throws Exception the exception
     */
    @Test
    public void leadReassignToOtherSpecialist() throws Exception {

        try (Context context = MappingWorkflowTestFixtures.Context.createReviewProjectInEdit()) {
            MappingWorkflowSnowstormSupport.wireSnowstormBranches(context, false);
            try {
                act(context, MappingWorkflowAction.ASSIGN, "assign", context.getSpecialistUser());
                MappingWorkflowSnowstormSupport.assertConceptBranch(context, CONCEPT, true);

                final MappingWorkflow reassigned =
                    act(context, MappingWorkflowAction.REASSIGN, "handoff", context.getLeadUser(), context.getOtherSpecialistUser().getUserName());
                assertEquals(MapWorkflowStatus.EDITING_IN_PROGRESS, reassigned.getWorkflowStatus());
                assertEquals(context.getOtherSpecialistUser().getUserName(), reassigned.getAssignedUser());
                MappingWorkflowSnowstormSupport.assertConceptBranch(context, CONCEPT, true);
            } finally {
                MappingWorkflowSnowstormSupport.cleanupBranches(context, CONCEPT);
            }
        }
    }

    /**
     * Lead assign to self edit and finish.
     *
     * @throws Exception the exception
     */
    @Test
    public void leadAssignToSelfEditAndFinish() throws Exception {

        try (Context context = MappingWorkflowTestFixtures.Context.createReviewProjectInEdit()) {
            MappingWorkflowSnowstormSupport.wireSnowstormBranches(context, true);
            try {
                final MappingWorkflow assigned = act(context, MappingWorkflowAction.ASSIGN, "lead-self", context.getLeadUser());
                assertEquals(MapWorkflowStatus.EDITING_IN_PROGRESS, assigned.getWorkflowStatus());
                assertEquals(context.getLeadUser().getUserName(), assigned.getAssignedUser());
                MappingWorkflowSnowstormSupport.assertConceptBranch(context, CONCEPT, true);

                final MappingWorkflow finished = act(context, MappingWorkflowAction.FINISH_EDITING, "lead-edited", context.getLeadUser());
                assertEquals(MapWorkflowStatus.REVIEW_NEEDED, finished.getWorkflowStatus());
                MappingWorkflowSnowstormSupport.assertConceptBranch(context, CONCEPT, false);

                final MappingWorkflow reviewing = act(context, MappingWorkflowAction.START_REVIEW, "lead-reviews-own", context.getLeadUser());
                assertEquals(MapWorkflowStatus.REVIEW_IN_PROGRESS, reviewing.getWorkflowStatus());
                assertEquals(context.getLeadUser().getUserName(), reviewing.getAssignedUser());
            } finally {
                MappingWorkflowSnowstormSupport.cleanupBranches(context, CONCEPT);
            }
        }
    }

    /**
     * Admin force release deletes concept branch.
     *
     * @throws Exception the exception
     */
    @Test
    public void adminForceReleaseDeletesConceptBranch() throws Exception {

        try (Context context = MappingWorkflowTestFixtures.Context.createReviewProjectInEdit()) {
            MappingWorkflowSnowstormSupport.wireSnowstormBranches(context, false);
            try {
                act(context, MappingWorkflowAction.ASSIGN, "assign", context.getSpecialistUser());
                MappingWorkflowSnowstormSupport.assertConceptBranch(context, CONCEPT, true);

                final MappingWorkflow forced = act(context, MappingWorkflowAction.FORCE_RELEASE, "force", context.getAdminUser());
                assertEquals(MapWorkflowStatus.NEW, forced.getWorkflowStatus());
                assertNull(forced.getAssignedUser());
                MappingWorkflowSnowstormSupport.assertConceptBranch(context, CONCEPT, false);
            } finally {
                MappingWorkflowSnowstormSupport.cleanupBranches(context, CONCEPT);
            }
        }
    }

    /**
     * Simple path approve for publication.
     *
     * @throws Exception the exception
     */
    @Test
    public void simplePathApproveForPublication() throws Exception {

        try (Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            MappingWorkflowSnowstormSupport.wireSnowstormBranches(context, false);
            try {
                act(context, MappingWorkflowAction.ASSIGN, "assign", context.getSpecialistUser());
                act(context, MappingWorkflowAction.FINISH_EDITING, "done", context.getSpecialistUser());
                final MappingWorkflow approved = act(context, MappingWorkflowAction.APPROVE_FOR_PUBLICATION, "approve", context.getLeadUser());
                assertEquals(MapWorkflowStatus.READY_FOR_PUBLICATION, approved.getWorkflowStatus());
                MappingWorkflowSnowstormSupport.assertConceptBranch(context, CONCEPT, false);
            } finally {
                MappingWorkflowSnowstormSupport.cleanupBranches(context, CONCEPT);
            }
        }
    }

    /**
     * Specialist cannot start review.
     *
     * @throws Exception the exception
     */
    @Test
    public void specialistCannotStartReview() throws Exception {

        try (Context context = MappingWorkflowTestFixtures.Context.createReviewProjectInEdit()) {
            MappingWorkflowSnowstormSupport.wireSnowstormBranches(context, false);
            try {
                act(context, MappingWorkflowAction.ASSIGN, "assign", context.getSpecialistUser());
                act(context, MappingWorkflowAction.FINISH_EDITING, "done", context.getSpecialistUser());
                expectForbidden(context, MappingWorkflowAction.START_REVIEW, "nope", context.getSpecialistUser());
                assertEquals(MapWorkflowStatus.REVIEW_NEEDED, context.reloadWorkflow().getWorkflowStatus());
            } finally {
                MappingWorkflowSnowstormSupport.cleanupBranches(context, CONCEPT);
            }
        }
    }

    /**
     * Assign when already assigned is rejected.
     *
     * @throws Exception the exception
     */
    @Test
    public void assignWhenAlreadyAssignedIsRejected() throws Exception {

        try (Context context = MappingWorkflowTestFixtures.Context.createReviewProjectInEdit()) {
            MappingWorkflowSnowstormSupport.wireSnowstormBranches(context, false);
            try {
                act(context, MappingWorkflowAction.ASSIGN, "first", context.getSpecialistUser());
                expectConflict(context, MappingWorkflowAction.ASSIGN, "second", context.getOtherSpecialistUser());
                assertEquals(context.getSpecialistUser().getUserName(), context.reloadWorkflow().getAssignedUser());
                MappingWorkflowSnowstormSupport.assertConceptBranch(context, CONCEPT, true);
            } finally {
                MappingWorkflowSnowstormSupport.cleanupBranches(context, CONCEPT);
            }
        }
    }

    /**
     * History records assign release and review actions.
     *
     * @throws Exception the exception
     */
    @Test
    public void historyRecordsAssignReleaseAndReviewActions() throws Exception {

        try (Context context = MappingWorkflowTestFixtures.Context.createReviewProjectInEdit()) {
            MappingWorkflowSnowstormSupport.wireSnowstormBranches(context, false);
            try {
                act(context, MappingWorkflowAction.ASSIGN, "assign", context.getSpecialistUser());
                act(context, MappingWorkflowAction.RELEASE, "release", context.getSpecialistUser());
                act(context, MappingWorkflowAction.ASSIGN, "assign2", context.getSpecialistUser());
                act(context, MappingWorkflowAction.FINISH_EDITING, "done", context.getSpecialistUser());
                act(context, MappingWorkflowAction.START_REVIEW, "review", context.getLeadUser());
                act(context, MappingWorkflowAction.ACCEPT_REVIEW, "accept", context.getLeadUser());

                assertTrue(workflowUtil.getWorkflowHistory(context.getMapSet().getId(), CONCEPT, context.getLeadUser()).size() >= 6);
            } finally {
                MappingWorkflowSnowstormSupport.cleanupBranches(context, CONCEPT);
            }
        }
    }

    /**
     * Act.
     *
     * @param context the context
     * @param action the action
     * @param note the note
     * @param asUser the as user
     * @return the mapping workflow
     * @throws Exception the exception
     */
    private MappingWorkflow act(final Context context, final MappingWorkflowAction action, final String note, final User asUser) throws Exception {

        return act(context, action, note, asUser, null);
    }

    /**
     * Act.
     *
     * @param context the context
     * @param action the action
     * @param note the note
     * @param asUser the as user
     * @param assignToUser the assign to user
     * @return the mapping workflow
     * @throws Exception the exception
     */
    private MappingWorkflow act(final Context context, final MappingWorkflowAction action, final String note, final User asUser, final String assignToUser)
        throws Exception {

        return workflowUtil.updateWorkflow(context.getMapSet().getId(), CONCEPT, action, note, asUser, assignToUser);
    }

    /**
     * Expect forbidden.
     *
     * @param context the context
     * @param action the action
     * @param note the note
     * @param asUser the as user
     * @throws Exception the exception
     */
    private void expectForbidden(final Context context, final MappingWorkflowAction action, final String note, final User asUser) throws Exception {

        workflowUtil.updateWorkflowExpectForbidden(context.getMapSet().getId(), CONCEPT, action, note, asUser);
    }

    /**
     * Expect conflict.
     *
     * @param context the context
     * @param action the action
     * @param note the note
     * @param asUser the as user
     * @throws Exception the exception
     */
    private void expectConflict(final Context context, final MappingWorkflowAction action, final String note, final User asUser) throws Exception {

        workflowUtil.updateWorkflowExpectConflict(context.getMapSet().getId(), CONCEPT, action, note, asUser);
    }
}
