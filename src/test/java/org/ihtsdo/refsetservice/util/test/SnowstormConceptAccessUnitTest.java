package org.ihtsdo.refsetservice.util.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status.Family;

import org.ihtsdo.refsetservice.model.PfsParameter;
import org.ihtsdo.refsetservice.util.LocalException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.google.common.net.InternetDomainName;

// TODO: Auto-generated Javadoc
/**
 * Validates the code-system-* files in the "src/resources" folder.
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)

public class SnowstormConceptAccessUnitTest {

    /** The api key. */
    private static final String API_KEY = "AIzaSyDPTktIi3TeBDvbzuXknjNjkTJxDdBCkDQ";

    /** The auth header. */
    private static final String AUTH_HEADER = "Basic c25vd293bDpzbm93b3ds";

    /** The headers. */
    private Map<String, String> headers = new HashMap<>();

    /** The snowstorm url. */
    // private final String SNOWSTORM_URL =
    // "https://snowstorm.ihtsdotools.org/snowstorm/snomed-ct/";
    private static final String BASE_URL = "https://snowstorm.ihtsdotools.org/snowstorm/snomed-ct/";

    /** The accept. */
    private static final String ACCEPT = "application/json";

    /** The logger. */
    private final Logger logger = LoggerFactory.getLogger(SnowstormConceptAccessUnitTest.class);

    /** The generic user cookie expiration date. */
    private Date genericUserCookieExpirationDate = null;

    /** The generic user cookie. */
    private String genericUserCookie;

    /** The domain. */
    private CharSequence domain;

    /** The user name. */
    private static final String USER_NAME = "refset-prod";

    /** The password. */
    private static final String PASSWORD = "aandfhtlsfjh90F";

    /** The authentication url. */
    private static final String AUTH_URL = "https://ims.ihtsdotools.org/api";

    /**
     * 
     * Gets an AmazonS3 object based first on
     * InstanceProfileCredentialsProvider. If not available, will then use
     * AWSStaticCredentialsProvider.
     * 
     * @throws Exception the exception
     */
    @Test
    public void testConnectability() throws Exception {
        logger.info("TEST");
        final Client client = ClientBuilder.newClient();
        // https://snowstorm.ihtsdotools.org/snowstorm/snomed-ct/swagger-ui.html#!/Refset_Members/
        // findBrowserReferenceSetMembersWithAggregationsUsingGET
        final WebTarget target = client.target(BASE_URL + "swagger-ui.html#!/Refset_Members/"
                + "findBrowserReferenceSetMembersWithAggregationsUsingGET");
        final Response response = target.request(ACCEPT).header("Authorization", AUTH_HEADER)
                .header("Cookie",
                        getGenericUserCookie() != null ? getGenericUserCookie() : getCookieHeader())
                .get();
        final String resultString = response.readEntity(String.class);

        logger.debug(resultString);
        logger.debug("family: " + response.getStatusInfo().getFamily());
        logger.debug("Reason phrase: " + response.getStatusInfo().getReasonPhrase());
        assertEquals(Family.SUCCESSFUL, response.getStatusInfo().getFamily());
    }

    /**
     * 
     * Gets an AmazonS3 object based first on
     * InstanceProfileCredentialsProvider. If not available, will then use
     * AWSStaticCredentialsProvider.
     * 
     * @throws Exception the exception
     */
    @Test
    public void testRefsetMembers() throws Exception {
        logger.info("TEST");
        final Client client = ClientBuilder.newClient();
        // https://snowstorm.ihtsdotools.org/snowstorm/snomed-ct/swagger-ui.html#!/browser/MAIN%2F
        // SNOMEDCT-EE/members?referenceSet=723563008&offset=0&limit=10

        // SHould have 3 results
        String url = BASE_URL
                + "browser/MAIN%2FSNOMEDCT-EE/members?referenceSet=723563008&offset=0&limit=10";
        final WebTarget target = client.target(url);
        final Response response = target.request(ACCEPT).header("Authorization", AUTH_HEADER)
                .header("Accept-Language", "en-X-900000000000509007,en-X-900000000000508004,en")
                .header("Cookie",
                        getGenericUserCookie() != null ? getGenericUserCookie() : getCookieHeader())
                .get();
        final String resultString = response.readEntity(String.class);

        logger.debug(resultString);
        logger.debug("family: " + response.getStatusInfo().getFamily());
        logger.debug("Reason phrase: " + response.getStatusInfo().getReasonPhrase());
        assertEquals(Family.SUCCESSFUL, response.getStatusInfo().getFamily());
    }

    /**
     * Test find concepts for query.
     *
     * @throws Exception the exception
     */
    @Test
    public void testFindConceptsForQuery() throws Exception {

        // Make a webservice call to browser api
        final Client client = ClientBuilder.newClient();

        final StringBuilder lookupErrors = new StringBuilder();

        PfsParameter localPfs = new PfsParameter();

        boolean useTerm = true;
        String localQuery = "<<226528004";
        if (localQuery.matches("\\d+[01]0\\d")) {
            useTerm = false;
        } else if (localQuery.matches("\\d+[01]0\\d\\*")) {
            localQuery = localQuery.replace("*", "");
            useTerm = false;
        }

        // It's either a concept id, otherwise a search term
        // if a search term, we will return up to 100 concepts and fake the
        // paging
        // on the front end
        // this is because we no longer have the offset parameter to do paging
        // and
        // keeping track
        // of the searchAfter parameter is too complicated for our current needs
        final String url = BASE_URL + "MAIN%2F2021-01-31/concepts/";
        final String targetUri = useTerm
                ? url + "?term=" + URLEncoder.encode(localQuery, "UTF-8").replaceAll(" ", "%20")
                        + "&limit=100" + "&expand=pt(),fsn()"
                : url + URLEncoder.encode(localQuery, "UTF-8").replaceAll(" ", "%20")
                        + "?expand=pt()";

        final WebTarget target = client.target(targetUri);

        final Response response = target.request(ACCEPT).header("Authorization", AUTH_HEADER)
                .header("Accept-Language", "en-X-900000000000509007,en-X-900000000000508004,en")
                .header("Cookie",
                        getGenericUserCookie() != null ? getGenericUserCookie() : getCookieHeader())
                .get();

        final String resultString = response.readEntity(String.class);
        logger.debug(resultString);
        logger.debug("family: " + response.getStatusInfo().getFamily());
        logger.debug("Reason phrase: " + response.getStatusInfo().getReasonPhrase());
        assertEquals(Family.SUCCESSFUL, response.getStatusInfo().getFamily());
    }

    /**
     * Gets the generic user cookie.
     *
     * @return the generic user cookie
     * @throws Exception the exception
     */
    private String getGenericUserCookie() throws Exception {

        // Check if the generic user cookie is expired and needs to be cleared
        // and re-read
        if (genericUserCookieExpirationDate == null
                || new Date().after(genericUserCookieExpirationDate)) {
            genericUserCookie = null;

            // Set the new expiration date for tomorrow
            Calendar now = Calendar.getInstance();
            now.add(Calendar.HOUR, 24);
            genericUserCookieExpirationDate = now.getTime();
        }

        if (genericUserCookie != null) {
            return genericUserCookie;
        }

        // Login the generic user, then save and return the cookie
        Client client = ClientBuilder.newClient();
        WebTarget target = client.target(AUTH_URL + "/authenticate");
        Builder builder = target.request(MediaType.APPLICATION_JSON);

        Response response = builder.post(Entity
                .json("{ \"login\": \"" + USER_NAME + "\", \"password\": \"" + PASSWORD + "\" }"));
        if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
            throw new LocalException(
                    "Authentication of generic user failed. " + response.toString());
        }
        Map<String, NewCookie> genericUserCookies = response.getCookies();
        StringBuilder sb = new StringBuilder();
        for (String key : genericUserCookies.keySet()) {
            sb.append(genericUserCookies.get(key));
            sb.append(";");
        }
        genericUserCookie = sb.toString();
        return genericUserCookie;
    }

    /**
     * Returns the cookie header.
     *
     * @return the cookie header
     * @throws MalformedURLException the malformed URL exception
     */
    public String getCookieHeader() throws MalformedURLException {
        domain = InternetDomainName.from(new URL(BASE_URL).getHost()).topPrivateDomain().toString();

        final String referer = headers.get("Referer");
        if (referer.contains(domain)) {
            return headers.get("Cookie");
        } else {
            logger.warn("UNEXPECTED referer not matching url domain = " + referer);
        }
        return "";
    }
}
