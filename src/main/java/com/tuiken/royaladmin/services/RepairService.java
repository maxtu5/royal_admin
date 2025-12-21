package com.tuiken.royaladmin.services;

import com.tuiken.royaladmin.builders.PersonBuilder;
import com.tuiken.royaladmin.datalayer.MonarchRepository;
import com.tuiken.royaladmin.datalayer.ReignRepository;
import com.tuiken.royaladmin.datalayer.WikiCacheRecordRepository;
import com.tuiken.royaladmin.exceptions.WikiApiException;
import com.tuiken.royaladmin.model.entities.Monarch;
import com.tuiken.royaladmin.model.entities.Reign;
import com.tuiken.royaladmin.model.entities.Throne;
import com.tuiken.royaladmin.model.enums.Gender;
import com.tuiken.royaladmin.model.enums.House;
import com.tuiken.royaladmin.model.enums.PersonStatus;
import com.tuiken.royaladmin.services.ai.AiService;
import com.tuiken.royaladmin.services.ai.AiServiceOpenAi;
import com.tuiken.royaladmin.services.wiki.LinkResolver;
import com.tuiken.royaladmin.services.wiki.WikiCacheService;
import com.tuiken.royaladmin.services.wiki.WikiService;
import com.tuiken.royaladmin.utils.JsonUtils;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.util.Strings;
import org.json.JSONArray;
import org.json.JSONException;
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
    private final AiServiceOpenAi aiResolverService;
    private final WikiService wikiService;
    private final WikiCacheRecordRepository wikiCacheRecordRepository;
    private final SmartIssueSearchService smartIssueSearchService;
    private final ProvenanceService provenanceService;
    private final LinkResolver linkResolver;
    private final ThroneService throneService;
    private final RetrieverService retrieverService;
    private final PersonBuilder personBuilder;
    private final MonarchRepository monarchRepository;
    private final ThroneLoaderService throneLoaderService;
    private final LinkResolver resolver;
    private final WikiCacheService wikiCacheService;
    private final AiService aiService;

    public boolean provenenceMissingIds() {
        provenanceService.findAllProvenances()
                .forEach(provenence -> {
                    if (monarchService.finById(provenence.getId()) == null) {
                        System.out.println("Deleting");
                        provenanceService.deleteProvenence(provenence);
                    }
                    else {
                        if (provenence.getMother() != null && monarchService.finById(provenence.getMother()) == null) {
                            provenence.setMother(null);
                            provenanceService.save(provenence);
                        }
                        if (provenence.getFather() != null && monarchService.finById(provenence.getFather()) == null) {
                            provenence.setFather(null);
                            provenanceService.save(provenence);
                        }
                        if (provenence.getFather() == null && provenence.getMother() == null)
                            provenanceService.deleteProvenence(provenence);
                    }
                });
        return true;
    }

    public boolean provenenceGenderMismatch() {
        provenanceService.findAllProvenances()
                .forEach(provenence -> {
                    if (provenence.getMother() != null && monarchService.existsByUrl(String.valueOf(provenence.getMother()))) {
                        if (!monarchService.finById(provenence.getMother()).getGender().equals(Gender.FEMALE))
                            System.out.println("oh mum " + monarchService.finById(provenence.getMother()).getUrl());
                    }

                    if (provenence.getFather() != null && monarchService.existsByUrl(String.valueOf(provenence.getFather()))) {
                        if (!monarchService.finById(provenence.getFather()).getGender().equals(Gender.MALE))
                            System.out.println("oh dad " + monarchService.finById(provenence.getFather()).getUrl());
                    }
                });
        return true;
    };

    public boolean reportProcess() {
        monarchService.reportProcess();
        return true;
    }

    @Transactional
    public boolean reportMissingHouses() {
        List<Monarch> allPeople = monarchService.loadAllMonarchs();
        Set<String> allHouses = new HashSet<>();
        System.out.println("Total: " + allPeople.size() + "\nWith house: " +
                allPeople.stream().filter(m -> !m.getHouse().isEmpty()).count() + "\nWith 2+: " +
                allPeople.stream().filter(m -> m.getHouse().size() > 1).peek(m -> System.out.println(m.getUrl())).count());
        return true;
    }

    @Transactional
    public boolean findUnknownHouses() {
        List<Monarch> allPeople = monarchService.loadAllMonarchs();
        Set<String> allHouses = new HashSet<>();
        for (Monarch monarch : allPeople) {
            if (monarch.getHouse().isEmpty()) {
                JSONArray jsonArray = wikiService.readJson(monarch.getUrl());
                List<JSONObject> list = JsonUtils.arrayTolist(jsonArray);
                List<JSONObject> houseObjects = JsonUtils.drillForName(list, "House", "Dynasty", "Noble Family");
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

    public boolean rereadHousesFromCache() {
        monarchService.loadAllMonarchs().stream()
                .filter(m -> m.getStatus().equals(PersonStatus.RESOLVED) &&
                        m.getHouse().isEmpty() &&
                        (m.getProcess() == null || !m.getProcess().equals("Done")))
                .forEach(this::updateHouses);
        return true;
    }

    @Transactional
    public void updateHouses(Monarch monarch) {
        System.out.println(monarch.getUrl());
        JSONArray read = wikiService.readJson(monarch.getUrl());
        Set<House> houses = RetrieverService.retrieveHouses(read);
        houses.forEach(System.out::print);
        System.out.println();
        monarch.getHouse().forEach(System.out::print);
        System.out.println();
        houses.forEach(monarch.getHouse()::add);
        monarch.setProcess("Done");
        monarchService.save(monarch);
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
                    JSONArray monarchJson = wikiService.readJson(m.getUrl());
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
        return false;
    }

    public List<String> extractWikiText(String url) {
        String jsonString = wikiCacheRecordRepository.findByUrl(url)
                .orElseThrow(() -> new RuntimeException("No record found for URL: " + url))
                .getBody();

        try {
            JSONArray rootArray = new JSONArray(jsonString);
            return JsonUtils.extractWikiText(rootArray);
        } catch (JSONException e) {
            throw new RuntimeException("Failed to parse JSON", e);
        }
    }


}
