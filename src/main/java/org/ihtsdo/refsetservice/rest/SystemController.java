
package org.ihtsdo.refsetservice.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller for /concept endpoints.
 */
// @RestController
// @Api(tags = "System endpoints")
public class SystemController extends BaseController {

    /** The Constant LOG. */
    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(SystemController.class);

    // /**
    // * Reindex the database.
    // *
    // * @throws Exception the exception
    // */
    // @ApiOperation(value = "Reindex the database")
    // @ApiResponses(value = { @ApiResponse(code = 200, message = "Successfully retrieved the requested information"),
    // @ApiResponse(code = 400, message = "Bad request"),
    // @ApiResponse(code = 404, message = "Resource not found") })
    // @RequestMapping(method = RequestMethod.GET, value = "/system/reindex", produces = "application/json")
    // public @ResponseBody void getRefset() throws Exception {
    //
    // try {
    //
    // try (final TerminologyService service = new TerminologyService()) {
    // service.computeLuceneIndexes(null);
    // }
    //
    // } catch (final Exception e) {
    // handleException(e);
    // }
    // }
}
