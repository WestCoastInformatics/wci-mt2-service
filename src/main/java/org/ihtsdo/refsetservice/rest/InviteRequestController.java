package org.ihtsdo.refsetservice.rest;

import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.ihtsdo.refsetservice.app.RecordMetric;
import org.ihtsdo.refsetservice.model.InviteRequest;
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
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

/**
 * Controller for /invite endpoints.
 */
@RestController
@Api(tags = "teams", description = "Endpoints for handling reponses from invites")
@RequestMapping(value = "/", produces = MediaType.APPLICATION_JSON)
public class InviteRequestController  extends BaseController {

    /** Logger. */
    private static Logger logger = LoggerFactory.getLogger(InviteRequestController.class);
    
    /**  The app url root. */
    private static String appUrlRoot;
    
    static {
        appUrlRoot = PropertyUtility.getProperties().getProperty("app.url.root");
    }
    
    /**
     * Response to invite organization.
     *
     * @param id the id of the invite request
     * @param acceptance the accepted or decline
     * @return the response entity
     * @throws Exception the exception
     */
    @ApiOperation(value = "Process invitation response.")
    @ApiResponses(value = {
        @ApiResponse(code = 302, message = "Response to invitation processed"), @ApiResponse(code = 404, message = "Not Found"), @ApiResponse(code = 417, message = "Failed Expectation"),
        @ApiResponse(code = 500, message = "Internal server error")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "id", value = "Invite request id, e.g. &lt;uuid&gt;", required = true, dataTypeClass = String.class, paramType = "path"),
        @ApiImplicitParam(name = "acceptance", value = "Indicate if accepted with true or false", required = true, dataTypeClass = Boolean.class, paramType = "query", defaultValue = "false")
    })
    @RecordMetric
    @GetMapping(value = "/inviterequest/{id}/response")
    public @ResponseBody ResponseEntity<String> responseToInviteOrganization(@PathVariable(value = "id") final String id, @QueryParam(value = "acceptance") final boolean acceptance) throws Exception {

        // no auth - response is from email.

        final HttpHeaders headers = new HttpHeaders();
        headers.add("Location", appUrlRoot);

        try (final TerminologyService service = new TerminologyService()) {
            
            final InviteRequest inviteRequest = service.findSingle("id:" + id, InviteRequest.class, null);
            
            if (inviteRequest == null) {
                logger.info("Did not find invite request id: " + id + " and acceptance: " + acceptance);
                return new ResponseEntity<>(headers, HttpStatus.FOUND);
            }

            logger.info("response to invite request: id: " + id + " and acceptance: " + acceptance);
            if (inviteRequest.getPayload().contains("organization")) {
                OrganizationService.processOrganizationInvitation(service, inviteRequest, acceptance);    
            }
            else if (inviteRequest.getPayload().contains("refset")) {
                RefsetService.processRefsetInvitation(service, inviteRequest, acceptance);
            }
            

            return new ResponseEntity<>(headers, HttpStatus.FOUND);

        } catch (final Exception e) {

            logger.error("Exception while processing response for organization id invite", acceptance);
            return new ResponseEntity<>(headers, HttpStatus.FOUND);
        }
    }
        
}
