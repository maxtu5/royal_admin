package com.tuiken.royaladmin.exceptions;

public class UnexpectedInfoboxWikiApiException extends WikiApiException{
    public UnexpectedInfoboxWikiApiException(String message, Throwable cause) {
        super(message, cause);
    }
    public UnexpectedInfoboxWikiApiException(String message) {
        super(message);
    }
}
