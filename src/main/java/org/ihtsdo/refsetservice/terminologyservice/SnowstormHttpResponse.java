/*
 * Copyright 2026 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.terminologyservice;

import java.lang.annotation.Annotation;
import java.net.URI;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import jakarta.ws.rs.core.EntityTag;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.Link;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status.Family;

/**
 * Minimal {@link Response} wrapping a JDK {@link HttpResponse} so existing Snowstorm call sites keep working without RESTEasy.
 */
final class SnowstormHttpResponse extends Response {

    /** HTTP status. */
    private final int status;

    /** Buffered body (may be empty). */
    private final String body;

    /** Response headers. */
    private final HttpHeaders headers;

    /** Closed flag. */
    private boolean closed;

    /**
     * Instantiates a new snowstorm http response.
     *
     * @param status the status
     * @param body the body
     * @param headers the headers
     */
    SnowstormHttpResponse(final int status, final String body, final HttpHeaders headers) {

        this.status = status;
        this.body = body == null ? "" : body;
        this.headers = headers;
    }

    /**
     * From http response.
     *
     * @param response the response
     * @return the snowstorm http response
     */
    static SnowstormHttpResponse from(final HttpResponse<String> response) {

        return new SnowstormHttpResponse(response.statusCode(), response.body(), response.headers());
    }

    @Override
    public int getStatus() {

        return status;
    }

    @Override
    public StatusType getStatusInfo() {

        final Status statusEnum = Status.fromStatusCode(status);
        if (statusEnum != null) {
            return statusEnum;
        }
        return new StatusType() {

            @Override
            public int getStatusCode() {

                return status;
            }

            @Override
            public Family getFamily() {

                return Family.familyOf(status);
            }

            @Override
            public String getReasonPhrase() {

                return "HTTP " + status;
            }
        };
    }

    @Override
    public Object getEntity() {

        return body;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T readEntity(final Class<T> entityType) {

        if (entityType == String.class || entityType == Object.class) {
            return (T) body;
        }
        throw new IllegalArgumentException("Unsupported entity type: " + entityType);
    }

    @Override
    public <T> T readEntity(final GenericType<T> entityType) {

        throw new UnsupportedOperationException("readEntity(GenericType) is not supported");
    }

    @Override
    public <T> T readEntity(final Class<T> entityType, final Annotation[] annotations) {

        return readEntity(entityType);
    }

    @Override
    public <T> T readEntity(final GenericType<T> entityType, final Annotation[] annotations) {

        throw new UnsupportedOperationException("readEntity(GenericType, Annotation[]) is not supported");
    }

    @Override
    public boolean hasEntity() {

        return body != null && !body.isEmpty();
    }

    @Override
    public boolean bufferEntity() {

        return true;
    }

    @Override
    public void close() {

        closed = true;
    }

    @Override
    public MediaType getMediaType() {

        final String value = getHeaderString("Content-Type");
        if (value == null || value.isBlank()) {
            return null;
        }
        return MediaType.valueOf(value);
    }

    @Override
    public Locale getLanguage() {

        return null;
    }

    @Override
    public int getLength() {

        return body == null ? -1 : body.length();
    }

    @Override
    public Set<String> getAllowedMethods() {

        return Collections.emptySet();
    }

    @Override
    public Map<String, NewCookie> getCookies() {

        final Map<String, NewCookie> cookies = new LinkedHashMap<>();
        final List<String> setCookies = headers == null ? List.of() : headers.allValues("Set-Cookie");
        for (final String setCookie : setCookies) {
            if (setCookie == null || setCookie.isBlank()) {
                continue;
            }
            final String firstPart = setCookie.split(";", 2)[0].trim();
            final int eq = firstPart.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            final String name = firstPart.substring(0, eq);
            final String value = firstPart.substring(eq + 1);
            cookies.put(name, new NewCookie.Builder(name).value(value).build());
        }
        return cookies;
    }

    @Override
    public EntityTag getEntityTag() {

        return null;
    }

    @Override
    public Date getDate() {

        return null;
    }

    @Override
    public Date getLastModified() {

        return null;
    }

    @Override
    public URI getLocation() {

        final String location = getHeaderString("Location");
        if (location == null || location.isBlank()) {
            return null;
        }
        return URI.create(location);
    }

    @Override
    public Set<Link> getLinks() {

        return Collections.emptySet();
    }

    @Override
    public boolean hasLink(final String relation) {

        return false;
    }

    @Override
    public Link getLink(final String relation) {

        return null;
    }

    @Override
    public Link.Builder getLinkBuilder(final String relation) {

        return null;
    }

    @Override
    public MultivaluedMap<String, Object> getMetadata() {

        final MultivaluedMap<String, Object> map = new MultivaluedHashMap<>();
        if (headers != null) {
            for (final Map.Entry<String, List<String>> entry : headers.map().entrySet()) {
                map.put(entry.getKey(), List.copyOf(entry.getValue()));
            }
        }
        return map;
    }

    @Override
    public MultivaluedMap<String, String> getStringHeaders() {

        final MultivaluedMap<String, String> map = new MultivaluedHashMap<>();
        if (headers != null) {
            for (final Map.Entry<String, List<String>> entry : headers.map().entrySet()) {
                map.put(entry.getKey(), List.copyOf(entry.getValue()));
            }
        }
        return map;
    }

    @Override
    public String getHeaderString(final String name) {

        if (headers == null || name == null) {
            return null;
        }
        return headers.firstValue(name).orElseGet(() -> {
            // HttpHeaders is case-insensitive for firstValue in JDK; keep fallback for odd proxies
            for (final Map.Entry<String, List<String>> entry : headers.map().entrySet()) {
                if (entry.getKey().equalsIgnoreCase(name) && !entry.getValue().isEmpty()) {
                    return entry.getValue().get(0);
                }
            }
            return null;
        });
    }

    /**
     * Indicates whether or not closed is the case.
     *
     * @return <code>true</code> if so, <code>false</code> otherwise
     */
    @Override
    public boolean isClosed() {

        return closed;
    }

    /**
     * Cookie header value for subsequent requests (name=value;name2=value2).
     *
     * @return the cookie header
     */
    String toCookieHeader() {

        final StringBuilder sb = new StringBuilder();
        for (final Map.Entry<String, NewCookie> entry : getCookies().entrySet()) {
            if (sb.length() > 0) {
                sb.append(';');
            }
            sb.append(entry.getValue().getName()).append('=').append(entry.getValue().getValue());
        }
        return sb.toString();
    }

    /**
     * Unused helper kept for debugging.
     *
     * @return header map copy
     */
    Map<String, List<String>> headerMapCopy() {

        return headers == null ? Map.of() : new HashMap<>(headers.map());
    }
}
