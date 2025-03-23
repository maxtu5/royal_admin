package com.tuiken.royaladmin.services;

import com.tuiken.royaladmin.datalayer.MonarchRepository;
import com.tuiken.royaladmin.datalayer.ProvenenceRepository;
import com.tuiken.royaladmin.datalayer.ReignRepository;
import com.tuiken.royaladmin.datalayer.ThroneRepository;
import com.tuiken.royaladmin.model.api.output.MonarchStatsApiDto;
import com.tuiken.royaladmin.model.api.output.ThroneStatsApiDto;
import com.tuiken.royaladmin.model.entities.Monarch;
import com.tuiken.royaladmin.model.entities.Provenence;
import com.tuiken.royaladmin.model.enums.Gender;
import com.tuiken.royaladmin.model.enums.PersonStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StatsService {

    private final MonarchRepository monarchRepository;
    private final ReignRepository reignRepository;
    private final ProvenenceRepository provenenceRepository;
    private final MonarchService monarchService;
    private final ThroneRepository throneRepository;


    public MonarchStatsApiDto monarchStats() {
        List<Provenence> nullRelations = new ArrayList<>();
        List<Provenence> badGenderRelations = new ArrayList<>();
        provenenceRepository.findAll().forEach(p -> {

            if (monarchService.finById(p.getId()) == null ||
                    p.getFather() != null && monarchService.finById(p.getFather()) == null ||
                    p.getMother() != null && monarchService.finById(p.getMother()) == null) {
                System.out.println(p.getId().toString());
                nullRelations.add(p);
                if (monarchService.finById(p.getId()) == null) {
//                    provenenceRepository.delete(p);
                    System.out.println("Monarch bad: " + p.getId());
                }
                if (p.getFather() != null && monarchService.finById(p.getFather()) == null) {
                    System.out.println("Father bad: " + p.getFather() + " for " + p.getId());
//                    p.setFather(null);
//                    provenenceRepository.save(p);
                }
                if (p.getMother() != null && monarchService.finById(p.getMother()) == null) {
                    System.out.println("Mother bad: " + p.getMother() + " for " + p.getId());
//                    p.setMother(null);
//                    provenenceRepository.save(p);
                }
            }

            if (p.getFather() != null && monarchService.finById(p.getFather()).getGender() != Gender.MALE) {
//                System.out.println("BGR "+p.getId()+p.getFather().equals(p.getMother()));
                badGenderRelations.add(p);
//                p.setFather(null);
//                provenenceRepository.save(p);
            }
            if (p.getMother() != null && monarchService.finById(p.getMother()).getGender() != Gender.FEMALE) {
//                System.out.println("BGR "+p.getId()+p.getFather().equals(p.getMother()));
                badGenderRelations.add(p);
                p.setMother(null);
                provenenceRepository.save(p);
            }

        });

        List<ThroneStatsApiDto> throneStatsApiDtos = new ArrayList<>();
        throneRepository.findAll().forEach(throne -> {
            List<Monarch> monarchList = throne.getReigns().stream()
                    .map(r -> monarchService.findByReignId(r.getId()))
                    .toList();
            if (monarchList.stream()
                    .filter(m->m.getStatus()!= PersonStatus.RESOLVED)
                    .count()>0) {
                throneStatsApiDtos.add(ThroneStatsApiDto.builder()
                        .name(throne.getName())
                        .totalMonarchs(monarchList.size())
                        .resolvedLevel(1).build());
            } else {
                int level = 2;
                Monarch monarch = null;
                do {
                    for (int i = 0; i < monarchList.size(); i++) {
                        monarch = monarchService.findFirstToResolve(monarchList.get(i).getId().toString(), level);
                        if (monarch != null) break;
                    }
                    if (monarch==null) {
                        level++;
                    }
                } while (monarch==null);
                throneStatsApiDtos.add(ThroneStatsApiDto.builder()
                        .name(throne.getName())
                        .totalMonarchs(monarchList.size())
                        .resolvedLevel(level)
                        .build());
            }
        });

        return MonarchStatsApiDto.builder()
                .totalMonarchs(monarchRepository.count())
                .resolvedMonarchs(monarchRepository.countByStatus(PersonStatus.RESOLVED))
                .totalRelations(provenenceRepository.count())
                .nullRelations(nullRelations.size())
                .badGenderRelations(badGenderRelations.size())
                .resolvedByThrones(throneStatsApiDtos)
                .build();
    }
}
