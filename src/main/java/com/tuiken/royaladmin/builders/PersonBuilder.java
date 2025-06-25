package com.tuiken.royaladmin.builders;

import com.tuiken.royaladmin.datalayer.ReignRepository;
import com.tuiken.royaladmin.exceptions.WikiApiException;
import com.tuiken.royaladmin.model.api.output.MonarchApiDto;
import com.tuiken.royaladmin.model.entities.Monarch;
import com.tuiken.royaladmin.model.entities.Reign;
import com.tuiken.royaladmin.model.enums.Country;
import com.tuiken.royaladmin.model.enums.Gender;
import com.tuiken.royaladmin.model.enums.PersonStatus;
import com.tuiken.royaladmin.services.*;
import com.tuiken.royaladmin.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.json.JSONArray;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PersonBuilder {

    private final LinkResolver linkResolver;
    private final WikiService wikiService;
    private final MonarchService monarchService;
    private final ReignRepository reignRepository;
    private final AiResolverService aiResolverService;

    public Monarch findOrCreate(String url, Gender gender) {

        String resolvedUrl = linkResolver.resolve(url);
        System.out.println("Reading from source: " + resolvedUrl);

        Monarch monarch = monarchService.findByUrl(resolvedUrl);
        if (monarch != null) System.out.println("== Exists");
        monarch = monarch == null ? buildPerson(url) : monarch;
        if (monarch == null) System.out.println("== FAILED to create attempt failed");
        if (monarch != null && monarch.getGender() == null) monarch.setGender(gender);
        return monarch;
    }

    public Monarch buildPerson(String url) {
        try {
            JSONArray jsonArray = wikiService.read(url);
            if (jsonArray == null || JsonUtils.readInfoboxes(jsonArray).size() == 0) return null;

            Monarch monarch = new Monarch(url);
            monarch.setName(RetrieverService.retrieveProperty(jsonArray, "name"));
            monarch.setGender(Gender.fromTitle(monarch.getName()));
            monarch.setBirth(RetrieverService.retrieveOneDate(jsonArray, "Born"));
            monarch.setDeath(RetrieverService.retrieveOneDate(jsonArray, "Died"));
            monarch.setHouse(RetrieverService.retrieveHouses(jsonArray));
            String[] imageData = RetrieverService.retrieveImage(jsonArray);
            monarch.setImageUrl(imageData[0]);
            monarch.setImageCaption(imageData[1]);
            monarch.setStatus(PersonStatus.NEW_URL);
            System.out.println("== Created person " + monarch.getName());
            return monarch;
        } catch (WikiApiException e) {
            return null;
        }
    }

    public Gender detectGender(Monarch monarch) {
        Gender retval = Gender.fromTitle(monarch.getName());
        if (retval == null) {
            retval = monarch.getReignIds().stream()
                    .map(id -> reignRepository.findById(id).orElse(null))
                    .filter(Objects::nonNull)
                    .map(Reign::getTitle)
                    .filter(Objects::nonNull)
                    .map(Gender::fromTitle)
                    .filter(Objects::nonNull)
                    .findFirst().orElse(null);
        }
//        if (retval == null) {
//            String aiResolved = aiResolverService.findGender(monarch.getName());
//            try {
//                retval = Gender.valueOf(aiResolved);
//            } catch (IllegalArgumentException e) {
//                System.out.println("UNKNOWN???");
//            }
//            System.out.println(monarch.getName() + " defined by AI as " + retval);
//        }
        return retval;
    }

    public List<Reign> createReignsWithSave(String url, Country country) {
        try {
            JSONArray jsonArray = wikiService.read(url);
            return RetrieverService.retrieveReigns(jsonArray, country).stream()
                    .map(reignRepository::save).collect(Collectors.toList());
        } catch (WikiApiException e) {
            return null;
        }
    }

    public MonarchApiDto createFromUrl(String url) {
        Monarch monarch = findOrCreate(url, null);
        if (monarch == null) {
            System.out.println("Could not be found or created");
            return null;
        }
        monarch = monarchService.save(monarch);
        return monarchService.toApiDto(monarch);
    }
}
