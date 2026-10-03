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
