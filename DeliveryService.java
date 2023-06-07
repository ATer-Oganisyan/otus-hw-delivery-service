import java.io.IOException;
import java.io.OutputStream;
import java.io.*;
import java.net.InetSocketAddress;
import java.util.*;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.sql.*;
import java.net.http.HttpClient;

public class DeliveryService {

    static private int SLOT_STATUS_AVAIALBALE = 1;
    static private int SLOT_HOLDED = 2;
    static private int SLOT_USED = 3;
    static Connection connection = null;

    public static void main(String[] args) throws Exception {
        String version = args[5];
        String host = args[0];
        String port = args[1];
        String user = args[2];
        String password = args[3];
        String db = args[4];
        System.out.println("Hardcode version: v7");
        System.out.println("Config version: " + version);
        System.out.println(host);
        System.out.println(port);
        System.out.println(user);
        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        server.createContext("/", new MyHandler());
        server.setExecutor(null); // creates a default executor
	    Class.forName("com.mysql.cj.jdbc.Driver");
        connection = DriverManager.getConnection("jdbc:mysql://" + host + ":"+port + "/"+ db, user, password);
        server.start();
    }

    static class MyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            System.out.println("Request accepted");
            String path = t.getRequestURI().getPath();
            System.out.println("Path: " + path);
            if ("/health".equals(path)) {
                routeHealth(t);
                System.out.println("matched");
            } else if ("/accquire-slot".equals(path)) { // by id
                accquireSlot(t);
                System.out.println("accquireItem");
            } else if ("/add-slot".equals(path)) {
                addSlot(t);
                System.out.println("release-order-items");
            } else if ("/release-slot".equals(path)) { // by id
                releaseSlot(t);
                System.out.println("release-item");
            } else if ("/get-accessable-slots".equals(path)) {
                getAvailableSlots(t);
                System.out.println("fill-item");
            }  else if ("/commit-delivery".equals(path)) { // by order_id
                commitDelivery(t);
                System.out.println("fill-item");
            } else {
                String response = "{\"status\": \"not found\"}";
                t.sendResponseHeaders(404, response.length());
                OutputStream os = t.getResponseBody();
                os.write(response.getBytes());
                os.close();
                System.out.println("not matched");
            }
        }
    }


    static private void routeHealth(HttpExchange t) throws IOException {
        System.out.println("Request accepted");
        String response = "{\"status\": \"OK\"}";
        t.sendResponseHeaders(200, response.length());
        OutputStream os = t.getResponseBody();
        os.write(response.getBytes());
        os.close();
    }

    static private void accquireSlot(HttpExchange t) throws IOException {
        System.out.println("accquireItem accepted");
        String r;
        try {
            Map<String, String> q = postToMap(buf(t.getRequestBody()));
            String orderId = q.get("order_id");
            String slotId = q.get("slot_id");
            connection.setAutoCommit(false);

            String sql = "select order_id from slots where id = " + slotId + " and status_id = " + SLOT_STATUS_AVAIALBALE + " for update";
            System.out.println("select available slot sql: " + sql);
            Statement stmt=connection.createStatement();
            ResultSet rs=stmt.executeQuery(sql);
            r = "";
            if (!rs.next()) {
                r = "slot is unavailable anymore";
                System.out.println("send headers");
                t.sendResponseHeaders(409, r.length());
                System.out.println("slot is unavailable anymore");
                OutputStream os = t.getResponseBody();
                os.write(r.getBytes());
                os.close();
                return;
            }

            String slotOrderId = rs.getInt("order_id") + "";
            if (slotOrderId.equals(orderId)) {
                r = "";
                System.out.println("send headers");
                t.sendResponseHeaders(200, r.length());
                OutputStream os = t.getResponseBody();
                os.write(r.getBytes());
                os.close();
                return;
            }

            stmt=connection.createStatement();
            sql = "update slots set order_id = " + orderId + ", status_id = " + SLOT_HOLDED + " where id = " + slotId;
            System.out.println("accquire slot update sql: " + sql);
            stmt.executeUpdate(sql);
            r = "";
            System.out.println("send headers");
            t.sendResponseHeaders(200, r.length());
            OutputStream os = t.getResponseBody();
            os.write(r.getBytes());
            os.close();
            commitTransaction();
        } catch (Throwable e) {
            rollbackTransaction();
            System.out.println("error: " + e.getMessage());
            r = "internal server error";
            t.sendResponseHeaders(500, r.length());
            OutputStream os = t.getResponseBody();
            os.write(r.getBytes());
            os.close();
        } finally {
            try {
                System.out.println("setAutoCommit = true");
                connection.setAutoCommit(true);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }

    static private void commitDelivery(HttpExchange t) throws IOException {
        System.out.println("commitDelivery accepted");
        String r;
        try {
            Map<String, String> q = postToMap(buf(t.getRequestBody()));
            String orderId = q.get("order_id");
            String slotId = q.get("slot_id");
            connection.setAutoCommit(false);

            String sql = "select order_id from slots where id = " + slotId + " and status_id = " + SLOT_STATUS_AVAIALBALE + " and order_id = " + orderId + " for update";
            System.out.println("select commitDelivery slot sql: " + sql);
            Statement stmt=connection.createStatement();
            ResultSet rs=stmt.executeQuery(sql);
            r = "";
            if (!rs.next()) {
                r = "holded slot is not found";
                System.out.println("send headers");
                t.sendResponseHeaders(409, r.length());
                System.out.println("holded slot is not found");
                OutputStream os = t.getResponseBody();
                os.write(r.getBytes());
                os.close();
                return;
            }

            stmt=connection.createStatement();
            sql = "update slots set status_id = " + SLOT_USED + " where id = " + slotId;
            System.out.println("commitDelivery slot update sql: " + sql);
            stmt.executeUpdate(sql);
            r = "";
            System.out.println("send headers");
            t.sendResponseHeaders(200, r.length());
            OutputStream os = t.getResponseBody();
            os.write(r.getBytes());
            os.close();
            commitTransaction();
        } catch (Throwable e) {
            rollbackTransaction();
            System.out.println("error: " + e.getMessage());
            r = "internal server error";
            t.sendResponseHeaders(500, r.length());
            OutputStream os = t.getResponseBody();
            os.write(r.getBytes());
            os.close();
        } finally {
            try {
                System.out.println("setAutoCommit = true");
                connection.setAutoCommit(true);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }

    static private String getStatusById(int status) {
        if (status == 2) return "In progress";
        if (status == 3) return "Done";
        return "Created";
    }

    static private void getAvailableSlots(HttpExchange t) throws IOException {
        System.out.println("getAvailableSlots request accepted");
        String r;
        System.out.println("start try-catch");
        try {
            String sql = "select id, slot_description from slots where order_id is null and status_id = " + SLOT_STATUS_AVAIALBALE;
            Statement stmt=connection.createStatement();
            ResultSet rs=stmt.executeQuery(sql);
            System.out.println("getAvailableSlots sql: " + sql);
            List<String> items = new ArrayList<>();
            while (rs.next()) {
                String id = "" + rs.getInt(1);
                String slotDescription = rs.getString(2);
                r = "id:" + id + ",slot_description:" + slotDescription;
                items.add(r);
            }
            r = String.join("\n", items);
            t.sendResponseHeaders(200, r.length());
            System.out.println("success");
        } catch (Throwable e) {
            System.out.println("error: " + e.getMessage());
            r = "internal server error";
            t.sendResponseHeaders(500, r.length());
        }
        System.out.println("send body");
        OutputStream os = t.getResponseBody();
        os.write(r.getBytes());
        os.close();
    }

    static private void commitTransaction() {
        try {
            connection.commit();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    static private void rollbackTransaction() {
        try {
            connection.rollback();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    static private void addSlot(HttpExchange t) throws IOException {
        System.out.println("addSlot");
        try {
            Map<String, String> q = postToMap(buf(t.getRequestBody()));
            String slotDescription = q.get("slot_description");
            Statement stmt=connection.createStatement();
            String sql = "insert into slots (slot_description) values (\"" + slotDescription + "\")";
            System.out.println("addSlot sql: " + sql);
            stmt.executeUpdate(sql);
            String r = "";
            System.out.println("send headers");
            t.sendResponseHeaders(200, r.length());
            OutputStream os = t.getResponseBody();
            os.write(r.getBytes());
            os.close();
            System.out.println("success");
        } catch (Throwable e) {
            System.out.println("error: " + e.getMessage());
            String r = "internal server error";
            t.sendResponseHeaders(500, r.length());
            OutputStream os = t.getResponseBody();
            os.write(r.getBytes());
            os.close();
        }
    }

    static private void releaseSlot(HttpExchange t) throws IOException {
        System.out.println("releaseSlot request accepted");
        String r;
        System.out.println("start try-catch");
        try {
            Map<String, String> q = postToMap(buf(t.getRequestBody()));
            String orderId = q.get("order_id");
            String slotId = q.get("slot_id");
            connection.setAutoCommit(false);

            String sql = "select order_id from slots where id = " + slotId + " and order_id = " + orderId + " and status_id = " + SLOT_HOLDED + " for update";
            System.out.println("select releaseSlot sql: " + sql);
            Statement stmt=connection.createStatement();
            ResultSet rs=stmt.executeQuery(sql);
            r = "";
            if (!rs.next()) {
                r = "holded slot is not found";
                System.out.println("send headers");
                t.sendResponseHeaders(409, r.length());
                System.out.println("holded slot is not found");
                OutputStream os = t.getResponseBody();
                os.write(r.getBytes());
                os.close();
                return;
            }

            stmt=connection.createStatement();
            sql = "update slots set order_id = null, status_id = " + SLOT_STATUS_AVAIALBALE + " where id = " + slotId;
            System.out.println("release slot update sql: " + sql);
            stmt.executeUpdate(sql);
            r = "";
            System.out.println("send headers");
            t.sendResponseHeaders(200, r.length());
            OutputStream os = t.getResponseBody();
            os.write(r.getBytes());
            os.close();
            commitTransaction();
        } catch (Throwable e) {
            System.out.println("error: " + e.getMessage());
            r = "internal server error";
            t.sendResponseHeaders(500, r.length());
        }
        System.out.println("send body");
        OutputStream os = t.getResponseBody();
        os.write(r.getBytes());
        os.close();

    }

    static private Map<String, String> queryToMap(String query) {
        if(query == null) {
            return new HashMap<>();
        }
        Map<String, String> result = new HashMap<>();
        for (String param : query.split("&")) {
            String[] entry = param.split("=");
            if (entry.length > 1) {
                result.put(entry[0], entry[1]);
            }else{
                result.put(entry[0], "");
            }
        }
        return result;
    }

    static private Map<String, String> postToMap(StringBuilder body){
        String[] parts = body
                .toString()
                .replaceAll("\r", "")
                .split("\n");
        Map<String, String> result = new HashMap<>();
        for (String part: parts) {
            String[] keyVal = part.split(":");
            result.put(keyVal[0], keyVal[1]);
        }
        System.out.println("postToMap: " + result.toString());
        return result;
    }

    static private List<List<String>> postToList(StringBuilder body){
        String[] parts = body
                .toString()
                .replaceAll("\r", "")
                .split("\n");
        List<List<String>> result = new ArrayList<>();
        for (String part: parts) {
            String[] keyVal = part.split(":");
            List<String> l = new ArrayList<>();
            l.add(0, keyVal[0]);
            l.add(1, keyVal[1]);
            result.add(l);
        }
        System.out.println("postToList: " + result.toString());
        return result;
    }

    static private StringBuilder buf(InputStream inp)  throws UnsupportedEncodingException, IOException {
        InputStreamReader isr =  new InputStreamReader(inp,"utf-8");
        BufferedReader br = new BufferedReader(isr);
        int b;
        StringBuilder buf = new StringBuilder(512);
        while ((b = br.read()) != -1) {
            buf.append((char) b);
        }
        br.close();
        isr.close();
        System.out.println("buf : " + buf);
        return buf;
    }
}