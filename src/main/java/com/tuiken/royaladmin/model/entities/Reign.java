package com.tuiken.royaladmin.model.entities;

import com.tuiken.royaladmin.model.enums.Country;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Setter
@Getter
@Entity
public class Reign {

    @Id
    @GeneratedValue(generator = "UUID")
    @Column(name = "reign_id", columnDefinition = "varchar(255)")
    private UUID id;

    @Column(nullable = false)
    String title;

    private Instant start;
    private Instant end;
    private Instant coronation;
    @Enumerated(EnumType.STRING)
    private Country country;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "throne_id")
    private Throne throne;

}
