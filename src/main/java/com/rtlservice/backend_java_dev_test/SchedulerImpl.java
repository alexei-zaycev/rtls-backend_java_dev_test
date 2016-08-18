package com.rtlservice.backend_java_dev_test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Очередность пакетов фиксируется внутри {@link SchedulerImpl#calc} на уровне {@link AtomicReference#getAndUpdate}. Если же нужно именно на уровне {@link SchedulerImpl#calc},<br>
 * то следует либо снаружи обеспечить синхронизацию вызова {@link SchedulerImpl#calc} либо переопределить в потомке метод {@link SchedulerImpl#calc}, добавив synchronized<br>
 * <br>
 * например:<br>
 * <pre>{@code
 *
 * Scheduler scheduler = new SchedulerImpl(threadsCount, calculatorInstance) {
 *
 *    @literal @Override
 *     public synchronized CompletionStage<Double> calc(
 *             short key,
 *             long ts,
 *             Map<String, Object> data) {
 *
 *         return super.calc(key, ts, data);
 *     }
 * };
 * }</pre>
 *
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

    private static final ConcurrentMap<Short, AtomicReference<Map.Entry<Long, CompletableFuture<?>>>> _TASKS
            = new ConcurrentHashMap<>();

    private static final Function<Short, AtomicReference<Map.Entry<Long, CompletableFuture<?>>>> _TASKS__GENERATOR = key ->
            new AtomicReference<>(
                    new HashMap.SimpleImmutableEntry<>(
                            0L,
                            CompletableFuture.completedFuture(null)));

    protected final ConcurrentMap<Short, AtomicReference<Map.Entry<Long, CompletableFuture<?>>>> getTasks() {
        return _TASKS;
    }

    private final AtomicLong calcSuccess = new AtomicLong(0L);
    private final AtomicLong calcFailed = new AtomicLong(0L);

    @Override
    public CompletionStage<Double> calc(
            short key,
            long ts,
            Map<String, Object> data) {

        CompletableFuture<Double> result = new CompletableFuture<>();

        result.whenComplete((res, ex) -> {
            if (ex == null) {
                calcSuccess.incrementAndGet();
            } else {
                calcFailed.incrementAndGet();
            }
        });

        Map.Entry<Long, CompletableFuture<?>> last
                = getTasks()
                .computeIfAbsent(key, _TASKS__GENERATOR)
                .getAndUpdate(prv -> new HashMap.SimpleImmutableEntry<>(
                        prv.getKey() + 1L,
                        result));

        long lastId = last.getKey();
        CompletableFuture<?> lastTask = last.getValue();

        long newId = lastId + 1L;

        if (getLogger().isLoggable(Level.FINE)) {
            getLogger().fine(
                    String.format("%s: schedule %s",
                            _getInstName(),
                            Main._packetToString(
                                    newId,
                                    key,
                                    ts,
                                    data)));
        }

        lastTask.whenCompleteAsync((_prv, ex) -> {

                    Map<String, Object> _data = null;

                    try {

                        _data = new HashMap<>(data);
                        _data.put(Calculator.DATA__PROP__ID, newId);

                        result.complete(
                                getCalculator().calc(
                                        key,
                                        ts,
                                        _data));

                    } catch (Throwable e) {

                        getLogger().log(
                                Level.WARNING,
                                String.format("%s: %s: FAILED",
                                        _getInstName(),
                                        _data != null
                                                ? Main._packetToString(
                                                        newId,
                                                        key,
                                                        ts,
                                                        _data)
                                                : null),
                                ex);

                        result.completeExceptionally(e);
                    }
                },
                getExecutorService());

        return result;
    }

    @Override
    public void shutdownAndWait(
            long timeout,
            TimeUnit unit)
            throws InterruptedException {

        getExecutorService().shutdown();
        getExecutorService().awaitTermination(timeout, unit);

        getLogger().log(
                calcFailed.get() > 0 ? Level.WARNING : Level.INFO,
                String.format("%s: STOPPED (success=%d, failed=%d)",
                        _getInstName(),
                        calcSuccess.get(),
                        calcFailed.get()));
    }

}
