
package org.ihtsdo.refsetservice.rest;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.lucene.queryparser.classic.QueryParserBase;
import org.ihtsdo.refsetservice.app.RecordMetric;
import org.ihtsdo.refsetservice.model.Concept;
import org.ihtsdo.refsetservice.model.PfsParameter;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.terminologyservice.RefsetMemberService;
import org.ihtsdo.refsetservice.util.ConceptResultList;
import org.ihtsdo.refsetservice.util.DateUtility;
import org.ihtsdo.refsetservice.util.ModelUtility;
import org.ihtsdo.refsetservice.util.ResultList;
import org.ihtsdo.refsetservice.util.SearchParameters;
import org.ihtsdo.refsetservice.util.TaxonomyParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

/**
 * Controller for /concept endpoints.
 */
@RestController
@Api(tags = "Refset endpoints")
@SuppressWarnings("javadoc")
public class RefsetController extends BaseController {

    /** The Constant STARTING_CONCEPT_ID. */
    // Setting it to blank ("") leaves value as default ConId (SNOMED_ROOT)
    private static final String STARTING_CONCEPT_ID = "35079003";

    /** Logger. */
    private static Logger logger = LoggerFactory.getLogger(RefsetController.class);

    /**
     * Returns the refset.
     *
     * @param refsetInternalId the internal refset ID
     * @return the concept
     * @throws Exception the exception
     */

    @ApiOperation(value = "Get the refset for the specified ID", response = Refset.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successfully retrieved the requested information"),
            @ApiResponse(code = 400, message = "Bad request"),
            @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
            @ApiImplicitParam(name = "refsetInternalId",
                    value = "The internal ID of the refset to return.", required = true,
                    dataType = "string", paramType = "path"),
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/refset/{refsetInternalId}",
            produces = "application/json")
    public @ResponseBody Refset getRefset(@PathVariable(value = "refsetInternalId")
    final String refsetInternalId) throws Exception {

        try {

            logger.info("*********** getRefset: refsetInternalId: " + refsetInternalId);

            try (TerminologyService service = new TerminologyService()) {

                final Refset refset = service.findSingle(
                        "id:" + QueryParserBase.escape(refsetInternalId) + "", Refset.class, null);

                refset.setDownloadable(true);
                refset.setFeedbackVisible(true);
                refset.setVersionList(getRefsetVersionList(refset.getRefsetId(), service));

                logger.info("*********** getRefset: refset: " + ModelUtility.toJson(refset));

                return refset;
            }

        } catch (final Exception e) {

            handleException(e);
            return null;
        }
    }

    /**
     * update active status.
     *
     * @param active the active status
     * @param refsetId The refset ID
     * @return the updated refset
     * @throws Exception the exception
     */
    @PutMapping("/refset/{refsetId}")
    Refset updateActive(final @RequestBody boolean active, final @PathVariable String refsetId)
        throws Exception {

        try {

            logger.info("*********** updateActive: active: " + active + " ; refsetId: " + refsetId);

            try (TerminologyService service = new TerminologyService()) {

                final Refset refset = service.findSingle(
                        "refsetId:" + QueryParserBase.escape(refsetId) + "", Refset.class, null);
                refset.setActive(active);
                service.setModifiedBy("restApi");
                service.update(refset);

                logger.info("*********** updateActive: refset: " + ModelUtility.toJson(refset));

                return refset;
            }

        } catch (final Exception e) {

            handleException(e);
            return null;
        }
    }

    /**
     * Search.
     *
     * @param searchParameters the search parameters
     * @param bindingResult the binding result
     * @return the string
     * @throws Exception the exception
     */
    @ApiOperation(value = "Get refset search results", response = ResultList.class,
            notes = "Use cases for search range from very simple term searches, use of paging "
                    + "parameters, additional filters, searches properties, and so on.")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successfully retrieved the requested information"),
            @ApiResponse(code = 400, message = "Bad request"),
            @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
            @ApiImplicitParam(name = "terminology",
                    value = "Terminologies to search, e.g. 'SNOMEDCT_US'", required = true,
                    dataType = "string", paramType = "query", defaultValue = "ncit"),
            @ApiImplicitParam(name = "query",
                    value = "The term, phrase, or code to be searched, e.g. 'melanoma'",
                    required = false, dataType = "string", paramType = "query", defaultValue = ""),
            @ApiImplicitParam(name = "limit", value = "The max number of results to return",
                    required = false, dataType = "int", paramType = "query", defaultValue = "0"),
            @ApiImplicitParam(name = "offset", value = "The offset for the first result",
                    required = false, dataType = "int", paramType = "query", defaultValue = "0")
    // TODO: activeOnly, sort, sortAscending
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/refset/search",
            produces = "application/json")
    public @ResponseBody ResultList<Refset> search(final SearchParameters searchParameters,
        final BindingResult bindingResult) throws Exception {

        // Check whether or not parameter binding was successful
        if (bindingResult.hasErrors()) {

            final List<FieldError> errors = bindingResult.getFieldErrors();
            final List<String> errorMessages = new ArrayList<>();

            for (final FieldError error : errors) {

                final String errorMessage = "ERROR " + bindingResult.getObjectName() + " = "
                        + error.getField() + ", " + error.getCode();
                logger.error(errorMessage);
                errorMessages.add(errorMessage);
            }

            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    String.join("\n ", errorMessages));
        }

        try (TerminologyService service = new TerminologyService()) {

            final long start = System.currentTimeMillis();
            ResultList<Refset> results = new ResultList<Refset>();

            logger.debug("******** searchParameters: " + ModelUtility.toJson(searchParameters));

            final PfsParameter pfs = new PfsParameter();

            if (searchParameters.getOffset() != null) {
                pfs.setOffset(searchParameters.getOffset());
            }

            if (searchParameters.getLimit() != null) {
                pfs.setLimit(searchParameters.getLimit());
            }

            if (searchParameters.getSortAscending() != null) {
                pfs.setAscending(searchParameters.getSortAscending());
            }

            if (searchParameters.getSort() != null) {
                pfs.setSort(searchParameters.getSort());
            }

            final ResultList<Refset> memberSearchResults =
                    RefsetMemberService.searchDirectoryMembers(searchParameters);

            results = service.find(searchParameters.getQuery(), pfs, Refset.class, null);

            for (Refset refset : results.getItems()) {

                refset.setDownloadable(true);
                refset.setFeedbackVisible(false);
                refset.setVersionList(getRefsetVersionList(refset.getRefsetId(), service));
            }
            results.setTimeTaken(System.currentTimeMillis() - start);
            logger.debug("******** results: " + ModelUtility.toJson(results));
            return results;

        } catch (final ResponseStatusException rse) {
            throw rse;

        } catch (final Exception e) {

            handleException(e);
            return null;
        }
    }

    /**
     * Refset Members.
     *
     * @param refsetInternalId the internal refset ID
     * @param searchParameters the search parameters
     * @param displayType Should results be a list or hierarchical taxonomy
     * @param taxonomyParameters the taxonomy parameters
     * @param bindingResult the binding result
     * @return the string
     * @throws Exception the exception
     */
    @ApiOperation(value = "Get refset search results", response = ResultList.class,
            notes = "Use cases for search range from very simple term searches, use of paging "
                    + "parameters, additional filters, searches properties, and so on.")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successfully retrieved the requested information"),
            @ApiResponse(code = 400, message = "Bad request"),
            @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
            @ApiImplicitParam(name = "terminology",
                    value = "Terminologies to search, e.g. 'SNOMEDCT_US'", required = true,
                    dataType = "string", paramType = "query", defaultValue = "ncit"),
            @ApiImplicitParam(name = "query",
                    value = "The term, phrase, or code to be searched, e.g. 'melanoma'",
                    required = false, dataType = "string", paramType = "query", defaultValue = ""),
            @ApiImplicitParam(name = "limit", value = "The max number of results to return",
                    required = true, dataType = "int", paramType = "query", defaultValue = "0"),
            @ApiImplicitParam(name = "offset", value = "The offset for the first result",
                    required = true, dataType = "int", paramType = "query", defaultValue = "0"),
            @ApiImplicitParam(name = "displayType", value = "Should results be a list or taxonomy",
                    required = false, dataType = "string", paramType = "query",
                    defaultValue = "list"),
            @ApiImplicitParam(name = "startingConceptId",
                    value = "For taxonomy calls the starting concept ID (exclusive - get the children of this concept not the concept itself)",
                    required = false, dataType = "string", paramType = "query", defaultValue = ""),
            @ApiImplicitParam(name = "depth",
                    value = "For taxonomy calls the depth - how many levels of children or parents to retrieve",
                    required = false, dataType = "int", paramType = "query", defaultValue = "1"),
            @ApiImplicitParam(name = "returnChildren",
                    value = "For taxonomy calls should children be returned. If false then parents will be returned",
                    required = false, dataType = "boolean", paramType = "query",
                    defaultValue = "true"),

    // TODO: activeOnly, sort, sortAscending
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/refset/{refsetInternalId}/members",
            produces = "application/json")
    public @ResponseBody ConceptResultList getMembers(@PathVariable(value = "refsetInternalId")
    final String refsetInternalId, final SearchParameters searchParameters,
        final String displayType, final TaxonomyParameters taxonomyParameters,
        final BindingResult bindingResult) throws Exception {

        // Check whether or not parameter binding was successful
        if (bindingResult.hasErrors()) {

            final List<FieldError> errors = bindingResult.getFieldErrors();
            final List<String> errorMessages = new ArrayList<>();

            for (final FieldError error : errors) {

                final String errorMessage = "ERROR " + bindingResult.getObjectName() + " = "
                        + error.getField() + ", " + error.getCode();
                logger.error(errorMessage);
                errorMessages.add(errorMessage);
            }

            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    String.join("\n ", errorMessages));
        }

        final long start = System.currentTimeMillis();
        ConceptResultList results = new ConceptResultList();

        logger.info("*********** getMembers: refsetInternalId: " + refsetInternalId);

        try {
            if (!STARTING_CONCEPT_ID.isBlank()) {
                taxonomyParameters.setStartingConceptId(STARTING_CONCEPT_ID);
            }

            results = RefsetMemberService.getRefsetMembers(refsetInternalId, searchParameters,
                    displayType, taxonomyParameters);
            logger.debug("******** results: " + ModelUtility.toJson(results));
            results.setTimeTaken(System.currentTimeMillis() - start);
            return results;

        } catch (final Exception e) {

            handleException(e);
            return null;
        }
    }

    /**
     * Export refset.
     *
     * @param refsetInternalId the internal refset id
     * @param format the format
     * @param exportType the export type
     * @param fileNameDate the file name date
     * @param startEffectiveTime the start effective time
     * @param transientEffectiveTime the transient effective time
     * @param exportMetadata the export metadata
     * @return the uri
     * @throws Exception the exception
     */
    @ApiOperation(value = "Export the refset for the specified ID", response = Refset.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successfully retrieved the requested information"),
            @ApiResponse(code = 400, message = "Bad request"),
            @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
            @ApiImplicitParam(name = "refsetInternalId",
                    value = "The internal ID of the refset to return.", required = true,
                    dataType = "string", paramType = "path"),
            @ApiImplicitParam(name = "exportType", value = "The RF2 type SNAPSHOT or DELTA.",
                    required = true, dataType = "string", paramType = "query"),
            @ApiImplicitParam(name = "format",
                    value = "The type of export: 'rf2', 'rf2_with_names', 'free_set', or 'sctids'.",
                    required = true, dataType = "string", paramType = "query"),
            @ApiImplicitParam(name = "fileNameDate",
                    value = "Format: yyyymmdd. Date to be embedded in the RF2 file names.",
                    required = true, dataType = "string", paramType = "query"),
            @ApiImplicitParam(name = "startEffectiveTime",
                    value = "Format: yyyymmdd. Can be used to produce a delta after content is versioned by filtering a SNAPSHOT export by effectiveTime.",
                    required = false, dataType = "string", paramType = "query"),
            @ApiImplicitParam(name = "transientEffectiveTime",
                    value = "Format: yyyymmdd. Add a transient effectiveTime to rows of content which are not yet versioned.",
                    required = false, dataType = "string", paramType = "query"),
            @ApiImplicitParam(name = "exportMetadata", value = "e.g.  true or false",
                    required = true, dataType = "boolean", paramType = "query"),
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/export/{refsetInternalId}",
            produces = "application/json")
    public @ResponseBody String exportRefset(@PathVariable(value = "refsetInternalId")
    final String refsetInternalId, final String format, final String exportType,
        final String fileNameDate, String startEffectiveTime, final String transientEffectiveTime,
        final boolean exportMetadata) throws Exception {

        try {

            logger.info(
                    "*********** exportRefset: refsetInternalId: type: fileNameDate: startEffectiveTime: transientEffectiveTime: exportMetadata:"
                            + refsetInternalId + "," + exportType + "," + fileNameDate + ","
                            + startEffectiveTime + "," + transientEffectiveTime + ","
                            + exportMetadata);

            try (TerminologyService service = new TerminologyService()) {

                try {

                    String url = null;

                    if (format.equals("rf2") || format.equals("rf2_with_names")) {

                        String uri = RefsetMemberService.exportRefsetRf2(refsetInternalId,
                                exportType, fileNameDate, startEffectiveTime,
                                transientEffectiveTime, exportMetadata);
                        logger.debug("******** results: " + uri);
                        url = "{\"url\": \"" + uri + "/archive\"}";

                    } else if (format.equals("sctids")) {

                        String uri = RefsetMemberService.exportRefsetSctidList(refsetInternalId,
                                exportMetadata);
                        url = "{\"url\": \"" + uri + "\"}";
                    }

                    return url;

                } catch (final Exception e) {

                    handleException(e);
                    return null;
                }
            }

        } catch (final Exception e) {

            handleException(e);
            return null;
        }
    }

    /**
     * Gets the concept details.
     *
     * @param conceptId the concept id
     * @param refsetInternalId the refset internal id
     * @return the concept details
     * @throws Exception the exception
     */
    @ApiOperation(value = "Get the concept for the specified ID", response = Refset.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successfully retrieved the requested information"),
            @ApiResponse(code = 400, message = "Bad request"),
            @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
            @ApiImplicitParam(name = "conceptId", value = "The ID of the concept to return.",
                    required = true, dataType = "string", paramType = "path"),
            @ApiImplicitParam(name = "branchPath", value = "e.g.  MAIN  or MAIN/2021-01-31",
                    required = true, dataType = "string", paramType = "query"),
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/concept/{conceptId}",
            produces = "application/json")
    public @ResponseBody Concept getConceptDetails(@PathVariable(value = "conceptId")
    final String conceptId, final String refsetInternalId) throws Exception {

        try {

            logger.info("*********** getConceptDetails: conceptId: " + conceptId);

            final Concept concept =
                    RefsetMemberService.getMemberDetails(conceptId, refsetInternalId);

            logger.info("*********** getConceptDetails: concept: " + ModelUtility.toJson(concept));

            return concept;

        } catch (final Exception e) {

            handleException(e);
            return null;
        }
    }

    /**
     * Get the full list of versions for a refset.
     *
     * @param refsetId the refset id
     * @param service the Terminology Service
     * @return the list of refset versions
     * @throws Exception the exception
     */
    private List<Map<String, String>> getRefsetVersionList(final String refsetId,
        final TerminologyService service) throws Exception {

        final List<Map<String, String>> versionList = new ArrayList<>();
        final PfsParameter pfs = new PfsParameter();
        pfs.setSort("versionDate");
        pfs.setAscending(false);

        // ResultList<Refset> test = service.find("id:
        // 78659156-b6d6-4935-bcdf-c4692bcee10d", pfs, Refset.class, null);
        // logger.debug("******** test: " + ModelUtility.toJson(test));

        final ResultList<Refset> results = service
                .find("refsetId: " + QueryParserBase.escape(refsetId), pfs, Refset.class, null);

        for (Refset refset : results.getItems()) {

            final Map<String, String> version = new HashMap<>();
            version.put("status", refset.getVersionStatus());

            if (refset.getVersionStatus().toLowerCase().equals("in development")) {

                version.put("date",
                        DateUtility.formatDate(new Date(), DateUtility.DATE_FORMAT_REVERSE, null));
                versionList.add(0, version);

            } else if ("beta, published".contains(refset.getVersionStatus().toLowerCase())) {

                version.put("date", DateUtility.formatDate(refset.getVersionDate(),
                        DateUtility.DATE_FORMAT_REVERSE, null));
                versionList.add(version);
            }
        }

        return versionList;
    }

}
