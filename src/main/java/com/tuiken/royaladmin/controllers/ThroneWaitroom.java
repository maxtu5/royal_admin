package com.tuiken.royaladmin.controllers;

import com.tuiken.royaladmin.model.api.input.ThroneOperationDto;
import com.tuiken.royaladmin.model.api.output.MonarchApiDto;
import com.tuiken.royaladmin.model.api.output.ReignDto;
import com.tuiken.royaladmin.model.api.output.ThroneDto;
import com.tuiken.royaladmin.model.enums.Country;
import com.tuiken.royaladmin.services.ThroneService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/data/thrones")
@CrossOrigin
@RequiredArgsConstructor
public class ThroneWaitroom {

    private final ThroneService throneService;

    @GetMapping(value = {"/", "/{country}"})
    public List<ThroneDto> findThrones(@PathVariable(required = false) String country) {
        return throneService.findThrones(country);
    }

    @PutMapping(path = "/create")
    public ThroneDto createThrone(@RequestBody ThroneOperationDto throne) {
        return throneService.createThrone(throne.getCountry(), throne.getLatestMonarchUrl(), throne.getName(), throne.getFlagUrl());
    }

    @PutMapping("/addreign")
    public MonarchApiDto addToReign(@RequestBody ReignDto reignDto) {
        return throneService.addReign(reignDto);
    }

}
