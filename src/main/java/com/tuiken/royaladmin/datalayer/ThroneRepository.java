package com.tuiken.royaladmin.datalayer;

import com.tuiken.royaladmin.model.entities.Throne;
import com.tuiken.royaladmin.model.enums.Country;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.UUID;

public interface ThroneRepository extends CrudRepository<Throne, UUID> {

    List<Throne> findByCountry(Country country);
}
