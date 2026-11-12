package com.reajason.javaweb.dubbo.verify;

import com.reajason.javaweb.memshell.MemShellGenerator;
import com.reajason.javaweb.memshell.MemShellRegistry;
import com.reajason.javaweb.memshell.MemShellResult;
import com.reajason.javaweb.memshell.PluginBootstrap;
import com.reajason.javaweb.memshell.PluginValidationReport;
import com.reajason.javaweb.memshell.ShellTool;
import com.reajason.javaweb.memshell.config.InjectorConfig;
import com.reajason.javaweb.memshell.config.ObfuscationConfig;
import com.reajason.javaweb.memshell.config.ShellConfig;
import com.reajason.javaweb.memshell.config.ShellToolConfig;
import com.reajason.javaweb.memshell.shelltool.command.CommandConfig;
import com.reajason.javaweb.memshell.shelltool.godzilla.GodzillaConfig;
import com.reajason.javaweb.memshell.shelltool.proxy.ProxyConfig;
import net.bytebuddy.jar.asm.Opcodes;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the shipped jar, loaded through the host's bootstrap and an isolated class loader,
 * advertises the Dubbo server and generates every tool/transport the host will offer.
 */
class PluginJarExtensionTest {

    private static final String SERVER = "Dubbo";
    private static final String APACHE = "ApacheDubboService";
    private static final String ALIBABA = "AlibabaDubboService";

    private static Path pluginDirectory;

    @BeforeAll
    static void loadPluginJar() throws Exception {
        Path jar = Path.of(System.getProperty("dubbo.plugin.jar"));
        assertTrue(Files.isRegularFile(jar), "plugin jar not found: " + jar);
        pluginDirectory = Files.createTempDirectory("dubbo-plugin-verification");
        Files.copy(jar, pluginDirectory.resolve(jar.getFileName()));
        System.setProperty(PluginBootstrap.DIRECTORY_PROPERTY, pluginDirectory.toString());

        PluginValidationReport report = PluginBootstrap.validate(pluginDirectory);
        assertTrue(report.isValid(), "plugin validation failed: " + report.getErrors()
                + " warnings: " + report.getWarnings());

        PluginBootstrap.load();
        assertTrue(MemShellRegistry.getInstance().getSupportedServers().contains(SERVER),
                "server registry: " + MemShellRegistry.getInstance().getSupportedServers()
                        + " errors: " + MemShellRegistry.getInstance().getLoadErrors());
    }

    @Test
    void advertisesEveryProvidedToolForBothTransports() {
        for (String tool : new String[]{ShellTool.Command, ShellTool.Godzilla, ShellTool.Proxy}) {
            assertTrue(MemShellRegistry.getInstance().getSupportedTools().contains(tool),
                    "tool registry: " + MemShellRegistry.getInstance().getSupportedTools());
            assertTrue(MemShellRegistry.getInstance().getServer(SERVER)
                            .getSupportedShellTypes(tool).containsAll(java.util.List.of(APACHE, ALIBABA)),
                    tool + " transports: "
                            + MemShellRegistry.getInstance().getServer(SERVER).getSupportedShellTypes(tool));
        }
    }

    @Test
    void generatesShellHelperAndInjectorForEveryToolAndTransport() {
        for (String tool : new String[]{ShellTool.Command, ShellTool.Godzilla, ShellTool.Proxy}) {
            for (String transport : new String[]{APACHE, ALIBABA}) {
                MemShellResult result = generate(transport, tool);
                assertNotNull(result.getShellBytes(), tool + "/" + transport + " shell bytes");
                assertTrue(result.getShellBytes().length > 0, tool + "/" + transport + " shell empty");
                assertNotNull(result.getHelperBytes(), tool + "/" + transport + " helper bytes");
                assertTrue(result.getHelperBytes().length > 0, tool + "/" + transport + " helper empty");
                assertNotNull(result.getInjectorBytesBase64Str(), tool + "/" + transport + " injector");
                assertTrue(result.getInjectorConfig().getUrlPattern()
                                .equals(result.getHelperClassName()),
                        tool + "/" + transport + " url pattern must be the service interface");
            }
        }
    }

    private static MemShellResult generate(String shellType, String shellTool) {
        ShellConfig shellConfig = ShellConfig.builder()
                .server(SERVER)
                .shellTool(shellTool)
                .shellType(shellType)
                .targetJreVersion(Opcodes.V1_8)
                .byPassJavaModule(false)
                .debug(true)
                .shrink(true)
                .lambdaSuffix(true)
                .obfuscation(ObfuscationConfig.enabled())
                .build();
        InjectorConfig injectorConfig = InjectorConfig.builder().staticInitialize(true).build();
        return new MemShellGenerator().generate(shellConfig, injectorConfig, toolConfig(shellTool));
    }

    private static ShellToolConfig toolConfig(String shellTool) {
        switch (shellTool) {
            case ShellTool.Command:
                return CommandConfig.builder().paramName("param").build();
            case ShellTool.Godzilla:
                return GodzillaConfig.builder()
                        .pass("pass").key("key")
                        .headerName("User-Agent").headerValue("dubbo")
                        .build();
            case ShellTool.Proxy:
                return ProxyConfig.builder()
                        .headerName("User-Agent").headerValue("dubbo")
                        .build();
            default:
                throw new IllegalArgumentException(shellTool);
        }
    }
}
