<?php
declare(strict_types=1);
/** Retail-only compact picker; final order/payment stays in the existing bot handlers. */
function ganjAppCatalogBotHandle(PDO $pdo,string $text,string $callback,array $user,array $setting,string $sellText,$chatId,$messageId): bool {
    if((string)($user['agent']??'')!=='f')return false;
    $entry=in_array($callback,['buy','buyback','buybacktow'],true)||in_array($text,[$sellText,'/buy','buy'],true);
    if(!$entry&&!str_starts_with($callback,'gac_'))return false;
    if(function_exists('check_active_btn')&&!check_active_btn($setting['keyboardmain'],'text_sell'))return false;
    if(($setting['get_number']??'')==='onAuthenticationphone'&&($user['number']??'none')==='none')return false;
    $panel=$pdo->query("SELECT code_panel,name_panel,MethodUsername FROM marzban_panel WHERE code_panel='91be' AND status='active'")->fetch(PDO::FETCH_ASSOC);
    if(!$panel)return false;
    $query=$pdo->prepare("SELECT * FROM product WHERE Location=? AND agent='f' AND code_product LIKE 'ga\\_%' ORDER BY web_sort,id");$query->execute([$panel['name_panel']]);$rows=$query->fetchAll(PDO::FETCH_ASSOC);
    if(!$rows)return false;
    $names=['economy'=>'اکونومی','standard'=>'استاندارد','professional'=>'حرفه‌ای','premium'=>'پریمیوم دوره‌ای'];
    $buttons=[];$body='اشتراک VIP گنج';
    if($entry) {$buttons[]=[['text'=>'اشتراک VIP','callback_data'=>'gac_root']];$body.="\nپلن متناسب با نیازتان را انتخاب کنید.";}
    elseif($callback==='gac_root') {
        foreach($names as $key=>$label)$buttons[]=[['text'=>$label,'callback_data'=>'gac_tier_'.$key]];
        $body.="\nاکونومی ۳۰ گیگ · استاندارد ۵۰ گیگ · حرفه‌ای ۱۰۰ گیگ\nپریمیوم دوره‌ای: ۱۰۰ گیگ به‌ازای هر ۳۰ روز";
    } elseif(preg_match('/^gac_tier_(economy|standard|professional|premium)$/D',$callback,$m)) {
        $tier=$m[1];
        if($tier==='premium') {foreach([30,60,90] as $days)$buttons[]=[['text'=>$days.' روز · '.($days/30*100).' گیگ','callback_data'=>'gac_days_'.$tier.'_'.$days]];$body.="\nمدت پریمیوم دوره‌ای را انتخاب کنید. حجم بین تمام اعضا مشترک است.";}
        else $callback='gac_days_'.$tier.'_30';
    }
    if(preg_match('/^gac_days_(economy|standard|professional|premium)_(30|60|90)$/D',$callback,$m)) {
        [$all,$tier,$days]=$m;$body=$names[$tier]."\nتعداد دستگاه را انتخاب کنید:";
        foreach($rows as $p){$meta=ganjAppProductMetadata($p);if(!$meta||$meta['tier']!==$tier||(int)$p['Service_time']!==(int)$days)continue;$price=max(0,(int)round((float)$p['price_product']*(1-(int)($user['pricediscount']??0)/100)));$buttons[]=[['text'=>$meta['devices'].' دستگاه · '.number_format($price).' تومان','callback_data'=>'gac_review_'.$p['code_product']]];}
    }
    if(str_starts_with($callback,'gac_review_')) {
        $code=substr($callback,11);$p=null;foreach($rows as $row)if($row['code_product']===$code){$p=$row;break;}
        if(!$p)return true;
        $meta=ganjAppProductMetadata($p);$price=max(0,(int)round((float)$p['price_product']*(1-(int)($user['pricediscount']??0)/100)));
        savedata('clear','name_panel',(string)$panel['name_panel']);
        $body=$meta['tier_name']."\nحجم: ".$p['Volume_constraint']." گیگ\nمدت: ".$p['Service_time']." روز\nظرفیت: ".$meta['devices']." دستگاه\nمبلغ: ".number_format($price)." تومان\n\n".(string)$p['note'];
        $custom=in_array((string)$panel['MethodUsername'],['نام کاربری دلخواه','نام کاربری دلخواه + عدد رندوم'],true);
        $buttons[]=[['text'=>'تأیید و ادامه پرداخت','callback_data'=>($custom?'prodcutservices_':'prodcutservice_').$p['code_product']]];
    }
    $buttons[]=[['text'=>'بازگشت','callback_data'=>$callback==='gac_root'?'backuser':'gac_root']];
    $keyboard=json_encode(['inline_keyboard'=>$buttons],JSON_UNESCAPED_UNICODE|JSON_UNESCAPED_SLASHES);
    if($callback!==''&&$messageId)Editmessagetext($chatId,$messageId,$body,$keyboard,'HTML');else sendmessage($chatId,$body,$keyboard,'HTML');
    return true;
}
