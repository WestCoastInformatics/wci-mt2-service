package org.ihtsdo.refsetservice.rest;

import javax.ws.rs.core.MediaType;

import org.ihtsdo.refsetservice.app.RecordMetric;
import org.ihtsdo.refsetservice.model.InviteRequest;
import org.ihtsdo.refsetservice.model.RestException;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.terminologyservice.OrganizationService;
import org.ihtsdo.refsetservice.terminologyservice.RefsetService;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Controller for /invite endpoints.
 */
@RestController
@OpenAPIDefinition(info = @Info(title = "Invite Request Controller", version = "1.0.0", description = "Endpoints for handling reponses from invites."), tags = {
		@Tag(name = "invite", description = "Invite Request service endpoints") }, servers = {
				@Server(description = "Current Instance", url = "/") })
@RequestMapping(value = "/", produces = MediaType.APPLICATION_JSON)
public class InviteRequestController extends BaseController {

	/** The Constant LOG. */
	private static final Logger LOG = LoggerFactory.getLogger(InviteRequestController.class);

	/** The app url root. */
	private static String appUrlRoot;

	static {
		appUrlRoot = PropertyUtility.getProperties().getProperty("app.url.root");
	}

	/**
	 * Response to invite organization.
	 *
	 * @param id         the id of the invite request
	 * @param acceptance the accepted or decline
	 * @return the response entity
	 * @throws Exception the exception
	 */
	@Operation(summary = "Process invitation response.", responses = {
			@ApiResponse(responseCode = "200", description = "Response to invitation processed"),
			@ApiResponse(responseCode = "404", description = "Not Found"),
			@ApiResponse(responseCode = "417", description = "Failed Expectation") })
	@Parameters({ @Parameter(name = "id", description = "Invite request id, e.g. &lt;uuid&gt;", required = true),
			@Parameter(name = "acceptance", description = "Indicate if accepted with true or false", required = true, schema = @Schema(implementation = Boolean.class), example = "false") })
	@RecordMetric
	@GetMapping(value = "/inviterequest/{id}/response")
	public @ResponseBody ResponseEntity<String> responseToInviteOrganization(
			@PathVariable(value = "id") final String id,
			@RequestParam(value = "acceptance", defaultValue = "false") final boolean acceptance) throws Exception {

		// no auth - response is from email.

		final HttpHeaders headers = new HttpHeaders();
		headers.add("Location", appUrlRoot);

		try (final TerminologyService service = new TerminologyService()) {

			final InviteRequest inviteRequest = service.findSingle("id:" + id, InviteRequest.class, null);

			if (inviteRequest == null) {
				throw new RestException(false, 404, "Not found",
						"Did not find invite request id: " + id + " and acceptance: " + acceptance);
			}

			LOG.info("response to invite request: id: " + id + " and acceptance: " + acceptance);
			if (inviteRequest.getPayload().contains("organization")) {
				OrganizationService.processOrganizationInvitation(service, inviteRequest, acceptance);
			} else if (inviteRequest.getPayload().contains("refset")) {
				RefsetService.processRefsetInvitation(service, inviteRequest, acceptance);
			}

			return new ResponseEntity<>(headers, HttpStatus.OK);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}
	}

}
