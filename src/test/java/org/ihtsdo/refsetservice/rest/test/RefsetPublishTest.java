
package org.ihtsdo.refsetservice.rest.test;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.json.JacksonTester;

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
    public void testAllRolesAndInitialStates() throws Exception {
    }
}
