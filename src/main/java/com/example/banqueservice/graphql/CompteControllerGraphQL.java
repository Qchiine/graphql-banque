package com.example.banqueservice.graphql;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

import com.example.banqueservice.entities.Compte;
import com.example.banqueservice.entities.Transaction;
import com.example.banqueservice.graphql.input.CompteRequest;
import com.example.banqueservice.graphql.input.TransactionRequest;
import com.example.banqueservice.graphql.input.VirementRequest;
import com.example.banqueservice.service.BanqueService;
import com.example.banqueservice.service.ReleveEffectue;
import com.example.banqueservice.service.StatistiquesSoldes;
import com.example.banqueservice.service.StatistiquesTransactions;
import com.example.banqueservice.service.VirementEffectue;

import jakarta.validation.Valid;

@Controller
public class CompteControllerGraphQL {

    private final BanqueService banqueService;

    public CompteControllerGraphQL(BanqueService banqueService) {
        this.banqueService = banqueService;
    }

    @QueryMapping
    public List<Compte> allComptes() {
        return banqueService.allComptes();
    }

    @QueryMapping
    public Compte compteById(@Argument Long id) {
        return banqueService.compteById(id);
    }

    @QueryMapping
    public StatistiquesSoldes totalSolde() {
        return banqueService.statistiquesSoldes();
    }

    @QueryMapping
    public List<Transaction> compteTransactions(@Argument Long id) {
        return banqueService.compteTransactions(id);
    }

    @QueryMapping
    public List<Transaction> allTransactions() {
        return banqueService.allTransactions();
    }

    @QueryMapping
    public StatistiquesTransactions transactionStats() {
        return banqueService.statistiquesTransactions();
    }

    @QueryMapping
    public ReleveEffectue releve(@Argument Long compteId) {
        return banqueService.releve(compteId);
    }

    @MutationMapping
    public Compte saveCompte(@Argument @Valid CompteRequest compte) {
        return banqueService.saveCompte(compte);
    }

    @MutationMapping
    public Transaction addTransaction(@Argument @Valid TransactionRequest transaction) {
        return banqueService.addTransaction(transaction);
    }

    @MutationMapping
    public VirementEffectue virement(@Argument @Valid VirementRequest virement) {
        return banqueService.virement(virement);
    }

    @MutationMapping
    public Boolean deleteCompte(@Argument Long id) {
        return banqueService.deleteCompte(id);
    }

    @SchemaMapping(typeName = "Compte", field = "soldeCalcule")
    public double soldeCalcule(Compte compte) {
        return banqueService.soldeCalcule(compte);
    }

    @SchemaMapping(typeName = "Compte", field = "nombreTransactions")
    public int nombreTransactions(Compte compte) {
        return banqueService.nombreTransactions(compte.getId());
    }

    @SchemaMapping(typeName = "Compte", field = "dateCreation")
    public String dateCreation(Compte compte) {
        return formaterDate(compte.getDateCreation(), "yyyy-MM-dd");
    }

    @SchemaMapping(typeName = "Transaction", field = "date")
    public String dateTransaction(Transaction transaction) {
        return formaterDate(transaction.getDate(), "yyyy-MM-dd");
    }

    private String formaterDate(java.util.Date date, String pattern) {
        if (date == null) {
            return null;
        }
        return new SimpleDateFormat(pattern, Locale.FRANCE).format(date);
    }
}
