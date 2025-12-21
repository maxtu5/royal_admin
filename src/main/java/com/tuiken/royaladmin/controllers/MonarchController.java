package com.tuiken.royaladmin.controllers;

import com.tuiken.royaladmin.builders.PersonBuilder;
import com.tuiken.royaladmin.model.api.input.UrlDto;
import com.tuiken.royaladmin.model.api.output.MonarchApiDto;
import com.tuiken.royaladmin.model.api.output.MonarchStatsApiDto;
import com.tuiken.royaladmin.model.enums.Country;
import com.tuiken.royaladmin.services.EnrichmentService;
import com.tuiken.royaladmin.services.MonarchService;
import com.tuiken.royaladmin.services.StatsService;
import com.tuiken.royaladmin.services.ThroneLoaderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
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
    private final ThroneLoaderService throneLoaderService;
    private final StatsService statsService;
    private final PersonBuilder personBuilder;
    private final EnrichmentService enrichmentService;

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
    public ResponseEntity<MonarchApiDto> deleteMonarchsByUrl(@RequestBody String toDelete) {
        MonarchApiDto monarchApiDto = monarchService.deleteByUrl(toDelete);
        return (monarchApiDto == null) ? new ResponseEntity<>(HttpStatusCode.valueOf(404)) : new ResponseEntity<>(monarchApiDto, HttpStatus.OK);
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
        if (quantity==1 && Strings.isNotBlank(predecessor.getUrl())) {
            retval.add(throneLoaderService.addToThroneNext(predecessor.getUrl(), Country.valueOf(country)));
            return retval;
        }
        for (int i=0; i<quantity; i++) {
            retval.add(throneLoaderService.addToThroneNext(Country.valueOf(country)));
        }
        return retval;
    }

    @PostMapping(path = "/resolve/{id}")
    public List<MonarchApiDto> resolveMonarch(@PathVariable String id) {
        return throneLoaderService.loadFamilyOne(UUID.fromString(id));
    }

    @PostMapping(path = "/resolveai/{id}")
    public MonarchApiDto resolveMonarchAi(@PathVariable String id) {
        return enrichmentService.enrichAi(UUID.fromString(id));
    }

}
