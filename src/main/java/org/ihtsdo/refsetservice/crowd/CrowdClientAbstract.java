package org.ihtsdo.refsetservice.crowd;

import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;

public class CrowdClientAbstract {

    
    /** Base URL for the Crowd API including HTTPS */
    protected static String BASE_URL;
    
    /** User name for authentication to Crowd API */
    private static String USER_NAME;
    
    /** User's password for authentication to Crowd API */
    private static String PASSWORD;
    
    /** initialization of required params */
    static {
        // make sure Crowd URL includes the context root
//        BASE_URL = PropertyUtility.getProperty("crowd.baseUrl");
//        USER_NAME = PropertyUtility.getProperty("crowd.username");
//        PASSWORD = PropertyUtility.getProperty("crowd.password");
        
        // for testing, DO NOT COMMIT
        BASE_URL = "https://dev-crowd.ihtsdotools.org/crowd";
        USER_NAME = "dev-rt2";
        PASSWORD = "4OQHe56e26@GhcJ#08d";
    }
    
    /** The accept. */
    private static final String ACCEPT_DEFAULT = MediaType.APPLICATION_JSON;
    
    
    @SuppressWarnings("serial")
    protected static final Set<String> ROLES = new HashSet<String>() {{
      add("admin");
      add("author");
      add("reviewer");
      add("viewer");
    }};
    
    
    /**
     * Calls a Crowd URL and returns the response.
     *
     * @param url The Crowd URL to call
     * @param  
     * @return 
     * @throws Exception the exception
     */
    protected static Response get(final String url)  {

        final Client client = ClientBuilder.newClient();
        final HttpAuthenticationFeature feature = HttpAuthenticationFeature.basic(USER_NAME, PASSWORD);
        client.register(feature);
        final WebTarget target = client.target(url);
        final Response response = target.request(ACCEPT_DEFAULT)
                .get();        
        return response;
    }
    
    /**
     * HTTP Post.
     *
     * @param url URL to post.
     * @param entity Payload to post.
     * @return Response the response
     * @throws Exception the exception
     */
    protected static Response post(final String url, String entity)  {
        
        final Client client = ClientBuilder.newClient();
        final HttpAuthenticationFeature feature = HttpAuthenticationFeature.basic(USER_NAME, PASSWORD);
        client.register(feature);
        final WebTarget target = client.target(url);
        final Builder builder = target.request(ACCEPT_DEFAULT);
        final Response response = builder.post(Entity.json(entity));
        return response;
    }
    
    /**
     * HTTP Delete
     *
     * @param URL 
     * @return the response
     * @throws Exception the exception
     */
    protected static Response delete(final String url)  {

        final Client client = ClientBuilder.newClient();
        final HttpAuthenticationFeature feature = HttpAuthenticationFeature.basic(USER_NAME, PASSWORD);
        client.register(feature);
        final WebTarget target = client.target(url);
        final Builder builder = target.request(ACCEPT_DEFAULT);
        final Response response = builder.delete();
        return response;
    }
}
