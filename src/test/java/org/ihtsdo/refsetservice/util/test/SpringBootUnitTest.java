package org.ihtsdo.refsetservice.util.test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Validates the code-system-* files in the "src/resources" folder.
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Disabled
public class SpringBootUnitTest {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(SpringBootUnitTest.class);

    /**
     * Test connectability.
     *
     * @throws Exception the exception
     */
    @Test
    public void testConnectability() throws Exception {

        LOG.info("TEST");
        final HttpClient client = HttpClient.newHttpClient();
        final HttpRequest request = HttpRequest.newBuilder().uri(URI.create("https://www.google.com")).GET().build();
        final HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
        assertTrue(response.statusCode() >= 200 && response.statusCode() < 300);
    }
}
