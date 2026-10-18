<?php
declare(strict_types=1);

/**
 * Optional bridge for the EXISTING GANJ bot PHP writer and cron dispatcher.
 * This file does nothing by itself and is NOT deployed by Android CI.
 * Require it from the existing bot mutation code only after installing the
 * MariaDB migration and provisioning an authenticated local Control API route.
 */
function ganjAppEventQueue(PDO $pdo, string $eventKey, string $telegramId, string $eventType, array $data): void {
    if (!$pdo->inTransaction()) {
        throw new RuntimeException('Outbox insert must share the original MySQL business transaction.');
    }
    if (!preg_match('/^[a-zA-Z0-9:_-]{1,191}$/D', $eventKey) ||
        !preg_match('/^[0-9]{4,20}$/D', $telegramId) ||
        !in_array($eventType, ['subscription_usage', 'subscription_expiry', 'payment_transition'], true)) {
        throw new InvalidArgumentException('Invalid bot event identity');
    }
    $json = json_encode($data, JSON_UNESCAPED_UNICODE | JSON_THROW_ON_ERROR);
    if (strlen($json) > 5000) throw new InvalidArgumentException('Event payload exceeds limit');
    $bytes = random_bytes(16);
    $bytes[6] = chr((ord($bytes[6]) & 0x0f) | 0x40);
    $bytes[8] = chr((ord($bytes[8]) & 0x3f) | 0x80);
    $hex = bin2hex($bytes);
    $id = substr($hex,0,8).'-'.substr($hex,8,4).'-'.substr($hex,12,4).'-'.substr($hex,16,4).'-'.substr($hex,20);
    $statement = $pdo->prepare('INSERT IGNORE INTO ganj_app_event_outbox ' .
        '(event_id,event_key,telegram_subject,event_type,event_payload) VALUES (?,?,?,?,?)');
    $statement->execute([$id,$eventKey,$telegramId,$eventType,$json]);
}

/**
 * Called once within the ALREADY EXISTING cron dispatcher, not by a new scanner.
 * Sends up to N pending rows; retry and idempotency protect against process
 * failures after Control API accepts the request but before MySQL acknowledges.
 *
 * Requires a 0600 secret readable only by the trusted PHP dispatcher account.
 * Never embed credentials in source control, logs, Telegram or Android apps.
 */
function ganjAppEventFlush(PDO $pdo, string $secretFile, int $limit = 20): int {
    if (!is_file($secretFile) || ($mode = fileperms($secretFile)) === false ||
        (($mode & 0077) !== 0)) throw new RuntimeException('Bot event key permission failure');
    $secret = trim((string) file_get_contents($secretFile));
    if (strlen($secret) < 32) throw new RuntimeException('Bot event key invalid');
    $limit = max(1, min(50, $limit));
    $lock = $pdo->query("SELECT GET_LOCK('ganj_app_outbox_dispatch', 0)")->fetchColumn();
    if ((int) $lock !== 1) return 0;
    $delivered = 0;
    try {
        $query = $pdo->query('SELECT event_id,telegram_subject,event_type,event_payload FROM ganj_app_event_outbox ' .
            'WHERE delivered_at IS NULL AND retry_after <= NOW() ' .
            'ORDER BY created_at, event_id LIMIT '.$limit);
        foreach ($query->fetchAll(PDO::FETCH_ASSOC) as $row) {
            $timestamp = (string) time();
            $body = json_encode([
                'event_id' => $row['event_id'],
                'telegram_subject' => $row['telegram_subject'],
                'type' => $row['event_type'],
                'data' => json_decode($row['event_payload'], true, 32, JSON_THROW_ON_ERROR),
            ], JSON_UNESCAPED_UNICODE | JSON_THROW_ON_ERROR);
            $signature = hash_hmac('sha256', $timestamp.'.'.$body, $secret);
            $curl = curl_init('http://127.0.0.1:8080/v1/internal/ganj-bot/customer-event');
            if ($curl === false) throw new RuntimeException('Cannot initialize event publisher');
            curl_setopt_array($curl, [
                CURLOPT_POST => true, CURLOPT_POSTFIELDS => $body,
                CURLOPT_RETURNTRANSFER => true, CURLOPT_FOLLOWLOCATION => false,
                CURLOPT_CONNECTTIMEOUT => 1, CURLOPT_TIMEOUT => 3,
                CURLOPT_HTTPHEADER => [
                    'Content-Type: application/json',
                    'X-Ganj-Event-Time: '.$timestamp,
                    'X-Ganj-Event-Signature: '.$signature,
                ],
            ]);
            curl_exec($curl);
            $status = (int)curl_getinfo($curl, CURLINFO_HTTP_CODE);
            curl_close($curl);
            if ($status === 202) {
                $pdo->prepare('UPDATE ganj_app_event_outbox SET delivered_at=NOW() WHERE event_id=?')
                    ->execute([$row['event_id']]);
                $delivered++;
            } else {
                $pdo->prepare('UPDATE ganj_app_event_outbox SET attempts=attempts+1, ' .
                    'retry_after=DATE_ADD(NOW(), INTERVAL LEAST(3600, POW(2,LEAST(attempts+1,10))) SECOND) ' .
                    'WHERE event_id=?')->execute([$row['event_id']]);
            }
        }
    } finally {
        $pdo->query("SELECT RELEASE_LOCK('ganj_app_outbox_dispatch')");
    }
    return $delivered;
}
