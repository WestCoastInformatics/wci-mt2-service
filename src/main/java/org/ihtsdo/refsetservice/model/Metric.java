
package org.ihtsdo.refsetservice.model;

import java.util.Date;
import java.util.Map;

import org.ihtsdo.refsetservice.app.MetricAdvice;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Represents a metric associated with a REST call.
 * @see MetricAdvice
 */

public class Metric {

  /** The remote ip address. */
  private String remoteIpAddress;

  /** The end point. */
  private String endPoint;

  /** The query params. */
  private Map<String, String[]> queryParams;

  /** The start time. */
  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
  private Date startTime;

  /** The end time. */
  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
  private Date endTime;

  /** The duration. */
  private Long duration;

  /** The hostname. */
  private String hostName;

  /**
   * Returns the remote ip address.
   *
   * @return the remote ip address
   */
  public String getRemoteIpAddress() {

    return remoteIpAddress;
  }

  /**
   * Sets the remote ip address.
   *
   * @param remoteIpAddress the remote ip address
   */
  public void setRemoteIpAddress(final String remoteIpAddress) {

    this.remoteIpAddress = remoteIpAddress;
  }

  /**
   * Returns the end point.
   *
   * @return the end point
   */
  public String getEndPoint() {

    return endPoint;
  }

  /**
   * Sets the end point.
   *
   * @param endPoint the end point
   */
  public void setEndPoint(final String endPoint) {

    this.endPoint = endPoint;
  }

  /**
   * Returns the query params.
   *
   * @return the query params
   */
  public Map<String, String[]> getQueryParams() {

    return queryParams;
  }

  /**
   * Sets the query params.
   *
   * @param queryParams the query params
   */
  public void setQueryParams(final Map<String, String[]> queryParams) {

    this.queryParams = queryParams;
  }

  /**
   * Returns the start time.
   *
   * @return the start time
   */
  public Date getStartTime() {

    return startTime;
  }

  /**
   * Sets the start time.
   *
   * @param startTime the start time
   */
  public void setStartTime(final Date startTime) {

    this.startTime = startTime;
  }

  /**
   * Returns the end time.
   *
   * @return the end time
   */
  public Date getEndTime() {

    return endTime;
  }

  /**
   * Sets the end time.
   *
   * @param endTime the end time
   */
  public void setEndTime(final Date endTime) {

    this.endTime = endTime;
  }

  /**
   * Returns the duration.
   *
   * @return the duration
   */
  public Long getDuration() {

    return duration;
  }

  /**
   * Sets the duration.
   *
   * @param duration the duration
   */
  public void setDuration(final Long duration) {

    this.duration = duration;
  }

  /**
   * Returns the host name.
   *
   * @return the host name
   */
  public String getHostName() {

    return hostName;
  }

  /**
   * Sets the host name.
   *
   * @param hostName the host name
   */
  public void setHostName(final String hostName) {

    this.hostName = hostName;
  }

  /**
   * To string.
   *
   * @return the string
   */
  /* see superclass */
  @Override
  public String toString() {

    try {
      return new ObjectMapper().writeValueAsString(this);
    } catch (final Exception e) {
      return e.getMessage();
    }
  }

}
