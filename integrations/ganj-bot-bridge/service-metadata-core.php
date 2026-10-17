<?php
declare(strict_types=1);

function ganjMetadataInteger(mixed $value): ?int {
    if (is_string($value) && preg_match('/^(?:0|[1-9][0-9]{0,15})$/D', $value)) $value = (float)$value;
    if ((!is_int($value) && !is_float($value)) || !is_finite((float)$value)
        || $value < 0 || $value > 9007199254740991 || floor((float)$value) !== (float)$value) return null;
    return (int)$value;
}

/** Exact allowlist; never serialize a provider object containing links, credentials or tokens. */
function ganjServiceMetadata(array $invoice, ?array $data = null): array {
    $username = (string)($invoice['username'] ?? '');
    $safeUsername = preg_match('/^[A-Za-z0-9._@:-]{1,128}$/D', $username) ? $username : null;
    $used = $data !== null && ($data['username'] ?? null) === $username ? ganjMetadataInteger($data['used_traffic'] ?? null) : null;
    $limit = $data !== null ? ganjMetadataInteger($data['data_limit'] ?? null) : null;
    $available = $used !== null && ($limit !== null || array_key_exists('data_limit', $data ?? []) && $data['data_limit'] === null);
    return [
        'external_service_id' => (string)$invoice['id_invoice'],
        'external_customer_id' => (string)$invoice['id_user'],
        'service_username' => $safeUsername,
        'traffic_limit_bytes' => $available && $limit !== 0 ? $limit : null,
        'traffic_used_bytes' => $available ? $used : null,
        'traffic_usage_available' => $available,
    ];
}

/**
 * Reuse ONLY the existing Bot-DB service cache; never contact a panel while
 * rendering app account details. A 2-minute freshness gate prevents outdated
 * quotas from being represented as real-time usage.
 */
function ganjServiceMetadataFromBotCache(array $invoice, int $now): array {
    $updated=(int)($invoice['cache_updated_at']??0);
    if ($updated<=0 || $updated>$now || $updated<$now-120) {
        return ganjServiceMetadata($invoice);
    }
    $payload=json_decode((string)($invoice['payload_json']??''),true);
    if (!is_array($payload) || empty($payload['data_fresh'])) {
        return ganjServiceMetadata($invoice);
    }
    $used=$payload['used_gb']??null;
    $total=$payload['total_gb']??null;
    if (!is_numeric($used) || !is_numeric($total)
        || !is_finite((float)$used) || !is_finite((float)$total)
        || (float)$used<0 || (float)$total<0
        || (float)$used>1000000 || (float)$total>1000000) {
        return ganjServiceMetadata($invoice);
    }
    $gb=1073741824;
    $data=[
        'username'=>(string)($invoice['username']??''),
        'used_traffic'=>(int)round((float)$used*$gb),
        'data_limit'=>(float)$total===0.0 ? null : (int)round((float)$total*$gb),
    ];
    // Convert only explicitly allowlisted usage fields, not a byte of config.
    return ganjServiceMetadata($invoice,$data);
}
