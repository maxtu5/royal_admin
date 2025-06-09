package com.tuiken.royaladmin.services;

import com.tuiken.royaladmin.builders.PersonBuilder;
import com.tuiken.royaladmin.datalayer.ProvenenceRepository;
import com.tuiken.royaladmin.exceptions.WikiApiException;
import com.tuiken.royaladmin.model.entities.Monarch;
import com.tuiken.royaladmin.model.entities.Provenence;
import com.tuiken.royaladmin.model.entities.Reign;
import com.tuiken.royaladmin.model.enums.Country;
import com.tuiken.royaladmin.model.enums.Gender;
import com.tuiken.royaladmin.model.enums.House;
import com.tuiken.royaladmin.model.workflows.LoadFamilyConfiguration;
import com.tuiken.royaladmin.model.workflows.SaveFamilyConfiguration;
import com.tuiken.royaladmin.utils.DatesParser;
import com.tuiken.royaladmin.utils.JsonUtils;
import lombok.AllArgsConstructor;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class RetrieverService {

    private final WikiService wikiService;
    private final ProvenenceRepository provenenceRepository;
    private final LinkResolver resolver;
    private final SmartIssueSearchService smartIssueSearchService;
    private final PersonBuilder personBuilder;
    private final MonarchService monarchService;

    public static String retrieveName(JSONArray jsonArray) {
        Set<String> list = new HashSet<>();
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject object = jsonArray.getJSONObject(i);
            if (object.has("name")) list.add((String) object.get("name"));
        }
        return list.size() > 0 ? list.stream().findFirst().get() : null;
    }

    public static String retrieveProperty(JSONArray jsonArray, String propertyName) {
        Set<String> list = new HashSet<>();
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject object = jsonArray.getJSONObject(i);
            if (object.has(propertyName)) list.add((String) object.get(propertyName));
        }
        return list.size() > 0 ? list.stream().findFirst().get() : null;
    }

    public static Instant retrieveOneDate(JSONArray jsonArray, String key) {
        List<JSONObject> list = JsonUtils.arrayTolist(jsonArray);
        List<JSONObject> dates = JsonUtils.drillForName(list, key);

        return dates.stream()
                .map(o -> JsonUtils.readValue(o))
                .filter(Objects::nonNull)
                .map(s -> DatesParser.findDate(s))
                .filter(Objects::nonNull)
                .findFirst().orElse(null);
    }

    public static Set<House> retrieveHouses(JSONArray jsonArray) {
        List<JSONObject> list = JsonUtils.arrayTolist(jsonArray);
        List<JSONObject> houseObjects = JsonUtils.drillForName(list, "House", "Dynasty", "Noble family", "Family");
        Set<String> houseStrings = JsonUtils.readFromLinks(houseObjects, "text").stream()
                .map(s -> s.contains("House of") ? s.replace("House of", "").trim() : s)
                .filter(s -> !s.equalsIgnoreCase("House"))
                .collect(Collectors.toSet());

        Set<House> houses = new HashSet<>();
        for (String s : houseStrings) {
            House house = House.HouseFromBeginningOfString(s);
            if (house != null) houses.add(house);
        }
        return houses;
    }

    public static Reign retrieveReign(JSONArray jsonArray, Country country) {
        List<JSONObject> list = JsonUtils.arrayTolist(jsonArray);
        List<JSONObject> reign = JsonUtils.drillForName(list, "Reign");

        Instant[] datesReign = reign.stream()
                .map(o -> JsonUtils.readValue(o))
                .filter(Objects::nonNull)
                .map(s -> DatesParser.findTwoDates(s))
                .filter(Objects::nonNull)
                .findFirst().orElse(null);

        if (datesReign.length == 2) {
            Reign retval = new Reign();
            retval.setStart(datesReign[0]);
            retval.setEnd(datesReign[1]);
            List<JSONObject> coronation = JsonUtils.drillForName(list, "Coronation");

            Instant coronationDate = reign.stream()
                    .map(o -> JsonUtils.readValue(o))
                    .filter(Objects::nonNull)
                    .map(s -> DatesParser.findDate(s))
                    .filter(Objects::nonNull)
                    .findFirst().orElse(null);
            retval.setCoronation(coronationDate);
            retval.setCountry(country);
            return retval;
        }
        return null;
    }

    //    reign retrieval
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
        String[] retval = {"",""};
        List<JSONObject> inf = JsonUtils.readInfoboxes(jsonArray);
            JSONObject image = JsonUtils.findImage(inf);
            if (image.has("content_url")) {
                retval[0]=image.getString("content_url");
                if (image.has("caption")) retval[1]=image.getString("caption");
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

    public LoadFamilyConfiguration createLoadFamilyConfiguration(Monarch root, Country rootCountry) {

        LoadFamilyConfiguration configuration = LoadFamilyConfiguration.builder()
                .rootId(root.getId())
                .rootUrl(root.getUrl())
                .rootGender(root.getGender())
                .build();

        JSONArray jsonArray = new JSONArray();
        try {
            jsonArray = wikiService.read(root.getUrl());
        }
        catch (WikiApiException e) {
            System.out.println("Wiki API error");
        }
        List<JSONObject> infoboxes = JsonUtils.readInfoboxes(jsonArray);

        // parents
        Provenence provenence = provenenceRepository.findById(root.getId()).orElse(null);
        if (provenence != null) {
            if (provenence.getFather() != null) configuration.setFatherId(provenence.getFather());
            if (provenence.getMother() != null) configuration.setMotherId(provenence.getMother());
        }

        List<JSONObject> father = JsonUtils.drillForName(infoboxes, "Father");
        String fatherUrl = JsonUtils.readFromLinks(father, "url").stream()
                .map(smartIssueSearchService::convertChildLink)
                .filter(Objects::nonNull)
                .findFirst().orElse(null);

        if (fatherUrl != null) {
            fatherUrl = resolver.resolve(fatherUrl);
            configuration.setFatherUrl(fatherUrl);
        }

        List<JSONObject> mother = JsonUtils.drillForName(infoboxes, "Mother");
        String motherUrl = JsonUtils.readFromLinks(mother, "url").stream()
                .map(smartIssueSearchService::convertChildLink)
                .filter(Objects::nonNull)
                .findFirst().orElse(null);
        if (motherUrl != null) {
            motherUrl = resolver.resolve(motherUrl);
            configuration.setMotherUrl(motherUrl);
        }
        // children
        List<Provenence> issueP = root.getGender() == Gender.MALE ?
                provenenceRepository.findByFather(root.getId()) :
                provenenceRepository.findByMother(root.getId());
        configuration.setIssueIds(issueP.stream()
                .map(p -> p.getId())
                .collect(Collectors.toList()));

        List<Monarch> childrenSaved = extractIssueFromWikiValidatedWithCreate(jsonArray, root, rootCountry);
        configuration.setIssueUrls(childrenSaved.isEmpty() ? null : childrenSaved.stream().map(Monarch::getUrl).collect(Collectors.toList()));
        return configuration;
    }

    public List<Monarch> extractIssueFromWikiValidatedWithCreate(JSONArray jsonArray, Monarch root, Country rootCountry) {
        List<JSONObject> infoboxes = JsonUtils.readInfoboxes(jsonArray);
        List<JSONObject> issue = JsonUtils.drillForName(infoboxes, "Issue detail", "Issue", "Issue more...", "Issue More", "Illegitimate children Detail", "Issue among others...", "Illegitimate children more...");

        List<String> issueUrls = JsonUtils.readFromLinks(issue, "url").stream()
                .map(smartIssueSearchService::convertChildLink)
                .filter(Objects::nonNull).toList();
        if (!issueUrls.isEmpty()) {
            System.out.println("Wow, found it in simple way...");
            List<Monarch> retval = issueUrls.stream()
                    .map(m->personBuilder.findOrCreate(m,null))
                    .filter(Objects::nonNull)
                    .map(monarchService::save)
                    .collect(Collectors.toList());
            System.out.println("* simple " + retval.size() + "/" + issueUrls.size());
            return retval;
        } else {
            return smartIssueSearchService.smartExtractWithCreate(jsonArray, root, rootCountry);
        }
    }

    public SaveFamilyConfiguration retrieveFamily(LoadFamilyConfiguration configuration) throws IOException, URISyntaxException {
        System.out.println("=== LOADING ===");
        SaveFamilyConfiguration updates = new SaveFamilyConfiguration();

        if (configuration.getRootId() == null || configuration.getRootUrl() == null ||
                configuration.getIssueUrls() != null && configuration.getRootGender() == null)
            return updates;

        // parents
        Provenence provenence = new Provenence(configuration.getRootId());
        if (configuration.getFatherId() == null && configuration.getFatherUrl() != null) {
            UUID father = extractRelative(configuration.getFatherUrl(), Gender.MALE);
            if (father != null) {
                provenence.setFather(father);
            }
        }
        if (configuration.getMotherId() == null && configuration.getMotherUrl() != null) {
            UUID mother = extractRelative(configuration.getMotherUrl(), Gender.FEMALE);
            if (mother != null) {
                provenence.setMother(mother);
            }
        }
        if (provenence.getFather() != null || provenence.getMother() != null) {
            updates.getToCreate().add(provenence);
        }

        // children
        if (configuration.getIssueUrls() == null || configuration.getIssueUrls().isEmpty() ||
                configuration.getIssueIds() != null && configuration.getIssueIds().size() >= configuration.getIssueUrls().size())
            return updates;

        List<UUID> issue = new ArrayList<>();
        for (String url : configuration.getIssueUrls()) {
            UUID uuid = extractRelative(url, null);
            if (uuid != null) issue.add(uuid);
        }

        for (UUID uuid : issue) {
            if (!configuration.getIssueIds().contains(uuid)) {
                updates.getToCreate().add(configuration.getRootGender() == Gender.MALE ?
                        Provenence.builder().id(uuid).father(configuration.getRootId()).build() :
                        Provenence.builder().id(uuid).mother(configuration.getRootId()).build());
            }
        }

        return updates;
    }

    UUID extractRelative(String url, Gender gender) {
        Monarch monarch = monarchService.findByUrl(url);
        monarch = monarch == null ? personBuilder.buildPerson(url) : monarch;

        if (monarch != null) {
            if (monarch.getGender()==null) {
                monarch.setGender(gender==null?
                        personBuilder.detectGender(monarch) :
                        gender);
            }
            System.out.println(monarch.getId() == null ? "== Saving: " + monarch.getUrl() :
                    "== Exists: " + monarch.getUrl());
            monarchService.save(monarch);
            System.out.println(monarch.getName());
            return monarch.getId();
        }
        return null;
    }
}
