package chr.wgx.builtin.wgcv1;

import chr.wgx.reactor.IWidget;
import chr.wgx.reactor.Reactor;
import chr.wgx.reactor.plugin.IPlugin;
import chr.wgx.reactor.plugin.IWidgetProvider;
import tech.icey.xjbutil.container.Pair;

import java.util.List;

public final class WGCV1 implements IPlugin, IWidgetProvider {
    @Override
    public void tick(Reactor reactor) throws Exception {
    }

    @Override
    public List<Pair<DockTarget, IWidget>> provide() {
        return List.of();
    }

    @Override
    public int priority() {
        return -1000;
    }
}
