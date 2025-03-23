package com.tuiken.royaladmin.services;

import com.tuiken.royaladmin.model.api.output.ReignDto;
import com.tuiken.royaladmin.model.api.output.ThroneDto;
import com.tuiken.royaladmin.model.entities.Monarch;
import com.tuiken.royaladmin.model.entities.Reign;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ThroneServiceTest {

    @Autowired
    private ThroneService throneService;
    @Autowired
    private MonarchService monarchService;

    @Test
    void loadThroneByCountry() {
        List<ThroneDto> bohemia = throneService.findThrones("BOHEMIA");
        List<ReignDto> reigns = bohemia.get(0).getReigns();
        int lastYear = 0;
        for (ReignDto r : reigns) {
            String prefix = lastYear == 0 || Math.abs(lastYear - r.getEnd().getYear())<2 ? "" : "*";
            lastYear = r.getStart().getYear();
            Monarch monarch = monarchService.findByReignId(UUID.fromString(r.getId()));
            System.out.println(prefix + r.getStart() + " " + r.getEnd() + " " + monarch.getUrl());
        }
        ;

    }
}