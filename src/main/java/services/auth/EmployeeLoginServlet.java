package services.auth;

import com.google.gson.JsonObject;
import data.user.Employee;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jasypt.util.password.StrongPasswordEncryptor;
import recaptcha.RecaptchaVerifyUtils;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class EmployeeLoginServlet extends HttpServlet {
    private DataSource dataSource;

    @Override
    public void init() throws ServletException {
        try {
            dataSource = (DataSource) new InitialContext().lookup("java:comp/env/jdbc/moviedb");
        } catch (NamingException e) {
            e.printStackTrace();
        }
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");

        // Get reCaptcha param
        // Turn off reCaptcha to test Jmeter
//        String recaptchaResponseParam = request.getParameter("g-recaptcha-response");

        // Verify reCaptcha
//        try {
//            RecaptchaVerifyUtils.verify(recaptchaResponseParam);
//        } catch (Exception e) {
//            JsonObject jsonResult = new JsonObject();
//            jsonResult.addProperty("status", "fail");
//            jsonResult.addProperty("message", "Invalid reCaptcha");
//            response.setStatus(400);
//            response.getWriter().write(jsonResult.toString());
//            return;
//        }

        // Get email, password params
        String email = request.getParameter("email");
        String password = request.getParameter("password");

        JsonObject responseJsonObject = new JsonObject();
        String retrievedEmail = "";
        String retrievedPassword = "";
        String retrievedFullname = "";

        // Get customer info from database
        try (Connection connection = dataSource.getConnection()) {
            String query = "SELECT e.* FROM employees as e WHERE e.email = ? ";

            PreparedStatement statement = connection.prepareStatement(query);

            statement.setString(1, email);

            ResultSet rs = statement.executeQuery();

            if (rs.next()) {
                // Record found
                retrievedEmail = rs.getString("email");
                retrievedPassword = rs.getString("password");
                retrievedFullname = rs.getString("fullname");
            } else {
                // No record found
                response.setStatus(404);
                responseJsonObject.addProperty("status", "fail");
                responseJsonObject.addProperty("message", "Employee not found");
                response.getWriter().write(responseJsonObject.toString());
                return;
            }

            rs.close();
            statement.close();
        } catch (Exception e) {
            // Query fail
            response.setStatus(500);
            responseJsonObject.addProperty("status", "fail");
            responseJsonObject.addProperty("message", "internal server error");
            response.getWriter().write(responseJsonObject.toString());
            return;
        }

        boolean isValidPassword  = new StrongPasswordEncryptor().checkPassword(password, retrievedPassword);
        if (email.equals(retrievedEmail) && isValidPassword) {
            // Login success
            Employee user = new Employee(retrievedEmail, retrievedFullname);
            response.setStatus(200);
            request.getSession().setAttribute("user", user);
            request.getSession().setAttribute("role", "employee");
            responseJsonObject.addProperty("status", "success");
            responseJsonObject.addProperty("message", "success");
        } else {
            // Login fail
            request.getServletContext().log("Login failed");
            if (!email.equals(retrievedEmail)) {
                response.setStatus(400);
                responseJsonObject.addProperty("status", "fail");
                responseJsonObject.addProperty("message", "user with " + email + " does not exist");
            } else {
                response.setStatus(400);
                responseJsonObject.addProperty("status", "fail");
                responseJsonObject.addProperty("message", "incorrect password");
            }
        }

        response.getWriter().write(responseJsonObject.toString());
    }
}
