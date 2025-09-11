package com.tuiken.royaladmin.exceptions;

import com.tuiken.royaladmin.exceptions.NotPersonWikiApiException;
import com.tuiken.royaladmin.exceptions.UnexpectedInfoboxWikiApiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class RepairControllerAdvice {

    @ExceptionHandler(UnexpectedInfoboxWikiApiException.class)
    public ResponseEntity<Map<String, Object>> handleUnexpectedInfobox(UnexpectedInfoboxWikiApiException ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("error", "Unexpected infobox format");
        response.put("details", ex.getMessage());
        response.put("type", "UnexpectedInfoboxWikiApiException");
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(NotPersonWikiApiException.class)
    public ResponseEntity<Map<String, Object>> handleNotPerson(NotPersonWikiApiException ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("error", "Entity is not a person");
        response.put("details", ex.getMessage());
        response.put("type", "NotPersonWikiApiException");
        return new ResponseEntity<>(response, HttpStatus.UNPROCESSABLE_ENTITY);
    }
}