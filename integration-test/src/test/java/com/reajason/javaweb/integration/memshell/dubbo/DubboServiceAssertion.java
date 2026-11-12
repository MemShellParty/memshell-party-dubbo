package com.reajason.javaweb.integration.memshell.dubbo;

import com.reajason.javaweb.memshell.MemShellResult;
import com.reajason.javaweb.memshell.ShellTool;
import com.reajason.javaweb.memshell.config.ShellToolConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;

import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;

final class DubboServiceAssertion {

    private static final Logger log = LoggerFactory.getLogger(DubboServiceAssertion.class);

    private static final String LOADER_INTERFACE = "io.github.reajason.dubbo.fixture.api.BytecodeLoadingService";
    private static final String COMMAND = "id";
    private static final String GODZILLA_KEY = "key";
    private static final String GODZILLA_PASS = "pass";

    private DubboServiceAssertion() {
    }

    static void assertCommandService(GenericContainer<?> container, DubboProviderScenario scenario) throws Exception {
        ShellToolConfig shellToolConfig = DubboTestSupport.commandConfig();
        MemShellResult result = DubboTestSupport.generate(
                scenario.shellType(),
                ShellTool.Command,
                scenario.targetJdkVersion(),
                shellToolConfig
        );
        String interfaceName = result.getInjectorConfig().getUrlPattern();
        String loadOutput = loadInjector(container, scenario, result);

        List<String> fallbackUrls = scenario.commandTargets().stream()
                .map(target -> target.url(container.getHost(),
                        container.getMappedPort(target.port()), interfaceName))
                .collect(Collectors.toList());
        List<String> commandUrls = DubboUrlResolver.resolveCommandUrls(loadOutput, interfaceName, fallbackUrls);

        for (String commandUrl : commandUrls) {
            assertCommandOutput(scenario, commandUrl, interfaceName);
        }
    }

    static void assertExporterCommand(GenericContainer<?> container, DubboProviderScenario scenario) throws Exception {
        MemShellResult result = DubboTestSupport.generate(
                scenario.exporterShellType(),
                ShellTool.Command,
                scenario.targetJdkVersion(),
                DubboTestSupport.commandConfig()
        );
        String controlPath = result.getInjectorConfig().getUrlPattern();
        loadInjector(container, scenario, result);

        assertCommandOutput(scenario, exporterUrl(container, scenario, controlPath), controlPath);
    }

    static void assertExporterGodzilla(GenericContainer<?> container, DubboProviderScenario scenario) throws Exception {
        MemShellResult result = DubboTestSupport.generate(
                scenario.exporterShellType(),
                ShellTool.Godzilla,
                scenario.targetJdkVersion(),
                DubboTestSupport.godzillaConfig()
        );
        String controlPath = result.getInjectorConfig().getUrlPattern();
        loadInjector(container, scenario, result);

        String url = exporterUrl(container, scenario, controlPath);
        String output = DubboClientRunner.runGodzilla(
                scenario.clientKind(), url, controlPath, GODZILLA_KEY, GODZILLA_PASS, COMMAND);
        log.info("{} exporter godzilla output: {}", scenario.name(), output);
        assertThat(scenario.name() + " godzilla command output", output, containsString("uid="));
    }

    private static String loadInjector(GenericContainer<?> container, DubboProviderScenario scenario,
                                       MemShellResult result) throws Exception {
        String loaderUrl = "dubbo://" + container.getHost() + ":"
                + container.getMappedPort(scenario.loaderPort()) + "/" + LOADER_INTERFACE;
        log.info("loading {} into {} via {}", result.getInjectorConfig().getUrlPattern(),
                scenario.name(), loaderUrl);
        String loadOutput = DubboClientRunner.loadBytes(
                scenario.clientKind(), loaderUrl, result.getInjectorBytesBase64Str());
        log.info("{} load output: {}", scenario.name(), loadOutput);
        return loadOutput;
    }

    private static String exporterUrl(GenericContainer<?> container, DubboProviderScenario scenario,
                                      String controlPath) {
        DubboProtocolTarget target = scenario.dubboTarget();
        return target.url(container.getHost(), container.getMappedPort(target.port()), controlPath);
    }

    private static void assertCommandOutput(DubboProviderScenario scenario,
                                            String commandUrl, String interfaceName) throws Exception {
        String output = DubboClientRunner.runCommand(scenario.clientKind(), commandUrl, interfaceName, COMMAND);
        log.info("{} {} command output: {}", scenario.name(),
                DubboUrlResolver.protocolOf(commandUrl), output);
        assertThat(output, anyOf(containsString("uid="), containsString("injected-ok")));
    }
}
