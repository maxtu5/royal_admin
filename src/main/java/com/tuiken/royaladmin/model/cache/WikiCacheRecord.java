package com.tuiken.royaladmin.model.cache;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.GenericGenerator;

import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class WikiCacheRecord {
    @Id
    @GeneratedValue(generator = "UUID")
    @Column(columnDefinition = "varchar(255)")
    UUID cacheId;
    String url;
    @Column(columnDefinition = "longtext")
    String body;
    @Column(columnDefinition = "varchar(10)")
    UUID status;

    public WikiCacheRecord(String url) {
        this.url = url;
    }
}
