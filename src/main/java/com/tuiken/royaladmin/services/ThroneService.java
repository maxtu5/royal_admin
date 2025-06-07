package com.tuiken.royaladmin.services;

import com.tuiken.royaladmin.builders.PersonBuilder;
import com.tuiken.royaladmin.datalayer.ReignRepository;
import com.tuiken.royaladmin.datalayer.ThroneRepository;
import com.tuiken.royaladmin.model.api.output.MonarchApiDto;
import com.tuiken.royaladmin.model.api.output.ReignDto;
import com.tuiken.royaladmin.model.api.output.ThroneDto;
import com.tuiken.royaladmin.model.entities.Monarch;
import com.tuiken.royaladmin.model.entities.Reign;
import com.tuiken.royaladmin.model.entities.Throne;
import com.tuiken.royaladmin.model.enums.Country;
import com.tuiken.royaladmin.utils.Converters;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ThroneService {

    private final ThroneRepository throneRepository;
    private final PersonBuilder personBuilder;
    private final MonarchService monarchService;
    private final ReignRepository reignRepository;

    public List<Throne> loadAllThrones() {
        List<Throne> retval = new ArrayList<>();
        throneRepository.findAll().forEach(retval::add);
        return retval;
    }

    @Transactional
    public List<ThroneDto> findThrones(String country) {
        List<ThroneDto> retval = new ArrayList<>();
        throneRepository.findAll()
                .forEach(t -> {
                    if (Strings.isBlank(country) || t.getCountry().equals(Country.valueOf(country))) {
                        retval.add(ThroneDto.builder()
                                .id(t.getId().toString())
                                .name(t.getName())
                                .country(t.getCountry().toString())
                                .reigns(t.getReigns().stream()
                                        .map(Reign::getId)
                                        .map(id -> {
                                            Reign reign = reignRepository.findById(id).orElse(null);
                                            Monarch monarch = monarchService.findByReignId(id);
                                            MonarchApiDto monarchApiDto = monarchService.toApiDto(monarch);
                                            return reign == null ? null : ReignDto.builder()
                                                    .id(id.toString())
                                                    .title(reign.getTitle())
                                                    .start(Converters.toLocalDate(reign.getStart()))
                                                    .end(Converters.toLocalDate(reign.getEnd()))
                                                    .monarch(monarchApiDto)
                                                    .build();
                                        })
                                        .collect(Collectors.toList()))
                                .build());
                    }
                });
        return retval;
    }

    public Throne loadThroneByCountry(Country country) {
        List<Throne> list = throneRepository.findByCountry(country);
        return list != null && list.size() == 1 ? list.get(0) : null;
    }

    @Transactional
    public ThroneDto createThrone(Country country, String latestMonarchUrl, String name, String flagUrl) {

        Throne throne = new Throne();
        throne.setCountry(country);
        throne.setName(name);
        throne.setFlagUrl(flagUrl);
        throneRepository.save(throne);

        Monarch monarch = personBuilder.findOrCreate(latestMonarchUrl, null);
        if (monarch == null) {
            System.out.println("Could not be found or created");
            return null;
        }
        monarch = monarchService.save(monarch);

        if (monarchService.isReignsByCountry(monarch, country)) {
            System.out.println("Monarch contains reigns for non-existing thrones");
            return null;
        }

        List<Reign> newReigns = personBuilder.createReignsWithSave(monarch.getUrl(), country);
        monarch.getReignIds().addAll(newReigns.stream().map(Reign::getId).toList());
        monarch = monarchService.save(monarch);

        if (monarch.getGender()==null) {
            monarch.setGender(personBuilder.detectGender(monarch));
        }
        monarchService.save(monarch);

        throne.getReigns().add(newReigns.get(0));
        throneRepository.save(throne);
        newReigns.get(0).setThrone(throne);
        reignRepository.save(newReigns.get(0));

        return ThroneDto.builder()
                .id(throne.getId().toString())
                .country(throne.getCountry().toString())
                .name(throne.getName())
                .build();
    }

    @Transactional
    public MonarchApiDto addReign(@RequestBody ReignDto reignDto) {
        Throne throne = loadThroneByCountry(reignDto.getCountry());
        Reign reign = new Reign();
        reign.setThrone(throne);
        reign.setCountry(reignDto.getCountry());
        reign.setTitle(reignDto.getTitle());
        reign.setStart(Converters.toInstant(reignDto.getStart()));
        reign.setEnd(Converters.toInstant(reignDto.getEnd()));
        reign.setCoronation(Converters.toInstant(reignDto.getCoronation()));
        Monarch monarch = monarchService.findByUrl(reignDto.getMonarch().getUrl());
        reign = reignRepository.save(reign);
        monarch.getReignIds().add(reign.getId());
        monarchService.save(monarch);
        throne.getReigns().add(reign);
        throneRepository.save(throne);
        return monarchService.toApiDto(monarch);
    }

    public Throne save(Throne throne) {
        return throneRepository.save(throne);
    }
}
