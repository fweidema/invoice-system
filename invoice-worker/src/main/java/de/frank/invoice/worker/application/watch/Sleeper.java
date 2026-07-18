package de.frank.invoice.worker.application.watch;

import java.time.Duration;

/**
 * Abstraction for sleeping between polling attempts.
 */
@FunctionalInterface
public interface Sleeper {

    /**
     * Sleeps for the given duration.
     *
     * @param duration sleep duration
     * @throws InterruptedException if the current thread is interrupted
     */
    void sleep(Duration duration) throws InterruptedException;

    /**
     * Returns a sleeper backed by Thread.sleep.
     *
     * @return system sleeper
     */
    static Sleeper system() {
        return duration -> Thread.sleep(duration.toMillis());
    }
}
