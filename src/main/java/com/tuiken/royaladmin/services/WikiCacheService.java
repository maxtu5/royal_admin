package com.tuiken.royaladmin.services;

import com.tuiken.royaladmin.datalayer.WikiCacheRecordRepository;
import com.tuiken.royaladmin.model.cache.WikiCacheRecord;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.json.JSONArray;
import org.json.JSONException;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class WikiCacheService {
    private final WikiCacheRecordRepository wikiCacheRecordRepository;

    @Transactional
    public void deleteWikiCacheRecord(String url) {
        wikiCacheRecordRepository.deleteByUrl(url);
    }

    public JSONArray extractRootArray(String url) {
        String jsonString = wikiCacheRecordRepository.findByUrl(url)
                .orElseThrow(() -> new RuntimeException("No record found for URL: " + url))
                .getBody();
        try {
            return new JSONArray(jsonString);
        } catch (JSONException e) {
            throw new RuntimeException("Failed to parse JSON", e);
        }
    }

    public boolean isCached(String url) {
        return wikiCacheRecordRepository.findByUrl(url).isPresent();
    }

    public void changeUrl(String url, String resolvedUrl) {
        WikiCacheRecord record = wikiCacheRecordRepository.findByUrl(url).orElseThrow(() -> new RuntimeException("No record found for URL: " + url));
        record.setUrl(resolvedUrl);
        wikiCacheRecordRepository.save(record);
    }
}
