
package org.ihtsdo.refsetservice.rest.test.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.text.SimpleDateFormat;
import java.util.Map;

import org.apache.lucene.queryparser.classic.QueryParserBase;
import org.ihtsdo.refsetservice.model.Concept;
import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.PfsParameter;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.model.TypeKeyValue;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.util.ConceptResultList;
import org.ihtsdo.refsetservice.util.ResultList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Integration tests for MetadataController.
 */

public class GetUnitTestUtilities {

    /** The logger. */
    private static Logger logger = LoggerFactory.getLogger(GetUnitTestUtilities.class);

    private MockMvc mvc;

    private String baseUrl;

    protected SimpleDateFormat sdf = null;

    public GetUnitTestUtilities(final MockMvc mvc, final String baseUrl, final SimpleDateFormat sdf) {

        this.mvc = mvc;
        this.baseUrl = baseUrl;
        this.sdf = sdf;
    }

    public String getRefsetInternalId(String refsetId, String version) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            final PfsParameter pfs = new PfsParameter();
            pfs.setSort("versionDate");
            pfs.setAscending(false);

            ResultList<Refset> refsets = service.find("refsetId:" + QueryParserBase.escape(refsetId) + "", pfs, Refset.class, null);

            assertThat(refsets.getItems().size()).isGreaterThan(0);

            Refset refsetToReturn = null;

            for (Refset refset : refsets.getItems()) {

                if (version.replaceAll("-", "").equals(sdf.format(refset.getVersionDate()))) {

                    refsetToReturn = refset;
                    break;
                }

            }

            if (refsetToReturn == null) {

                throw new Exception("Refset Id: " + refsetId + " does not exist in the RT2 database");
            }

            assertThat(refsetToReturn).isNotNull();
            assertThat(refsetToReturn.getRefsetId()).isEqualTo(refsetId);

            return refsetToReturn.getId();
        }

    }

    /**
     * Get the internal project ID based on the project's name
     * 
     * @param projectName The name of the project
     * @return the internal project ID
     * @throws Exception the exception
     */
    public String getProjectInternalId(String projectName) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            final PfsParameter pfs = new PfsParameter();

            ResultList<Project> projects = service.find("name:" + QueryParserBase.escape(projectName) + "", pfs, Project.class, null);

            if (projects.getItems().size() == 0) {

                throw new Exception("Refset Internal Id: " + projectName + " does not exist in the RT2 database");
            }

            Project project = projects.getItems().get(0);

            assertThat(project.getName()).isEqualTo(projectName);

            return project.getId();
        }

    }

    /**
     * Get the internal edition ID based on the edition's name
     * 
     * @param name The name of the edition
     * @return the internal edition ID
     * @throws Exception the exception
     */
    public String getEditionInternalId(String name) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            final PfsParameter pfs = new PfsParameter();

            ResultList<Edition> editions = service.find("name:" + QueryParserBase.escape(name) + "", pfs, Edition.class, null);

            if (editions.getItems().size() == 0) {

                throw new Exception("Refset Internal Id: " + name + " does not exist in the RT2 database");
            }

            Edition edition = editions.getItems().get(0);

            assertThat(edition.getName()).isEqualTo(name);

            return edition.getId();
        }

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

    public Refset getRefsetFromRefsetIdAndVersion(final String refsetId, final String version) {

        try {

            final String url = baseUrl + "/" + refsetId + "/versionDate/" + version;
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

    public Refset getRefsetFromInternalId(String refsetInternalId) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            final Refset refset = service.findSingle("id:" + QueryParserBase.escape(refsetInternalId), Refset.class, null);

            if (refset == null) {

                throw new Exception("Refset Id: " + refsetInternalId + " does not exist in the RT2 database");
            }

            assertThat(refset.getId()).isEqualTo(refsetInternalId);

            return refset;
        }

    }

    public ResultList<TypeKeyValue> getEditions() {

        try {

            final String url = baseUrl + "/editions";
            logger.info("Get Editions Testing url - " + url);

            final MvcResult result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
            final String content = result.getResponse().getContentAsString();
            logger.info(" content = " + content);

            final ResultList<TypeKeyValue> editions = new ObjectMapper().readValue(content, (new TypeReference<ResultList<TypeKeyValue>>() {
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
            ResultList<String> versions = new ObjectMapper().readValue(content, (new TypeReference<ResultList<String>>() {
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

            final ResultList<Project> projectList = new ObjectMapper().readValue(content, (new TypeReference<ResultList<Project>>() {
            }));

            assertThat(projectList).isNotNull();
            assertThat(projectList.getItems()).isNotEmpty();
            return projectList;
        } catch (Exception e) {

            e.printStackTrace();

            return null;
        }

    }

    public ResultList<Refset> searchDirectory(String searchTerm) {

        try {

            String url = baseUrl

                + "/search?searchConcepts=true&limit=500&offset=0&sort=versionDate&sortAscending=false&" + searchTerm;
            logger.info("Testing url - " + url);
            final MvcResult result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
            final String content = result.getResponse().getContentAsString();
            logger.info(" content = " + content);
            final ResultList<Refset> refsetList = new ObjectMapper().readValue(content, (new TypeReference<ResultList<Refset>>() {
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

    public ConceptResultList getRefsetConcepts(String branch, RefsetConceptsType refsetConceptType) {

        // For REST call, areParentConcepts variable true if returning for new
        // refset concepts, false for existing
        try {

            boolean areParentConceptsRequest = (refsetConceptType.equals(RefsetConceptsType.ALL_SIMPLE_TYPE_CONCEPTS)) ? true : false;

            final String url = "/general/refsetConcepts?branch=" + branch + "&areParentConcepts=" + areParentConceptsRequest;
            logger.info("Testing url - " + url);

            final MvcResult result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
            final String content = result.getResponse().getContentAsString();
            logger.info(" content = " + content);

            final ConceptResultList refsetConcepts = new ObjectMapper().readValue(content, ConceptResultList.class);
            assertThat(refsetConcepts.getItems()).isNotEmpty();
            return refsetConcepts;

        } catch (Exception e) {

            e.printStackTrace();

            return null;
        }

    }

    public ConceptResultList getMembers(String internalRefsetId) {

        try {

            final String url = "/refset/" + internalRefsetId + "/members?limit=100000&offset=0&displayType=list";
            logger.info("Testing url - " + url);

            final MvcResult result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
            final String content = result.getResponse().getContentAsString();
            logger.info(" content = " + content);
            final ConceptResultList members = new ObjectMapper().readValue(content, (ConceptResultList.class));

            // Testing Results
            assertThat(members).isNotNull();
            return members;

        } catch (Exception e) {

            e.printStackTrace();

            return null;
        }

    }

    public ConceptResultList searchMembers(String internalRefsetId, String searchTerm) {

        try {

            final String url = "/refset/" + internalRefsetId + "/members?limit=500&offset=0&query=" + searchTerm + "&displayType=list&editing=true";

            logger.info("Testing url - " + url);
            final MvcResult result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
            final String content = result.getResponse().getContentAsString();

            logger.info(" content = " + content);
            final ConceptResultList members = new ObjectMapper().readValue(content, (ConceptResultList.class));

            // Testing Results
            assertThat(members).isNotNull();
            return members;

        } catch (Exception e) {

            e.printStackTrace();

            return null;
        }

    }

    public ConceptResultList searchTaxonomy(String internalRefsetId, String searchTerm) {

        try {

            final String url = "/refset/" + internalRefsetId + "/taxonomySearch?limit=100000&offset=0&query=" + searchTerm;

            logger.info("Testing url - " + url);
            final MvcResult result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
            final String content = result.getResponse().getContentAsString();

            logger.info(" content = " + content);
            final ConceptResultList members = new ObjectMapper().readValue(content, (ConceptResultList.class));

            // Testing Results
            assertThat(members).isNotNull();
            return members;

        } catch (Exception e) {

            e.printStackTrace();

            return null;
        }

    }

    public ConceptResultList searchConcepts(String internalRefsetId, String searchTerm) {

        try {

            final String url = "/refset/" + internalRefsetId + "/conceptSearch?limit=500&offset=0&query=" + searchTerm;

            logger.info("Testing url - " + url);
            final MvcResult result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
            final String content = result.getResponse().getContentAsString();

            logger.info(" content = " + content);
            final ConceptResultList members = new ObjectMapper().readValue(content, (ConceptResultList.class));

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

            final String url = "/refset/" + internalRefsetId + "/members?limit=500&offset=0&displayType=taxonomy&startingConceptId=" + parentId + "&language=nl-X-31000172101";
            logger.info("Testing url - " + url);

            final MvcResult result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
            final String content = result.getResponse().getContentAsString();
            logger.info(" content = " + content);

            final ConceptResultList children = new ObjectMapper().readValue(content, (ConceptResultList.class));

            assertThat(children).isNotNull();
            assertThat(children.getItems()).isNotEmpty();
            return children;

        } catch (Exception e) {

            e.printStackTrace();

            return null;
        }

    }

    public ResultList<Map<String, String>> getMemberHistory(String internalRefsetId, String conceptId) {

        try {

            final String url = "/refset/" + internalRefsetId + "/member/" + conceptId;
            logger.info("Testing url - " + url);

            final MvcResult result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
            final String content = result.getResponse().getContentAsString();
            logger.info(" content = " + content);

            final ResultList<Map<String, String>> memberHistory = new ObjectMapper().readValue(content, (new TypeReference<ResultList<Map<String, String>>>() {
                /* NA */}));

            assertThat(memberHistory).isNotNull();
            assertThat(memberHistory.getTotal()).isEqualTo(2);
            return memberHistory;

        } catch (Exception e) {

            e.printStackTrace();

            return null;
        }

    }

    public Concept getAncestorPath(String conceptId, String internalRefsetId) {

        try {

            final String url = "/refset/" + internalRefsetId + "/member/" + conceptId + "/ancestorConcepts";
            logger.info("Testing url - " + url);

            final MvcResult result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
            final String content = result.getResponse().getContentAsString();
            logger.info(" content = " + content);

            Concept returnedconcept = new ObjectMapper().readValue(content, Concept.class);
            return returnedconcept;

        } catch (Exception e) {

            e.printStackTrace();

            return null;
        }

    }

    public boolean setupAncestorCache(final String refsetId, final String version) {

        try {

            final String url = "/ancestors/" + refsetId + "/versionDate/" + version;
            logger.info("Testing url - " + url);

            final MvcResult result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
            final String content = result.getResponse().getContentAsString();
            logger.info(" content = " + content);

            final JsonNode root = new ObjectMapper().readTree(content);
            final boolean success = root.get("success").asBoolean();

            return success;

        } catch (Exception e) {

            e.printStackTrace();

            return false;
        }

    }
}
