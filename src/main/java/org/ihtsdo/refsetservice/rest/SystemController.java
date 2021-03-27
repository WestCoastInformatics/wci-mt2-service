
package org.ihtsdo.refsetservice.rest;

import org.ihtsdo.refsetservice.service.TerminologyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

/**
 * Controller for /concept endpoints.
 */
@RestController
@Api(tags = "System endpoints")
public class SystemController extends BaseController {

    /** Logger. */
    @SuppressWarnings("unused")
    private static final Logger logger = LoggerFactory.getLogger(SystemController.class);

    /**
     * Reindex the database.
     *
     * @throws Exception the exception
     */
    @ApiOperation(value = "Reindex the database")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successfully retrieved the requested information"),
            @ApiResponse(code = 400, message = "Bad request"),
            @ApiResponse(code = 404, message = "Resource not found")
    })
    @RequestMapping(method = RequestMethod.GET, value = "/system/reindex",
            produces = "application/json")
    public @ResponseBody void getRefset() throws Exception {

        try {

            try (TerminologyService service = new TerminologyService()) {
                service.computeLuceneIndexes(null);
            }

        } catch (final Exception e) {
            handleException(e);
        }
    }
}
