package com.example.banqueservice.graphql.input;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record VirementRequest(
        @NotNull(message = "Le compte source est obligatoire.")
        Long sourceId,
        @NotNull(message = "Le compte destination est obligatoire.")
        Long destinationId,
        @NotNull(message = "Le montant est obligatoire.")
        @Positive(message = "Le montant doit etre strictement positif.")
        Double montant,
        String libelle
) {
}
