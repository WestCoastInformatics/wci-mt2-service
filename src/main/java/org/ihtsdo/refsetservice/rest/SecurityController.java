package org.ihtsdo.refsetservice.rest;

import java.util.Set;

import javax.ws.rs.core.MediaType;

import org.ihtsdo.refsetservice.model.RestException;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.service.SecurityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Controller for authentication and user end points.
 * 
 * @author Nuno
 *
 */
@SecurityScheme(name = "IMS authentication token", description = "IMS authentication token",
        type = SecuritySchemeType.HTTP, scheme = "basic")
@OpenAPIDefinition(info = @Info(title = "Security for Refset Service", version = "1.0.0",
        description = "API documentation for the Refset Service " + "account service API.",
        contact = @Contact(name = "API Support", url = "https://ims.idstsdotools.com",
                email = "info@idstsdotools.com")),
        tags = {
                @Tag(name = "user", description = "User service endpoints"),
        }, servers = {
                @Server(description = "Dev Deployment", url = "https://dev.terminologyhub.com")
        })
@CrossOrigin(origins = "https://demo.terminologyhub.com")
@RestController
@RequestMapping(value = "/", produces = MediaType.APPLICATION_JSON)
@SuppressWarnings("javadoc")
public class SecurityController extends BaseController {
	
	/** Logger. */
    private static Logger logger = LoggerFactory.getLogger(SecurityController.class);
    
    /**
     * Returns the user.
     *
     * @param 
     * @return the user
     * @throws Exception the exception
     */
    @PostMapping("/authenticate/{userName}")
    @Operation(summary = "Authorize user",
            description = "Used to log into the platform and obtain an access token "
                    + "for future API calls.",
            responses = {
                    @ApiResponse(responseCode = "200",
                            description = "Successful authorization, payload contains access_token",
                            content = @Content(mediaType = MediaType.APPLICATION_JSON,
                                    schema = @Schema(implementation = String.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized"),
                    @ApiResponse(responseCode = "403", description = "Forbidden"),
                    @ApiResponse(responseCode = "417", description = "Expectation failed"),
                    @ApiResponse(responseCode = "500", description = "Internal server error")
            }, tags = {
                    "auth"
            }, requestBody = @RequestBody(description = "Authorization request", required = true,
                    content = {
                            @Content(mediaType = MediaType.TEXT_PLAIN,
                                    schema = @Schema(implementation = String.class),
                                    examples = @ExampleObject(value = "")),
                            @Content(mediaType = MediaType.APPLICATION_JSON,
                                    schema = @Schema(implementation = User.class))
                    }))
    public @ResponseBody ResponseEntity<User> authenticate(
    		@PathVariable(value = "userName") final String userName,
    		final @org.springframework.web.bind.annotation.RequestBody String password) throws Exception {
    	
    	logger.info("RESTful call POST (Security): authentication for username = {}", userName);
    	
    	
    	try (SecurityService securityService = new SecurityService()) {
    		final User user = securityService.authenticate(userName, password);
    		if (user == null || user.getAuthToken() == null) {
    			throw new Exception("Unable to authenticate user");
    		}
    		return new ResponseEntity<>(user, new HttpHeaders(), HttpStatus.OK);
    	}
    }
    
    /**
     * Logout the authentication token
     *
     * @param 
     * @return the user
     * @throws Exception the exception
     */
    @PostMapping("/logout/{authToken}")
    @Operation(summary = "Logout Authorized user",
            description = "Performs logout on specified auth token. This effectively logs the user out.",
            responses = {
                    @ApiResponse(responseCode = "200",
                            description = "Successful logout",
                            content = @Content(mediaType = MediaType.APPLICATION_JSON,
                                    schema = @Schema(implementation = String.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized"),
                    @ApiResponse(responseCode = "403", description = "Forbidden"),
                    @ApiResponse(responseCode = "500", description = "Internal server error")
            }, tags = {
                    "auth"
            })
    public void logout(
    		@PathVariable(value = "authToken", required = true) final String authToken) throws Exception {
    	
    	// TODO: update after demo
    	
    	logger.info("RESTful call POST (Security): logout for authToken = {}", authToken);
    	
    	try (SecurityService securityService = new SecurityService()) {
    		securityService.logout(authToken);
    	} catch (Exception e) {
    		handleException(e);
    	}
    }
    
    
    @RequestMapping(value = "/user/{id}", method = RequestMethod.GET)
    @Operation(summary = "Get user", description = "Gets user by specified id",
            security = @SecurityRequirement(name = "basic"), responses = {
                    @ApiResponse(responseCode = "200", description = "User matching specified id",
                            content = @Content(mediaType = MediaType.APPLICATION_JSON,
                                    schema = @Schema(implementation = User.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized"),
                    @ApiResponse(responseCode = "403", description = "Forbidden"),
                    @ApiResponse(responseCode = "500", description = "Internal server error")
            }, tags = {
                    "user"
            })
    public @ResponseBody ResponseEntity<User> getUser(
    		@PathVariable("id") final String id,
    		@RequestHeader("Authorization") final String authToken
    		) throws Exception {
    	
    	logger.info("RESTful call GET (Security): getUser for id = {}", id);
    	
    	try (final SecurityService securityService = new SecurityService()) {
    		
    		// throws exception if not found
    		final String userName = securityService. authorizeApp(authToken, "retrieve the user",
    		          User.ROLE_USER);
    		
    		final User authTokenUser = securityService.getUserFromUserName(userName);
    		if (authTokenUser == null) {
    			throw new RestException(false, 403, "Forbidden", null);
    		}
    		final User requestedUser = securityService.getUser(id);

    		// admin request
    		if (authTokenUser.getRoles().contains(User.ROLE_ADMIN)) {

    			if (requestedUser != null) {
    				return new ResponseEntity<>(requestedUser, new HttpHeaders(), HttpStatus.OK);
    			}
    			else {
    				throw new RestException(false, 404, "Not found", "Unable to find user for " + id);	
    			}
    		}
    		// non-admin request
    		else {
    			if (requestedUser == null || !authTokenUser.getId().equals(requestedUser.getId())) {
    				throw new RestException(false, 403, "Forbidden", null);	
    			}
    			else {
    				return new ResponseEntity<>(requestedUser, new HttpHeaders(), HttpStatus.OK);
    			}
    		}
    	} catch (final Exception e) {
    		handleException(e);
    		return null;
    	}
    }
}
