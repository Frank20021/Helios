package io.helios.coordinator;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

public final class Retries {
    private Retries() {
    }

    public static <T> T call(int max, Duration base, Supplier<T> fn) {
        if (max < 0) {
            max = 0;
        }
        if (base == null || base.isZero() || base.isNegative()) {
            base = Duration.ofMillis(20);
        }
        RuntimeException last = null;
        for (int attempt = 0; attempt <= max; attempt++) {
            try {
                return fn.get();
            } catch (RuntimeException e) {
                last = e;
                if (!retryable(e) || attempt == max) {
                    throw e;
                }
                long delay = base.toMillis() * (1L << attempt);
                long jitter = ThreadLocalRandom.current().nextLong(delay / 5 + 1);
                try {
                    Thread.sleep(delay + jitter);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
        throw last;
    }

    public static void run(int max, Duration base, Runnable fn) {
        call(max, base, () -> {
            fn.run();
            return null;
        });
    }

    static boolean retryable(Throwable err) {
        return err != null;
    }
}
