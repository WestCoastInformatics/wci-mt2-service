package org.ihtsdo.refsetservice.handler.snowstorm;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.refsetservice.util.SearchParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Class SnowstormApiPaging.
 */
public final class SnowstormApiPaging {

    /** The Constant LOG. */
    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(SnowstormApiPaging.class);

    /** The Constant MEMBER_SORT_FIELD. */
    private static final String MEMBER_SORT_FIELD = "referencedComponentId";

    /** The Constant MEMBER_SORT_ORDER. */
    private static final String MEMBER_SORT_ORDER = "asc";

    /**
     * Instantiates an empty {@link SnowstormApiPaging}.
     */
    private SnowstormApiPaging() {

        // n/a
    }

    /**
     * Snowstorm member endpoints require sortField and sortOrder together.
     *
     * @return the member sort query string
     */
    public static String getMemberSortQueryString() {

        return "sortField=" + MEMBER_SORT_FIELD + "&sortOrder=" + MEMBER_SORT_ORDER;
    }

    /**
     * Gets the paging query string.
     *
     * @param searchParameters the search parameters
     * @return the paging query string
     */
    public static String getPagingQueryString(final SearchParameters searchParameters) {

        if (searchParameters == null) {
            return "";
        }

        final StringBuilder pagingQueryString = new StringBuilder();

        // limit
        final int limit = (searchParameters.getLimit() != null && searchParameters.getLimit() > 0) ? Math.min(searchParameters.getLimit(), 10000) : 50;
        pagingQueryString.append("&limit=").append(limit);

        // searchAfter
        if (searchParameters.getSearchAfter() != null) {
            pagingQueryString.append("&searchAfter=").append(searchParameters.getSearchAfter());
        }

        // offset=0
        if (searchParameters.getOffset() != null && searchParameters.getOffset() > 0) {
            pagingQueryString.append("&offset=").append(searchParameters.getOffset());
        } else {
            pagingQueryString.append("&offset=").append(0);
        }

        // active=true
        if (searchParameters.getActiveOnly() != null) {
            pagingQueryString.append("&active=").append(searchParameters.getActiveOnly());
        } else {
            pagingQueryString.append("&active=true");
        }

        if (pagingQueryString.toString().startsWith("&")) {
            pagingQueryString.deleteCharAt(0);
        }

        return pagingQueryString.toString();
    }

    /**
     * Gets the member search paging query string.
     *
     * @param searchParameters the search parameters
     * @return the member search paging query string
     */
    public static String getMemberSearchPagingQueryString(final SearchParameters searchParameters) {

        final String pagingQueryString = getPagingQueryString(searchParameters);
        if (StringUtils.isBlank(pagingQueryString)) {
            return getMemberSortQueryString();
        }
        return pagingQueryString + "&" + getMemberSortQueryString();
    }
}
