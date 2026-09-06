package ru.payflow.payment.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import ru.payflow.payment.account.dto.AccountResponse;

/**
 * Кэш ответа о счёте, cache-aside: читающий кладёт, пишущий выбрасывает. Сам по себе кэш здесь
 * спорный — счёт это горячая запись, — и почему он всё-таки заведён и когда его снять, написано
 * в README.
 */
@Component
public class BalanceCache {

    private static final Logger LOG = LoggerFactory.getLogger(BalanceCache.class);
    private static final String KEY_PREFIX = "account:balance:";

    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final Duration ttl;

    public BalanceCache(
            StringRedisTemplate redis, ObjectMapper json, @Value("${payflow.cache.balance-ttl:PT60S}") Duration ttl) {
        this.redis = redis;
        this.json = json;
        this.ttl = ttl;
    }

    public Optional<AccountResponse> find(UUID accountId) {
        String stored = redis.opsForValue().get(key(accountId));
        if (stored == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(json.readValue(stored, AccountResponse.class));
        } catch (JsonProcessingException e) {
            // Формат ответа мог измениться с прошлого релиза: это не повод падать на чтении счёта.
            LOG.warn("Кэш счёта {} не разобран, читаем из базы", accountId, e);
            redis.delete(key(accountId));
            return Optional.empty();
        }
    }

    public void put(AccountResponse account) {
        try {
            redis.opsForValue().set(key(account.id()), json.writeValueAsString(account), ttl);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Ответ о счёте не сериализуется: " + account.id(), e);
        }
    }

    /**
     * Выбрасывает запись после коммита, а не сразу. Если снести её внутри транзакции, читающий
     * успеет промахнуться, сходить в базу за ещё не изменённым балансом и положить в кэш старое
     * значение — уже после нашей инвалидации.
     */
    public void evictAfterCommit(UUID accountId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            evict(accountId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                // И на откате тоже: запись могла попасть в кэш во время транзакции.
                evict(accountId);
            }
        });
    }

    private void evict(UUID accountId) {
        redis.delete(key(accountId));
    }

    private static String key(UUID accountId) {
        return KEY_PREFIX + accountId;
    }
}
