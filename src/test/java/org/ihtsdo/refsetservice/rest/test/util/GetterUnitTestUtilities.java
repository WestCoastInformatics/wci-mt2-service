
package org.ihtsdo.refsetservice.rest.test.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.ihtsdo.refsetservice.model.Concept;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.model.TypeKeyValue;
import org.ihtsdo.refsetservice.util.ConceptResultList;
import org.ihtsdo.refsetservice.util.ResultList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Integration tests for MetadataController.
 */

public class GetterUnitTestUtilities {

    /** The logger. */
    private static Logger logger = LoggerFactory.getLogger(GetterUnitTestUtilities.class);

    private MockMvc mvc;

    private String baseUrl;

    public GetterUnitTestUtilities(final MockMvc mvc, final String baseUrl) {
        this.mvc = mvc;
        this.baseUrl = baseUrl;
    }

    public Project getProject(final String projectId) {

        try {
            final String url = "/project/" + projectId;
            logger.info("Get Project Testing url - " + url);

            final MvcResult result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
            final String content = result.getResponse().getContentAsString();
            logger.info(" content = " + content);

            final Project project = new ObjectMapper().readValue(content, Project.class);

            assertThat(project).isNotNull();
            return project;
        } catch (Exception e) {
            e.printStackTrace();

            return null;
        }
    }

    public Refset getRefsetFromInternalId(final String internalRefsetId) {
        try {
            final String url = baseUrl + "/" + internalRefsetId;
            logger.info("Get Refset Testing url - " + url);

            final MvcResult result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
            final String content = result.getResponse().getContentAsString();
            logger.info(" content = " + content);

            final Refset refset = new ObjectMapper().readValue(content, Refset.class);

            assertThat(refset).isNotNull();
            return refset;
        } catch (Exception e) {
            e.printStackTrace();

            return null;
        }
    }

    public ResultList<TypeKeyValue> getEditions() {
        try {
            final String url = baseUrl + "/editions";
            logger.info("Get Editions Testing url - " + url);

            final MvcResult result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
            final String content = result.getResponse().getContentAsString();
            logger.info(" content = " + content);

            final ResultList<TypeKeyValue> editions = new ObjectMapper().readValue(content,
                    (new TypeReference<ResultList<TypeKeyValue>>() {
                        /* NA */}));

            assertThat(editions).isNotNull();
            return editions;
        } catch (Exception e) {
            e.printStackTrace();

            return null;
        }
    }

    public ResultList<String> getBranches(String codeSystem) {
        try {
            final String url = "/general/branchVersions?branch=MAIN/" + codeSystem;
            logger.info("Testing url - " + url);

            final MvcResult result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
            final String content = result.getResponse().getContentAsString();
            logger.info(" content = " + content);
            ResultList<String> versions =
                    new ObjectMapper().readValue(content, (new TypeReference<ResultList<String>>() {
                        /* NA */}));

            assertThat(versions).isNotNull();
            return versions;
        } catch (Exception e) {
            e.printStackTrace();

            return null;
        }
    }

    public ResultList<Project> searchProjects() {

        try {
            // Test full list
            final String url = "/project/search?limit=500&offset=0&sort=name&sortAscending=false";
            logger.info("Project Search Testing url - " + url);

            final MvcResult result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
            final String content = result.getResponse().getContentAsString();
            logger.info(" content = " + content);

            final ResultList<Project> projectList = new ObjectMapper().readValue(content,
                    (new TypeReference<ResultList<Project>>() {
                    }));

            assertThat(projectList).isNotNull();
            assertThat(projectList.getItems()).isNotEmpty();
            return projectList;
        } catch (Exception e) {
            e.printStackTrace();

            return null;
        }
    }

    public ResultList<Refset> searchDirectory(String string) {
        try {
            String url = baseUrl

                    + "/search?searchConcepts=true&limit=500&offset=0&sort=versionDate&sortAscending=false&query=name:animal";
            logger.info("Testing url - " + url);
            final MvcResult result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
            final String content = result.getResponse().getContentAsString();
            logger.info(" content = " + content);
            final ResultList<Refset> refsetList =
                    new ObjectMapper().readValue(content, (new TypeReference<ResultList<Refset>>() {
                    }));

            assertThat(refsetList).isNotNull();
            assertThat(refsetList.getItems()).isNotEmpty();
            return refsetList;
        } catch (Exception e) {
            e.printStackTrace();

            return null;
        }
    }

    public Concept getConceptDetails(String conceptId, String internalRefsetId) {
        try {
            final String url = "/concept/" + conceptId + "?refsetInternalId=" + internalRefsetId;
            logger.info("Testing url - " + url);

            final MvcResult result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
            final String content = result.getResponse().getContentAsString();
            logger.info(" content = " + content);

            Concept concept = new ObjectMapper().readValue(content, Concept.class);
            return concept;

        } catch (Exception e) {
            e.printStackTrace();

            return null;
        }
    }

    public ConceptResultList getRefsetConcepts(String branch,
        RefsetConceptsType refsetConceptType) {
        // For REST call, areParentConcepts variable true if returning for new
        // refset concepts, false for existing
        try {
            boolean areParentConceptsRequest =
                    (refsetConceptType.equals(RefsetConceptsType.NEW_REFSET_CONCEPTS)) ? true
                            : false;

            final String url = "/general/refsetConcepts?branch=" + branch + "&areParentConcepts="
                    + areParentConceptsRequest;
            logger.info("Testing url - " + url);

            final MvcResult result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
            final String content = result.getResponse().getContentAsString();
            logger.info(" content = " + content);

            final ConceptResultList refsetConcepts =
                    new ObjectMapper().readValue(content, ConceptResultList.class);
            assertThat(refsetConcepts.getItems()).isNotEmpty();
            return refsetConcepts;

        } catch (Exception e) {
            e.printStackTrace();

            return null;
        }
    }

    public ConceptResultList getMembers(String refsetId) {
        try {
            final String url =
                    "/refset/" + refsetId + "/members?limit=100000&offset=0&displayType=list";
            logger.info("Testing url - " + url);

            final MvcResult result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
            final String content = result.getResponse().getContentAsString();
            logger.info(" content = " + content);
            final ConceptResultList members =
                    new ObjectMapper().readValue(content, (ConceptResultList.class));

            // Testing Results
            assertThat(members).isNotNull();
            return members;

        } catch (Exception e) {
            e.printStackTrace();

            return null;
        }
    }

    public ConceptResultList searchMembers(String refsetId, String searchTerm) {
        try {
            final String url = "/refset/" + refsetId + "/members?limit=500&offset=0&query="
                    + searchTerm + "&displayType=list";

            logger.info("Testing url - " + url);
            final MvcResult result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
            final String content = result.getResponse().getContentAsString();

            logger.info(" content = " + content);
            final ConceptResultList members =
                    new ObjectMapper().readValue(content, (ConceptResultList.class));

            // Testing Results
            assertThat(members).isNotNull();
            return members;

        } catch (Exception e) {
            e.printStackTrace();

            return null;
        }
    }

    public ConceptResultList searchTaxonomy(String refsetId, String searchTerm) {
        try {
            final String url = "/refset/" + refsetId
                    + "/taxonomySearch?limit=100000&offset=0&query=" + searchTerm;

            logger.info("Testing url - " + url);
            final MvcResult result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
            final String content = result.getResponse().getContentAsString();

            logger.info(" content = " + content);
            final ConceptResultList members =
                    new ObjectMapper().readValue(content, (ConceptResultList.class));

            // Testing Results
            assertThat(members).isNotNull();
            return members;

        } catch (Exception e) {
            e.printStackTrace();

            return null;
        }
    }

    public ConceptResultList searchConcepts(String refsetId, String searchTerm) {
        try {
            final String url =
                    "/refset/" + refsetId + "/conceptSearch?limit=500&offset=0&query=" + searchTerm;

            logger.info("Testing url - " + url);
            final MvcResult result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
            final String content = result.getResponse().getContentAsString();

            logger.info(" content = " + content);
            final ConceptResultList members =
                    new ObjectMapper().readValue(content, (ConceptResultList.class));

            // Testing Results
            assertThat(members).isNotNull();
            assertThat(members.getItems()).isNotEmpty();
            return members;

        } catch (Exception e) {
            e.printStackTrace();

            return null;
        }
    }

    public ConceptResultList getChildren(String internalRefsetId, String parentId) {
        try {
            final String url = "/refset/" + internalRefsetId
                    + "/members?limit=500&offset=0&displayType=taxonomy&startingConceptId="
                    + parentId + "&language=nl-X-31000172101";
            logger.info("Testing url - " + url);

            final MvcResult result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
            final String content = result.getResponse().getContentAsString();
            logger.info(" content = " + content);

            final ConceptResultList children =
                    new ObjectMapper().readValue(content, (ConceptResultList.class));

            assertThat(children).isNotNull();
            assertThat(children.getItems()).isNotEmpty();
            return children;

        } catch (Exception e) {
            e.printStackTrace();

            return null;
        }
    }

    public ResultList<Map<String, String>> getMemberHistory(String internalRefsetId,
        String conceptId) {
        try {
            final String url = "/refset/" + internalRefsetId + "/member/" + conceptId;
            logger.info("Testing url - " + url);

            final MvcResult result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
            final String content = result.getResponse().getContentAsString();
            logger.info(" content = " + content);

            final ResultList<Map<String, String>> memberHistory = new ObjectMapper()
                    .readValue(content, (new TypeReference<ResultList<Map<String, String>>>() {
                        /* NA */}));

            assertThat(memberHistory).isNotNull();
            assertThat(memberHistory.getTotal()).isEqualTo(2);
            return memberHistory;

        } catch (Exception e) {
            e.printStackTrace();

            return null;
        }
    }
}
