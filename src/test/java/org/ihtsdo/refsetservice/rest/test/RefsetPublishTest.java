
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
                testingEditionId = getEditionInternalId(TESTING_EDITION_NAME);
                testingProjectId = getProjectInternalId(TESTING_PROJECT_NAME);

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
        String releaseBranch = null;
        String refsetInternalId = null;

        // verify the new version
        try (final TerminologyService service = new TerminologyService()) {
            refsetInternalId = getRefsetInternalId(refsetId, versionDate);
            Refset refset = service.get(refsetInternalId, Refset.class);
            assertThat(refset).isNotNull();
            releaseBranch = refset.getEdition().getBranch();

            logger.debug("Have internalId: " + refsetInternalId + " and branch: " + releaseBranch);
        } catch (Exception e) {
            throw e;
        }

        
        
        
        
        /* Setup Edit Cycle */
        List<Concept> members = getMembers(refsetInternalId);
        assertThat(members.size()).isEqualTo(5);

        // Create Refset Branch (with assumption that refset branch doesn't
        // exist)
        final String refsetBranchName = "refset-" + refsetId;
        String payload = "{\n" + "  \"metadata\": {},\n" + "  \"name\": " + refsetBranchName + ",\n"
                + "  \"parent\": " + releaseBranch + "\n" + "}";

        callSnow("POST", "https://dev-snowstorm.ihtsdotools.org/snowstorm/snomed-ct/branches",
                "createBranch", "to create the refset branch under the release branch", payload);

        // Create Edit branch
        final String refsetBranchPath = releaseBranch + "/" + refsetBranchName;
        final String editBranchName = "edit";
        payload = "{\n" + "  \"metadata\": {},\n" + "  \"name\": " + editBranchName + ",\n"
                + "  \"parent\": " + refsetBranchPath + "\n" + "}";

        callSnow("POST", "https://dev-snowstorm.ihtsdotools.org/snowstorm/snomed-ct/branches",
                "createBranch", "to create the edit branch under the refset branch", payload);

        
        
        
        
        /* Mimic Edit */
        // populate
        // TODO: Update RefsetMemberService.addMembers() to use editionBranch +
        // /refset-<refsetId> + /edit
        // addMember("48176007,280416009,10828004,260385009");
        // removeMember("63401000052101");

        
        
        
        
        /* Mimic Ready for Review */
        // Merge/promote edit branch to review branch
        String comment = "The refset is at ready_for_review";
        final String editBranchPath = refsetBranchPath + "/edit";

        payload = "{\n" + " \"commitComment" + comment + ",\n" + " \"reviewId\": \"string\",\n"
                + " \"source\": " + editBranchPath + ",\n" + " \"target\": " + refsetBranchPath
                + "\n" + "}";

        callSnow("POST", "https://dev-snowstorm.ihtsdotools.org/snowstorm/snomed-ct/merges",
                "Perform a branch rebase or promotion",
                "to promote the content in the edit branch to the refset branch", payload);

        // Delete edit branch
        callSnow("DELETE",
                "https://dev-snowstorm.ihtsdotools.org/snowstorm/snomed-ct/admin/" + editBranchPath
                        + "/actions/hard-delete",
                "Hard delete a branch including its content and history",
                "to remove the edit branch. A new one will be created if more editing occurs.", payload);

        
        
        
        
        /* Mimic Ready for Pub */
        // Merge review branch to code system branch
        // Merge/promote edit branch to review branch
        comment = "The refset is at ready_for_publication";
        payload = "{\n" + " \"commitComment" + comment + ",\n" + " \"reviewId\": \"string\",\n"
                + " \"source\": " + refsetBranchPath + ",\n" + " \"target\": " + releaseBranch
                + "\n" + "}";

        callSnow("POST", "https://dev-snowstorm.ihtsdotools.org/snowstorm/snomed-ct/merges",
                "Perform a branch rebase or promotion",
                "to remove the refset branch. A new one will be created if RVF fails.", payload);

        // Delete refset branch
        callSnow("DELETE",
                "https://dev-snowstorm.ihtsdotools.org/snowstorm/snomed-ct/admin/" + refsetBranchPath
                        + "/actions/hard-delete",
                "Hard delete a branch including its content and history",
                "to promote the content in the edit branch to the refset branch", payload);



        // Remove refset membership
        // removeMember("48176007,280416009,10828004,260385009");
        // addMember("63401000052101");
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
