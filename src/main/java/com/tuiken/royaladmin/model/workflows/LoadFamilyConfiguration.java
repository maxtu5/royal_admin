package com.tuiken.royaladmin.model.workflows;

import com.tuiken.royaladmin.model.entities.Monarch;
import com.tuiken.royaladmin.model.enums.Gender;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class LoadFamilyConfiguration {

    UUID rootId;
    String rootUrl;
    Gender rootGender;

    UUID fatherId;
    Monarch father;
    UUID motherId;
    Monarch mother;

    List<UUID> issueIds = new ArrayList<>();
    List<Monarch> issue;

    public void print() {
        System.out.println("=== PRE LOAD CONFIGURATION ===");
        System.out.println("=== Monarch: " + getRootUrl());
        String knownLine = "== Known: " +
                (motherId!=null ? "mother, " : "") +
                (fatherId!=null ? "father, " : "") +
                (issueIds!=null && !issueIds.isEmpty() ? issueIds.size() + " children" : "");
        System.out.println(knownLine);
        System.out.println("== In wiki:");
        if (father!=null) System.out.println((father.getId()==null?"= (new)":"= ")+"Father: " + father.getName());
        if (mother!=null) System.out.println((mother.getId()==null?"= (new)":"= ")+"Mother: " + mother.getName());
        if (issue!=null && !issue.isEmpty()) {
            System.out.println("= Children: " + issue.size());
            issue.forEach(s-> System.out.println((s.getId()==null?"= (new)":"= ")+"Child: " + s.getName()));
        }
    }

}
