/*
 * Copyright 2022 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.terminologyservice;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.refsetservice.util.SearchParameters;


/**
 * The Class BaseService.
 */
public abstract class BaseService {
    
    /**
     * Returns the query for active only.
     *
     * @param searchParameters the search parameters
     * @return the query for active only
     */
    protected static String getQueryForActiveOnly(final SearchParameters searchParameters) {

        final Boolean activeOnly = (searchParameters == null || searchParameters.getActiveOnly() == null) ? true : searchParameters.getActiveOnly();

        // default to active only if search parameters or active only not supplied
        if (searchParameters == null) {
            return "active:true";
        } else {
            if (StringUtils.isBlank(searchParameters.getQuery())) {
                return "active:" + activeOnly;
            } else {
                return "(" + searchParameters.getQuery() + ") AND active:" + activeOnly;
            }
        }
    }
}
