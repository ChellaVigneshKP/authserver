package com.chellavignesh.authserver.adminportal.forgotusername.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.json.JSONArray;
import org.json.JSONObject;

import java.sql.ResultSet;
import java.sql.SQLException;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UsernameLookupCriteria {
    private Integer orgId;
    private Integer applicationId;
    private Integer priority;
    private String[] lookupCriteria;

    public static UsernameLookupCriteria fromResult(ResultSet rs) {
        UsernameLookupCriteria criteria = new UsernameLookupCriteria();
        try {
            criteria.setOrgId(rs.getInt("OrganizationId"));
            criteria.setApplicationId(rs.getInt("ApplicationId"));
            criteria.setPriority(rs.getInt("ParamPriority"));
            String lookupCriteriaJsonString = rs.getString("ParamJson");
            criteria.setLookupCriteria(UsernameLookupCriteria.lookupCriteriaFromJsonString(lookupCriteriaJsonString));
        } catch (SQLException e) {
            return null;
        }
        return criteria;
    }

    public String toJSONString() {
        JSONObject obj = new JSONObject();
        obj.put("lookupCriteria", this.getLookupCriteria());
        return obj.toString();
    }

    public boolean isEmpty() {
        return this.getLookupCriteria() == null || this.getLookupCriteria().length == 0;
    }

    public static String[] lookupCriteriaFromJsonString(String lookupCriteria) {
        if (lookupCriteria == null) return null;
        JSONObject jsonObj = new JSONObject(lookupCriteria);
        JSONArray jsonArray = jsonObj.getJSONArray("lookupCriteria");
        String[] criteria = new String[jsonArray.length()];
        for (int i = 0; i < jsonArray.length(); i++) {
            criteria[i] = jsonArray.getString(i);
        }
        return criteria;
    }
}
