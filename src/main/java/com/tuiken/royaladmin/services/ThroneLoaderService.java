package com.tuiken.royaladmin.services;

import com.tuiken.royaladmin.builders.PersonBuilder;
import com.tuiken.royaladmin.datalayer.ReignRepository;
import com.tuiken.royaladmin.datalayer.ThroneRepository;
import com.tuiken.royaladmin.model.api.output.MonarchApiDto;
import com.tuiken.royaladmin.model.api.output.ReignDto;
import com.tuiken.royaladmin.model.entities.*;
import com.tuiken.royaladmin.model.enums.Country;
import com.tuiken.royaladmin.services.wiki.WikiService;
import com.tuiken.royaladmin.utils.Converters;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.util.Strings;
import org.json.JSONArray;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ThroneLoaderService {

    private final ThroneService throneRoom;
    private final MonarchService monarchService;
    private final WikiService wikiService;
    private final RetrieverService retrieverService;
    private final PersonBuilder personBuilder;
    private final ThroneRepository throneRepository;
    private final ReignRepository reignRepository;
    private final ProvenanceService provenanceService;

    @Transactional
    public MonarchApiDto addToThroneNext(Country country) {
        String predecessorUrl = findPredecessor(country);
        if (Strings.isNotBlank(predecessorUrl)) {
            System.out.println("Predecessor found " + predecessorUrl);
            return addToThroneNext(predecessorUrl, country);
        }
        return null;
    }

    @Transactional
    public MonarchApiDto addToThroneNext(String url, Country country) {
        Throne throne = throneRoom.loadThroneByCountry(country);
        Monarch monarch = personBuilder.findOrCreate(url, null);
        if (monarch == null) {
            System.out.println("Could not be found or created");
            return null;
        }
        monarch = monarchService.save(monarch);

        List<Reign> newReigns = personBuilder.createReignsWithSave(monarch.getUrl(), country);
        if (newReigns.size() == 1) {
            return addReignToThroneAndMonarchAndSave(newReigns.get(0), throne, monarch);
        }
        newReigns.sort((r1, r2) -> (int) Duration.between(r1.getStart(), r2.getStart()).toMinutes());
        addReignToThroneAndMonarchAndSave(newReigns.get(0), throne, monarch);
        return addReignToThroneAndMonarchAndSave(newReigns.get(1), throne, monarch);
    }

    private String findPredecessor(Country country) {
        Throne throne = throneRoom.loadThroneByCountry(country);
        if (throne != null && throne.getReigns().size() > 0) {
            Reign lastReign = throne.getReigns().get(throne.getReigns().size() - 1);
            Monarch lastMonarch = monarchService.findByReignId(lastReign.getId());
            System.out.println("Latest ruler is " + lastMonarch.getName());

            JSONArray jsonArray = null;
            jsonArray = wikiService.readJson(lastMonarch.getUrl());
            return retrieverService.retrievePredecessor(jsonArray, country);
        }
        return null;
    }

    private boolean reignsOverlap(Reign reign0, Reign reign1) {
        return reign0.getStart().minus(1, ChronoUnit.DAYS).isBefore(reign1.getEnd()) && reign1.getStart().minus(1, ChronoUnit.DAYS).isBefore(reign0.getEnd());
    }

    private MonarchApiDto addReignToThroneAndMonarchAndSave(Reign reign, Throne throne, Monarch monarch) {
        monarch.getReignIds().add(reign.getId());
        monarch = monarchService.save(monarch);
        if (monarch.getGender() == null) {
            monarch.setGender(personBuilder.detectGender(monarch));
        }
        monarch = monarchService.save(monarch);

        reign.setThrone(throne);
        throne.getReigns().add(reign);
        reignRepository.save(reign);
        throneRepository.save(throne);

        ReignDto reignDto = ReignDto.builder()
                .id(reign.getId().toString())
                .title(reign.getTitle())
                .start(Converters.toLocalDate(reign.getStart()))
                .end(Converters.toLocalDate(reign.getEnd()))
                .coronation(Converters.toLocalDate(reign.getCoronation()))
                .country(reign.getCountry())
                .build();
        List<ReignDto> reignDtos = new ArrayList<>();
        reignDtos.add(reignDto);
        return monarchService.toApiDto(monarch);
    }

}
