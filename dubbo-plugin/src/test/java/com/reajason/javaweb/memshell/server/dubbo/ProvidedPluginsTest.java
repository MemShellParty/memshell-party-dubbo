package com.reajason.javaweb.memshell.server.dubbo;

import com.reajason.javaweb.memshell.ShellTool;
import com.reajason.javaweb.memshell.server.ToolMapping;
import com.reajason.javaweb.memshell.shelltool.command.CommandShellToolProvider;
import com.reajason.javaweb.memshell.shelltool.godzilla.GodzillaShellToolProvider;
import com.reajason.javaweb.memshell.shelltool.proxy.ProxyShellToolProvider;
import com.reajason.javaweb.memshell.spi.ShellToolMeta;
import com.reajason.javaweb.memshell.spi.ShellToolProvider;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The server extends the upstream plugins by name. These assertions fail the build if a
 * provided plugin renames its registration or stops shipping the transports we override.
 */
class ProvidedPluginsTest {

    @Test
    void overridesTargetTheNamesTheProvidedPluginsRegister() {
        Dubbo server = new Dubbo();

        assertToolName(CommandShellToolProvider.class, ShellTool.Command);
        assertToolName(GodzillaShellToolProvider.class, ShellTool.Godzilla);
        assertToolName(ProxyShellToolProvider.class, ShellTool.Proxy);

        assertThat(server.getToolMappingOverrides().keySet(),
                containsInAnyOrder(ShellTool.Command, ShellTool.Godzilla, ShellTool.Proxy));
    }

    @Test
    void everyOverrideNamesAllDubboTransportsWithAResolvableTemplate() {
        Dubbo server = new Dubbo();
        for (Map.Entry<String, ToolMapping> entry : server.getToolMappingOverrides().entrySet()) {
            ToolMapping mapping = entry.getValue();
            assertThat(mapping, notNullValue());
            assertThat(mapping.getShellClassMap().keySet(), containsInAnyOrder(
                    DubboShellType.APACHE_DUBBO_SERVICE, DubboShellType.ALIBABA_DUBBO_SERVICE,
                    DubboShellType.APACHE_DUBBO_EXPORTER, DubboShellType.ALIBABA_DUBBO_EXPORTER));
            for (String template : mapping.getShellClassMap().values()) {
                assertTrue(classExists(template), entry.getKey() + " template not found: " + template);
            }
        }
    }

    private static void assertToolName(Class<? extends ShellToolProvider> providerType, String expected) {
        ShellToolMeta meta = providerType.getAnnotation(ShellToolMeta.class);
        assertThat(meta, notNullValue());
        assertEquals(expected, meta.name());
    }

    private static boolean classExists(String className) {
        return ProvidedPluginsTest.class.getClassLoader()
                .getResource(className.replace('.', '/') + ".class") != null;
    }
}
