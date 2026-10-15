<?php
/** CLI-only additive catalog installation. Existing products and invoices are never rewritten. */
declare(strict_types=1);
if(PHP_SAPI!=='cli')exit(1);
$root=$argv[1]??'/var/www/bot.pedramhs.ir';
require $root.'/config.php';
$panel=$pdo->query("SELECT * FROM marzban_panel WHERE code_panel='91be' AND status='active'")->fetch(PDO::FETCH_ASSOC);
if(!$panel)throw new RuntimeException('Canonical VIP panel unavailable.');
$source=$pdo->prepare("SELECT * FROM product WHERE Location=? AND agent='f' AND category=? AND Volume_constraint=? AND Service_time=30 ORDER BY id DESC LIMIT 1");
$tiers=[['economy','اکونومی',30],['standard','استاندارد',50],['professional','حرفه‌ای',100],['premium','پریمیوم دوره‌ای',100]];
$pdo->beginTransaction();$created=0;
try {
 foreach($tiers as [$tier,$label,$volume]) foreach($tier==='premium'?[30,60,90]:[30] as $days) foreach([1,2,3] as $devices) {
  $code='ga_'.$tier.'_'.$days.'_'.$devices;
  $exists=$pdo->prepare('SELECT code_product FROM product WHERE code_product=?');$exists->execute([$code]);if($exists->fetchColumn())continue;
  $source->execute([$panel['name_panel'],$devices.' کاربر',$volume]);$template=$source->fetch(PDO::FETCH_ASSOC);
  if(!$template)throw new RuntimeException('Missing authoritative price/template for '.$volume.'GB/'.$devices.' devices');
  unset($template['id']);
  $quota=$tier==='premium'?(int)($days/30)*100:$volume;
  $template['code_product']=$code;
  $template['name_product']=$label.' · '.$quota.' گیگ · '.$days.' روز · '.$devices.' دستگاه';
  $template['price_product']=(string)(int)round((float)$template['price_product']*match($days){60=>1.8,90=>2.55,default=>1});
  $template['Volume_constraint']=(string)$quota;$template['Service_time']=(string)$days;
  $template['category']='گنج اپ';$template['web_sort']=(string)(array_search($tier,array_column($tiers,0),true)*100+$days+$devices);
  $template['note']=$tier==='premium'?'حجم منصفانه: ۱۰۰ گیگ به‌ازای هر ۳۰ روز؛ حجم و دستگاه‌ها بین اعضا مشترک است.':'حجم، مدت و ظرفیت دستگاه‌ها بین اعضای دعوت‌شده مشترک است.';
  $columns=array_keys($template);$query='INSERT INTO product (`'.implode('`,`',$columns).'`) VALUES('.implode(',',array_fill(0,count($columns),'?')).')';$pdo->prepare($query)->execute(array_values($template));$created++;
 }
 $pdo->commit();echo json_encode(['created'=>$created,'app_products'=>(int)$pdo->query("SELECT COUNT(*) FROM product WHERE code_product LIKE 'ga\\_%'")->fetchColumn()],JSON_UNESCAPED_UNICODE)."\n";
}catch(Throwable $e){$pdo->rollBack();throw $e;}
