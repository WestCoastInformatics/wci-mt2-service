
package org.ihtsdo.refsetservice.rest.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.List;

import org.ihtsdo.refsetservice.model.Concept;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.rest.test.util.EditUnitTestUtilities;
import org.ihtsdo.refsetservice.rest.test.util.ExportUnitTestUtilities;
import org.ihtsdo.refsetservice.rest.test.util.GetUnitTestUtilities;
import org.ihtsdo.refsetservice.rest.test.util.WorkflowUnitTestUtilities;
import org.ihtsdo.refsetservice.service.SecurityService;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.terminologyservice.WorkflowService;
import org.ihtsdo.refsetservice.util.ConceptResultList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.json.JacksonTester;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Integration tests for MetadataController.
 */

@AutoConfigureMockMvc
public class RefsetPublishTest extends AbstractRefsetTests {

    /** The logger. */
    private static Logger logger = LoggerFactory.getLogger(RefsetPublishTest.class);

    static private boolean firstTimeSetup = true;

    /**
     * Sets the up.
     */
    @BeforeEach
    public void setUp(TestInfo info) {

        if (getUtil == null) {

            getUtil = new GetUnitTestUtilities(mvc, baseUrl, SIMPLE_DATE_FORMAT);
            exportUtil = new ExportUnitTestUtilities(mvc);
            workflowUtil = new WorkflowUnitTestUtilities(mvc, baseUrl, REFSET_FILE_PATH);
        }

        objectMapper = new ObjectMapper();
        JacksonTester.initFields(this, objectMapper);
        baseUrl = "/refset";

        try {

            if (firstTimeSetup) {

                testingProjectId = getUtil.getProjectInternalId(TESTING_PROJECT_NAME);
                testingEditionId = getUtil.getEditionInternalId(TESTING_EDITION_NAME);

                mainNrcTestingRefsetInternalId = getUtil.getRefsetInternalId(MAIN_NRC_TESTING_REFSET_ID, MAIN_NRC_TESTING_REFSET_VERSION);
                mainCoreTestingRefsetInternalId = getUtil.getRefsetInternalId(MAIN_CORE_TESTING_REFSET_ID, MAIN_CORE_TESTING_REFSET_VERSION);

                editUtil = new EditUnitTestUtilities(mvc, baseUrl, SIMPLE_DATE_FORMAT, testingProjectId, testingEditionId);

                firstTimeSetup = false;
            }

        } catch (Exception e) {

            e.printStackTrace();
        }

    }

    /**
     * Simulate an editing-to-publication cycle as much as possible
     * 
     * TODO: Advance with Workflow status
     *
     * @throws Exception the exception
     */
    @Test
    public void testOnExistingRefsetBranch() throws Exception {

        // Refset - 500201000057102 (Address type reference set)
        final String refsetId = "500201000057102";
        final String versionDate = "20201130";
        final String publicationVersionDate = "20201201";
        String releaseBranchPath = null;
        String refsetBranchPath = null;
        String refsetInternalId = null;
        Refset refset = null;
        // verify the new version
        try (final TerminologyService service = new TerminologyService()) {

            refsetInternalId = getUtil.getRefsetInternalId(refsetId, versionDate);
            refset = service.get(refsetInternalId, Refset.class);
            assertThat(refset).isNotNull();
            releaseBranchPath = refset.getEdition() + "/" + refset.getVersionDate();
            
            refsetBranchPath = releaseBranchPath + "/" + WorkflowService.REFSET_BRANCH_PREFIX + refsetId;
            logger.debug("Have internalId: " + refsetInternalId + " and branch: " + releaseBranchPath);
        } catch (Exception e) {

            throw e;
        }
        
        // Validate that refset is at expected state
        List<String> allowedWorkflowStatuses = WorkflowService.getAllowedActions(SecurityService.getUserFromSession(), refset);
        assertThat(allowedWorkflowStatuses).contains(WorkflowService.EDIT);
        
        /* Mimic Start Editing */
        // Verify number of members at start
        ConceptResultList members = getUtil.getMembers(refsetInternalId);
        assertThat(members.size()).isEqualTo(5);

        // Create Edit branch 
        // TODO: Remove this as it's unnecessary given bring refset into EDIT state automatically creates the branch
//        final String editBranchPath = snowUtil.createEditBranch(refsetBranchPath, refsetInternalId, refsetId);
//        assertThat(editBranchPath).isNotNull();

        String comment = "Starting testing editing cycle";
        refset = WorkflowService.setWorkflowStatusByAction(SecurityService.getUserFromSession(), WorkflowService.EDIT, refset, comment);
        
        // Verify still have the same 5 members in the refset found under the newly edit branch
        members = getUtil.getMembers(refsetInternalId);
        assertThat(members.size()).isEqualTo(5);
        
        // populate with one new member
        boolean success = editUtil.addMembers(refsetInternalId, Arrays.asList("404684003"));
        assertThat(success).isTrue();


        // Verify still have the same 6 members in the refset found under the newly create branch
        members = getUtil.getMembers(refsetInternalId);
        assertThat(members.size()).isEqualTo(6);

        // Update Workflow State to review
        comment = "The refset is at ready_for_review";
        refset = WorkflowService.setWorkflowStatusByAction(SecurityService.getUserFromSession(), WorkflowService.REQUEST_REVIEW, refset, comment);

        /* Merge/promote edit branch to refset branch */
        // Verify still have the same 5 members in the refset branch
        members = getUtil.getMembers(refsetInternalId);
        assertThat(members.size()).isEqualTo(5);

        // Merge Edit branch back to Refset branch
        success = WorkflowService.mergeEditIntoRefsetBranch(refset.getEdition().getBranch(), refsetId, refset.getEditBranchId(),    "Merging after adding one member to refset");
        assertThat(success).isTrue();

        // Verify still have the same 6 members in the refset found under the newly create branch
        members = getUtil.getMembers(refsetInternalId);
        assertThat(members.size()).isEqualTo(6);

        // Update Workflow State to review
        comment = "The refset is at ready_for_publication";
        refset = WorkflowService.setWorkflowStatusByAction(SecurityService.getUserFromSession(), WorkflowService.REQUEST_PUBLICATION, refset, comment);


        try (final TerminologyService service = new TerminologyService()) {
            List<String> failedConcepts = WorkflowService.completeRefsetPublication(service, refset, versionDate);
            assertThat(failedConcepts).isEmpty();
        } catch (Exception e) {
            e.printStackTrace();
        }
           
         
        /* Mimic Ready for Pub */
        // Merge review branch to code system branch
        // Merge/promote edit branch to review branch

//refset.getEdition().get
        
        
        // Delete refset branch - This is done once we are notified that
        comment = "The refset editing cycle is done. Delete refset branch from about-to-be-published release branch.";
//WorkflowService.getdeleteBranch(publicationVersionDate);
    }

}
