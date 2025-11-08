import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;

public class AdminPanel extends JPanel {
    private final JTable table;
    private final DefaultTableModel ordersModel = new DefaultTableModel(
            new Object[]{"Order ID","Customer","Total","Status","Created"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    }; 
    private final JTable ordersTable = new JTable(ordersModel);

    public AdminPanel(MainApp app, JTable table, DefaultTableModel model) {
        super(new BorderLayout());
        // bind the provided admin table instance
        this.table = table;
        JLabel lbl = new JLabel("Admin", SwingConstants.CENTER);
        lbl.setFont(lbl.getFont().deriveFont(Font.BOLD, 18f));
        JPanel top = new JPanel(new BorderLayout());
        top.add(lbl, BorderLayout.CENTER);
        JButton logout = new JButton("Logout");
        logout.addActionListener(e -> app.logout());
        top.add(logout, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);

        // Tabs: Menu and Orders
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Menu", buildMenuTab(app, table, model));
        tabs.addTab("Orders", buildOrdersTab(app));
        add(tabs, BorderLayout.CENTER);
    }

    public JTable getTable() { return table; }

    private JPanel buildMenuTab(MainApp app, JTable table, DefaultTableModel model) {
        JPanel panel = new JPanel(new BorderLayout());

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
        JScrollPane sp = new JScrollPane(table);
        sp.setViewportView(table);
        sp.setColumnHeaderView(table.getTableHeader());
        panel.add(sp, BorderLayout.CENTER);

        JPanel bot = new JPanel();
        JButton add = new JButton("Add Item");
        add.addActionListener(e -> app.showAddMenuDialog());
        JButton edit = new JButton("Edit Selected");
        edit.addActionListener(e -> editSelectedMenu(app, model));
        JButton del = new JButton("Delete Selected");
        del.addActionListener(e -> app.deleteSelectedMenu());
        bot.add(add); bot.add(edit); bot.add(del);
        panel.add(bot, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildOrdersTab(MainApp app) {
        JPanel panel = new JPanel(new BorderLayout());
        ordersTable.setRowHeight(24);
        JScrollPane sp = new JScrollPane(ordersTable);
        panel.add(sp, BorderLayout.CENTER);

        JPanel top = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton refresh = new JButton("Refresh Orders");
        refresh.addActionListener(e -> loadOrders());
        JButton advance = new JButton("Advance Status");
        advance.addActionListener(e -> advanceSelectedOrder());
        top.add(refresh); top.add(advance);
        panel.add(top, BorderLayout.NORTH);

        loadOrders();
        return panel;
    }

    private void editSelectedMenu(MainApp app, DefaultTableModel model) {
        int r = table.getSelectedRow();
        if (r < 0) { JOptionPane.showMessageDialog(this, "Select a row"); return; }
        int id = (Integer) model.getValueAt(r, 0);
        String currName = String.valueOf(model.getValueAt(r,1));
        double currPrice = ((Number) model.getValueAt(r,2)).doubleValue();
        boolean currAvail = (Boolean) model.getValueAt(r,3);
        String currOptions = String.valueOf(model.getValueAt(r,4));

        JTextField name = new JTextField(currName);
        JTextField price = new JTextField(String.valueOf(currPrice));
        JCheckBox avail = new JCheckBox("Available", currAvail);
        JTextField options = new JTextField(currOptions);
        Object[] fields = {"Name", name, "Price", price, "Options (comma separated, optional)", options, avail};
        int ok = JOptionPane.showConfirmDialog(this, fields, "Edit Menu Item", JOptionPane.OK_CANCEL_OPTION);
        if (ok == JOptionPane.OK_OPTION) {
            try (Connection conn = DBUtil.getConnection();
                 PreparedStatement ps = conn.prepareStatement("UPDATE menu_items SET name=?, price=?, available=?, options=? WHERE id=?")) {
                ps.setString(1, name.getText().trim());
                ps.setDouble(2, Double.parseDouble(price.getText().trim()));
                ps.setBoolean(3, avail.isSelected());
                String opts = options.getText().trim();
                if (opts.isEmpty()) ps.setNull(4, Types.VARCHAR); else ps.setString(4, opts);
                ps.setInt(5, id);
                ps.executeUpdate();
                app.loadMenu();
                app.adjustColumnWidths(app.getCustomerTable());
                app.adjustColumnWidths(table);
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "Update failed: " + ex.getMessage());
            }
        }
    }

    private void loadOrders() {
        ordersModel.setRowCount(0);
        String sql = "SELECT o.id, u.name, o.total, o.status, o.created_at FROM orders o JOIN users u ON o.user_id=u.id ORDER BY o.created_at DESC";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                ordersModel.addRow(new Object[]{
                        rs.getInt(1), rs.getString(2), rs.getDouble(3), rs.getString(4), rs.getTimestamp(5)
                });
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Load orders error: " + ex.getMessage());
        }
    }

    private void advanceSelectedOrder() {
        int r = ordersTable.getSelectedRow();
        if (r < 0) { JOptionPane.showMessageDialog(this, "Select an order"); return; }
        int orderId = (Integer) ordersModel.getValueAt(r, 0);
        String status = String.valueOf(ordersModel.getValueAt(r, 3));
        String next = nextStatus(status);
        if (next == null) { JOptionPane.showMessageDialog(this, "Order already COMPLETED"); return; }
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE orders SET status=? WHERE id=?")) {
            ps.setString(1, next); ps.setInt(2, orderId); ps.executeUpdate();
            loadOrders();
        } catch (SQLException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Update status error: " + ex.getMessage());
        }
    }

    private String nextStatus(String s) {
        if (s == null) return "PENDING";
        s = s.toUpperCase();
        if ("PENDING".equals(s)) return "PREPARING";
        if ("PREPARING".equals(s)) return "COMPLETED";
        return null;
    }
}
