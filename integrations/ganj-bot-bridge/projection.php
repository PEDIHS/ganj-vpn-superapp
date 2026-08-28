<?php
declare(strict_types=1);

header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store');
header('X-Content-Type-Options: nosniff');
header('Referrer-Policy: no-referrer');
header("Content-Security-Policy: default-src 'none'; frame-ancestors 'none'");

function bridgeFail(int $status, string $code): never
{
    http_response_code($status);
    echo json_encode(['error' => ['code' => $code]], JSON_UNESCAPED_SLASHES);
    exit;
}

function bridgeTruncate(string $value, int $limit): string
{
    if ($limit < 1 || preg_match('//u', $value) !== 1) return '';
    if (function_exists('mb_substr')) return mb_substr($value, 0, $limit, 'UTF-8');
    if (preg_match('/^.{0,' . $limit . '}/us', $value, $match) !== 1) return '';
    return $match[0];
}

function bridgeSecret(string $path): string
{
    $real = realpath($path);
    if ($real === false || !is_file($real) || is_link($real)) bridgeFail(503, 'bridge_secret_unavailable');
    $mode = @fileperms($real);
    if (is_int($mode) && (($mode & 0077) !== 0)) bridgeFail(503, 'bridge_secret_permissions');
    $value = trim((string)file_get_contents($real));
    if (strlen($value) < 32 || strlen($value) > 4096) bridgeFail(503, 'bridge_secret_invalid');
    return $value;
}

function bridgeStatus(string $value): string
{
    return match (strtolower(trim($value))) {
        'active', 'sendedwarn', 'send_on_hold' => 'active',
        'unpaid', 'pending', 'waiting' => 'pending',
        'end_of_time', 'end_of_volume', 'expired', 'removetime' => 'expired',
        'deleted', 'cancel', 'cancelled', 'rejected' => 'revoked',
        default => 'disabled',
    };
}

function bridgeExpiresAt(array $row): ?string
{
    $sold = filter_var($row['time_sell'] ?? null, FILTER_VALIDATE_INT);
    $days = filter_var($row['Service_time'] ?? null, FILTER_VALIDATE_INT);
    if ($sold === false || $days === false || $sold <= 0 || $days <= 0) return null;
    return gmdate('c', $sold + ($days * 86400));
}

function bridgeTrafficLimit(array $row): ?int
{
    if (!is_numeric($row['Volume'] ?? null)) return null;
    $gigabytes = (float)$row['Volume'];
    if (!is_finite($gigabytes) || $gigabytes <= 0) return null;
    return (int)min(9007199254740991, round($gigabytes * 1073741824));
}

// HTTP_X_FORWARDED_PROTO is deliberately not trusted because it is client controlled unless
// the web server strips it. Reverse proxies must set the PHP HTTPS server variable explicitly.
$secure = !empty($_SERVER['HTTPS']) && strtolower((string)$_SERVER['HTTPS']) !== 'off';
if (!$secure) bridgeFail(400, 'https_required');
if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'GET') bridgeFail(405, 'method_not_allowed');

$configPath = __DIR__ . '/config.php';
if (!is_file($configPath)) bridgeFail(503, 'bridge_not_configured');
$config = require $configPath;
if (!is_array($config)) bridgeFail(503, 'bridge_not_configured');

$expected = bridgeSecret((string)($config['projection_token_file'] ?? ''));
$authorization = (string)($_SERVER['HTTP_AUTHORIZATION'] ?? '');
$supplied = str_starts_with($authorization, 'Bearer ') ? substr($authorization, 7) : '';
if ($supplied === '' || strlen($supplied) !== strlen($expected) || !hash_equals($expected, $supplied)) {
    bridgeFail(401, 'unauthorized');
}
$expected = str_repeat("\0", strlen($expected));
$supplied = str_repeat("\0", strlen($supplied));

$root = dirname(__DIR__);
$configFile = $root . '/config.php';
if (!is_file($configFile)) bridgeFail(503, 'bot_database_unavailable');
require_once $configFile;
if (!isset($pdo) || !$pdo instanceof PDO) bridgeFail(503, 'bot_database_unavailable');

$limit = filter_input(INPUT_GET, 'limit', FILTER_VALIDATE_INT, ['options' => ['min_range' => 1, 'max_range' => 500]]);
if ($limit === false || $limit === null) $limit = 100;
$cursorRaw = (string)($_GET['cursor'] ?? '0');
if (!preg_match('/^(?:0|[1-9][0-9]{0,19})$/D', $cursorRaw)) bridgeFail(400, 'invalid_cursor');
$cursor = (int)$cursorRaw;

$connectorMap = [];
$mapPath = (string)($config['pasarguard_connector_map_file'] ?? '');
if ($mapPath !== '') {
    $realMap = realpath($mapPath);
    if ($realMap === false || !is_file($realMap) || is_link($realMap)) bridgeFail(503, 'connector_map_unavailable');
    $decoded = json_decode((string)file_get_contents($realMap), true);
    if (!is_array($decoded)) bridgeFail(503, 'connector_map_invalid');
    foreach ($decoded as $panel => $connector) {
        if (!is_string($panel) || !is_string($connector) || !preg_match('/^[A-Za-z0-9._:-]{1,128}$/D', $connector)) {
            bridgeFail(503, 'connector_map_invalid');
        }
        $connectorMap[$panel] = $connector;
    }
}

try {
    $statement = $pdo->prepare(
        'SELECT j.*, m.plan_code, m.allowed_protocols, m.device_limit
           FROM ganj_app_ownership_journal j
           LEFT JOIN ganj_app_plan_mapping m ON m.source_product_name = j.name_product
          WHERE j.seq > :cursor ORDER BY j.seq ASC LIMIT :page_limit'
    );
    $statement->bindValue(':cursor', $cursor, PDO::PARAM_INT);
    $statement->bindValue(':page_limit', $limit + 1, PDO::PARAM_INT);
    $statement->execute();
    $rows = $statement->fetchAll(PDO::FETCH_ASSOC);
} catch (Throwable $error) {
    bridgeFail(503, 'projection_database_unavailable');
}

$hasMore = count($rows) > $limit;
if ($hasMore) array_pop($rows);
$items = [];
$nextCursor = $cursor;
foreach ($rows as $row) {
    $planCode = trim((string)($row['plan_code'] ?? ''));
    $protocols = json_decode((string)($row['allowed_protocols'] ?? ''), true);
    if ($planCode === '' || !is_array($protocols) || $protocols === []) {
        bridgeFail(503, 'projection_plan_mapping_missing');
    }
    $protocols = array_values(array_unique(array_filter(array_map(
        static fn($value) => is_string($value) ? strtolower(trim($value)) : '',
        $protocols
    ), static fn($value) => in_array($value, ['vless', 'vmess', 'trojan', 'shadowsocks'], true))));
    if ($protocols === []) bridgeFail(503, 'projection_protocol_mapping_invalid');

    $nextCursor = (int)$row['seq'];
    $item = [
        'event_id' => 'invoice:' . (string)$row['id_invoice'] . ':journal:' . (string)$row['seq'],
        'external_customer_id' => (string)$row['id_user'],
        'telegram_subject' => (string)$row['id_user'],
        'external_service_id' => (string)$row['id_invoice'],
        'plan_code' => $planCode,
        'display_name' => bridgeTruncate(trim((string)$row['name_product']), 160) ?: 'Ganj VPN Service',
        'status' => bridgeStatus((string)$row['status']),
        'expires_at' => bridgeExpiresAt($row),
        'traffic_limit_bytes' => bridgeTrafficLimit($row),
        'traffic_used_bytes' => 0,
        'device_limit' => max(1, min(1000, (int)($row['device_limit'] ?? 1))),
        'allowed_protocols' => $protocols,
        'source_updated_at' => gmdate('c', strtotime((string)$row['created_at']) ?: time()),
    ];

    $panel = (string)$row['service_location'];
    $username = (string)$row['service_username'];
    if (isset($connectorMap[$panel]) && preg_match('/^[A-Za-z0-9._@:-]{1,128}$/D', $username)) {
        $item['upstream_binding'] = [
            'provider_type' => 'pasarguard',
            'external_service_username' => $username,
            'connector_ref' => $connectorMap[$panel],
        ];
    }
    $items[] = $item;
}

echo json_encode([
    'items' => $items,
    'next_cursor' => (string)$nextCursor,
    'has_more' => $hasMore,
], JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_THROW_ON_ERROR);
