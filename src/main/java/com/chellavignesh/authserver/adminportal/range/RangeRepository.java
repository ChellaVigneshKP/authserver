package com.chellavignesh.authserver.adminportal.range;

import com.chellavignesh.authserver.adminportal.range.entity.Range;
import com.chellavignesh.authserver.adminportal.range.entity.RangeRowMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class RangeRepository {
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Autowired
    public RangeRepository(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public List<Range> getAll() {
        return namedParameterJdbcTemplate.query(
                "{call dbo.GetRanges}",
                new MapSqlParameterSource(),
                new RangeRowMapper()
        );
    }

    public List<Range> updateRanges() {
        return namedParameterJdbcTemplate.query(
                "{call dbo.UpdateRanges}",
                new MapSqlParameterSource(),
                new RangeRowMapper()
        );
    }
}
