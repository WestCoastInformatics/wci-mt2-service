package org.ihtsdo.refsetservice.rest;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.MediaType;

import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.service.SecurityService;
import org.ihtsdo.refsetservice.util.ModelUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
            }, requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Authorization request", required = true,
                    content = {
                            @Content(mediaType = MediaType.TEXT_PLAIN,
                                    schema = @Schema(implementation = String.class),
                                    examples = @ExampleObject(value = "supersecretpwd**")),
                            @Content(mediaType = MediaType.APPLICATION_JSON,
                                    schema = @Schema(implementation = User.class))
                    }))
    public @ResponseBody ResponseEntity<User> authenticate(@PathVariable(value = "userName") final String userName, final @RequestBody String password, HttpServletRequest request) throws Exception {
    	
    	logger.info("RESTful call POST (Security): authentication for username = {}", userName);
    	
    	try (final SecurityService securityService = new SecurityService()) {
    	    
    		final User user = securityService.authenticate(userName, password);
    		
    		if (user == null || user.getAuthToken() == null) {
    			throw new Exception("Unable to authenticate user");
    		}
    		
    		logger.debug("******** SESSION USER: " + ModelUtility.toJson(user));
    		request.getSession().setAttribute(SecurityService.SESSION_USER_OBJECT_KEY, user);
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
    public @ResponseBody ResponseEntity<Void> logout(
    		@PathVariable(value = "authToken", required = true) final String authToken) throws Exception {

    	logger.info("RESTful call POST (Security): logout for authToken = {}", authToken);
    	
    	try (final SecurityService securityService = new SecurityService()) {
    	
    	    securityService.logout(authToken);
                        
          return new ResponseEntity<>(null, HttpStatus.OK);
            
    	} catch (Exception e) {
    		handleException(e);
    		return null;
    	}
    }
}
