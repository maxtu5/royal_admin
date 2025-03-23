package com.tuiken.royaladmin.model.entities;

import com.tuiken.royaladmin.model.enums.Country;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Setter
@Getter
public class Throne {

    @Id
    @GeneratedValue(generator = "UUID")
    @Column(name = "throne_id", columnDefinition = "varchar(255)")
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Country country;

    @OrderColumn
    @OneToMany(
            fetch = FetchType.LAZY,
            cascade = CascadeType.ALL,
            orphanRemoval = true)
    @JoinColumn(name = "throne_id")

    private List<Reign> reigns = new ArrayList<>();
}
