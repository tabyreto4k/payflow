package ru.payflow.notification.idempotency;

import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Отсечка повторной доставки без своей БД: ключ ставится в Redis атомарным {@code SET NX EX} [Р6].
 *
 * <p>Гарантия слабее, чем таблица {@code processed_events} у остальных сервисов: потеря Redis
 * означает, что письмо может уйти второй раз. Для уведомления это приемлемо — деньги здесь
 * не движутся, — и записано в README честным абзацем.
 */
@Component
public class EventDeduplicator {

    private final StringRedisTemplate redis;
    private final Duration ttl;
    private final String keyPrefix;

    public EventDeduplicator(
            StringRedisTemplate redis,
            @Value("${payflow.dedup.ttl:PT24H}") Duration ttl,
            // Префикс — настройка, а не константа: одну и ту же базу Redis может делить не один
            // потребитель, и чужие отметки не должны выглядеть своими.
            @Value("${payflow.dedup.key-prefix:notification:event:}") String keyPrefix) {
        this.redis = redis;
        this.ttl = ttl;
        this.keyPrefix = keyPrefix;
    }

    /** {@code false} — событие уже обработано, письмо по нему уходило. */
    public boolean tryMarkProcessed(UUID eventId) {
        return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key(eventId), "1", ttl));
    }

    /**
     * Снять отметку. Нужна ровно на одном пути: письмо не ушло, сообщение поедет на повтор — и
     * без снятия повтор был бы принят за дубль, а уведомление потерялось бы молча.
     */
    public void forget(UUID eventId) {
        redis.delete(key(eventId));
    }

    private String key(UUID eventId) {
        return keyPrefix + eventId;
    }
}
