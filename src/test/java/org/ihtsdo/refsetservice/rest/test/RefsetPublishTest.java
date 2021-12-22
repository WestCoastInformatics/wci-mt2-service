
package org.ihtsdo.refsetservice.rest.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.ihtsdo.refsetservice.model.Concept;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.service.TerminologyService;
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

    /**
     * Sets the up.
     */
    @BeforeEach
    public void setUp(TestInfo info) {

        if (testingEditionId != null && testingEditionId.isEmpty()) {

            objectMapper = new ObjectMapper();
            JacksonTester.initFields(this, objectMapper);
            baseUrl = "/refset";

            try {
                testingEditionId = internalidGetterUtil.getEditionInternalId(TESTING_EDITION_NAME);
                testingProjectId = internalidGetterUtil.getProjectInternalId(TESTING_PROJECT_NAME);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

    /**
     *
     * @throws Exception the exception
     */
    @Test
    public void testOnExistingRefsetWithNrcBranch() throws Exception {
        // Refset - 500201000057102 (Address type reference set)
        final String refsetId = "500201000057102";
        final String versionDate = "20201130";
        String releaseBranchPath = null;
        String refsetInternalId = null;

        // verify the new version
        try (final TerminologyService service = new TerminologyService()) {
            refsetInternalId = internalidGetterUtil.getRefsetInternalId(refsetId, versionDate);
            Refset refset = service.get(refsetInternalId, Refset.class);
            assertThat(refset).isNotNull();
            releaseBranchPath = refset.getEdition().getBranch();

            logger.debug(
                    "Have internalId: " + refsetInternalId + " and branch: " + releaseBranchPath);
        } catch (Exception e) {
            throw e;
        }

        /* Mimic Start Editing */
        // Verify number of members at start
        callSnow("000 - POST",
                "https://dev-snowstorm.ihtsdotools.org/snowstorm/snomed-ct/browser/{branch}/members",
                "Search for reference set ids",
                "to determine number of members on release branch prior to editing",
                generateGetMembersPayload(releaseBranchPath, refsetId));

        List<Concept> members = getMembers(refsetInternalId);
        assertThat(members.size()).isEqualTo(5);

        // Create Refset Branch as if this is first edit in current editing
        // cycle i.e., since last release/publication
        final String refsetBranchName = "refset-" + refsetId;

        callSnow("111 - POST", "https://dev-snowstorm.ihtsdotools.org/snowstorm/snomed-ct/branches",
                "createBranch", "to create the refset branch under the release branch",
                generateNewBranchPayload(refsetBranchName, releaseBranchPath));

        callSnow("222 - POST",
                "https://dev-snowstorm.ihtsdotools.org/snowstorm/snomed-ct/browser/{branch}/members",
                "Search for reference set ids",
                "to determine number of members on refset branch prior to editing",
                generateGetMembersPayload(refsetBranchName, refsetId));

        // Create Edit branch
        final String refsetBranchPath = releaseBranchPath + "/" + refsetBranchName;
        final String editBranchName = "edit";
        final String editBranchPath = refsetBranchPath + "/" + editBranchName;

        callSnow("333 - POST", "https://dev-snowstorm.ihtsdotools.org/snowstorm/snomed-ct/branches",
                "createBranch", "to create the edit branch under the refset branch",
                generateNewBranchPayload(editBranchName, refsetBranchPath));

        callSnow("444 - POST",
                "https://dev-snowstorm.ihtsdotools.org/snowstorm/snomed-ct/browser/{branch}/members",
                "Search for reference set ids",
                "to determine number of members on edit branch prior to editing",
                generateGetMembersPayload(editBranchPath, refsetId));

        // populate
        // TODO: Update RefsetMemberService.addMembers() to use editionBranch +
        callSnow("555 - ADD MEMBERS - POST",
                "https://dev-snowstorm.ihtsdotools.org/snowstorm/snomed-ct/{branch}/members",
                "Create a refseterence set member", "to create a member on the edit branch",
                generateAddMemberPayload(editBranchPath, "404684003", refsetId));

        callSnow("666 - POST",
                "https://dev-snowstorm.ihtsdotools.org/snowstorm/snomed-ct/browser/{branch}/members",
                "Search for reference set ids",
                "to determine number of members on edit branch after editing",
                generateGetMembersPayload(editBranchPath, refsetId));

        /* Mimic Ready for Review */
        // Merge/promote edit branch to review branch
        String comment = "The refset is at ready_for_review";

        callSnow("777 - POST",
                "https://dev-snowstorm.ihtsdotools.org/snowstorm/snomed-ct/browser/{branch}/members",
                "Search for reference set ids",
                "to determine number of members on refset branch prior to merging",
                generateGetMembersPayload(refsetBranchPath, refsetId));

        callSnow("888 - POST", "https://dev-snowstorm.ihtsdotools.org/snowstorm/snomed-ct/merges",
                "Perform a branch rebase or promotion",
                "to promote the content in the edit branch to the refset branch (with a merge comment: "
                        + comment + ")",
                generateMergePayload(editBranchPath, refsetBranchPath, comment));

        callSnow("999 - POST",
                "https://dev-snowstorm.ihtsdotools.org/snowstorm/snomed-ct/browser/{branch}/members",
                "Search for reference set ids",
                "to determine number of members on refset branch after merging",
                generateGetMembersPayload(refsetBranchPath, refsetId));

        // Delete edit branch - If/when the refset goes back into "IN_EDIT", a
        // new branch will be created for that specific edit
        comment =
                "The refset editing cycle is done. Delete refset branch from about-to-be-published release branch.";

        callSnow("AAA - DELETE",
                "https://dev-snowstorm.ihtsdotools.org/snowstorm/snomed-ct/admin/" + editBranchPath
                        + "/actions/hard-delete",
                "Hard delete a branch including its content and history",
                "to remove the edit branch. A new one will be created if more editing occurs.",
                generateDeletePayload(editBranchPath));

        /* Mimic Ready for Pub */
        // Merge review branch to code system branch
        // Merge/promote edit branch to review branch

        callSnow("BBB - POST",
                "https://dev-snowstorm.ihtsdotools.org/snowstorm/snomed-ct/browser/{branch}/members",
                "Search for reference set ids",
                "to determine number of members on release branch prior to merging",
                generateGetMembersPayload(releaseBranchPath, refsetId));

        comment = "The refset is at ready_for_publication";

        callSnow("CCC - POST", "https://dev-snowstorm.ihtsdotools.org/snowstorm/snomed-ct/merges",
                "Perform a branch rebase or promotion",
                "to promote the content in the edit branch to the refset branch (with a merge comment: "
                        + comment + ")",
                generateMergePayload(refsetBranchPath, releaseBranchPath, comment));

        callSnow("DDD - POST",
                "https://dev-snowstorm.ihtsdotools.org/snowstorm/snomed-ct/browser/{branch}/members",
                "Search for reference set ids",
                "to determine number of members on release branch after merging",
                generateGetMembersPayload(releaseBranchPath, refsetId));

        // Delete refset branch - This is done once we are notified that
        comment =
                "The refset editing cycle is done. Delete refset branch from about-to-be-published release branch.";

        callSnow("EEE - DELETE",
                "https://dev-snowstorm.ihtsdotools.org/snowstorm/snomed-ct/admin/"
                        + refsetBranchPath + "/actions/hard-delete",
                "Hard delete a branch including its content and history",
                "to remove the refset branch. A new one will be created during next edit cycle (after current one published).",
                generateDeletePayload(refsetBranchPath));
    }

    private String generateAddMemberPayload(String editBranchPath, String conceptId,
        String refsetId) {

        return "\nBranch: " + editBranchPath + "\n{\n" + " \"refsetId\": \"" + refsetId + "\",\n"
                + " \"referencedComponentId\": \"" + conceptId + "\"\n" + "}";
    }

    private String generateGetMembersPayload(String branch, String refsetId) {
        return "\nBranch: " + branch + " \nRefsetId: " + refsetId;
    }

    private String generateDeletePayload(String branchToDelete) {
        return "\nBranch to Delete: " + branchToDelete;
    }

    private String generateMergePayload(String fromBranch, String toBranch, String comment) {
        return "\n{\n" + " \"commitComment\": \"" + comment + "\",\n" + " \"source\": \""
                + fromBranch + "\",\n" + " \"target\": \"" + toBranch + "\"\n" + "}";

    }

    private String generateNewBranchPayload(String newBranch, String parentBranch) {
        return "\n{\n" + "  \"metadata\": {},\n" + "  \"name\": \"" + newBranch + "\",\n"
                + "  \"parent\": \"" + parentBranch + "\"\n" + "}";
    }

    private void callSnow(String url, String restType, String snowMethodDescription, String reason,
        String payload) throws Exception {
        logger.info(" Making " + restType + " call on method " + snowMethodDescription);
        logger.info("In order to be able to: " + reason);
        logger.info(" on URL  " + url);
        logger.info("   with payload: " + payload);
    }

    private List<Concept> getMembers(String refsetInternalId) throws Exception {
        MvcResult result = null;
        String content = null;

        logger.debug("  Find members for refset internal id: " + refsetInternalId);
        final String url =
                "/refset/" + refsetInternalId + "/members?limit=500&offset=0&displayType=list";
        logger.info("Testing url - " + url);

        result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        content = result.getResponse().getContentAsString();
        logger.info(" content = " + content);
        ConceptResultList members =
                new ObjectMapper().readValue(content, (ConceptResultList.class));

        // Testing Results
        assertThat(members).isNotNull();

        return members.getItems();
    }
}
