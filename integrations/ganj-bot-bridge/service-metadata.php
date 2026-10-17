<?php
declare(strict_types=1);
ini_set('display_errors', '0');
header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store');
require_once __DIR__ . '/service-metadata-core.php';

function ganjMetadataFail(int $status, string $code): never {
    http_response_code($status); echo json_encode(['error' => $code]); exit;
}
if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') ganjMetadataFail(405, 'method_not_allowed');
$tokenPath = '/etc/ganj-bot/app-projection-token';
$expected = is_file($tokenPath) ? trim((string)file_get_contents($tokenPath)) : '';
$header = (string)($_SERVER['HTTP_AUTHORIZATION'] ?? '');
if (strlen($expected) < 32 || !hash_equals('Bearer ' . $expected, $header)) ganjMetadataFail(401, 'unauthorized');
if ((int)($_SERVER['CONTENT_LENGTH'] ?? 0) > 256 * 1024) ganjMetadataFail(413, 'request_too_large');
$raw = (string)file_get_contents('php://input');
if (strlen($raw) > 256 * 1024) ganjMetadataFail(413, 'request_too_large');
$body = json_decode($raw, true);
if (!is_array($body) || array_diff(array_keys($body), ['records', 'include_usage'])
    || !is_bool($body['include_usage'] ?? null) || !is_array($body['records'] ?? null)
    || count($body['records']) < 1 || count($body['records']) > 1000) ganjMetadataFail(400, 'request_invalid');
$records = [];
foreach ($body['records'] as $record) {
    if (!is_array($record) || count($record) !== 2
        || !is_string($record['external_service_id'] ?? null) || !preg_match('/^[A-Za-z0-9._:-]{1,128}$/D', $record['external_service_id'])
        || !is_string($record['external_customer_id'] ?? null) || !preg_match('/^[0-9]{1,20}$/D', $record['external_customer_id'])) ganjMetadataFail(400, 'record_invalid');
    if (isset($records[$record['external_service_id']])) ganjMetadataFail(400, 'duplicate_record');
    $records[$record['external_service_id']] = $record;
}

try {
    $root = dirname(__DIR__, 4);
    require_once $root . '/config.php';
    require_once $root . '/function.php';
    if (!isset($pdo) || !($pdo instanceof PDO)) ganjMetadataFail(503, 'database_unavailable');
    $sql = 'SELECT i.id_invoice,i.id_user,i.username,i.Service_location,'
        . 'c.payload_json,c.updated_at AS cache_updated_at FROM invoice i '
        . 'LEFT JOIN ganj_web_service_cache c ON c.user_id=i.id_user AND c.invoice_id=i.id_invoice '
        . 'WHERE i.id_invoice IN ('
        . implode(',', array_fill(0, count($records), '?')) . ')';
    $stmt = $pdo->prepare($sql); $stmt->execute(array_keys($records));
    $invoices = $stmt->fetchAll(PDO::FETCH_ASSOC);
    $output = [];
    foreach ($invoices as $invoice) {
        $record = $records[(string)$invoice['id_invoice']] ?? null;
        // Invoice owner is checked against the server-side projection again.
        if ($record === null || (string)$invoice['id_user'] !== $record['external_customer_id']) continue;
        $output[] = $body['include_usage']
            ? ganjServiceMetadataFromBotCache($invoice,time())
            : ganjServiceMetadata($invoice);
    }
    echo json_encode(['items' => $output], JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_THROW_ON_ERROR);
} catch (Throwable $error) { ganjMetadataFail(503, 'metadata_unavailable'); }
