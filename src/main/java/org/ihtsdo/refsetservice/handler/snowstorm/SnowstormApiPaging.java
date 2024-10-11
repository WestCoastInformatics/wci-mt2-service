package org.ihtsdo.refsetservice.handler.snowstorm;

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
    if (searchParameters.getLimit() != null && searchParameters.getLimit() > 0) {
      pagingQueryString.append("&limit=").append(searchParameters.getLimit());
    } else {
      pagingQueryString.append("&limit=").append(50);
    }

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
    
    if (searchParameters.getSortAscending() != null) {
        pagingQueryString.append("&sortOrder=").append((searchParameters.getSortAscending()) ? "asc" : "desc");
    } else {
        pagingQueryString.append("&sortOrder=asc");
    }
    
    if (pagingQueryString.toString().startsWith("&")) {
        pagingQueryString.deleteCharAt(0);
    }

    return pagingQueryString.toString();
  }
}
