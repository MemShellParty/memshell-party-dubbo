package com.reajason.javaweb.integration.memshell.dubbo;

import com.reajason.javaweb.memshell.server.dubbo.DubboShellType;

import java.util.List;

final class DubboProviderScenario {
    private final String name;
    private final String clientKind;
    private final String providerJarProperty;
    private final String imageName;
    private final int loaderPort;
    private final int targetJdkVersion;
    private final List<DubboProtocolTarget> commandTargets;

    DubboProviderScenario(String name, String clientKind, String providerJarProperty, String imageName,
                          int loaderPort, int targetJdkVersion, List<DubboProtocolTarget> commandTargets) {
        this.name = name;
        this.clientKind = clientKind;
        this.providerJarProperty = providerJarProperty;
        this.imageName = imageName;
        this.loaderPort = loaderPort;
        this.targetJdkVersion = targetJdkVersion;
        this.commandTargets = commandTargets;
    }

    String name() {
        return name;
    }

    String clientKind() {
        return clientKind;
    }

    String providerJarProperty() {
        return providerJarProperty;
    }

    String imageName() {
        return imageName;
    }

    int loaderPort() {
        return loaderPort;
    }

    int targetJdkVersion() {
        return targetJdkVersion;
    }

    List<DubboProtocolTarget> commandTargets() {
        return commandTargets;
    }

    /**
     * The exporter lives in the DubboProtocol exporter map, so only the dubbo protocol port
     * carries it. Hessian and tri have no exporter map and are excluded on purpose.
     */
    DubboProtocolTarget dubboTarget() {
        for (DubboProtocolTarget target : commandTargets) {
            if ("dubbo".equals(target.protocol())) {
                return target;
            }
        }
        throw new IllegalStateException(name + " has no dubbo protocol target");
    }

    String shellType() {
        if ("alibaba".equals(clientKind)) {
            return DubboShellType.ALIBABA_DUBBO_SERVICE;
        }
        return DubboShellType.APACHE_DUBBO_SERVICE;
    }

    String exporterShellType() {
        if ("alibaba".equals(clientKind)) {
            return DubboShellType.ALIBABA_DUBBO_EXPORTER;
        }
        return DubboShellType.APACHE_DUBBO_EXPORTER;
    }

    @Override
    public String toString() {
        return name;
    }
}
