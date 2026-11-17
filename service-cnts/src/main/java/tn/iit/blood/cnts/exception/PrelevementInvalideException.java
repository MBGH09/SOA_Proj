package tn.iit.blood.cnts.exception;

/**
 * Thrown when a prelevement is invalid (R8: volume ≠ 450 ml, unknown group, future date).
 * @SoapFault annotation is added in S5-07.
 */
public class PrelevementInvalideException extends RuntimeException {
    public PrelevementInvalideException(String message) {
        super(message);
    }
}
