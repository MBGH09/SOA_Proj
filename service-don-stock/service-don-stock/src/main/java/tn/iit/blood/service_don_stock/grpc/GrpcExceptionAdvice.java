package tn.iit.blood.service_don_stock.grpc;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import net.devh.boot.grpc.server.advice.GrpcAdvice;
import net.devh.boot.grpc.server.advice.GrpcExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * S4-11 – Centralised gRPC status mapping.
 * Maps checked exceptions to gRPC Status codes in one place.
 */
@GrpcAdvice
public class GrpcExceptionAdvice {

    private static final Logger log = LoggerFactory.getLogger(GrpcExceptionAdvice.class);

    @GrpcExceptionHandler(IllegalArgumentException.class)
    public StatusRuntimeException handleIllegalArgument(IllegalArgumentException e) {
        log.debug("INVALID_ARGUMENT: {}", e.getMessage());
        return Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException();
    }

    @GrpcExceptionHandler(jakarta.persistence.EntityNotFoundException.class)
    public StatusRuntimeException handleNotFound(jakarta.persistence.EntityNotFoundException e) {
        log.debug("NOT_FOUND: {}", e.getMessage());
        return Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException();
    }

    @GrpcExceptionHandler(IllegalStateException.class)
    public StatusRuntimeException handleIllegalState(IllegalStateException e) {
        log.debug("FAILED_PRECONDITION: {}", e.getMessage());
        return Status.FAILED_PRECONDITION.withDescription(e.getMessage()).asRuntimeException();
    }

    @GrpcExceptionHandler(java.net.ConnectException.class)
    public StatusRuntimeException handleConnect(java.net.ConnectException e) {
        log.warn("UNAVAILABLE: {}", e.getMessage());
        return Status.UNAVAILABLE.withDescription(e.getMessage()).asRuntimeException();
    }

    @GrpcExceptionHandler(java.util.concurrent.TimeoutException.class)
    public StatusRuntimeException handleTimeout(java.util.concurrent.TimeoutException e) {
        log.warn("DEADLINE_EXCEEDED: {}", e.getMessage());
        return Status.DEADLINE_EXCEEDED.withDescription(e.getMessage()).asRuntimeException();
    }

    @GrpcExceptionHandler(Exception.class)
    public StatusRuntimeException handleGeneric(Exception e) {
        log.error("INTERNAL gRPC error", e);
        return Status.INTERNAL.withDescription("Internal server error").asRuntimeException();
    }
}
