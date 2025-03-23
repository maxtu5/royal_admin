package com.tuiken.royaladmin.datalayer;

import com.tuiken.royaladmin.model.workflows.UnhandledRecord;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.UUID;

public interface UnhandledRecordRepository extends CrudRepository<UnhandledRecord, UUID> {

    List<UnhandledRecord> findAll();

}
