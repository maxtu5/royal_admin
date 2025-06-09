package com.tuiken.royaladmin.model.entities;

import com.tuiken.royaladmin.model.enums.PersonStatus;

import java.util.UUID;

public interface MonarchIdStatus {
    UUID getId();
    PersonStatus getStatus();
}
