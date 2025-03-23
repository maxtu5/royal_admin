package com.tuiken.royaladmin.controllers;

import com.tuiken.royaladmin.exceptions.WikiApiException;
import com.tuiken.royaladmin.model.api.output.CsvFullExportDto;
import com.tuiken.royaladmin.services.ExportService;
import com.tuiken.royaladmin.services.RepairService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/data/repair")
@CrossOrigin
@RequiredArgsConstructor
public class RepairController {

    private final RepairService repairService;

    @GetMapping(path = "/{code}")
    public boolean repair(@PathVariable int code) throws WikiApiException {
        switch (code) {
            case 100: return repairService.reportGender();
            case 200: return repairService.reportMissingHouses();
            case 300: return repairService.wrongParents();
            case 400: return repairService.missingIdsProvenence();
            case 500: return repairService.reportReignCollisions();
            case 600: return repairService.unresolvedUrls();
        }
        return false;
    }
}
