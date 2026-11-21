package com.example.banqueservice.graphql.exception;

public class CompteIntrouvableException extends RuntimeException {

    private final Long identifiant;

    public CompteIntrouvableException(Long identifiant) {
        super("Compte " + identifiant + " introuvable");
        this.identifiant = identifiant;
    }

    public Long getIdentifiant() {
        return identifiant;
    }
}
