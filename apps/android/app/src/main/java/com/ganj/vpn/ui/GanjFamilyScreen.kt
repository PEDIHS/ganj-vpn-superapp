package com.ganj.vpn.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ganj.vpn.core.controlapi.*
import com.ganj.vpn.presentation.ServiceUiModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun familyError(error: ApiError): String = when(error) {
    is ApiError.Network -> "ارتباط برقرار نشد؛ دوباره تلاش کنید."
    is ApiError.AuthenticationExpired, is ApiError.AuthenticationRequired -> "ابتدا با حساب تلگرام خود وارد شوید."
    is ApiError.Conflict -> when(error.code) {
        "device_limit_reached" -> "ظرفیت دستگاه‌های این اشتراک تکمیل است."
        "invite_self" -> "این اشتراک متعلق به خود شماست."
        "invite_used" -> "این دعوت قبلاً استفاده شده؛ لینک جدید بگیرید."
        else -> "این دعوت در دسترس نیست."
    }
    is ApiError.Forbidden -> "دسترسی لازم یا اشتراک فعال در دسترس نیست."
    is ApiError.RateLimited -> "ابتدا یکی از لینک‌های دعوت قبلی را لغو کنید."
    else -> "اطلاعات دریافت نشد؛ ممکن است دعوت منقضی یا لغو شده باشد."
}

@Composable
internal fun GanjFamilyScreen(
    api: FamilyApi?, services: List<ServiceUiModel>, selectedServiceId: String?,
    onBack: ()->Unit, onRefreshServices: ()->Unit, onRevokeDevice: (String)->Unit,
) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var serviceId by rememberSaveable { mutableStateOf(selectedServiceId ?: services.firstOrNull()?.entitlementId) }
    var reply by remember { mutableStateOf<FamilyReply?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmation by remember { mutableStateOf<Pair<String,String>?>(null) }
    fun load() {
        val id=serviceId ?: return
        val active=api ?: run {error="سرویس مدیریت اشتراک در دسترس نیست.";return}
        busy=true;error=null
        scope.launch {
            try { when(val result=withContext(Dispatchers.IO){active.summary(id)}) {
                is ApiResult.Success -> reply=result.value
                is ApiResult.Failure -> error=familyError(result.error)
            }} finally { busy=false }
        }
    }
    fun mutate(action: (FamilyApi,String)->ApiResult<FamilyReply>) {
        val active=api ?: return
        val id=serviceId ?: return
        if(busy)return
        busy=true;error=null
        scope.launch {
            try {
                when(val result=withContext(Dispatchers.IO){action(active,id)}) {
                    is ApiResult.Success -> {
                        result.value.inviteUrl?.let { url ->
                            if(url.matches(Regex("https://bot\\.pedramhs\\.ir/app/join#[A-Za-z0-9_-]{43}")))
                                runCatching {context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {type="text/plain";putExtra(Intent.EXTRA_TEXT,"دعوت به اشتراک گنج\n$url")},"ارسال دعوت"))}
                        }
                        onRefreshServices()
                        when(val refreshed=withContext(Dispatchers.IO){active.summary(id)}) {
                            is ApiResult.Success -> reply=refreshed.value
                            is ApiResult.Failure -> {reply=null;onBack()}
                        }
                    }
                    is ApiResult.Failure -> error=familyError(result.error)
                }
            } finally { busy=false }
        }
    }
    LaunchedEffect(serviceId) {reply=null;load()}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=responsiveHorizontalPadding(),vertical=18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        AccountBackAction(onBack)
        Text("اعضا و دستگاه‌های اشتراک",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
        Text("هر عضو با حساب تلگرام خودش وارد می‌شود؛ حجم و ظرفیت دستگاه‌ها مشترک است.",color=MaterialTheme.colorScheme.onSurfaceVariant)
        services.forEach { service -> GanjChoicePill(service.username ?: service.displayName,service.entitlementId==serviceId,{if(!busy)serviceId=service.entitlementId},Modifier.fillMaxWidth(),enabled=!busy) }
        if(services.isEmpty())Text("ابتدا یک اشتراک خریداری یا دعوت دریافت کنید.")
        if(busy)Text("در حال بررسی…")
        error?.let {Text(it,color=MaterialTheme.colorScheme.error);GanjGlassButton("تلاش دوباره",::load,enabled=!busy)}
        reply?.let { data ->
            Text(data.serviceName.orEmpty(),style=MaterialTheme.typography.titleLarge)
            Text("ظرفیت اشتراک: ${data.deviceLimit.toPersianDigits()} دستگاه · ${data.slotsUsed.toPersianDigits()} جایگاه استفاده‌شده")
            if(data.owner) {
                GanjGlassButton("ساخت و ارسال لینک دعوت",{mutate{a,id->a.createInvite(id)}},Modifier.fillMaxWidth(),enabled=!busy&&data.slotsUsed<data.deviceLimit)
                Text("لینک ۲۴ ساعت اعتبار دارد و برای یک عضو است.",style=MaterialTheme.typography.bodySmall)
            }
            Text("اعضا",style=MaterialTheme.typography.titleMedium)
            if(data.members.isEmpty())Text("هنوز عضوی اضافه نشده است.")
            data.members.forEach { member ->
                Text(member.name+(if(member.current)" · شما" else ""))
                if(data.owner || member.current)GanjGlassButton(if(member.current)"خروج از اشتراک" else "حذف عضو",{confirmation=member.id to member.name},enabled=!busy,filled=false)
            }
            Text("دستگاه‌ها",style=MaterialTheme.typography.titleMedium)
            data.devices.forEach { device ->
                Text(device.name+(if(device.current)" · دستگاه فعلی" else ""))
                if(device.own&&!device.current)GanjGlassButton("مدیریت و حذف دستگاه",{onRevokeDevice(device.id)},filled=false,enabled=!busy)
            }
            if(data.devices.isEmpty())Text("دستگاهی هنوز به این اشتراک متصل نشده است.")
            if(data.owner) data.invites.forEach { invite -> GanjGlassButton("لغو دعوت · ${invite.expiresAt.take(10)}",{mutate{a,id->a.revokeInvite(id,invite.id)}},filled=false,enabled=!busy) }
            GanjGlassButton("به‌روزرسانی",::load,enabled=!busy,filled=false)
        }
    }
    confirmation?.let { member -> GanjLiquidConfirmDialog("قطع دسترسی به اشتراک","دسترسی «${member.second}» به این اشتراک حذف می‌شود؛ حساب و کیف پول او باقی می‌ماند.","تأیید","انصراف",{confirmation=null;mutate{a,id->a.remove(id,member.first)}},{confirmation=null},destructive=true) }
}

@Composable
internal fun GanjFamilyInvitation(
    api: FamilyApi?, token: String, linked: Boolean, onLogin: ()->Unit, onDismiss: ()->Unit, onJoined: ()->Unit,
) {
    val scope=rememberCoroutineScope()
    var data by remember(token) {mutableStateOf<FamilyReply?>(null)}
    var busy by remember(token) {mutableStateOf(false)}
    var error by remember(token) {mutableStateOf<String?>(null)}
    var previewRevision by remember(token) { mutableIntStateOf(0) }
    LaunchedEffect(token, linked, api, previewRevision) {
        data = null
        if (!linked) return@LaunchedEffect
        val active = api ?: run {
            error = "سرویس مدیریت دعوت در دسترس نیست."
            return@LaunchedEffect
        }
        busy = true
        error = null
        try {
            when (val result = withContext(Dispatchers.IO) { active.preview(token) }) {
                is ApiResult.Success -> data = result.value
                is ApiResult.Failure -> error = familyError(result.error)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            error = "پیش‌نمایش دعوت بارگذاری نشد؛ دوباره تلاش کنید."
        } finally {
            busy = false
        }
    }
    Dialog(onDismissRequest={if(!busy)onDismiss()}) {
        GanjGlassSurface(role=GanjGlassRole.Dense,accent=MaterialTheme.colorScheme.primary,modifier=Modifier.fillMaxWidth().heightIn(max=600.dp).verticalScroll(rememberScrollState()),shapeRadius=18.dp,padding=PaddingValues(20.dp)) {
            Text("دعوت به اشتراک گنج",style=MaterialTheme.typography.titleLarge)
            if(!linked) {
                Text("برای دیدن و پذیرش دعوت، با حساب تلگرام خود وارد شوید.")
                GanjGlassButton("ورود با تلگرام",onLogin,Modifier.fillMaxWidth())
            } else {
                data?.let { invite ->
                    Text("${invite.inviterName} شما را به اشتراک «${invite.serviceName}» دعوت کرده است.")
                    Text("ظرفیت مشترک ${invite.deviceLimit.toPersianDigits()} دستگاه است؛ حجم و مدت تمدید نمی‌شود.")
                    GanjGlassButton("قبول دعوت",{
                        if(!busy) {busy=true;error=null;scope.launch {try {when(val result=withContext(Dispatchers.IO){api!!.accept(token)}) {
                            is ApiResult.Success -> onJoined()
                            is ApiResult.Failure -> error=familyError(result.error)
                        }} finally {busy=false}}}
                    },Modifier.fillMaxWidth(),enabled=!busy&&!invite.ownInvite)
                }
                if(busy)Text("در حال بررسی…")
                error?.let {Text(it,color=MaterialTheme.colorScheme.error);GanjGlassButton("تلاش دوباره",{ previewRevision++ },enabled=!busy,filled=false)}
            }
            GanjGlassButton("رد و بستن",onDismiss,Modifier.fillMaxWidth(),enabled=!busy,filled=false)
        }
    }
}
