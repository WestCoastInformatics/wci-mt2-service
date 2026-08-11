/*
 * Copyright 2025 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for allowlisted OAuth returnUrl resolution.
 */
public class SecurityControllerUnitTest {

    /**
     * Seed CORS allowlist used by {@link SecurityController#resolveAllowlistedReturnUrl(String)}.
     */
    @BeforeEach
    public void setUp() {
        PropertyUtility.setProperty("cors.allowed-origins",
            "http://localhost:4200,http://localhost:3000,http://127.0.0.1:4200,http://localhost:8888,http://local.ihtsdotools.org:8888,https://mt2-dev.westcoastinformatics.com/,https://mt2-uat.westcoastinformatics.com/");
    }

    /**
     * Local UI origin is accepted.
     */
    @Test
    public void testLocalhost4200Allowed() {
        assertEquals("http://localhost:4200", SecurityController.resolveAllowlistedReturnUrl("http://localhost:4200"));
    }

    /**
     * Local API/UI hostname used in README is accepted.
     */
    @Test
    public void testLocalIhtsdoTools8888Allowed() {
        assertEquals("http://local.ihtsdotools.org:8888",
            SecurityController.resolveAllowlistedReturnUrl("http://local.ihtsdotools.org:8888"));
    }

    /**
     * Path on an allowlisted origin is stripped to origin only.
     */
    @Test
    public void testPathStrippedToOrigin() {
        assertEquals("http://localhost:4200", SecurityController.resolveAllowlistedReturnUrl("http://localhost:4200/dashboard?x=1"));
    }

    /**
     * Trailing slash on allowlist entry still matches.
     */
    @Test
    public void testTrailingSlashAllowlistEntry() {
        assertEquals("https://mt2-dev.westcoastinformatics.com",
            SecurityController.resolveAllowlistedReturnUrl("https://mt2-dev.westcoastinformatics.com"));
    }

    /**
     * Non-allowlisted host is rejected.
     */
    @Test
    public void testRejectUnknownHost() {
        assertNull(SecurityController.resolveAllowlistedReturnUrl("https://evil.example.com"));
    }

    /**
     * javascript scheme is rejected.
     */
    @Test
    public void testRejectNonHttpScheme() {
        assertNull(SecurityController.resolveAllowlistedReturnUrl("javascript:alert(1)"));
    }
}
