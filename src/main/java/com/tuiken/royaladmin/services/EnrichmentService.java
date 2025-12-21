package com.tuiken.royaladmin.services;

import com.tuiken.royaladmin.builders.PersonBuilder;
import com.tuiken.royaladmin.model.api.output.MonarchApiDto;
import com.tuiken.royaladmin.model.cache.WikiCacheRecord;
import com.tuiken.royaladmin.model.entities.Monarch;
import com.tuiken.royaladmin.model.entities.Provenence;
import com.tuiken.royaladmin.model.enums.Gender;
import com.tuiken.royaladmin.model.enums.House;
import com.tuiken.royaladmin.model.enums.PersonStatus;
import com.tuiken.royaladmin.model.workflows.AiEnrichment;
import com.tuiken.royaladmin.services.ai.AiService;
import com.tuiken.royaladmin.services.wiki.WikiCacheService;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EnrichmentService {

    private final MonarchService monarchService;
    private final WikiCacheService wikiCacheService;
    private final ProvenanceService provenanceService;
    private final AiService aiService;
    private final PersonBuilder personBuilder;

    public MonarchApiDto enrichAi(UUID uuid) {
        Monarch monarch = monarchService.finById(uuid);
        if (monarch == null) {
            System.out.println("Monarch not found");
            return null;
        }
        WikiCacheRecord wikiCacheRecord = wikiCacheService.findByUrl(monarch.getUrl());
        if (wikiCacheRecord == null) {
            System.out.println("Cache record not found");
            return null;
        }
        System.out.println("AI enrichment: " + monarch.getUrl());
        String wikiData = wikiCacheRecord.getBody();
        AiEnrichment enrichment = aiService.enrichAiMonarch(wikiData);
        System.out.println(enrichment);
        return applyEnrichment(monarch, enrichment);
    }

    private MonarchApiDto applyEnrichment(Monarch monarch, AiEnrichment enrichment) {
        proceedParent(monarch, enrichment.fatherUrl, Gender.MALE);
        proceedParent(monarch, enrichment.motherUrl, Gender.FEMALE);
        enrichment.childUrls.forEach(childUrl -> {
            proceedChild(monarch, childUrl);
        });
        if (Strings.isNotBlank(enrichment.house)) {
            House house = House.HouseFromBeginningOfString(enrichment.house);
            if (house != null) monarch.getHouse().add(house);
        }
        monarch.setStatus(PersonStatus.RESOLVED);
        monarch.setProcess("AI");
        monarchService.save(monarch);
        return monarchService.toApiDto(monarch);
    }

    private void proceedChild(Monarch parent, String childUrl) {
        List<Provenence> allChildren = parent.getGender().equals(Gender.MALE) ? provenanceService.findByFather(parent.getId()) : provenanceService.findByMother(parent.getId());
        final boolean[] foundChild = {false};
        allChildren.forEach(provenance -> {
            Monarch child = monarchService.finById(provenance.getId());
            if (child != null && child.getUrl().equals(childUrl)) foundChild[0] =true;
        });
        if (!foundChild[0]) return;
        Monarch child = personBuilder.findOrCreate(childUrl, null);
        if (child == null) return;
        updateProvenence(parent, child, parent.getGender());
    }

    private void proceedParent(Monarch child, String parentUrl, Gender gender) {
        if (Strings.isBlank(parentUrl)) return;
        Monarch parent = personBuilder.findOrCreate(parentUrl, gender);
        if (parent == null) return;
        if (parent.getId() == null) monarchService.save(parent);
        updateProvenence(parent, child, gender);
    }

    private void updateProvenence(Monarch parent, Monarch child, Gender gender) {
        Provenence provenence = provenanceService.findById(child.getId());
        if (provenence == null) {
            provenence = new Provenence();
            provenence.setId(child.getId());
        }
        if (provenence.getFather() == null && Gender.MALE.equals(gender))
            provenence.setFather(parent.getId());
        if (provenence.getMother() == null && Gender.FEMALE.equals(gender))
            provenence.setMother(parent.getId());
        provenanceService.save(provenence);
    }
}
