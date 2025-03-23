package com.tuiken.royaladmin.services;

import com.tuiken.royaladmin.builders.PersonBuilder;
import com.tuiken.royaladmin.datalayer.ProvenenceRepository;
import com.tuiken.royaladmin.datalayer.UnhandledRecordRepository;
import com.tuiken.royaladmin.model.entities.Monarch;
import com.tuiken.royaladmin.model.entities.Provenence;
import com.tuiken.royaladmin.model.enums.Gender;
import com.tuiken.royaladmin.model.workflows.UnhandledRecord;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.util.Strings;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLDecoder;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UnhandledRecordService {

    private static final String BROWSE_AI_REQUEST = """
            {
              "inputParameters": {
                "originUrl": "%s",
                "notable_descendants_limit": "15"
              }
            }
            """;
    private static final String BROWSE_AI_REQUEST_PATH = "https://api.browse.ai/v2/robots/%s/tasks";
    private static final String BROWSE_AI_RESPONSE_PATH = "https://api.browse.ai/v2/robots/%s/tasks/%s";

    private final UnhandledRecordRepository unhandledRecordRepository;
    private final MonarchService monarchService;
    private final PersonBuilder personBuilder;
    private final ProvenenceRepository provenenceRepository;
    private final AiResolverService aiResolverService;

    private final RestTemplate restTemplate = new RestTemplate();

    public UnhandledRecord save(UnhandledRecord monarch) {
        return unhandledRecordRepository.save(monarch);
    }

    @Transactional
    public void resolve() {
        List<UnhandledRecord> unhandledRecords = new ArrayList<>();
        unhandledRecordRepository.findAll().forEach(unhandledRecords::add);
        List<UUID> toKill = unhandledRecords.stream()
                .filter(r -> r.getSolution() != null)
                .map(r -> {
                    if (!r.getSolution().equals("kill")) {
                        Monarch parent = monarchService.findByUrl(r.getParentUrl());
                        if (parent == null || parent.getGender() == null) {
                            System.out.println("no gender or parent: " + r.getParentUrl());
                            return null;
                        }
                        r.setSolution(URLDecoder.decode(r.getSolution()));
                        Monarch monarch = monarchService.findByUrl(r.getSolution());
                        if (monarch == null) {
                            monarch = personBuilder.buildPerson(r.getSolution());
                            if (monarch == null) return r;
                            if (monarch.getGender() == null) {
                                monarch.setGender(personBuilder.detectGender(monarch));
                            }
                            monarch = monarchService.save(monarch);
                        }
                        Provenence provenence = provenenceRepository.findById(monarch.getId()).orElse(null);
                        if (provenence == null) {
                            provenence = new Provenence(monarch.getId());
                            if (parent.getGender().equals(Gender.MALE)) {
                                provenence.setFather(parent.getId());
                            }
                            if (parent.getGender().equals(Gender.FEMALE)) {
                                provenence.setMother(parent.getId());
                            }
                            provenence = provenenceRepository.save(provenence);
                        }
                        r.setSolution("kill");
                    }
                    return r;
                })
                .filter(Objects::nonNull)
                .filter(r -> r.getSolution().equals("kill"))
                .map(UnhandledRecord::getId).toList();
        toKill.forEach(System.out::println);
    }

    public int deleteKilled() {
        List<UnhandledRecord> unhandledRecords = new ArrayList<>();
        unhandledRecordRepository.findAll().forEach(unhandledRecords::add);
        List<UnhandledRecord> kill = unhandledRecords.stream().filter(r -> r.getSolution() != null && r.getSolution().equals("kill"))
                .collect(Collectors.toList());
        unhandledRecordRepository.deleteAll(kill);
        return kill.size();
    }

    @Transactional
    public long orderResolve() {
        Map.Entry<String, List<UnhandledRecord>> stringListEntry = unhandledRecordRepository.findAll().stream()
                .filter(r -> Strings.isBlank(r.getSolution()))
                .collect(Collectors.groupingBy(UnhandledRecord::getParentUrl, Collectors.toList()))
                .entrySet().stream()
                .sorted((es2, es1) -> es1.getValue().size() - es2.getValue().size())
                .findFirst()
                .orElse(null);
        List<UnhandledRecord> unhandledRecords = stringListEntry.getValue();
        Monarch parent = monarchService.findByUrl(stringListEntry.getKey());

        String sentRequest = sendBrowseAiRequest(parent.getUrl());
        JSONObject obj = new JSONObject(sentRequest);
        String requestId = obj.getJSONObject("result").getString("id");
        unhandledRecords.forEach(r -> {
                    r.setSolution(requestId);
                    unhandledRecordRepository.save(r);
                });
        return unhandledRecords.size();
    }

    @Transactional
    public long receiveResolve() {
        Map<String, List<UnhandledRecord>> stringListMap = unhandledRecordRepository.findAll().stream()
                .filter(r -> Strings.isNotBlank(r.getSolution()) && !r.getSolution().startsWith("http"))
                .collect(Collectors.groupingBy(UnhandledRecord::getParentUrl, Collectors.toList()));
        stringListMap.entrySet().forEach(r -> {
            System.out.println(r.getKey());
            r.getValue().forEach(m -> System.out.println(m.getSolution()));
            Monarch parent = monarchService.findByUrl(r.getKey());
            List<Monarch> children = parent.getGender().equals(Gender.FEMALE) ?
                    provenenceRepository.findByMother(parent.getId()).stream()
                            .map(Provenence::getId)
                            .map(monarchService::finById)
                            .toList() :
                    provenenceRepository.findByFather(parent.getId()).stream()
                            .map(Provenence::getId)
                            .map(monarchService::finById)
                            .toList();
            JSONArray browseAiData = new JSONArray(receiveBrowseAiResponse(r.getValue().get(0).getSolution()));
            Map<String, String> browsed = new HashMap<>();
            for (int i = 0; i < browseAiData.length(); i++) {
                String link = browseAiData.getJSONObject(i).isNull("Profile Link")? "" : browseAiData.getJSONObject(i).getString("Profile Link");
                String name = browseAiData.getJSONObject(i).isNull("Name")? "" : browseAiData.getJSONObject(i).getString("Name");

                if (children.stream().noneMatch(m -> m.getUrl().equals(link))) {
                    System.out.println(link.equals("") ? "Unlinked" : name + " " + link);
                    if (Strings.isNotBlank(link)) {
                        browsed.put(name, link);
                    }
                } else {
                    browsed.put(name, "exists");
                    System.out.println("Matched " + name);
                }
            }
            r.getValue().forEach(rh -> {
                    if (browsed.containsKey(rh.getChild())) {
                        rh.setSolution(browsed.get(rh.getChild()));
                    }
                    System.out.println(rh.getChild()+" "+rh.getChildUrl());
                    Monarch p = monarchService.findByUrl(rh.getParentUrl());
                    unhandledRecordRepository.save(rh);
                });
        });
        return stringListMap.size();
    }

    private String receiveBrowseAiResponse(String solution) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("2ba50e70-5e74-4cfa-a390-33927ffb9a12:729ad3ce-ba5b-40d6-bb65-c97f80faac60");
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> requestEntity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(
                String.format(BROWSE_AI_RESPONSE_PATH, "9e038bbf-3fa5-4c61-a022-b7762c9bf991", solution), HttpMethod.GET, requestEntity, String.class);
        return (new JSONObject(response.getBody()))
                .getJSONObject("result").getJSONObject("capturedLists").getJSONArray("Notable Descendants")
                .toString();
    }

    private String sendBrowseAiRequest(String url) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("2ba50e70-5e74-4cfa-a390-33927ffb9a12:729ad3ce-ba5b-40d6-bb65-c97f80faac60");
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> requestEntity = new HttpEntity<>(String.format(BROWSE_AI_REQUEST, url), headers);
        ResponseEntity<String> response = restTemplate.exchange(
                String.format(BROWSE_AI_REQUEST_PATH, "9e038bbf-3fa5-4c61-a022-b7762c9bf991"), HttpMethod.POST, requestEntity, String.class);
        return response.getBody();
    }

}
