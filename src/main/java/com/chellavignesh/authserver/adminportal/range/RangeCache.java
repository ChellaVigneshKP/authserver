package com.chellavignesh.authserver.adminportal.range;

import com.chellavignesh.authserver.adminportal.range.entity.Range;
import com.chellavignesh.authserver.enums.entity.RangeTypeEnum;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RangeCache {

    private final RangeService rangeService;

    private Map<String, Range> rangeMap = null;

    public RangeCache(RangeService rangeService) {
        this.rangeService = rangeService;
    }

    public Range getRange(RangeTypeEnum rangeEnum) {
        return getRangeMap().get(rangeEnum.getRangeType());
    }

    public Map<String, Range> getRangeMap() {
        if (rangeMap == null) {
            rangeMap = rangeService.getAll().stream().collect(
                    Collectors.toMap(
                            Range::getName,
                            range -> range
                    )
            );
        }
        return rangeMap;
    }
}
