package com.rtlservice.backend_java_dev_test;

import java.util.Map;

/**
 * @author Zaitsev Alexei (az) / alexei.zaycev@rtlservice.com
 */
public interface Calculator {

    public static final String DATA__PROP__ID       = "id";
    public static final String DATA__PROP__RND      = "rnd";

    Double calc(
            short key,
            long ts,
            Map<String, Object> data)
            throws Exception;

}
