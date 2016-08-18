package com.rtlservice.backend_java_dev_test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * @author Zaitsev Alexei (az) / alexei.zaycev@rtlservice.com
 */
public class CalculatorImpl
        implements Calculator {

    public static final long TIMEOUT                        = 100L;

    public static final long CALC__MIN_VALUE                = 1L;
    public static final long CALC__MAX_VALUE                = 100L;

    public CalculatorImpl() {

        this._instName = Main._instToString("CALCULATOR", this);
    }

    protected final Logger getLogger() {
        return Logger.getLogger(this.getClass().getName());
    }

    private final String _instName;

    protected String _getInstName() {
        return _instName;
    }

    private static final ConcurrentMap<Short, Map<String, Object>> _STATES
            = new ConcurrentHashMap<>();

    protected ConcurrentMap<Short, Map<String, Object>> getStates() {
        return _STATES;
    }

    private static final ConcurrentMap<Short, Long> _HISTORY
            = new ConcurrentHashMap<>();

    protected Long checkOrder(
            short key,
            long id) {

        Long prvId = _HISTORY.put(key, id);

        if (prvId != null && id <= prvId) {
            return prvId;
        } else {
            return null;
        }
    }

    @Override
    public Double calc(
            short key,
            long ts,
            Map<String, Object> data)
            throws Exception {

        long now = System.currentTimeMillis();
        long id = (long) data.get(Calculator.DATA__PROP__ID);

        String _packetAsString = Main._packetToString(
                id,
                key,
                ts,
                data);

        Long prvId = checkOrder(key, id);
        if (prvId != null) {

            getLogger().severe(
                    String.format("%s: %s: FAILED (try calculate packet %d after %d)",
                            _getInstName(),
                            _packetAsString,
                            id,
                            prvId));

//            return null;
            System.exit(-1);
        }

        if (now - ts <= TIMEOUT) {

            long duration = ThreadLocalRandom.current().nextLong(
                    CALC__MIN_VALUE,
                    CALC__MAX_VALUE);

            Thread.sleep(duration);

            Map<String, Object> state = getStates().get(key);
            double delta = (state != null ? (double) state.get(Calculator.DATA__PROP__RND) : 0.0);

            Double result = ThreadLocalRandom.current().nextDouble() + delta;

            getStates().put(key, data);

            getLogger().info(
                    String.format("%s: %s: CALCULATED (result=%s, duration=%dms)",
                            _getInstName(),
                            _packetAsString,
                            result,
                            duration));

            return result;

        } else {

            getLogger().warning(
                    String.format("%s: %s: IGNORED (delay=%dms)",
                            _getInstName(),
                            _packetAsString,
                            now - ts));

            return null;
        }
    }

}
