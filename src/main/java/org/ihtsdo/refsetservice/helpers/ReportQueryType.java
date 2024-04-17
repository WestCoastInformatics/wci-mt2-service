/*
 * Copyright 2024 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.helpers;

/**
 * Enum of query types for report. Determines type of handler to be used. NONE
 * is used for "diff" reports.
 */
public enum ReportQueryType {

  /** lucene type. */
  LUCENE,

  /** HQL type. */
  HQL,

  /** SQL type. */
  SQL,

  /** No query used, no query type. */
  NONE
}
