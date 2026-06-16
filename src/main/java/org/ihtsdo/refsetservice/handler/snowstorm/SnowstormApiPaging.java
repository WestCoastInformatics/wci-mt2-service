package org.ihtsdo.refsetservice.handler.snowstorm;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.refsetservice.util.SearchParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SnowstormApiPaging {

    /** The Constant LOG. */
    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(SnowstormApiPaging.class);

    public static String getPagingQueryString(final SearchParameters searchParameters) {

        if (searchParameters == null) {
            return "";
        }

        final StringBuilder pagingQueryString = new StringBuilder();

        // limit
        final int limit = (searchParameters.getLimit() != null && searchParameters.getLimit() > 0) 
            ? Math.min(searchParameters.getLimit(), 10000) : 50;
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

        // Snowstorm requires sortBy and sortOrder to both be present or both absent.
        if (searchParameters.getSortAscending() != null && StringUtils.isNotBlank(searchParameters.getSort())) {
            pagingQueryString.append("&sortBy=").append(searchParameters.getSort());
            pagingQueryString.append("&sortOrder=").append(searchParameters.getSortAscending() ? "asc" : "desc");
        }

        if (pagingQueryString.toString().startsWith("&")) {
            pagingQueryString.deleteCharAt(0);
        }

        return pagingQueryString.toString();
    }
}
