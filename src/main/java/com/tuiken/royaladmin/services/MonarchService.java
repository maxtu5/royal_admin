package com.tuiken.royaladmin.services;

import com.tuiken.royaladmin.datalayer.MonarchRepository;
import com.tuiken.royaladmin.datalayer.ProvenenceRepository;
import com.tuiken.royaladmin.datalayer.ReignRepository;
import com.tuiken.royaladmin.datalayer.ThroneRepository;
import com.tuiken.royaladmin.model.api.output.MonarchApiDto;
import com.tuiken.royaladmin.model.api.output.ReignDto;
import com.tuiken.royaladmin.model.entities.Monarch;
import com.tuiken.royaladmin.model.entities.Provenence;
import com.tuiken.royaladmin.model.entities.Reign;
import com.tuiken.royaladmin.model.entities.Throne;
import com.tuiken.royaladmin.model.enums.Country;
import com.tuiken.royaladmin.model.enums.PersonStatus;
import com.tuiken.royaladmin.utils.Converters;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MonarchService {

    private final MonarchRepository monarchRepository;
    private final ReignRepository reignRepository;
    private final ProvenenceRepository provenenceRepository;
    private final ProvenanceService provenanceService;
    private final ThroneRepository throneRepository;
    private final AiResolverService aiResolverService;

    public Monarch findByUrl(String monarchUrl) {
        return monarchRepository.findByUrl(monarchUrl).orElse(null);
    }

    public Monarch finById(UUID id) {
        return monarchRepository.findById(id).orElse(null);
    }

    public List<Monarch> loadAllMonarchs() {
        List<Monarch> retval = new ArrayList<>();
        monarchRepository.findAll().forEach(retval::add);
        return retval;
    }

    public Monarch save(Monarch monarch) {
        return monarchRepository.save(monarch);
    }

    public boolean isReignsByCountry(Monarch monarch, Country country) {
        List<UUID> ids = monarch.getReignIds().stream().toList();
        return !reignRepository.findByIdInAndCountry(ids, country).isEmpty();
    }

    @Transactional
    public MonarchApiDto deleteByUrl(String url) {
        MonarchApiDto retval = null;
        Monarch monarch = findByUrl(url);
        if (monarch != null) {
            List<Provenence> related = provenanceService.findProvenencesWith(monarch);
            for (Provenence provenence: related) {
                if (provenence.getId().equals(monarch.getId())) {
                    provenanceService.deleteProvenence(provenence);
                }
                if (provenence.getFather()!= null && provenence.getFather().equals(monarch.getId())) {
                    if (provenence.getMother()==null) {
                        provenanceService.deleteProvenence(provenence);
                    } else {
                        provenence.setFather(null);
                        provenanceService.save(provenence);
                    }
                }
                if (provenence.getMother()!=null && provenence.getMother().equals(monarch.getId())) {
                    if (provenence.getFather()==null) {
                        provenanceService.deleteProvenence(provenence);
                    } else {
                        provenence.setMother(null);
                        provenanceService.save(provenence);
                    }
                }
            }
            if (!monarch.getReignIds().isEmpty()) {
                for (UUID reignId: monarch.getReignIds()) {
                    Reign reign = reignRepository.findById(reignId).orElse(null);
                    if (reign!=null) {
                        List<Throne> throne = throneRepository.findByCountry(reign.getCountry());
                        throne.get(0).getReigns().remove(reign);
                        throneRepository.save(throne.get(0));
                    }
                }
            }
            retval = toApiDto(monarch);
            System.out.printf("Deleting monarch %s, %s%n", monarch.getName(), monarch.getId());
            monarchRepository.deleteById(monarch.getId());
        }
        return retval;
    }

    public MonarchApiDto toApiDtoByUrl(String url) {
        Monarch monarch = monarchRepository.findByUrl(url).orElse(null);
        if (monarch != null) {
            MonarchApiDto retval = toApiDto(monarch);
            Provenence provenence = provenenceRepository.findById(monarch.getId()).orElse(null);
            retval.setFamily(provenanceService.toFamilyDto(monarch, provenence));
            return retval;
        }
        return null;
    }

    public MonarchApiDto toApiDto(Monarch monarch) {
        if (monarch == null) {
            return null;
        } else {
            List<ReignDto> reignDtos = new ArrayList<>();
            monarch.getReignIds().forEach(rid -> {
                Reign reignFromDb = reignRepository.findById(rid).orElse(null);
                System.out.println(rid);
                ReignDto reignDto = ReignDto.builder()
                        .title(reignFromDb.getTitle())
                        .country(reignFromDb.getCountry())
                        .start(reignFromDb.getStart() == null ? null : reignFromDb.getStart().atZone(ZoneId.systemDefault()).toLocalDate())
                        .end(reignFromDb.getEnd() == null ? null : reignFromDb.getEnd().atZone(ZoneId.systemDefault()).toLocalDate())
                        .coronation(reignFromDb.getCoronation() == null ? null : reignFromDb.getCoronation().atZone(ZoneId.systemDefault()).toLocalDate())
                        .build();
                reignDtos.add(reignDto);
            });
            return MonarchApiDto.builder()
                    .id(monarch.getId())
                    .name(monarch.getName())
                    .url(monarch.getUrl())
                    .gender(monarch.getGender())
                    .house(monarch.getHouse())
                    .birth(monarch.getBirth() == null ? null : monarch.getBirth().atZone(ZoneId.systemDefault()).toLocalDate())
                    .death(monarch.getDeath() == null ? null : monarch.getDeath().atZone(ZoneId.systemDefault()).toLocalDate())
                    .status(monarch.getStatus())
                    .reigns(reignDtos)
                    .build();
        }
    }

    public Monarch findByReignId(UUID id) {
        return monarchRepository.findByReignIdsContains(id).orElse(null);
    }

    Monarch findFirstToResolve(String id, int depth) {
        Monarch monarch = finById(UUID.fromString(id));
        if (monarch.getStatus().equals(PersonStatus.NEW_URL)) {
            return monarch;
        }
        int level = 1;
        List<Monarch> previousLevel = new ArrayList<>();
        previousLevel.add(monarch);
        while (level < depth) {
            List<Monarch> newPreviousLevel = new ArrayList<>();
            for (Monarch current : previousLevel) {
                Monarch father = provenanceService.findFather(current);
                if (father != null) {
                    if (father.getStatus().equals(PersonStatus.NEW_URL)) {
                        return father;
                    }
                    newPreviousLevel.add(father);
                }
                Monarch mother = provenanceService.findMother(current);
                if (mother != null) {
                    if (mother.getStatus().equals(PersonStatus.NEW_URL)) {
                        return mother;
                    }
                    newPreviousLevel.add(mother);
                }
                Set<Monarch> childeren = provenanceService.findChildren(monarch);
                for (Monarch child : childeren) {
                    if (child.getStatus().equals(PersonStatus.NEW_URL)) {
                        return child;
                    }
                    newPreviousLevel.add(child);
                }
            }
            previousLevel = newPreviousLevel;
            level++;
        }
        return null;
    }

    public MonarchApiDto forceCreate(MonarchApiDto toCreate) {
        Monarch monarch = new Monarch(toCreate.getUrl());
        monarch.setName(toCreate.getName());
        monarch.setBirth(Converters.toInstant(toCreate.getBirth()));
        monarch.setDeath(Converters.toInstant(toCreate.getDeath()));
        monarch.setGender(toCreate.getGender());
        monarch.setStatus(toCreate.getStatus());
        monarch.setHouse(toCreate.getHouse());
        monarch = monarchRepository.save(monarch);
        return toApiDto(monarch);
    }

    public String descriptionById(String id) {
        Monarch monarch = finById(UUID.fromString(id));
        if (Strings.isNotBlank(monarch.getDescription())) {
            return monarch.getDescription();
        } else {
            String desc = aiResolverService.createDescription(monarch.getName());
            System.out.println(desc);
            System.out.println(desc.length());
            monarch.setDescription(desc);
            save(monarch);
            return desc;
        }
    }
}
