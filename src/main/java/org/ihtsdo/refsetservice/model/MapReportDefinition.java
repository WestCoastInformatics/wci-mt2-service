/*
 * Copyright 2024 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.model;

import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;
import org.ihtsdo.refsetservice.helpers.MapUserRole;
import org.ihtsdo.refsetservice.helpers.ReportFrequency;
import org.ihtsdo.refsetservice.helpers.ReportQueryType;
import org.ihtsdo.refsetservice.helpers.ReportResultType;
import org.ihtsdo.refsetservice.helpers.ReportTimePeriod;
import org.ihtsdo.refsetservice.util.ModelUtility;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * enabled implementation of {@link MapReportDefinition}.
 */
@Entity
@Table(name = "map_report_definitions", uniqueConstraints = {
    @UniqueConstraint(columnNames = {
        "name"
    })
})
@Schema(description = "Represents a mapping report definition")
@JsonIgnoreProperties(ignoreUnknown = true)
@Indexed
public class MapReportDefinition extends AbstractHasId {

    /** The report type name. */
    @Column(nullable = false)
    private String name;

    /** The report description. */
    @Column(length = 4000, nullable = true)
    private String description;

    /** The is diff report. */
    @Column(nullable = false)
    private boolean isDiffReport = false;

    /** The is qa check. */
    @Column(nullable = false)
    private boolean isQACheck = false;

    /** The time period (in days) for diff and rate reports. */
    @Enumerated(EnumType.STRING)
    private ReportTimePeriod timePeriod;

    /** The frequency with which the report is run. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportFrequency frequency;

    /** The result type. */
    @Enumerated(EnumType.STRING)
    private ReportResultType resultType;

    /** The query type. */
    @Enumerated(EnumType.STRING)
    private ReportQueryType queryType;

    /** The query. */
    @Column(nullable = true, length = 10000)
    private String query;

    /** The role required. */
    @Enumerated(EnumType.STRING)
    private MapUserRole roleRequired;

    /**
     * The report definition used for constructing diff reports (if applicable).
     */
    @Column(nullable = true)
    private String diffReportDefinitionName;

    /**
     * Default constructor.
     */
    public MapReportDefinition() {

    }

    /**
     * Instantiates a {@link MapReportDefinition} from the specified parameters.
     *
     * @param reportDefinition the report definition
     */
    public MapReportDefinition(final MapReportDefinition reportDefinition) {

        super();
        this.name = reportDefinition.getName();
        this.description = reportDefinition.getDescription();
        this.isDiffReport = reportDefinition.isDiffReport();
        this.isQACheck = reportDefinition.isQACheck();
        this.timePeriod = reportDefinition.getTimePeriod();
        this.frequency = reportDefinition.getFrequency();
        this.resultType = reportDefinition.getResultType();
        this.queryType = reportDefinition.getQueryType();
        this.query = reportDefinition.getQuery();
        this.roleRequired = reportDefinition.getRoleRequired();
        this.diffReportDefinitionName = reportDefinition.getDiffReportDefinitionName();
    }

    /**
     * Gets the report name.
     * 
     * @return the report name
     */

    public String getName() {

        return name;
    }

    /**
     * Sets the report name.
     * 
     * @param name the new report name
     */

    public void setName(final String name) {

        this.name = name;
    }

    /**
     * Gets the description.
     *
     * @return the description
     */
    public String getDescription() {

        return this.description;
    }

    /**
     * Sets the description.
     *
     * @param description the new description
     */
    public void setDescription(final String description) {

        this.description = description;
    }

    /**
     * Gets the result type.
     *
     * @return the result type
     */
    public ReportResultType getResultType() {

        return resultType;
    }

    /**
     * Sets the result type.
     *
     * @param resultType the new result type
     */
    public void setResultType(final ReportResultType resultType) {

        this.resultType = resultType;
    }

    /**
     * Gets the query type.
     *
     * @return the query type
     */
    public ReportQueryType getQueryType() {

        return queryType;
    }

    /**
     * Sets the query type.
     *
     * @param queryType the new query type
     */
    public void setQueryType(final ReportQueryType queryType) {

        this.queryType = queryType;
    }

    /**
     * Gets the query.
     *
     * @return the query
     */
    public String getQuery() {

        return query;
    }

    /**
     * Sets the query.
     *
     * @param query the new query
     */
    public void setQuery(final String query) {

        this.query = query;
    }

    /**
     * Gets the role required.
     *
     * @return the role required
     */
    public MapUserRole getRoleRequired() {

        return roleRequired;
    }

    /**
     * Sets the role required.
     *
     * @param roleRequired the new role required
     */
    public void setRoleRequired(final MapUserRole roleRequired) {

        this.roleRequired = roleRequired;
    }

    /**
     * Checks if is diff report.
     *
     * @return true, if is diff report
     */
    public boolean isDiffReport() {

        return isDiffReport;
    }

    /**
     * Sets the diff report.
     *
     * @param isDiffReport the new diff report
     */
    public void setDiffReport(final boolean isDiffReport) {

        this.isDiffReport = isDiffReport;
    }

    /**
     * Gets the time period.
     *
     * @return the time period
     */
    public ReportTimePeriod getTimePeriod() {

        return this.timePeriod;
    }

    /**
     * Sets the time period.
     *
     * @param timePeriod the new time period
     */
    public void setTimePeriod(final ReportTimePeriod timePeriod) {

        this.timePeriod = timePeriod;

    }

    /**
     * Checks if is QA check.
     *
     * @return true, if is QA check
     */
    public boolean isQACheck() {

        return isQACheck;
    }

    /**
     * Sets the QA check.
     *
     * @param isQACheck the new QA check
     */
    public void setQACheck(final boolean isQACheck) {

        this.isQACheck = isQACheck;
    }

    /**
     * Gets the frequency.
     *
     * @return the frequency
     */
    public ReportFrequency getFrequency() {

        return this.frequency;
    }

    /**
     * Sets the frequency.
     *
     * @param timePeriod the new frequency
     */
    public void setFrequency(final ReportFrequency timePeriod) {

        this.frequency = timePeriod;
    }

    /**
     * Gets the diff report definition name.
     *
     * @return the diff report definition name
     */
    public String getDiffReportDefinitionName() {

        return diffReportDefinitionName;
    }

    /**
     * Sets the diff report definition name.
     *
     * @param diffReportDefinitionName the new diff report definition name
     */
    public void setDiffReportDefinitionName(final String diffReportDefinitionName) {

        this.diffReportDefinitionName = diffReportDefinitionName;
    }

    /**
     * To string.
     *
     * @return the string
     */
    @Override
    public String toString() {

        try {
            return ModelUtility.toJson(this);
        } catch (final Exception e) {
            return e.getMessage();
        }
    }

    /**
     * Hash code.
     *
     * @return the int
     */
    @Override
    public int hashCode() {

        return Objects.hash(description, diffReportDefinitionName, frequency, isDiffReport, isQACheck, name, query, queryType, resultType, roleRequired,
            timePeriod);
    }

    /**
     * Equals.
     *
     * @param obj the obj
     * @return true, if successful
     */
    @Override
    public boolean equals(final Object obj) {

        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final MapReportDefinition other = (MapReportDefinition) obj;
        return Objects.equals(description, other.description) && Objects.equals(diffReportDefinitionName, other.diffReportDefinitionName)
            && frequency == other.frequency && isDiffReport == other.isDiffReport && isQACheck == other.isQACheck && Objects.equals(name, other.name)
            && Objects.equals(query, other.query) && queryType == other.queryType && resultType == other.resultType && roleRequired == other.roleRequired
            && timePeriod == other.timePeriod;
    }

}
