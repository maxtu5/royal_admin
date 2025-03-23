package com.tuiken.royaladmin.model.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@Entity
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class Provenence {

    @Id
    @Column(name="provenenceId", columnDefinition = "varchar(255)")
    UUID id;

    @Column(columnDefinition = "varchar(255)")
    UUID mother;

    @Column(columnDefinition = "varchar(255)")
    UUID father;

    public Provenence(UUID id) {
        this.id = id;
    }
}
