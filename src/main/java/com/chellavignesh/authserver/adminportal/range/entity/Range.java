package com.chellavignesh.authserver.adminportal.range.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.ResultSet;
import java.sql.SQLException;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Range {
    private Integer id;
    private String name;
    private String description;
    private Integer min;
    private Integer max;

    public static Range fromResult(ResultSet rs) throws SQLException {
        Range range = new Range();
        range.setId(rs.getInt("RangeId"));
        range.setName(rs.getString("Name"));
        range.setDescription(rs.getString("Description"));
        range.setMin(rs.getInt("Min"));
        range.setMax(rs.getInt("Max"));
        return range;
    }

    public org.apache.commons.lang3.Range<Integer> getRange() {
        return org.apache.commons.lang3.Range.of(this.min, this.max);
    }
}
