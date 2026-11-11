package com.service_demandes.exception;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.service_demandes.dto.ErreurApi;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Arrays;
import java.util.stream.Collectors;

/** Toutes les erreurs de l'API ont le meme JSON : timestamp, status, error, message, path. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Champ manquant, quantite inferieure a 1, reference patient invalide, compensation hors de 2 a 3... */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErreurApi> validation(MethodArgumentNotValidException ex, HttpServletRequest requete) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .sorted()
                .collect(Collectors.joining("; "));
        return reponse(HttpStatus.BAD_REQUEST, message, requete);
    }

    /** JSON mal forme ou valeur d'enum inconnue (groupe, composant, motif, urgence). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErreurApi> corpsIllisible(HttpMessageNotReadableException ex, HttpServletRequest requete) {
        String message = "Corps de la requête invalide ou illisible";
        if (ex.getCause() instanceof InvalidFormatException invalide
                && invalide.getTargetType() != null
                && invalide.getTargetType().isEnum()) {
            message = "Valeur '%s' invalide pour le champ '%s'. Valeurs possibles : %s".formatted(
                    invalide.getValue(),
                    nomDuChamp(invalide),
                    Arrays.toString(invalide.getTargetType().getEnumConstants()));
        }
        return reponse(HttpStatus.BAD_REQUEST, message, requete);
    }

    /** Erreurs HTTP standard de Spring (404, 405, 415...) gardent leur code mais prennent notre format. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErreurApi> autre(Exception ex, HttpServletRequest requete) {
        if (ex instanceof ErrorResponse erreurHttp) {
            HttpStatus status = HttpStatus.valueOf(erreurHttp.getStatusCode().value());
            String detail = erreurHttp.getBody().getDetail();
            return reponse(status, detail != null ? detail : status.getReasonPhrase(), requete);
        }
        log.error("Erreur inattendue sur {}", requete.getRequestURI(), ex);
        return reponse(HttpStatus.INTERNAL_SERVER_ERROR, "Erreur interne du serveur", requete);
    }

    private static String nomDuChamp(JsonMappingException ex) {
        if (ex.getPath().isEmpty()) {
            return "?";
        }
        return ex.getPath().get(ex.getPath().size() - 1).getFieldName();
    }

    private static ResponseEntity<ErreurApi> reponse(HttpStatus status, String message, HttpServletRequest requete) {
        ErreurApi corps = new ErreurApi(Instant.now(), status.value(), status.getReasonPhrase(), message, requete.getRequestURI());
        return ResponseEntity.status(status).body(corps);
    }
}
