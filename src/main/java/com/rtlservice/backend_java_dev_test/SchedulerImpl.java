package com.rtlservice.backend_java_dev_test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Zaitsev Alexei (az) / alexei.zaycev@rtlservice.com
 */
public class SchedulerImpl
        implements Scheduler {

    private final ExecutorService executorService;
    private final Calculator calculator;

    public SchedulerImpl(
            int threadsCount,
            Calculator calculator) {

        this.executorService = Executors.newFixedThreadPool(threadsCount);
        this.calculator = calculator;

        this._instName = Main._instToString("SCHEDULER", this);

        getLogger().info(
                String.format("%s: STARTED",
                        _getInstName()));
    }

    protected final Logger getLogger() {
        return Logger.getLogger(this.getClass().getName());
    }

    protected ExecutorService getExecutorService() {
        return executorService;
    }

    protected Calculator getCalculator() {
        return calculator;
    }

    private final String _instName;

    protected String _getInstName() {
        return _instName;
    }

    private static final AtomicLong _ID_COUNTER = new AtomicLong(1L);

    protected final long generatePacketID() {
        return _ID_COUNTER.getAndIncrement();
    }

    private static final BiFunction<Short, AtomicReference<CompletableFuture<?>>, AtomicReference<CompletableFuture<?>>> _TASKS__GEN
            = (k, v) -> (v != null ? v : new AtomicReference<>(CompletableFuture.completedFuture(null)));

    private static final ConcurrentMap<Short, AtomicReference<CompletableFuture<?>>> _TASKS
            = new ConcurrentHashMap<>();

    protected final AtomicReference<CompletableFuture<?>> getLastTask(short key) {
        return _TASKS.compute(key, _TASKS__GEN);
    }

    private final AtomicLong calcSuccess = new AtomicLong(0L);
    private final AtomicLong calcFailed = new AtomicLong(0L);

    @Override
    public synchronized CompletionStage<Double> calc(
            short key,
            long ts,
            Map<String, Object> data) {

        CompletableFuture<Double> result = new CompletableFuture<>();

        long id = generatePacketID();
        Map<String, Object> _data = new HashMap<>(data);
        _data.put(Calculator.DATA__PROP__ID, id);

        String _packetAsString = Main._packetToString(
                id,
                key,
                ts,
                data);

        if (getLogger().isLoggable(Level.FINE)) {
            getLogger().info(
                    String.format("%s: schedule %s",
                            _getInstName(),
                            _packetAsString));
        }

        getLastTask(key)
                .getAndSet(result)
                .whenCompleteAsync(
                        (prv, ex) -> {
                            try {
                                result.complete(getCalculator().calc(key, ts, _data));
                            } catch (Throwable e) {
                                result.completeExceptionally(e);
                            }
                        },
                        getExecutorService());

        result.whenComplete((res, ex) -> {
            if (ex == null) {
                calcSuccess.incrementAndGet();
            } else {
                calcFailed.incrementAndGet();
            }
        });

        return result;
    }

    @Override
    public void shutdownAndWait(
            long timeout,
            TimeUnit unit)
            throws InterruptedException {

        getExecutorService().shutdown();
        getExecutorService().awaitTermination(timeout, unit);

        getLogger().info(
                String.format("%s: STOPPED (success=%d, failed=%d)",
                        _getInstName(),
                        calcSuccess.get(),
                        calcFailed.get()));
    }

}
