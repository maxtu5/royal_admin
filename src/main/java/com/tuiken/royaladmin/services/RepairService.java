package com.tuiken.royaladmin.services;

import com.tuiken.royaladmin.datalayer.ReignRepository;
import com.tuiken.royaladmin.datalayer.WikiCacheRecordRepository;
import com.tuiken.royaladmin.exceptions.WikiApiException;
import com.tuiken.royaladmin.model.cache.WikiCacheRecord;
import com.tuiken.royaladmin.model.entities.Monarch;
import com.tuiken.royaladmin.model.entities.Provenence;
import com.tuiken.royaladmin.model.entities.Reign;
import com.tuiken.royaladmin.model.entities.Throne;
import com.tuiken.royaladmin.model.enums.Gender;
import com.tuiken.royaladmin.utils.JsonUtils;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.util.Strings;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static java.lang.Thread.sleep;

@Service
@RequiredArgsConstructor
public class RepairService {

    private final MonarchService monarchService;
    private final ReignRepository reignRepository;
    private final AiResolverService aiResolverService;
    private final WikiService wikiService;
    private final WikiCacheRecordRepository wikiCacheRecordRepository;
    private final SmartIssueSearchService smartIssueSearchService;
    private final ProvenanceService provenanceService;
    private final LinkResolver linkResolver;
    private final ThroneService throneService;

    @Transactional
    public boolean reportGender() {
        List<Monarch> all = monarchService.loadAllMonarchs().stream()
                .filter(m -> m.getGender() == null)
                .collect(Collectors.toList());
        Map<String, Long> collect = all.stream()
                .filter(m -> m.getGender() == null)
                .map(m -> {
                    if (!Strings.isBlank(m.getName())) {
                        m.setGender(Gender.fromTitle(m.getName()));
                    }
                    if (m.getGender() == null && !m.getReignIds().isEmpty()) {
                        Reign reign = reignRepository.findById(m.getReignIds().get(0)).orElse(null);
                        m.setGender(reign != null && reign.getTitle() != null ? Gender.fromTitle(reign.getTitle()) : null);
                    }
                    if (m.getGender() == null) {
                        String aiResolved = aiResolverService.findGender(m.getName());
                        try {
                            m.setGender(Gender.valueOf(aiResolved));
                        } catch (IllegalArgumentException e) {
                            System.out.println("UNKNOWN???");
                        }
                        System.out.println(m.getName());
                        System.out.println(m.getGender() + "\n");
                    }
                    return m;
                })
                .map(monarchService::save)
                .collect(Collectors.groupingBy(t -> t.getGender() == null ? "NULL" : t.getGender().toString(), Collectors.counting()));
        for (Map.Entry<String, Long> es : collect.entrySet()) {
            System.out.println(es.getKey() + " " + es.getValue());
        }
        return false;
    }

    @Transactional
    public boolean reportMissingHouses() throws WikiApiException {
        List<Monarch> allPeople = monarchService.loadAllMonarchs();
        Set<String> allHouses = new HashSet<>();
        for (Monarch monarch : allPeople) {
            if (monarch.getHouse().isEmpty()) {
                JSONArray jsonArray = wikiService.read(monarch.getUrl());
                List<JSONObject> list = JsonUtils.arrayTolist(jsonArray);
                List<JSONObject> houseObjects = JsonUtils.drillForName(list, "House", "Dynasty");
                Set<String> houseStrings = JsonUtils.readFromLinks(houseObjects, "text").stream()
                        .map(s -> s.contains("House of") ? s.replace("House of", "").trim() : s)
                        .filter(s -> !s.equalsIgnoreCase("House"))
                        .collect(Collectors.toSet());
                allHouses.addAll(houseStrings);
            }
        }
        allHouses.forEach(s -> {
            System.out.println(s.toUpperCase() + "(\"" + s + "\'),");
        });
        return true;
    }

    @Transactional
    public boolean wrongParents() {
        Iterable<WikiCacheRecord> all = wikiCacheRecordRepository.findAll();
        final int[] i = new int[]{0};
        all.forEach(record -> {
            if (i[0] % 10 == 0) System.out.println(i[0]);
            Monarch monarch = monarchService.findByUrl(record.getUrl());
            if (monarch != null) {
                Monarch parent = monarch == null ? null : provenanceService.findMother(monarch);
                String parentDb = parent == null ? null : parent.getUrl();
                try {
                    JSONArray jsonArray = wikiService.read(record.getUrl());
                    List<JSONObject> infoboxes = JsonUtils.readInfoboxes(jsonArray);
                    List<JSONObject> mother = JsonUtils.drillForName(infoboxes, "Mother");
                    String parentUrl = JsonUtils.readFromLinks(mother, "url").stream()
                            .map(smartIssueSearchService::convertChildLink)
                            .filter(Objects::nonNull)
                            .findFirst().orElse(null);
//                    if (parentDb == null && parentUrl != null) {
//                        System.out.println(monarch.getUrl());
//                        System.out.println("null");
//                        System.out.println(parentUrl);
//                        System.out.println();
//                    }
                    if (parentDb != null && parentUrl != null && !parentDb.equals(parentUrl)) {
                        parentUrl = linkResolver.resolve(parentUrl);
                        if (!parentDb.equals(parentUrl)) {
                            if (i[0] % 10 != 0) System.out.println(i[0]);

                            System.out.println(monarch.getUrl());
                            System.out.println(parentDb);
                            System.out.println(parentUrl);
//                        System.out.println();
                        }
                    }

                } catch (WikiApiException e) {
                    throw new RuntimeException(e);
                }
                i[0]++;
            }
        });
        return true;
    }

    public boolean missingIdsProvenence() {
        List<Provenence> provenences = provenanceService.findAllProvenances().stream()
                .filter(p -> {
                    if (p.getMother() != null && monarchService.finById(p.getMother()) == null ||
                            p.getFather() != null && monarchService.finById(p.getFather()) == null ||
                            monarchService.finById(p.getId()) == null) return true;
                    return false;
                })
                .collect(Collectors.toList());
        for (Provenence p :
                provenences) {
            provenanceService.deleteProvenence(p);
        }
        return true;
    }

    public boolean reportReignCollisions() {
        List<Throne> thrones = throneService.loadAllThrones();
        for (Throne throne : thrones) {
            List<Reign> reigns = throne.getReigns();
            for (Reign reign : reigns) {
                Monarch monarch = monarchService.findByReignId(reign.getId());
                if (monarch == null) {
                    System.out.println(throne.getName() + " " + reign.getStart() + " " + reign.getId());
                }
            }
        }
        return true;
    }

    public boolean unresolvedUrls() {
        List<Monarch> monarchs = monarchService.loadAllMonarchs();
        int i=0;
        for (Monarch monarch : monarchs) {
            i++;
            if (i%10==7) {
                try {
                    sleep(1000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            String resolvedUrl = linkResolver.resolve(monarch.getUrl());
            if (!resolvedUrl.equals(monarch.getUrl())) {
                System.out.println(monarch.getUrl());
                System.out.println(resolvedUrl);
                Monarch rightMonarch = monarchService.findByUrl(resolvedUrl);
                if (rightMonarch == null) {
                    monarch.setUrl(resolvedUrl);
                    monarchService.save(monarch);
                    System.out.println("fixed");
                } else {
                    Monarch father = provenanceService.findFather(monarch);
                    Monarch rightFather = provenanceService.findFather(rightMonarch);
                    Monarch mother = provenanceService.findMother(monarch);
                    Monarch rightMother = provenanceService.findMother(rightMonarch);

                    if (father!=null && rightFather!=null && !father.getId().equals(rightFather.getId()) ||
                            mother!=null && rightMother!=null && !mother.getId().equals(rightMother.getId())) {
                        System.out.println("different parents");
                    } else {
                        rightMonarch.getReignIds().addAll(monarch.getReignIds());
                        monarchService.save(rightMonarch);
                        monarch.setReignIds(new ArrayList<>());
                        monarchService.save(monarch);
                        Set<Monarch> children = provenanceService.findChildren(monarch);
                        for (Monarch child: children) {
                            provenanceService.setParent(child, rightMonarch);
                        }
                        if (rightFather==null && father!=null) {
                            provenanceService.setParent(rightMonarch, father);
                        }
                        if (rightMother==null && mother!=null) {
                            provenanceService.setParent(rightMonarch, mother);
                        }
                        monarchService.deleteByUrl(monarch.getUrl());
                    }
                    System.out.println("merged");
                }
            }
        }
        return false;
    }
}
