package com.rtlservice.backend_java_dev_test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Очередность пакетов фиксируется внутри {@link SchedulerImpl#calc} на уровне {@link ConcurrentMap#compute}. Если же нужно именно на уровне {@link SchedulerImpl#calc},<br>
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

    private static final AtomicLong _ID_COUNTER = new AtomicLong(1L);

    protected final long generatePacketID() {
        return _ID_COUNTER.getAndIncrement();
    }

    private static final ConcurrentMap<Short, AtomicReference<CompletableFuture<?>>> _TASKS
            = new ConcurrentHashMap<>();

    protected final ConcurrentMap<Short, AtomicReference<CompletableFuture<?>>> getTasks() {
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

        getTasks().compute(
                key,
                (_key, task) -> {

                    long id = generatePacketID();
                    Map<String, Object> _data = new HashMap<>(data);
                    _data.put(Calculator.DATA__PROP__ID, id);

                    if (getLogger().isLoggable(Level.FINE)) {

                        String _packetAsString = Main._packetToString(
                                id,
                                key,
                                ts,
                                _data);

                        getLogger().fine(
                                String.format("%s: schedule %s",
                                        _getInstName(),
                                        _packetAsString));
                    }

                    if (task == null) {
                        task = new AtomicReference<>(CompletableFuture.completedFuture(null));
                    }

                    task.getAndSet(result)
                            .whenCompleteAsync(
                                    (prv, ex) -> {
                                        try {
                                            result.complete(getCalculator().calc(key, ts, _data));
                                        } catch (Throwable e) {
                                            result.completeExceptionally(e);
                                        }
                                    },
                                    getExecutorService());

                    return task;
                });

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
