package com.tuiken.royaladmin.datalayer;

import com.tuiken.royaladmin.model.entities.Monarch;
import com.tuiken.royaladmin.model.enums.PersonStatus;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;
import java.util.UUID;

public interface MonarchRepository extends CrudRepository<Monarch, UUID> {

    Optional<Monarch> findByUrl(String url);

    Optional<Monarch> findByReignIdsContains(UUID reignId);

    long countByStatus(PersonStatus status);
}
