package com.rtlservice.backend_java_dev_test;

import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Main {

    private static final int GENERATORS_COUNT                   = 10;
    private static final int PACKETS_PER_GENERATOR              = 1000;

    private static final int CALCULATOR_CONCURRENCY_LEVEL       = 10;

    public static String _instToString(
            String name,
            Object inst) {

        return String.format("%s[%s@%s]",
                name,
                inst.getClass().getSimpleName(),
                System.identityHashCode(inst));
    }

    public static String _packetToString(
            long id,
            short key,
            long ts,
            Map<String, Object> data) {

        return String.format("PACKET #%d {ts=%d, key=%d, data=%s}",
                id,
                ts,
                key,
                data);
    }

    public static void main(String[] args) {
        try {

            Calculator calculator = new CalculatorImpl();

            Scheduler scheduler = new SchedulerImpl(CALCULATOR_CONCURRENCY_LEVEL, calculator);

            ExecutorService generatorsPool = Executors.newCachedThreadPool();
            for (int i = 0; i < GENERATORS_COUNT; i++) {
                generatorsPool.submit(new Generator(PACKETS_PER_GENERATOR, scheduler));
            }

            generatorsPool.shutdown();
            generatorsPool.awaitTermination(1, TimeUnit.MINUTES);

            scheduler.shutdownAndWait(10, TimeUnit.SECONDS);

        } catch (InterruptedException ex) {
            ex.printStackTrace();
        }
    }

}