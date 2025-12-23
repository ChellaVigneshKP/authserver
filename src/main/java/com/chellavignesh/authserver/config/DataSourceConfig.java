package com.chellavignesh.authserver.config;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import javax.sql.DataSource;

@Configuration
@EnableScheduling
public class DataSourceConfig {
    @Value("${app.database.query-timeout}")
    private int queryTimeoutSeconds;

    @Value("${spring.database.driver-class-name}")
    private String appDriverClassName;

    @Value("${spring.datasource.url}")
    private String appDataSourceUrl;

    @Value("${spring.datasource.username}")
    private String appDataSourceUsername;

    @Value("${spring.datasource.password}")
    private String appDataSourcePassword;

    // App DataSource HikariCP Configuration Properties
    @Value("${app.datasource.hikari.maximum-pool-size}")
    private int appMaximumPoolSize;

    @Value("${app.datasource.hikari.minimum-idle}")
    private int appMinimumIdle;

    @Value("${app.datasource.hikari.connection-timeout}")
    private long appConnectionTimeout;

    @Value("${app.datasource.hikari.idle-timeout}")
    private long appIdleTimeout;

    @Value("${app.datasource.hikari.max-lifetime}")
    private long appMaxLifetime;

    @Value("${app.datasource.hikari.leak-detection-threshold}")
    private long appLeakDetectionThreshold;

    @Value("${app.datasource.hikari.validation-timeout}")
    private long appValidationTimeout;

    // Session datasource properties removed – now using Redis for session management

    // RE-ENABLED: Basic DataSource now primary, DatabaseOptimizationConfig disabled
    @Bean
    @Primary
    public DataSource appDataSource() {
        HikariDataSource hikariDataSource = new HikariDataSource();
        hikariDataSource.setDriverClassName(appDriverClassName);
        hikariDataSource.setJdbcUrl(appDataSourceUrl);
        hikariDataSource.setUsername(appDataSourceUsername);
        hikariDataSource.setPassword(appDataSourcePassword);
        hikariDataSource.setPoolName("app-db-pool");

        return getDataSource(hikariDataSource, appMaximumPoolSize, appMinimumIdle, appConnectionTimeout, appIdleTimeout, appMaxLifetime, appLeakDetectionThreshold, appValidationTimeout);
    }

    @NotNull
    private DataSource getDataSource(HikariDataSource hikariDataSource, int appMaximumPoolSize, int appMinimumIdle, long appConnectionTimeout, long appIdleTimeout, long appMaxLifetime, long appLeakDetectionThreshold, long appValidationTimeout) {
        hikariDataSource.setMaximumPoolSize(appMaximumPoolSize);
        hikariDataSource.setMinimumIdle(appMinimumIdle);
        hikariDataSource.setConnectionTimeout(appConnectionTimeout);
        hikariDataSource.setIdleTimeout(appIdleTimeout);
        hikariDataSource.setMaxLifetime(appMaxLifetime);
        hikariDataSource.setLeakDetectionThreshold(appLeakDetectionThreshold);
        hikariDataSource.setValidationTimeout(appValidationTimeout);
        hikariDataSource.setConnectionTestQuery("SELECT 1");

        return hikariDataSource;
    }

    // RE-ENABLED: NamedParameterJdbcTemplate needed by repositories
    @Bean
    @Primary
    public NamedParameterJdbcTemplate appNamedParameterJdbcTemplate(DataSource appDataSource) {
        NamedParameterJdbcTemplate template = new NamedParameterJdbcTemplate(appDataSource);

        // Set query timeout to prevent long waits during deadlocks
        template.getJdbcTemplate().setQueryTimeout(queryTimeoutSeconds);

        return template;
    }

    /**
     * FIX #14: Database connection pool health monitoring
     * Logs HikariCP metrics to detect connection leaks early
     */
    @Bean
    public HikariPoolMonitor hikariPoolMonitor(DataSource appDataSource) {
        return new HikariPoolMonitor((HikariDataSource) appDataSource);
    }

    /**
     * Monitors HikariCP connection pool health to detect leaks
     */
    public static class HikariPoolMonitor {

        private static final Logger log = LoggerFactory.getLogger(HikariPoolMonitor.class);

        private final HikariDataSource dataSource;

        public HikariPoolMonitor(HikariDataSource dataSource) {
            this.dataSource = dataSource;
        }

        @Scheduled(fixedRate = 300000) // Every 5 minutes
        public void logPoolMetrics() {
            try {
                HikariPoolMXBean poolMXBean = dataSource.getHikariPoolMXBean();
                if (poolMXBean != null) {
                    int active = poolMXBean.getActiveConnections();
                    int idle = poolMXBean.getIdleConnections();
                    int total = poolMXBean.getTotalConnections();
                    int waiting = poolMXBean.getThreadsAwaitingConnection();
                    int max = dataSource.getMaximumPoolSize();

                    // Calculate utilization percentage
                    double utilization = (double) active / max * 100;

                    // Log with visual indicators
                    String status = utilization > 90 ? "🔴 CRITICAL" : utilization > 70 ? "🟡 WARNING" : "🟢 HEALTHY";

                    // Only log healthy status at DEBUG level to reduce INFO noise
                    if (utilization > 70) {
                        log.info("📊 [DB-POOL-METRICS] {} - Pool: {}", status, dataSource.getPoolName());
                        log.info("   Active: {}/{} ({} idle) | Utilization: {}% | Waiting threads: {}", active, max, idle, String.format("%.1f", utilization), waiting);
                    } else {
                        log.debug("📊 [DB-POOL-METRICS] {} - Pool: {}", status, dataSource.getPoolName());
                    }

                    // Alert if pool is near exhaustion
                    if (utilization > 90) {
                        log.error("🚨 [DB-POOL-ALERT] Connection pool near exhaustion! Active: {}/{} ({}%)", active, max, String.format("%.1f", utilization));
                        log.error("   This may indicate connection leaks. Check for unclosed connections.");
                        log.error("   Threads waiting for connection: {}", waiting);
                    } else if (utilization > 70) {
                        log.warn("⚠️ [DB-POOL-WARNING] High connection pool usage: {}/{} ({}%)", active, max, String.format("%.1f", utilization));
                    }
                }
            } catch (Exception e) {
                log.debug("Failed to collect HikariCP metrics: {}", e.getMessage());
            }
        }
    }

    // Session datasource beans removed – now using Redis for session management
}