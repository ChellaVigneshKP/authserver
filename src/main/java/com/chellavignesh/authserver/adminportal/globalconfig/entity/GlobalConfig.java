package com.chellavignesh.authserver.adminportal.globalconfig.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.ResultSet;
import java.sql.SQLException;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GlobalConfig {

    private Integer id;
    private String name;
    private String description;
    private Integer value;

    public static GlobalConfig fromResult(ResultSet rs) throws SQLException {
        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.setId(rs.getInt("GlobalConfigId"));
        globalConfig.setName(rs.getString("Name"));
        globalConfig.setDescription(rs.getString("Description"));
        globalConfig.setValue(rs.getInt("Value"));
        return globalConfig;
    }

}
