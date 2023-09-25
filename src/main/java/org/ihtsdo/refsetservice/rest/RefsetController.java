/*
 * Copyright 2023 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.rest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.MediaType;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.queryparser.classic.QueryParserBase;
import org.ihtsdo.refsetservice.app.RecordMetric;
import org.ihtsdo.refsetservice.model.Concept;
import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.Organization;
import org.ihtsdo.refsetservice.model.PfsParameter;
import org.ihtsdo.refsetservice.model.QueryParameter;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.model.RefsetMemberComparison;
import org.ihtsdo.refsetservice.model.RestException;
import org.ihtsdo.refsetservice.model.SendCommunicationEmailInfo;
import org.ihtsdo.refsetservice.model.TypeKeyValue;
import org.ihtsdo.refsetservice.model.UpgradeInactiveConcept;
import org.ihtsdo.refsetservice.model.UpgradeReplacementConcept;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.model.VersionStatus;
import org.ihtsdo.refsetservice.model.WorkflowHistory;
import org.ihtsdo.refsetservice.service.SecurityService;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.sync.SyncAgent;
import org.ihtsdo.refsetservice.sync.util.SyncTestingInitializer;
import org.ihtsdo.refsetservice.terminologyservice.DiscussionService;
import org.ihtsdo.refsetservice.terminologyservice.OrganizationService;
import org.ihtsdo.refsetservice.terminologyservice.ProjectService;
import org.ihtsdo.refsetservice.terminologyservice.RefsetMemberService;
import org.ihtsdo.refsetservice.terminologyservice.RefsetService;
import org.ihtsdo.refsetservice.terminologyservice.WorkflowService;
import org.ihtsdo.refsetservice.util.AuditEntryHelper;
import org.ihtsdo.refsetservice.util.ConceptResultList;
import org.ihtsdo.refsetservice.util.DateUtility;
import org.ihtsdo.refsetservice.util.ModelUtility;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.ihtsdo.refsetservice.util.ResultList;
import org.ihtsdo.refsetservice.util.SearchParameters;
import org.ihtsdo.refsetservice.util.StringUtility;
import org.ihtsdo.refsetservice.util.TaxonomyParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Controller for /concept endpoints.
 */
@RestController
@OpenAPIDefinition(info = @Info(title = "Refset Controller", version = "1.0.0", description = "Endpoints for refsets and refset member managment."), tags = {
		@Tag(name = "refset", description = "Refset service endpoints") }, servers = {
				@Server(description = "Current Instance", url = "/") })
public class RefsetController extends BaseController {

	/** The Constant LOG. */
	private static final Logger LOG = LoggerFactory.getLogger(RefsetController.class);

	/** The local directory to store exported refset files. */
	private static String exportFileDir;

	/** The local directory to store exported refset files. */
	private static final String API_NOTES = "Use cases for search range from use of paging parameters, additional filters, searches properties, and so on.";

	/** Static initialization. */
	static {

		exportFileDir = PropertyUtility.getProperty("export.fileDir") + "/";
	}

	/**
	 * Returns the refset.
	 *
	 * @param refsetId    the refset ID
	 * @param versionDate the version date or IN DEVELOPMENT
	 * @return the refset
	 * @throws Exception the exception
	 */
	@Operation(summary = "Get the refset for the specified ID and version date. To see certain results this call requires authentication with the correct role.", responses = {
			@ApiResponse(responseCode = "200", description = "Successfully retrieved the requested information"),
			@ApiResponse(responseCode = "400", description = "Bad request"),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "403", description = "Forbidden"),
			@ApiResponse(responseCode = "404", description = "Resource not found") })
	@Parameters({ @Parameter(name = "refsetId", description = "The ID of the refset to return.", required = true),
			@Parameter(name = "versionDate", description = "The date of the refset version (YYYY-MM-DD) or IN DEVELOPMENT.", required = true), })
	@RecordMetric
	@RequestMapping(method = RequestMethod.GET, value = "/refset/{refsetId}/versionDate/{versionDate}", produces = MediaType.APPLICATION_JSON)
	public @ResponseBody ResponseEntity<Refset> getRefset(@PathVariable(value = "refsetId") final String refsetId,
			@PathVariable(value = "versionDate") final String versionDate) throws Exception {

		final User authUser = authorizeUser();
		try (final TerminologyService service = new TerminologyService()) {

			final Refset refset = RefsetService.getRefset(service, authUser, refsetId, versionDate);

			// if (user.getUserName().equals(SecurityService.GUEST_USERNAME) &&
			// (refset.getVersionStatus().equals(Refset.IN_DEVELOPMENT) ||
			// refset.isPrivateRefset())) {
			// return new ResponseEntity<>(new Refset(), HttpStatus.OK);
			//
			// } else if ((refset.getVersionStatus().equals(Refset.IN_DEVELOPMENT) ||
			// refset.isPrivateRefset()) &&
			// !user.doesUserHavePermission(User.ROLE_VIEWER, refset.getProject())) {
			// throw new RestException(false, 401, "Unauthorized", "User does not have
			// permission to view this refset.");
			// }

			RefsetService.getRefsetDescriptions(refset);

			LOG.debug("getRefset: Including discussion count");
			DiscussionService.attachRefsetDiscussionCount(service, authUser, refset);

			if (RefsetMemberService.REFSETS_BEING_UPDATED.contains(refset.getId())) {

				refset.setLocked(true);
			}

			if (RefsetService.REFSETS_TO_SHOW_UPGRADE_WARNING.contains(refset.getId())) {

				refset.setUpgradeWarning(true);
				RefsetService.REFSETS_TO_SHOW_UPGRADE_WARNING.remove(refset.getId());
			}

			refset.getProject().getEdition().setModuleNames(ProjectService.getModuleNames(refset.getProject()));

			LOG.debug("getRefset: refset: " + ModelUtility.toJson(refset));

			return new ResponseEntity<>(refset, HttpStatus.OK);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}

	}

	/**
	 * Returns the refset member count.
	 *
	 * @param refsetInternalId the internal refset ID
	 * @param request          the request
	 * @return the refset member count
	 * @throws Exception the exception
	 */
	@Operation(summary = "Returns the refset member count. To see certain results this call requires authentication with the correct role.", responses = {
			@ApiResponse(responseCode = "200", description = "Successfully retrieved the requested information"),
			@ApiResponse(responseCode = "400", description = "Bad request"),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "403", description = "Forbidden"),
			@ApiResponse(responseCode = "404", description = "Resource not found") })
	@Parameters({
			@Parameter(name = "refsetInternalId", description = "The internal ID of the refset to check.", required = true) })
	@RecordMetric
	@RequestMapping(method = RequestMethod.GET, value = "/refset/{refsetInternalId}/memberCount", produces = MediaType.APPLICATION_JSON)
	public @ResponseBody ResponseEntity<String> getRefsetMemberCount(
			@PathVariable(value = "refsetInternalId") final String refsetInternalId, final HttpServletRequest request)
			throws Exception {

		final User authUser = authorizeUser();
		try (final TerminologyService service = new TerminologyService()) {

			final Refset refset = service.findSingle("id:" + QueryParserBase.escape(refsetInternalId) + "",
					Refset.class, null);

			if (refset == null) {
				throw new Exception("Unable to retrieve reference set " + refsetInternalId);
			}

			service.setModifiedBy(authUser.getUserName());
			service.setModifiedFlag(true);
			RefsetService.setRefsetMemberCount(service, refset, false);

			LOG.debug("getRefsetMemberCount: refset: " + refset.getRefsetId() + " ; member count: "
					+ refset.getMemberCount());

			return new ResponseEntity<>(refset.getMemberCount() + "", HttpStatus.OK);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}
	}

	/**
	 * Returns the refset.
	 *
	 * @param refsetInternalId the internal refset ID
	 * @param request          the request
	 * @return the refset
	 * @throws Exception the exception
	 */
	@Operation(summary = "Returns if the refset is locked and the status of any member changes", responses = {
			@ApiResponse(responseCode = "200", description = "Successfully retrieved the requested information. Payload may include member update statuses"),
			@ApiResponse(responseCode = "400", description = "Bad request"),
			@ApiResponse(responseCode = "404", description = "Resource not found") })
	@Parameters({
			@Parameter(name = "refsetInternalId", description = "The internal ID of the refset to check.", required = true) })
	@RecordMetric
	@RequestMapping(method = RequestMethod.GET, value = "/refset/{refsetInternalId}/isLocked", produces = MediaType.APPLICATION_JSON)
	public @ResponseBody ResponseEntity<String> isRefsetLocked(
			@PathVariable(value = "refsetInternalId") final String refsetInternalId, final HttpServletRequest request)
			throws Exception {

		authorizeUser();
		try {

			final boolean isLocked = RefsetMemberService.REFSETS_BEING_UPDATED.contains(refsetInternalId);
			String returnString = isLocked + "";
			// LOG.debug("isRefsetLocked: refsetInternalId: " + refsetInternalId + " ;
			// Locked: " + isLocked);
			// LOG.debug("isRefsetLocked: refsetsUpdatedMembers: " +
			// RefsetMemberService.REFSETS_UPDATED_MEMBERS);
			// LOG.debug("isRefsetLocked: does update map contain this refset: " +
			// RefsetMemberService.REFSETS_UPDATED_MEMBERS.containsKey(refsetInternalId));

			if (!isLocked && RefsetMemberService.REFSETS_UPDATED_MEMBERS.containsKey(refsetInternalId)) {

				returnString = ModelUtility.toJson(RefsetMemberService.REFSETS_UPDATED_MEMBERS.get(refsetInternalId));
				RefsetMemberService.REFSETS_UPDATED_MEMBERS.remove(refsetInternalId);
			}

			return new ResponseEntity<>(returnString, HttpStatus.OK);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}

	}

	/**
	 * Add new refset members.
	 *
	 * @param refsetInternalId the internal refset ID
	 * @param conceptIds       a comma separated list of concepts to add
	 * @param ecl              an ECL query to identify concepts to add
	 * @param conceptFile      a file containing concept IDs to add
	 * @param fileType         the type of file uploaded (list or rf2)
	 * @return the new internal refset ID
	 * @throws Exception the exception
	 */
	@Operation(summary = "Add new refset members. This call requires authentication with the correct role.", responses = {
			@ApiResponse(responseCode = "200", description = "Successfully added the members. payload contains the status of the operation. Long running background process, "
					+ "call /refset/{refsetInternalId}/isLocked to get full status."),
			@ApiResponse(responseCode = "400", description = "Bad request"),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "403", description = "Forbidden"),
			@ApiResponse(responseCode = "404", description = "Resource not found") })
	@Parameters({
			@Parameter(name = "refsetInternalId", description = "The internal ID of the refset.", required = true),
			@Parameter(name = "conceptIds", description = "A comma separated list of concept IDs to add", required = false),
			@Parameter(name = "ecl", description = "An ECL query to identify concepts to add", required = false),
			@Parameter(name = "conceptFile", description = "A file containing concept IDs to add", required = false),
			@Parameter(name = "fileType", description = "The type of file uploaded (list or rf2)", required = false) })
	@PostMapping("/refset/{refsetInternalId}/members")
	public @ResponseBody ResponseEntity<String> addRefsetMembers(
			@PathVariable(value = "refsetInternalId") final String refsetInternalId,
			@RequestBody(required = false) final String conceptIds, @RequestParam(required = false) final String ecl,
			@RequestParam(required = false) final MultipartFile conceptFile,
			@RequestParam(required = false) final String fileType) throws Exception {

		final User authUser = authorizeUser();
		try (final TerminologyService service = new TerminologyService()) {

			RefsetMemberService.REFSETS_BEING_UPDATED.add(refsetInternalId);
			RefsetMemberService.REFSETS_UPDATED_MEMBERS.put(refsetInternalId, new HashMap<>());
			List<String> conceptIdList = new ArrayList<>();
			String error = "";
			List<String> unaddedConcepts;

			final Refset refset = RefsetService.getRefset(service, authUser, refsetInternalId);
			WorkflowService.canUserEditRefset(authUser, refset);

			service.setModifiedBy(authUser.getUserName());
			// service.setTransactionPerOperation(false);
			// service.beginTransaction();

			LOG.debug("addRefsetMembers: refsetInternalId: " + refsetInternalId + "; conceptIds: " + conceptIds
					+ "; ecl: " + ecl + "; fileType: " + fileType);

			String type = "an individual concept";

			// create the list of concepts based on what was passed in
			if (conceptIds != null && !conceptIds.equals("")) {

				conceptIdList = new ArrayList<String>(Arrays.asList(conceptIds.split(",")));

				if (conceptIdList.size() > 1) {

					type = "concepts by list";
				}

			} else if (ecl != null && !ecl.equals("")) {

				type = "by changing ECL definition";
				conceptIdList = RefsetMemberService.getConceptIdsFromEcl(refset.getBranchPath(), ecl);
			} else {

				type = "by file";

				conceptIdList = RefsetService.getConceptIdsFromFile(conceptFile, fileType);
			}

			LOG.debug("addRefsetMembers: conceptIdList: " + conceptIdList);

			// add the list of concepts as members to the refset
			unaddedConcepts = RefsetMemberService.addRefsetMembers(service, authUser, refset, conceptIdList);

			// see if there are any concepts that were unable to be added and craft the
			// error message
			if (unaddedConcepts.size() > 0) {

				error = "Unable to add concepts ";

				for (final String unaddedConcept : unaddedConcepts) {

					error += unaddedConcept + ", ";
				}

				error = StringUtils.removeEnd(error, ", ");
			}

			// service.commit();
			LOG.debug("addRefsetMembers: Finished with " + unaddedConcepts.size() + " invaild concepts");

			if (error.equals("")) {

				AuditEntryHelper.addMembersEntry(refset, type, conceptIds);

				return new ResponseEntity<>("{\"status\": \"All concepts added.\"}", HttpStatus.OK);
			} else {

				return new ResponseEntity<>("{\"error\": \"" + error + "\"}", HttpStatus.OK);
			}

		} catch (final Exception e) {
			handleException(e);
			return null;
		}

		finally {

			RefsetMemberService.REFSETS_BEING_UPDATED.remove(refsetInternalId);
		}

	}

	/**
	 * Remove or inactivate refset membership for a group of concepts.
	 *
	 * @param refsetInternalId the internal refset ID
	 * @param conceptIds       a comma separated list of concepts to remove
	 * @param ecl              an ECL query to identify concepts to remove
	 * @param conceptFile      a file containing concept IDs to remove
	 * @param fileType         the type of file uploaded (list or rf2)
	 * @return the status of the operation
	 * @throws Exception the exception
	 */
	@Operation(summary = "Remove or inactivate refset membership for a group of concepts. This call requires authentication with the correct role.", responses = {
			@ApiResponse(responseCode = "200", description = "Successfully removed the members. payload contains the status of the operation. Long running background process, "
					+ "call /refset/{refsetInternalId}/isLocked to get full status."),
			@ApiResponse(responseCode = "400", description = "Bad request"),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "403", description = "Forbidden"),
			@ApiResponse(responseCode = "404", description = "Resource not found") })
	@Parameters({
			@Parameter(name = "refsetInternalId", description = "The internal ID of the refset.", required = true),
			@Parameter(name = "conceptIds", description = "A comma separated list of concept IDs to remove", required = false),
			@Parameter(name = "ecl", description = "An ECL query to identify concepts to remove", required = false),
			@Parameter(name = "conceptFile", description = "A file containing concept IDs to remove", required = false),
			@Parameter(name = "fileType", description = "The type of file uploaded (list or rf2)", required = false), })
	@PostMapping("/refset/{refsetInternalId}/removeMembers")
	public @ResponseBody ResponseEntity<String> removeRefsetMembers(
			@PathVariable(value = "refsetInternalId") final String refsetInternalId,
			@RequestBody(required = false) final String conceptIds, @RequestParam(required = false) final String ecl,
			@RequestParam(required = false) final MultipartFile conceptFile,
			@RequestParam(required = false) final String fileType) throws Exception {

		final User authUser = authorizeUser();
		try (final TerminologyService service = new TerminologyService()) {

			RefsetMemberService.REFSETS_BEING_UPDATED.add(refsetInternalId);
			RefsetMemberService.REFSETS_UPDATED_MEMBERS.put(refsetInternalId, new HashMap<>());
			String conceptsToRemove = null;

			final Refset refset = RefsetService.getRefset(service, authUser, refsetInternalId);
			WorkflowService.canUserEditRefset(authUser, refset);

			service.setModifiedBy(authUser.getUserName());
			// service.setTransactionPerOperation(false);
			// service.beginTransaction();

			LOG.debug("removeRefsetMembers: refsetInternalId: " + refsetInternalId + "; conceptIds: " + conceptIds
					+ "; ecl: " + ecl + "; fileType: " + fileType);

			String error = "";
			String type = "an individual concept";

			// If concepts were passed in use those
			if (conceptIds != null && !conceptIds.equals("")) {

				conceptsToRemove = conceptIds;

				if (Arrays.asList(conceptsToRemove.split(",")).size() > 1) {

					type = "concepts by list";
				}

			} else if (ecl != null && !ecl.equals("")) {

				type = "by changing ECL definition";
				conceptsToRemove = String.join(",", RefsetMemberService.getConceptIdsFromEcl(refset.getBranchPath(),
						ecl + " AND ^" + refset.getRefsetId()));
			} else {

				type = "by file";

				conceptsToRemove = String.join(",", RefsetService.getConceptIdsFromFile(conceptFile, fileType));
			}

			LOG.debug("removeRefsetMembers: conceptIds: " + conceptIds);

			// add the list of concepts as members to the refset
			final List<String> unremovedConcepts = RefsetMemberService.removeRefsetMembers(service, authUser, refset,
					conceptsToRemove);
			// service.commit();

			// see if there are any concepts that were unable to be added and craft the
			// error message
			if (unremovedConcepts.size() > 0) {

				error = "Unable to remove concepts ";

				for (final String unremovedConcept : unremovedConcepts) {

					error += unremovedConcept + ", ";
				}

				error = StringUtils.removeEnd(error, ", ");
			}

			if (error.equals("")) {

				AuditEntryHelper.removeMembersEntry(refset, type, conceptsToRemove);

				return new ResponseEntity<>("{\"status\": \"All concepts removed.\"}", HttpStatus.OK);
			} else {

				return new ResponseEntity<>("{\"error\": \"" + error + "\"}", HttpStatus.OK);
			}

		} catch (final Exception e) {
			handleException(e);
			return null;
		}

		finally {

			RefsetMemberService.REFSETS_BEING_UPDATED.remove(refsetInternalId);
		}

	}

	/**
	 * Add new intensional refset definition exceptions.
	 *
	 * @param refsetInternalId        the internal refset ID
	 * @param conceptIds              a comma separated list of concepts to add
	 * @param ecl                     an ECL query to identify concepts to add
	 * @param conceptFile             a file containing concept IDs to add
	 * @param fileType                the type of file uploaded (list or rf2)
	 * @param definitionExceptionType the definition exception type
	 * @return the status or error message
	 * @throws Exception the exception
	 */
	@Operation(summary = "Add new intensional refset definition exceptions. This call requires authentication with the correct role.", responses = {
			@ApiResponse(responseCode = "200", description = "Successfully added the exceptions. payload contains the status of the operation. Long running background process, "
					+ "call /refset/{refsetInternalId}/isLocked to get full status."),
			@ApiResponse(responseCode = "400", description = "Bad request"),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "403", description = "Forbidden"),
			@ApiResponse(responseCode = "404", description = "Resource not found") })
	@Parameters({
			@Parameter(name = "refsetInternalId", description = "The internal ID of the refset.", required = true),
			@Parameter(name = "conceptIds", description = "A comma separated list of concept IDs to add", required = false),
			@Parameter(name = "ecl", description = "An ECL query to identify concepts to add", required = false),
			@Parameter(name = "conceptFile", description = "A file containing concept IDs to add", required = false),
			@Parameter(name = "fileType", description = "The type of file uploaded (list or rf2)", required = false), })
	@PostMapping("/refset/{refsetInternalId}/definitionExceptions")
	public @ResponseBody ResponseEntity<String> addRefsetDefinitionExceptions(
			@PathVariable(value = "refsetInternalId") final String refsetInternalId,
			@RequestBody(required = false) final String conceptIds, @RequestParam(required = false) final String ecl,
			@RequestParam(required = false) final MultipartFile conceptFile,
			@RequestParam(required = false) final String fileType,
			@RequestParam(required = false) final String definitionExceptionType) throws Exception {

		final User authUser = authorizeUser();

		try (final TerminologyService service = new TerminologyService()) {

			RefsetMemberService.REFSETS_BEING_UPDATED.add(refsetInternalId);
			RefsetMemberService.REFSETS_UPDATED_MEMBERS.put(refsetInternalId, new HashMap<>());
			List<String> conceptIdList = new ArrayList<>();

			final Refset refset = RefsetService.getRefset(service, authUser, refsetInternalId);
			WorkflowService.canUserEditRefset(authUser, refset);

			service.setModifiedBy(authUser.getUserName());
			// service.setTransactionPerOperation(false);
			// service.beginTransaction();

			LOG.debug("addRefsetDefinitionExceptions: refsetInternalId: " + refsetInternalId + "; conceptIds: "
					+ conceptIds + "; ecl: " + ecl + "; fileType: " + fileType + " ; definitionExceptionType: "
					+ definitionExceptionType);

			String inclusionEcl = ecl;

			if (ecl == null || ecl.equals("")) {

				// create the list of concepts based on what was passed in
				if (conceptIds != null && !conceptIds.equals("")) {

					conceptIdList = Arrays.asList(conceptIds.split(","));

				} else if (ecl == null || ecl.equals("")) {

					conceptIdList = RefsetService.getConceptIdsFromFile(conceptFile, fileType);
				}

				inclusionEcl = RefsetMemberService.conceptListToEclStatement(conceptIdList);
			}

			final String status = RefsetService.addDefinitionException(service, authUser, refset, inclusionEcl,
					definitionExceptionType);
			// service.commit();

			if (!status.startsWith("Error")) {

				return new ResponseEntity<>("{\"status\": \"Definition exception added.\"}", HttpStatus.OK);
			} else {

				return new ResponseEntity<>("{\"error\": \"" + status + "\"}", HttpStatus.OK);
			}

		} catch (final Exception e) {
			handleException(e);
			return null;
		}

		finally {

			RefsetMemberService.REFSETS_BEING_UPDATED.remove(refsetInternalId);
		}

	}

	/**
	 * Remove an intensional refset definition exception.
	 *
	 * @param refsetInternalId      the internal refset ID
	 * @param definitionExceptionId the exception ID
	 * @return the status or error message
	 * @throws Exception the exception
	 */
	@Operation(summary = "Remove an intensional refset definition exception. This call requires authentication with the correct role.", responses = {
			@ApiResponse(responseCode = "200", description = "Successfully removed the exception. payload contains the status of the operation. Long running background process, "
					+ "call /refset/{refsetInternalId}/isLocked to get full status."),
			@ApiResponse(responseCode = "400", description = "Bad request"),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "403", description = "Forbidden"),
			@ApiResponse(responseCode = "404", description = "Resource not found") })
	@Parameters({
			@Parameter(name = "refsetInternalId", description = "The internal ID of the refset.", required = true),
			@Parameter(name = "definitionExceptionId", description = "The internal definition ID", required = true) })
	@PostMapping("/refset/{refsetInternalId}/removeDefinitionException/{definitionExceptionId}")
	public @ResponseBody ResponseEntity<String> removeRefsetDefinitionExceptions(
			@PathVariable(value = "refsetInternalId") final String refsetInternalId,
			@PathVariable(value = "definitionExceptionId") final String definitionExceptionId) throws Exception {

		final User authUser = authorizeUser();

		try (final TerminologyService service = new TerminologyService()) {

			RefsetMemberService.REFSETS_BEING_UPDATED.add(refsetInternalId);
			RefsetMemberService.REFSETS_UPDATED_MEMBERS.put(refsetInternalId, new HashMap<>());
			LOG.debug("removeRefsetDefinitionExceptions: refsetInternalId: " + refsetInternalId
					+ "; definitionExceptionId: " + definitionExceptionId);

			final Refset refset = RefsetService.getRefset(service, authUser, refsetInternalId);
			WorkflowService.canUserEditRefset(authUser, refset);

			service.setModifiedBy(authUser.getUserName());
			// service.setTransactionPerOperation(false);
			// service.beginTransaction();

			final String status = RefsetService.removeDefinitionException(service, authUser, refset,
					definitionExceptionId);
			// service.commit();

			if (!status.startsWith("Error")) {

				return new ResponseEntity<>("{\"status\": \"Definition exception removed.\"}", HttpStatus.OK);
			} else {

				return new ResponseEntity<>("{\"error\": \"" + status + "\"}", HttpStatus.OK);
			}

		} catch (final Exception e) {
			handleException(e);
			return null;
		}

		finally {

			RefsetMemberService.REFSETS_BEING_UPDATED.remove(refsetInternalId);
		}

	}

	/**
	 * Create a new refset.
	 *
	 * @param refsetParameters The paramaters for the new refset
	 * @param bindingResult    the binding result
	 * @return the new internal refset ID
	 * @throws Exception the exception
	 */
	@Operation(summary = "Add a new refset. This call requires authentication with the correct role.", responses = {
			@ApiResponse(responseCode = "200", description = "Successfully added the refset. payload contains the new refset ID."),
			@ApiResponse(responseCode = "400", description = "Bad request"),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "403", description = "Forbidden"),
			@ApiResponse(responseCode = "404", description = "Resource not found"),
			@ApiResponse(responseCode = "417", description = "Expectation failed") })
	@Parameters({
			@Parameter(name = "refsetParameters", description = "The required fields of the refset to add.", required = true) })
	@PostMapping("/refset")
	public @ResponseBody ResponseEntity<String> createRefset(final @RequestBody Refset refsetParameters,
			final BindingResult bindingResult) throws Exception {

		// Check to make sure parameters were properly bound to variables.
		checkBinding(bindingResult);

		final User authUser = authorizeUser();

		String newRefsetInternalId = null;
		try (final TerminologyService service = new TerminologyService()) {

			service.setModifiedBy(authUser.getUserName());

			String status = "";
			final Object returned = RefsetService.createRefset(service, authUser, refsetParameters);

			if (returned instanceof String) {

				status = (String) returned;
			} else {

				final Refset refset = (Refset) returned;
				newRefsetInternalId = refset.getId();
				status = refset.getRefsetId();
			}

			// service.commit();

			if (status.startsWith("Error")) {

				return new ResponseEntity<>("{\"error\": \"" + status + "\"}", HttpStatus.OK);
			}

			return new ResponseEntity<>("{\"refsetId\": \"" + status + "\"}", HttpStatus.OK);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}

		finally {

			RefsetMemberService.REFSETS_BEING_UPDATED.remove(newRefsetInternalId);
		}

	}

	/**
	 * Modify an existing refset that is in edit mode.
	 *
	 * @param refsetInternalId the internal refset ID
	 * @param refsetParameters the refset parameters
	 * @param bindingResult    the binding result
	 * @return the refset internal ID or errors
	 * @throws Exception the exception
	 */
	@Operation(summary = "Modify an existing refset that is in edit mode. This call requires authentication with the correct role.", responses = {
			@ApiResponse(responseCode = "200", description = "Successfully modified the refset."),
			@ApiResponse(responseCode = "400", description = "Bad request"),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "403", description = "Forbidden"),
			@ApiResponse(responseCode = "404", description = "Resource not found"),
			@ApiResponse(responseCode = "417", description = "Expectation failed") })
	@Parameters({
			@Parameter(name = "refsetInternalId", description = "The internal ID of the refset.", required = true),
			@Parameter(name = "refsetParameters", description = "The refset to modify.", required = true) })
	@PutMapping("/refset/{refsetInternalId}")
	public @ResponseBody ResponseEntity<String> modifyRefset(
			@PathVariable(value = "refsetInternalId") final String refsetInternalId,
			final @RequestBody Refset refsetParameters, final BindingResult bindingResult) throws Exception {

		// Check to make sure parameters were properly bound to variables.
		checkBinding(bindingResult);

		final User authUser = authorizeUser();
		try (final TerminologyService service = new TerminologyService()) {

			RefsetMemberService.REFSETS_BEING_UPDATED.add(refsetInternalId);
			RefsetMemberService.REFSETS_UPDATED_MEMBERS.put(refsetInternalId, new HashMap<>());

			final Refset refset = RefsetService.getRefset(service, authUser, refsetInternalId);
			WorkflowService.canUserEditRefset(authUser, refset);

			service.setModifiedBy(authUser.getUserName());
			service.setTransactionPerOperation(false);
			service.beginTransaction();

			final String status = RefsetService.modifyRefset(service, authUser, refset, refsetParameters);
			service.commit();

			if (!status.startsWith("Error")) {
				return new ResponseEntity<>("{\"refsetInternalId\": \"" + refsetInternalId + "\"}", HttpStatus.OK);
			} else {

				return new ResponseEntity<>("{\"error\": \"" + status + "\"}", HttpStatus.OK);
			}

		} catch (final Exception e) {
			handleException(e);
			return null;
		}

		finally {

			RefsetMemberService.REFSETS_BEING_UPDATED.remove(refsetInternalId);
		}

	}

	/**
	 * Recalculate the definition of an intensional refset, updating the members.
	 *
	 * @param refsetInternalId the internal refset ID
	 * @return the refset internal ID or errors
	 * @throws Exception the exception
	 */
	@Operation(summary = "Recalculate the definition of an intensional refset, updating the members. This call requires authentication with the correct role.", responses = {
			@ApiResponse(responseCode = "200", description = "Successfully modified the refset. Long running background process, call /refset/{refsetInternalId}/isLocked to get full status"),
			@ApiResponse(responseCode = "400", description = "Bad request"),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "403", description = "Forbidden"),
			@ApiResponse(responseCode = "404", description = "Resource not found") })
	@Parameters({
			@Parameter(name = "refsetInternalId", description = "The internal ID of the refset.", required = true) })
	@PutMapping("/refset/{refsetInternalId}/recalculateDefinition")
	public @ResponseBody ResponseEntity<String> recalculateRefsetDefinition(
			@PathVariable(value = "refsetInternalId") final String refsetInternalId) throws Exception {

		final User authUser = authorizeUser();
		try (final TerminologyService service = new TerminologyService()) {

			RefsetMemberService.REFSETS_BEING_UPDATED.add(refsetInternalId);
			RefsetMemberService.REFSETS_UPDATED_MEMBERS.put(refsetInternalId, new HashMap<>());

			final Refset refset = RefsetService.getRefset(service, authUser, refsetInternalId);
			WorkflowService.canUserEditRefset(authUser, refset);

			service.setModifiedBy(authUser.getUserName());
			// service.setTransactionPerOperation(false);
			// service.beginTransaction();

			final String status = RefsetService.modifyRefsetDefinition(authUser, service, refset,
					refset.getDefinitionClauses());
			// service.commit();

			if (!status.startsWith("Error")) {
				return new ResponseEntity<>("{\"refsetInternalId\": \"" + refsetInternalId + "\"}", HttpStatus.OK);
			} else {

				return new ResponseEntity<>("{\"error\": \"" + status + "\"}", HttpStatus.OK);
			}

		} catch (final Exception e) {
			handleException(e);
			return null;
		}

		finally {

			RefsetMemberService.REFSETS_BEING_UPDATED.remove(refsetInternalId);
		}

	}

	/**
	 * Get Workflow history for a refset.
	 *
	 * @param refsetInternalId the refset internal id
	 * @param searchParameters the search parameters
	 * @param bindingResult    the binding result
	 * @return the workflow history
	 * @throws Exception the exception
	 */
	@Operation(summary = "Get Workflow history search results. This call requires authentication with the correct role.", responses = {
			@ApiResponse(responseCode = "200", description = "Successfully retrieved the requested information"),
			@ApiResponse(responseCode = "400", description = "Bad request"),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "403", description = "Forbidden"),
			@ApiResponse(responseCode = "404", description = "Resource not found"),
			@ApiResponse(responseCode = "417", description = "Expectation failed"), })
	// @ModelAttribute API params documented in SearchParameter
	@Parameters({
			@Parameter(name = "refsetInternalId", description = "The internal ID of the refset.", required = true), })
	@RecordMetric
	@RequestMapping(method = RequestMethod.GET, value = "/refset/{refsetInternalId}/workflowHistory", produces = MediaType.APPLICATION_JSON)
	public @ResponseBody ResponseEntity<ResultList<WorkflowHistory>> getWorkflowHistory(
			@PathVariable(value = "refsetInternalId") final String refsetInternalId,
			final SearchParameters searchParameters, final BindingResult bindingResult) throws Exception {

		// Check to make sure parameters were properly bound to variables.
		checkBinding(bindingResult);

		final User authUser = authorizeUser();
		try (final TerminologyService service = new TerminologyService()) {

			// LOG.debug("getWorkflowHistory refsetInternalId: " + refsetInternalId + " ;
			// searchParameters: " + ModelUtility.toJson(searchParameters));

			final Refset refset = RefsetService.getRefset(service, authUser, refsetInternalId);
			final ResultList<WorkflowHistory> results = WorkflowService.getWorkflowHistory(service, refset,
					searchParameters);

			return new ResponseEntity<>(results, HttpStatus.OK);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}

	}

	/**
	 * Change the workflow status of a refset.
	 *
	 * @param refsetInternalId the internal refset ID
	 * @param action           the action triggering the status change
	 * @param notes            Notes about the status change
	 * @return the refset internal ID or errors
	 * @throws Exception the exception
	 */
	@Operation(summary = "Change the workflow status of a refset. This call requires authentication with the correct role.", responses = {
			@ApiResponse(responseCode = "200", description = "Successfully changed the refset status. The payload contains the updated refset"),
			@ApiResponse(responseCode = "400", description = "Bad request"),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "403", description = "Forbidden"),
			@ApiResponse(responseCode = "404", description = "Resource not found") })
	@Parameters({
			@Parameter(name = "refsetInternalId", description = "The internal ID of the refset.", required = true),
			@Parameter(name = "action", description = "The action triggering the status change", required = true),
			@Parameter(name = "notes", description = "Notes about the status change", required = false), })
	@PostMapping("/refset/{refsetInternalId}/workflowStatus")
	public @ResponseBody ResponseEntity<Refset> setWorkflowStatus(
			@PathVariable(value = "refsetInternalId") final String refsetInternalId, @RequestParam final String action,
			@RequestParam(required = false) final String notes) throws Exception {

		final User authUser = authorizeUser();
		try (final TerminologyService service = new TerminologyService()) {

			LOG.debug("setWorkflowStatus: refsetInternalId: " + refsetInternalId + " ; action: " + action + " ; notes: "
					+ notes);

			Refset refset = RefsetService.getRefset(service, authUser, refsetInternalId);
			WorkflowService.canUserPerformWorkflowAction(authUser, refset, action);

			final String currentStatus = refset.getWorkflowStatus();

			service.setModifiedBy(authUser.getUserName());
			// service.setTransactionPerOperation(false);
			// service.beginTransaction();

			if (action.equals(WorkflowService.FINISH_EDIT)) {

				AuditEntryHelper.addEditingCycleEntry(refset, true);
			} else if (action.equals(WorkflowService.CANCEL_EDIT)) {

				AuditEntryHelper.addEditingCycleEntry(refset, false);
			} else if (action.equals(WorkflowService.CANCEL_UPGRADE)) {
				RefsetMemberService.REFSETS_UPDATED_MEMBERS.remove(refsetInternalId);
			}

			// if the status is Published then create a new version of the refset that is
			// ready to be edited
			if (currentStatus == null || currentStatus.equals(WorkflowService.PUBLISHED)) {

				final String newRefsetInternalId = RefsetService.createNewRefsetVersion(service, authUser,
						refset.getId(), true);
				refset = RefsetService.getRefset(service, authUser, newRefsetInternalId);

				if (action.equals(WorkflowService.EDIT) && !refset.isBasedOnLatestVersion()) {

					RefsetService.REFSETS_TO_SHOW_UPGRADE_WARNING.add(newRefsetInternalId);
					refset.setUpgradeWarning(true);
				}

				return new ResponseEntity<>(refset, HttpStatus.OK);
			}

			refset = WorkflowService.setWorkflowStatusByAction(service, authUser, action, refset, notes);
			// service.commit();

			// if the status changed return the updated refset else return null
			if (!currentStatus.equals(refset.getWorkflowStatus())) {

				LOG.debug("setWorkflowStatus: updated refset: " + ModelUtility.toJson(refset));
				return new ResponseEntity<>(refset, HttpStatus.OK);
			} else {

				LOG.debug("setWorkflowStatus: did not update workflow status.");
				return null;
			}

		} catch (final Exception e) {
			handleException(e);
			return null;
		}

	}

	/**
	 * Modify an existing refset that is in edit mode.
	 *
	 * @param refsetInternalId the internal refset ID
	 * @param notes            the notes
	 * @return the refset internal ID or errors
	 * @throws Exception the exception
	 */
	@Operation(summary = "Modify a refset workflow status note. This call requires authentication with the correct role.", responses = {
			@ApiResponse(responseCode = "200", description = "Successfully changed the status note. The payload contains the full updated workflow history"),
			@ApiResponse(responseCode = "400", description = "Bad request"),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "403", description = "Forbidden"),
			@ApiResponse(responseCode = "404", description = "Resource not found") })
	@Parameters({
			@Parameter(name = "refsetInternalId", description = "The internal ID of the refset.", required = true),
			@Parameter(name = "notes", description = "The updated note", required = true), })
	@PutMapping("/refset/{refsetInternalId}/workflowNote")
	public @ResponseBody ResponseEntity<ResultList<WorkflowHistory>> updateWorkflowNote(
			@PathVariable(value = "refsetInternalId") final String refsetInternalId,
			@RequestBody(required = true) final String notes) throws Exception {

		final User authUer = authorizeUser();
		try (final TerminologyService service = new TerminologyService()) {

			service.setModifiedBy(authUer.getUserName());
			// service.setTransactionPerOperation(false);
			// service.beginTransaction();

			final Refset refset = RefsetService.getRefset(service, authUer, refsetInternalId);
			// not used final String currentStatus = refset.getWorkflowStatus();

			WorkflowService.updateWorkflowNote(service, authUer, refset, notes);
			// service.commit();

			final ResultList<WorkflowHistory> results = WorkflowService.getWorkflowHistory(service, refset,
					new SearchParameters());

			return new ResponseEntity<>(results, HttpStatus.OK);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}

	}

	/**
	 * Start the publication of all Ready for Publication refsets in a code system
	 * by promoting them to the REFSETS branch. This call requires authentication
	 * with the correct role. ** IMPORTANT ** Once this step is taken it will be
	 * very hard to reverse
	 *
	 * @param codeSystem a code system to limit the publication to
	 * @return the status of the operation
	 * @throws Exception the exception
	 */
	@Hidden
	@Operation(summary = "Start the publication of all Ready for Publication refsets in a code system by promoting them to the REFSETS branch. "
			+ "** IMPORTANT ** Once this step is taken it will be very hard to reverse. This call requires authentication with the correct role.", responses = {
					@ApiResponse(responseCode = "200", description = "Successfully began the publication process. The payload contains the status."),
					@ApiResponse(responseCode = "400", description = "Bad request"),
					@ApiResponse(responseCode = "401", description = "Unauthorized"),
					@ApiResponse(responseCode = "403", description = "Forbidden"),
					@ApiResponse(responseCode = "404", description = "Resource not found") })
	@Parameters({
			@Parameter(name = "codeSystem", description = "A code system to limit the publication to", required = true), })
	@PutMapping("/admin/startAllRefsetPublications")
	public @ResponseBody ResponseEntity<String> startAllRefsetPublications(
			@RequestParam(required = true) final String codeSystem) throws Exception {

		final User authUser = authorizeUser();

		if (!authUser.checkPermission(User.ROLE_ADMIN, "all", null, null)) {
			throw new RestException(false, 403, "Forbidden", "User does not have permission to perform this action");
		}

		if (StringUtility.isEmpty(codeSystem)) {

			throw new Exception("A Code System must be specified.");
		}

		try (final TerminologyService service = new TerminologyService()) {

			final Edition edition = service.findSingle("shortName:" + codeSystem, Edition.class, null);

			if (edition == null) {
				throw new RestException(false, 417, "Expectation failed",
						"The code system '" + codeSystem + "' could not be found");
			}

			service.setModifiedBy(authUser.getUserName());
			service.setModifiedFlag(true);

			LOG.debug("startAllRefsetPublications: editionShortName (codeSystem): " + codeSystem);

			final List<String> refsetsNotUpdated = WorkflowService.startAllRefsetPublications(service, codeSystem);
			String error = "";

			// see if there are any refsets that were unable to be updated and craft the
			// error message
			if (refsetsNotUpdated.size() > 0) {

				error = "Unable to promote refsets in code system " + codeSystem + ": ";

				for (final String unremovedConcept : refsetsNotUpdated) {

					error += unremovedConcept + ", ";
				}

				error = StringUtils.removeEnd(error, ", ");
			}

			if (error.equals("")) {

				final String message = "All reference sets promoted in code system " + codeSystem;
				return new ResponseEntity<>("{\"status\": \"" + message + ".\"}", HttpStatus.OK);

			} else {

				return new ResponseEntity<>("{\"error\": \"" + error + "\"}", HttpStatus.OK);
			}

		} catch (final Exception e) {
			handleException(e);
			return null;
		}

	}

	/**
	 * Complete the publication of all Ready for Publication refsets in a code
	 * system. This call requires authentication with the correct role.
	 *
	 * @param versionDate the publication date of the refset in yyyy-MM-dd format
	 * @param codeSystem  a code system to limit the refset to
	 * @param publishType if value is 'localset' this will publish (non-snomed
	 *                    versioning) only local sets. If not supplied or any other
	 *                    value this will published everything other than local
	 *                    sets.
	 * @return the status of the operation
	 * @throws Exception the exception
	 */
	@Hidden
	@Operation(summary = "Complete the publication of all Ready for Publication refsets in a code system. This call requires authentication with the correct role.", responses = {
			@ApiResponse(responseCode = "200", description = "Successfully published the refsets. The payload contains the status."),
			@ApiResponse(responseCode = "400", description = "Bad request"),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "403", description = "Forbidden"),
			@ApiResponse(responseCode = "404", description = "Resource not found") })
	@Parameters({
			@Parameter(name = "versionDate", description = "the publication date of the refsets (YYYY-MM-DD)", required = true),
			@Parameter(name = "codeSystem", description = "A code system to limit the publication to", required = true),
			@Parameter(name = "publishType", description = "If value is 'localset' this will publish (non-snomed versioning) only local sets. "
					+ "If not supplied or any other value this will published everything other than local sets.", required = false), })
	@PutMapping("/admin/completeAllRefsetPublications")
	public @ResponseBody ResponseEntity<String> completeAllRefsetPublications(
			@RequestParam(required = true) final String versionDate,
			@RequestParam(required = true) final String codeSystem,
			@RequestParam(required = false) final String publishType) throws Exception {

		final User authUser = authorizeUser();
		String typeToPublish = "regular";

		if (StringUtility.isEmpty(codeSystem)) {
			throw new RestException(false, 417, "Expecatation failed", "Code System must be specified");
		}

		try {
			new SimpleDateFormat(DateUtility.DATE_FORMAT_REVERSE).parse(versionDate);

		} catch (final Exception e) {
			throw new RestException(false, 417, "Expectation failed",
					"The version date must be specified in this format: " + DateUtility.DATE_FORMAT_REVERSE);
		}

		String branchPath = "MAIN/";

		if (!codeSystem.equals("SNOMEDCT")) {
			branchPath += codeSystem + "/";
		}

		branchPath += versionDate;

		if (!WorkflowService.doesBranchExist(branchPath)) {
			throw new RestException(false, 417, "Expectation failed", "The version branch '" + branchPath
					+ "' does not exist. This must be created and populated with the reference sets to be versioned outside of this tool "
					+ "before this publication completion process can be run.");
		}

		try (final TerminologyService service = new TerminologyService()) {

			final Edition edition = service.findSingle("shortName:" + codeSystem, Edition.class, null);

			if (edition == null) {
				throw new RestException(false, 417, "Expectation failed",
						"Unable to find edition for code system = " + codeSystem);
			}

			if (!StringUtility.isEmpty(publishType) && publishType.equals("localset")) {

				typeToPublish = "localset";

				if (!authUser.checkPermission(User.ROLE_ADMIN, edition.getOrganizationName(), edition.getShortName(),
						null)) {
					throw new RestException(false, 403, "Forbidden",
							"This user does not have permission to perform this action");
				}
			} else {

				if (!authUser.checkPermission(User.ROLE_ADMIN, "all", null, null)) {
					throw new RestException(false, 403, "Forbidden",
							"This user does not have permission to perform this action");
				}
			}

			service.setModifiedBy(authUser.getUserName());
			service.setModifiedFlag(true);
			service.setTransactionPerOperation(false);
			service.beginTransaction();

			LOG.debug("completeAllRefsetPublications: versionDate: " + versionDate
					+ " ; editionShortName (codeSystem): " + codeSystem + " ; typeToPublish: " + typeToPublish);

			final List<String> refsetsNotUpdated = WorkflowService.completeAllRefsetPublications(service, versionDate,
					codeSystem, typeToPublish);
			String error = "";
			String messageType = "";

			if (typeToPublish.equals("localset")) {
				messageType = "local ";
			}

			service.commit();

			// see if there are any refsets that were unable to be updated and craft the
			// error message
			if (refsetsNotUpdated.size() > 0) {

				error = "Unable to complete publication for " + messageType + "reference sets in code system "
						+ codeSystem + ": ";

				for (final String refsetNotUpdated : refsetsNotUpdated) {

					error += refsetNotUpdated + ", ";
				}

				error = StringUtils.removeEnd(error, ", ");
			}

			if (error.equals("")) {

				final String message = "All " + messageType + "reference set publications completed in code system "
						+ codeSystem;
				return new ResponseEntity<>("{\"status\": \"" + message + ".\"}", HttpStatus.OK);

			} else {

				return new ResponseEntity<>("{\"error\": \"" + error + "\"}", HttpStatus.OK);
			}

		} catch (final Exception e) {
			handleException(e);
			return null;
		}

	}

	/**
	 * Publish a Ready for Publication local refset in a code system. This call
	 * requires authentication with the correct role.
	 *
	 * @param refsetInternalId the internal refset ID
	 * @param versionDate      the publication date of the refset in yyyy-MM-dd
	 *                         format
	 * @return the status of the operation
	 * @throws Exception the exception
	 */
	@Hidden
	@Operation(summary = "Publish a Ready for Publication local refset in a code system. This call requires authentication with the correct role.", responses = {
			@ApiResponse(responseCode = "200", description = "Successfully published the refset. The payload contains the status."),
			@ApiResponse(responseCode = "400", description = "Bad request"),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "403", description = "Forbidden"),
			@ApiResponse(responseCode = "404", description = "Resource not found") })
	@Parameters({
			@Parameter(name = "refsetInternalId", description = "The internal ID of the refset.", required = true),
			@Parameter(name = "versionDate", description = "the publication date of the refset (YYYY-MM-DD)", required = true) })
	@PutMapping("/admin/refset/{refsetInternalId}/publishLocalset")
	public @ResponseBody ResponseEntity<String> publishLocalsetRefset(
			@PathVariable(value = "refsetInternalId") final String refsetInternalId,
			@RequestParam(required = true) final String versionDate) throws Exception {

		final User authUser = authorizeUser();

		try (final TerminologyService service = new TerminologyService()) {

			service.setModifiedBy(authUser.getUserName());
			service.setModifiedFlag(true);

			final Refset refset = RefsetService.getRefset(service, authUser, refsetInternalId);
			final String organizationName = refset.getOrganizationName();
			final String editionName = refset.getEdition().getShortName();

			if (!authUser.checkPermission(User.ROLE_ADMIN, organizationName, editionName,
					refset.getProject().getCrowdProjectId())) {
				throw new RestException(false, 403, "Forbidden",
						"This user does not have permission to perform this action");
			}

			LOG.debug(
					"publishLocalsetRefset: refsetInternalId: " + refsetInternalId + " ; versionDate: " + versionDate);

			final List<String> refsetsNotUpdated = WorkflowService.completeRefsetPublication(service, refset,
					versionDate);
			String error = "";

			// see if there are any refsets that were unable to be updated and craft the
			// error message
			if (refsetsNotUpdated.size() > 0) {
				error = "Unable to complete publication for local reference set " + refset.getRefsetId();
			}

			if (error.equals("")) {

				final Refset newVersionRefset = RefsetService.getLatestRefsetVersion(service, refset.getRefsetId());

				// final String message = "Publication completed for local reference set " +
				// refset.getRefsetId();
				return new ResponseEntity<>(ModelUtility.toJson(newVersionRefset), HttpStatus.OK);

			}
			throw new Exception(error);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}

	}

	/**
	 * Set refsets that failed publication back to 'Ready For Edit' status. This
	 * call requires authentication with the correct role.
	 *
	 * @param refsetIds a comma separated list of refset IDs
	 * @param notes     the reason why the refsets failed
	 * @return the status of the operation
	 * @throws Exception the exception
	 */
	@Hidden
	@Operation(summary = "Set refsets that failed publication back to 'Ready For Edit' status. This call requires authentication with the correct role.", responses = {
			@ApiResponse(responseCode = "200", description = "Successfully changed the refset statuses. The payload contains the status."),
			@ApiResponse(responseCode = "400", description = "Bad request"),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "403", description = "Forbidden"),
			@ApiResponse(responseCode = "404", description = "Resource not found") })
	@Parameters({ @Parameter(name = "refsetIds", description = "A comma separated list of refset IDs", required = true),
			@Parameter(name = "notes", description = "The reason why the refsets failed", required = true) })
	@PutMapping("/admin/failRefsetPublications")
	public @ResponseBody ResponseEntity<String> failRefsetPublications(
			@RequestParam(required = true) final String refsetIds, @RequestParam(required = true) final String notes)
			throws Exception {

		final User authUser = authorizeUser();

		if (!authUser.checkPermission(User.ROLE_ADMIN, "all", null, null)) {
			throw new RestException(false, 403, "Forbidden",
					"This user does not have permission to perform this action");
		}

		try (final TerminologyService service = new TerminologyService()) {

			service.setModifiedBy(authUser.getUserName());
			service.setModifiedFlag(true);

			LOG.debug("failRefsetPublications: refset IDs: " + refsetIds + " ; notes: " + notes);

			final List<String> refsetsNotUpdated = WorkflowService.setBatchWorkflowStatusByAction(service, authUser,
					refsetIds, WorkflowService.FAILS_RVF, notes);
			String error = "";

			// see if there are any refsets that were unable to be updated and craft the
			// error message
			if (refsetsNotUpdated.size() > 0) {

				error = "Unable to update reference sets: ";

				for (final String unremovedConcept : refsetsNotUpdated) {

					error += unremovedConcept + ", ";
				}

				error = StringUtils.removeEnd(error, ", ");
			}

			if (error.equals("")) {

				return new ResponseEntity<>("{\"status\": \"All reference sets updated.\"}", HttpStatus.OK);
			} else {

				return new ResponseEntity<>("{\"error\": \"" + error + "\"}", HttpStatus.OK);
			}

		} catch (final Exception e) {
			handleException(e);
			return null;
		}

	}

	/**
	 * Create a new version of an existing refset in edit mode.
	 *
	 * @param refsetInternalId the internal refset ID
	 * @return the new refset internal ID or errors
	 * @throws Exception the exception
	 */
	@Operation(summary = "Create a new In Development version of an existing refset in edit mode. This call requires authentication with the correct role.", responses = {
			@ApiResponse(responseCode = "200", description = "Successfully created the new refset version. payload contains the internal ID of the new version."),
			@ApiResponse(responseCode = "400", description = "Bad request"),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "403", description = "Forbidden"),
			@ApiResponse(responseCode = "404", description = "Resource not found") })
	@Parameters({
			@Parameter(name = "refsetInternalId", description = "The internal ID of the refset.", required = true) })
	@PostMapping("/refset/{refsetInternalId}/newVersion")
	public @ResponseBody ResponseEntity<String> createNewRefsetVersion(
			@PathVariable(value = "refsetInternalId") final String refsetInternalId) throws Exception {

		final User authUser = authorizeUser();

		try (final TerminologyService service = new TerminologyService()) {

			service.setModifiedBy(authUser.getUserName());
			// service.setTransactionPerOperation(false);
			// service.beginTransaction();

			WorkflowService.canUserPerformWorkflowAction(authUser, null, refsetInternalId);
			final String newRefsetInternalId = RefsetService.createNewRefsetVersion(service, authUser, refsetInternalId,
					true);
			// service.commit();

			if (newRefsetInternalId.startsWith("Error")) {

				return new ResponseEntity<>("{\"error\": \"" + newRefsetInternalId + "\"}", HttpStatus.OK);
			}

			return new ResponseEntity<>("{\"refsetInternalId\": \"" + newRefsetInternalId + "\"}", HttpStatus.OK);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}

	}

	/**
	 * Change a refset status.
	 *
	 * @param refsetInternalId the internal refset ID
	 * @param active           is the refset active
	 * @return the status of the operation
	 * @throws Exception the exception
	 */
	@Operation(summary = "Change a refset status. This call requires authentication with the correct role.", responses = {
			@ApiResponse(responseCode = "200", description = "Successfully changed the refset status. payload contains the new status."),
			@ApiResponse(responseCode = "400", description = "Bad request"),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "403", description = "Forbidden"),
			@ApiResponse(responseCode = "404", description = "Resource not found") })
	@Parameters({
			@Parameter(name = "refsetInternalId", description = "The internal ID of the refset.", required = true),
			@Parameter(name = "active", description = "Is the refset active", required = true) })
	@PutMapping("/refset/{refsetInternalId}/refsetStatus")
	public @ResponseBody ResponseEntity<String> updateRefsetStatus(final @PathVariable String refsetInternalId,
			final boolean active) throws Exception {

		final User authUser = authorizeUser();
		try (final TerminologyService service = new TerminologyService()) {

			final Refset refset = RefsetService.getRefset(service, authUser, refsetInternalId);
			WorkflowService.canUserEditRefset(authUser, refset);

			service.setModifiedBy(authUser.getUserName());

			final String status = RefsetService.updatedRefsetStatus(service, authUser, refset, active);

			return new ResponseEntity<>("{\"status\": \"" + status + "\"}", HttpStatus.OK);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}

	}

	/**
	 * Convert intensional refset to extensional.
	 *
	 * @param refsetInternalId the internal refset ID
	 * @return the status of the operation
	 * @throws Exception the exception
	 */
	@Operation(summary = "Convert intensional refset to extensional. This call requires authentication with the correct role.", responses = {
			@ApiResponse(responseCode = "200", description = "Successfully converted the refset. Long running background process, call /refset/{refsetInternalId}/isLocked to get full status. "
					+ "Payload contains the status."),
			@ApiResponse(responseCode = "400", description = "Bad request"),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "403", description = "Forbidden"),
			@ApiResponse(responseCode = "404", description = "Resource not found") })
	@Parameters({
			@Parameter(name = "refsetInternalId", description = "The internal ID of the refset.", required = true), })
	@RequestMapping(method = RequestMethod.GET, value = "/refset/{refsetInternalId}/convert", produces = MediaType.APPLICATION_JSON)
	public @ResponseBody ResponseEntity<String> convertToExtensional(final @PathVariable String refsetInternalId)
			throws Exception {

		final User authUser = authorizeUser();
		try (final TerminologyService service = new TerminologyService()) {

			RefsetMemberService.REFSETS_BEING_UPDATED.add(refsetInternalId);
			RefsetMemberService.REFSETS_UPDATED_MEMBERS.put(refsetInternalId, new HashMap<>());

			final Refset refset = RefsetService.getRefset(service, authUser, refsetInternalId);
			WorkflowService.canUserEditRefset(authUser, refset);

			service.setModifiedBy(authUser.getUserName());

			final String status = RefsetService.convertToExtensional(service, authUser, refset);

			return new ResponseEntity<>("{\"status\": \"" + status + "\"}", HttpStatus.OK);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}

		finally {

			RefsetMemberService.REFSETS_BEING_UPDATED.remove(refsetInternalId);
		}

	}

	/**
	 * Delete the edit version of a refset.
	 *
	 * @param refsetInternalId the internal refset ID
	 * @return the status of the operation
	 * @throws Exception the exception
	 */
	@Operation(summary = "Delete the edit version of a refset. This call requires authentication with the correct role.", responses = {
			@ApiResponse(responseCode = "200", description = "Successfully deleted the refset edit version. payload contains the status."),
			@ApiResponse(responseCode = "400", description = "Bad request"),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "403", description = "Forbidden"),
			@ApiResponse(responseCode = "404", description = "Resource not found") })
	@Parameters({
			@Parameter(name = "refsetInternalId", description = "The internal ID of the refset.", required = true), })
	@DeleteMapping("/refset/{refsetInternalId}/editVersion")
	public @ResponseBody ResponseEntity<String> deleteRefsetEditVersion(final @PathVariable String refsetInternalId)
			throws Exception {

		final User authUser = authorizeUser();
		try (final TerminologyService service = new TerminologyService()) {

			final Refset refset = RefsetService.getRefset(service, authUser, refsetInternalId);
			WorkflowService.canUserPerformInDevelopmentActionsOnRefset(authUser, refset);

			service.setModifiedBy(authUser.getUserName());
			// service.setTransactionPerOperation(false);
			// service.beginTransaction();

			final String status = RefsetService.deleteInDevelopmentVersion(service, authUser, refset, true);
			// service.commit();
			return new ResponseEntity<>("{\"status\": \"" + status + "\"}", HttpStatus.OK);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}

	}

	/**
	 * Search Directory.
	 *
	 * @param searchParameters  the search parameters
	 * @param searchConcepts    the search concepts
	 * @param showInDevelopment flag on whether to include IN_DEVELOPMENT refsets
	 * @param countComments     the count comments
	 * @param showOnlyPermitted flag on whether to only show refsets user has
	 *                          specific permission to and not general public
	 *                          refsets
	 * @param bindingResult     the binding result
	 * @return the string
	 * @throws Exception the exception
	 */
	@Operation(summary = "Get refset search results. To see certain results this call requires authentication with the correct role.", responses = {
			@ApiResponse(responseCode = "200", description = "Successfully retrieved the requested information"),
			@ApiResponse(responseCode = "400", description = "Bad request"),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "403", description = "Forbidden"),
			@ApiResponse(responseCode = "404", description = "Resource not found"),
			@ApiResponse(responseCode = "417", description = "Expectation failed") })
	// @ModelAttribute API params documented in SearchParameter
	@Parameters({
			@Parameter(name = "query", description = "The term, phrase, or code to be searched, e.g. 'melanoma'", required = false, example = ""),
			@Parameter(name = "showInDevelopment", description = " A flag on whether to include IN_DEVELOPMENT refsets", required = false),
			@Parameter(name = "showOnlyPermitted", description = " A flag on whether to only show refsets user has specific permission to and not general public refsets", required = false) })
	@RecordMetric
	@RequestMapping(method = RequestMethod.GET, value = "/refset/search", produces = MediaType.APPLICATION_JSON)
	public @ResponseBody ResponseEntity<ResultList<Refset>> searchRefsets(final SearchParameters searchParameters,
			final boolean searchConcepts, @RequestParam(required = false) final Boolean showInDevelopment,
			@RequestParam(required = false) final Boolean countComments,
			@RequestParam(required = false) final Boolean showOnlyPermitted, final BindingResult bindingResult)
			throws Exception {

		// Check to make sure parameters were properly bound to variables.
		checkBinding(bindingResult);
		final User authUser = authorizeUser();

		final boolean includeInDevelopment = (showInDevelopment != null) ? showInDevelopment : true;
		final boolean onlyShowPermitted = (showOnlyPermitted != null) ? showOnlyPermitted : false;

		try (final TerminologyService service = new TerminologyService()) {

			RefsetService.getInDevelopmentBranchPaths(service);

			LOG.debug("searchRefsets searchParameters: " + ModelUtility.toJson(searchParameters) + "; searchConcepts: "
					+ searchConcepts + " ; showInDevelopment: " + includeInDevelopment + " ; countComments: "
					+ countComments);

			final ResultList<Refset> results = RefsetService.searchRefsets(authUser, service, searchParameters,
					searchConcepts, true, false, includeInDevelopment, onlyShowPermitted);

			if (countComments != null && countComments) {

				LOG.debug("searchRefsets: Including discussion count");
				DiscussionService.attachRefsetDiscussionCounts(service, authUser, results.getItems());
			}

			return new ResponseEntity<>(results, HttpStatus.OK);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}

	}

	/**
	 * Search Taxonomy for members.
	 *
	 * @param refsetInternalId the internal refset ID
	 * @param searchParameters the search parameters
	 * @param bindingResult    the binding result
	 * @param request          the request
	 * @return the string
	 * @throws Exception the exception
	 */
	@Operation(summary = "Search the taxonomy for refset members. To see certain results this call requires authentication with the correct role.", responses = {
			@ApiResponse(responseCode = "200", description = "Successfully retrieved the requested information"),
			@ApiResponse(responseCode = "400", description = "Bad request"),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "403", description = "Forbidden"),
			@ApiResponse(responseCode = "404", description = "Resource not found"),
			@ApiResponse(responseCode = "417", description = "Expectation failed") })
	// @ModelAttribute API params documented in SearchParameter
	@Parameters({ @Parameter(name = "refsetInternalId", description = "the internal refset ID", required = true) })
	@RecordMetric
	@RequestMapping(method = RequestMethod.GET, value = { "/refset/{refsetInternalId}/taxonomySearch",
			"/refset/{refsetInternalId}/conceptSearch" }, produces = MediaType.APPLICATION_JSON)
	public @ResponseBody ResponseEntity<ConceptResultList> searchConcepts(
			@PathVariable(value = "refsetInternalId") final String refsetInternalId,
			final SearchParameters searchParameters, final BindingResult bindingResult,
			final HttpServletRequest request) throws Exception {

		// Check to make sure parameters were properly bound to variables.
		checkBinding(bindingResult);
		final User authUser = authorizeUser();

		boolean searchRefsetMembers = false;
		final String uri = request.getRequestURI();

		if (uri.contains("taxonomySearch")) {

			searchRefsetMembers = true;
		}

		try (final TerminologyService service = new TerminologyService()) {

			ConceptResultList results = new ConceptResultList();
			final String query = searchParameters.getQuery();

			LOG.debug("taxonomySearch: searchConcepts: " + refsetInternalId + " ; searchParameters: "
					+ ModelUtility.toJson(searchParameters) + " ; searchRefsetMembers: " + searchRefsetMembers);

			if (query != null && !query.equals("")) {

				results = RefsetMemberService.prepareConceptSearch(service, authUser, refsetInternalId,
						searchParameters, searchRefsetMembers);
			}

			return new ResponseEntity<>(results, HttpStatus.OK);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}

	}

	/**
	 * Search for refset members.
	 *
	 * @param refsetInternalId   the internal refset ID
	 * @param searchParameters   the search parameters
	 * @param displayType        Should results be a list or hierarchical taxonomy
	 * @param taxonomyParameters the taxonomy parameters
	 * @param countComments      the count comments
	 * @param bindingResult      the binding result
	 * @return the string
	 * @throws Exception the exception
	 */
	@Operation(summary = "Search for refset members. To see certain results this call requires authentication with the correct role.", description = API_NOTES, responses = {
			@ApiResponse(responseCode = "200", description = "Successfully retrieved the requested information"),
			@ApiResponse(responseCode = "400", description = "Bad request"),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "403", description = "Forbidden"),
			@ApiResponse(responseCode = "404", description = "Resource not found"),
			@ApiResponse(responseCode = "417", description = "Expectation failed") })
	// @ModelAttribute API params documented in SearchParameter
	@Parameters({
			@Parameter(name = "query", description = "The term, phrase, or code to be searched, e.g. 'melanoma'", required = false),
			@Parameter(name = "displayType", description = "Should results be a list or taxonomy", required = true, example = "list"),
			@Parameter(name = "startingConceptId", description = "For taxonomy calls the starting concept ID (exclusive - get the children of this concept not the concept itself)", required = false),
			@Parameter(name = "depth", description = "For taxonomy calls the depth - how many levels of children or parents to retrieve", required = false),
			@Parameter(name = "returnChildren", description = "For taxonomy calls should children be returned. If false then parents will be returned", required = false, example = "true") })
	@RecordMetric
	@RequestMapping(method = RequestMethod.GET, value = "/refset/{refsetInternalId}/members", produces = MediaType.APPLICATION_JSON)
	public @ResponseBody ResponseEntity<ConceptResultList> getMembers(
			@PathVariable(value = "refsetInternalId") final String refsetInternalId,
			final SearchParameters searchParameters, final String displayType,
			final TaxonomyParameters taxonomyParameters, @RequestParam(required = false) final Boolean countComments,
			final BindingResult bindingResult) throws Exception {

		checkBinding(bindingResult);
		final User authUser = authorizeUser();

		try (final TerminologyService service = new TerminologyService()) {

			// no auth required
			final long start = System.currentTimeMillis();
			ConceptResultList results = new ConceptResultList();

			LOG.debug("getMembers: refsetInternalId: " + refsetInternalId + " ; searchParameters: + " + searchParameters
					+ " ; taxonomyParameters: " + taxonomyParameters + " ; displayType: " + displayType
					+ " ; countComments: " + countComments);

			final Refset refset = RefsetMemberService.getRefset(authUser, service, refsetInternalId);

			results = RefsetMemberService.getRefsetMembers(service, authUser, refsetInternalId, searchParameters,
					displayType, taxonomyParameters);

			if (countComments != null && countComments) {

				LOG.debug("getMembers: Including discussion count");
				DiscussionService.attachMemberDiscussionCounts(service, authUser, refset, results.getItems());
			}

			results.setTimeTaken(System.currentTimeMillis() - start);
			return new ResponseEntity<>(results, HttpStatus.OK);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}

	}

	/**
	 * Cache all ancestors for all members of a refset.
	 *
	 * @param refsetId    the refset ID
	 * @param versionDate the version date or IN DEVELOPMENT
	 * @return the success/failure
	 * @throws Exception the exception
	 */
	@Operation(summary = "Cache the ancestors of the refset members for the specified refset ID. To see certain results this call requires "
			+ "authentication with the correct role.", responses = {
					@ApiResponse(responseCode = "200", description = "Successfully populated the refset's ancestor cache. Payload contains the status"),
					@ApiResponse(responseCode = "400", description = "Bad request"),
					@ApiResponse(responseCode = "401", description = "Unauthorized"),
					@ApiResponse(responseCode = "403", description = "Forbidden"),
					@ApiResponse(responseCode = "404", description = "Resource not found") })
	@Parameters({
			@Parameter(name = "refsetId", description = "The ID of the refset for which ancestors are to be identified.", required = true),
			@Parameter(name = "versionDate", description = "The date of the refset version (YYYY-MM-DD) or IN DEVELOPMENT.", required = true), })
	@RecordMetric
	@RequestMapping(method = RequestMethod.GET, value = "/ancestors/{refsetId}/versionDate/{versionDate}", produces = MediaType.APPLICATION_JSON)
	public @ResponseBody ResponseEntity<String> cacheMemberAncestors(
			@PathVariable(value = "refsetId") final String refsetId,
			@PathVariable(value = "versionDate") final String versionDate) throws Exception {

		final User authUser = authorizeUser();
		try (final TerminologyService service = new TerminologyService()) {

			LOG.debug("cacheMemberAncestors: refsetId: " + refsetId + " ; versionDate: " + versionDate);

			String returnJson = "{\"success\": \"<RESULT>\"}";

			final boolean success = RefsetMemberService.cacheMemberAncestors(service, authUser, refsetId, versionDate);

			if (success) {

				returnJson = returnJson.replace("<RESULT>", "true");
			} else {

				returnJson = returnJson.replace("<RESULT>", "false");
			}

			// LOG.debug("cacheMemberAncestors results: " + returnJson);

			return new ResponseEntity<>(returnJson, HttpStatus.OK);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}

	}

	/**
	 * Export refset.
	 *
	 * @param refsetInternalId       the internal refset id
	 * @param format                 the format
	 * @param exportType             the export type
	 * @param languageId             the language to display names in
	 * @param fileNameDate           the file name date
	 * @param startEffectiveTime     the start effective time
	 * @param transientEffectiveTime the transient effective time
	 * @param exportMetadata         the export metadata
	 * @return the uri
	 * @throws Exception the exception
	 */
	@Operation(summary = "Export the refset for the specified ID. Payload contains the URL to download the export file. "
			+ "To see certain results this call requires authentication with the correct role.", responses = {
					@ApiResponse(responseCode = "200", description = "Successfully retrieved the requested information. Payload contains the URL to download the export file"),
					@ApiResponse(responseCode = "400", description = "Bad request"),
					@ApiResponse(responseCode = "401", description = "Unauthorized"),
					@ApiResponse(responseCode = "403", description = "Forbidden"),
					@ApiResponse(responseCode = "404", description = "Resource not found") })
	@Parameters({
			@Parameter(name = "refsetInternalId", description = "The internal ID of the refset to return.", required = true),
			@Parameter(name = "exportType", description = "The RF2 type SNAPSHOT or DELTA", required = false),
			@Parameter(name = "languageId", description = "For formats with names which language to display the name in.", required = false),
			@Parameter(name = "format", description = "The type of export: 'rf2', 'rf2_with_names', 'sctids' or 'freeset'.", required = true),
			@Parameter(name = "fileNameDate", description = "Format: yyyymmdd. Date to be embedded in the RF2 file names.", required = true),
			@Parameter(name = "startEffectiveTime", description = "Format: yyyymmdd. Can be used to produce a delta after content is versioned by filtering a SNAPSHOT export by effectiveTime.", required = false),
			@Parameter(name = "transientEffectiveTime", description = "Format: yyyymmdd. Add a transient effectiveTime to rows of content which are not yet versioned.", required = false),
			@Parameter(name = "exportMetadata", description = "e.g.  true or false", required = true), })
	@RecordMetric
	@RequestMapping(method = RequestMethod.GET, value = "/export/{refsetInternalId}", produces = MediaType.APPLICATION_JSON)
	public @ResponseBody ResponseEntity<String> exportRefset(
			@PathVariable(value = "refsetInternalId") final String refsetInternalId, final String format,
			final String exportType, final String languageId, final String fileNameDate,
			final String startEffectiveTime, final String transientEffectiveTime, final boolean exportMetadata)
			throws Exception {

		final User authUser = authorizeUser();
		try (final TerminologyService service = new TerminologyService()) {

			LOG.debug("exportRefset: refsetInternalId: " + refsetInternalId + " ; format: " + format + " ; type: "
					+ exportType + " ; fileNameDate: " + fileNameDate + " ; startEffectiveTime: " + startEffectiveTime
					+ " ; transientEffectiveTime: " + transientEffectiveTime + " ; exportMetadata: " + exportMetadata);

			String responseMessage = null;

			if ("rf2".equalsIgnoreCase(format) || "rf2_with_names".equalsIgnoreCase(format)) {

				final boolean withNames = ("rf2_with_names".equalsIgnoreCase(format));

				String downloadUri = "";

				if (exportType.contentEquals("SNAPSHOT")) {

					downloadUri = RefsetMemberService.exportRefsetRf2(service, refsetInternalId, exportType, languageId,
							fileNameDate, startEffectiveTime, transientEffectiveTime, exportMetadata, withNames);

				} else {

					downloadUri = RefsetMemberService.exportRefsetRf2Delta(service, authUser, refsetInternalId,
							exportType, languageId, fileNameDate, startEffectiveTime, transientEffectiveTime,
							exportMetadata, withNames);
				}

				LOG.debug("results: " + downloadUri);
				responseMessage = "{\"url\": \"" + downloadUri + "\"}";

			} else if (format.equals("sctids")) {

				final String downloadUri = RefsetMemberService.exportRefsetSctidList(service, refsetInternalId,
						exportMetadata);
				responseMessage = "{\"url\": \"" + downloadUri + "\"}";

			} else if ("freeset".equals(format)) {

				final String downloadUri = RefsetMemberService.exportFreeset(service, refsetInternalId, languageId);
				responseMessage = "{\"url\": \"" + downloadUri + "\"}";
			}

			return new ResponseEntity<>(responseMessage, HttpStatus.OK);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}

	}

	/**
	 * Export all published refsets for a project.
	 *
	 * @param projectId      the project id
	 * @param format         the format
	 * @param languageId     the language to display names in
	 * @param fileNameDate   the file name date
	 * @param exportMetadata the export metadata
	 * @return the uri
	 * @throws Exception the exception
	 */
	@Operation(summary = "Export the all latest version of all published refsets for the project. Payload contains the URL to download the export file. "
			+ "To see certain results this call requires authentication with the correct role.", responses = {
					@ApiResponse(responseCode = "200", description = "Successfully retrieved the requested information. Payload contains the URL to download the export file"),
					@ApiResponse(responseCode = "400", description = "Bad request"),
					@ApiResponse(responseCode = "401", description = "Unauthorized"),
					@ApiResponse(responseCode = "403", description = "Forbidden"),
					@ApiResponse(responseCode = "404", description = "Resource not found") })
	@Parameters({
			@Parameter(name = "projectId", description = "The id of the project to export refsets.", required = true),
			@Parameter(name = "languageId", description = "For formats with names which language to display the name in.", required = false),
			@Parameter(name = "fileNameDate", description = "Format: yyyymmdd. Date to be embedded in the RF2 file names.", required = true),
			@Parameter(name = "format", description = "The type of export: 'rf2', 'rf2_with_names'", required = true),
			@Parameter(name = "exportMetadata", description = "e.g.  true or false", required = true) })
	@RecordMetric
	@RequestMapping(method = RequestMethod.GET, value = "/export/project/{projectId}", produces = MediaType.APPLICATION_JSON)
	public @ResponseBody ResponseEntity<String> exportAllRefsetsForProject(
			@PathVariable(value = "projectId") final String projectId, final String format, final String languageId,
			final String fileNameDate, final boolean exportMetadata) throws Exception {

		final User authUser = authorizeUser();
		try (final TerminologyService service = new TerminologyService()) {

			LOG.debug("exportAllRefsetsForProject: projectId: " + projectId + " ; fileNameDate: " + fileNameDate);
			final boolean withNames = ("rf2_with_names".equalsIgnoreCase(format));

			final String downloadUri = RefsetMemberService.exportAllRefsetsRf2ForProject(service, authUser, projectId,
					"snapshot", languageId, fileNameDate, exportMetadata, withNames);
			final String responseMessage = "{\"url\": \"" + downloadUri + "\"}";

			return new ResponseEntity<>(responseMessage, HttpStatus.OK);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}

	}

	/**
	 * Download an exported refset.
	 *
	 * @param fileName the file name
	 * @return the file
	 * @throws Exception the exception
	 */
	@Operation(summary = "Download the specified refset export file", responses = {
			@ApiResponse(responseCode = "200", description = "Successfully retrieved the requested information"),
			@ApiResponse(responseCode = "400", description = "Bad request"),
			@ApiResponse(responseCode = "404", description = "Resource not found") })
	@Parameters({ @Parameter(name = "fileName", description = "The name of the file to download.", required = true) })
	@RecordMetric
	@RequestMapping(method = RequestMethod.GET, value = "/export/download/{fileName}", produces = MediaType.APPLICATION_JSON)
	public @ResponseBody ResponseEntity<Resource> downloadExport(
			@PathVariable(value = "fileName") final String fileName) throws Exception {

		authorizeUser();
		try {

			final Path filePath = Paths.get(exportFileDir + fileName);
			final Resource file = new UrlResource(filePath.toUri());

			if (!file.exists() || !file.isReadable()) {

				throw new RuntimeException("Could not read the file!");
			}

			return ResponseEntity.ok()
					.header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
					.header(HttpHeaders.CONTENT_TYPE, Files.probeContentType(filePath))
					.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getFilename() + "\"")
					.contentLength(file.contentLength()).body(file);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}

	}

	/**
	 * Gets member history.
	 *
	 * @param refsetInternalId the refset internal id
	 * @param conceptId        the member id
	 * @return the member history
	 * @throws Exception the exception
	 */
	@Operation(summary = "Get the member history for the specified ID. To see certain results this call requires authentication with the correct role.", responses = {
			@ApiResponse(responseCode = "200", description = "Successfully retrieved the requested information"),
			@ApiResponse(responseCode = "400", description = "Bad request"),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "403", description = "Forbidden"),
			@ApiResponse(responseCode = "404", description = "Resource not found") })
	@Parameters({
			@Parameter(name = "refsetInternalId", description = "The internal ID of the refset to return.", required = true),
			@Parameter(name = "conceptId", description = "The ID of the member to return.", required = true), })
	@RecordMetric
	@RequestMapping(method = RequestMethod.GET, value = "/refset/{refsetInternalId}/member/{conceptId}", produces = MediaType.APPLICATION_JSON)
	public @ResponseBody ResponseEntity<ResultList<Map<String, String>>> getMemberHistory(
			@PathVariable(value = "refsetInternalId") final String refsetInternalId,
			@PathVariable(value = "conceptId") final String conceptId) throws Exception {

		final User authUser = authorizeUser();

		LOG.debug("getMemberHistory: memberId: " + conceptId + "; refsetInternalId: " + refsetInternalId);

		try (final TerminologyService service = new TerminologyService()) {

			final Refset refset = service.findSingle("id:" + QueryParserBase.escape(refsetInternalId) + "",
					Refset.class, null);

			if (refset == null) {

				throw new Exception("Unable to retrieve reference set " + refsetInternalId);
			}

			RefsetService.setRefsetPermissions(authUser, refset);
			final List<Map<String, String>> versions = RefsetService.getSortedRefsetVersionList(refset, service, true);

			final List<Map<String, String>> memberHistory = RefsetMemberService.getMemberHistory(service, conceptId,
					versions);

			LOG.debug("getMemberHistory: member: " + ModelUtility.toJson(memberHistory));

			final ResultList<Map<String, String>> results = new ResultList<>(memberHistory);
			results.setTotalKnown(true);

			return new ResponseEntity<>(results, HttpStatus.OK);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}

	}

	/**
	 * Gets the concept details.
	 *
	 * @param conceptId        the concept id
	 * @param refsetInternalId the refset internal id
	 * @return the concept details
	 * @throws Exception the exception
	 */
	@Operation(summary = "Get the concept for the specified ID", responses = {
			@ApiResponse(responseCode = "200", description = "Successfully retrieved the requested information"),
			@ApiResponse(responseCode = "400", description = "Bad request"),
			@ApiResponse(responseCode = "404", description = "Resource not found") })
	@Parameters({ @Parameter(name = "conceptId", description = "The ID of the concept to return.", required = true),
			@Parameter(name = "refsetInternalId", description = "The internal ID of the refset to return.", required = true), })
	@RecordMetric
	@RequestMapping(method = RequestMethod.GET, value = "/concept/{conceptId}", produces = MediaType.APPLICATION_JSON)
	public @ResponseBody ResponseEntity<Concept> getConceptDetails(
			@PathVariable(value = "conceptId") final String conceptId, final String refsetInternalId) throws Exception {

		authorizeUser();

		// LOG.debug("getConceptDetails: conceptId: " + conceptId + ";
		// refsetInternalId: " + refsetInternalId);

		try (final TerminologyService service = new TerminologyService()) {

			final Refset refset = service.findSingle("id:" + QueryParserBase.escape(refsetInternalId) + "",
					Refset.class, null);

			if (refset == null) {

				throw new Exception("Unable to retrieve reference set " + refsetInternalId);
			}

			final Concept concept = RefsetMemberService.getConceptDetails(conceptId, refset);

			// LOG.debug("getConceptDetails: concept: " + ModelUtility.toJson(concept));

			return new ResponseEntity<>(concept, HttpStatus.OK);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}

	}

	/**
	 * Syncs RTT data into the database but only if the database is empty. This call
	 * requires authentication with the correct role.
	 *
	 * @param perVersionCreation If true, create a refset for every version created.
	 *                           If false, only when changes are observed.
	 * @param forProduction      the for production
	 * @param ignoreCoreRefsets  the ignore core refsets
	 * @return the status of the sync
	 * @throws Exception the exception
	 */
	@Hidden
	@RequestMapping(method = RequestMethod.GET, value = "/admin/sync/rtt", produces = MediaType.APPLICATION_JSON)
	public @ResponseBody ResponseEntity<String> syncRttData(
			@RequestParam(required = false) final Boolean perVersionCreation,
			@RequestParam(required = false) final Boolean forProduction,
			@RequestParam(required = false) final Boolean ignoreCoreRefsets) throws Exception {

		return syncSnowstorm(perVersionCreation, forProduction, ignoreCoreRefsets);
	}

	/**
	 * Sync against snowstorm still relying upon latest RTT data files to sync.
	 * Compares against all of a given refets's versions on snowstorm, so no need
	 * for a quickSync option. This call requires authentication with the correct
	 * role.
	 * 
	 * TODO: Determine if can do a nightly update of data files programmatically
	 *
	 * @param perVersionCreation If true, create a refset for every version created.
	 *                           If false, only when changes are observed.
	 * @param forProduction      Should the sync add projects, teams, and other
	 *                           testing data, which it should NOT do for
	 *                           Production. Default is true
	 * @param ignoreCoreRefsets  the ignore core refsets
	 * @return the status of the sync
	 * @throws Exception the exception
	 */
	@Hidden
	@RequestMapping(method = RequestMethod.GET, value = "/admin/sync/snowstorm", produces = MediaType.APPLICATION_JSON)
	public @ResponseBody ResponseEntity<String> syncSnowstorm(
			@RequestParam(required = false) final Boolean perVersionCreation,
			@RequestParam(required = false) final Boolean forProduction,
			@RequestParam(required = false) final Boolean ignoreCoreRefsets) throws Exception {

		final User authUser = authorizeUser();

		if (!authUser.checkPermission(User.ROLE_ADMIN, "all", null, null)) {
			throw new RestException(false, 403, "Forbidden",
					"This user does not have permission to perform this action");
		}

		final String message = "";

		try {

			boolean refsetPerVersionSync = false;
			boolean runForProduction = false;
			boolean isIgnoreCoreRefsets = false;

			if (perVersionCreation != null && perVersionCreation.booleanValue()) {

				LOG.info("!!!!! syncSnowstorm RUNNING QUICK SYNC - WILL HAVE MORE THAN ONLY PUBLISHED REFSET VERSIONS");
				refsetPerVersionSync = true;
			}

			if (forProduction != null && forProduction.booleanValue()) {

				LOG.info(
						"!!!!! syncSnowstorm RUNNING SYNC ON PRODUCTION - SHOULDN'T CONTAIN TESTING PROJECTS, TEAMS, AND REFSETS");
				runForProduction = true;
			}

			if (ignoreCoreRefsets != null && ignoreCoreRefsets.booleanValue()) {

				LOG.info("!!!!! syncSnowstorm IGNORING SNOMED INTERNATIONAL Refsets");
				isIgnoreCoreRefsets = true;
			}

			try (final TerminologyService service = new TerminologyService()) {

				service.setModifiedBy("Sync");
				service.setModifiedFlag(true);

				SyncAgent.sync(service, refsetPerVersionSync, runForProduction, isIgnoreCoreRefsets);

				return new ResponseEntity<>(message + "RT2 synced with Snowstorm successfully", HttpStatus.OK);
			}

		} catch (final Exception e) {
			handleException(e);
			return null;
		}

	}

	/**
	 * Creates a new testing refset containing initial feedback. The method will
	 * identify the last refset created for this purpose (based on numbering). It
	 * will create a new one, with the same initial feedback content, but with an
	 * incremented number appended to the name and refsetId. This call requires
	 * authentication with the correct role.
	 *
	 * @return the status of the creation
	 * @throws Exception the exception
	 */
	@Hidden
	@RequestMapping(method = RequestMethod.GET, value = "/admin/sync/feedback", produces = MediaType.APPLICATION_JSON)
	public @ResponseBody ResponseEntity<String> createNewFeedbackTestingRefset() throws Exception {

		final User authUser = authorizeUser();
		try {

			if (!authUser.checkPermission(User.ROLE_ADMIN, "all", null, null)) {
				throw new RestException(false, 403, "Forbidden",
						"This user does not have permission to perform this action");
			}

			final String status = "Feedback testing refset created successfully";
			LOG.info("Create new refset, initialized with feedback, for testing purposes");
			final SyncTestingInitializer initializer = new SyncTestingInitializer();
			final Refset refset = initializer.createTestingFeedbackRefset();

			LOG.info("New Feedback testing refset created succesffully with internal/SctiId pair: " + refset.getId()
					+ "/" + refset.getRefsetId());

			return new ResponseEntity<>(status, HttpStatus.OK);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}

	}

	/**
	 * Creates a new testing refset for testing intensional functionality. The
	 * method will identify the last refset created for this purpose (based on
	 * numbering). It will create a new one, similarly as intensionsal, but with an
	 * incremented number appended to the name and refsetId. This call requires
	 * authentication with the correct role.
	 *
	 * @return the status of the creation
	 * @throws Exception the exception
	 */
	@Hidden
	@RequestMapping(method = RequestMethod.GET, value = "/admin/sync/intensional", produces = MediaType.APPLICATION_JSON)
	public @ResponseBody ResponseEntity<String> createNewIntensionalTestingRefset() throws Exception {

		final User authUser = authorizeUser();
		try {

			if (!authUser.checkPermission(User.ROLE_ADMIN, "all", null, null)) {
				throw new RestException(false, 403, "Forbidden",
						"This user does not have permission to perform this action");
			}

			final String status = "Intensional testing refset created successfully";
			LOG.info("Create new intensional refset for testing purposes");
			final SyncTestingInitializer initializer = new SyncTestingInitializer();
			final Refset refset = initializer.createTestingIntensionalRefset();

			LOG.info("New Feedback testing refset created succesffully with internal/SctiId pair: " + refset.getId()
					+ "/" + refset.getRefsetId());

			return new ResponseEntity<>(status, HttpStatus.OK);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}

	}

	/**
	 * Gets the version statuses.
	 *
	 * @return the version statuses
	 * @throws Exception the exception
	 */
	@Operation(summary = "Gets a list of possible version statuses", responses = {
			@ApiResponse(responseCode = "200", description = "Successfully retrieved the requested information"),
			@ApiResponse(responseCode = "400", description = "Bad request"),
			@ApiResponse(responseCode = "404", description = "Resource not found") })
	@RecordMetric
	@RequestMapping(method = RequestMethod.GET, value = "/refset/versionStatuses", produces = MediaType.APPLICATION_JSON)
	public @ResponseBody ResponseEntity<ResultList<TypeKeyValue>> getVersionStatuses() throws Exception {

		authorizeUser();

		try (final TerminologyService service = new TerminologyService()) {

			final List<TypeKeyValue> versionStatuses = new ArrayList<>();

			for (final VersionStatus value : VersionStatus.values()) {

				final TypeKeyValue typeKeyValue = new TypeKeyValue("status", value.getLabel(), value.getLabel());
				versionStatuses.add(typeKeyValue);
			}

			final ResultList<TypeKeyValue> results = new ResultList<>(versionStatuses);
			results.setTotalKnown(true);

			return new ResponseEntity<>(results, HttpStatus.OK);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}

	}

	/**
	 * Gets the versions.
	 *
	 * @return the versions
	 * @throws Exception the exception
	 */
	@Operation(summary = "Gets a list of all current refset versions", responses = {
			@ApiResponse(responseCode = "200", description = "Successfully retrieved the requested information"),
			@ApiResponse(responseCode = "400", description = "Bad request"),
			@ApiResponse(responseCode = "404", description = "Resource not found") })
	@RecordMetric
	@RequestMapping(method = RequestMethod.GET, value = "/refset/versions", produces = MediaType.APPLICATION_JSON)
	public @ResponseBody ResponseEntity<ResultList<TypeKeyValue>> getVersions() throws Exception {

		authorizeUser();

		try (final TerminologyService service = new TerminologyService()) {

			ResultList<Refset> refsets = new ResultList<Refset>();
			final PfsParameter pfs = new PfsParameter();
			final QueryParameter query = new QueryParameter();
			query.setQuery("_exists_:versionDate AND latestPublishedVersion: true");

			refsets = service.find(query, pfs, Refset.class, null);

			final ResultList<TypeKeyValue> results = new ResultList<>();
			final List<TypeKeyValue> resultItems = new ArrayList<>();
			final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

			for (final Refset refset : refsets.getItems()) {

				final String version = sdf.format(refset.getVersionDate());
				final TypeKeyValue entry = new TypeKeyValue("version", version, version);

				if (!resultItems.contains(entry)) {

					resultItems.add(entry);
				}

			}

			resultItems.sort(new Comparator<TypeKeyValue>() {

				@Override
				public int compare(final TypeKeyValue o1, final TypeKeyValue o2) {

					return o2.getValue().compareTo(o1.getValue());
				}

			});
			results.setItems(resultItems);
			results.setTotal(resultItems.size());

			return new ResponseEntity<>(results, HttpStatus.OK);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}

	}

	/**
	 * Gets the editions.
	 *
	 * @param onlyEditionsWithoutOrganizations should the results be limited to
	 *                                         editions that do not have an
	 *                                         organization tied to them
	 * @return the editions
	 * @throws Exception the exception
	 */
	@Operation(summary = "Gets a list of all the editions", responses = {
			@ApiResponse(responseCode = "200", description = "Successfully retrieved the requested information"),
			@ApiResponse(responseCode = "400", description = "Bad request"),
			@ApiResponse(responseCode = "404", description = "Resource not found") })
	@Parameters({
			@Parameter(name = "onlyEditionsWithoutOrganizations", description = "Should the results be limited to editions that do not have an organization tied to them.", required = false), })
	@RecordMetric
	@RequestMapping(method = RequestMethod.GET, value = "/refset/editions", produces = MediaType.APPLICATION_JSON)
	public @ResponseBody ResponseEntity<ResultList<TypeKeyValue>> getEditions(
			@RequestParam(value = "onlyUsersTeams") final boolean onlyEditionsWithoutOrganizations) throws Exception {

		final User authUser = authorizeUser();

		try (final TerminologyService service = new TerminologyService()) {

			final long start = System.currentTimeMillis();
			ResultList<Edition> results = new ResultList<Edition>();
			final PfsParameter pfs = new PfsParameter();
			final QueryParameter query = new QueryParameter();
			List<Organization> organizationList = new ArrayList<>();

			if (onlyEditionsWithoutOrganizations) {

				organizationList = OrganizationService
						.searchOrganizations(service, authUser, new SearchParameters(), false).getItems();
			}

			results = service.find(query, pfs, Edition.class, null);

			results.setTimeTaken(System.currentTimeMillis() - start);
			results.setTotalKnown(true);

			LOG.debug("getEditions results: " + ModelUtility.toJson(results));
			final List<Edition> editionList = results.getItems();
			editionList.sort(new Comparator<Edition>() {

				@Override
				public int compare(final Edition o1, final Edition o2) {

					return o1.getName().compareTo(o2.getName());
				}
			});
			final List<TypeKeyValue> entryList = new ArrayList<>();
			final ResultList<TypeKeyValue> entryResults = new ResultList<>();

			for (final Edition edition : editionList) {

				// if this flag is set skip any edition already tied to an organization
				if (onlyEditionsWithoutOrganizations) {

					boolean hasOrganization = false;

					for (final Organization organization : organizationList) {

						if (edition.getOrganization().getId().equals(organization.getId())) {

							hasOrganization = true;
						}

					}

					if (hasOrganization) {

						continue;
					}

				}

				final TypeKeyValue tkv = new TypeKeyValue("edition", edition.getName(), edition.getName());
				tkv.setId(edition.getId());
				entryList.add(tkv);
			}

			entryResults.setItems(entryList);
			entryResults.setTotalKnown(true);

			return new ResponseEntity<>(entryResults, HttpStatus.OK);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}

	}

	/**
	 * Gets the list of Refset Concepts that can be used as parents to a refset or
	 * as the underlying concept for a new refset.
	 *
	 * @param branch            the branch to retrieve the concepts from
	 * @param areParentConcepts Do these concepts represent parent concepts for a
	 *                          new refset, or will they be the underlying concepts
	 *                          for a the refset itself
	 * @return the editions
	 * @throws Exception the exception
	 */
	@Operation(summary = "Gets the list of Refset Concepts that can be used as parents to a refset or as the underlying concept for a new refset.", responses = {
			@ApiResponse(responseCode = "200", description = "Successfully retrieved the requested information"),
			@ApiResponse(responseCode = "400", description = "Bad request"),
			@ApiResponse(responseCode = "404", description = "Resource not found") })
	@Parameters({
			@Parameter(name = "branch", description = "The branch to retrieve the concepts from.", required = true),
			@Parameter(name = "areParentConcepts", description = "Do these concepts represent parent concepts for a new refset, or will they be the underlying concepts for a the refset itself.", required = true), })
	@RecordMetric
	@RequestMapping(method = RequestMethod.GET, value = "/general/refsetConcepts", produces = MediaType.APPLICATION_JSON)
	public @ResponseBody ResponseEntity<ConceptResultList> getRefsetConcepts(final String branch,
			final boolean areParentConcepts) throws Exception {

		authorizeUser();
		try (final TerminologyService service = new TerminologyService()) {

			// LOG.debug("getRefsetConcepts: branch: " + branch + "; areParentConcepts: "
			// + areParentConcepts);

			final ConceptResultList results = RefsetService.getRefsetConcepts(service, branch, areParentConcepts);

			// LOG.debug("getRefsetConcepts: results: " + ModelUtility.toJson(results));

			return new ResponseEntity<>(results, HttpStatus.OK);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}

	}

	/**
	 * Gets the list of branch versions.
	 *
	 * @param branch the branch to get versions from
	 * @return list of edition versions
	 * @throws Exception the exception
	 */
	@Operation(summary = "Gets the list of branch versions", responses = {
			@ApiResponse(responseCode = "200", description = "Successfully retrieved the requested information"),
			@ApiResponse(responseCode = "400", description = "Bad request"),
			@ApiResponse(responseCode = "404", description = "Resource not found") })
	@Parameters({
			@Parameter(name = "branch", description = "The branch to retrieve the concepts from.", required = true) })
	@RecordMetric
	@RequestMapping(method = RequestMethod.GET, value = "/general/branchVersions", produces = MediaType.APPLICATION_JSON)
	public @ResponseBody ResponseEntity<ResultList<String>> getBranchVersions(final String branch) throws Exception {

		authorizeUser();
		try {
			final ResultList<String> results = new ResultList<>();
			results.setItems(RefsetService.getBranchVersions(branch));

			// LOG.debug("getBranchVersions - results: " + results);

			return new ResponseEntity<>(results, HttpStatus.OK);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}

	}

	/**
	 * Gets the Organizations.
	 *
	 * @return the organizations
	 * @throws Exception the exception
	 */
	@Operation(summary = "Gets a list of the organizations", responses = {
			@ApiResponse(responseCode = "200", description = "Successfully retrieved the requested information"),
			@ApiResponse(responseCode = "400", description = "Bad request"),
			@ApiResponse(responseCode = "404", description = "Resource not found") })
	@RecordMetric
	@RequestMapping(method = RequestMethod.GET, value = "/refset/organizations", produces = MediaType.APPLICATION_JSON)
	public @ResponseBody ResponseEntity<ResultList<TypeKeyValue>> getOrganizations() throws Exception {

		final User authUser = authorizeUser();

		try (final TerminologyService service = new TerminologyService()) {

			final long start = System.currentTimeMillis();
			ResultList<Organization> results = new ResultList<Organization>();
			final PfsParameter pfs = new PfsParameter();

			String query = "active:true";

			if (SecurityService.GUEST_USERNAME.equals(authUser.getUserName())) {
				query += " AND affiliate:false ";
			}

			final QueryParameter queryParameter = new QueryParameter();
			queryParameter.setQuery(query);

			results = service.find(query, pfs, Organization.class, null);

			results.setTimeTaken(System.currentTimeMillis() - start);
			results.setTotalKnown(true);

			// LOG.debug("results: " + ModelUtility.toJson(results));
			final List<Organization> organizationList = results.getItems();

			organizationList.removeIf(org -> {
				return org.isAffiliate()
						&& !org.getMembers().stream().anyMatch(m -> m.getId().equals(authUser.getId()));
			});

			results.setTotal(organizationList.size());

			organizationList.sort(new Comparator<Organization>() {

				@Override
				public int compare(final Organization o1, final Organization o2) {

					return o1.getName().compareTo(o2.getName());
				}
			});
			final List<TypeKeyValue> entryList = new ArrayList<>();
			final ResultList<TypeKeyValue> entryResults = new ResultList<>();

			for (final Organization organization : organizationList) {

				final TypeKeyValue tkv = new TypeKeyValue("organization", organization.getName(),
						organization.getName());
				tkv.setId(organization.getId());
				entryList.add(tkv);
			}

			entryResults.setItems(entryList);
			entryResults.setTotalKnown(true);

			return new ResponseEntity<>(entryResults, HttpStatus.OK);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}

	}

	/**
	 * Returns the contents of the ancestor cache for a refset.
	 *
	 * @param refsetInternalId the internal refset ID
	 * @return the ancestor cache
	 * @throws Exception the exception
	 */
	@Operation(summary = "Returns the contents of the ancestor cache for a refset. To see certain results this call requires authentication with the correct role.", responses = {
			@ApiResponse(responseCode = "200", description = "Successfully retrieved the requested information. Payload contains a set of concept IDs that are ancestors "
					+ "to the members of this refset."),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "403", description = "Forbidden"),
			@ApiResponse(responseCode = "400", description = "Bad request"),
			@ApiResponse(responseCode = "404", description = "Resource not found") })
	@Parameters({
			@Parameter(name = "refsetInternalId", description = "The internal ID of the refset.", required = true), })
	@RecordMetric
	@RequestMapping(method = RequestMethod.GET, value = "/refset/{refsetInternalId}/ancestorCache", produces = MediaType.APPLICATION_JSON)
	public @ResponseBody ResponseEntity<String> getRefsetAncestorCache(
			@PathVariable(value = "refsetInternalId") final String refsetInternalId) throws Exception {

		final User authUser = authorizeUser();

		try (final TerminologyService service = new TerminologyService()) {

			LOG.debug("getRefsetAncestorCache: refsetInternalId: " + refsetInternalId);

			final Refset refset = RefsetMemberService.getRefset(authUser, service, refsetInternalId);
			final Map<String, Set<String>> ancestorsCache = RefsetMemberService
					.getCacheForMemberAncestors(RefsetMemberService.getBranchPath(refset));

			if (ancestorsCache.containsKey(refsetInternalId)) {

				return new ResponseEntity<>(ModelUtility.toJson(ancestorsCache.get(refsetInternalId)), HttpStatus.OK);
			} else {

				return new ResponseEntity<>("Not Cached", HttpStatus.OK);
			}

		} catch (final Exception e) {
			handleException(e);
			return null;
		}

	}

	/**
	 * Returns the ancestor path concepts for a refset member.
	 *
	 * @param refsetInternalId the internal refset ID
	 * @param conceptId        the ID of the member concept
	 * @return the concept with the ancestor path filled in
	 * @throws Exception the exception
	 */
	@Operation(summary = "Returns the ancestor path concepts for a refset member. To see certain results this call requires authentication with the correct role.", responses = {
			@ApiResponse(responseCode = "200", description = "Successfully retrieved the requested information"),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "403", description = "Forbidden"),
			@ApiResponse(responseCode = "400", description = "Bad request"),
			@ApiResponse(responseCode = "404", description = "Resource not found") })
	@Parameters({
			@Parameter(name = "refsetInternalId", description = "The internal ID of the refset.", required = true),
			@Parameter(name = "conceptId", description = "The ID of the member concept.", required = true) })
	@RecordMetric
	@RequestMapping(method = RequestMethod.GET, value = "/refset/{refsetInternalId}/member/{conceptId}/ancestorConcepts", produces = MediaType.APPLICATION_JSON)
	public @ResponseBody ResponseEntity<Concept> getMemberAncestorConcepts(
			@PathVariable(value = "refsetInternalId") final String refsetInternalId,
			@PathVariable(value = "conceptId") final String conceptId) throws Exception {

		final User authUser = authorizeUser();
		try (final TerminologyService service = new TerminologyService()) {

			LOG.debug(
					"getMemberAncestorConcepts: refsetInternalId: " + refsetInternalId + " ; conceptId: " + conceptId);

			final Refset refset = RefsetService.getRefset(service, authUser, refsetInternalId);

			final Concept concept = RefsetMemberService.getConceptAncestors(refset, conceptId);

			return new ResponseEntity<>(concept, HttpStatus.OK);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}

	}

	/**
	 * Compile and store the data to upgrade a list of refsets.
	 *
	 * @param refsetInternalIds a list of comma separated internal refset IDs to
	 *                          upgrade
	 * @return The operation status
	 * @throws Exception the exception
	 */
	@Operation(summary = "Compile and store the data to upgrade a list of refsets. Long running background process, call /refset/{refsetInternalId}/isLocked "
			+ "to get full status. This call requires authentication with the correct role.", responses = {
					@ApiResponse(responseCode = "200", description = "Successfully retrieved the requested information. Payload contains the status of the operation"),
					@ApiResponse(responseCode = "401", description = "Unauthorized"),
					@ApiResponse(responseCode = "403", description = "Forbidden"),
					@ApiResponse(responseCode = "400", description = "Bad request"),
					@ApiResponse(responseCode = "404", description = "Resource not found") })
	@Parameters({
			@Parameter(name = "refsetInternalId", description = "A list of comma separated internal refset IDs to upgrade.", required = true) })
	@RecordMetric
	@RequestMapping(method = RequestMethod.GET, value = "/refset/{refsetInternalIds}/compileUpgradeData", produces = MediaType.APPLICATION_JSON)
	public @ResponseBody ResponseEntity<String> compileUpgradeData(
			@PathVariable(value = "refsetInternalIds") final String refsetInternalIds) throws Exception {

		final User authUser = authorizeUser();

		final Thread t = new Thread(new Runnable() {

			@Override
			public void run() {

				try (final TerminologyService service = new TerminologyService()) {

					service.setModifiedBy(authUser.getUserName());
					service.setModifiedFlag(true);

					String status = "";

					final String[] refsetInternalIdArray = refsetInternalIds.split(",");
					boolean isBatch = false;

					if (refsetInternalIdArray.length > 1) {

						isBatch = true;
						RefsetMemberService.REFSETS_BEING_UPDATED.add(refsetInternalIds);
						LOG.debug("compileUpgradeData: Batch upgrade started with refsetInternalIds: "
								+ refsetInternalIds);
					}

					for (final String internalId : refsetInternalIdArray) {

						RefsetMemberService.REFSETS_BEING_UPDATED.add(internalId);
						LOG.debug("compileUpgradeData: individual refsetInternalId: " + internalId);

						try {

							// add the list of concepts as members to the refset
							status = RefsetMemberService.compileUpgradeData(service, authUser, internalId);

						} finally {
							RefsetMemberService.REFSETS_BEING_UPDATED.remove(internalId);
						}

						LOG.debug("compileUpgradeData: individual refsetInternalId " + internalId
								+ " finished with status " + status);
					}

					if (isBatch) {
						LOG.debug("compileUpgradeData: Batch upgrade finished");
					}

				} catch (final Exception e) {
					try {
						handleException(e);
					} catch (final Exception e1) {
						// n/a - in thread
					}
				}

				finally {

					RefsetMemberService.REFSETS_BEING_UPDATED.remove(refsetInternalIds);
				}

			}
		});
		t.start();
		return new ResponseEntity<>("{\"status\": \"started\"}", HttpStatus.OK);

	}

	/**
	 * Get the stored the data to upgrade a refset.
	 *
	 * @param refsetInternalId the internal refset ID
	 * @return The upgrade data
	 * @throws Exception the exception
	 */
	@Operation(summary = "Get the stored the data to upgrade a refset. This call requires authentication with the correct role.", responses = {
			@ApiResponse(responseCode = "200", description = "Successfully retrieved the requested information"),
			@ApiResponse(responseCode = "400", description = "Bad request"),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "403", description = "Forbidden"),
			@ApiResponse(responseCode = "404", description = "Resource not found") })
	@Parameters({
			@Parameter(name = "refsetInternalId", description = "The internal ID of the refset.", required = true) })
	@RecordMetric
	@RequestMapping(method = RequestMethod.GET, value = "/refset/{refsetInternalId}/upgradeData", produces = MediaType.APPLICATION_JSON)
	public @ResponseBody ResponseEntity<ResultList<UpgradeInactiveConcept>> getUpgradeData(
			@PathVariable(value = "refsetInternalId") final String refsetInternalId) throws Exception {

		final User authUser = authorizeUser();

		try (final TerminologyService service = new TerminologyService()) {

			LOG.debug("getUpgradeData: refsetInternalId: " + refsetInternalId);

			final Refset refset = RefsetService.getRefset(service, authUser, refsetInternalId);

			// add the list of concepts as members to the refset
			final ResultList<UpgradeInactiveConcept> results = RefsetMemberService.getUpgradeData(service, authUser,
					refset);

			LOG.debug("getUpgradeData: results " + results);

			return new ResponseEntity<>(results, HttpStatus.OK);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}

	}

	/**
	 * Make a change to an upgrade concept.
	 *
	 * @param refsetInternalId         the internal refset ID
	 * @param inactiveConceptId        the concept ID of the inactive concept to be
	 *                                 upgraded
	 * @param replacementConceptId     the concept ID of the replacement concept to
	 *                                 be updated
	 * @param changed                  a string identifying what has been changed
	 * @param manualReplacementConcept the manual upgrade replacement concept that
	 *                                 to be added
	 * @return the status
	 * @throws Exception the exception
	 */
	@Operation(summary = "Make a change to an upgrade concept. This call requires authentication with the correct role.", responses = {
			@ApiResponse(responseCode = "200", description = "Successfully retrieved the requested information"),
			@ApiResponse(responseCode = "400", description = "Bad request"),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "403", description = "Forbidden"),
			@ApiResponse(responseCode = "404", description = "Resource not found") })
	@Parameters({
			@Parameter(name = "refsetInternalId", description = "The internal ID of the refset.", required = true),
			@Parameter(name = "inactiveConceptId", description = "The concept ID of the inactive concept to be upgraded.", required = true),
			@Parameter(name = "replacementConceptId", description = "The concept ID of the replacement concept to be updated.", required = false),
			@Parameter(name = "changed", description = "A string identifying what has been changed.", required = true),
			@Parameter(name = "manualReplacementConcept", description = "The manual upgrade replacement concept to be added.", required = false), })
	@RecordMetric
	@PostMapping("/refset/{refsetInternalId}/modifyUpgradeConcept")
	public @ResponseBody ResponseEntity<String> modifyUpgradeConcept(
			@PathVariable(value = "refsetInternalId") final String refsetInternalId,
			@RequestParam(required = true) final String inactiveConceptId,
			@RequestParam(required = false) final String replacementConceptId,
			@RequestParam(required = true) final String changed,
			@RequestBody(required = false) final UpgradeReplacementConcept manualReplacementConcept) throws Exception {

		final User authUser = authorizeUser();

		try (final TerminologyService service = new TerminologyService()) {

			final Refset refset = RefsetService.getRefset(service, authUser, refsetInternalId);
			WorkflowService.canUserEditRefset(authUser, refset);

			service.setModifiedBy(authUser.getUserName());
			service.setModifiedFlag(true);
			String status = "All changes made successfully";

			LOG.debug("modifyUpgradeConcept: refsetInternalId: " + refsetInternalId + "; changed: " + changed
					+ "; inactiveConceptId: " + inactiveConceptId + "; replacementConceptId: " + replacementConceptId
					+ "; manualReplacementConcept: " + manualReplacementConcept);

			status = RefsetMemberService.modifyUpgradeConcept(service, authUser, refset, inactiveConceptId,
					replacementConceptId, manualReplacementConcept, changed);

			LOG.debug("modifyUpgradeConcept: Finished with status: " + status);

			return new ResponseEntity<>("{\"status\": \"" + status + "\"}", HttpStatus.OK);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}

	}

	/**
	 * Remove all inactive Upgrade concepts at once.
	 *
	 * @param refsetInternalId the internal refset ID
	 * @return the status
	 * @throws Exception the exception
	 */
	@Operation(summary = "Remove all inactive Upgrade concepts at once. Long running background process, call /refset/{refsetInternalId}/isLocked "
			+ "to get full status. This call requires authentication with the correct role.", responses = {
					@ApiResponse(responseCode = "200", description = "Successfully retrieved the requested information. The payload contains the status of the operation"),
					@ApiResponse(responseCode = "401", description = "Unauthorized"),
					@ApiResponse(responseCode = "403", description = "Forbidden"),
					@ApiResponse(responseCode = "404", description = "Resource not found") })
	@Parameters({
			@Parameter(name = "refsetInternalId", description = "The internal ID of the refset.", required = true) })
	@RecordMetric
	@PostMapping("/refset/{refsetInternalId}/removeAllUpgradeInactiveConcepts")
	public @ResponseBody ResponseEntity<String> removeAllUpgradeInactiveConcepts(
			@PathVariable(value = "refsetInternalId") final String refsetInternalId) throws Exception {

		final User authUser = authorizeUser();

		try (final TerminologyService service = new TerminologyService()) {

			final Refset refset = RefsetService.getRefset(service, authUser, refsetInternalId);
			WorkflowService.canUserEditRefset(authUser, refset);

			service.setModifiedBy(authUser.getUserName());
			service.setModifiedFlag(true);
			String status = "All changes made successfully";

			LOG.debug("removeAllUpgradeInactiveConcepts: refsetInternalId: " + refsetInternalId);

			status = RefsetMemberService.removeAllUpgradeInactiveConcepts(service, authUser, refset);

			LOG.debug("removeAllUpgradeInactiveConcepts: Finished with status: " + status);

			return new ResponseEntity<>("{\"status\": \"" + status + "\"}", HttpStatus.OK);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}

	}

	/**
	 * Add all replacement Upgrade concepts as members at once.
	 *
	 * @param refsetInternalId the internal refset ID
	 * @return the status
	 * @throws Exception the exception
	 */
	@Operation(summary = "Add all replacement Upgrade concepts as members at once. Long running background process, call /refset/{refsetInternalId}/isLocked "
			+ "to get full status. This call requires authentication with the correct role.", responses = {
					@ApiResponse(responseCode = "200", description = "Successfully retrieved the requested information. The payload contains the status of the operation"),
					@ApiResponse(responseCode = "401", description = "Unauthorized"),
					@ApiResponse(responseCode = "403", description = "Forbidden"),
					@ApiResponse(responseCode = "404", description = "Resource not found") })
	@Parameters({
			@Parameter(name = "refsetInternalId", description = "The internal ID of the refset.", required = true) })
	@RecordMetric
	@PostMapping("/refset/{refsetInternalId}/addAllUpgradeReplacementConcepts")
	public @ResponseBody ResponseEntity<String> addAllUpgradeReplacementConcepts(
			@PathVariable(value = "refsetInternalId") final String refsetInternalId) throws Exception {

		final User authUser = authorizeUser();

		try (final TerminologyService service = new TerminologyService()) {

			final Refset refset = RefsetService.getRefset(service, authUser, refsetInternalId);
			WorkflowService.canUserEditRefset(authUser, refset);

			service.setModifiedBy(authUser.getUserName());
			service.setModifiedFlag(true);
			String status = "All changes made successfully";

			LOG.debug("addAllUpgradeReplacementConcepts: refsetInternalId: " + refsetInternalId);

			status = RefsetMemberService.addAllUpgradeReplacementConcepts(service, authUser, refset);

			LOG.debug("addAllUpgradeReplacementConcepts: Finished with status: " + status);

			return new ResponseEntity<>("{\"status\": \"" + status + "\"}", HttpStatus.OK);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}

	}

	/**
	 * Search for members replacement concepts for upgrade.
	 *
	 * @param refsetInternalId the internal refset ID
	 * @param searchParameters the search parameters
	 * @param bindingResult    the binding result
	 * @param request          the request
	 * @return the string
	 * @throws Exception the exception
	 */
	@Operation(summary = "Search for members replacement concepts for upgrade. This call requires authentication with the correct role.", responses = {
			@ApiResponse(responseCode = "200", description = "Successfully retrieved the requested information."),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "403", description = "Forbidden"),
			@ApiResponse(responseCode = "404", description = "Resource not found"),
			@ApiResponse(responseCode = "417", description = "Expectation failed") })
	// @ModelAttribute API params documented in SearchParameter
	@Parameters({
			@Parameter(name = "refsetInternalId", description = "The internal ID of the refset.", required = true) })
	@RecordMetric
	@RequestMapping(method = RequestMethod.GET, value = {
			"/refset/{refsetInternalId}/replacementConceptSearch" }, produces = MediaType.APPLICATION_JSON)
	public @ResponseBody ResponseEntity<ResultList<UpgradeReplacementConcept>> replacementConceptSearch(
			@PathVariable(value = "refsetInternalId") final String refsetInternalId,
			final SearchParameters searchParameters, final BindingResult bindingResult,
			final HttpServletRequest request) throws Exception {

		// Check to make sure parameters were properly bound to variables.
		checkBinding(bindingResult);

		final User authUser = authorizeUser();

		try (final TerminologyService service = new TerminologyService()) {

			final Refset refset = RefsetMemberService.getRefset(authUser, service, refsetInternalId);
			ResultList<UpgradeReplacementConcept> results = new ResultList<>();
			final String query = searchParameters.getQuery();

			LOG.debug("replacementConceptSearch: refsetInternalId: " + refsetInternalId + " ; searchParameters: "
					+ ModelUtility.toJson(searchParameters));

			if (query != null && !query.equals("")) {

				results = RefsetMemberService.replacementConceptSearch(authUser, service, refset, searchParameters);
			}

			return new ResponseEntity<>(results, HttpStatus.OK);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}

	}

	/**
	 * Search for refsets for dropdown menus.
	 *
	 * @param searchParameters the search parameters
	 * @param bindingResult    the binding result
	 * @return the string
	 * @throws Exception the exception
	 */
	@Operation(summary = "Search for refsets for dropdown menus. To see certain results this call requires authentication with the correct role.", responses = {
			@ApiResponse(responseCode = "200", description = "Successfully retrieved the requested information"),
			@ApiResponse(responseCode = "404", description = "Resource not found"),
			@ApiResponse(responseCode = "417", description = "Expectation failed") })
	// @ModelAttribute API params documented in SearchParameter
	@Parameters({ @Parameter(name = "refsetInternalId", description = "the internal refset ID", required = true) })
	@RecordMetric
	@RequestMapping(method = RequestMethod.GET, value = {
			"/refset/dropdownSearch" }, produces = MediaType.APPLICATION_JSON)
	public @ResponseBody ResponseEntity<ResultList<Refset>> searchRefsetsForDropdowns(
			final SearchParameters searchParameters, final BindingResult bindingResult) throws Exception {

		// Check to make sure parameters were properly bound to variables.
		checkBinding(bindingResult);

		final User authUser = authorizeUser();

		try (final TerminologyService service = new TerminologyService()) {

			ResultList<Refset> results = new ResultList<>();
			final String query = searchParameters.getQuery();

			LOG.debug("refsetDropdownSearch: searchParameters: " + ModelUtility.toJson(searchParameters));

			if (query != null && !query.equals("")) {

				results = RefsetService.refsetDropdownSearch(authUser, service, searchParameters, true, true);
			}

			return new ResponseEntity<>(results, HttpStatus.OK);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}

	}

	/**
	 * Compile the data to compare two refsets.
	 *
	 * @param activeRefsetInternalId     the internal refset ID of the active refset
	 * @param comparisonRefsetInternalId the internal refset ID of the comparison
	 *                                   refset
	 * @return The operation status
	 * @throws Exception the exception
	 */
	@Operation(summary = "Compile the data to compare two refsets. Long running background process, call /refset/{refsetInternalId}/isLocked to get full status. "
			+ "To see certain results this call requires authentication with the correct role.", responses = {
					@ApiResponse(responseCode = "200", description = "Successfully retrieved the requested information. Payload contains the status of the operation"),
					@ApiResponse(responseCode = "401", description = "Unauthorized"),
					@ApiResponse(responseCode = "403", description = "Forbidden"),
					@ApiResponse(responseCode = "404", description = "Resource not found") })
	@Parameters({
			@Parameter(name = "activeRefsetInternalId", description = "The internal ID of the active refset.", required = true),
			@Parameter(name = "comparisonRefsetInternalId", description = "The internal ID of the comparison refset.", required = true) })
	@RecordMetric
	@RequestMapping(method = RequestMethod.GET, value = "/refset/{activeRefsetInternalId}/compileComparisonData", produces = MediaType.APPLICATION_JSON)
	public @ResponseBody ResponseEntity<String> compileComparisonData(
			@PathVariable(value = "activeRefsetInternalId") final String activeRefsetInternalId,
			@RequestParam(required = true) final String comparisonRefsetInternalId) throws Exception {

		final User authUser = authorizeUser();

		try (final TerminologyService service = new TerminologyService()) {

			String status = "";
			RefsetMemberService.REFSETS_BEING_UPDATED.add(activeRefsetInternalId);

			LOG.debug("compileComparisonData: activeRefsetInternalId: " + activeRefsetInternalId
					+ "; comparisonRefsetInternalId: " + comparisonRefsetInternalId);

			// add the list of concepts as members to the refset
			status = RefsetMemberService.compileComparisonData(service, authUser, activeRefsetInternalId,
					comparisonRefsetInternalId);

			LOG.debug("compileComparisonData: Finished with status " + status);

			return new ResponseEntity<>("{\"status\": \"" + status + "\"}", HttpStatus.OK);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}

		finally {

			RefsetMemberService.REFSETS_BEING_UPDATED.remove(activeRefsetInternalId);
		}

	}

	/**
	 * Get the data to compare two refsets.
	 *
	 * @param activeRefsetInternalId the internal ID of the active refset
	 * @param request                the request
	 * @return The comparison data
	 * @throws Exception the exception
	 */
	@Operation(summary = "Compile the data to compare two refsets. To see certain results this call requires authentication with the correct role.", responses = {
			@ApiResponse(responseCode = "200", description = "Successfully retrieved the requested information."),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "403", description = "Forbidden"),
			@ApiResponse(responseCode = "404", description = "Resource not found") })
	@Parameters({
			@Parameter(name = "activeRefsetInternalId", description = "The internal ID of the active refset.", required = true) })
	@RecordMetric
	@RequestMapping(method = RequestMethod.GET, value = "/refset/{activeRefsetInternalId}/comparisonData", produces = MediaType.APPLICATION_JSON)
	public @ResponseBody ResponseEntity<RefsetMemberComparison> getComparisonData(
			@PathVariable(value = "activeRefsetInternalId") final String activeRefsetInternalId,
			final HttpServletRequest request) throws Exception {

		authorizeUser();

		try (final TerminologyService service = new TerminologyService()) {

			LOG.debug("getComparisonData: activeRefsetInternalId: " + activeRefsetInternalId);

			// add the list of concepts as members to the refset
			final String uuid = (String) request.getSession()
					.getAttribute("refsetMemberComparison_" + activeRefsetInternalId);
			final RefsetMemberComparison results = (RefsetMemberComparison) SecurityService
					.getFromInMemoryStorage(uuid);
			SecurityService.removeFromInMemoryStorage(uuid);
			request.getSession().removeAttribute("refsetMemberComparison_" + activeRefsetInternalId);

			if (results == null) {

				throw new Exception("There were no comparison results to retrieve for this reference set.");
			}

			LOG.debug("getComparisonData: results " + results);

			return new ResponseEntity<>(results, HttpStatus.OK);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}

	}

	/**
	 * Request access to the refset for the specified ID.
	 *
	 * @param refsetInternalId the internal refset id
	 * @param comments         Any comments related to the request
	 * @return was the operation successful
	 * @throws Exception the exception
	 */
	@Operation(summary = "Request access to the refset for the specified ID", responses = {
			@ApiResponse(responseCode = "200", description = "Successfully retrieved the requested information"),
			@ApiResponse(responseCode = "404", description = "Resource not found") })
	@Parameters({
			@Parameter(name = "refsetInternalId", description = "The internal ID of the refset to request access to.", required = true),
			@Parameter(name = "comments", description = "Any comments related to the request.", required = true), })
	@RecordMetric
	@RequestMapping(method = RequestMethod.GET, value = "/refset/{refsetInternalId}/requestAccess", produces = MediaType.APPLICATION_JSON)
	public @ResponseBody ResponseEntity<Boolean> requestRefsetAccess(
			@PathVariable(value = "refsetInternalId") final String refsetInternalId, final String comments)
			throws Exception {

		authorizeUser();
		try (final TerminologyService service = new TerminologyService()) {

			// TODO: nothing happens here !!
			return new ResponseEntity<>(true, HttpStatus.OK);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}

	}

	/**
	 * Share refset.
	 *
	 * @param refsetInternalId the refset internal id
	 * @param emailInfo        the email info
	 * @return the response entity
	 * @throws Exception the exception
	 */
	@Operation(summary = "Share a refset via email. To see certain results this call requires authentication with the correct role.", responses = {
			@ApiResponse(responseCode = "200", description = "Successfully shared the requested refset. The payload contains the status of the operation"),
			@ApiResponse(responseCode = "404", description = "Resource not found") })
	@Parameters({
			@Parameter(name = "refsetInternalId", description = "The internal ID of the refset.", required = true),
			@Parameter(name = "emailInfo", description = "The information about the email address to send to.", required = true) })
	@RecordMetric
	@PostMapping(value = "/refset/{refsetInternalId}/share", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
	public @ResponseBody ResponseEntity<String> shareRefset(@PathVariable final String refsetInternalId,
			@RequestBody(required = true) final SendCommunicationEmailInfo emailInfo) throws Exception {

		final User authUser = authorizeUser();
		try {

			RefsetService.shareRefset(authUser, refsetInternalId, emailInfo.getRecipient(),
					emailInfo.getAdditionalMessage());

			final String returnMessage = "{\"message\": \"Share Reference Set was Successful\"}";

			return new ResponseEntity<>(returnMessage, HttpStatus.OK);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}

	}

	/**
	 * Request project access.
	 *
	 * @param refsetInternalId the refset internal id
	 * @param emailInfo        the email info
	 * @return the response entity
	 * @throws Exception the exception
	 */
	@Operation(summary = "Request project access from administrators. To see certain results this call requires authentication with the correct role.", responses = {
			@ApiResponse(responseCode = "200", description = "Successfully requested access to the refset's ecnlosing project"),
			@ApiResponse(responseCode = "404", description = "Resource not found") })
	@Parameters({
			@Parameter(name = "refsetInternalId", description = "The internal ID of the refset.", required = true),
			@Parameter(name = "emailInfo", description = "The information about the email address to send to.", required = true) })
	@RecordMetric
	@PostMapping(value = "/refset/{refsetInternalId}/request", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
	public @ResponseBody ResponseEntity<String> requestProjectAccess(@PathVariable final String refsetInternalId,
			@RequestBody(required = true) final SendCommunicationEmailInfo emailInfo) throws Exception {

		final User authUser = authorizeUser();
		try {
			RefsetService.requestProjectAccess(authUser, refsetInternalId, emailInfo.getRecipient(),
					emailInfo.getAdditionalMessage());

			final String returnMessage = "{\"message\": \"Reference set access (via project access) was requested was Successful\"}";

			return new ResponseEntity<>(returnMessage, HttpStatus.OK);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}

	}

	/**
	 * Copy refset.
	 *
	 * @param refsetInternalId   the refset internal id
	 * @param name               the name
	 * @param projectId          the project id
	 * @param localSet           the local set
	 * @param privateRefset      the private refset
	 * @param comboSet           the combo set
	 * @param narrative          the narrative
	 * @param tags               the tags
	 * @param parentConceptId    the parent concept id
	 * @param newRefsetConceptId the new refset concept id
	 * @return the response entity
	 * @throws Exception the exception
	 */
	@Operation(summary = "Create a new refset that is a copy of an existing one. This call requires authentication with the correct role.", responses = {
			@ApiResponse(responseCode = "200", description = "Successfully copied refset specified"),
			@ApiResponse(responseCode = "404", description = "Resource not found") })
	@Parameters({
			@Parameter(name = "refsetInternalId", description = "The internal ID of the refset to copy.", required = true),
			@Parameter(name = "name", description = "The information about the email address to send to.", required = true),
			@Parameter(name = "projectId", description = "The project ID for the new refset.", required = true),
			@Parameter(name = "localSet", description = "Will the new refset be a local set.", required = true),
			@Parameter(name = "privateRefset", description = "Will the new refset be a local set.", required = true),
			@Parameter(name = "comboSet", description = "Will the new refset be a local set.", required = true),
			@Parameter(name = "narrative", description = "The new refset's narrative.", required = true),
			@Parameter(name = "tags", description = "The new refset's tags.", required = true),
			@Parameter(name = "parentConceptId", description = "The new refset's parent concept ID.", required = true),
			@Parameter(name = "newRefsetConceptId", description = "The new refset's concept ID", required = true) })
	@RecordMetric
	@RequestMapping(method = RequestMethod.GET, value = "/refset/{refsetInternalId}/copy", produces = MediaType.APPLICATION_JSON)
	public @ResponseBody ResponseEntity<String> copyRefset(@PathVariable(required = true) final String refsetInternalId,
			@RequestParam final String name, @RequestParam final String projectId, @RequestParam final Boolean localSet,
			@RequestParam final Boolean privateRefset, @RequestParam final Boolean comboSet,
			@RequestParam final String narrative, @RequestParam final Set<String> tags,
			@RequestParam final String parentConceptId, @RequestParam final String newRefsetConceptId)
			throws Exception {

		final User authUser = authorizeUser();

		// TODO: Add support for providing a zip RF2 or a refset file to clone off of.
		// Questions to be answered first: Always use a) latest version for refsetId
		// provided or b) Version if provided RF2 file instead and c) Can't supply both
		try (final TerminologyService service = new TerminologyService()) {

			service.setModifiedBy(authUser.getUserName());

			String status = "";
			final Object returned = RefsetService.copyRefset(service, authUser, refsetInternalId, name, projectId,
					localSet, privateRefset, comboSet, narrative, tags, parentConceptId, newRefsetConceptId);

			if (returned instanceof String) {

				status = (String) returned;
			} else {

				final Refset refset = (Refset) returned;
				status = refset.getRefsetId();
			}

			if (status.startsWith("Error")) {

				return new ResponseEntity<>("{\"error\": \"" + status + "\"}", HttpStatus.OK);
			}

			return new ResponseEntity<>("{\"refsetId\": \"" + status + "\"}", HttpStatus.OK);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}

	}

	/**
	 * Reset refset.
	 *
	 * @param refsetId the refset id
	 * @return the response entity
	 * @throws Exception the exception
	 */
	@Hidden
	@Operation(summary = "Reset a refset to contain the contents of Snowstorm. Note only works if refset has not been upgraded during edit cycle. "
			+ "This call requires authentication with the correct role.", responses = {
					@ApiResponse(responseCode = "200", description = "Successfully reset the refset"),
					@ApiResponse(responseCode = "403", description = "May not reset refset on a production system"),
					@ApiResponse(responseCode = "404", description = "Resource not found") })
	@RecordMetric
	@RequestMapping(method = RequestMethod.GET, value = "/refset/{refsetId}/reset", produces = MediaType.APPLICATION_JSON)
	public @ResponseBody ResponseEntity<String> resetRefset(@PathVariable final String refsetId) throws Exception {

		final User authUser = authorizeUser();

		if (!authUser.checkPermission(User.ROLE_ADMIN, "all", null, null)) {
			throw new RestException(false, 403, "Forbidden",
					"This user does not have permission to perform this action");
		}

		try (final TerminologyService service = new TerminologyService()) {

			if (!SyncAgent.getIsProductionSystem()) {

				service.setModifiedBy(authUser.getUserName());

				final String result = RefsetService.resetRefset(service, authUser, refsetId);

				final String returnMessage = "{\"message\": \"Reset Successful " + result + "\"}";

				return new ResponseEntity<>(returnMessage, HttpStatus.OK);
			}

			final String returnMessage = "{\"message\": \"It is prohibited to be reseting reference sets on this production system\"}";
			throw new RestException(false, 403, "Forbidden", returnMessage);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}

	}

	/**
	 * Invite user to refset.
	 *
	 * @param refsetInternalId the refset internal id
	 * @param emailInfo        the email info
	 * @return the response entity
	 * @throws Exception the exception
	 */
	@Operation(summary = "Request member/non-member to join organization. This call requires authentication with the correct role.")
	@RecordMetric
	@PostMapping(value = "/refset/{refsetInternalId}/invite", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
	@Hidden
	public @ResponseBody ResponseEntity<String> inviteUserToRefset(@PathVariable final String refsetInternalId,
			@RequestBody(required = true) final SendCommunicationEmailInfo emailInfo) throws Exception {

		final User authUser = authorizeUser();
		try {

			RefsetService.inviteUserToOrganization(authUser, refsetInternalId, emailInfo.getRecipient(),
					emailInfo.getAdditionalMessage());

			final String returnMessage = "{\"message\": \"Reference set invite was Successful\"}";

			return new ResponseEntity<>(returnMessage, HttpStatus.OK);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}

	}

}
