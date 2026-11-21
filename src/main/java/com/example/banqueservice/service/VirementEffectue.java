package com.example.banqueservice.service;

public record VirementEffectue(
        Long sourceId,
        Long destinationId,
        double montant,
        double soldeSource,
        double soldeDestination
) {
}
