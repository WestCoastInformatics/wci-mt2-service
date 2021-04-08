
package org.ihtsdo.refsetservice.rest.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.ihtsdo.refsetservice.model.Concept;
import org.ihtsdo.refsetservice.test.BaseTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.json.JacksonTester;
import org.springframework.core.env.Environment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Integration tests for MetadataController.
 */
@AutoConfigureMockMvc
public class ConceptControllerTests extends BaseTest {

    /** The logger. */
    private static Logger logger = LoggerFactory.getLogger(ConceptControllerTests.class);

    /** The mvc. */
    @Autowired
    private MockMvc mvc;

    /** The object mapper. */
    private ObjectMapper objectMapper;

    /** The base url. */
    private String baseUrl = "";

    /** The env. */
    @Autowired
    private Environment env;

    /**
     * Sets the up.
     */
    @BeforeEach
    public void setUp() {

        objectMapper = new ObjectMapper();
        JacksonTester.initFields(this, objectMapper);
        baseUrl = "/concept";
    }

    /**
     * Test.
     *
     * @throws Exception the exception
     */
    @Test
    public void testConcept() throws Exception {
        String url = null;
        MvcResult result = null;
        String content = null;
        Concept concept = null;

        // Test with "by code"
        url = baseUrl + "/SNOMEDCT_US/404684003";
        logger.info("Testing url - " + url);
        result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        content = result.getResponse().getContentAsString();
        logger.info(" content = " + content);
        concept = new ObjectMapper().readValue(content, Concept.class);
        assertThat(concept).isNotNull();
        assertThat(concept.getCode()).isEqualTo("404684003");

    }

}
