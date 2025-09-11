package com.tuiken.royaladmin.exceptions;

public class WikiApiException extends Exception {

    public WikiApiException(String message) { super(message);}
    public WikiApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
