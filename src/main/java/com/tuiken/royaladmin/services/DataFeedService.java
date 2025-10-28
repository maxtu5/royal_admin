package com.tuiken.royaladmin.services;

import com.tuiken.royaladmin.builders.PersonBuilder;
import com.tuiken.royaladmin.exceptions.NotPersonWikiApiException;
import com.tuiken.royaladmin.model.api.output.MonarchApiDto;
import com.tuiken.royaladmin.model.cache.WikiCacheRecord;
import com.tuiken.royaladmin.model.entities.Monarch;
import com.tuiken.royaladmin.model.enums.PersonStatus;
import com.tuiken.royaladmin.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.json.JSONArray;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class DataFeedService {
    private final WikiCacheService wikiCacheService;
    private final MonarchService monarchService;
    private final LinkResolver linkResolver;
    private final PersonBuilder personBuilder;
    private final WikiLoaderService wikiLoaderService;

    public List<MonarchApiDto> resolveUnusedCacheRecord(String url) {
        List<MonarchApiDto> retval = new ArrayList<>();

        // validate url
        System.out.println("\n=@ Resolving unused cache record " + url);
        if (!wikiCacheService.isCached(url)) {
            System.out.println("cache miss");
            return retval;
        }
        if (monarchService.existsByUrl(url)) {
            System.out.println("monarch exists");
            return retval;
        }
        String resolvedUrl = linkResolver.resolve(url);
        if (!resolvedUrl.equals(url)) {
            if (wikiCacheService.isCached(resolvedUrl)) {
                wikiCacheService.deleteWikiCacheRecord(url);
                System.out.println("Deleted by unresolved url " + url + ". Must be: " + resolvedUrl);
            } else {
                wikiCacheService.changeUrl(url, resolvedUrl);
                System.out.println("Renamed unresolved url " + url + " to  " + resolvedUrl+"\nProcessing");
            }
            return retval;
        }
        // read raw data, extract urls
        Set<String> monarchUrls = null;
        WikiCacheRecord wikiCacheRecord = wikiCacheService.findByUrl(resolvedUrl);
        JSONArray rootArray = wikiCacheService.extractRootArray(resolvedUrl);
        try {
            monarchUrls = extractWikiLinks(rootArray);
        } catch (NotPersonWikiApiException e) {
            wikiCacheRecord.setStatus("NOT_PERSON");
            wikiCacheService.save(wikiCacheRecord);
            return retval;
        }
        monarchUrls = refine(monarchUrls);
        System.out.println("URLS in article: " + monarchUrls.size());
        // check if urls link back to source url
        Set<String> backLinked = monarchUrls.stream().filter(monarchUrl -> {
            try {
                return extractWikiLinks(wikiCacheService.extractRootArray(resolvedUrl)).contains(resolvedUrl);
            } catch (NotPersonWikiApiException e) {
                throw new RuntimeException(e);
            }
        }).collect(Collectors.toSet());
        System.out.println("URLS back link: " + backLinked.size());
        if (backLinked.isEmpty() || monarchUrls.isEmpty()) {
            wikiCacheRecord.setStatus("SKIP");
            wikiCacheService.save(wikiCacheRecord);
            return retval;
        };
        // create monarch
        Monarch monarch = personBuilder.buildPerson(resolvedUrl, rootArray);
        if (monarch.getStatus().equals(PersonStatus.NEW_AI))
            monarch.setStatus(PersonStatus.EPHEMERAL);
        monarchService.save(monarch);
        // reread backlinks to enrich data
        Set<MonarchApiDto> allLoaded = new HashSet<>();
        backLinked.forEach(link -> {
            Monarch relative = monarchService.findByUrl(link);
            if (relative.getStatus().equals(PersonStatus.RESOLVED)) {
                relative.setStatus(PersonStatus.NEW_URL);
                relative.setProcess("AI");
                monarchService.save(relative);
            }
            List<MonarchApiDto> loaded = wikiLoaderService.loadFamilyOne(relative.getId());
            allLoaded.addAll(loaded);
        });
        allLoaded.add(monarchService.toApiDto(monarch));
        retval = allLoaded.stream().filter(m -> !m.getStatus().equals(PersonStatus.RESOLVED)).toList();
        return retval;
    }

    private Set<String> extractWikiLinks(JSONArray rootArray) throws NotPersonWikiApiException {
        if (rootArray.getJSONObject(0).has("error") &&
                rootArray.getJSONObject(0).getString("error").equals("not a person"))
            throw new NotPersonWikiApiException("not person");
        return JsonUtils.extractWikiLinks(rootArray).stream()
                .filter(s -> !s.contains("#"))
                .collect(Collectors.toSet());
    }

    private Set<String> refine(Set<String> strings) {
        Set<String> monarchUrls = strings.stream().filter(monarchService::existsByUrl).collect(Collectors.toSet());
        return !monarchUrls.isEmpty() ? monarchUrls :
                (strings.size() > 6 ? new HashSet<>() :
                        strings.stream()
                                .map(link -> {
                                    try {
                                        Thread.sleep(1000);
                                    } catch (InterruptedException e) {
                                        throw new RuntimeException(e);
                                    }
                                    return linkResolver.resolve(link);
                                })
                                .filter(Objects::nonNull)
                                .filter(monarchService::existsByUrl)
                                .collect(Collectors.toSet())
                );
    }

}
