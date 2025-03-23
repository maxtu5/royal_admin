package com.tuiken.royaladmin.datalayer;

import com.tuiken.royaladmin.model.entities.Provenence;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.UUID;

public interface ProvenenceRepository extends CrudRepository<Provenence, UUID> {

    List<Provenence> findByFather(UUID id);

    List<Provenence> findByMother(UUID id);
}
