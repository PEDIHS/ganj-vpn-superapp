<?php
declare(strict_types=1);
require_once __DIR__.'/app-account-snapshot.php';
function check(bool $condition,string $message):void { if(!$condition)throw new RuntimeException($message); }

$now=1760000000;
$owner=['id'=>'1234567','Balance'=>'200000','namecustom'=>'مالک'];
$invoices=[[
    'id_invoice'=>'INV-1','username'=>'one','name_product'=>'Plan',
    'Service_location'=>'Germany','Status'=>'active','Volume'=>100,'Service_time'=>30,
    'time_sell'=>$now-86400,'cache_updated_at'=>$now-10,
    'payload_json'=>json_encode([
        'status'=>'active','used_gb'=>20,'remaining_gb'=>80,
        'configs'=>['vless://SECRET-CREDENTIAL'],'user_info'=>'SECRET',
        'subscription_url'=>'https://example.invalid/SECRET',
    ]),
]];
$a=ganjAppSnapshotFromRows($owner,$invoices,[],$now);
$json=json_encode($a);
check(str_contains($json,'"balance":200000'),'owner wallet from canonical DB');
check(str_contains($json,'"usage_fresh":true'),'current bot cache');
check(!str_contains($json,'vless://')&&!str_contains($json,'SECRET'),'no provider credentials');
$b=ganjAppSnapshotFromRows($owner,$invoices,[],$now);
check($a['revision']===$b['revision'],'unchanged read model revision');
$invoices[0]['cache_updated_at']=$now-120;
$c=ganjAppSnapshotFromRows($owner,$invoices,[],$now);
check($c['services'][0]['usage_fresh']===false,'stale usage marked');
check($c['services'][0]['status']==='active','invoice wins over stale panel cache');
try{ganjAppSnapshotFromRows(['id'=>'1234567','Balance'=>'5.5'],[],[],$now); throw new RuntimeException('not rejected');}
catch(RuntimeException $e){ check($e->getMessage()==='invalid_account_amount','invalid balance rejected'); }
echo "Ganj account snapshot tests passed\n";
