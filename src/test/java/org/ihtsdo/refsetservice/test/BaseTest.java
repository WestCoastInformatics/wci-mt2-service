
package org.ihtsdo.refsetservice.test;

import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.service.SecurityService;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Integration tests for MetadataController.
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles({
        "test"
})
public class BaseTest {
    
    /** The logger. */
    private static Logger logger = LoggerFactory.getLogger(BaseTest.class);
    
    /**
     * Adds the user.
     *
     * @param user the user
     * @return the user
     * @throws Exception the exception
     */
    protected User addUser(final User user) throws Exception {

        try (final SecurityService service = new SecurityService()) {
            service.addUser(user);

            return user;
        } catch (Exception e) {
            logger.error("Error adding user: {}", user, e);
            throw e;
        }
    }
}
