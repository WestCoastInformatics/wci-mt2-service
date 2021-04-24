
package org.ihtsdo.refsetservice.rest.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.ihtsdo.refsetservice.model.Concept;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.test.BaseTest;
import org.ihtsdo.refsetservice.util.ConceptResultList;
import org.ihtsdo.refsetservice.util.ResultList;
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
public class RefsetControllerTests extends BaseTest {

    /** The logger. */
    private static Logger logger = LoggerFactory.getLogger(RefsetControllerTests.class);

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
        baseUrl = "/refset";
    }

    /**
     * Test getting a refset.
     *
     * @throws Exception the exception
     */
    @Test
    public void testRefset() throws Exception {
        
        String url = null;
        MvcResult result = null;
        String content = null;
        Refset refset = null;

        url = baseUrl + "/d11dfa11-d2c2-4f6d-a85a-32071fd49c42";
        logger.info("Testing url - " + url);
        result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        content = result.getResponse().getContentAsString();
        logger.info(" content = " + content);
        refset = new ObjectMapper().readValue(content, Refset.class);
        assertThat(refset).isNotNull();
        assertThat(refset.getRefsetId()).isEqualTo("001");

    }
    
    /**
     * Test getting the member concepts of a refset.
     *
     * @throws Exception the exception
     */
    @Test
    public void testRefsetMembers() throws Exception {
        
        String url = null;
        MvcResult result = null;
        String content = null;
        ConceptResultList members = null;

        url = baseUrl + "/7626e1f3-60ef-4cb4-a900-7905d4897a20/members?limit=10&offset=0"; //5a2f0f94-da88-4b20-a6b5-ca9990fbbc1f
        logger.info("Testing url - " + url);
        result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        content = result.getResponse().getContentAsString();
        logger.info(" content = " + content);
        members = new ObjectMapper().readValue(content, (ConceptResultList.class));
        assertThat(members).isNotNull();
        assertThat(members.getItems().size()).isGreaterThan(0);
        assertThat(members.getItems().get(0).getCode()).isEqualTo("162290004");
        assertThat(members.getItems().get(0).getDescriptions().size()).isGreaterThan(0);
        assertThat(members.getItems().get(0).getDescriptions().get(0).get("term")).isEqualTo("Dry eyes");

    }

}
