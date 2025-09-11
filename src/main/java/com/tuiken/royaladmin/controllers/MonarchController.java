package com.tuiken.royaladmin.controllers;

import com.tuiken.royaladmin.builders.PersonBuilder;
import com.tuiken.royaladmin.exceptions.WikiApiException;
import com.tuiken.royaladmin.model.api.input.UrlDto;
import com.tuiken.royaladmin.model.api.output.MonarchApiDto;
import com.tuiken.royaladmin.model.api.output.MonarchStatsApiDto;
import com.tuiken.royaladmin.model.enums.Country;
import com.tuiken.royaladmin.services.MonarchService;
import com.tuiken.royaladmin.services.StatsService;
import com.tuiken.royaladmin.services.UnhandledRecordService;
import com.tuiken.royaladmin.services.WikiLoaderService;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/data/monarchs")
@CrossOrigin
@Slf4j
@RequiredArgsConstructor
public class MonarchController {

    private final MonarchService monarchService;
    private final WikiLoaderService wikiLoaderService;
    private final StatsService statsService;
    private final UnhandledRecordService unhandledRecordService;
    private final PersonBuilder personBuilder;

    @GetMapping("/stats")
    public MonarchStatsApiDto monarchStats() {
        return statsService.monarchStats();
    }

    @GetMapping(path = "/byurl")
    public MonarchApiDto findMonarchByUrl(@RequestBody UrlDto url) {
        return monarchService.toApiDtoByUrl(url.getUrl());
    }

    @GetMapping(path = "/descbyid/{id}")
    public String monarchDescByUrl(@PathVariable String id) {
        System.out.println(id);
        return monarchService.descriptionById(id);
    }

    @DeleteMapping(path = "/delete")
    public List<MonarchApiDto> deleteMonarchsByUrl(@RequestBody List<String> toDelete) {
        List<MonarchApiDto> retval = new ArrayList<>();
        for (String url : toDelete) {
            retval.add(monarchService.deleteByUrl(url));
        }
        return retval;
    }

    @PostMapping(path = "/create")
    public MonarchApiDto createMonarch(@RequestBody MonarchApiDto toCreate) {
            return monarchService.forceCreate(toCreate);
    }

    @PostMapping(path = "/createlink")
    public MonarchApiDto createMonarch(@RequestBody UrlDto urlDto) {
        return personBuilder.createFromUrl(urlDto.getUrl());
    }

    @PostMapping(path = "/predecessor/{country}/{quantity}")
    public List<MonarchApiDto> loadPredeseccors(@PathVariable String country, @PathVariable int quantity, @RequestBody UrlDto predecessor) {
        List<MonarchApiDto> retval = new ArrayList<>();
        for (int i=0; i<quantity; i++) {
            if (quantity==1 && Strings.isNotBlank(predecessor.getUrl())) {
                retval.add(wikiLoaderService.addToThroneNext(predecessor.getUrl(), Country.valueOf(country)));
            } else {
                retval.add(wikiLoaderService.addToThroneNext(Country.valueOf(country)));
            }
        }
        return retval;
    }

    @PostMapping(path = "/family/{country}/{depth}/{quantity}")
    public List<MonarchApiDto> loadRulersFamilyMembers(
            @PathVariable Country country,
            @PathVariable @Min(1) int quantity,
            @PathVariable @Min(1) int depth)
    {
        return wikiLoaderService.loadRulersFamilyMembers(country, quantity, depth);
    }

    @PostMapping(path = "/resolve/{id}")
    public List<MonarchApiDto> resolveMonarch(@PathVariable String id) {
        return wikiLoaderService.loadFamilyOne(UUID.fromString(id));
    }

    @GetMapping(path = "/unhandled")
    public int resolveUnhandled() {
        unhandledRecordService.resolve();
        return unhandledRecordService.deleteKilled();
    }

    @GetMapping(path = "/unhandled/order")
    public long resolveUnhandledFind() {
        return unhandledRecordService.orderResolve();
    }

    @GetMapping(path = "/unhandled/receive")
    public long resolveUnhandledGet() {
        return unhandledRecordService.receiveResolve();
    }

//    @PostMapping(path = "/update")
//    public MonarchApiDto updateMonarch(@RequestBody MonarchApiDto updatedMonarch) {
//        return monarchService.updateMonarch(updatedMonarch);
//    }
//
//
//
//
//    @PostMapping(path = "/loadpforce")
//    public ThroneDto loadRulerByUrl(@RequestBody ThroneOperationDto throneOperationDto) throws WikiApiException {
//        return workflowService.addToThroneByUrl(throneOperationDto.getLatestMonarchUrl(), throneOperationDto.getCountry());
//    }
//


}
