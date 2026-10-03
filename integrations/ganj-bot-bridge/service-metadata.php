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
    require_once $root . '/panels.php';
    if (!isset($pdo) || !($pdo instanceof PDO)) ganjMetadataFail(503, 'database_unavailable');
    $sql = 'SELECT id_invoice,id_user,username,Service_location FROM invoice WHERE id_invoice IN ('
        . implode(',', array_fill(0, count($records), '?')) . ')';
    $stmt = $pdo->prepare($sql); $stmt->execute(array_keys($records));
    $invoices = $stmt->fetchAll(PDO::FETCH_ASSOC);
    $output = [];
    $deadline = microtime(true) + 4.0;
    foreach ($invoices as $invoice) {
        $record = $records[(string)$invoice['id_invoice']] ?? null;
        // Recheck current invoice ownership; a delayed reconciliation cannot disclose a transferred service.
        if ($record === null || (string)$invoice['id_user'] !== $record['external_customer_id']) continue;
        $data = null;
        if ($body['include_usage'] && microtime(true) < $deadline) {
            $panel = select('marzban_panel', '*', 'name_panel', $invoice['Service_location'], 'select');
            if (is_array($panel) && in_array((string)($panel['type'] ?? ''), ['marzban', 'rebecca'], true)) {
                // Read only the user JSON. DataUser also downloads the entire subscription and update history.
                $response = getuser((string)$invoice['username'], (string)$invoice['Service_location']);
                if (is_array($response) && empty($response['error']) && (int)($response['status'] ?? 200) < 400) {
                    $decoded = json_decode((string)($response['body'] ?? ''), true);
                    if (is_array($decoded)) $data = $decoded;
                }
            } else {
                require_once $root . '/panels.php';
                $candidate = (new ManagePanel())->DataUser((string)$invoice['Service_location'], (string)$invoice['username']);
                if (is_array($candidate) && ($candidate['status'] ?? 'Unsuccessful') !== 'Unsuccessful') $data = $candidate;
            }
        }
        $output[] = ganjServiceMetadata($invoice, $data);
    }
    echo json_encode(['items' => $output], JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_THROW_ON_ERROR);
} catch (Throwable $error) { ganjMetadataFail(503, 'metadata_unavailable'); }
