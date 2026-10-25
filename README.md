# QuickPay Digital Wallet

A console-based digital wallet built with **Java, JDBC and MySQL**. Users can create a PIN-protected wallet, add and withdraw money, send money to other wallets, and view their transaction history.

The focus of this project is **data safety**: money transfers run as database transactions (commit / rollback), wallet rows are locked to prevent conflicts, all queries use `PreparedStatement`, and PINs are stored as hashes instead of plain text.

---

## Features

| # | Feature | What it does |
|---|---------|--------------|
| 1 | Create Wallet | Creates a wallet with a name, a 4-digit PIN and an optional opening amount |
| 2 | View Wallet | Shows wallet ID, owner, balance and creation time (PIN required) |
| 3 | Add Money | Adds money to a wallet |
| 4 | Withdraw Money | Withdraws money after PIN and balance checks |
| 5 | Send Money | Transfers money between two wallets as one all-or-nothing transaction |
| 6 | Check Balance | Shows the current balance (PIN required) |
| 7 | Transaction History | Shows the last 20 transactions, newest first (PIN required) |
| 8 | Close Wallet | Deletes a wallet, only when its balance is 0 (PIN required) |

---

## Tech Stack

- **Language:** Java (JDK 23; Java 14 or newer is required)
- **Database:** MySQL
- **Connectivity:** JDBC with `mysql-connector-j` 8.4.0
- **Build tool:** Maven
- **IDE used:** IntelliJ IDEA

---

## Project Structure

```
QuickPayWallet
├── src/main/java/com/quickpay/wallet
│   └── QuickPayApp.java        # entire application (menu, features, DB helpers)
├── quickpay_schema.sql         # database and table creation script
├── pom.xml                     # Maven config (MySQL driver dependency)
└── README.md
```

---

## Database Design

Database name: `quickpay_db`

**wallets**

| Column | Type | Notes |
|--------|------|-------|
| `wallet_id` | INT, primary key, auto increment | Starts at 5001 |
| `owner_name` | VARCHAR(100) | |
| `pin_hash` | VARCHAR(64) | SHA-256 hash of the PIN |
| `balance` | DECIMAL(15,2) | `CHECK (balance >= 0)` |
| `created_at` | TIMESTAMP | Set automatically |

**wallet_transactions**

| Column | Type | Notes |
|--------|------|-------|
| `txn_id` | INT, primary key, auto increment | |
| `wallet_id` | INT, foreign key to `wallets` | `ON DELETE CASCADE` |
| `txn_type` | VARCHAR(20) | `ADD_MONEY`, `WITHDRAW`, `SEND`, `RECEIVE` |
| `amount` | DECIMAL(15,2) | |
| `balance_after` | DECIMAL(15,2) | Balance right after this transaction |
| `other_wallet_id` | INT, nullable | The other wallet for `SEND` / `RECEIVE` |
| `txn_time` | TIMESTAMP | Set automatically |

One wallet has many transactions (one-to-many). A transfer creates two rows: a `SEND` row for the sender and a `RECEIVE` row for the receiver.

---

## How It Works

Every money operation follows the same pattern:

```
setAutoCommit(false)         start a transaction
SELECT ... FOR UPDATE        lock the wallet row(s)
validate (PIN, balance)      stop early if anything is wrong
UPDATE balance(s)            change the money
INSERT transaction row(s)    record the history
commit()                     save everything together
rollback() on any error      undo everything
```

### Send Money

1. Validate the amount and make sure sender and receiver are different.
2. Start a transaction and verify the sender's PIN.
3. Lock both wallet rows, **always the smaller wallet ID first** (prevents deadlock).
4. Check that both wallets exist and the sender has enough balance.
5. Subtract from the sender, add to the receiver, and write the `SEND` and `RECEIVE` rows.
6. Commit. If anything fails, roll back so no money is lost or created.

---

## Key Concepts Used

- **JDBC:** `Connection`, `PreparedStatement`, `ResultSet`, `getGeneratedKeys()`
- **Transactions (ACID):** `setAutoCommit(false)`, `commit()`, `rollback()`
- **Row locking:** `SELECT ... FOR UPDATE` to stop two operations changing the same balance at once
- **Deadlock avoidance:** consistent lock ordering in transfers
- **SQL injection protection:** `PreparedStatement` with `?` placeholders everywhere
- **PIN hashing:** SHA-256 via `MessageDigest`; the real PIN is never stored
- **Input validation:** invalid numbers, negative amounts and bad PIN formats are rejected
- **try-with-resources:** connections and statements close automatically
- **Database constraints:** `CHECK`, `FOREIGN KEY`, `ON DELETE CASCADE`

---

## Getting Started

### Prerequisites

- JDK 14 or newer
- MySQL Server running on `localhost:3306`
- Maven (or IntelliJ IDEA, which includes it)

### Setup

1. **Clone the repository**
   ```bash
   git clone https://github.com/keerthan-acharya/QuickPayWallet.git
   cd QuickPayWallet
   ```

2. **Create the database**

   Run `quickpay_schema.sql` in MySQL Workbench or the MySQL command line:
   ```bash
   mysql -u root -p < quickpay_schema.sql
   ```

3. **Set your MySQL password**

   Open `src/main/java/com/quickpay/wallet/QuickPayApp.java` and change:
   ```java
   private static final String DB_PASS = "YOUR_MYSQL_PASSWORD";
   ```
   Do not commit your real password.

4. **Run the application**

   Run `QuickPayApp` from IntelliJ, or from the command line:
   ```bash
   mvn compile exec:java -Dexec.mainClass="com.quickpay.wallet.QuickPayApp"
   ```
   (The `exec` command needs the Maven exec plugin; running from IntelliJ is the simplest option.)

---

## Sample Run

```
========== QUICKPAY WALLET ==========
1. Create Wallet
2. View Wallet
3. Add Money
4. Withdraw Money
5. Send Money
6. Check Balance
7. Transaction History
8. Close Wallet
9. Exit
Enter choice: 1
Owner name: Keerthan
4-digit PIN: 1234
Opening amount (0 for none): 1000
Wallet created! Your Wallet ID is 5001
```

---

## Testing Checklist

| Test | Expected result |
|------|-----------------|
| Create wallet with 1000 | Wallet ID 5001 shown |
| Add 200 | Balance becomes 1200.0 |
| Withdraw 100 | Balance becomes 1100.0 |
| Send 300 to another wallet | Payment Successful, both balances updated |
| Withdraw more than the balance | `Insufficient balance`, nothing changes |
| Wrong PIN | `Invalid Wallet ID or PIN` |
| Send to a wallet that does not exist | `Receiver wallet not found`, nothing changes |
| Send to your own wallet | `Cannot send money to your own wallet` |
| Close a wallet that still has money | Asked to withdraw the balance first |

After any failed transfer, both balances stay unchanged, which confirms that rollback works.

---

## Limitations and Future Improvements

- Replace unsalted SHA-256 with **bcrypt or Argon2** and add a limit on wrong PIN attempts (a 4-digit PIN has only 10,000 possibilities)
- Use `BigDecimal` instead of `double` for money in Java
- Use a connection pool such as HikariCP
- Read database credentials from environment variables
- Split the code into model, DAO and service layers, and add unit tests
- Build a GUI (JavaFX) or a web front end (Spring Boot)

---

## Author

**Keerthan**
7th semester ECE student
GitHub: [keerthan-acharya](https://github.com/keerthan-acharya)
