package com.tuiken.royaladmin.ai;

import lombok.Getter;

@Getter
public enum Prompts {
    PERSON_ALL_WDESC("""
            given this wiki text
            %s
            
            create json similar to following
            {
              "name": "Otto II the Black",
              "gender": "MALE",
              "birth": "1467-02-01T00:00:00Z",
              "death": "1522-02-01T00:00:00Z",
              "description" : ...// limit to 500 chars
            }
            
            return { null } if provided text is not a noble person description
            
            default date to feb 01 if only year is known
            give average year if range provided
            dont return any other comments of formatting, only parsable json
            dont add 'json' word and triple quotes
            """),

    PERSON_ALL("""
                        given this wiki text
                        %s
              
                        create json similar to following
                        {
                          "name": "Otto II the Black",
                          "gender": "MALE",
                          "birth": "1467-02-01T00:00:00Z",
                          "death": "1522-02-01T00:00:00Z",
                        }
            
                        return { null } if provided text is not a noble person description
            
                        default date to feb 01 if only year is known
                        give average year if range provided
                        dont return any other comments of formatting, only parsable json
                        dont add 'json' word and triple quotes
            """);

    private final String text;

    Prompts(String text) {
        this.text = text;
    }

}