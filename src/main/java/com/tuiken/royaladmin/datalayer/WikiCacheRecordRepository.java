package com.tuiken.royaladmin.datalayer;

import com.tuiken.royaladmin.model.cache.WikiCacheRecord;
import org.springframework.data.domain.Limit;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WikiCacheRecordRepository extends CrudRepository<WikiCacheRecord, UUID> {

    Optional<WikiCacheRecord> findByUrl(String url);

    Iterable<WikiCacheRecord> findFirst100ByOrderByCacheId();

    List<WikiCacheRecord> findAll();
    boolean existsByUrl(String url);

    void deleteByUrl(String url);
}
