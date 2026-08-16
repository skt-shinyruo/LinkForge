package com.linkforge.testsupport;

import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;

/** 在整个 JUnit 会话关闭后输出共享拓扑的最终指标。 */
public final class SharedIntegrationTopologyMetricsListener implements LauncherSessionListener {

    @Override
    public void launcherSessionClosed(LauncherSession session) {
        System.out.println("LINKFORGE_SHARED_TOPOLOGY_METRICS " + SharedIntegrationTopology.metrics());
    }
}
