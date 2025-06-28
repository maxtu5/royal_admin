package com.tuiken.royaladmin.services;

import com.tuiken.royaladmin.builders.PersonBuilder;
import com.tuiken.royaladmin.datalayer.ReignRepository;
import com.tuiken.royaladmin.datalayer.WikiCacheRecordRepository;
import com.tuiken.royaladmin.exceptions.WikiApiException;
import com.tuiken.royaladmin.model.cache.WikiCacheRecord;
import com.tuiken.royaladmin.model.entities.Monarch;
import com.tuiken.royaladmin.model.entities.Provenence;
import com.tuiken.royaladmin.model.entities.Reign;
import com.tuiken.royaladmin.model.entities.Throne;
import com.tuiken.royaladmin.model.enums.Gender;
import com.tuiken.royaladmin.model.enums.PersonStatus;
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
    private final RetrieverService retrieverService;
    private final PersonBuilder personBuilder;
    private final WikiDirectService wikiDirectService;

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
    public boolean reportMissingHouses() {
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
        List<WikiCacheRecord> all = wikiCacheRecordRepository.findAll();
        int[] counts = {0, 0, 0, 0, 0, 0};
        all.forEach(record -> {
            if (counts[0] % 100 == 0) System.out.println(counts[0]);
            Monarch monarch = monarchService.findByUrl(record.getUrl());
            if (monarch == null || monarch.getStatus().equals(PersonStatus.NEW_URL)) return;
            counts[0]++;

            JSONArray jsonArray = new JSONArray(record.getBody());
            List<JSONObject> infoboxes = JsonUtils.readInfoboxes(jsonArray);

            List<String> parents = retrieverService.extractParents(infoboxes, new HashMap<>());
            if (parents.size()==1) {
                counts[1]++;
                Monarch unknownParent = personBuilder.findOrCreate(parents.get(0), null);
                if (unknownParent == null) return;
                Monarch father = provenanceService.findFather(monarch);
                Monarch mother = provenanceService.findMother(monarch);
                if (father!=null && unknownParent.getUrl().equals(father.getUrl()) || mother!=null && unknownParent.getUrl().equals(mother.getUrl())) return;
                System.out.println(monarch.getName());
                if (Gender.MALE.equals(unknownParent.getGender()) && father==null) {
                    if (unknownParent.getId()==null) monarchService.save(unknownParent);
                    provenanceService.setParent(monarch, unknownParent);
                }
//                if (father == null) {
//                    Monarch mum = personBuilder.findOrCreate(parents.get(0), Gender.MALE);
//                    if (mum != null) {

//                        mum = monarchService.save(mum);
//                        provenanceService.setParent(monarch, mum);
//                    }
//                }
//                System.out.println((father == null ? "" : "*") + (father != null && father.getUrl().equals(parents.get(0)) ? "+" : "") + parents.get(0));
//                System.out.println((mother == null ? "" : "*") + (mother != null && mother.getUrl().equals(parents.get(1)) ? "+" : "") + parents.get(1));
            }

//            parentNames.forEach(System.out::println);
//            parentUrls.forEach(System.out::println);
//
//            if (!parentNames.isEmpty()) counts[2]++;
//            if (!parentUrls.isEmpty()) counts[3]++;
//            if (!parentUrls.isEmpty() && !parentNames.isEmpty()) counts[4]++;
//            if (parentUrls.isEmpty() && parentNames.isEmpty()) counts[5]++;

//            Monarch mother = provenanceService.findMother(monarch);
//            if (mother == null) return;
//            counts[1]++;
//            String parentDb = mother.getUrl();
//
//            JSONArray jsonArray = wikiService.read(record.getUrl());
//            List<JSONObject> infoboxes = JsonUtils.readInfoboxes(jsonArray);
//            List<JSONObject> motherO = JsonUtils.drillForName(infoboxes, "Mother");
//            String parentUrl = JsonUtils.readFromLinks(motherO, "url").stream()
//                    .map(smartIssueSearchService::convertChildLink)
//                    .filter(Objects::nonNull)
//                    .findFirst().orElse(null);
//            if (parentUrl != null) counts[2]++;

//            if (parentDb != null && parentUrl != null && !parentDb.equals(parentUrl)) {
//                parentUrl = linkResolver.resolve(parentUrl);
//                if (!parentDb.equals(parentUrl)) {
//                    if (i[0] % 10 != 0) System.out.println(i[0]);
//
//                    System.out.println(monarch.getUrl());
//                    System.out.println(parentDb);
//                    System.out.println(parentUrl);
////                        System.out.println();
//                }
//            }
        });
        System.out.println("With saved cache: " + all.size());
        System.out.println("In database: " + counts[0]);
        System.out.println("With Parents in database: " + counts[1]);
//        System.out.println("With names: "+counts[2]);
//        System.out.println("With urls: "+counts[3]);
//        System.out.println("With none: "+counts[5]);

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
        int i = 0;
        for (Monarch monarch : monarchs) {
            i++;
            if (i % 10 == 7) {
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

                    if (father != null && rightFather != null && !father.getId().equals(rightFather.getId()) ||
                            mother != null && rightMother != null && !mother.getId().equals(rightMother.getId())) {
                        System.out.println("different parents");
                    } else {
                        rightMonarch.getReignIds().addAll(monarch.getReignIds());
                        monarchService.save(rightMonarch);
                        monarch.setReignIds(new ArrayList<>());
                        monarchService.save(monarch);
                        Set<Monarch> children = provenanceService.findChildren(monarch);
                        for (Monarch child : children) {
                            provenanceService.setParent(child, rightMonarch);
                        }
                        if (rightFather == null && father != null) {
                            provenanceService.setParent(rightMonarch, father);
                        }
                        if (rightMother == null && mother != null) {
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

    public boolean listMonarchsNotInCache() throws WikiApiException {
        List<Monarch> monarches = monarchService.loadAllMonarchs();
        monarches = monarches.stream()
                .filter(m -> Strings.isBlank(m.getImageUrl()))
                .filter(m -> !wikiCacheRecordRepository.existsByUrl(m.getUrl()))
                .toList();
        System.out.println(monarches.size());
        monarches.stream()
                .limit(10)
                .forEach(m -> {
                    JSONArray monarchJson = wikiService.read(m.getUrl());
                    List<JSONObject> inf = JsonUtils.readInfoboxes(monarchJson);
                    for (JSONObject infobox : inf) {
                        JSONObject image = JsonUtils.findImage(inf);
                        if (image.has("content_url")) {
                            m.setImageUrl(image.getString("content_url"));
                            if (image.has("caption")) m.setImageCaption(image.getString("caption"));
                            monarchService.save(m);
                            System.out.println(m.getUrl());
                        }

                    }
                });
//        monarches.forEach(m-> System.out.println(m.getUrl()));
//        System.out.println(monarches.size());

//        Iterable<WikiCacheRecord> wikiCacheRecords = wikiCacheRecordRepository.findAll();
//        for (WikiCacheRecord record: wikiCacheRecords) {
//            Monarch monarch = monarchService.findByUrl(record.getUrl());
//            if (monarch!=null && Strings.isBlank(monarch.getImageUrl())) {
//                JSONArray monarchJson = new JSONArray(record.getBody());
//                List<JSONObject> inf = JsonUtils.readInfoboxes(monarchJson);
//                for (JSONObject infobox: inf) {
//                    JSONObject image = JsonUtils.findImage(inf);
//                    if (image.has("content_url")) {
//                        monarch.setImageUrl(image.getString("content_url"));
//                        if (image.has("caption")) monarch.setImageCaption(image.getString("caption"));
//                        monarchService.save(monarch);
//                    }
//                }
//            }
//        }
        return false;
    }
}
