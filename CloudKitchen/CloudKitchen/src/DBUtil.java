import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBUtil {
    // Configuration can be provided via environment variables:
    // CLOUDKITCHEN_DB_URL, CLOUDKITCHEN_DB_USER, CLOUDKITCHEN_DB_PASS
    // Falls back to the defaults below if env vars are not set.
    private static final String URL = firstNonEmpty(
            System.getenv("CLOUDKITCHEN_DB_URL"),
            "jdbc:mysql://localhost:3306/cloud_kitchen?useSSL=false&serverTimezone=UTC"
    );
    private static final String USER = firstNonEmpty(
            System.getenv("CLOUDKITCHEN_DB_USER"),
            "root"
    );
    private static final String PASS = firstNonEmpty(
            System.getenv("CLOUDKITCHEN_DB_PASS"),
            "asdfghjkl;'"
    );

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

    private static String firstNonEmpty(String a, String b) {
        if (a != null && !a.trim().isEmpty()) return a;
        return b;
    }
}
