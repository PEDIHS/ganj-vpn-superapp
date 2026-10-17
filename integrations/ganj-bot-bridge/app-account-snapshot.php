<?php
declare(strict_types=1);

/**
 * Owner-scoped, one-request account read model derived only from the existing
 * Ganj Bot MySQL database. Do not query provider/panel APIs on UI requests.
 * The full service cache can include VPN credentials: NEVER echo payload_json.
 */
function ganjAppSafeAccountNumber(mixed $value): int {
    if (!is_numeric($value) || !is_finite((float)$value)
        || (float)$value < -1000000000000 || (float)$value > 1000000000000
        || floor((float)$value) !== (float)$value) {
        throw new RuntimeException('invalid_account_amount');
    }
    return (int)$value;
}

function ganjAppSafeOptionalFloat(mixed $value): ?float {
    if ($value === null || !is_numeric($value) || !is_finite((float)$value)) return null;
    $number=(float)$value;
    return $number >= 0 && $number < 1000000000000 ? $number : null;
}

function ganjAppSnapshotFromRows(array $owner, array $invoices, array $payments, int $now): array {
    $id=(string)($owner['id']??'');
    if (!preg_match('/^[0-9]{4,24}$/D', $id)) throw new RuntimeException('invalid_owner');
    $services=[];
    foreach (array_slice($invoices,0,40) as $invoice) {
        $cached=json_decode((string)($invoice['payload_json']??''),true);
        if (!is_array($cached)) $cached=[];
        $sync=(int)($invoice['cache_updated_at']??0);
        $fresh=$sync>0 && $sync <= $now && $sync >= $now-60;
        $expiry=$fresh && isset($cached['expires_at'])
            ? max(0,(int)$cached['expires_at'])
            : max(0,(int)($invoice['time_sell']??0)+(int)($invoice['Service_time']??0)*86400);
        $services[]=[
            'id'=>(string)($invoice['id_invoice']??''),
            'username'=>mb_substr((string)($invoice['username']??''),0,128),
            'name'=>mb_substr((string)($invoice['name_product']??''),0,200),
            'location'=>mb_substr((string)($invoice['Service_location']??''),0,128),
            // Invoice database wins if panel sync is stale. No invented active/expired.
            'status'=>$fresh?(string)($cached['status']??$invoice['Status']):(string)($invoice['Status']??'unknown'),
            'volume_gb'=>ganjAppSafeOptionalFloat($invoice['Volume']??null),
            'used_gb'=>ganjAppSafeOptionalFloat($cached['used_gb']??null),
            'remaining_gb'=>ganjAppSafeOptionalFloat($cached['remaining_gb']??null),
            'expires_at'=>$expiry,
            'panel_synced_at'=>$sync,
            'usage_fresh'=>$fresh,
        ];
    }
    $recent=[];
    foreach (array_slice($payments,0,15) as $payment) {
        $recent[]=[
            'id'=>(string)($payment['id_order']??''),
            'amount'=>ganjAppSafeOptionalFloat($payment['price']??null),
            'status'=>(string)($payment['payment_Status']??'unknown'),
            'created_at'=>(string)($payment['time']??''),
        ];
    }
    $data=[
        'schema'=>1,
        'account'=>[
            'id'=>$id,
            'name'=>mb_substr((string)($owner['namecustom']??''),0,128),
            'balance'=>ganjAppSafeAccountNumber($owner['Balance']??0),
        ],
        'services'=>$services,
        'recent_payments'=>$recent,
    ];
    // Stable content revision; generated_at is deliberately excluded.
    $canonical=json_encode($data,JSON_UNESCAPED_UNICODE|JSON_UNESCAPED_SLASHES|JSON_THROW_ON_ERROR);
    $data['revision']=hash('sha256',$canonical);
    $data['generated_at']=$now;
    return $data;
}

function ganjAppAccountSnapshot(PDO $pdo,array $owner):array {
    $id=(string)($owner['id']??'');
    if (!preg_match('/^[0-9]{4,24}$/D',$id)) throw new RuntimeException('invalid_owner');
    $stmt=$pdo->prepare(
        "SELECT i.id_invoice, i.username, i.name_product, i.Service_location,
                i.Status, i.Volume, i.Service_time, i.time_sell,
                c.payload_json, c.updated_at AS cache_updated_at
           FROM invoice i
           LEFT JOIN ganj_web_service_cache c
             ON c.user_id=i.id_user AND c.invoice_id=i.id_invoice
          WHERE i.id_user=? AND i.name_product<>'سرویس تست' AND COALESCE(i.hidden_from_user,0)=0
          ORDER BY CAST(i.time_sell AS UNSIGNED) DESC LIMIT 40"
    );
    $stmt->execute([$id]);
    $invoices=$stmt->fetchAll(PDO::FETCH_ASSOC);
    $stmt=$pdo->prepare(
        "SELECT id_order, price, payment_Status, time
           FROM Payment_report WHERE id_user=?
          ORDER BY id DESC LIMIT 15"
    );
    $stmt->execute([$id]);
    $payments=$stmt->fetchAll(PDO::FETCH_ASSOC);
    return ganjAppSnapshotFromRows($owner,$invoices,$payments,time());
}
