package com.rtlservice.backend_java_dev_test;

import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/**
 * @author Zaitsev Alexei (az) / alexei.zaycev@rtlservice.com
 */
public interface Scheduler {

    CompletionStage<Double> calc(
            short key,
            long ts,
            Map<String, Object> data);

    void shutdownAndWait(
            long timeout,
            TimeUnit unit)
            throws InterruptedException;

}
