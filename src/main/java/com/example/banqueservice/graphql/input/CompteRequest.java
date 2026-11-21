package com.example.banqueservice.graphql.input;

import com.example.banqueservice.entities.TypeCompte;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record CompteRequest(
        Long id,
        @NotNull(message = "Le solde est obligatoire.")
        @PositiveOrZero(message = "Le solde ne peut pas etre negatif.")
        Double solde,
        String dateCreation,
        @NotNull(message = "Le type de compte est obligatoire.")
        TypeCompte type
) {
}
