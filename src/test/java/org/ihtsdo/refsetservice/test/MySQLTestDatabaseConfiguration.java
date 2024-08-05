/*
 * Copyright 2024 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.test;

import java.util.Properties;

import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * The Class TestDatabaseConfiguration.
 */
//@Configuration
//@Profile("test")
public class MySQLTestDatabaseConfiguration {

    /** The config properties. */
    private final Properties properties = PropertyUtility.getProperties();

    /**
     * My SQL container.
     *
     * @return the my SQL container
     */
    @Bean(initMethod = "start", destroyMethod = "stop")
    public MySQLContainer<?> mySQLContainer() {

        final String dbName = properties.getProperty("flyway.database.name");
        final String user = properties.getProperty("spring.jpa.properties.hibernate.connection.username");
        final char[] pwd = properties.getProperty("spring.jpa.properties.hibernate.connection.password").toCharArray();

        return new MySQLContainer<>(DockerImageName.parse("mysql:8.0.26")).withDatabaseName(dbName).withUsername(user).withPassword(String.valueOf(pwd))
            .withCommand("mysqld --lower_case_table_names=1");
    }

}
