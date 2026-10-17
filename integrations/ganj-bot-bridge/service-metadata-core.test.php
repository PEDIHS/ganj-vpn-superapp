<?php
declare(strict_types=1);
require_once __DIR__ . '/service-metadata-core.php';
function expectMetadata(bool $value, string $message): void { if (!$value) throw new RuntimeException($message); }
$invoice = ['id_invoice' => '42', 'id_user' => '99', 'username' => 'fixture_user_99'];
$private = ['username' => 'fixture_user_99', 'data_limit' => 1000, 'used_traffic' => 250,
    'links' => ['vless://private'], 'subscription_url' => 'https://private.invalid', 'token' => 'private'];
$result = ganjServiceMetadata($invoice, $private);
expectMetadata($result['traffic_used_bytes'] === 250 && $result['traffic_limit_bytes'] === 1000 && $result['traffic_usage_available'], 'Actual traffic missing');
expectMetadata(array_keys($result) === ['external_service_id', 'external_customer_id', 'service_username', 'traffic_limit_bytes', 'traffic_used_bytes', 'traffic_usage_available'], 'Allowlist changed');
expectMetadata(!str_contains(json_encode($result), 'private'), 'Private provider material escaped');
foreach ([0, null, '0'] as $limit) {
    $data = $private; $data['data_limit'] = $limit;
    $r = ganjServiceMetadata($invoice, $data);
    expectMetadata($r['traffic_limit_bytes'] === null && $r['traffic_usage_available'], 'Unlimited quota normalization failed');
}
foreach ([null, ['username' => 'another-owner', 'data_limit' => 1000, 'used_traffic' => 1],
    ['username' => 'fixture_user_99', 'data_limit' => 1000, 'used_traffic' => -1],
    ['username' => 'fixture_user_99', 'data_limit' => 1000, 'used_traffic' => 'invalid']] as $data) {
    $r = ganjServiceMetadata($invoice, $data);
    expectMetadata(!$r['traffic_usage_available'] && $r['traffic_used_bytes'] === null, 'Unknown usage was fabricated');
}
foreach ([-1, 1.5, '1.5', '999999999999999999', INF, NAN, true] as $invalid) expectMetadata(ganjMetadataInteger($invalid) === null, 'Unsafe integer accepted');
expectMetadata(ganjMetadataInteger('107374182400') === 107374182400, 'Byte precision lost');
$invoice['cache_updated_at']=1000;
$invoice['payload_json']=json_encode([
    'data_fresh'=>true,'used_gb'=>1.5,'total_gb'=>10,
    'configs'=>['vless://secret'], 'subscription_url'=>'https://secret.invalid',
]);
$r=ganjServiceMetadataFromBotCache($invoice,1010);
expectMetadata($r['traffic_used_bytes']===1610612736
    && $r['traffic_limit_bytes']===10737418240
    && $r['traffic_usage_available']===true,'fresh bot DB usage cache not read');
expectMetadata(!str_contains(json_encode($r),'secret'),'leaked raw bot cache');
expectMetadata(ganjServiceMetadataFromBotCache($invoice,1500)['traffic_usage_available']===false,
    'stale cache must not be shown as real-time');
echo "Service metadata PHP privacy/precision/cache-only checks passed\n";
