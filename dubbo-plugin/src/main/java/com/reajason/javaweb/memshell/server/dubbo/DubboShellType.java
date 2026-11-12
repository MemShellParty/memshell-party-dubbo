package com.reajason.javaweb.memshell.server.dubbo;

/**
 * Dubbo vocabulary owned by this plugin. The host's shared string tables no longer carry
 * Dubbo entries, so the server, its injectors and the tests read the names from here.
 *
 * @author ReaJason
 */
public final class DubboShellType {

    /**
     * Server registration name. The target framework is still Dubbo; only the names moved.
     */
    public static final String SERVER = "Dubbo";

    public static final String DUBBO_SERVICE = "DubboService";
    public static final String APACHE_DUBBO_SERVICE = "Apache" + DUBBO_SERVICE;
    public static final String ALIBABA_DUBBO_SERVICE = "Alibaba" + DUBBO_SERVICE;

    private DubboShellType() {
    }
}
