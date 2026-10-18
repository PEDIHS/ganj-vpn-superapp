-- Install only during a reviewed bot deployment. No customer balances are stored.
CREATE TABLE IF NOT EXISTS ganj_app_event_outbox (
 event_id CHAR(36) NOT NULL PRIMARY KEY,
 event_key VARCHAR(191) NOT NULL UNIQUE,
 telegram_subject VARCHAR(24) NOT NULL,
 event_type VARCHAR(30) NOT NULL,
 event_payload JSON NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 delivered_at TIMESTAMP NULL DEFAULT NULL,
 attempts INT UNSIGNED NOT NULL DEFAULT 0,
 retry_after TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 KEY ganj_app_event_outbox_pending (delivered_at, retry_after, event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
