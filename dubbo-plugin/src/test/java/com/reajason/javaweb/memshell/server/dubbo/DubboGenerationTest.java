package com.reajason.javaweb.memshell.server.dubbo;

import com.reajason.javaweb.memshell.MemShellGenerator;
import com.reajason.javaweb.memshell.MemShellResult;
import com.reajason.javaweb.memshell.MemShellRegistry;
import com.reajason.javaweb.memshell.ShellTool;
import com.reajason.javaweb.memshell.config.InjectorConfig;
import com.reajason.javaweb.memshell.config.ObfuscationConfig;
import com.reajason.javaweb.memshell.config.ShellConfig;
import com.reajason.javaweb.memshell.config.ShellToolConfig;
import com.reajason.javaweb.memshell.shelltool.command.CommandConfig;
import com.reajason.javaweb.memshell.shelltool.godzilla.GodzillaConfig;
import com.reajason.javaweb.memshell.shelltool.proxy.ProxyConfig;
import net.bytebuddy.jar.asm.ClassReader;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Generation is the behavior the host exposes to users: a user picks Dubbo, a tool and a
 * transport, and gets injectable bytes. These tests drive that path through the public
 * {@link MemShellGenerator}, with the real upstream tool providers on the classpath.
 */
class DubboGenerationTest {

    static Stream<Arguments> tools() {
        return Stream.of(
                Arguments.of(ShellTool.Command),
                Arguments.of(ShellTool.Godzilla),
                Arguments.of(ShellTool.Proxy)
        );
    }

    @ParameterizedTest
    @MethodSource("tools")
    void generatesEveryDubboTransportForEachProvidedTool(String shellTool) {
        assertThatSupported(shellTool);

        for (String shellType : new String[]{
                DubboShellType.APACHE_DUBBO_SERVICE, DubboShellType.ALIBABA_DUBBO_SERVICE,
                DubboShellType.APACHE_DUBBO_EXPORTER, DubboShellType.ALIBABA_DUBBO_EXPORTER}) {
            MemShellResult result = generate(shellType, shellTool);

            assertNotNull(result.getShellBytes(), shellType + " shell bytes");
            assertTrue(result.getShellBytes().length > 0, shellType + " shell bytes empty");
            assertNotNull(result.getInjectorBytesBase64Str(), shellType + " injector bytes");

            ClassInfo shell = ClassInfo.read(result.getShellBytes());
            assertTrue(shell.methods.contains("handle([B)[B"),
                    shellType + " shell must keep the Dubbo handle method, got " + shell.methods);

            if (shellType.endsWith(DubboShellType.DUBBO_EXPORTER)) {
                assertExporterResult(result, shellType, shell);
                continue;
            }
            assertHelperBackedService(result, shellType, shell);
        }
    }

    private static void assertHelperBackedService(MemShellResult result, String shellType,
                                                  ClassInfo shell) {
        assertNotNull(result.getHelperBytes(), shellType + " helper bytes");
        assertTrue(result.getHelperBytes().length > 0, shellType + " helper bytes empty");

        String interfaceName = result.getInjectorConfig().getUrlPattern();
        assertEquals(result.getHelperClassName(), interfaceName);
        assertTrue(shell.interfaces.contains(interfaceName.replace('.', '/')),
                shellType + " shell must implement " + interfaceName);
    }

    /**
     * An exporter shell exports no interface and needs no helper, so the control path must be
     * derived from the shell class name instead of a generated service interface.
     */
    private static void assertExporterResult(MemShellResult result, String shellType,
                                             ClassInfo shell) {
        assertNull(result.getHelperBytes(), shellType + " must not generate a helper interface");
        assertTrue(shell.interfaces.isEmpty(),
                shellType + " must not implement an interface, got " + shell.interfaces);

        String controlPath = result.getInjectorConfig().getUrlPattern();
        String expected = result.getShellClassName().substring(
                0, result.getShellClassName().lastIndexOf('.'))
                + ".I" + result.getShellClassName()
                .substring(result.getShellClassName().lastIndexOf('.') + 1);
        assertEquals(expected, controlPath, shellType + " control path");
    }

    @Test
    void helperIsTheServiceInterfaceTheShellImplements() {
        MemShellResult result = generate(DubboShellType.APACHE_DUBBO_SERVICE, ShellTool.Command);
        ClassInfo helper = ClassInfo.read(result.getHelperBytes());
        assertTrue(helper.isInterface, "helper must be an interface");
        assertTrue(helper.interfaces.isEmpty(), "helper must be a standalone interface");
        assertTrue(helper.methods.contains("handle([B)[B"), "helper methods: " + helper.methods);
    }

    @Test
    void theTwoTransportsUseDifferentInjectors() {
        MemShellResult apache = generate(DubboShellType.APACHE_DUBBO_SERVICE, ShellTool.Command);
        MemShellResult alibaba = generate(DubboShellType.ALIBABA_DUBBO_SERVICE, ShellTool.Command);
        assertNotEquals(apache.getInjectorClassName(), alibaba.getInjectorClassName());
        assertNotEquals(apache.getInjectorBytesBase64Str(), alibaba.getInjectorBytesBase64Str());

        MemShellResult apacheExporter = generate(DubboShellType.APACHE_DUBBO_EXPORTER, ShellTool.Command);
        MemShellResult alibabaExporter = generate(DubboShellType.ALIBABA_DUBBO_EXPORTER, ShellTool.Command);
        assertNotEquals(apacheExporter.getInjectorClassName(), apache.getInjectorClassName());
        assertNotEquals(alibabaExporter.getInjectorClassName(), alibaba.getInjectorClassName());
        assertNotEquals(apacheExporter.getInjectorClassName(), alibabaExporter.getInjectorClassName());
    }

    @Test
    void fullyObfuscatedServiceInterfaceStaysLoadable() throws Exception {
        MemShellResult result = generate(DubboShellType.APACHE_DUBBO_SERVICE, ShellTool.Command);
        BytesLoader loader = new BytesLoader(DubboGenerationTest.class.getClassLoader());
        Class<?> helper = loader.define(result.getHelperClassName(), result.getHelperBytes());
        Class<?> shell = loader.define(result.getShellClassName(), result.getShellBytes());

        assertTrue(helper.isInterface(), "helper must stay an interface");
        assertTrue(helper.isAssignableFrom(shell), "shell must keep implementing its service interface");
        assertNotNull(helper.getMethod("handle", byte[].class), "handle must survive obfuscation");
    }

    private static final class BytesLoader extends ClassLoader {
        BytesLoader(ClassLoader parent) {
            super(parent);
        }

        Class<?> define(String name, byte[] bytes) {
            return defineClass(name, bytes, 0, bytes.length);
        }
    }

    private static void assertThatSupported(String shellTool) {
        Dubbo server = (Dubbo) MemShellRegistry.getInstance().getServer(DubboShellType.SERVER);
        assertNotNull(server, "Dubbo server must be registered");
        Set<String> supported = server.getSupportedShellTypes(shellTool);
        assertTrue(supported.contains(DubboShellType.APACHE_DUBBO_SERVICE),
                shellTool + " must advertise ApacheDubboService, got " + supported);
        assertTrue(supported.contains(DubboShellType.ALIBABA_DUBBO_SERVICE),
                shellTool + " must advertise AlibabaDubboService, got " + supported);
        assertTrue(supported.contains(DubboShellType.APACHE_DUBBO_EXPORTER),
                shellTool + " must advertise ApacheDubboExporter, got " + supported);
        assertTrue(supported.contains(DubboShellType.ALIBABA_DUBBO_EXPORTER),
                shellTool + " must advertise AlibabaDubboExporter, got " + supported);
    }

    private static MemShellResult generate(String shellType, String shellTool) {
        ShellToolConfig toolConfig = toolConfig(shellTool);
        ShellConfig shellConfig = ShellConfig.builder()
                .server(DubboShellType.SERVER)
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
        return new MemShellGenerator().generate(shellConfig, injectorConfig, toolConfig);
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

    private static final class ClassInfo {
        private String name;
        private boolean isInterface;
        private final List<String> interfaces = new ArrayList<String>();
        private final Set<String> methods = new LinkedHashSet<String>();

        static ClassInfo read(byte[] bytes) {
            ClassInfo info = new ClassInfo();
            new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public void visit(int version, int access, String name, String signature,
                                  String superName, String[] interfaces) {
                    info.name = name;
                    info.isInterface = (access & Opcodes.ACC_INTERFACE) != 0;
                    if (interfaces != null) {
                        for (String anInterface : interfaces) {
                            info.interfaces.add(anInterface);
                        }
                    }
                }

                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    info.methods.add(name + descriptor);
                    return null;
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return info;
        }
    }
}
