package com.tuiken.royaladmin.services;

import com.tuiken.royaladmin.datalayer.MonarchRepository;
import com.tuiken.royaladmin.datalayer.ProvenenceRepository;
import com.tuiken.royaladmin.model.api.output.FamilyDto;
import com.tuiken.royaladmin.model.api.output.ShortMonarchDto;
import com.tuiken.royaladmin.model.entities.Monarch;
import com.tuiken.royaladmin.model.entities.Provenence;
import com.tuiken.royaladmin.model.enums.Gender;
import com.tuiken.royaladmin.model.workflows.SaveFamilyConfiguration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProvenanceService {

    private final ProvenenceRepository provenenceRepository;
    private final MonarchRepository monarchRepository;

    public List<Provenence> findAllProvenances() {
        List<Provenence> retval = new ArrayList<>();
        provenenceRepository.findAll().forEach(retval::add);
        return retval;
    }
    public Monarch findFather(Monarch child) {
        Provenence provenence = provenenceRepository.findById(child.getId()).orElse(null);
        if (provenence != null && provenence.getFather() != null) {
            return monarchRepository.findById(provenence.getFather()).orElse(null);
        }
        return null;
    }

    public Monarch findMother(Monarch child) {
        Provenence provenence = provenenceRepository.findById(child.getId()).orElse(null);
        if (provenence != null && provenence.getMother() != null) {
            return monarchRepository.findById(provenence.getMother()).orElse(null);
        }
        return null;
    }

    public Set<Monarch> findChildren(Monarch parent) {
        List<Provenence> children = parent.getGender() == Gender.MALE ?
                provenenceRepository.findByFather(parent.getId()) :
                provenenceRepository.findByMother(parent.getId());
        return children.stream()
                .map(Provenence::getId)
                .map(id->monarchRepository.findById(id).orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    public void saveFamily(SaveFamilyConfiguration saveConfig) {
        for (Provenence source : saveConfig.getToCreate()) {
            Provenence existing = provenenceRepository.findById(source.getId()).orElse(source);
            if (existing != source) {
                if (existing.getFather() == null && source.getFather() != null) existing.setFather(source.getFather());
                if (existing.getMother() == null && source.getMother() != null) existing.setMother(source.getMother());
            }
            provenenceRepository.save(existing);
        }
    }

    public FamilyDto toFamilyDto(Monarch monarch, Provenence provenence) {
        FamilyDto family = new FamilyDto();

        if (provenence!=null) {
            if (provenence.getFather() != null) {
                Monarch father = monarchRepository.findById(provenence.getFather()).orElse(null);
                ShortMonarchDto dad = new ShortMonarchDto(father.getName(), father.getUrl());
                family.setFather(dad);
            }
            if (provenence.getMother() != null) {
                Monarch mother = monarchRepository.findById(provenence.getMother()).orElse(null);
                ShortMonarchDto mum = new ShortMonarchDto(mother.getName(), mother.getUrl());
                family.setMother(mum);
            }
        }
        Set<Monarch> children = findChildren(monarch);
        family.setChildren(children.stream()
                .map(m->new ShortMonarchDto(m.getName(),m.getUrl())).collect(Collectors.toList()));
        return family;
    }

    public void deleteProvenence(Provenence p) {
        provenenceRepository.delete(p);
    }

    public void setParent(Monarch child, Monarch parent) {
        Provenence provenence = provenenceRepository.findById(child.getId()).orElse(new Provenence(child.getId()));
        if (parent.getGender().equals(Gender.MALE)) provenence.setFather(parent.getId());
        if (parent.getGender().equals(Gender.FEMALE)) provenence.setMother(parent.getId());
        if (provenence.getFather()!=null || provenence.getMother()!=null) {
            provenenceRepository.save(provenence);
        }
    }
}
