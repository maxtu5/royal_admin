package com.tuiken.royaladmin.datalayer;

import com.tuiken.royaladmin.model.entities.Reign;
import com.tuiken.royaladmin.model.enums.Country;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.UUID;

public interface ReignRepository extends CrudRepository<Reign, UUID> {

    List<Reign> findByIdInAndCountry(List<UUID> id, Country country);
}
