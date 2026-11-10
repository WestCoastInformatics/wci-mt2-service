/*
 * Copyright 2022 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.model;

import org.ihtsdo.refsetservice.util.ResultList;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Represents a list of projects.
 */
@Schema(description = "Represents a list of projects returned from a find call")
public class ResultListProject extends ResultList<Project> {

    /**
     * Instantiates an empty {@link ResultListProject}.
     */
    public ResultListProject() {

        // n/a
    }

    /**
     * Instantiates a {@link ResultListProject} from the specified parameters.
     *
     * @param list the list
     */
    public ResultListProject(final ResultList<Project> list) {

        this.setItems(list.getItems());
        this.setParameters(list.getParameters());
        this.setTotal(list.getTotal());
    }
}
