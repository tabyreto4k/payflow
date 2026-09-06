package ru.payflow.payment.logging;

import java.util.UUID;
import org.slf4j.MDC;

/**
 * Сквозной идентификатор запроса. Приезжает заголовком снаружи, живёт в MDC и уезжает дальше:
 * в HTTP-вызов — заголовком, в Kafka — заголовком записи. По нему один поход клиента собирается
 * из логов трёх сервисов, между которыми нет ни одного синхронного вызова.
 */
public final class CorrelationId {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    private CorrelationId() {}

    /** Текущий идентификатор или {@code null}, если работа началась не с запроса. */
    public static String current() {
        return MDC.get(MDC_KEY);
    }

    public static String orNew(String incoming) {
        return incoming == null || incoming.isBlank() ? UUID.randomUUID().toString() : incoming;
    }

    /** Выполнить работу под этим идентификатором и вернуть MDC в прежнее состояние. */
    public static void with(String correlationId, Runnable work) {
        String previous = MDC.get(MDC_KEY);
        MDC.put(MDC_KEY, orNew(correlationId));
        try {
            work.run();
        } finally {
            if (previous == null) {
                MDC.remove(MDC_KEY);
            } else {
                MDC.put(MDC_KEY, previous);
            }
        }
    }
}
