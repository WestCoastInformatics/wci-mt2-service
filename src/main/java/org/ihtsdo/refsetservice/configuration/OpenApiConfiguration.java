/*
 * Copyright 2026 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.configuration;

import java.io.InputStream;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;

import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.HandlerMethod;

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;

/**
 * OpenAPI 3 security schemes and response-schema fill for springdoc. Replaces SpringFox {@code Docket} security from 2.7.x.
 *
 * <p>
 * {@link org.ihtsdo.refsetservice.rest.BaseController#authorizeUser} accepts an HTTP session or {@code Authorization: Bearer} JWT.
 */
@Configuration
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT",
    description = "Application JWT from authenticate. Send as Authorization: Bearer <token>. Browser session cookies also work when using Swagger UI on the same origin.")
public class OpenApiConfiguration {

    /** Success codes that should carry a response schema when the method returns a body. */
    private static final List<String> SUCCESS_CODES = List.of("200", "201", "202");

    /**
     * Fills empty success responses from the handler return type so the generated OpenAPI remains valid.
     *
     * @return the customizer
     */
    @Bean
    public OperationCustomizer fillEmptySuccessResponseSchemas() {

        return (final Operation operation, final HandlerMethod handlerMethod) -> {

            if (operation.getResponses() == null) {
                return operation;
            }

            final Type bodyType = unwrapResponseEntity(handlerMethod.getMethod().getGenericReturnType());
            if (bodyType == null) {
                return operation;
            }

            for (final String code : SUCCESS_CODES) {
                final ApiResponse response = operation.getResponses().get(code);
                if (response == null) {
                    continue;
                }
                if (response.getContent() != null && !response.getContent().isEmpty()) {
                    continue;
                }
                response.setContent(contentFor(bodyType));
            }
            return operation;
        };
    }

    /**
     * Unwraps {@link ResponseEntity} to the body type. Returns null when there is no documentable JSON/binary body.
     *
     * @param returnType the method return type
     * @return the body type, or null
     */
    private static Type unwrapResponseEntity(final Type returnType) {

        if (returnType instanceof ParameterizedType parameterized && ResponseEntity.class.equals(parameterized.getRawType())) {
            return skipIfUndocumentable(parameterized.getActualTypeArguments()[0]);
        }
        return skipIfUndocumentable(returnType);
    }

    /**
     * Returns null for void and raw types that are not useful as JSON schemas.
     *
     * @param type the body type
     * @return the type, or null to skip
     */
    private static Type skipIfUndocumentable(final Type type) {

        if (type == null || type == Void.class || type == void.class) {
            return null;
        }
        final Class<?> raw = rawClass(type);
        if (raw == null || raw == Object.class) {
            return null;
        }
        return type;
    }

    /**
     * Builds OpenAPI content for a return type.
     *
     * @param bodyType the body type
     * @return content
     */
    private static Content contentFor(final Type bodyType) {

        final Content content = new Content();
        final MediaType mediaType = new MediaType();
        final Class<?> raw = rawClass(bodyType);
        if (raw != null && (Resource.class.isAssignableFrom(raw) || InputStream.class.isAssignableFrom(raw) || raw == byte[].class)) {
            mediaType.setSchema(new Schema<>().type("string").format("binary"));
            content.addMediaType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM_VALUE, mediaType);
            return content;
        }

        final ResolvedSchema resolved = ModelConverters.getInstance().resolveAsResolvedSchema(new AnnotatedType(bodyType).resolveAsRef(false));
        if (resolved != null && resolved.schema != null) {
            mediaType.setSchema(resolved.schema);
        }
        content.addMediaType(org.springframework.http.MediaType.APPLICATION_JSON_VALUE, mediaType);
        return content;
    }

    /**
     * Raw class for a type.
     *
     * @param type the type
     * @return the class, or null
     */
    private static Class<?> rawClass(final Type type) {

        if (type instanceof Class<?> clazz) {
            return clazz;
        }
        if (type instanceof ParameterizedType parameterized && parameterized.getRawType() instanceof Class<?> clazz) {
            return clazz;
        }
        return null;
    }
}
