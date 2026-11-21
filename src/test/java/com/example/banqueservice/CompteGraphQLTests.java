package com.example.banqueservice;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.graphql.tester.AutoConfigureGraphQlTester;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.context.ActiveProfiles;

import com.example.banqueservice.repositories.CompteRepository;
import com.example.banqueservice.repositories.TransactionRepository;

@SpringBootTest
@AutoConfigureGraphQlTester
@ActiveProfiles("test")
class CompteGraphQLTests {

    @Autowired
    private GraphQlTester graphQlTester;

    @Autowired
    private CompteRepository compteRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @BeforeEach
    void vider() {
        transactionRepository.deleteAll();
        compteRepository.deleteAll();
    }

    private Long creerCompte(double solde, String type) {
        String id = graphQlTester.document("""
                        mutation($solde: Float, $type: TypeCompte) {
                            saveCompte(compte: {solde: $solde, dateCreation: "2024-11-18", type: $type}) {
                                id
                            }
                        }
                        """)
                .variable("solde", solde)
                .variable("type", type)
                .execute()
                .path("saveCompte.id")
                .entity(String.class)
                .get();
        return Long.parseLong(id);
    }

    @Test
    void saveCompte_persisteEtRendLeCompte() {
        graphQlTester.document("""
                        mutation {
                            saveCompte(compte: {solde: 1500.0, dateCreation: "2024-11-18", type: COURANT}) {
                                id
                                solde
                                dateCreation
                                type
                            }
                        }
                        """)
                .execute()
                .path("saveCompte.solde").entity(Double.class).isEqualTo(1500.0)
                .path("saveCompte.dateCreation").entity(String.class).isEqualTo("2024-11-18")
                .path("saveCompte.type").entity(String.class).isEqualTo("COURANT");

        assertThat(compteRepository.count()).isEqualTo(1);
    }

    @Test
    void allComptes_retourneTousLesComptes() {
        creerCompte(1500.0, "COURANT");
        creerCompte(3000.0, "EPARGNE");

        graphQlTester.document("{ allComptes { solde type } }")
                .execute()
                .path("allComptes").entityList(Object.class).hasSize(2);
    }

    @Test
    void compteById_retourneLeCompteDemande() {
        Long id = creerCompte(2000.0, "EPARGNE");

        graphQlTester.document("query($id: ID) { compteById(id: $id) { id solde type } }")
                .variable("id", id)
                .execute()
                .path("compteById.solde").entity(Double.class).isEqualTo(2000.0)
                .path("compteById.type").entity(String.class).isEqualTo("EPARGNE");
    }

    @Test
    void compteById_leveUneErreurNotFoundSiAbsent() {
        graphQlTester.document("{ compteById(id: 999) { id } }")
                .execute()
                .errors()
                .expect(error -> error.getMessage().contains("Compte 999 introuvable"))
                .verify();
    }

    @Test
    void totalSolde_calculeSommeEtMoyenne() {
        creerCompte(1000.0, "COURANT");
        creerCompte(3000.0, "EPARGNE");

        graphQlTester.document("{ totalSolde { count sum average } }")
                .execute()
                .path("totalSolde.count").entity(Integer.class).isEqualTo(2)
                .path("totalSolde.sum").entity(Double.class).isEqualTo(4000.0)
                .path("totalSolde.average").entity(Double.class).isEqualTo(2000.0);
    }

    @Test
    void addTransaction_metAJourLeSoldeCalcule() {
        Long id = creerCompte(1000.0, "COURANT");

        graphQlTester.document("""
                        mutation($id: ID) {
                            addTransaction(transaction: {compteId: $id, montant: 500.0, date: "2024-11-19", type: DEPOT, libelle: "Salaire"}) {
                                montant
                                type
                                libelle
                            }
                        }
                        """)
                .variable("id", id)
                .execute()
                .path("addTransaction.type").entity(String.class).isEqualTo("DEPOT")
                .path("addTransaction.libelle").entity(String.class).isEqualTo("Salaire");

        graphQlTester.document("query($id: ID) { compteById(id: $id) { soldeCalcule nombreTransactions } }")
                .variable("id", id)
                .execute()
                .path("compteById.soldeCalcule").entity(Double.class).isEqualTo(1500.0)
                .path("compteById.nombreTransactions").entity(Integer.class).isEqualTo(1);
    }

    @Test
    void addTransaction_refuseUnRetraitSuperieurAuSolde() {
        Long id = creerCompte(100.0, "COURANT");

        graphQlTester.document("""
                        mutation($id: ID) {
                            addTransaction(transaction: {compteId: $id, montant: 500.0, date: "2024-11-19", type: RETRAIT}) { id }
                        }
                        """)
                .variable("id", id)
                .execute()
                .errors()
                .expect(error -> error.getMessage().contains("Solde insuffisant"))
                .verify();

        assertThat(transactionRepository.count()).isZero();
    }

    @Test
    void compteTransactions_retourneLesMouvementsDuCompte() {
        Long id = creerCompte(1000.0, "COURANT");
        graphQlTester.document("""
                        mutation($id: ID) {
                            addTransaction(transaction: {compteId: $id, montant: 250.0, date: "2024-11-19", type: DEPOT}) { id }
                        }
                        """)
                .variable("id", id)
                .execute()
                .path("addTransaction.id").entity(String.class).satisfies(valeur -> assertThat(valeur).isNotBlank());

        graphQlTester.document("query($id: ID) { compteTransactions(id: $id) { montant type } }")
                .variable("id", id)
                .execute()
                .path("compteTransactions").entityList(Object.class).hasSize(1);
    }

    @Test
    void transactionStats_agregeDepotsEtRetraits() {
        Long premier = creerCompte(1000.0, "COURANT");
        Long second = creerCompte(500.0, "EPARGNE");
        graphQlTester.document("""
                        mutation($a: ID, $b: ID) {
                            depot: addTransaction(transaction: {compteId: $a, montant: 300.0, date: "2024-11-19", type: DEPOT}) { id }
                            retrait: addTransaction(transaction: {compteId: $b, montant: 100.0, date: "2024-11-19", type: RETRAIT}) { id }
                        }
                        """)
                .variable("a", premier)
                .variable("b", second)
                .execute()
                .path("depot.id").entity(String.class).satisfies(valeur -> assertThat(valeur).isNotBlank());

        graphQlTester.document("{ transactionStats { count sumDepots sumRetraits balance } }")
                .execute()
                .path("transactionStats.count").entity(Integer.class).isEqualTo(2)
                .path("transactionStats.sumDepots").entity(Double.class).isEqualTo(300.0)
                .path("transactionStats.sumRetraits").entity(Double.class).isEqualTo(100.0)
                .path("transactionStats.balance").entity(Double.class).isEqualTo(200.0);
    }

    @Test
    void virement_debiteEtCrediteLesDeuxComptes() {
        Long source = creerCompte(1000.0, "COURANT");
        Long destination = creerCompte(200.0, "EPARGNE");

        graphQlTester.document("""
                        mutation($s: ID, $d: ID) {
                            virement(virement: {sourceId: $s, destinationId: $d, montant: 300.0, libelle: "Loyer"}) {
                                montant
                                soldeSource
                                soldeDestination
                            }
                        }
                        """)
                .variable("s", source)
                .variable("d", destination)
                .execute()
                .path("virement.soldeSource").entity(Double.class).isEqualTo(700.0)
                .path("virement.soldeDestination").entity(Double.class).isEqualTo(500.0);

        assertThat(transactionRepository.count()).isEqualTo(2);
    }

    @Test
    void virement_refuseUnSoldeInsuffisantSansRienModifier() {
        Long source = creerCompte(100.0, "COURANT");
        Long destination = creerCompte(200.0, "EPARGNE");

        graphQlTester.document("""
                        mutation($s: ID, $d: ID) {
                            virement(virement: {sourceId: $s, destinationId: $d, montant: 5000.0}) { montant }
                        }
                        """)
                .variable("s", source)
                .variable("d", destination)
                .execute()
                .errors()
                .expect(error -> error.getMessage().contains("Solde insuffisant"))
                .verify();

        assertThat(transactionRepository.count()).isZero();
    }

    @Test
    void releve_rendLeSoldeInitialEtLesMouvements() {
        Long id = creerCompte(1000.0, "COURANT");
        graphQlTester.document("""
                        mutation($id: ID) {
                            addTransaction(transaction: {compteId: $id, montant: 400.0, date: "2024-11-19", type: DEPOT}) { id }
                        }
                        """)
                .variable("id", id)
                .execute()
                .path("addTransaction.id").entity(String.class).satisfies(valeur -> assertThat(valeur).isNotBlank());

        graphQlTester.document("query($id: ID) { releve(compteId: $id) { soldeInitial soldeCalcule transactions { montant type } } }")
                .variable("id", id)
                .execute()
                .path("releve.soldeInitial").entity(Double.class).isEqualTo(1000.0)
                .path("releve.soldeCalcule").entity(Double.class).isEqualTo(1400.0)
                .path("releve.transactions").entityList(Object.class).hasSize(1);
    }

    @Test
    void deleteCompte_refuseSiDesTransactionsExistent() {
        Long id = creerCompte(1000.0, "COURANT");
        graphQlTester.document("""
                        mutation($id: ID) {
                            addTransaction(transaction: {compteId: $id, montant: 100.0, date: "2024-11-19", type: DEPOT}) { id }
                        }
                        """)
                .variable("id", id)
                .execute()
                .path("addTransaction.id").entity(String.class).satisfies(valeur -> assertThat(valeur).isNotBlank());

        graphQlTester.document("mutation($id: ID) { deleteCompte(id: $id) }")
                .variable("id", id)
                .execute()
                .errors()
                .expect(error -> error.getMessage().contains("suppression refusee"))
                .verify();

        assertThat(compteRepository.existsById(id)).isTrue();
    }

    @Test
    void deleteCompte_supprimeUnCompteSansTransaction() {
        Long id = creerCompte(1000.0, "COURANT");

        graphQlTester.document("mutation($id: ID) { deleteCompte(id: $id) }")
                .variable("id", id)
                .execute()
                .path("deleteCompte").entity(Boolean.class).isEqualTo(true);

        assertThat(compteRepository.existsById(id)).isFalse();
    }

    @Test
    void allTransactions_exposeLeCompteSansErreurDeChargement() {
        Long id = creerCompte(1000.0, "COURANT");
        graphQlTester.document("""
                        mutation($id: ID) {
                            addTransaction(transaction: {compteId: $id, montant: 100.0, date: "2024-11-19", type: DEPOT}) { id }
                        }
                        """)
                .variable("id", id)
                .execute()
                .path("addTransaction.id").entity(String.class).satisfies(valeur -> assertThat(valeur).isNotBlank());

        graphQlTester.document("{ allTransactions { id montant compte { id solde type } } }")
                .execute()
                .path("allTransactions").entityList(Object.class).hasSize(1)
                .path("allTransactions[0].compte.solde").entity(Double.class).isEqualTo(1000.0)
                .path("allTransactions[0].compte.type").entity(String.class).isEqualTo("COURANT");
    }

    @Test
    void saveCompte_refuseUnSoldeNegatif() {
        graphQlTester.document("""
                        mutation {
                            saveCompte(compte: {solde: -50.0, dateCreation: "2024-11-18", type: COURANT}) { id }
                        }
                        """)
                .execute()
                .errors()
                .expect(error -> error.getMessage().contains("negatif"))
                .verify();

        assertThat(compteRepository.count()).isZero();
    }
}
