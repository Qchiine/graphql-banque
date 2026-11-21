package com.example.banqueservice.graphql.exception;

import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.stereotype.Component;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

/**
 * Traduction des exceptions metier en erreurs GraphQL classees.
 *
 * Le message est celui redige pour le client ; la cause technique reste dans
 * les logs du serveur. Retourner null laisse GraphQL produire son erreur par
 * defaut pour les exceptions non prevues.
 */
@Component
@Slf4j
public class GraphQLExceptionHandler extends DataFetcherExceptionResolverAdapter {

    @Override
    protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
        if (ex instanceof CompteIntrouvableException introuvable) {
            log.warn("Compte introuvable : {}", introuvable.getIdentifiant());
            return erreur(introuvable.getMessage(), ErrorType.NOT_FOUND);
        }
        if (ex instanceof OperationRefuseeException refusee) {
            log.warn("Operation refusee : {}", refusee.getMessage());
            return erreur(refusee.getMessage(), ErrorType.BAD_REQUEST);
        }
        if (ex instanceof ConstraintViolationException violation) {
            log.warn("Requete invalide : {}", violation.getMessage());
            return erreur(violation.getMessage(), ErrorType.BAD_REQUEST);
        }
        if (ex instanceof IllegalArgumentException argument) {
            log.warn("Argument invalide : {}", argument.getMessage());
            return erreur(argument.getMessage(), ErrorType.BAD_REQUEST);
        }
        return null;
    }

    private GraphQLError erreur(String message, ErrorType type) {
        return GraphqlErrorBuilder.newError()
                .message(message)
                .errorType(type)
                .build();
    }
}