package com.example.banqueservice.service;

import java.util.List;

import com.example.banqueservice.entities.Transaction;

public record ReleveEffectue(
        Long compteId,
        double soldeInitial,
        double soldeCalcule,
        List<Transaction> transactions
) {
}
