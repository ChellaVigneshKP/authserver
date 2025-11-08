package com.chellavignesh.authserver.adminportal.application;

import com.chellavignesh.authserver.adminportal.application.entity.ApplicationDetailRowMapper;
import com.chellavignesh.authserver.adminportal.forgotusername.entity.UsernameLookupCriteriaRowMapper;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.jdbc.core.SqlReturnResultSet;
import org.springframework.jdbc.object.StoredProcedure;

import javax.sql.DataSource;
import java.sql.Types;
import java.util.Map;

public class GetApplicationDetailProcedure extends StoredProcedure {
    public GetApplicationDetailProcedure(DataSource dataSource) {
        super(dataSource, "Client.GetApplicationDetailById");
        declareParameter(new SqlParameter("appId", Types.INTEGER));
        declareParameter(new SqlReturnResultSet("resultSet1", new ApplicationDetailRowMapper()));
        declareParameter(new SqlReturnResultSet("resultSet2", new UsernameLookupCriteriaRowMapper()));
        compile();
    }

    public Map<String, Object> execute(int appId) {
        return super.execute(appId);
    }
}
