package com.reajason.javaweb.memshell.server.dubbo;

import com.reajason.javaweb.memshell.server.AbstractServer;
import com.reajason.javaweb.memshell.spi.ServerMeta;
import com.reajason.javaweb.memshell.spi.ServerProvider;
import org.kohsuke.MetaInfServices;

/**
 * @author ReaJason
 * @since 3.0.0
 */
@MetaInfServices(ServerProvider.class)
@ServerMeta(name = DubboShellType.SERVER, order = 170)
public class DubboServerProvider implements ServerProvider {

    @Override
    public AbstractServer createServer() {
        return new Dubbo();
    }
}
