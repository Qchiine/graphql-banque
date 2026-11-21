package com.example.banqueservice.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.banqueservice.entities.Compte;
import com.example.banqueservice.entities.Transaction;
import com.example.banqueservice.entities.TypeTransaction;
import com.example.banqueservice.graphql.exception.CompteIntrouvableException;
import com.example.banqueservice.graphql.exception.OperationRefuseeException;
import com.example.banqueservice.graphql.input.CompteRequest;
import com.example.banqueservice.graphql.input.TransactionRequest;
import com.example.banqueservice.graphql.input.VirementRequest;
import com.example.banqueservice.repositories.CompteRepository;
import com.example.banqueservice.repositories.TransactionRepository;

@Service
@Transactional
public class BanqueService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter DATE_SLASH_FORMAT =
            DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final CompteRepository compteRepository;
    private final TransactionRepository transactionRepository;

    public BanqueService(CompteRepository compteRepository,
                         TransactionRepository transactionRepository) {
        this.compteRepository = compteRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional(readOnly = true)
    public List<Compte> allComptes() {
        return compteRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Compte compteById(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("L'identifiant du compte est obligatoire.");
        }
        return compteRepository.findById(id)
                .orElseThrow(() -> new CompteIntrouvableException(id));
    }

    public Compte saveCompte(CompteRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("La requete de compte est obligatoire.");
        }
        Compte compte = request.id() == null
                ? new Compte()
                : compteRepository.findById(request.id())
                        .orElseThrow(() -> new CompteIntrouvableException(request.id()));
        compte.setSolde(arrondir(request.solde()));
        compte.setDateCreation(parseDate(request.dateCreation()));
        compte.setType(request.type());
        return compteRepository.save(compte);
    }

    @Transactional(readOnly = true)
    public double soldeCalcule(Long compteId) {
        return soldeCalcule(compteById(compteId));
    }

    /**
     * Le solde stocke est le solde de reference saisi a la creation ; le solde
     * courant se deduit des mouvements. Une seule source de verite, donc pas de
     * double comptage entre le champ solde et la table transaction.
     */
    @Transactional(readOnly = true)
    public double soldeCalcule(Compte compte) {
        double depots = 0.0;
        double retraits = 0.0;
        for (Transaction transaction : transactionRepository.findByCompte(compte)) {
            if (transaction.getType() == TypeTransaction.DEPOT) {
                depots += transaction.getMontant();
            } else {
                retraits += transaction.getMontant();
            }
        }
        return arrondir(compte.getSolde() + depots - retraits);
    }

    @Transactional(readOnly = true)
    public int nombreTransactions(Long compteId) {
        return transactionRepository.countByCompte(compteById(compteId));
    }

    @Transactional(readOnly = true)
    public List<Transaction> compteTransactions(Long compteId) {
        return transactionRepository.findByCompte(compteById(compteId));
    }

    @Transactional(readOnly = true)
    public List<Transaction> allTransactions() {
        return transactionRepository.findAll();
    }

    public Transaction addTransaction(TransactionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("La requete de transaction est obligatoire.");
        }
        Compte compte = compteById(request.compteId());
        Transaction transaction = new Transaction();
        transaction.setMontant(arrondir(request.montant()));
        transaction.setDate(parseDate(request.date()));
        transaction.setType(request.type());
        transaction.setLibelle(normaliserLibelle(request.libelle(), request.type()));
        transaction.setCompte(compte);
        verifierRetraitPossible(compte, transaction);
        return transactionRepository.save(transaction);
    }

    public boolean deleteCompte(Long id) {
        Compte compte = compteRepository.findById(id)
                .orElseThrow(() -> new CompteIntrouvableException(id));
        List<Transaction> transactions = transactionRepository.findByCompte(compte);
        if (!transactions.isEmpty()) {
            throw new OperationRefuseeException(
                    "Le compte " + id + " porte " + transactions.size()
                            + " transaction(s) : suppression refusee.");
        }
        compteRepository.delete(compte);
        return true;
    }

    /**
     * Les deux comptes sont mis a jour dans la meme transaction : un echec
     * laisse les soldes et les transactions inchanges.
     */
    public VirementEffectue virement(VirementRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("La requete de virement est obligatoire.");
        }
        if (request.sourceId().equals(request.destinationId())) {
            throw new OperationRefuseeException(
                    "Le compte source et le compte destination doivent differer.");
        }
        Compte source = compteById(request.sourceId());
        Compte destination = compteById(request.destinationId());

        double montant = arrondir(request.montant());
        double soldeSource = soldeCalcule(source);
        if (soldeSource < montant) {
            throw new OperationRefuseeException("Solde insuffisant sur le compte "
                    + source.getId() + " : " + soldeSource + " disponible, " + montant + " demande.");
        }

        String libelle = normaliserLibelle(request.libelle(), null);
        Date maintenant = new Date();

        Transaction debit = new Transaction();
        debit.setMontant(montant);
        debit.setDate(maintenant);
        debit.setType(TypeTransaction.RETRAIT);
        debit.setLibelle(libelle + " vers compte " + destination.getId());
        debit.setCompte(source);

        Transaction credit = new Transaction();
        credit.setMontant(montant);
        credit.setDate(maintenant);
        credit.setType(TypeTransaction.DEPOT);
        credit.setLibelle(libelle + " depuis compte " + source.getId());
        credit.setCompte(destination);

        transactionRepository.save(debit);
        transactionRepository.save(credit);

        return new VirementEffectue(
                source.getId(),
                destination.getId(),
                montant,
                soldeCalcule(source),
                soldeCalcule(destination)
        );
    }
    @Transactional(readOnly = true)
    public ReleveEffectue releve(Long compteId) {
        Compte compte = compteById(compteId);
        List<Transaction> transactions = transactionRepository.findByCompte(compte);
        return new ReleveEffectue(
                compte.getId(),
                arrondir(compte.getSolde()),
                soldeCalcule(compte),
                transactions
        );
    }

    @Transactional(readOnly = true)
    public StatistiquesSoldes statistiquesSoldes() {
        List<Compte> comptes = compteRepository.findAll();
        double sum = 0.0;
        for (Compte compte : comptes) {
            sum += soldeCalcule(compte);
        }
        double total = arrondir(sum);
        double average = comptes.isEmpty() ? 0.0 : arrondir(total / comptes.size());
        return new StatistiquesSoldes(comptes.size(), total, average);
    }

    @Transactional(readOnly = true)
    public StatistiquesTransactions statistiquesTransactions() {
        long count = transactionRepository.count();
        double depots = arrondir(transactionRepository.sumByType(TypeTransaction.DEPOT));
        double retraits = arrondir(transactionRepository.sumByType(TypeTransaction.RETRAIT));
        return new StatistiquesTransactions(count, depots, retraits, arrondir(depots - retraits));
    }

    private void verifierRetraitPossible(Compte compte, Transaction transaction) {
        if (transaction.getType() != TypeTransaction.RETRAIT) {
            return;
        }
        double disponible = soldeCalcule(compte);
        if (disponible < transaction.getMontant()) {
            throw new OperationRefuseeException("Solde insuffisant sur le compte "
                    + compte.getId() + " : " + disponible + " disponible, "
                    + transaction.getMontant() + " demande.");
        }
    }

    private String normaliserLibelle(String libelle, TypeTransaction type) {
        if (libelle != null && !libelle.isBlank()) {
            return libelle.trim();
        }
        return type == null ? "Virement" : type.name();
    }

    private Date parseDate(String value) {
        if (value == null || value.isBlank()) {
            return new Date();
        }
        String raw = value.trim();
        LocalDate date;
        try {
            date = LocalDate.parse(raw, DATE_FORMAT);
        } catch (DateTimeParseException first) {
            try {
                date = LocalDate.parse(raw, DATE_SLASH_FORMAT);
            } catch (DateTimeParseException second) {
                throw new IllegalArgumentException(
                        "Format de date invalide : " + value + " (attendu yyyy-MM-dd ou yyyy/MM/dd).");
            }
        }
        return Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private double arrondir(double valeur) {
        return BigDecimal.valueOf(valeur).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
