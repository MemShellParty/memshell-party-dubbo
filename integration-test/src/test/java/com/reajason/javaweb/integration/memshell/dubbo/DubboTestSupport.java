package com.reajason.javaweb.integration.memshell.dubbo;

import com.reajason.javaweb.memshell.MemShellGenerator;
import com.reajason.javaweb.memshell.MemShellResult;
import com.reajason.javaweb.memshell.config.InjectorConfig;
import com.reajason.javaweb.memshell.config.ObfuscationConfig;
import com.reajason.javaweb.memshell.config.ShellConfig;
import com.reajason.javaweb.memshell.config.ShellToolConfig;
import com.reajason.javaweb.memshell.server.dubbo.DubboShellType;
import com.reajason.javaweb.memshell.shelltool.command.CommandConfig;
import com.reajason.javaweb.memshell.shelltool.godzilla.GodzillaConfig;
import net.bytebuddy.jar.asm.Opcodes;

/**
 * The two helpers the Dubbo tests need from the host's integration harness, kept local so the
 * plugin repository does not depend on the upstream test sources.
 */
final class DubboTestSupport {

    private DubboTestSupport() {
    }

    static ShellToolConfig commandConfig() {
        return CommandConfig.builder().paramName("param").build();
    }

    static ShellToolConfig godzillaConfig() {
        return GodzillaConfig.builder()
                .pass("pass")
                .key("key")
                .headerName("User-Agent")
                .headerValue("dubbo")
                .build();
    }

    static MemShellResult generate(String shellType, String shellTool, int targetJdkVersion,
                                   ShellToolConfig shellToolConfig) {
        ShellConfig shellConfig = ShellConfig.builder()
                .server(DubboShellType.SERVER)
                .shellTool(shellTool)
                .shellType(shellType)
                .targetJreVersion(targetJdkVersion)
                .byPassJavaModule(targetJdkVersion >= Opcodes.V9)
                .debug(true)
                .shrink(true)
                .lambdaSuffix(true)
                .obfuscation(ObfuscationConfig.enabled())
                .build();
        InjectorConfig injectorConfig = InjectorConfig.builder().staticInitialize(true).build();
        return new MemShellGenerator().generate(shellConfig, injectorConfig, shellToolConfig);
    }
}
