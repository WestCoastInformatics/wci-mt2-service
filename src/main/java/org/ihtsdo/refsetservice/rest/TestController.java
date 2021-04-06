
package org.ihtsdo.refsetservice.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test controller.
 */
@RestController
public class TestController {

    /** Logger. */
    @SuppressWarnings("unused")
    private static Logger logger = LoggerFactory.getLogger(TestController.class);

    /**
     * Info.
     *
     * @return the string
     */
    @GetMapping("/test/info")
    public String info() {
        return "welcome";
    }
}
