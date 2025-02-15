package chr.wgx.ui;

import chr.wgx.reactor.IDockingPort;
import chr.wgx.reactor.IWidget;
import chr.wgx.reactor.plugin.DockTarget;
import chr.wgx.reactor.plugin.MenuDockTarget;
import chr.wgx.reactor.plugin.MenuInfo;
import chr.wgx.reactor.plugin.WidgetDockTarget;
import chr.wgx.util.JULUtil;
import org.jetbrains.annotations.Nullable;
import tech.icey.xjbutil.container.Option;
import tech.icey.xjbutil.container.Pair;
import tech.icey.xjbutil.container.Tuple3;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.*;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public final class ControlWindow extends JFrame {
    public ControlWindow() {
        super("Project-WGX - 控制器");
        ImageIcon icon = new ImageIcon(Objects.requireNonNull(getClass().getResource("/resources/icon/icon-v2.png")));
        this.setIconImage(icon.getImage());

        this.menuBar = new JMenuBar();
        this.setJMenuBar(menuBar);

        this.systemMenu = new JMenu("系统");
        menuBar.add(systemMenu);

        JMenuItem pluginManagementItem = new JMenuItem("插件管理");
        systemMenu.add(pluginManagementItem);
        pluginManagementItem.addActionListener(_ -> JOptionPane.showMessageDialog(
                this,
                "插件系统尚未加载，请稍等片刻",
                "提示",
                JOptionPane.INFORMATION_MESSAGE
        ));

        JMenu logLevelSubMenu = new JMenu("日志级别");
        systemMenu.add(logLevelSubMenu);

        JMenuItem logLevelDebugMenuItem = new JMenuItem("调试");
        logLevelDebugMenuItem.addActionListener(_ -> JULUtil.setLogLevel(Level.FINE));
        logLevelSubMenu.add(logLevelDebugMenuItem);
        JMenuItem logLevelInfoMenuItem = new JMenuItem("信息");
        logLevelInfoMenuItem.addActionListener(_ -> JULUtil.setLogLevel(Level.INFO));
        logLevelSubMenu.add(logLevelInfoMenuItem);
        JMenuItem logLevelWarnMenuItem = new JMenuItem("警告");
        logLevelWarnMenuItem.addActionListener(_ -> JULUtil.setLogLevel(Level.WARNING));
        logLevelSubMenu.add(logLevelWarnMenuItem);
        JMenuItem logLevelErrorMenuItem = new JMenuItem("错误");
        logLevelErrorMenuItem.addActionListener(_ -> JULUtil.setLogLevel(Level.SEVERE));
        logLevelSubMenu.add(logLevelErrorMenuItem);
        logLevelSubMenu.addSeparator();
        JMenuItem logLevelTest = new JMenuItem("测试日志等级");
        logLevelTest.addActionListener(_ -> {
            logger.fine("这是一条调试日志");
            logger.info("这是一条信息日志");
            logger.warning("这是一条警告日志");
            logger.severe("这是一条错误日志");
        });
        logLevelSubMenu.add(logLevelTest);

        JMenuItem pauseOrResumeMenuItem = new JMenuItem("暂停日志") {
            @Override
            public void doClick(int pressTime) {
                logPaused = !logPaused;
                this.setText(logPaused ? "恢复日志" : "暂停日志");
            }
        };
        systemMenu.add(pauseOrResumeMenuItem);

        JMenuItem exitMenuItem = new JMenuItem("退出") {
            @Override
            public void doClick(int pressTime) {
                System.exit(0);
            }
        };
        systemMenu.add(exitMenuItem);

        this.helpMenu = new JMenu("帮助");
        menuBar.add(helpMenu);
        JMenuItem helpMenuItem = new JMenuItem("帮助主题");
        helpMenu.add(helpMenuItem);
        JMenuItem aboutItem = new JMenuItem("关于");
        helpMenu.add(aboutItem);

        JPanel contentPanel = new JPanel();
        GridBagLayout contentPanelLayout = new GridBagLayout();
        contentPanel.setLayout(contentPanelLayout);
        this.setContentPane(contentPanel);

        this.textArea = new JTextArea();
        this.textArea.setLineWrap(true);
        this.textArea.setEditable(false);
        SwingUtil.createTextAreaMenu(this.textArea);

        JScrollPane scrollPane = new JScrollPane(this.textArea);
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        c.weightx = 1;
        c.weighty = 1;
        c.gridx = 0;
        c.gridy = 0;
        this.add(scrollPane, c);

        JLabel statusLabel = new JLabel("就绪");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));

        c.fill = GridBagConstraints.HORIZONTAL;
        c.weighty = 0;
        c.gridy = 1;
        this.add(statusLabel, c);

        this.setMinimumSize(new Dimension(640, 480));
        this.pack();
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        new Thread(() -> {
            while (true) {
                try {
                    //noinspection BusyWait
                    Thread.sleep(100);
                    Runtime runtime = Runtime.getRuntime();
                    long totalMemory = runtime.totalMemory() / (1024 * 1024);
                    long freeMemory = runtime.freeMemory() / (1024 * 1024);
                    long maxMemory = runtime.maxMemory() / (1024 * 1024);
                    long usedMemory = totalMemory - freeMemory;
                    SwingUtilities.invokeLater(() -> statusLabel.setText(String.format(
                            "JVM 内存: 当前 %d, 最大 %d, 已使用 %d, 空闲 %d (MiB)",
                            totalMemory,
                            maxMemory,
                            usedMemory,
                            freeMemory
                    )));
                } catch (InterruptedException ignored) {}
            }
        }).start();

        JULUtil.addLogHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                // 过滤 AWT 和 Swing 的日志，以免用户在操作用户界面时产生大量日志把用户真正关心的日志淹没
                // 控制台？淹了就淹了
                if (record.getLoggerName().contains("java.awt") ||
                    record.getLoggerName().contains("sun.awt") ||
                    record.getMessage().contains("java.awt") ||
                    record.getMessage().contains("java.swing")) {
                    return;
                }

                addLogText(String.format(
                        "[%s] [%s] %s : %s%n",
                        record.getInstant(),
                        record.getLevel(),
                        record.getLoggerName(),
                        record.getMessage()
                ));
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() throws SecurityException {
            }
        });
    }

    public void addLogText(String logText) {
        SwingUtilities.invokeLater(() -> {
            if (this.logPaused) {
                return;
            }

            if (this.textArea.getLineCount() > 3000) {
                try {
                    int start = this.textArea.getLineStartOffset(0);
                    int end = this.textArea.getLineEndOffset(100);
                    this.textArea.replaceRange("", start, end);
                } catch (Exception ignored) {}
            }

            this.textArea.append(logText);
            this.textArea.setCaretPosition(this.textArea.getDocument().getLength());
        });
    }

    public void addWidgets(List<MenuInfo> menuInfos, List<Pair<DockTarget, IWidget>> widgets) {
        SwingUtilities.invokeLater(() -> {
            // 初始化菜单
            this.menuBar.removeAll();
            this.menuBar.add(systemMenu);

            HashMap<String, Pair<MenuInfo, JMenu>> subMenus = new HashMap<>();

            for (MenuInfo menuInfo : menuInfos) {
                JMenu subMenu = new JMenu(menuInfo.displayName);
                subMenus.put(menuInfo.name, new Pair<>(menuInfo, subMenu));
            }

            List<Pair<MenuInfo, JMenu>> topLevelMenus = new ArrayList<>();
            for (Pair<MenuInfo, JMenu> pair : subMenus.values()) {
                MenuInfo menuInfo = pair.first();
                if (!(menuInfo.parent instanceof Option.Some<String> some)) {
                    topLevelMenus.add(pair);
                    continue;
                }

                String parent = some.value;
                JMenu subMenu = pair.second();

                @Nullable Pair<MenuInfo, JMenu> parentPair = subMenus.get(parent);
                if (parentPair == null) {
                    logger.warning("菜单 " + menuInfo.displayName + " (" + menuInfo.name + ") 指定的父菜单 " + parent + " 不存在");
                    continue;
                }

                parentPair.second().add(subMenu);
            }

            topLevelMenus.sort(Comparator.comparingInt(a -> a.first().sortingOrder));

            for (Pair<MenuInfo, JMenu> pair : topLevelMenus) {
                JMenu subMenu = pair.second();
                this.menuBar.add(subMenu);
            }

            this.menuBar.add(helpMenu);

            // 初始化控件
            HashMap<String, IDockingPort> dockingPorts = new HashMap<>();
            for (Pair<DockTarget, IWidget> pair : widgets) {
                if (pair.second() instanceof IDockingPort port) {
                    dockingPorts.put(port.getPortName(), port);
                }
            }

            for (Pair<DockTarget, IWidget> pair : widgets) {
                DockTarget target = pair.first();
                IWidget widget = pair.second();

                switch (target) {
                    case WidgetDockTarget dockWidget -> {
                        IDockingPort port = dockingPorts.get(dockWidget.targetName);
                        if (port == null) {
                            logger.warning("组件 " + widget.displayName() + " 指定的停靠区域 " + dockWidget.targetName + " 不存在");
                            continue;
                        }

                        port.addElement(widget.displayName(), dockWidget.location, widget.getContentPanel());
                    }
                    case MenuDockTarget dockMenu -> {
                        Pair<MenuInfo, JMenu> menuPair = subMenus.get(dockMenu.targetName);
                        if (menuPair == null) {
                            logger.warning("组件 " + widget.displayName() + " 指定的菜单 " + dockMenu.targetName + " 不存在");
                            continue;
                        }

                        JMenuItem menuItem = new JMenuItem(widget.displayName());
                        JFrame frame = new JFrame(widget.displayName());
                        frame.setContentPane(widget.getContentPanel());
                        frame.pack();
                        frame.setSize(frame.getPreferredSize());
                        frame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
                        frame.addWindowListener(new WindowAdapter() {
                            @Override
                            public void windowClosing(WindowEvent windowEvent) {
                                widget.onUndock();
                            }

                            @Override
                            public void windowOpened(WindowEvent windowEvent) {
                                widget.onDock();
                            }
                        });
                        menuItem.addActionListener(_ -> {
                            frame.setContentPane(widget.getContentPanel());
                            frame.pack();
                            frame.setVisible(true);
                        });
                        menuPair.second().add(menuItem);
                    }
                    default -> logger.warning(
                            "组件 " + widget.displayName()
                                    + " 指定的停靠区域类型 "
                                    + target.getClass().getName()
                                    + " 无效"
                    );
                }
            }
        });
    }

    private final JMenuBar menuBar;
    private final JMenu systemMenu;
    private final JMenu helpMenu;

    private final JTextArea textArea;
    private boolean logPaused = false;

    private static final Logger logger = Logger.getLogger(ControlWindow.class.getName());
}
