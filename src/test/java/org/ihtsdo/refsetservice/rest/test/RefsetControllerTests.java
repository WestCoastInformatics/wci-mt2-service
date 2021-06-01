
package org.ihtsdo.refsetservice.rest.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.text.SimpleDateFormat;
import java.util.Map;

import org.apache.lucene.queryparser.classic.QueryParserBase;
import org.ihtsdo.refsetservice.model.Concept;
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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Integration tests for MetadataController.
 */

@AutoConfigureMockMvc
public class RefsetControllerTests extends BaseTest {

    /** The Constant TESTING_REFSET_ID. */
    // Body temperature refset with 10 members
    private static final String TESTING_REFSET_ID = "551000172106"; // this code
                                                                    // works for
                                                                    // sure:
                                                                    // "551000172106"

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
        String refsetTerminologyId = getRefsetInternalId();

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
     * Test listing refsets.
     *
     * @throws Exception the exception
     */
    @Test
    public void testRefsetDirectory() throws Exception {

        String url = null;
        MvcResult result = null;
        String content = null;
        ResultList<Refset> resultList = null;
        String refsetTerminologyId = getRefsetInternalId(); // "091f9238-3083-4e60-9e70-b011c97980c3"

        url = baseUrl
                + "/search?limit=10&offset=1&sort=versionDate&sortAscending=false&query=refsetId:"
                + refsetTerminologyId; // (refsetId:(447562003 OR
                                       // 900000000000497000 OR 733073007 OR
                                       // 721144007 OR 721145008))"; //id:(" +
                                       // refsetTerminologyId + " OR
                                       // 8357399a-f1c2-43a1-9d91-1c3fb08dd997)
                                       // AND Hyperdontia"; //Hyperdontia
        logger.info("Testing url - " + url);
        result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        content = result.getResponse().getContentAsString();
        logger.info(" content = " + content);
        resultList =
                new ObjectMapper().readValue(content, (new TypeReference<ResultList<Refset>>() {
                    /* NA */}));
        assertThat(resultList).isNotNull();
        assertThat(resultList.getItems().size()).isGreaterThan(2);
        assertThat(resultList.getItems().get(0).getRefsetId()).isEqualTo(TESTING_REFSET_ID);

    }

    /**
     * Test getting the member concepts of a refset.
     *
     * @throws Exception the exception
     */
    @Test
    public void testRefsetMemberList() throws Exception {

        String url = null;
        MvcResult result = null;
        String content = null;
        ConceptResultList members = null;
        String refsetTerminologyId = getRefsetInternalId();

        url = baseUrl + "/" + refsetTerminologyId + "/members?limit=10&offset=2&displayType=list"; // 5a2f0f94-da88-4b20-a6b5-ca9990fbbc1f
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

    /**
     * Test getting the member concepts of a refset.
     *
     * @throws Exception the exception
     */
    @Test
    public void testRefsetDetailsTaxonomy() throws Exception {

        String url = null;
        MvcResult result = null;
        String content = null;
        ConceptResultList children = null;
        String refsetTerminologyId = getRefsetInternalId();

        url = baseUrl + "/" + refsetTerminologyId
                + "/members?limit=10&offset=2&displayType=taxonomy&startingConceptId=404684003"; // 5a2f0f94-da88-4b20-a6b5-ca9990fbbc1f
        logger.info("Testing url - " + url);
        result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        content = result.getResponse().getContentAsString();
        logger.info(" content = " + content);
        children = new ObjectMapper().readValue(content, (ConceptResultList.class));
        assertThat(children).isNotNull();
        assertThat(children.getItems().size()).isGreaterThan(0);
        assertThat(children.getItems().get(0).getDescriptions().size()).isGreaterThan(0);

        logger.info("Done -- Just returned refset with " + children.size() + " members.");
    }

    /**
     * Test exporting a refset SCTID list.
     *
     * @throws Exception the exception
     */
    // @Test
    public void testExportSctidList() throws Exception {

        String url = null;
        MvcResult result = null;
        String resultString = null;
        final String refsetInternalId = getRefsetInternalId();

        url = "/export/" + refsetInternalId + "/?format=sctids&exportMetadata=true";
        logger.info("Testing url - " + url);

        result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        resultString = result.getResponse().getContentAsString();

        final ObjectMapper mapper = new ObjectMapper();
        final JsonNode root = mapper.readTree(resultString);
        final String fileUrl = (root.get("url")).asText();
        logger.info("File Url: " + fileUrl);

        assertThat(fileUrl).isNotNull();

    }

    /**
     * Test exporting a refset SCTID list.
     *
     * @throws Exception the exception
     */
    @Test
    public void testExportRf2() throws Exception {

        String url = null;
        MvcResult result = null;
        String resultString = null;
        final String refsetInternalId = getRefsetInternalId();

        url = "/export/" + refsetInternalId
                + "/?format=rf2&exportMetadata=true&exportType=SNAPSHOT&fileNameDate=20210315&transientEffectiveTime=20210315";
        logger.info("Testing url - " + url);

        result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        resultString = result.getResponse().getContentAsString();

        final ObjectMapper mapper = new ObjectMapper();
        final JsonNode root = mapper.readTree(resultString);
        final String fileUrl = (root.get("url")).asText();
        logger.info("File Url: " + fileUrl);

        assertThat(fileUrl).isNotNull();

    }

    /**
     * Test getting concept details.
     *
     * @throws Exception the exception
     */
    @Test
    public void testConceptDetails() throws Exception {

        String url = null;
        MvcResult result = null;
        String content = null;
        final String conceptId = "716186003"; // with 1 parent & 5 children & 1
                                              // role group of 4 rels
                                              // descriptions in all 3 lang
                                              // including Acceptable
        final String refsetId = "741000172102";
        url = "/concept/" + conceptId + "?refsetInternalId=" + getRefsetInternalId(refsetId);
        logger.info("Testing url - " + url);

        result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        content = result.getResponse().getContentAsString();
        logger.info(" content = " + content);
        final Concept concept = new ObjectMapper().readValue(content, Concept.class);
        assertThat(concept).isNotNull();
        assertThat(concept.getCode()).isEqualTo(conceptId);
        assertThat(concept.getDescriptions().size()).isEqualTo(5);
        assertThat(concept.getRoleGroups().size()).isEqualTo(1);
        int groupId = concept.getRoleGroups().keySet().iterator().next();
        assertThat(concept.getRoleGroups().get(groupId).keySet().size()).isEqualTo(4);
        assertThat(concept.getParents().size()).isEqualTo(1);
        assertThat(concept.getChildren().size()).isEqualTo(5);

    }

    /**
     * Test getting concept details.
     *
     * @throws Exception the exception
     */
    @Test
    public void testMemberList() throws Exception {

        String url = null;
        MvcResult result = null;
        String content = null;
        final String conceptIdToExamine = "716186003"; // with 1 parent & 5
                                                       // children & 1 role
                                                       // group of 4 rels
        // descriptions in all 3 lang
        final String refsetId = "741000172102";
        final String expectedEffectiveTime = "20210315";

        url = "/refset/" + getRefsetInternalId(refsetId)
                + "/members?limit=10&offset=0&displayType=list&refsetInternalId="
                + getRefsetInternalId(refsetId);
        logger.info("Testing url - " + url);

        result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        content = result.getResponse().getContentAsString();
        logger.info(" content = " + content);
        ConceptResultList members =
                new ObjectMapper().readValue(content, (ConceptResultList.class));

        // Testing Results
        assertThat(members).isNotNull();
        assertThat(members.size()).isEqualTo(7);

        Concept concept = null;
        for (Concept conceptBeingTested : members.getItems()) {
            if (conceptBeingTested.getCode().equals(conceptIdToExamine)) {
                concept = conceptBeingTested;
                break;
            }
        }

        assertThat(concept).isNotNull();
        assertThat(concept.getCode()).isEqualTo(conceptIdToExamine);

        final SimpleDateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat("yyyyMMdd");
        assertThat(concept.getMemberEffectiveTime())
                .isEqualTo(SIMPLE_DATE_FORMAT.parseObject(expectedEffectiveTime));
        assertTrue(concept.isMemberOfRefset());
        assertTrue(concept.isMemberStatus());
        assertThat(concept.getDescriptions().size()).isEqualTo(4); // The 5th
                                                                   // active
                                                                   // description
                                                                   // is
                                                                   // Acceptable,
                                                                   // so
                                                                   // shouldn't
                                                                   // be
                                                                   // returned
        assertThat(concept.getRoleGroups().size()).isEqualTo(0);

        // Call does not pull in parents & Children
        assertThat(concept.getParents().size()).isEqualTo(0);
        assertThat(concept.getChildren().size()).isEqualTo(0);

    }

    /**
     * Test getting concept details.
     *
     * @throws Exception the exception
     */
    @Test
    public void testMemberTaxonomy() throws Exception {

        // with 1 parent & 5 children & 1 role group of 4 rels
        // descriptions in all 3 lang
        final String conceptIdToExamine = "716220001";
        final String refsetId = "741000172102";
        final String expectedEffectiveTime = "20210315";
        final String startingConceptId = "716186003";

        final String url = "/refset/" + getRefsetInternalId(refsetId)
                + "/members?limit=10&offset=0&displayType=taxonomy&startingConceptId="
                + startingConceptId + "&refsetInternalId=" + getRefsetInternalId(refsetId);
        logger.info("Testing url - " + url);

        final MvcResult result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        final String content = result.getResponse().getContentAsString();
        logger.info(" content = " + content);
        final ConceptResultList members =
                new ObjectMapper().readValue(content, (ConceptResultList.class));

        // Testing Results
        assertThat(members).isNotNull();
        assertThat(members.size()).isEqualTo(5);

        Concept concept = null;
        for (Concept conceptBeingTested : members.getItems()) {
            logger.info("Concept: " + conceptBeingTested.getName());
            if (conceptBeingTested.getCode().equals(conceptIdToExamine)) {
                concept = conceptBeingTested;
            }
        }

        assertThat(concept).isNotNull();
        assertThat(concept.getCode()).isEqualTo(conceptIdToExamine);

        final SimpleDateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat("yyyyMMdd");
        assertThat(concept.getMemberEffectiveTime())
                .isEqualTo(SIMPLE_DATE_FORMAT.parse(expectedEffectiveTime));
        assertTrue(concept.isMemberOfRefset());
        assertTrue(concept.isMemberStatus());
        assertThat(concept.getDescriptions().size()).isEqualTo(4); // The 5th
                                                                   // active
                                                                   // description
                                                                   // is
                                                                   // Acceptable,
                                                                   // so
                                                                   // shouldn't
                                                                   // be
                                                                   // returned
        assertThat(concept.getRoleGroups().size()).isEqualTo(0);

        // Call does not pull in parents & Children
        assertTrue(concept.getHasChildren());
        assertThat(concept.getParents().size()).isEqualTo(0);
        assertThat(concept.getChildren().size()).isEqualTo(0);

    }

    /**
     * Test getting concept details.
     *
     * @throws Exception the exception
     */
    @Test
    public void testMemberHistory() throws Exception {

        // with 1 parent & 5 children & 1 role group of 4 rels
        // descriptions in all 3 lang
        final String conceptIdToExamine = "495160018";
        final String refsetId = "900000000000490003";

        final String url = "/refset/" + getRefsetInternalId(refsetId) + "/history?conceptId="
                + conceptIdToExamine;
        logger.info("Testing url - " + url);

        final MvcResult result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        final String content = result.getResponse().getContentAsString();
        logger.info(" content = " + content);

        Map<String, Boolean> versionedMembership =
                new ObjectMapper().readValue(content, (new TypeReference<Map<String, Boolean>>() {
                    /* NA */}));

        assertThat(versionedMembership).isNotNull();
        for (String version : versionedMembership.keySet()) {
            logger.info(
                    "Version: " + version + " with status: " + versionedMembership.get(version));
        }
    }

    /**
     * Get the internal refset ID based on the refset's terminology specific ID
     * .
     *
     * @return the internal refset ID
     * @throws Exception the exception
     */
    private String getRefsetInternalId() throws Exception {
        return getRefsetInternalId(TESTING_REFSET_ID);
    }

    private String getRefsetInternalId(String requestedId) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            final PfsParameter pfs = new PfsParameter();
            pfs.setLimit(1);
            pfs.setSort("versionDate");
            pfs.setAscending(false);

            ResultList<Refset> refsets =
                    service.find("refsetId:" + QueryParserBase.escape(requestedId) + "", pfs,
                            Refset.class, null);

            assertThat(refsets.getItems().size()).isGreaterThan(0);

            Refset refset = refsets.getItems().get(0);
            assertThat(refset).isNotNull();
            assertThat(refset.getRefsetId()).isEqualTo(requestedId);

            return refset.getId();
        }
    }
}
