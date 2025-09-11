package com.tuiken.royaladmin.exceptions;

public class NotPersonWikiApiException extends WikiApiException{
    public NotPersonWikiApiException(String message, Throwable cause) {
        super(message, cause);
    }
    public NotPersonWikiApiException(String message) {
        super(message);
    }
}
