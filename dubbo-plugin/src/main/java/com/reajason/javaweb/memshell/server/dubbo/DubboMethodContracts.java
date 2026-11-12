package com.reajason.javaweb.memshell.server.dubbo;

import com.reajason.javaweb.memshell.obfuscation.MethodContracts;
import com.reajason.javaweb.memshell.obfuscation.MethodSignature;

import java.util.Collections;
import java.util.Set;

/**
 * Method names the Dubbo injectors call reflectively on the generated shell. Renaming them
 * would break the exported service entry point even though the obfuscator cannot see the call.
 *
 * @author ReaJason
 */
public final class DubboMethodContracts {

    /**
     * {@code handle(byte[])} is the transport entry point; {@code invoke} covers generated
     * service proxies that route through the same name.
     */
    public static final Set<MethodSignature> DUBBO_CONTRACTS = Collections.unmodifiableSet(
            MethodContracts.signatures("invoke", "handle")
    );

    private DubboMethodContracts() {
    }
}
