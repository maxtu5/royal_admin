package com.tuiken.royaladmin.model.entities;

import com.tuiken.royaladmin.model.enums.Gender;
import com.tuiken.royaladmin.model.enums.House;
import com.tuiken.royaladmin.model.enums.PersonStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.lang.NonNull;

import java.time.Instant;
import java.util.*;

@Setter
@Getter
@Entity
@NoArgsConstructor
public class Monarch {

    @Id
    @GeneratedValue(generator = "UUID")
    @Column(name = "monarchId", columnDefinition = "varchar(255)")
    private UUID id;

    @Column(unique = true, nullable = false)
    private String url;

    @Column(nullable = false)
    private String name;

//    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Gender gender;

    @ElementCollection
    @Enumerated(EnumType.STRING)
    private Set<House> house = new HashSet<>();

    private Instant birth;

    private Instant death;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    PersonStatus status;

    @ElementCollection
    @CollectionTable
    private List<UUID> reignIds = new ArrayList<>();

    public Monarch(String url) {
        this.url = url;
    }

}
