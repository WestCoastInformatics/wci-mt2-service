package org.ihtsdo.refsetservice.util.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status.Family;

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
        final Client client = ClientBuilder.newClient();
        final WebTarget target = client.target("https://www.google.com");

        try (Response response = target.request("application/json").get()) {
            assertEquals(Family.SUCCESSFUL, response.getStatusInfo().getFamily());
        }
    }
}
