package com.chellavignesh.authserver.adminportal.range;

import com.chellavignesh.authserver.adminportal.range.entity.Range;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RangeService {
    private final RangeRepository rangeRepository;

    public RangeService(RangeRepository rangeRepository) {
        this.rangeRepository = rangeRepository;
    }

    public List<Range> getAll(){
        return rangeRepository.getAll();
    }
}
