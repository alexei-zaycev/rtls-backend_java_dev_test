package com.rtlservice.backend_java_dev_test;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Zaitsev Alexei (az) / alexei.zaycev@rtlservice.com
 */
public class Generator
        implements Runnable {

    public static final short KEY__MIN_VALUE                = 1;
    public static final short KEY__MAX_VALUE                = 1000;

    public static final long GEN_DELAY__MIN_VALUE           = 5L;
    public static final long GEN_DELAY__MAX_VALUE           = 50L;

    private final int packetTotalCount;
    private final Scheduler scheduler;

    public Generator(
            int packetTotalCount,
            Scheduler scheduler) {

        this.packetTotalCount = packetTotalCount;
        this.scheduler = scheduler;
    }

    protected final Logger getLogger() {
        return Logger.getLogger(this.getClass().getName());
    }

    protected int getPacketTotalCount() {
        return packetTotalCount;
    }

    protected Scheduler getScheduler() {
        return scheduler;
    }

    protected short generateKey() {

        return (short) ThreadLocalRandom.current().nextInt(
                KEY__MIN_VALUE,
                KEY__MAX_VALUE);
    }

    protected Map<String, Object> generateData() {

        return Collections.singletonMap(
                Calculator.DATA__PROP__RND,
                ThreadLocalRandom.current().nextDouble());
    }

    @Override
    public void run() {

        String _instName = Main._instToString("GENERATOR", this);

        try {

            getLogger().info(
                    String.format("%s: STARTED",
                            _instName));

            AtomicLong calcSuccess = new AtomicLong(0L);
            AtomicLong calcFailed = new AtomicLong(0L);

            CompletableFuture<Double>[] results
                    = new CompletableFuture[getPacketTotalCount()];

            for (int i = 0; i < getPacketTotalCount(); i++) {

                long delay = ThreadLocalRandom.current().nextLong(
                        GEN_DELAY__MIN_VALUE,
                        GEN_DELAY__MAX_VALUE);

                if (getLogger().isLoggable(Level.FINE)) {
                    getLogger().fine(
                            String.format("%s: sleep %dms",
                                    _instName,
                                    delay));
                }

                Thread.sleep(delay);

                long now = System.currentTimeMillis();
                short key = generateKey();
                Map<String, Object> data = generateData();

                getLogger().info(
                        String.format("%s: generate packet: ts=%d, key=%d, data=%s",
                                _instName,
                                now,
                                key,
                                data));

                CompletionStage<Double> result = getScheduler().calc(
                        key,
                        now,
                        data);

                result.whenComplete((res, ex) -> {
                   if (ex == null) {
                       calcSuccess.incrementAndGet();
                   } else {
                       calcFailed.incrementAndGet();
                   }
                });

                results[i] = result.toCompletableFuture();
            }

            if (getLogger().isLoggable(Level.FINE)) {
                getLogger().fine(
                        String.format("%s: WAITING",
                                _instName));
            }

            CompletableFuture.allOf(results)
                    .join();
//                    .get(1, TimeUnit.MINUTES);

            getLogger().log(
                    calcFailed.get() > 0 || calcSuccess.get() + calcFailed.get() != getPacketTotalCount()
                            ? Level.WARNING
                            : Level.INFO,
                    String.format("%s: STOPPED (success=%d, failed=%d)",
                            _instName,
                            calcSuccess.get(),
                            calcFailed.get()));

        } catch (Throwable ex) {
            getLogger().log(
                    Level.SEVERE,
                    String.format("%s: FAILED",
                            _instName),
                    ex);
        }
    }

}
