package com.chellavignesh.authserver.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.session.jdbc.config.annotation.SpringSessionDataSource;

import javax.sql.DataSource;

@Configuration
public class DataSourceConfig {

    @Value("${app.database.query-timeout:15}")
    private int queryTimeoutSeconds;

    @Value("${spring.datasource.driver-class-name}")
    private String driverClassName;

    @Value("${spring.datasource.url}")
    private String url;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    // Hikari properties
    @Value("${app.datasource.hikari.maximum-pool-size:40}")
    private int maximumPoolSize;

    @Value("${app.datasource.hikari.minimum-idle:20}")
    private int minimumIdle;

    @Value("${app.datasource.hikari.connection-timeout:30000}")
    private long connectionTimeout;

    @Value("${app.datasource.hikari.idle-timeout:600000}")
    private long idleTimeout;

    @Value("${app.datasource.hikari.max-lifetime:1800000}")
    private long maxLifetime;

    @Value("${app.datasource.hikari.leak-detection-threshold:600000}")
    private long leakDetectionThreshold;

    @Value("${app.datasource.hikari.validation-timeout:5000}")
    private long validationTimeout;

    @Bean
    @Primary
    public DataSource appDataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setDriverClassName(driverClassName);
        ds.setJdbcUrl(url);
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setPoolName("app-db-pool");
        ds.setMaximumPoolSize(maximumPoolSize);
        ds.setMinimumIdle(minimumIdle);
        ds.setConnectionTimeout(connectionTimeout);
        ds.setIdleTimeout(idleTimeout);
        ds.setMaxLifetime(maxLifetime);
        ds.setLeakDetectionThreshold(leakDetectionThreshold);
        ds.setValidationTimeout(validationTimeout);
        ds.setConnectionTestQuery("SELECT 1");
        return ds;
    }

    @Bean
    @Primary
    public NamedParameterJdbcTemplate appNamedParameterJdbcTemplate(DataSource appDataSource) {
        NamedParameterJdbcTemplate template = new NamedParameterJdbcTemplate(appDataSource);
        template.getJdbcTemplate().setQueryTimeout(queryTimeoutSeconds);
        return template;
    }

    @Bean
    @SpringSessionDataSource
    public DataSource springSessionDataSource() {
        return appDataSource();
    }

    @Bean
    public NamedParameterJdbcTemplate sessionNamedParameterJdbcTemplate(DataSource springSessionDataSource) {
        NamedParameterJdbcTemplate template = new NamedParameterJdbcTemplate(springSessionDataSource);
        template.getJdbcTemplate().setQueryTimeout(queryTimeoutSeconds);
        return template;
    }
}