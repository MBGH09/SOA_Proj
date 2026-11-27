package tn.iit.blood.gateway.error;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Construction du corps JSON d'erreur commun (S2-06 et S2-07).
 *
 * <p>Cette classe garantit que les erreurs levees par le gateway et les
 * erreurs d'authentification produites par Spring Security utilisent
 * exactement le meme contrat : {@code timestamp}, {@code status},
 * {@code error}, {@code message}, {@code path}, puis {@code requestId}
 * lorsqu'il est disponible.</p>
 */
public final class JsonErrorPayload {

    private JsonErrorPayload() {
    }

    /** Construit le corps ordonne du contrat d'erreur. */
    public static Map<String, Object> create(HttpStatus status,
                                             String message,
                                             String path,
                                             String requestId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", OffsetDateTime.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        body.put("path", path);
        if (requestId != null && !requestId.isBlank()) {
            body.put("requestId", requestId);
        }
        return body;
    }

    /** Serialise le corps ; retourne un JSON minimal si Jackson echoue. */
    public static byte[] serialize(ObjectMapper objectMapper,
                                   Map<String, Object> body,
                                   HttpStatus status) {
        try {
            return objectMapper.writeValueAsBytes(body);
        }
        catch (JsonProcessingException exception) {
            String fallback = "{\"status\":" + status.value()
                    + ",\"error\":\"" + status.getReasonPhrase() + "\"}";
            return fallback.getBytes(StandardCharsets.UTF_8);
        }
    }
}
