package com.tuiken.royaladmin.services;

import com.tuiken.royaladmin.builders.PersonBuilder;
import com.tuiken.royaladmin.model.api.output.MonarchApiDto;
import com.tuiken.royaladmin.model.cache.WikiCacheRecord;
import com.tuiken.royaladmin.model.entities.Monarch;
import com.tuiken.royaladmin.model.entities.Provenence;
import com.tuiken.royaladmin.model.enums.Gender;
import com.tuiken.royaladmin.model.enums.PersonStatus;
import com.tuiken.royaladmin.model.workflows.AiEnrichment;
import com.tuiken.royaladmin.model.workflows.LoadFamilyConfiguration;
import com.tuiken.royaladmin.services.ai.AiService;
import com.tuiken.royaladmin.services.wiki.WikiCacheService;
import com.tuiken.royaladmin.utils.HouseParser;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EnrichmentService {

    private final MonarchService monarchService;
    private final WikiCacheService wikiCacheService;
    private final ProvenanceService provenanceService;
    private final AiService aiService;
    private final PersonBuilder personBuilder;
    private final RetrieverService retrieverService;

    public MonarchApiDto enrichAi(UUID uuid) {
        Monarch monarch = monarchService.finById(uuid);
        if (monarch == null || !monarch.getStatus().equals(PersonStatus.NEW_AI)) {
            System.out.println("Monarch not found or not NEW AI");
            return null;
        }
        WikiCacheRecord wikiCacheRecord = wikiCacheService.findByUrl(monarch.getUrl());
        if (wikiCacheRecord == null) {
            System.out.println("Cache record not found");
            return null;
        }

        System.out.println("\n+++ AI enrichment: " + monarch.getUrl());
        String wikiData = wikiCacheRecord.getBody();
        AiEnrichment enrichment = aiService.enrichAiMonarch(wikiData);
        System.out.println(enrichment);
        return applyAiEnrichment(monarch, enrichment);
    }

    private MonarchApiDto applyAiEnrichment(Monarch monarch, AiEnrichment enrichment) {
        proceedParent(monarch, enrichment.fatherUrl, Gender.MALE);
        proceedParent(monarch, enrichment.motherUrl, Gender.FEMALE);
        enrichment.childUrls.forEach(childUrl -> {
            proceedChild(monarch, childUrl);
        });
        if (Strings.isNotBlank(enrichment.house)) {
            monarch.getHouse().addAll(HouseParser.parseHouse(enrichment.house));
        }
        if (Strings.isNotBlank(enrichment.title)) monarch.setTitle(enrichment.title.toUpperCase());
        monarch.setStatus(PersonStatus.RESOLVED_AI);
        monarchService.save(monarch);
        return monarchService.toApiDto(monarch);
    }

    private void proceedChild(Monarch parent, String childUrl) {
        // validate url
        if (Strings.isBlank(childUrl) || childUrl.contains("?action=") || childUrl.contains("#")) return;

        // find all children of parent, search for given child in the list
        List<Provenence> dbChildren = parent.getGender().equals(Gender.MALE) ?
                provenanceService.findByFather(parent.getId()) : provenanceService.findByMother(parent.getId());
        final boolean[] foundChild = {false};
        dbChildren.forEach(provenance -> {
            Monarch child = monarchService.finById(provenance.getId());
            if (child != null && child.getUrl().equals(childUrl)) foundChild[0] =true;
        });
        System.out.println("== Child URL: " + (foundChild[0] ? "Exists" : childUrl));

        // if not found, find or create child
        if (foundChild[0]) return;
        Monarch child = personBuilder.findOrCreate(childUrl, null);
        if (child == null) return;
        if (child.getStatus().equals(PersonStatus.EPHEMERAL)) child.setStatus(PersonStatus.NEW_AI);
        monarchService.save(child);

        // update parent-child info
        updateProvenence(parent.getId(), child.getId(), parent.getGender());
    }

    private void proceedParent(Monarch child, String parentUrl, Gender gender) {
        // validate url
        if (Strings.isBlank(parentUrl) || parentUrl.contains("?action=") || parentUrl.contains("#")) return;

        // try to find parent in db
        String parentWord = gender.equals(Gender.MALE) ? "Father" : "Mother";
        Monarch dbparent = gender.equals(Gender.MALE) ? provenanceService.findFather(child) : provenanceService.findMother(child);
        if (dbparent != null) {
            System.out.println("== " + parentWord + " exists, not adding");
            return;
        };

        // create parent if not found
        System.out.println("== " + parentWord+ " url: " + parentUrl);
        Monarch parent = personBuilder.findOrCreate(parentUrl, gender);
        if (parent == null) return;
        if (parent.getId() == null) monarchService.save(parent);

        // update parent info
        updateProvenence(parent.getId(), child.getId(), gender);
    }

    private void updateProvenence(UUID parent, UUID child, Gender gender) {
        Provenence provenence = provenanceService.findById(child);
        if (provenence == null) {
            provenence = new Provenence();
            provenence.setId(child);
        }
        if (provenence.getFather() == null && Gender.MALE.equals(gender)) {
            provenence.setFather(parent);
        }
        if (provenence.getMother() == null && Gender.FEMALE.equals(gender)) {
            provenence.setMother(parent);
        }
        provenanceService.save(provenence);
    }

    @Transactional
    public List<MonarchApiDto> enrichInfobox(UUID uuid) {
        Monarch monarch = monarchService.finById(uuid);
        if (monarch == null || !monarch.getStatus().equals(PersonStatus.NEW_URL)) {
            System.out.println("Monarch not found or not NEW URL");
            return null;
        }
        WikiCacheRecord wikiCacheRecord = wikiCacheService.findByUrl(monarch.getUrl());
        if (wikiCacheRecord == null) {
            System.out.println("Cache record not found");
            return null;
        }

        System.out.printf("\n+++ Loading family for %s +++%n", monarch.getName());
        LoadFamilyConfiguration enrichment = retrieverService.createLoadFamilyConfiguration(monarch.getId(), monarch.getUrl(), monarch.getGender());
        enrichment.print();
        List<Monarch> saved = applyInfoboxEnrichment(monarch, enrichment); // retrieverService.saveLoaded(enrichment);

        monarch.setProcess("PRNTOK");
        monarch.setStatus(PersonStatus.RESOLVED);
        monarchService.save(monarch);

        MonarchApiDto retval = monarchService.toApiDto(monarch);
        Provenence provenence = provenanceService.findById(monarch.getId());
        retval.setFamily(provenanceService.toFamilyDto(monarch, provenence));
        List<MonarchApiDto> monarchApiDtos = new ArrayList<>();
        monarchApiDtos.add(retval);
        monarchApiDtos.addAll(saved.stream().
                filter(m -> !m.getStatus().equals(PersonStatus.RESOLVED))
                .map(monarchService::toApiDto)
                .toList());
        return monarchApiDtos;
    }

    private List<Monarch> applyInfoboxEnrichment(Monarch monarch, LoadFamilyConfiguration enrichment) {
        System.out.println("=== SAVING ===");
        int savedRels = 0;
        int savedMonarchs = 0;
        List<Monarch> newMonarchs = new ArrayList<>();
//        if (enrichment.getIssue().isEmpty())
//            return newMonarchs;

        // parents
        savedMonarchs += proceedParentInfobox(monarch, enrichment.getFather(), Gender.MALE);
        if (enrichment.getFather()!=null && !enrichment.getFather().getStatus().equals(PersonStatus.RESOLVED)&& !enrichment.getFather().getStatus().equals(PersonStatus.RESOLVED_AI))
            newMonarchs.add(enrichment.getFather());
        savedMonarchs += proceedParentInfobox(monarch, enrichment.getMother(), Gender.FEMALE);
        if (enrichment.getMother()!=null && !enrichment.getMother().getStatus().equals(PersonStatus.RESOLVED)&& !enrichment.getMother().getStatus().equals(PersonStatus.RESOLVED_AI))
            newMonarchs.add(enrichment.getMother());

        // children
        for (Monarch child : enrichment.getIssue()) {
            boolean newMonarch = child.getId()==null;
            savedMonarchs += proceedChildInfobox(monarch, child);
            if (newMonarch || !child.getStatus().equals(PersonStatus.RESOLVED) && !child.getStatus().equals(PersonStatus.RESOLVED_AI) )
                newMonarchs.add(child);
        }

        System.out.println("Monarchs:  " + savedMonarchs);
        System.out.println("Relations: " + savedRels);
        return newMonarchs;

    }

    private int proceedChildInfobox(Monarch parent, Monarch child) {
        int saved = 0;
        boolean mustSave = false;
        if (child.getId() == null) {
            saved++;
            mustSave = true;
        }
        if (mustSave) monarchService.save(child);

        // find all children of parent, search for given child in the list
        List<Provenence> dbChildren = parent.getGender().equals(Gender.MALE) ?
                provenanceService.findByFather(parent.getId()) : provenanceService.findByMother(parent.getId());
        final boolean[] foundChild = {false};
        dbChildren.forEach(provenance -> {
            Monarch dbChild = monarchService.finById(provenance.getId());
            if (dbChild != null && dbChild.getUrl().equals(child.getUrl())) foundChild[0] =true;
        });
        System.out.println("== Child URL: " + (foundChild[0] ? "Exists" : child.getUrl()));

        if (foundChild[0]) {
            return 0;
        }

        if (child.getStatus().equals(PersonStatus.EPHEMERAL)) {
            child.setStatus(PersonStatus.NEW_AI);
            monarchService.save(child);
        }

        updateProvenence(parent.getId(), child.getId(), parent.getGender());
        return saved;
    }

    private int proceedParentInfobox(Monarch child, Monarch parent, Gender gender) {
        if (parent==null || gender==null) return 0;
        Monarch dbParent = gender.equals(Gender.MALE) ? provenanceService.findFather(child) : provenanceService.findMother(child);
        if (dbParent!=null) return 0;

        int retval = 0;
        boolean mustSave = false;
        if (parent.getStatus().equals(PersonStatus.EPHEMERAL)) {
            parent.setStatus(PersonStatus.NEW_AI);
            mustSave = true;
        }
        if (parent.getId() == null) {
            retval++;
            mustSave = true;
        }
        if (mustSave) monarchService.save(parent);
        updateProvenence(parent.getId(), child.getId(), gender);
        return retval;
    }

}
