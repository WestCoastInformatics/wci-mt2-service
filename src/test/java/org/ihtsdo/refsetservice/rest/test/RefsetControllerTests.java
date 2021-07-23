
package org.ihtsdo.refsetservice.rest.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.text.SimpleDateFormat;
import java.util.Map;

import org.apache.lucene.queryparser.classic.QueryParserBase;
import org.ihtsdo.refsetservice.model.Concept;
import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.PfsParameter;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.model.TypeKeyValue;
import org.ihtsdo.refsetservice.model.VersionStatus;
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
    private static final String TESTING_REFSET_ID = "551000172106"; // Belgian

    private static final String INACTIVE_CONCEPT_ID = "727156001";

    private static final String REFSET_WITH_INACTIVE_CONCEPT = "723264001";

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

        url = baseUrl + "˙?limit=10&offset=0&sort=versionDate&sortAscending=false&query=id:("
                + refsetTerminologyId + " OR 378ff7fb-0ec3-45a7-aab4-97b1ea2123cd)"; // Hyperdontia
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
        String refsetTerminologyId = "0b3133c4-7e27-4a12-88c2-58d2f5d612ee"; // getRefsetInternalId();

        url = baseUrl + "/" + refsetTerminologyId + "/members?limit=10&offset=0&displayType=list"; // 5a2f0f94-da88-4b20-a6b5-ca9990fbbc1f
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
        String refsetTerminologyId = "0b3133c4-7e27-4a12-88c2-58d2f5d612ee"; // getRefsetInternalId();

        url = baseUrl + "/" + refsetTerminologyId
                + "/members?limit=10&offset=0&displayType=taxonomy&startingConceptId=404684003"; // 5a2f0f94-da88-4b20-a6b5-ca9990fbbc1f
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
    @Test
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
     * Test exporting as RF2 Snapshot.
     *
     * @throws Exception the exception
     */
    @Test
    public void testExportRf2Snapshot() throws Exception {

        String url = null;
        MvcResult result = null;
        String resultString = null;
        final String refsetInternalId = getRefsetInternalId();
        final String format = "rf2_with_names";
        url = "/export/" + refsetInternalId + "/?format=" + format
                + "&exportMetadata=true&exportType=SNAPSHOT&fileNameDate=20200315&transientEffectiveTime=20200315&languageId=900000000000509007FSN";

        // final String refsetInternalId = getRefsetInternalId("723264001");
        // url = "/export/" + refsetInternalId
        // +
        // "/?format=rf2&exportMetadata=true&exportType=SNAPSHOT&fileNameDate=202010131&transientEffectiveTime=20210131";
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
     * Test exporting as RF2 Delta.
     *
     * @throws Exception the exception
     */
    @Test
    public void testExportRf2Delta() throws Exception {
        final String refsetId = "723264001"; // Lateralizable body refset
        String url = null;
        MvcResult result = null;
        String resultString = null;
        // Will default to the latest version of the refset (2021-07-31 for now)
        final String refsetInternalId = getRefsetInternalId(refsetId);

        url = "/export/" + refsetInternalId
                + "/?format=rf2&exportMetadata=true&exportType=DELTA&fileNameDate=20210131&transientEffectiveTime=20210731&startEffectiveTime=20200131";
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
        // TODO: Update test as was based on PROD-Snowstorm, not our dev
        // instance
        String url = null;
        MvcResult result = null;
        String content = null;

        // Test no failure when calling conceptDetails on inactive concept
        url = "/concept/" + INACTIVE_CONCEPT_ID + "?refsetInternalId="
                + getRefsetInternalId(REFSET_WITH_INACTIVE_CONCEPT);
        logger.info("Inactive Concept Testing url - " + url);

        result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        content = result.getResponse().getContentAsString();
        logger.info(" content = " + content);
        final Concept inactiveConcept = new ObjectMapper().readValue(content, Concept.class);
        assertThat(inactiveConcept).isNotNull();
        assertThat(inactiveConcept.getCode()).isEqualTo(INACTIVE_CONCEPT_ID);
        assertThat(inactiveConcept.getDescriptions().size()).isEqualTo(2);
        assertThat(inactiveConcept.getParents().size()).isEqualTo(0);
        assertThat(inactiveConcept.getChildren().size()).isEqualTo(0);

        // Test normal concept Details Call
        final String conceptId = "123976001"; // with 1 parent & 5 children & 1
                                              // role group of 4 rels
                                              // descriptions in all 3 lang
                                              // including Acceptable
        final String refsetId = "723264001";
        url = "/concept/" + conceptId + "?refsetInternalId=" + getRefsetInternalId(refsetId);
        logger.info("Testing url - " + url);

        result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        content = result.getResponse().getContentAsString();
        logger.info(" content = " + content);
        final Concept concept = new ObjectMapper().readValue(content, Concept.class);
        assertThat(concept).isNotNull();
        assertThat(concept.getCode()).isEqualTo(conceptId);
        assertThat(concept.getDescriptions().size()).isEqualTo(4);
        assertThat(concept.getRoleGroups().size()).isEqualTo(1);
        int groupId = concept.getRoleGroups().keySet().iterator().next();
        assertThat(concept.getRoleGroups().get(groupId).size()).isEqualTo(4);
        assertThat(concept.getParents().size()).isEqualTo(1);
        assertThat(concept.getChildren().size()).isEqualTo(5);

        // Test multiple descriptions of same type across 3 languages
        final String descriptionTestingConceptId = "276310004";
        final String descriptionTestingRefsetId = "561000172108"; // Belgian Refset to get all the translations as well
                                                               // as just need
                                                               // branch path
                                                               // for it
        url = "/concept/" + descriptionTestingConceptId + "?refsetInternalId="
                + getRefsetInternalId(descriptionTestingRefsetId);
        logger.info("Testing url - " + url);

        result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        content = result.getResponse().getContentAsString();
        logger.info(" content = " + content);
        final Concept descriptionTestingConcept =
                new ObjectMapper().readValue(content, Concept.class);
        assertThat(descriptionTestingConcept).isNotNull();
        assertThat(descriptionTestingConcept.getCode()).isEqualTo(descriptionTestingConceptId);
        logger.debug(descriptionTestingConcept.getDescriptions().toString());
        assertThat(descriptionTestingConcept.getDescriptions().size()).isEqualTo(7);
        assertThat(descriptionTestingConcept.getRoleGroups().size()).isEqualTo(1);
        int descriptionTestingGroupId =
                descriptionTestingConcept.getRoleGroups().keySet().iterator().next();
        assertThat(descriptionTestingConcept.getRoleGroups().get(descriptionTestingGroupId).size())
                .isEqualTo(4);
        assertThat(descriptionTestingConcept.getParents().size()).isEqualTo(1);
        assertThat(descriptionTestingConcept.getChildren().size()).isEqualTo(5);

    }

    @Test
    public void testExportFreeset() throws Exception {

        String url = null;
        MvcResult result = null;
        String resultString = null;
        final String refsetId = "787778008";
        final String refsetInternalId = getRefsetInternalId(refsetId);

        url = "/export/" + refsetInternalId + "/?format=free_set";
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
    public void testMemberList() throws Exception {
        // TODO: Update test as was based on PROD-Snowstorm, not our dev
        // instance

        String url = null;
        MvcResult result = null;
        String content = null;
        final String conceptIdToExamine = "716186003"; // with 1 parent & 5
                                                       // children & 1 role
                                                       // group of 4 rels
        // descriptions in all 3 lang
        final String refsetId = "561000172108";
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
     * Test searching refset members
     *
     * @throws Exception the exception
     */
    @Test
    public void testSearchRefsetMembers() throws Exception {
        // TODO: Update test as was based on PROD-Snowstorm, not our dev
        // instance

        String url = null;
        MvcResult result = null;
        String content = null;
        final String conceptIdToExamine = "429625007";

        // descriptions in all 3 lang
        final String refsetId = "561000172108";
        final String expectedEffectiveTime = "20210315";

        url = "/refset/" + getRefsetInternalId(refsetId)
                + "/members?limit=10&offset=0&query=food&displayType=list&refsetInternalId="
                + getRefsetInternalId(refsetId);
        logger.info("Testing url - " + url);

        result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        content = result.getResponse().getContentAsString();
        logger.info(" content = " + content);
        ConceptResultList members =
                new ObjectMapper().readValue(content, (ConceptResultList.class));

        // Testing Results
        assertThat(members).isNotNull();
        assertThat(members.size()).isEqualTo(1);

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
        assertThat(concept.getDescriptions().size()).isEqualTo(4);
        assertThat(concept.getRoleGroups().size()).isEqualTo(0);

        // Call does not pull in parents & Children
        assertThat(concept.getParents().size()).isEqualTo(0);
        assertThat(concept.getChildren().size()).isEqualTo(0);

    }
    
    /**
     * Test searching refset members taxonomy
     *
     * @throws Exception the exception
     */
    @Test
    public void testSearchRefsetTaxonomy() throws Exception {
        // TODO: Update test as was based on PROD-Snowstorm, not our dev
        // instance

        String url = null;
        MvcResult result = null;
        String content = null;
        final String conceptIdToExamine = "256248008";

        url = "/refset/" + getRefsetInternalId()
                + "/taxonomySearch?limit=10&offset=0&query=plant";
        logger.info("Testing url - " + url);

        result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        content = result.getResponse().getContentAsString();
        logger.info(" content = " + content);
        ConceptResultList members =
                new ObjectMapper().readValue(content, (ConceptResultList.class));

        // Testing Results
        assertThat(members).isNotNull();
        //assertThat(members.size()).isEqualTo(1);

        Concept concept = null;
        for (Concept conceptBeingTested : members.getItems()) {
            if (conceptBeingTested.getCode().equals(conceptIdToExamine)) {
                concept = conceptBeingTested;
                break;
            }
        }

        assertThat(concept).isNotNull();
        assertThat(concept.getCode()).isEqualTo(conceptIdToExamine);
        assertTrue(concept.isMemberOfRefset());
        assertTrue(concept.isMemberStatus());
        assertThat(concept.getDescriptions().size()).isEqualTo(4);

        assertThat(concept.getParents().size()).isGreaterThan(2);

    }

    /**
     * Test getting concept details.
     *
     * @throws Exception the exception
     */
    @Test
    public void testMemberTaxonomy() throws Exception {

        final String inactiveTestUrl =
                "/refset/" + getRefsetInternalId(REFSET_WITH_INACTIVE_CONCEPT)
                        + "/members?limit=10&offset=0&displayType=taxonomy&startingConceptId="
                        + INACTIVE_CONCEPT_ID + "&refsetInternalId="
                        + getRefsetInternalId(REFSET_WITH_INACTIVE_CONCEPT);
        logger.info("Testing url - " + inactiveTestUrl);

        final MvcResult inactiveResult =
                mvc.perform(get(inactiveTestUrl)).andExpect(status().isOk()).andReturn();
        final String inactiveContent = inactiveResult.getResponse().getContentAsString();
        logger.info(" content = " + inactiveContent);
        final ConceptResultList inactiveMembers =
                new ObjectMapper().readValue(inactiveContent, (ConceptResultList.class));

        // Testing Results
        assertThat(inactiveMembers).isNotNull();
        assertThat(inactiveMembers.size()).isEqualTo(0);

        // TODO: Update test as was based on PROD-Snowstorm, not our dev
        // instance

        // with 1 parent & 5 children & 1 role group of 4 rels
        // descriptions in all 3 lang
        final String conceptIdToExamine = "716220001";
        final String refsetId = "h";
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
        final String conceptIdToExamine = "727156001"; // "771410009";
        final String refsetId = "723264001"; // "723264001";

        final String url =
                "/refset/" + getRefsetInternalId(refsetId) + "/member/" + conceptIdToExamine;
        logger.info("Testing url - " + url);

        final MvcResult result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        final String content = result.getResponse().getContentAsString();
        logger.info(" content = " + content);

        ResultList<Map<String, String>> memberHistory = new ObjectMapper().readValue(content,
                (new TypeReference<ResultList<Map<String, String>>>() {
                    /* NA */}));

        assertThat(memberHistory).isNotNull();
        assertThat(memberHistory.getTotal()).isEqualTo(2);

        for (final Map<String, String> historyEntry : memberHistory.getItems()) {

            final String version = historyEntry.get("version");
            final String change = historyEntry.get("change");

            assertThat(version.equals("2017-07-31") || version.equals("2018-07-31"));

            if (version.equals("2018-07-31")) {
                assertThat(change.equals("Inactivated"));
            } else {
                assertThat(change.equals("Added"));
            }
        }
    }
    
    /**
     * Test the RTT Migration **** DO NOT CHECK THIS IN WITH @Test UNCOMMENTED.
     *
     * @throws Exception the exception
     */
    // **** DO NOT CHECK THIS IN WITH @Test UNCOMMENTED ****
    @Test
    public void testRttMigration() throws Exception {

        String url = null;
        MvcResult result = null;
        String content = null;

        url = "/admin/migration/rtt";
        logger.info("Testing url - " + url);
        result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        content = result.getResponse().getContentAsString();
        logger.info(" content = " + content);
        
        assertThat(content).isEqualTo("RTT data migration completed successfully");
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
    
    /**
     * Test getting list of version statuses.
     *
     * @throws Exception the exception
     */
    @Test
    public void testVersionStatuses() throws Exception {

        String url = null;
        MvcResult result = null;
        String content = null;
        Refset refset = null;

        url = baseUrl + "/versionStatuses";
        logger.info("Testing url - " + url);
        result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        content = result.getResponse().getContentAsString();
        logger.info(" content = " + content);
        ResultList<TypeKeyValue> versionStatuses = new ObjectMapper().readValue(content,
                (new TypeReference<ResultList<TypeKeyValue>>() {
                    /* NA */}));
        assertThat(versionStatuses).isNotNull();
        assertThat(versionStatuses.getTotal()).isEqualTo(VersionStatus.values().length);

    }
    
    /**
     * Test getting list of version statuses.
     *
     * @throws Exception the exception
     */
    @Test
    public void testVersions() throws Exception {

        String url = null;
        MvcResult result = null;
        String content = null;

        url = baseUrl + "/versions";
        logger.info("Testing url - " + url);
        result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        content = result.getResponse().getContentAsString();
        logger.info(" content = " + content);
        ResultList<TypeKeyValue> versions = new ObjectMapper().readValue(content,
                (new TypeReference<ResultList<TypeKeyValue>>() {
                    /* NA */}));
        assertThat(versions).isNotNull();
        assertThat(versions.getTotal()).isGreaterThan(5);

    }
    
    /**
     * Test getting editions.
     *
     * @throws Exception the exception
     */
    @Test
    public void testEditions() throws Exception {

        String url = null;
        MvcResult result = null;
        String content = null;

        url = baseUrl + "/editions";
        logger.info("Testing url - " + url);
        result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        content = result.getResponse().getContentAsString();
        logger.info(" content = " + content);
        ResultList<TypeKeyValue> editions = new ObjectMapper().readValue(content,
                (new TypeReference<ResultList<TypeKeyValue>>() {
                    /* NA */}));
        assertThat(editions).isNotNull();
        assertThat(editions.getItems().size()).isGreaterThan(8);

    }

}
