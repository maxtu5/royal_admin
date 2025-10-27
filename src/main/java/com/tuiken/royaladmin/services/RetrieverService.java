package com.tuiken.royaladmin.services;

import com.tuiken.royaladmin.builders.PersonBuilder;
import com.tuiken.royaladmin.exceptions.WikiApiException;
import com.tuiken.royaladmin.model.entities.Monarch;
import com.tuiken.royaladmin.model.entities.Provenence;
import com.tuiken.royaladmin.model.entities.Reign;
import com.tuiken.royaladmin.model.enums.Country;
import com.tuiken.royaladmin.model.enums.Gender;
import com.tuiken.royaladmin.model.enums.House;
import com.tuiken.royaladmin.model.enums.PersonStatus;
import com.tuiken.royaladmin.model.workflows.LoadFamilyConfiguration;
import com.tuiken.royaladmin.utils.DatesParser;
import com.tuiken.royaladmin.utils.JsonUtils;
import lombok.AllArgsConstructor;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class RetrieverService {

    private final WikiService wikiService;
    private final ProvenanceService provenanceService;
    private final LinkResolver resolver;
    private final SmartIssueSearchService smartIssueSearchService;
    private final PersonBuilder personBuilder;
    private final MonarchService monarchService;
    private final WikiDirectService wikiDirectService;

    public LoadFamilyConfiguration createLoadFamilyConfiguration(Monarch root) {

        LoadFamilyConfiguration configuration = LoadFamilyConfiguration.builder()
                .rootId(root.getId()).rootUrl(root.getUrl()).rootGender(root.getGender()).build();

        JSONArray jsonArray = new JSONArray();

        jsonArray = wikiService.read(root.getUrl());

        List<JSONObject> infoboxes = JsonUtils.readInfoboxes(jsonArray);
        Map<String, List<String>> allLinks = wikiDirectService.allLinks(root.getUrl());

        // parents
        Provenence provenence = provenanceService.findById(root.getId());
        if (provenence != null) {
            if (provenence.getFather() != null) configuration.setFatherId(provenence.getFather());
            if (provenence.getMother() != null) configuration.setMotherId(provenence.getMother());
        }

        String fatherUrl = extractParent(infoboxes, allLinks, "Father");
        if (fatherUrl != null) {
            Monarch monarch = personBuilder.findOrCreate(fatherUrl, Gender.MALE);
            configuration.setFather(monarch);
        }

        String motherUrl = extractParent(infoboxes, allLinks, "Mother");
        if (motherUrl != null) {
            Monarch monarch = personBuilder.findOrCreate(motherUrl, Gender.FEMALE);
            configuration.setMother(monarch);
        }

        if (motherUrl == null && fatherUrl == null) {
            List<String> parents = extractParents(infoboxes, allLinks);
            System.out.printf("Found %s parents%n", parents.size());
            if (parents.size() == 2) {
                Monarch father = personBuilder.findOrCreate(parents.get(0), Gender.FEMALE);
                configuration.setFather(father);
                Monarch mother = personBuilder.findOrCreate(parents.get(1), Gender.MALE);
                configuration.setMother(mother);
            }
            if (parents.size() == 1) {
                Monarch parent = personBuilder.findOrCreate(parents.get(0), null);
                if (parent != null) {
                    if (Gender.MALE.equals(parent.getGender())) configuration.setFather(parent);
                    if (Gender.FEMALE.equals(parent.getGender())) configuration.setMother(parent);
                }
            }
        }

        // children
        List<Provenence> issueP = root.getGender() == Gender.MALE ?
                provenanceService.findByFather(root.getId()) :
                provenanceService.findByMother(root.getId());
        configuration.setIssueIds(issueP.stream()
                .map(Provenence::getId)
                .collect(Collectors.toList()));
        List<Monarch> children = extractIssue(infoboxes, root, allLinks);
        configuration.setIssue(children);
        return configuration;
    }

    private String extractParent(List<JSONObject> infoboxes, Map<String, List<String>> allLinks, String key) {
        List<JSONObject> parent = JsonUtils.drillForName(infoboxes, key);
        String parentUrl = JsonUtils.readFromLinks(parent, "url").stream()
                .map(smartIssueSearchService::convertChildLink)
                .filter(Objects::nonNull)
                .findFirst().orElse(null);
        if (parentUrl == null) {
            String parentName = JsonUtils.readFromLinks(parent, "name").stream()
                    .findFirst().orElse(null);
            parentUrl = smartIssueSearchService.findInAllLinks(parentName, allLinks);
        }
        return parentUrl == null ? parentUrl : resolver.resolve(parentUrl);
    }

    public List<String> extractParents(List<JSONObject> infoboxes, Map<String, List<String>> allLinks) {
        List<JSONObject> parent = JsonUtils.drillForName(infoboxes, "Parents", "Parent(s)");
        if (parent.isEmpty()) return new ArrayList<>();
        List<String> parentUrls = JsonUtils.readFromLinks(parent, "url").stream()
                .map(smartIssueSearchService::convertChildLink)
                .filter(Objects::nonNull)
                .map(resolver::resolve)
                .toList();
        if (!parentUrls.isEmpty()) return parentUrls;

        List<String> parentNames = JsonUtils.readFromValues(parent);
        return parentNames.stream()
                .map(s -> s.replace("(mother)", "").replace("(father)", "").trim())
                .map(n -> smartIssueSearchService.findInAllLinks(n, allLinks))
                .filter(Objects::nonNull)
                .map(resolver::resolve)
                .toList();
    }

    private List<Monarch> extractIssue(List<JSONObject> infoboxes, Monarch root, Map<String, List<String>> allLinks) {
        List<JSONObject> issue = JsonUtils.drillForName(infoboxes, "Issue detail", "Issue", "Issue more...", "Issue More", "Illegitimate children Detail", "Issue among others...", "Illegitimate children more...", "Children");

        List<String> issueUrls = JsonUtils.readFromLinks(issue, "url").stream()
                .map(smartIssueSearchService::convertChildLink)
                .filter(Objects::nonNull).toList();
        if (!issueUrls.isEmpty()) System.out.println("Wow found simply: " + issueUrls.size());

        List<Monarch> retval = issueUrls.isEmpty() ? smartIssueSearchService.findInAllLinksParentCheck(issue, root, allLinks) :
                issueUrls.stream()
                        .map(url -> personBuilder.findOrCreate(url, null))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());

        return retval.stream()
                .collect(Collectors.toMap(
                        Monarch::getUrl,       // key: url
                        Function.identity(),   // value: Monarch object
                        (existing, replacement) -> existing, // keep first occurrence
                        LinkedHashMap::new     // preserve order
                ))
                .values()
                .stream()
                .collect(Collectors.toList());
    }

    public List<Monarch> saveLoaded(LoadFamilyConfiguration configuration) {
        System.out.println("=== SAVING ===");
        int savedRels = 0;
        int savedMonarchs = 0;
        List<Monarch> newMonarchs = new ArrayList<>();
        if (configuration.getRootId() == null || configuration.getRootUrl() == null ||
                !configuration.getIssue().isEmpty() && configuration.getRootGender() == null)
            return newMonarchs;

        // parents
        Provenence provenence = new Provenence(configuration.getRootId());
        if (configuration.getFatherId() == null && configuration.getFather() != null) {
            savedMonarchs += configuration.getFather().getId() == null ? 1 : 0;
            Monarch father = configuration.getFather().getId() == null ?
                    monarchService.save(configuration.getFather()) :
                    configuration.getFather();
            provenence.setFather(father.getId());
            if (!father.getStatus().equals(PersonStatus.RESOLVED)) newMonarchs.add(father);
            if (father.getStatus().equals(PersonStatus.EPHEMERAL)) {
                father.setStatus(PersonStatus.NEW_AI);
                monarchService.save(father);
            }
        }
        if (configuration.getMotherId() == null && configuration.getMother() != null) {
            savedMonarchs += configuration.getMother().getId() == null ? 1 : 0;
            Monarch mother = configuration.getMother().getId() == null ?
                    monarchService.save(configuration.getMother()) :
                    configuration.getMother();
            if (!mother.getStatus().equals(PersonStatus.RESOLVED)) newMonarchs.add(mother);
            provenence.setMother(mother.getId());
            if (mother.getStatus().equals(PersonStatus.EPHEMERAL)) {
                mother.setStatus(PersonStatus.NEW_AI);
                monarchService.save(mother);
            }
        }
        if (provenence.getFather() != null || provenence.getMother() != null) {
            provenanceService.saveOrMerge(provenence);
            savedRels++;
        }

        // children
        if (configuration.getIssue().isEmpty() ||
                configuration.getIssueIds() != null && configuration.getIssueIds().size() >= configuration.getIssue().size()) {
            System.out.println("Monarchs:  " + savedMonarchs);
            System.out.println("Relations: " + savedRels);
            return newMonarchs;
        }

        savedMonarchs += (int) configuration.getIssue().stream().filter(m -> m.getId() == null).count();
        savedRels += configuration.getIssue().stream().mapToInt(child -> {
            Monarch savedChild = child.getId() == null ? monarchService.save(child) : child;
            if (savedChild.getStatus().equals(PersonStatus.EPHEMERAL)) {
                savedChild.setStatus(PersonStatus.NEW_AI);
                monarchService.save(savedChild);
            }
            if (!configuration.getIssueIds().contains(savedChild.getId())) {
                Provenence provenenceChild = configuration.getRootGender() == Gender.MALE ?
                        Provenence.builder().id(savedChild.getId()).father(configuration.getRootId()).build() :
                        Provenence.builder().id(savedChild.getId()).mother(configuration.getRootId()).build();
                provenanceService.save(provenenceChild);
                if (!savedChild.getStatus().equals(PersonStatus.RESOLVED)) newMonarchs.add(savedChild);
                return 1;
            } else return 0;
        }).sum();
        System.out.println("Monarchs:  " + savedMonarchs);
        System.out.println("Relations: " + savedRels);
        return newMonarchs;
    }

// +++++++ +++++++++++

    public static String retrieveProperty(JSONArray jsonArray, String propertyName) {
        Set<String> list = new HashSet<>();
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject object = jsonArray.getJSONObject(i);
            if (object.has(propertyName)) list.add((String) object.get(propertyName));
        }
        return list.isEmpty() ? null : list.stream().findFirst().get();
    }

    public static Instant retrieveOneDate(JSONArray jsonArray, String key) {
        List<JSONObject> list = JsonUtils.arrayTolist(jsonArray);
        List<JSONObject> dates = JsonUtils.drillForName(list, key);

        return dates.stream()
                .map(JsonUtils::readValue)
                .filter(Objects::nonNull)
                .map(DatesParser::findDate)
                .filter(Objects::nonNull)
                .findFirst().orElse(null);
    }

    public static Set<House> retrieveHouses(JSONArray jsonArray) {
        Set<String> captions = Set.of("House of", "Noble family", "Family", "agnatic", "Dynasty", "family", "Noble");
        List<JSONObject> list = JsonUtils.arrayTolist(jsonArray);
        List<JSONObject> houseObjects = JsonUtils.drillForName(list, "House", "Dynasty", "Noble family", "Family");
        Set<String> houseStrings = JsonUtils.readFromLinks(houseObjects, "text").stream()
                .map(s -> {
                    for (String sample: captions) {
                        if (s.contains(sample)) return s.replaceAll(sample, "");
                    }
                    return s;
                })
                .map(s->s.trim().replaceAll("\\s{2,}", " "))
                .filter(s->!s.isEmpty())
                .collect(Collectors.toSet());
        houseStrings.addAll(houseObjects.stream()
                .map(JsonUtils::readValue)
                .filter(Objects::nonNull)
                .map(s -> {
                    for (String sample: captions) {
                        if (s.contains(sample)) return s.replaceAll(sample, "");
                    }
                    return s;
                })
                .map(s->s.trim().replaceAll("\\s{2,}", " "))
                .filter(s->!s.isEmpty())
                .collect(Collectors.toSet()));
        Set<House> houses = new HashSet<>();
        for (String s : houseStrings) {
            House house = House.HouseFromBeginningOfString(s);
            if (house != null) houses.add(house);
        }
        return houses;
    }

    public static List<Reign> retrieveReigns(JSONArray jsonArray, Country country) {

        List<JSONObject> list = JsonUtils.extendParts(JsonUtils.readInfoboxes(jsonArray));
        List<Reign> retval = new ArrayList<>();

        List<JSONObject> reign = JsonUtils.findByNameAndDrill(list, country, "Reign");
        if (reign.isEmpty()) {
            reign = JsonUtils.findByNameAndDrill(list, country, "Margrave");
            reign.addAll(JsonUtils.findByNameAndDrill(list, country, "1st reign"));
            reign.addAll(JsonUtils.findByNameAndDrill(list, country, "2nd reign"));
            reign.addAll(JsonUtils.findByNameAndDrill(list, country, "3rd reign"));
            reign.addAll(JsonUtils.findByNameAndDrill(list, country, "First reign"));
            reign.addAll(JsonUtils.findByNameAndDrill(list, country, "Second reign"));
        }
        for (int i = 0; i < reign.size(); i++) {
            String reignline = JsonUtils.readValue(reign.get(i));
            Instant[] reignDates = DatesParser.findTwoDates(reignline);
            Reign r = new Reign();
            r.setCountry(country);
            r.setStart(reignDates[0]);
            r.setEnd(reignDates[1]);
            List<JSONObject> corona = JsonUtils.drillForName(list, "Coronation");
            if (corona.size() == 1) {
                String coronationLine = JsonUtils.readValue(corona.get(0));
                r.setCoronation(DatesParser.findDate(coronationLine));
            }
            r.setTitle(retrieveTitle(jsonArray, country));
            retval.add(r);
        }
        return retval;
    }

    public static String retrieveTitle(JSONArray jsonArray, Country country) {
        List<JSONObject> list = JsonUtils.extendParts(JsonUtils.readInfoboxes(jsonArray));
        list = list.stream().filter(o -> o.has("name") && country.belongs((String) o.get("name"))).collect(Collectors.toList());
        if (list.size() > 1) {
            list = list.stream()
                    .filter(o -> {
                        List<JSONObject> lissi = new ArrayList<>();
                        lissi.add(o);
                        return JsonUtils.drillForName(lissi, "Reign").size() > 0 ||
                                JsonUtils.drillForName(lissi, "1st reign").size() > 0;
                    })
                    .collect(Collectors.toList());
        }

        System.out.println("==found Title " + list.size());
        if (list.size() > 0) {
            return (String) list.get(0).get("name");
        }
        return null;
    }

    public static String[] retrieveImage(JSONArray jsonArray) {
        String[] retval = {"", ""};
        List<JSONObject> inf = JsonUtils.readInfoboxes(jsonArray);
        JSONObject image = JsonUtils.findImage(inf);
        if (image.has("content_url")) {
            retval[0] = image.getString("content_url");
            if (image.has("caption")) retval[1] = image.getString("caption");
        }
        return retval;
    }

    public String retrievePredecessor(JSONArray jsonArray, Country country) {
        List<JSONObject> list = JsonUtils.extendParts(JsonUtils.readInfoboxes(jsonArray));
        list = list.stream().filter(o -> o.has("name") && country.belongs((String) o.get("name"))).collect(Collectors.toList());
        if (list.size() > 1) {
            list = list.stream()
                    .filter(o -> {
                        List<JSONObject> lissi = new ArrayList<>();
                        lissi.add(o);
                        return JsonUtils.drillForName(lissi, "Reign").size() > 0;
                    })
                    .collect(Collectors.toList());
        }

        List<JSONObject> predecessor = JsonUtils.drillForName(list, "Predecessor");

        if (predecessor.size() > 0) {
            return JsonUtils.readFromLinks(predecessor, "url").get(0);
        }
        return null;
    }

}
