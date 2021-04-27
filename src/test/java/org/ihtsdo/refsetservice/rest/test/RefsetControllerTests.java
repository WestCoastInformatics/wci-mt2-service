
package org.ihtsdo.refsetservice.rest.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.apache.lucene.queryparser.classic.QueryParserBase;
import org.ihtsdo.refsetservice.model.PfsParameter;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.service.TerminologyService;
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

    /** The Constant TESTING_REFSET_ID. */
    private static final String TESTING_REFSET_ID = "551000172106"; // this code works for sure: "551000172106";

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
        
        String refsetTerminologyId = null;

        try (final TerminologyService service = new TerminologyService()) {
            
            ResultList<Refset> refsets= service.find(
                    "refsetId:" + QueryParserBase.escape(TESTING_REFSET_ID) + "", new PfsParameter(0, 1), Refset.class, null);
            
            assertThat(refsets.getItems().size()).isGreaterThan(0);
            
            refset = refsets.getItems().get(0);
            assertThat(refset).isNotNull();
            
            refsetTerminologyId = refset.getId();
        }

        url = baseUrl + "/" + refsetTerminologyId;
        logger.info("Testing url - " + url);
        result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        content = result.getResponse().getContentAsString();
        logger.info(" content = " + content);
        refset = new ObjectMapper().readValue(content, Refset.class);
        assertThat(refset).isNotNull();
        assertThat(refset.getRefsetId()).isEqualTo(TESTING_REFSET_ID);

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
        String refsetTerminologyId = null;
        
        try (final TerminologyService service = new TerminologyService()) {
            
            ResultList<Refset> refsets= service.find(
                    "refsetId:" + QueryParserBase.escape(TESTING_REFSET_ID) + "", new PfsParameter(0, 1), Refset.class, null);
            
            assertThat(refsets.getItems().size()).isGreaterThan(0);
            
            Refset refset = refsets.getItems().get(0);
            assertThat(refset).isNotNull();
            assertThat(refset.getRefsetId()).isEqualTo(TESTING_REFSET_ID);
            
            refsetTerminologyId = refset.getId();
        }

        url = baseUrl + "/" + refsetTerminologyId + "/members?limit=10&offset=0"; // 5a2f0f94-da88-4b20-a6b5-ca9990fbbc1f
        logger.info("Testing url - " + url);
        result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        content = result.getResponse().getContentAsString();
        logger.info(" content = " + content);
        members = new ObjectMapper().readValue(content, (ConceptResultList.class));
        assertThat(members).isNotNull();
        assertThat(members.getItems().size()).isGreaterThan(0);
        assertThat(members.getItems().get(0).getDescriptions().size()).isGreaterThan(0);

        logger.info("Done -- Just returned refset with " + members.size() + " members.");
    }

}
