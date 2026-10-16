<?php
declare(strict_types=1);

function ganjAppDeviceLimit(string $name): int {
    $name=strtr($name,array_combine(preg_split('//u','۰۱۲۳۴۵۶۷۸۹٠١٢٣٤٥٦٧٨٩',-1,PREG_SPLIT_NO_EMPTY),str_split('01234567890123456789')));
    if(preg_match('/(?:^|[^0-9])([1-9][0-9]?)\s*(?:دستگاه|کاربر)/u',$name,$m))return max(1,min(100,(int)$m[1]));
    return 1;
}
function ganjAppProductMetadata(array $product): ?array {
    if (!str_starts_with((string)($product['code_product']??''),'ga_')) return null;
    if(!preg_match('/^ga_(economy|standard|professional|premium)_(30|60|90)_([1-3])$/D',(string)$product['code_product'],$m))return null;
    $names=['economy'=>'اکونومی','standard'=>'استاندارد','professional'=>'حرفه‌ای','premium'=>'پریمیوم دوره‌ای'];
    return ['tier'=>$m[1],'tier_name'=>$names[$m[1]],'devices'=>(int)$m[3],'description'=>(string)($product['note']??'')];
}
function ganjAppDecorateProduct(array $formatted,array $product): array {
    $meta=ganjAppProductMetadata($product);
    if(!$meta)return $formatted;
    $formatted['description']=(string)($meta['description']??'');
    $formatted['device_limit']=max(1,min(3,(int)($meta['devices']??1)));
    $formatted['app_tier']=(string)$meta['tier'];
    $formatted['app_tier_name']=(string)$meta['tier_name'];
    return $formatted;
}
/** Only authenticated server-side app SSO marks provisioning origin. No mobile admin secret. */
function ganjAppProvisioningOrigin(): bool { return !empty($_SESSION['ganj_native_app']); }
function ganjAppPanelToken(array $panel): array {
    $credentialPath='/etc/ganj-bot/app-panel-admin.json';
    $credentials=is_file($credentialPath)?json_decode((string)file_get_contents($credentialPath),true):null;
    if(!is_array($credentials)||($credentials['username']??'')!=='ganj_app'||strlen((string)($credentials['password']??''))<20)throw new RuntimeException('ادمین گنج اپ آماده نیست.');
    $base=rtrim((string)$panel['url_panel'],'/');
    if(!str_starts_with($base,'https://'))throw new RuntimeException('مسیر امن پنل آماده نیست.');
    $cachePath='/var/lib/ganj-bot/app-admin-token-'.hash('sha256',$base).'.json';
    $cache=is_file($cachePath)?json_decode((string)file_get_contents($cachePath),true):null;
    if(is_array($cache)&&(int)($cache['created_at']??0)>time()-240&&is_string($cache['access_token']??null))return ['access_token'=>$cache['access_token']];
    $ch=curl_init($base.'/api/admin/token');
    curl_setopt_array($ch,[CURLOPT_POST=>true,CURLOPT_POSTFIELDS=>http_build_query(['username'=>$credentials['username'],'password'=>$credentials['password']]),CURLOPT_RETURNTRANSFER=>true,CURLOPT_FOLLOWLOCATION=>false,CURLOPT_CONNECTTIMEOUT=>3,CURLOPT_TIMEOUT=>8,CURLOPT_HTTPHEADER=>['Accept: application/json','Content-Type: application/x-www-form-urlencoded']]);
    $raw=curl_exec($ch);$status=(int)curl_getinfo($ch,CURLINFO_RESPONSE_CODE);curl_close($ch);
    $result=is_string($raw)?json_decode($raw,true):null;
    if($status!==200||!is_array($result)||!is_string($result['access_token']??null))throw new RuntimeException('ورود ادمین گنج اپ انجام نشد.');
    $tmp=$cachePath.'.'.bin2hex(random_bytes(4));
    if(file_put_contents($tmp,json_encode(['created_at'=>time(),'access_token'=>$result['access_token']]))!==false){chmod($tmp,0600);rename($tmp,$cachePath);}
    return ['access_token'=>$result['access_token']];
}
