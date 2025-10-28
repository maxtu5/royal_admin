package com.tuiken.royaladmin.controllers;

import com.tuiken.royaladmin.model.api.input.UrlDto;
import com.tuiken.royaladmin.model.api.output.MonarchApiDto;
import com.tuiken.royaladmin.services.DataFeedService;
import com.tuiken.royaladmin.services.RepairService;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.util.Strings;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/data/repair")
@CrossOrigin
@RequiredArgsConstructor
public class RepairController {

    private final RepairService repairService;
    private final DataFeedService dataFeedService;

    @GetMapping(path = "/{code}")
    public boolean repair(@PathVariable int code) throws Exception {
        switch (code) {
            case 000:
                return repairService.reportProcess();
            case 100:
                return repairService.reportGender();
            case 200:
                return repairService.reportMissingHouses();
            case 201:
                return repairService.findUnknownHouses();
            case 202:
                return repairService.rereadHousesFromCache();

            case 400:
                return repairService.missingIdsProvenence();
            case 500:
                return repairService.reportReignCollisions();
            case 600:
                return repairService.unresolvedUrls();
            case 1000:
                return repairService.listMonarchsNotInCache();
         }
        return false;
    }

    @PostMapping(path = "/byurl")
    public List<MonarchApiDto> repairByUrl(@RequestBody UrlDto url) {
        if (Strings.isNotBlank(url.getUrl())) {
            return dataFeedService.resolveUnusedCacheRecord(url.getUrl());
        } else return null;
    }

}
