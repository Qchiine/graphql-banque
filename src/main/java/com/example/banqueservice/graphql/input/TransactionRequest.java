package com.example.banqueservice.graphql.input;

import com.example.banqueservice.entities.TypeTransaction;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record TransactionRequest(
        @NotNull(message = "L'identifiant du compte est obligatoire.")
        Long compteId,
        @NotNull(message = "Le montant est obligatoire.")
        @Positive(message = "Le montant doit etre strictement positif.")
        Double montant,
        String date,
        @NotNull(message = "Le type de transaction est obligatoire.")
        TypeTransaction type,
        String libelle
) {
}
