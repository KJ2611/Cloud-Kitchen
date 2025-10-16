import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBUtil {
    // <<-- CHANGE these to match your MySQL setup:
    private static final String URL = "jdbc:mysql://localhost:3306/cloud_kitchen?useSSL=false&serverTimezone=UTC";
    private static final String USER = "root";
    private static final String PASS = "asdfghjkl;'";
    // ----------------------------------------------------

    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException ex) {
            ex.printStackTrace();
        }
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASS);
    }
}
