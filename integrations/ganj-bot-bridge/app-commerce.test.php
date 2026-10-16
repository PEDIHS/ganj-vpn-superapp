<?php
require __DIR__.'/app-commerce.php';
foreach(['اکونومی · ۳۰ گیگ · ۳۰ روز · ۳ دستگاه'=>3,'2 کاربر نامحدود'=>2,'50 گیگ'=>1,'۱۵۰ گیگ · ۲ دستگاه'=>2] as $name=>$expected)if(ganjAppDeviceLimit($name)!==$expected)throw new Exception('device limit parse failed');
$p=['code_product'=>'ga_premium_60_2','note'=>'۲۰۰ گیگ برای ۶۰ روز'];
$r=ganjAppDecorateProduct(['name'=>'پریمیوم','price'=>972000],$p);
if($r['device_limit']!==2||$r['app_tier']!=='premium'||$r['price']!==972000)throw new Exception('catalog metadata failed');
if(ganjAppProductMetadata(['code_product'=>'ga_forged','note'=>'x'])!==null)throw new Exception('invalid code accepted');
echo "App commerce parsing tests passed\n";
