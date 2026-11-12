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
                .build();
    }

    @Override
    public Set<MethodSignature> getShellMethodContracts(String shellType) {
        if (shellType != null && shellType.endsWith(DubboShellType.DUBBO_SERVICE)) {
            return DubboMethodContracts.DUBBO_CONTRACTS;
        }
        return super.getShellMethodContracts(shellType);
    }

    /**
     * Dubbo 服务按接口名注册，类名沿用上游的 Service 后缀，保持与旧版本生成的马一致。
     */
    @Override
    public String getShellClassName(String serverName, String shellType) {
        return sampleShellClassName(getShellPackageName(serverName), "Service");
    }

    /**
     * DubboService 马需要配套接口：生成接口 helper 类、给马字节码补上接口实现，
     * 且 urlPattern 默认取接口全限定名（Dubbo 服务按接口名注册）
     */
    @Override
    public byte[] postProcessShellBytes(byte[] shellBytes, ShellConfig shellConfig,
                                        ShellToolConfig shellToolConfig, InjectorConfig injectorConfig) {
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
}
