CREATE DATABASE IF NOT EXISTS quickpay_db;
USE quickpay_db;

CREATE TABLE IF NOT EXISTS wallets (
                                       wallet_id   INT AUTO_INCREMENT PRIMARY KEY,
                                       owner_name  VARCHAR(100)  NOT NULL,
    pin_hash    VARCHAR(64)   NOT NULL,
    balance     DECIMAL(15,2) NOT NULL DEFAULT 0,
    created_at  TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_wallet_balance CHECK (balance >= 0)
    ) AUTO_INCREMENT = 5001;

CREATE TABLE IF NOT EXISTS wallet_transactions (
                                                   txn_id          INT AUTO_INCREMENT PRIMARY KEY,
                                                   wallet_id       INT           NOT NULL,
                                                   txn_type        VARCHAR(20)   NOT NULL,   -- ADD_MONEY, WITHDRAW, SEND, RECEIVE
    amount          DECIMAL(15,2) NOT NULL,
    balance_after   DECIMAL(15,2) NOT NULL,
    other_wallet_id INT NULL,
    txn_time        TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (wallet_id) REFERENCES wallets(wallet_id) ON DELETE CASCADE
    );