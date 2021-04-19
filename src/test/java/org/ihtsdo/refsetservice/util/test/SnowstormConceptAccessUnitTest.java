package org.ihtsdo.refsetservice.util.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status.Family;

import org.ihtsdo.refsetservice.model.Concept;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.util.LocalException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.net.InternetDomainName;

// TODO: Auto-generated Javadoc
/**
 * Validates the code-system-* files in the "src/resources" folder.
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)

public class SnowstormConceptAccessUnitTest {

    /** The auth header. */
    // TODO: Put this value in config if it is "sensitive"
    private static final String AUTH_HEADER = "Basic c25vd293bDpzbm93b3ds";

    /** The headers. */
    private Map<String, String> headers = new HashMap<>();

    /** The snowstorm url. */
    // TODO: Consider putting this value in config (we likely migrate to a daily
    // build instance which I believe is in a different URL
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

    /** The members. */

    /** The user name. */
    // TODO: Put this value in config if it is "sensitive"
    private static final String USER_NAME = "refset-prod";

    /** The password. */
    // TODO: Put this value in config if it is "sensitive"
    private static final String PASSWORD = "aandfhtlsfjh90F";

    /** The authentication url. */
    private static final String AUTH_URL = "https://ims.ihtsdotools.org/api";

    /** The Constant SDF. */
    private static final SimpleDateFormat SDF = new SimpleDateFormat("yyyyMMdd");

    /** The Constant refsetToLanguageMap. */
    private static final Set<String> refsetToLanguages = new HashSet<>();

    /** The Constant DESC_TERM. */
    private static final String DESC_TERM = "term";

    /** The Constant DESC_TYPE. */
    private static final String DESC_TYPE = "type";

    /** The Constant DESC_LANG. */
    private static final String DESC_LANG = "lang";

    /** The Constant TYPE_FSN. */
    private static final String TYPE_FSN = "FSN";

    /** The Constant TYPE_DEFAULT_PT. */
    private static final String TYPE_DEFAULT_PT = "PT";

    /** The Constant TYPE_OTHER_PT. */
    private static final String TYPE_OTHER_PT = "OTHER";

    {
        // TODO: Remove once Edition updated
        refsetToLanguages.add("900000000000509007");
        refsetToLanguages.add("31000172101");
        refsetToLanguages.add("21000172104");
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
    public void testConnectability() throws Exception {
        logger.info("TEST");
        final Client client = ClientBuilder.newClient();
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

        final Client client = ClientBuilder.newClient();
        // https://snowstorm.ihtsdotools.org/snowstorm/snomed-ct/swagger-ui.html#!/browser/MAIN%2F
        // SNOMEDCT-EE/members?referenceSet=723563008&offset=0&limit=10

        String refsetId = "";
        List<String> orderedRemainingPts = null;
        String defaultLanguageCode = null;
        int pageSize = 0;

        // TODO: Integrate with Edition below and remove hard coding listed here
        try (final TerminologyService service = new TerminologyService()) {
            // Refset r = service.get(refsetId, Refset.class);
            // Edition e = r.getEdition();
            // Hardcoding defaultLanguageCode & refsetToLanguageMap as edition
            // is blank right now

            defaultLanguageCode = "en";
            orderedRemainingPts = refsetToLanguages.stream().collect(Collectors.toList());
            orderedRemainingPts.remove(defaultLanguageCode);
            Collections.sort(orderedRemainingPts);

            refsetId = "721000172106";
            pageSize = 10;
        }

        // SHould have 3 results
        String url = BASE_URL + "browser/MAIN%2FSNOMEDCT-BE%2F2021-03-15/members?referenceSet="
                + refsetId + "&offset=0&limit=" + pageSize;
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

        Set<Concept> members = new HashSet<>();
        final ObjectMapper mapper = new ObjectMapper();
        final JsonNode root = mapper.readTree(resultString.toString());

        JsonNode items = root.get("items");
        Iterator<JsonNode> itr = items.iterator();

        while (itr.hasNext()) {
            JsonNode item = itr.next();
            Concept c = new Concept();
            c.setCode(item.get("referencedComponentId").asText());
            c.setTerminology("SNOMEDCT");
            c.setMemberStatus(item.get("active").asBoolean());
            c.setMemberEffectiveTime(SDF.parse(item.get("releasedEffectiveTime").asText()));

            members.add(c);
        }

        assertEquals(pageSize, members.size());

        Set<Concept> concepts =
                getConceptDescriptions(refsetId, members, orderedRemainingPts, defaultLanguageCode);

        for (Concept c : concepts) {
            logger.info(c.toString());
        }
    }

    /**
     * Gets the concept descriptions.
     *
     * @param refsetId the refset id
     * @param members the members
     * @param orderedRemainingPts the ordered remaining pts
     * @param defaultLanguageCode the default language code
     * @return the concept descriptions
     * @throws MalformedURLException the malformed URL exception
     * @throws Exception the exception
     */
    private Set<Concept> getConceptDescriptions(final String refsetId, final Set<Concept> members,
        final List<String> orderedRemainingPts, final String defaultLanguageCode)
        throws MalformedURLException, Exception {
        final Client client = ClientBuilder.newClient();

        StringBuffer conIds = new StringBuffer();

        for (int i = 0; i < members.size(); i++) {
            Concept c = (Concept) members.toArray()[i];
            conIds.append(c.getCode());

            if (i + 1 < members.size()) {
                conIds.append(",");
            }
        }

        // SHould have 3 results
        String url = BASE_URL + "browser/MAIN%2FSNOMEDCT-BE%2F2021-03-15/concepts?conceptIds="
                + conIds + "&offset=0&limit=10";
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

        final ObjectMapper mapper = new ObjectMapper();
        final JsonNode root = mapper.readTree(resultString.toString());

        JsonNode concepts = root.get("items");
        Iterator<JsonNode> conItr = concepts.iterator();
        HashMap<String, Set<Map<String, String>>> conDescMap = new HashMap<>();

        while (conItr.hasNext()) {
            JsonNode concept = conItr.next();
            String conId = concept.get("conceptId").asText();

            JsonNode descriptions = concept.get("descriptions");
            Iterator<JsonNode> descItr = descriptions.iterator();

            Set<Map<String, String>> descs = new HashSet<>();

            while (descItr.hasNext()) {
                Map<String, String> descMap = new HashMap<>();
                JsonNode desc = descItr.next();

                if (desc.get("active").asBoolean()) {
                    boolean isPreferred = false;
                    String typeName = null;

                    if (!"900000000000003001".equals(desc.get("typeId").asText())) {
                        JsonNode acceptabilityMap = desc.get("acceptabilityMap");

                        for (String langRefsetId : refsetToLanguages) {
                            if (acceptabilityMap.has(langRefsetId)
                                    && "PREFERRED".equals(acceptabilityMap.get(langRefsetId).asText())) {
                                isPreferred = true;
                                typeName = "PT";
                                break;
                            }
                        }
                    } else {
                        typeName = "FSN";
                    }

                    if (isPreferred || "900000000000003001".equals(desc.get("typeId").asText())) {
                        descMap.put(DESC_TERM, desc.get("term").asText());
                        descMap.put(DESC_TYPE, typeName);
                        descMap.put(DESC_LANG, desc.get("lang").asText());

                        descs.add(descMap);
                    }
                }
            }

            conDescMap.put(conId, descs);
        }

        Map<String, List<Map<String, String>>> sortedDescs =
                sortDescriptions(refsetId, conDescMap, orderedRemainingPts, defaultLanguageCode);

        for (Concept c : members) {
            c.setDescriptions(sortedDescs.get(c.getCode()));
        }

        return members;
    }

    /**
     * Sort descriptions.
     *
     * @param refsetId the refset id
     * @param conDescMap the con desc map
     * @param orderedRemainingPts the ordered remaining pts
     * @param defaultLanguageCode the default language code
     * @return the map
     * @throws Exception the exception
     */
    /*-
     * Sort descriptions in the order defined below.
     * 
     * 1)   PT – Default Lang Code
     * 2)  FSN
     * 3)  All other PTs 
     *      a.  Order by language code
     *      b.  If no translation, will be null
     */
    private Map<String, List<Map<String, String>>> sortDescriptions(final String refsetId,
        final HashMap<String, Set<Map<String, String>>> conDescMap,
        final List<String> orderedRemainingPts, final String defaultLanguageCode) throws Exception {
        Map<String, List<Map<String, String>>> retValues = new HashMap<>();

        // Actual code
        for (String conId : conDescMap.keySet()) {
            // do this for each concept
            List<Map<String, String>> sortedConDescs = new ArrayList<>();
            Map<String, Map<String, String>> sortingMap = new HashMap<>();
            int otherPtCount = 0;

            for (Map<String, String> descMap : conDescMap.get(conId)) {
                if (descMap.get(DESC_TYPE).equalsIgnoreCase("fsn")) {
                    // Always 0
                    if (sortingMap.containsKey(TYPE_FSN)) {
                        logger.warn(
                                "An FSN in the default language has already been identified for conceptId: "
                                        + conId);
                        logger.warn(
                                "Original one identified: " + printDesc(sortingMap.get(TYPE_FSN)));
                        logger.warn("New one encountered: " + printDesc(descMap));
                        continue;
                    }
                    sortingMap.put(TYPE_FSN, descMap);
                } else if (descMap.get(DESC_LANG).equals(defaultLanguageCode)) {
                    // Always 1
                    if (sortingMap.containsKey(TYPE_DEFAULT_PT)) {
                        logger.warn(
                                "A PT in the default language has already been identified for conceptId: "
                                        + conId);
                        logger.warn("Original one identified. "
                                + printDesc(sortingMap.get(TYPE_DEFAULT_PT)));
                        logger.warn("New one encountered: " + printDesc(descMap));
                        continue;
                    }
                    sortingMap.put(TYPE_DEFAULT_PT, descMap);
                } else {
                    // Always 2 + the index in orderedRemainingPts
                    int index = orderedRemainingPts.indexOf(descMap.get(DESC_LANG));

                    if (sortingMap.containsKey(TYPE_OTHER_PT + index)) {
                        logger.warn("A PT in the non-default language " + descMap.get(DESC_LANG)
                                + " has already been identified for conceptId: " + conId);
                        logger.warn("Original one identified. "
                                + printDesc(sortingMap.get(TYPE_OTHER_PT + index)));
                        logger.warn("New one encountered: " + printDesc(descMap));
                        continue;
                    }
                    otherPtCount++;
                    sortingMap.put(TYPE_OTHER_PT + index, descMap);
                }
            }

            sortedConDescs.add(sortingMap.get(TYPE_DEFAULT_PT));
            sortedConDescs.add(sortingMap.get(TYPE_FSN));

            for (int i = 0; i < otherPtCount; i++) {
                sortedConDescs.add(sortingMap.get(TYPE_OTHER_PT + i));
            }

            retValues.put(conId, sortedConDescs);
        }

        return retValues;
    }

    /**
     * Prints the desc.
     *
     * @param map the map
     * @return the string
     */
    private String printDesc(final Map<String, String> map) {
        return "Type = " + map.get(DESC_TYPE) + " for lang = " + map.get(DESC_LANG)
                + " with term = " + map.get(DESC_TERM);
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
