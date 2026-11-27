package tn.iit.blood.gateway;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;
import tn.iit.blood.gateway.filter.RequestIdFilter;

import java.util.ArrayList;
import java.util.List;

/**
 * Capture temporaire des logs d'une classe pour verifier le MDC associe a
 * chaque evenement. L'appender est retire a la fermeture : aucun effet sur les
 * autres tests.
 */
final class MdcLogCapture implements AutoCloseable {

    private final Logger logger;
    private final Level previousLevel;
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    MdcLogCapture(Class<?> loggerOwner) {
        this.logger = (Logger) LoggerFactory.getLogger(loggerOwner);
        this.previousLevel = logger.getLevel();
        logger.setLevel(Level.INFO);
        appender.start();
        logger.addAppender(appender);
    }

    List<ILoggingEvent> events() {
        return new ArrayList<>(appender.list);
    }

    /** Extrait l'identifiant ecrit par RequestIdFilter dans son message. */
    static String messageRequestId(ILoggingEvent event) {
        String message = event.getFormattedMessage();
        String marker = "(requestId=";
        int start = message.lastIndexOf(marker);
        if (start < 0) {
            return null;
        }
        start += marker.length();
        int end = message.indexOf(')', start);
        return end < 0 ? null : message.substring(start, end);
    }

    /** Identifiant present dans le MDC de l'evenement de log. */
    static String mdcRequestId(ILoggingEvent event) {
        return event.getMDCPropertyMap().get(RequestIdFilter.MDC_KEY);
    }

    @Override
    public void close() {
        logger.detachAppender(appender);
        appender.stop();
        logger.setLevel(previousLevel);
    }
}
