/*
 * Copyright 2023 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.rest;

import java.io.BufferedReader;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.ihtsdo.refsetservice.model.RestException;
import org.ihtsdo.refsetservice.terminologyservice.SnowstormConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Hidden;


/**
 * The Class SnowstormController.
 */
@RestController
@Hidden
@RequestMapping(value = "/", produces = MediaType.APPLICATION_JSON)
public class SnowstormController extends BaseController {

	/** The Constant LOG. */
	private static final Logger LOG = LoggerFactory.getLogger(SnowstormController.class);

	/**
	 * Returns the from snowstorm.
	 *
	 * @param request the request
	 * @return the from snowstorm
	 * @throws Exception the exception
	 */
	@Hidden
	@RequestMapping(method = RequestMethod.GET, value = "/snowstorm/**")
	public @ResponseBody ResponseEntity<String> getFromSnowstorm(final HttpServletRequest request) throws Exception {

		try {
			final String url = request.getRequestURI().replace("/refsetservice/snowstorm/", "");
			final String query = request.getQueryString();

			final String fullUrl = SnowstormConnection.getBaseUrl() + url + "?" + query;

			LOG.info("Snowstorm full url: {}", fullUrl);

			try (final Response response = SnowstormConnection.getResponse(fullUrl);) {

				if (response.getStatus() != 200) {
					LOG.info("ERROR from snowstorm server {}", response.getStatus());
					LOG.info("{}", response.readEntity(String.class));
					throw new RestException(false, 417, "Expectation failed", "Error occured performing lookup.");
				}
				final String json = response.readEntity(String.class);
				return new ResponseEntity<>(json, HttpStatus.OK);
			}
		} catch (final Exception e) {
			handleException(e);
			return null;
		}
	}

	/**
	 * Returns the from snowstorm.
	 *
	 * @param request the request
	 * @return the from snowstorm
	 * @throws Exception the exception
	 */
	@Hidden
	@RequestMapping(method = RequestMethod.POST, value = "/snowstorm/**")
	public @ResponseBody ResponseEntity<String> postToSnowstorm(final HttpServletRequest request) throws Exception {

		final String url = request.getRequestURI().replace("/refsetservice/snowstorm/", "");
		final String query = request.getQueryString();
		try (final BufferedReader in = request.getReader()) {
			final String body = in.lines().collect(Collectors.joining(System.lineSeparator()));
			final String fullUrl = SnowstormConnection.getBaseUrl() + url + "?" + query;

			LOG.info("full url: {}", fullUrl);
			LOG.info("body: {}", body);

			try (final Response response = SnowstormConnection.postResponse(fullUrl, body);) {

				if (response.getStatus() < 200 && response.getStatus() > 399) {
					LOG.info("ERROR from snowstorm server {}", response.getStatus());
					LOG.info("{}", response.readEntity(String.class));
					throw new RestException(false, 417, "Expectation failed", "Error occured performing lookup.");
				}
				final String json = response.readEntity(String.class);
				return new ResponseEntity<>(json, HttpStatus.OK);
			}
		} catch (final Exception e) {
			handleException(e);
			return null;
		}
	}
}
