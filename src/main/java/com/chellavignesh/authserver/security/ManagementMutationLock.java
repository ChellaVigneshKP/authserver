package com.chellavignesh.authserver.security;

import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Shared cross-instance mutation boundary for B4/B5. Acquire inside a transaction BEFORE actor,
 * role or membership reads, then organization/role rows, then profile/membership rows. Global
 * scope deliberately covers platform last-admin invariants across selected organizations.
 */
@Component
public class ManagementMutationLock {
    public static final String RESOURCE = "authserver:identity-management:v1";
    private final JdbcTemplate sql;

    public ManagementMutationLock(JdbcTemplate sql) { this.sql = sql; }

    public void acquire() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Management mutation lock requires a transaction");
        }
        try {
            sql.execute("""
                    -- SQL Server JDBC defers BEGIN until the first table statement. Read no rows
                    -- and emit no result set, so JDBC consumes the subsequent lock result/errors.
                    DECLARE @bootstrap INT;
                    IF @@TRANCOUNT=0 SELECT @bootstrap=OrganizationId FROM Partner.Organization WHERE 1=0;
                    DECLARE @result INT;
                    EXEC @result=sys.sp_getapplock @Resource=N'%s',
                        @LockMode='Exclusive', @LockOwner='Transaction', @LockTimeout=5000;
                    IF @result<0 THROW 51012, 'Management mutation lock unavailable', 1;
                    """.formatted(RESOURCE));
        } catch (UncategorizedSQLException failure) {
            if (failure.getSQLException() != null && failure.getSQLException().getErrorCode() == 51012) {
                throw new QueryTimeoutException("Management mutation lock temporarily unavailable", failure);
            }
            throw failure;
        }
    }
}
