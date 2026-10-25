package com.quickpay.wallet;

import java.security.MessageDigest;
import java.sql.*;
import java.util.Scanner;

public class QuickPayApp {

    // ===================== DB CONFIG =====================
    private static final String DB_URL  = "jdbc:mysql://localhost:3306/quickpay_db";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "YOUR_MYSQL_PASSWORD"; // don't push the real one to GitHub

    private static final Scanner sc = new Scanner(System.in);

    private static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
    }

    // ===================== MAIN MENU =====================
    public static void main(String[] args) {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            System.out.println("MySQL driver not found: " + e.getMessage());
            return;
        }

        while (true) {
            System.out.println("\n========== QUICKPAY WALLET ==========");
            System.out.println("1. Create Wallet");
            System.out.println("2. View Wallet");
            System.out.println("3. Add Money");
            System.out.println("4. Withdraw Money");
            System.out.println("5. Send Money");
            System.out.println("6. Check Balance");
            System.out.println("7. Transaction History");
            System.out.println("8. Close Wallet");
            System.out.println("9. Exit");
            int choice = readInt("Enter choice: ");

            switch (choice) {
                case 1 -> createWallet();
                case 2 -> viewWallet();
                case 3 -> addMoney();
                case 4 -> withdrawMoney();
                case 5 -> sendMoney();
                case 6 -> checkBalance();
                case 7 -> transactionHistory();
                case 8 -> closeWallet();
                case 9 -> {
                    System.out.println("Thank you for using QuickPay!");
                    sc.close();
                    return;
                }
                default -> System.out.println("Invalid choice.");
            }
        }
    }

    // ===================== 1. CREATE WALLET =====================
    private static void createWallet() {
        System.out.print("Owner name: ");
        String name = sc.nextLine().trim();
        if (name.isEmpty()) { System.out.println("Name cannot be empty."); return; }
        String pin = readPin();
        double initial = readDouble("Opening amount (0 for none): ");
        if (initial < 0) { System.out.println("Amount cannot be negative."); return; }

        String q = "INSERT INTO wallets (owner_name, pin_hash, balance) VALUES (?,?,?)";
        try (Connection con = getConnection()) {
            con.setAutoCommit(false);
            try (PreparedStatement ps = con.prepareStatement(q, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, name);
                ps.setString(2, hash(pin));
                ps.setDouble(3, initial);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    rs.next();
                    int id = rs.getInt(1);
                    if (initial > 0) logTxn(con, id, "ADD_MONEY", initial, initial, null);
                    con.commit();
                    System.out.println("Wallet created! Your Wallet ID is " + id);
                }
            } catch (SQLException e) {
                con.rollback();
                throw e;
            }
        } catch (SQLException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    // ===================== 2. VIEW WALLET =====================
    private static void viewWallet() {
        int id = readInt("Wallet ID: ");
        String pin = readPin();
        try (Connection con = getConnection()) {
            if (!checkPin(con, id, pin)) { System.out.println("Invalid Wallet ID or PIN."); return; }
            String q = "SELECT wallet_id, owner_name, balance, created_at FROM wallets WHERE wallet_id=?";
            try (PreparedStatement ps = con.prepareStatement(q)) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        System.out.println("\n----- Wallet Details -----");
                        System.out.println("Wallet ID : " + rs.getInt("wallet_id"));
                        System.out.println("Owner     : " + rs.getString("owner_name"));
                        System.out.println("Balance   : " + rs.getDouble("balance"));
                        System.out.println("Created   : " + rs.getTimestamp("created_at"));
                    }
                }
            }
        } catch (SQLException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    // ===================== 3. ADD MONEY =====================
    private static void addMoney() {
        int id = readInt("Wallet ID: ");
        double amt = readDouble("Amount to add: ");
        if (amt <= 0) { System.out.println("Amount must be greater than 0."); return; }

        try (Connection con = getConnection()) {
            con.setAutoCommit(false);
            try {
                double bal = lockBalance(con, id);
                if (bal < 0) {
                    System.out.println("Wallet not found.");
                    con.rollback();
                    return;
                }
                double newBal = bal + amt;
                setBalance(con, id, newBal);
                logTxn(con, id, "ADD_MONEY", amt, newBal, null);
                con.commit();
                System.out.println("Added " + amt + ". New balance: " + newBal);
            } catch (SQLException e) {
                con.rollback();
                throw e;
            }
        } catch (SQLException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    // ===================== 4. WITHDRAW MONEY =====================
    private static void withdrawMoney() {
        int id = readInt("Wallet ID: ");
        String pin = readPin();
        double amt = readDouble("Amount to withdraw: ");
        if (amt <= 0) { System.out.println("Amount must be greater than 0."); return; }

        try (Connection con = getConnection()) {
            con.setAutoCommit(false);
            try {
                if (!checkPin(con, id, pin)) {
                    System.out.println("Invalid Wallet ID or PIN.");
                    con.rollback();
                    return;
                }
                double bal = lockBalance(con, id);
                if (amt > bal) {
                    System.out.println("Insufficient balance. Available: " + bal);
                    con.rollback();
                    return;
                }
                double newBal = bal - amt;
                setBalance(con, id, newBal);
                logTxn(con, id, "WITHDRAW", amt, newBal, null);
                con.commit();
                System.out.println("Withdrawn " + amt + ". New balance: " + newBal);
            } catch (SQLException e) {
                con.rollback();
                throw e;
            }
        } catch (SQLException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    // ===================== 5. SEND MONEY =====================
    private static void sendMoney() {
        int from = readInt("Your Wallet ID: ");
        String pin = readPin();
        int to = readInt("Receiver Wallet ID: ");
        double amt = readDouble("Amount to send: ");
        if (amt <= 0) { System.out.println("Amount must be greater than 0."); return; }
        if (from == to) { System.out.println("Cannot send money to your own wallet."); return; }

        try (Connection con = getConnection()) {
            con.setAutoCommit(false);
            try {
                if (!checkPin(con, from, pin)) {
                    System.out.println("Invalid Wallet ID or PIN.");
                    con.rollback();
                    return;
                }
                // lock the smaller wallet id first so two transfers can never deadlock
                int first = Math.min(from, to), second = Math.max(from, to);
                double b1 = lockBalance(con, first);
                double b2 = lockBalance(con, second);
                if (b1 < 0 || b2 < 0) {
                    System.out.println("Receiver wallet not found.");
                    con.rollback();
                    return;
                }
                double fromBal = (from == first) ? b1 : b2;
                double toBal   = (to == first) ? b1 : b2;

                if (amt > fromBal) {
                    System.out.println("Insufficient balance. Available: " + fromBal);
                    con.rollback();
                    return;
                }
                setBalance(con, from, fromBal - amt);
                setBalance(con, to, toBal + amt);
                logTxn(con, from, "SEND", amt, fromBal - amt, to);
                logTxn(con, to, "RECEIVE", amt, toBal + amt, from);
                con.commit();
                System.out.println("Payment Successful! New balance: " + (fromBal - amt));
            } catch (SQLException e) {
                con.rollback();
                throw e;
            }
        } catch (SQLException e) {
            System.out.println("Payment Failed: " + e.getMessage());
        }
    }

    // ===================== 6. CHECK BALANCE =====================
    private static void checkBalance() {
        int id = readInt("Wallet ID: ");
        String pin = readPin();
        try (Connection con = getConnection()) {
            if (!checkPin(con, id, pin)) { System.out.println("Invalid Wallet ID or PIN."); return; }
            try (PreparedStatement ps = con.prepareStatement("SELECT balance FROM wallets WHERE wallet_id=?")) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) System.out.println("Current balance: " + rs.getDouble("balance"));
                }
            }
        } catch (SQLException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    // ===================== 7. TRANSACTION HISTORY =====================
    private static void transactionHistory() {
        int id = readInt("Wallet ID: ");
        String pin = readPin();
        try (Connection con = getConnection()) {
            if (!checkPin(con, id, pin)) { System.out.println("Invalid Wallet ID or PIN."); return; }
            String q = "SELECT txn_id, txn_type, amount, balance_after, other_wallet_id, txn_time "
                    + "FROM wallet_transactions WHERE wallet_id=? ORDER BY txn_time DESC, txn_id DESC LIMIT 20";
            try (PreparedStatement ps = con.prepareStatement(q)) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    System.out.println("\n----- Last 20 Transactions -----");
                    System.out.printf("%-6s %-12s %-12s %-14s %-12s %s%n",
                            "ID", "TYPE", "AMOUNT", "BALANCE", "OTHER WALLET", "TIME");
                    boolean any = false;
                    while (rs.next()) {
                        any = true;
                        int other = rs.getInt("other_wallet_id");
                        String otherText = rs.wasNull() ? "-" : String.valueOf(other);
                        System.out.printf("%-6d %-12s %-12.2f %-14.2f %-12s %s%n",
                                rs.getInt("txn_id"), rs.getString("txn_type"),
                                rs.getDouble("amount"), rs.getDouble("balance_after"),
                                otherText, rs.getTimestamp("txn_time"));
                    }
                    if (!any) System.out.println("No transactions yet.");
                }
            }
        } catch (SQLException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    // ===================== 8. CLOSE WALLET =====================
    private static void closeWallet() {
        int id = readInt("Wallet ID: ");
        String pin = readPin();
        System.out.print("Are you sure you want to close this wallet? (yes/no): ");
        if (!sc.nextLine().trim().equalsIgnoreCase("yes")) {
            System.out.println("Cancelled.");
            return;
        }
        try (Connection con = getConnection()) {
            con.setAutoCommit(false);
            try {
                if (!checkPin(con, id, pin)) {
                    System.out.println("Invalid Wallet ID or PIN.");
                    con.rollback();
                    return;
                }
                double bal = lockBalance(con, id);
                if (bal > 0) {
                    System.out.println("Withdraw the remaining balance (" + bal + ") before closing the wallet.");
                    con.rollback();
                    return;
                }
                try (PreparedStatement ps = con.prepareStatement("DELETE FROM wallets WHERE wallet_id=?")) {
                    ps.setInt(1, id);
                    ps.executeUpdate();   // its transactions are removed by ON DELETE CASCADE
                }
                con.commit();
                System.out.println("Wallet " + id + " closed.");
            } catch (SQLException e) {
                con.rollback();
                throw e;
            }
        } catch (SQLException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    // ===================== DB HELPERS =====================
    /** Locks the wallet row and returns its balance, or -1 if the wallet doesn't exist. */
    private static double lockBalance(Connection con, int id) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT balance FROM wallets WHERE wallet_id=? FOR UPDATE")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble("balance") : -1;
            }
        }
    }

    private static boolean checkPin(Connection con, int id, String pin) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT pin_hash FROM wallets WHERE wallet_id=?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getString("pin_hash").equals(hash(pin));
            }
        }
    }

    private static void setBalance(Connection con, int id, double newBalance) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("UPDATE wallets SET balance=? WHERE wallet_id=?")) {
            ps.setDouble(1, newBalance);
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }

    private static void logTxn(Connection con, int id, String type, double amt,
                               double balAfter, Integer otherWallet) throws SQLException {
        String q = "INSERT INTO wallet_transactions (wallet_id, txn_type, amount, balance_after, other_wallet_id) "
                + "VALUES (?,?,?,?,?)";
        try (PreparedStatement ps = con.prepareStatement(q)) {
            ps.setInt(1, id);
            ps.setString(2, type);
            ps.setDouble(3, amt);
            ps.setDouble(4, balAfter);
            if (otherWallet == null) ps.setNull(5, Types.INTEGER);
            else ps.setInt(5, otherWallet);
            ps.executeUpdate();
        }
    }

    private static String hash(String pin) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest(pin.getBytes())) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ===================== INPUT HELPERS =====================
    private static int readInt(String msg) {
        while (true) {
            System.out.print(msg);
            try {
                return Integer.parseInt(sc.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid number.");
            }
        }
    }

    private static double readDouble(String msg) {
        while (true) {
            System.out.print(msg);
            try {
                return Double.parseDouble(sc.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid amount.");
            }
        }
    }

    private static String readPin() {
        while (true) {
            System.out.print("4-digit PIN: ");
            String pin = sc.nextLine().trim();
            if (pin.matches("\\d{4}")) return pin;
            System.out.println("PIN must be exactly 4 digits.");
        }
    }
}