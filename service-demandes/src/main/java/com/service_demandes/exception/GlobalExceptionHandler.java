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
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Gestionnaire global des erreurs.
 * Toutes les réponses d'erreur suivent le même format JSON :
 * {@code timestamp}, {@code status}, {@code error}, {@code message}, {@code path}.
 *
 * <p>Hiérarchie des codes de retour :</p>
 * <ul>
 *   <li>400 — données invalides ou règle métier non respectée</li>
 *   <li>404 — ressource introuvable</li>
 *   <li>409 — conflit métier (doublon, état incompatible)</li>
 *   <li>503 — service aval injoignable</li>
 *   <li>500 — erreur interne inattendue (loggée côté serveur)</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // -------------------------------------------------------------------------
    // 400 — Données invalides
    // -------------------------------------------------------------------------

    /**
     * Champ manquant ou contrainte de validation non respectée
     * ({@code @NotBlank}, {@code @Min}, {@code @Pattern}, etc.).
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErreurApi> surErreurValidation(
            MethodArgumentNotValidException erreurValidation, HttpServletRequest requete) {

        String messageRegroupant = erreurValidation.getBindingResult().getFieldErrors().stream()
                .map(erreurChamp -> erreurChamp.getField() + ": " + erreurChamp.getDefaultMessage())
                .sorted()
                .collect(Collectors.joining("; "));
        return construireReponse(HttpStatus.BAD_REQUEST, messageRegroupant, requete);
    }

    /**
     * Corps JSON illisible ou valeur d'énumération inconnue
     * (groupe sanguin, composant, motif, urgence).
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErreurApi> surCorpsIllisible(
            HttpMessageNotReadableException erreurLecture, HttpServletRequest requete) {

        String message = "Corps de la requête invalide ou illisible";

        if (erreurLecture.getCause() instanceof InvalidFormatException erreurFormat
                && erreurFormat.getTargetType() != null
                && erreurFormat.getTargetType().isEnum()) {
            message = "Valeur '%s' invalide pour le champ '%s'. Valeurs possibles : %s".formatted(
                    erreurFormat.getValue(),
                    extraireNomDuChamp(erreurFormat),
                    Arrays.toString(erreurFormat.getTargetType().getEnumConstants()));
        }
        return construireReponse(HttpStatus.BAD_REQUEST, message, requete);
    }

    /**
     * Paramètre de chemin ou de requête de mauvais type
     * (statut inconnu, identifiant qui n'est pas un UUID).
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErreurApi> surTypeParametreInvalide(
            MethodArgumentTypeMismatchException erreurType, HttpServletRequest requete) {

        String message = "Valeur '%s' invalide pour le paramètre '%s'"
                .formatted(erreurType.getValue(), erreurType.getName());
        if (erreurType.getRequiredType() != null && erreurType.getRequiredType().isEnum()) {
            message += ". Valeurs possibles : " + Arrays.toString(erreurType.getRequiredType().getEnumConstants());
        }
        return construireReponse(HttpStatus.BAD_REQUEST, message, requete);
    }

    @ExceptionHandler(RequeteInvalideException.class)
    public ResponseEntity<ErreurApi> surRequeteInvalide(
            RequeteInvalideException erreur, HttpServletRequest requete) {
        return construireReponse(HttpStatus.BAD_REQUEST, erreur.getMessage(), requete);
    }

    // -------------------------------------------------------------------------
    // 404 — Ressource introuvable
    // -------------------------------------------------------------------------

    @ExceptionHandler(RessourceIntrouvableException.class)
    public ResponseEntity<ErreurApi> surRessourceIntrouvable(
            RessourceIntrouvableException erreur, HttpServletRequest requete) {
        return construireReponse(HttpStatus.NOT_FOUND, erreur.getMessage(), requete);
    }

    // -------------------------------------------------------------------------
    // 409 — Conflit métier
    // -------------------------------------------------------------------------

    @ExceptionHandler(ConflitMetierException.class)
    public ResponseEntity<ErreurApi> surConflitMetier(
            ConflitMetierException erreur, HttpServletRequest requete) {
        return construireReponse(HttpStatus.CONFLICT, erreur.getMessage(), requete);
    }

    // -------------------------------------------------------------------------
    // 503 — Service aval injoignable
    // -------------------------------------------------------------------------

    /** Service-donneurs ou service-don-stock injoignable. */
    @ExceptionHandler(ServiceIndisponibleException.class)
    public ResponseEntity<ErreurApi> surServiceIndisponible(
            ServiceIndisponibleException erreur, HttpServletRequest requete) {
        return construireReponse(HttpStatus.SERVICE_UNAVAILABLE, erreur.getMessage(), requete);
    }

    // -------------------------------------------------------------------------
    // Autres erreurs Spring (404, 405, 415…) et erreurs internes (500)
    // -------------------------------------------------------------------------

    /**
     * Attrape toutes les erreurs non gérées au-dessus.
     * Les erreurs HTTP standard de Spring gardent leur code mais prennent notre format JSON.
     * Toute autre erreur retourne 500 et est loggée côté serveur.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErreurApi> surErreurNonGeree(Exception erreur, HttpServletRequest requete) {
        if (erreur instanceof ErrorResponse erreurHttpStandard) {
            HttpStatus statut = HttpStatus.valueOf(erreurHttpStandard.getStatusCode().value());
            String detail = erreurHttpStandard.getBody().getDetail();
            return construireReponse(statut, detail != null ? detail : statut.getReasonPhrase(), requete);
        }
        log.error("Erreur inattendue sur {}", requete.getRequestURI(), erreur);
        return construireReponse(HttpStatus.INTERNAL_SERVER_ERROR, "Erreur interne du serveur", requete);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String extraireNomDuChamp(JsonMappingException erreurMapping) {
        if (erreurMapping.getPath().isEmpty()) {
            return "?";
        }
        return erreurMapping.getPath().get(erreurMapping.getPath().size() - 1).getFieldName();
    }

    private static ResponseEntity<ErreurApi> construireReponse(
            HttpStatus statut, String message, HttpServletRequest requete) {
        ErreurApi corps = new ErreurApi(
                Instant.now(),
                statut.value(),
                statut.getReasonPhrase(),
                message,
                requete.getRequestURI());
        return ResponseEntity.status(statut).body(corps);
    }
}
