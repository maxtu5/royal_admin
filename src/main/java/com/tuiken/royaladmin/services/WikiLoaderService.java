package com.tuiken.royaladmin.services;

import com.tuiken.royaladmin.builders.PersonBuilder;
import com.tuiken.royaladmin.datalayer.ProvenenceRepository;
import com.tuiken.royaladmin.datalayer.ReignRepository;
import com.tuiken.royaladmin.datalayer.ThroneRepository;
import com.tuiken.royaladmin.exceptions.WikiApiException;
import com.tuiken.royaladmin.model.api.output.MonarchApiDto;
import com.tuiken.royaladmin.model.api.output.ReignDto;
import com.tuiken.royaladmin.model.entities.*;
import com.tuiken.royaladmin.model.enums.Country;
import com.tuiken.royaladmin.model.enums.PersonStatus;
import com.tuiken.royaladmin.model.workflows.LoadFamilyConfiguration;
import com.tuiken.royaladmin.model.workflows.SaveFamilyConfiguration;
import com.tuiken.royaladmin.utils.Converters;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.util.Strings;
import org.json.JSONArray;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WikiLoaderService {

    private final ThroneService throneRoom;
    private final MonarchService monarchService;
    private final WikiService wikiService;
    private final RetrieverService retrieverService;
    private final PersonBuilder personBuilder;
    private final ThroneRepository throneRepository;
    private final ReignRepository reignRepository;
    private final ProvenanceService provenanceService;
    private final ProvenenceRepository provenenceRepository;


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
        if (newReigns.size() == 2
//                && reignsOverlap(newReigns.get(0), newReigns.get(1))
        ) {
            newReigns.sort((r1, r2) -> (int) Duration.between(r1.getStart(), r2.getStart()).toMinutes());
            addReignToThroneAndMonarchAndSave(newReigns.get(0), throne, monarch);
            return addReignToThroneAndMonarchAndSave(newReigns.get(1), throne, monarch);
        } else {
            System.out.println("Not one reign");
            throw new RuntimeException();
        }
    }

    private String findPredecessor(Country country) {
        Throne throne = throneRoom.loadThroneByCountry(country);
        if (throne != null && throne.getReigns().size() > 0) {
            Reign lastReign = throne.getReigns().get(throne.getReigns().size() - 1);
            Monarch lastMonarch = monarchService.findByReignId(lastReign.getId());
            System.out.println("Latest ruler is " + lastMonarch.getName());

            JSONArray jsonArray = null;
            try {
                jsonArray = wikiService.read(lastMonarch.getUrl());
            } catch (WikiApiException e) {
                return null;
            }
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

    public List<MonarchApiDto> loadRulersFamilyMembers(Country country, int quantity, int depth) {
        List<UUID> idsToLoad = findUnresolvedIds(country, quantity, depth);
        return idsToLoad.stream().map(id->loadFamilyOne(id, country)).toList();
    }

    private List<UUID> findUnresolvedIds(Country country, int quantity, int maxDepth) {
        List<UUID> retval = new ArrayList<>();

        Throne throne = throneRoom.loadThroneByCountry(country);
        if (throne == null || throne.getReigns().size() == 0) return retval;

        Set<UUID> idsOnly = throne.getReigns().stream()
                .map(Reign::getId)
                .map(monarchService::findByReignId)
                .map(Monarch::getId).collect(Collectors.toSet());

        int depth = 0;
        while (retval.size()<quantity && depth<maxDepth)
        {
            List<MonarchIdStatus> monarchsLevel = monarchService.finByManyId(idsOnly);
            List<MonarchIdStatus> monarchsLevelUnresolved = monarchsLevel.stream().filter(ids->ids.getStatus()!=PersonStatus.RESOLVED).toList();
            retval.addAll(monarchsLevelUnresolved.stream().map(MonarchIdStatus::getId).limit(quantity - retval.size()).toList());
            idsOnly = provenanceService.findAllRelatives(monarchsLevel.stream().map(MonarchIdStatus::getId).toList());
            depth++;
        }

        return retval;
    }

    private MonarchApiDto loadFamilyOne(UUID id, Country country) {
        Monarch monarch = monarchService.finById(id);

        System.out.printf("\n+++ Loading family for %s +++%n", monarch.getName());

        Monarch unmanaged = new Monarch(monarch.getUrl());
        unmanaged.setId(monarch.getId());
        unmanaged.setName(monarch.getName());
        unmanaged.setGender(monarch.getGender());
        unmanaged.setBirth(monarch.getBirth());
        unmanaged.setDeath(monarch.getDeath());

        LoadFamilyConfiguration configuration = retrieverService.createLoadFamilyConfiguration(
                unmanaged, country);
        configuration.print();

        try {
            SaveFamilyConfiguration saveConfig = retrieverService.retrieveFamily(configuration);
            saveConfig.print();

            provenanceService.saveFamily(saveConfig);
            monarch.setStatus(PersonStatus.RESOLVED);
            monarchService.save(monarch);
        } catch (IOException | URISyntaxException e) {
            System.out.println("Crashed");
            return null;
        }
        MonarchApiDto retval = monarchService.toApiDto(monarch);
        Provenence provenence = provenenceRepository.findById(monarch.getId()).orElse(null);
        retval.setFamily(provenanceService.toFamilyDto(monarch, provenence));
        return retval;
    }
}
