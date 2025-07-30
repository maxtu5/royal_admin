package com.tuiken.royaladmin.datalayer;

import com.tuiken.royaladmin.model.entities.Monarch;
import com.tuiken.royaladmin.model.entities.MonarchIdStatus;
import com.tuiken.royaladmin.model.enums.PersonStatus;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface MonarchRepository extends CrudRepository<Monarch, UUID> {

    Optional<Monarch> findByUrl(String url);

    Optional<Monarch> findByReignIdsContains(UUID reignId);

    List<MonarchIdStatus> findAllByIdIn(Set<UUID> ids);

    long countByStatus(PersonStatus status);

    long countByProcess(String done);
}
