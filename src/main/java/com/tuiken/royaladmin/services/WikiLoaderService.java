package com.tuiken.royaladmin.services;

import com.tuiken.royaladmin.builders.PersonBuilder;
import com.tuiken.royaladmin.datalayer.ProvenenceRepository;
import com.tuiken.royaladmin.datalayer.ReignRepository;
import com.tuiken.royaladmin.datalayer.ThroneRepository;
import com.tuiken.royaladmin.exceptions.WikiApiException;
import com.tuiken.royaladmin.model.api.output.MonarchApiDto;
import com.tuiken.royaladmin.model.api.output.ReignDto;
import com.tuiken.royaladmin.model.entities.Monarch;
import com.tuiken.royaladmin.model.entities.Provenence;
import com.tuiken.royaladmin.model.entities.Reign;
import com.tuiken.royaladmin.model.entities.Throne;
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

    public MonarchApiDto resolveFamilyNext(Country country, int depth) throws WikiApiException {
        Throne throne = throneRoom.loadThroneByCountry(country);
        if (throne == null || throne.getReigns().size() == 0) return null;

        Monarch monarch = null;
        for (int i = 0; i < throne.getReigns().size(); i++) {
            String monarchId = monarchService.findByReignId(throne.getReigns().get(i).getId())
                    .getId().toString();
            monarch = monarchService.findFirstToResolve(monarchId, depth);
            if (monarch != null) break;
        }
        if (monarch == null) return null;

        System.out.printf("\n+++ Loading family for %s +++%n", monarch.getName());

        Monarch simplified = new Monarch(monarch.getUrl());
        simplified.setId(monarch.getId());
        simplified.setName(monarch.getName());
        simplified.setGender(monarch.getGender());
        simplified.setBirth(monarch.getBirth());
        simplified.setDeath(monarch.getDeath());

        LoadFamilyConfiguration configuration = retrieverService.createLoadFamilyConfiguration(
                simplified, country);
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
