package com.example.banqueservice.graphql.exception;

public class OperationRefuseeException extends RuntimeException {

    public OperationRefuseeException(String motif) {
        super(motif);
    }
}
