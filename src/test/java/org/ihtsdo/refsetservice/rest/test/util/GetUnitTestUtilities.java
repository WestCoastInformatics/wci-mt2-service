/*
 * Copyright 2023 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */

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
import org.ihtsdo.refsetservice.model.ResultListConcept;
import org.ihtsdo.refsetservice.model.TypeKeyValue;
import org.ihtsdo.refsetservice.model.enums.VersionStatus;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.util.ResultList;
import org.ihtsdo.refsetservice.util.ThreadLocalMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;

// TODO: Auto-generated Javadoc
/**
 * Integration tests for MetadataController.
 */

public class GetUnitTestUtilities {

	/** The Constant LOG. */
	private static final Logger LOG = LoggerFactory.getLogger(GetUnitTestUtilities.class);

	/** The mvc. */
	private MockMvc mvc;

	/** The base url. */
	private String baseUrl;

	/** The sdf. */
	private SimpleDateFormat sdf = null;

	/**
	 * Instantiates a {@link GetUnitTestUtilities} from the specified parameters.
	 *
	 * @param mvc     the mvc
	 * @param baseUrl the base url
	 * @param sdf     the sdf
	 */
	public GetUnitTestUtilities(final MockMvc mvc, final String baseUrl, final SimpleDateFormat sdf) {

		this.mvc = mvc;
		this.baseUrl = baseUrl;
		this.sdf = sdf;
	}

	/**
	 * Returns the internal refset id.
	 *
	 * @param refsetId the refset id
	 * @param version  the version
	 * @return the internal refset id
	 * @throws Exception the exception
	 */
	public String getInternalRefsetId(final String refsetId, final String version) throws Exception {

		try (final TerminologyService service = new TerminologyService()) {

			final PfsParameter pfs = new PfsParameter();
			pfs.setSort("versionDate");
			pfs.setAscending(false);

			final ResultList<Refset> refsets = service.find("refsetId:" + QueryParserBase.escape(refsetId) + "", pfs,
					Refset.class, null);

			assertThat(refsets.getItems().size()).isGreaterThan(0);

			Refset refsetToReturn = null;

			for (final Refset refset : refsets.getItems()) {

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
	 * Get the internal project ID based on the project's name.
	 *
	 * @param projectName The name of the project
	 * @return the internal project ID
	 * @throws Exception the exception
	 */
	public String getInternalProjectId(final String projectName) throws Exception {

		try (final TerminologyService service = new TerminologyService()) {

			final PfsParameter pfs = new PfsParameter();

			final ResultList<Project> projects = service.find("name:" + QueryParserBase.escape(projectName) + "", pfs,
					Project.class, null);

			if (projects.getItems().size() == 0) {

				throw new Exception("Refset Internal Id: " + projectName + " does not exist in the RT2 database");
			}

			final Project project = projects.getItems().get(0);

			assertThat(project.getName()).isEqualTo(projectName);

			return project.getId();
		}

	}

	/**
	 * Get the internal edition ID based on the edition's name.
	 *
	 * @param name The name of the edition
	 * @return the internal edition ID
	 * @throws Exception the exception
	 */
	public String getInternalEditionId(final String name) throws Exception {

		try (final TerminologyService service = new TerminologyService()) {

			final PfsParameter pfs = new PfsParameter();

			final ResultList<Edition> editions = service.find("name:" + QueryParserBase.escape(name) + "", pfs,
					Edition.class, null);

			if (editions.getItems().size() == 0) {

				throw new Exception("Refset Internal Id: " + name + " does not exist in the RT2 database");
			}

			final Edition edition = editions.getItems().get(0);

			assertThat(edition.getName()).isEqualTo(name);

			return edition.getId();
		}

	}

	/**
	 * Returns the project.
	 *
	 * @param projectId the project id
	 * @return the project
	 */
	public Project getProject(final String projectId) {

		try {

			final String url = "/project/" + projectId;
			LOG.info("Get Project Testing url - " + url);

			final MvcResult result = mvc
					.perform(get(url).contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
					.andExpect(status().isOk()).andReturn();
			final String content = result.getResponse().getContentAsString();
			LOG.info(" content = " + content);

			final Project project = ThreadLocalMapper.get().readValue(content, Project.class);

			assertThat(project).isNotNull();
			return project;
		} catch (final Exception e) {

			e.printStackTrace();

			return null;
		}

	}


	/**
	 * Gets the refset from refset id and version.
	 *
	 * @param refsetId the refset id
	 * @param versionDate the version date
	 * @return the refset from refset id and version
	 */
	public Refset getRefsetFromRefsetIdAndVersion(final String refsetId, final String versionDate) {

		try {

			final String url = baseUrl + "/" + refsetId + "/versionDate/" + versionDate;
			LOG.info("Get Refset Testing url - " + url);

			final MvcResult result = mvc
					.perform(get(url).contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
					.andExpect(status().isOk()).andReturn();
			final String content = result.getResponse().getContentAsString();
			LOG.info(" content = " + content);

			final Refset refset = ThreadLocalMapper.get().readValue(content, Refset.class);

			assertThat(refset).isNotNull();
			return refset;
		} catch (final Exception e) {

			e.printStackTrace();

			return null;
		}

	}
	
    /**
     * Gets the refset from refset id and version.
     *
     * @param refsetId the refset id
     * @param versionStatus the version status
     * @return the refset from refset id and version
     */
    public Refset getRefsetFromRefsetIdAndVersion(final String refsetId, final VersionStatus versionStatus) {

        try {

            final String url = baseUrl + "/" + refsetId + "/versionDate/" + versionStatus.getLabel();
            LOG.info("Get Refset Testing url - " + url);

            final MvcResult result = mvc
                    .perform(get(url).contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk()).andReturn();
            final String content = result.getResponse().getContentAsString();
            LOG.info(" content = " + content);

            final Refset refset = ThreadLocalMapper.get().readValue(content, Refset.class);

            assertThat(refset).isNotNull();
            return refset;
        } catch (final Exception e) {

            e.printStackTrace();

            return null;
        }

    }

	/**
	 * Returns the refset from internal id.
	 *
	 * @param interalRefsetId the interal refset id
	 * @return the refset from internal id
	 * @throws Exception the exception
	 */
	public Refset getRefsetFromInternalId(final String interalRefsetId) throws Exception {

		try (final TerminologyService service = new TerminologyService()) {

			final Refset refset = service.findSingle("id:" + QueryParserBase.escape(interalRefsetId), Refset.class,
					null);

			if (refset == null) {

				throw new Exception("Refset Id: " + interalRefsetId + " does not exist in the RT2 database");
			}

			assertThat(refset.getId()).isEqualTo(interalRefsetId);

			return refset;
		}

	}

	/**
	 * Returns the editions.
	 *
	 * @return the editions
	 */
	public ResultList<TypeKeyValue> getEditions() {

		try {

			final String url = baseUrl + "/editions";
			LOG.info("Get Editions Testing url - " + url);

			final MvcResult result = mvc
					.perform(get(url).contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
					.andExpect(status().isOk()).andReturn();
			final String content = result.getResponse().getContentAsString();
			LOG.info(" content = " + content);

			final ResultList<TypeKeyValue> editions = ThreadLocalMapper.get().readValue(content,
					(new TypeReference<ResultList<TypeKeyValue>>() {
						/* NA */
					}));

			assertThat(editions).isNotNull();
			return editions;
		} catch (final Exception e) {

			e.printStackTrace();

			return null;
		}

	}

	/**
	 * Returns the branches.
	 *
	 * @param codeSystem the code system
	 * @return the branches
	 */
	public ResultList<String> getBranches(final String codeSystem) {

		try {

			final String url = "/general/branchVersions?branch=MAIN/" + codeSystem;
			LOG.info("Testing url - " + url);

			final MvcResult result = mvc
					.perform(get(url).contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
					.andExpect(status().isOk()).andReturn();
			final String content = result.getResponse().getContentAsString();
			LOG.info(" content = " + content);
			final ResultList<String> versions = ThreadLocalMapper.get().readValue(content,
					(new TypeReference<ResultList<String>>() {
						/* NA */
					}));

			assertThat(versions).isNotNull();
			return versions;
		} catch (final Exception e) {

			e.printStackTrace();

			return null;
		}

	}

	/**
	 * Search projects.
	 *
	 * @return the result list
	 */
	public ResultList<Project> searchProjects() {

		try {

			// Test full list
			final String url = "/project/search?limit=500&offset=0&sort=name&sortAscending=false";
			LOG.info("Project Search Testing url - " + url);

			final MvcResult result = mvc
					.perform(get(url).contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
					.andExpect(status().isOk()).andReturn();
			final String content = result.getResponse().getContentAsString();
			LOG.info(" content = " + content);

			final ResultList<Project> projectList = ThreadLocalMapper.get().readValue(content,
					(new TypeReference<ResultList<Project>>() {
						// n/a
					}));

			assertThat(projectList).isNotNull();
			assertThat(projectList.getItems()).isNotEmpty();
			return projectList;
		} catch (final Exception e) {

			e.printStackTrace();

			return null;
		}

	}

	/**
	 * Search directory.
	 *
	 * @param searchTerm the search term
	 * @return the result list
	 */
	public ResultList<Refset> searchDirectory(final String searchTerm) {

		try {

			final String url = baseUrl
					+ "/search?searchConcepts=true&limit=500&offset=0&sort=versionDate&sortAscending=false&query="
					+ searchTerm;

			LOG.info("Testing url - " + url);
			final MvcResult result = mvc
					.perform(get(url).contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
					.andExpect(status().isOk()).andReturn();
			final String content = result.getResponse().getContentAsString();
			LOG.info(" content = " + content);
			final ResultList<Refset> refsetList = ThreadLocalMapper.get().readValue(content,
					(new TypeReference<ResultList<Refset>>() {
						// n/a
					}));

			assertThat(refsetList).isNotNull();
			return refsetList;
		} catch (final Exception e) {

			e.printStackTrace();

			return null;
		}

	}

	/**
	 * Returns the concept details.
	 *
	 * @param internalRefsetId the internal refset id
	 * @param conceptId        the concept id
	 * @return the concept details
	 */
	public Concept getConceptDetails(final String internalRefsetId, final String conceptId) {

		try {

			final String url = "/concept/" + conceptId + "?refsetInternalId=" + internalRefsetId;
			LOG.info("Testing url - " + url);

			final MvcResult result = mvc
					.perform(get(url).contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
					.andExpect(status().isOk()).andReturn();
			final String content = result.getResponse().getContentAsString();
			LOG.info(" content = " + content);

			final Concept concept = ThreadLocalMapper.get().readValue(content, Concept.class);
			return concept;

		} catch (final Exception e) {

			e.printStackTrace();

			return null;
		}

	}

	/**
	 * Returns the refset concepts.
	 *
	 * @param branch            the branch
	 * @param refsetConceptType the refset concept type
	 * @return the refset concepts
	 */
	public ResultListConcept getRefsetConcepts(final String branch, final RefsetConceptsType refsetConceptType) {

		// For REST call, areParentConcepts variable true if returning for new
		// refset concepts, false for existing
		try {

			final boolean areParentConceptsRequest = (refsetConceptType
					.equals(RefsetConceptsType.ALL_SIMPLE_TYPE_CONCEPTS)) ? true : false;

			final String url = "/general/refsetConcepts?branch=" + branch + "&areParentConcepts="
					+ areParentConceptsRequest;
			LOG.info("Testing url - " + url);

			final MvcResult result = mvc
					.perform(get(url).contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
					.andExpect(status().isOk()).andReturn();
			final String content = result.getResponse().getContentAsString();
			LOG.info(" content = " + content);

			final ResultListConcept refsetConcepts = ThreadLocalMapper.get().readValue(content, ResultListConcept.class);
			assertThat(refsetConcepts.getItems()).isNotEmpty();
			return refsetConcepts;

		} catch (final Exception e) {

			e.printStackTrace();

			return null;
		}

	}

	/**
	 * Returns the members.
	 *
	 * @param internalRefsetId the internal refset id
	 * @return the members
	 */
	public ResultListConcept getMembers(final String internalRefsetId) {

		try {

			final String url = "/refset/" + internalRefsetId + "/members?limit=100000&offset=0&displayType=list";
			LOG.info("Testing url - " + url);

			final MvcResult result = mvc
					.perform(get(url).contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
					.andExpect(status().isOk()).andReturn();
			final String content = result.getResponse().getContentAsString();
			LOG.info(" content = " + content);
			final ResultListConcept members = ThreadLocalMapper.get().readValue(content, (ResultListConcept.class));

			// Testing Results
			assertThat(members).isNotNull();
			return members;

		} catch (final Exception e) {

			e.printStackTrace();

			return null;
		}

	}

	/**
	 * Search members.
	 *
	 * @param internalRefsetId the internal refset id
	 * @param searchTerm       the search term
	 * @return the concept result list
	 */
	public ResultListConcept searchMembers(final String internalRefsetId, final String searchTerm) {

		try {

			final String url = "/refset/" + internalRefsetId + "/members?limit=500&offset=0&query=" + searchTerm
					+ "&displayType=list&editing=true";

			LOG.info("Testing url - " + url);
			final MvcResult result = mvc
					.perform(get(url).contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
					.andExpect(status().isOk()).andReturn();
			final String content = result.getResponse().getContentAsString();

			LOG.info(" content = " + content);
			final ResultListConcept members = ThreadLocalMapper.get().readValue(content, (ResultListConcept.class));

			// Testing Results
			assertThat(members).isNotNull();
			return members;

		} catch (final Exception e) {

			e.printStackTrace();

			return null;
		}

	}

	/**
	 * Search taxonomy.
	 *
	 * @param internalRefsetId the internal refset id
	 * @param searchTerm       the search term
	 * @return the concept result list
	 */
	public ResultListConcept searchTaxonomy(final String internalRefsetId, final String searchTerm) {

		try {

			final String url = "/refset/" + internalRefsetId + "/taxonomySearch?limit=100000&offset=0&query="
					+ searchTerm;

			LOG.info("Testing url - " + url);
			final MvcResult result = mvc
					.perform(get(url).contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
					.andExpect(status().isOk()).andReturn();
			final String content = result.getResponse().getContentAsString();

			LOG.info(" content = " + content);
			final ResultListConcept members = ThreadLocalMapper.get().readValue(content, (ResultListConcept.class));

			// Testing Results
			assertThat(members).isNotNull();
			return members;

		} catch (final Exception e) {

			e.printStackTrace();

			return null;
		}

	}

	/**
	 * Returns the children.
	 *
	 * @param internalRefsetId the internal refset id
	 * @param parentId         the parent id
	 * @return the children
	 */
	public ResultListConcept getChildren(final String internalRefsetId, final String parentId) {

		try {

			final String url = "/refset/" + internalRefsetId
					+ "/members?limit=500&offset=0&displayType=taxonomy&startingConceptId=" + parentId
					+ "&language=nl-X-31000172101";
			LOG.info("Testing url - " + url);

			final MvcResult result = mvc
					.perform(get(url).contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
					.andExpect(status().isOk()).andReturn();
			final String content = result.getResponse().getContentAsString();
			LOG.info(" content = " + content);

			final ResultListConcept children = ThreadLocalMapper.get().readValue(content, (ResultListConcept.class));

			assertThat(children).isNotNull();
			assertThat(children.getItems()).isNotEmpty();
			return children;

		} catch (final Exception e) {

			e.printStackTrace();

			return null;
		}

	}

	/**
	 * Returns the member history.
	 *
	 * @param internalRefsetId the internal refset id
	 * @param conceptId        the concept id
	 * @return the member history
	 */
	public ResultList<Map<String, String>> getMemberHistory(final String internalRefsetId, final String conceptId) {

		try {

			final String url = "/refset/" + internalRefsetId + "/member/" + conceptId;
			LOG.info("Testing url - " + url);

			final MvcResult result = mvc
					.perform(get(url).contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
					.andExpect(status().isOk()).andReturn();
			final String content = result.getResponse().getContentAsString();
			LOG.info(" content = " + content);

			final ResultList<Map<String, String>> memberHistory = ThreadLocalMapper.get().readValue(content,
					(new TypeReference<ResultList<Map<String, String>>>() {
						/* NA */
					}));

			assertThat(memberHistory).isNotNull();
			assertThat(memberHistory.getTotal()).isEqualTo(1);
			return memberHistory;

		} catch (final Exception e) {

			e.printStackTrace();

			return null;
		}

	}

	/**
	 * Returns the ancestor path.
	 *
	 * @param conceptId        the concept id
	 * @param internalRefsetId the internal refset id
	 * @return the ancestor path
	 */
	public Concept getAncestorPath(final String conceptId, final String internalRefsetId) {

		try {

			final String url = "/refset/" + internalRefsetId + "/member/" + conceptId + "/ancestorConcepts";
			LOG.info("Testing url - " + url);

			final MvcResult result = mvc
					.perform(get(url).contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
					.andExpect(status().isOk()).andReturn();
			final String content = result.getResponse().getContentAsString();
			LOG.info(" content = " + content);

			final Concept returnedconcept = ThreadLocalMapper.get().readValue(content, Concept.class);
			return returnedconcept;

		} catch (final Exception e) {

			e.printStackTrace();

			return null;
		}

	}

	/**
	 * Setup ancestor cache.
	 *
	 * @param refsetId the refset id
	 * @param version  the version
	 * @return true, if successful
	 */
	public boolean setupAncestorCache(final String refsetId, final String version) {

		try {

			final String url = "/ancestors/" + refsetId + "/versionDate/" + version;
			LOG.info("Testing url - " + url);

			final MvcResult result = mvc
					.perform(get(url).contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
					.andExpect(status().isOk()).andReturn();
			final String content = result.getResponse().getContentAsString();
			LOG.info(" content = " + content);

			final JsonNode root = ThreadLocalMapper.get().readTree(content);
			final boolean success = root.get("success").asBoolean();

			return success;

		} catch (final Exception e) {

			e.printStackTrace();

			return false;
		}

	}
}
