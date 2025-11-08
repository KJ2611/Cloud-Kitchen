import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;

public class CustomerPanel extends JPanel {
    private final JTable table;
    private final DefaultTableModel ordersModel = new DefaultTableModel(
            new Object[]{"Order ID","Total","Status","Created"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable ordersTable = new JTable(ordersModel);
    // Embedded tracking widgets
    private final JLabel trackingTitle = new JLabel("No active tracking", SwingConstants.CENTER);
    private final JLabel trackingStatus = new JLabel(" ", SwingConstants.CENTER);
    private final JProgressBar trackingBar = new JProgressBar();
    private Timer trackingTimer;
    private Integer trackingOrderId = null;

    public CustomerPanel(MainApp app, JTable table, DefaultTableModel model) {
        super(new BorderLayout());
        JLabel lbl = new JLabel("Customer - Menu", SwingConstants.CENTER);
        lbl.setFont(lbl.getFont().deriveFont(Font.BOLD, 18f));
        JPanel top = new JPanel(new BorderLayout());
        top.add(lbl, BorderLayout.CENTER);

        JPanel topRight = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> { app.loadMenu(); app.adjustColumnWidths(getTable()); refreshOrders(app); });
        JButton logout = new JButton("Logout");
        logout.addActionListener(e -> app.logout());
        topRight.add(refresh);
        topRight.add(logout);
        top.add(topRight, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);

        this.table = table;
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setRowSelectionAllowed(true);
        table.setFillsViewportHeight(true);
        table.setRowHeight(26);
        // editors
        table.getColumnModel().getColumn(4).setCellEditor(new MainApp.OptionsCellEditor(table, model));
        table.getColumnModel().getColumn(5).setCellEditor(new MainApp.SpinnerEditor(0, 999, 1));
        // visuals
        app.adjustColumnWidths(table);
        MainApp.JTableHeaderStyler.style(table);
        JScrollPane spMenu = new JScrollPane(table);
        spMenu.setViewportView(table);
        spMenu.setColumnHeaderView(table.getTableHeader());
        add(spMenu, BorderLayout.CENTER);

        // Right sidebar: recent orders
        JPanel right = new JPanel(new BorderLayout(6,6));
        JLabel rightTitle = new JLabel("My Recent Orders", SwingConstants.CENTER);
        rightTitle.setFont(rightTitle.getFont().deriveFont(Font.BOLD));
        right.add(rightTitle, BorderLayout.NORTH);
        ordersTable.setRowHeight(22);
        JScrollPane spOrders = new JScrollPane(ordersTable);
        right.add(spOrders, BorderLayout.CENTER);

        // Tracking panel under the orders list
        JPanel trackingPanel = new JPanel(new BorderLayout(4,4));
        trackingTitle.setFont(trackingTitle.getFont().deriveFont(Font.BOLD));
        trackingBar.setIndeterminate(false);
        trackingBar.setMinimum(0);
        trackingBar.setMaximum(100);
        trackingPanel.add(trackingTitle, BorderLayout.NORTH);
        JPanel trackingCenter = new JPanel(new GridLayout(2,1,2,2));
        trackingCenter.add(trackingStatus);
        trackingCenter.add(trackingBar);
        trackingPanel.add(trackingCenter, BorderLayout.CENTER);
        trackingPanel.setBorder(BorderFactory.createTitledBorder("Live Tracking"));
        right.add(trackingPanel, BorderLayout.SOUTH);
        JPanel rightBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton refreshOrders = new JButton("Refresh");
        refreshOrders.addActionListener(e -> refreshOrders(app));
        JButton track = new JButton("Track Selected");
        track.addActionListener(e -> {
            int r = ordersTable.getSelectedRow();
            if (r < 0) { JOptionPane.showMessageDialog(this, "Select an order"); return; }
            int orderId = (Integer) ordersModel.getValueAt(r, 0);
            startTracking(app, orderId);
        });
        rightBtns.add(refreshOrders);
        rightBtns.add(track);
        // Place buttons above tracking panel
        right.add(rightBtns, BorderLayout.CENTER);
        right.setBorder(BorderFactory.createEmptyBorder(6,6,6,6));
        right.setPreferredSize(new Dimension(340, 10));
        add(right, BorderLayout.EAST);

        JPanel bottom = new JPanel();
        JButton placeOrder = new JButton("Place Order");
        placeOrder.addActionListener(e -> app.placeOrder());
        bottom.add(placeOrder);
        add(bottom, BorderLayout.SOUTH);

        // initial orders load
        refreshOrders(app);
    }

    public JTable getTable() { return table; }

    public void refreshOrders(MainApp app) {
        ordersModel.setRowCount(0);
        int uid = app.getCurrentUserId();
        if (uid <= 0) return;
        String sql = "SELECT id, total, status, created_at FROM orders WHERE user_id=? ORDER BY created_at DESC LIMIT 20";
        try (Connection conn = DBUtil.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, uid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ordersModel.addRow(new Object[]{ rs.getInt(1), rs.getDouble(2), rs.getString(3), rs.getTimestamp(4) });
                }
            }
        } catch (SQLException ex) {
            // show non-intrusive error
        }
    }

    public void startTracking(MainApp app, int orderId) {
        // stop previous timer if any
        if (trackingTimer != null) trackingTimer.stop();
        trackingOrderId = orderId;
        trackingTitle.setText("Tracking Order " + orderId);
        trackingStatus.setText("Checking...");
        trackingBar.setIndeterminate(true);
        trackingBar.setValue(0);

        trackingTimer = new Timer(2000, e -> {
            try {
                String st = app.fetchOrderStatus(orderId);
                if (st == null) st = "UNKNOWN";
                trackingStatus.setText("Status: " + st);
                if ("COMPLETED".equalsIgnoreCase(st)) {
                    trackingBar.setIndeterminate(false);
                    trackingBar.setValue(100);
                    trackingTimer.stop();
                }
            } catch (SQLException ex) {
                trackingStatus.setText("Error: " + ex.getMessage());
                trackingTimer.stop();
            }
        });
        trackingTimer.start();
    }
}
