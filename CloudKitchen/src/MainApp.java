import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.EventObject;


public class MainApp extends JFrame {
    private CardLayout cards = new CardLayout();
    private JPanel root = new JPanel(cards);

    private JTextField emailField = new JTextField(20);
    private JPasswordField passField = new JPasswordField(20);

    // Columns: ID, Name, Price, Available, Options, Qty
    private DefaultTableModel menuModel = new DefaultTableModel(
            new Object[]{"ID", "Name", "Price", "Available", "Options", "Qty"}, 0) {
        @Override public boolean isCellEditable(int row, int col) {
            // allow editing Options (col 4) and Qty (col 5)
            return col == 4 || col == 5;
        }
        @Override public Class<?> getColumnClass(int columnIndex) {
            if (columnIndex == 0) return Integer.class;
            if (columnIndex == 2) return Double.class;
            if (columnIndex == 3) return Boolean.class;
            return Object.class;
        }
    };
    private JTable menuTable = new JTable(menuModel);

    private int currentUserId = -1;
    private String currentUserRole = null;

    public MainApp() {
        setTitle("Simple Cloud Kitchen (inline Options)");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(980, 560);
        setLocationRelativeTo(null);

        // disable auto-resize so horizontal scrollbar appears and Options is visible
        menuTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

        // install custom editor for Options column (index 4)
        menuTable.getColumnModel().getColumn(4).setCellEditor(new OptionsCellEditor(menuTable, menuModel));

        // ensure table shows horizontal scrollbar and columns visible
        menuTable.setRowSelectionAllowed(true);
        menuTable.setFillsViewportHeight(true);

        // Force reasonable default column widths
        try {
            if (menuTable.getColumnModel().getColumnCount() >= 6) {
                menuTable.getColumnModel().getColumn(0).setPreferredWidth(50);  // ID
                menuTable.getColumnModel().getColumn(1).setPreferredWidth(300); // Name
                menuTable.getColumnModel().getColumn(2).setPreferredWidth(80);  // Price
                menuTable.getColumnModel().getColumn(3).setPreferredWidth(80);  // Available
                menuTable.getColumnModel().getColumn(4).setPreferredWidth(300); // Options
                menuTable.getColumnModel().getColumn(5).setPreferredWidth(60);  // Qty
            }
        } catch (Exception ignored) {}

        // Start editing Options cell on single click so the JComboBox appears immediately
        menuTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                Point p = e.getPoint();
                int row = menuTable.rowAtPoint(p);
                int col = menuTable.columnAtPoint(p);
                if (row >= 0 && col == 4) { // column 4 = Options
                    // begin editing so the Options JComboBox editor shows
                    if (!menuTable.isEditing() || menuTable.getEditingRow() != row || menuTable.getEditingColumn() != col) {
                        menuTable.editCellAt(row, col);
                        Component editor = menuTable.getEditorComponent();
                        if (editor != null) editor.requestFocusInWindow();
                    }
                }
            }
        });

        root.add(buildLoginPanel(), "login");
        root.add(buildCustomerPanel(), "customer");
        root.add(buildAdminPanel(), "admin");

        add(root);
        cards.show(root, "login");
    }

    private JPanel buildLoginPanel() {
        JPanel p = new JPanel(new BorderLayout());
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(8,8,8,8);
        c.gridx = 0; c.gridy = 0; form.add(new JLabel("Email:"), c);
        c.gridx = 1; form.add(emailField, c);
        c.gridx = 0; c.gridy = 1; form.add(new JLabel("Password:"), c);
        c.gridx = 1; form.add(passField, c);

        c.gridy = 2; c.gridx = 0;
        JButton loginBtn = new JButton("Login");
        loginBtn.addActionListener(e -> doLogin());
        form.add(loginBtn, c);

        c.gridx = 1;
        JButton regBtn = new JButton("Register");
        regBtn.addActionListener(e -> showRegisterDialog());
        form.add(regBtn, c);

        p.add(new JLabel("<html><h1 style='text-align:center;'>Cloud Kitchen</h1></html>", SwingConstants.CENTER), BorderLayout.NORTH);
        p.add(form, BorderLayout.CENTER);
        return p;
    }

    private JPanel buildCustomerPanel() {
        JPanel p = new JPanel(new BorderLayout());
        JPanel top = new JPanel(new BorderLayout());
        JLabel lbl = new JLabel("Customer - Menu", SwingConstants.CENTER);
        lbl.setFont(lbl.getFont().deriveFont(18f));
        top.add(lbl, BorderLayout.CENTER);

        JButton logout = new JButton("Logout");
        logout.addActionListener(e -> logout());
        top.add(logout, BorderLayout.EAST);

        p.add(top, BorderLayout.NORTH);

        menuTable.setRowHeight(26);
        JScrollPane sp = new JScrollPane(menuTable);
        
        // 💡 CRITICAL FIX: Explicitly set the JTable as the viewport view.
        // This is often needed to force the headers to be displayed, which 
        // makes the table content visible.
        sp.setViewportView(menuTable); 
        
        p.add(sp, BorderLayout.CENTER);

        JPanel bottom = new JPanel();
        JButton placeOrder = new JButton("Place Order");
        placeOrder.addActionListener(e -> placeOrder());
        bottom.add(placeOrder);
        p.add(bottom, BorderLayout.SOUTH);

        return p;
    }

    private JPanel buildAdminPanel() {
        JPanel p = new JPanel(new BorderLayout());
        JPanel top = new JPanel(new BorderLayout());
        JLabel lbl = new JLabel("Admin - Menu Management", SwingConstants.CENTER);
        lbl.setFont(lbl.getFont().deriveFont(18f));
        top.add(lbl, BorderLayout.CENTER);

        JButton logout = new JButton("Logout");
        logout.addActionListener(e -> logout());
        top.add(logout, BorderLayout.EAST);
        p.add(top, BorderLayout.NORTH);

        JScrollPane sp = new JScrollPane(menuTable);
        p.add(sp, BorderLayout.CENTER);

        JPanel bot = new JPanel();
        JButton add = new JButton("Add Item");
        add.addActionListener(e -> showAddMenuDialog());
        JButton del = new JButton("Delete Selected");
        del.addActionListener(e -> deleteSelectedMenu());
        bot.add(add); bot.add(del);
        p.add(bot, BorderLayout.SOUTH);

        return p;
    }

    private void doLogin() {
        String email = emailField.getText().trim();
        String pass = new String(passField.getPassword()).trim();
        if (email.isEmpty() || pass.isEmpty()) { JOptionPane.showMessageDialog(this, "Enter both fields"); return; }

        String sql = "SELECT id,role FROM users WHERE email=? AND password=?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            ps.setString(2, pass);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    currentUserId = rs.getInt("id");
                    currentUserRole = rs.getString("role");
                    loadMenu();
                    adjustColumnWidths();
                    if ("ADMIN".equalsIgnoreCase(currentUserRole)) cards.show(root, "admin");
                    else cards.show(root, "customer");
                } else {
                    JOptionPane.showMessageDialog(this, "Invalid login");
                }
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "DB error: " + ex.getMessage());
        }
    }

    private void showRegisterDialog() {
        JTextField nameF = new JTextField();
        JTextField emailF = new JTextField();
        JPasswordField pwdF = new JPasswordField();
        Object[] fields = {"Name", nameF, "Email", emailF, "Password", pwdF};
        int ok = JOptionPane.showConfirmDialog(this, fields, "Register", JOptionPane.OK_CANCEL_OPTION);
        if (ok == JOptionPane.OK_OPTION) {
            String name = nameF.getText().trim();
            String email = emailF.getText().trim();
            String pwd = new String(pwdF.getPassword()).trim();
            if (name.isEmpty() || email.isEmpty() || pwd.isEmpty()) { JOptionPane.showMessageDialog(this, "All required"); return; }

            String sql = "INSERT INTO users (name,email,password,role) VALUES (?,?,?,'CUSTOMER')";
            try (Connection conn = DBUtil.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, name); ps.setString(2, email); ps.setString(3, pwd);
                ps.executeUpdate();
                JOptionPane.showMessageDialog(this, "Registered. You can login now.");
            } catch (SQLException ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "Registration failed: " + ex.getMessage());
            }
        }
    }

    private void loadMenu() {
        menuModel.setRowCount(0);
        String sql = "SELECT id,name,price,available,options FROM menu_items";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String options = rs.getString("options");
                if (options == null) options = "";
                String defaultOption = "";
                if (!options.trim().isEmpty()) {
                    String[] opts = options.split("\\s*,\\s*");
                    if (opts.length > 0) defaultOption = opts[0];
                }
                menuModel.addRow(new Object[]{
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getDouble("price"),
                        rs.getBoolean("available"),
                        defaultOption.isEmpty() ? "" : defaultOption,
                        10
                });
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Load menu error: " + ex.getMessage());
        }
    }


    private void placeOrder() {
        ArrayList<OrderItemSelection> items = new ArrayList<>();
        double total = 0;
        for (int r = 0; r < menuModel.getRowCount(); r++) {
            int id = (Integer) menuModel.getValueAt(r, 0);
            String name = String.valueOf(menuModel.getValueAt(r,1));
            double price = (Double) menuModel.getValueAt(r, 2);
            // options cell stores the selected option (string) for that row
            String selectedOption = String.valueOf(menuModel.getValueAt(r, 4));
            Object qtyObj = menuModel.getValueAt(r, 5);
            int qty = 0;
            try { qty = Integer.parseInt(qtyObj.toString()); } catch (Exception ex) { qty = 0; }
            if (qty > 0) {
                // If item has options in DB but user left selectedOption empty -> ask to select
                if (hasOptionsInDB(id)) {
                    if (selectedOption == null || selectedOption.trim().isEmpty()) {
                        JOptionPane.showMessageDialog(this, "Please select option for " + name);
                        return;
                    }
                } else {
                    selectedOption = null; // no option stored
                }
                items.add(new OrderItemSelection(id, qty, selectedOption));
                total += price * qty;
            }
        }
        if (items.isEmpty()) { JOptionPane.showMessageDialog(this, "Choose at least one item"); return; }

        String insertOrder = "INSERT INTO orders (user_id, total, status) VALUES (?,?,?)";
        String insertItem = "INSERT INTO order_items (order_id, menu_item_id, qty, price, option_selected) VALUES (?,?,?,?,?)";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement psOrder = conn.prepareStatement(insertOrder, Statement.RETURN_GENERATED_KEYS);
             PreparedStatement psItem = conn.prepareStatement(insertItem)) {

            conn.setAutoCommit(false);
            psOrder.setInt(1, currentUserId);
            psOrder.setDouble(2, total);
            psOrder.setString(3, "PENDING");
            psOrder.executeUpdate();

            try (ResultSet gk = psOrder.getGeneratedKeys()) {
                if (gk.next()) {
                    int orderId = gk.getInt(1);
                    for (OrderItemSelection it : items) {
                        double price = getMenuPrice(conn, it.menuId);
                        psItem.setInt(1, orderId);
                        psItem.setInt(2, it.menuId);
                        psItem.setInt(3, it.qty);
                        psItem.setDouble(4, price);
                        psItem.setString(5, it.optionSelected); // may be null
                        psItem.addBatch();
                    }
                    psItem.executeBatch();
                    conn.commit();
                    JOptionPane.showMessageDialog(this, "Order placed! Order ID: " + orderId + " | Total: " + total);
                    for (int r = 0; r < menuModel.getRowCount(); r++) {
                        menuModel.setValueAt(0, r, 5); // reset qty
                    }
                } else {
                    conn.rollback();
                    JOptionPane.showMessageDialog(this, "Cannot create order");
                }
            }
            conn.setAutoCommit(true);
        } catch (SQLException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Order error: " + ex.getMessage());
        }
    }

    private boolean hasOptionsInDB(int menuId) {
        String sql = "SELECT options FROM menu_items WHERE id=?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, menuId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String opts = rs.getString("options");
                    return opts != null && !opts.trim().isEmpty();
                }
            }
        } catch (SQLException ex) { /* ignore */ }
        return false;
    }

    private double getMenuPrice(Connection conn, int menuId) throws SQLException {
        String sql = "SELECT price FROM menu_items WHERE id=?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, menuId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getDouble("price");
            }
        }
        return 0.0;
    }

    private void showAddMenuDialog() {
        JTextField name = new JTextField();
        JTextField price = new JTextField();
        JCheckBox avail = new JCheckBox("Available", true);
        JTextField options = new JTextField(); // comma-separated options
        Object[] fields = {"Name", name, "Price", price, "Options (comma separated, optional)", options, avail};
        int ok = JOptionPane.showConfirmDialog(this, fields, "Add Menu Item", JOptionPane.OK_CANCEL_OPTION);
        if (ok == JOptionPane.OK_OPTION) {
            try {
                double p = Double.parseDouble(price.getText().trim());
                String opts = options.getText().trim();
                if (opts.isEmpty()) opts = null;
                String sql = "INSERT INTO menu_items (name,price,available,options) VALUES (?,?,?,?)";
                try (Connection conn = DBUtil.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, name.getText().trim());
                    ps.setDouble(2, p);
                    ps.setBoolean(3, avail.isSelected());
                    if (opts == null) ps.setNull(4, Types.VARCHAR); else ps.setString(4, opts);
                    ps.executeUpdate();
                    loadMenu();
                    adjustColumnWidths();
                }
            } catch (NumberFormatException nfe) {
                JOptionPane.showMessageDialog(this, "Bad price");
            } catch (SQLException ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "DB error: " + ex.getMessage());
            }
        }
    }

    private void deleteSelectedMenu() {
        int r = menuTable.getSelectedRow();
        if (r < 0) { JOptionPane.showMessageDialog(this, "Select a row"); return; }
        int id = (Integer) menuModel.getValueAt(r, 0);
        int ok = JOptionPane.showConfirmDialog(this, "Delete menu id " + id + " ?", "Confirm", JOptionPane.YES_NO_OPTION);
        if (ok == JOptionPane.YES_OPTION) {
            String sql = "DELETE FROM menu_items WHERE id=?";
            try (Connection conn = DBUtil.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, id); ps.executeUpdate();
                loadMenu();
                adjustColumnWidths();
            } catch (SQLException ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "Delete error: " + ex.getMessage());
            }
        }
    }

    private void logout() {
        currentUserId = -1; currentUserRole = null;
        emailField.setText(""); passField.setText("");
        cards.show(root, "login");
    }

    private void adjustColumnWidths() {
        // ensure Options column is visible and sized
        try {
            if (menuTable.getColumnModel().getColumnCount() >= 6) {
                menuTable.getColumnModel().getColumn(0).setPreferredWidth(50);  // ID
                menuTable.getColumnModel().getColumn(1).setPreferredWidth(300); // Name
                menuTable.getColumnModel().getColumn(2).setPreferredWidth(80);  // Price
                menuTable.getColumnModel().getColumn(3).setPreferredWidth(80);  // Available
                menuTable.getColumnModel().getColumn(4).setPreferredWidth(300); // Options
                menuTable.getColumnModel().getColumn(5).setPreferredWidth(60);  // Qty
            }
        } catch (Exception ignored) {}
    }

    // custom editor: creates a JComboBox based on the options stored in the DB for the row
    private static class OptionsCellEditor extends AbstractCellEditor implements TableCellEditor {
        private final JTable table;
        private final DefaultTableModel model;
        private JComponent editorComponent = new JTextField();

        public OptionsCellEditor(JTable table, DefaultTableModel model) {
            this.table = table;
            this.model = model;
        }

        @Override
        public Object getCellEditorValue() {
            if (editorComponent instanceof JComboBox) {
                Object sel = ((JComboBox<?>) editorComponent).getSelectedItem();
                return sel == null ? "" : sel.toString();
            } else if (editorComponent instanceof JTextField) {
                return ((JTextField) editorComponent).getText();
            }
            return null;
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            Object menuIdObj = model.getValueAt(row, 0);
            if (menuIdObj == null) return new JTextField("");
            int menuId = (Integer) menuIdObj;

            try (Connection conn = DBUtil.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT options FROM menu_items WHERE id=?")) {
                ps.setInt(1, menuId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String opts = rs.getString("options");
                        if (opts != null && !opts.trim().isEmpty()) {
                            String[] arr = opts.split("\\s*,\\s*");
                            JComboBox<String> cb = new JComboBox<>(arr);
                            // if model has a selected value, set it; otherwise choose first
                            String selected = value == null ? "" : value.toString();
                            if (selected.isEmpty()) cb.setSelectedIndex(0);
                            else cb.setSelectedItem(selected);
                            editorComponent = cb;
                            return cb;
                        }
                    }
                }
            } catch (SQLException ex) {
                // fallback to text field if DB error
            }

            // no options in DB -> use editable text field (user can type or leave blank)
            JTextField tf = new JTextField(value == null ? "" : value.toString());
            editorComponent = tf;
            return tf;
        }

        // allow click to start editing immediately
        @Override public boolean isCellEditable(EventObject e) { return true; }
    }

    private static class OrderItemSelection {
        int menuId;
        int qty;
        String optionSelected;
        OrderItemSelection(int m, int q, String o) { menuId = m; qty = q; optionSelected = o; }
    }

    public static void main(String[] args) {
        // Nimbus look & feel if available
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels())
                if ("Nimbus".equals(info.getName())) { UIManager.setLookAndFeel(info.getClassName()); break; }
        } catch (Exception e) { /* ignore */ }

        SwingUtilities.invokeLater(() -> {
            MainApp app = new MainApp();
            app.setVisible(true);
        });
    }
}
