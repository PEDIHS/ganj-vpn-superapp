package com.ganj.vpn.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

// Account/family tools use a restrained cool accent. VPN connection status keeps its own semantic colors.
private val FamilyIndigo = Color(0xFF8B91EE)
private val FamilyCyan = Color(0xFF62BFD7)
private val FamilyMint = Color(0xFF6AC5B0)

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
            }} catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                error = "دریافت اطلاعات اشتراک ناموفق بود. دوباره تلاش کنید."
            } finally { busy=false }
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
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                error = "عملیات انجام نشد. اتصال را بررسی و دوباره تلاش کنید."
            } finally { busy=false }
        }
    }
    LaunchedEffect(serviceId) {reply=null;load()}
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = responsiveHorizontalPadding(), vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AccountBackAction(onBack)
        Text("مدیریت اشتراک مشترک", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "اعضای خانواده با تلگرام خودشان وارد می‌شوند. حساب، کیف پول و تراکنش هر نفر مستقل می‌ماند.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (services.isEmpty()) {
            GanjGlassSurface(GanjGlassRole.Regular, accent = FamilyIndigo, shapeRadius = 14.dp) {
                Text("هنوز اشتراکی برای مدیریت وجود ندارد.", style = MaterialTheme.typography.titleMedium)
                Text("بعد از فعال شدن اشتراک یا قبول دعوت، اعضا و دستگاه‌ها اینجا نمایش داده می‌شوند.")
            }
        } else {
            GanjGlassSurface(GanjGlassRole.Regular, accent = FamilyIndigo, shapeRadius = 14.dp) {
                Text("انتخاب اشتراک", style = MaterialTheme.typography.titleMedium)
                services.forEach { service ->
                    GanjChoicePill(
                        service.username ?: service.displayName,
                        service.entitlementId == serviceId,
                        { if (!busy) serviceId = service.entitlementId },
                        Modifier.fillMaxWidth(),
                        enabled = !busy,
                    )
                }
            }
        }
        if (busy) Text("در حال همگام‌سازی با حساب گنج…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        error?.let {
            GanjGlassSurface(GanjGlassRole.Regular, accent = MaterialTheme.colorScheme.error, shapeRadius = 14.dp) {
                Text(it, color = MaterialTheme.colorScheme.error)
                GanjGlassButton("تلاش دوباره", ::load, enabled = !busy, filled = false)
            }
        }
        reply?.let { data ->
            val slotsAvailable = (data.deviceLimit - data.slotsUsed).coerceAtLeast(0)
            val usage = if (data.deviceLimit > 0) {
                (data.slotsUsed.toFloat() / data.deviceLimit).coerceIn(0f, 1f)
            } else 0f
            GanjGlassSurface(GanjGlassRole.Prominent, accent = FamilyIndigo, shapeRadius = 14.dp) {
                Text("اشتراک فعال", style = MaterialTheme.typography.labelMedium, color = FamilyIndigo)
                Text(data.serviceName.orEmpty(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    data.slotsUsed.toPersianDigits() + " از " + data.deviceLimit.toPersianDigits() + " جایگاه استفاده شده",
                    style = MaterialTheme.typography.bodyMedium,
                )
                LinearProgressIndicator(
                    progress = { usage },
                    modifier = Modifier.fillMaxWidth().height(5.dp),
                    color = FamilyIndigo,
                    trackColor = MaterialTheme.colorScheme.outlineVariant,
                )
                Text(
                    slotsAvailable.toPersianDigits() + " جایگاه برای دستگاه جدید باقی مانده",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                if (data.owner) {
                    GanjGlassButton(
                        "ساخت و اشتراک‌گذاری لینک دعوت",
                        { mutate { apiClient, id -> apiClient.createInvite(id) } },
                        Modifier.fillMaxWidth(),
                        enabled = !busy && slotsAvailable > 0,
                        accent = FamilyIndigo,
                        contentColor = Color(0xFF101427),
                    )
                    Text("لینک هر دعوت فقط یک بار قابل استفاده است و ۲۴ ساعت اعتبار دارد.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            GanjGlassSurface(GanjGlassRole.Regular, accent = FamilyMint, shapeRadius = 14.dp) {
                Text("افراد دارای دسترسی", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (data.members.isEmpty()) Text("هنوز عضو دیگری به اشتراک اضافه نشده است.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                data.members.forEach { member ->
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(member.name + if (member.current) " · شما" else "",
                            style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text(if (member.current) "حساب تلگرام فعلی" else "عضو اشتراک",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (data.owner || member.current) {
                            GanjGlassButton(
                                if (member.current) "خروج از این اشتراک" else "حذف دسترسی عضو",
                                { confirmation = member.id to member.name },
                                enabled = !busy, filled = false, accent = FamilyMint,
                            )
                        }
                    }
                }
            }
            GanjGlassSurface(GanjGlassRole.Regular, accent = FamilyCyan, shapeRadius = 14.dp) {
                Text("دستگاه‌های متصل", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (data.devices.isEmpty()) Text("هیچ دستگاهی به این اشتراک اختصاص نیافته است.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                data.devices.forEach { device ->
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(device.name + if (device.current) " · همین دستگاه" else "",
                            style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        if (device.own && !device.current) {
                            GanjGlassButton(
                                "لغو دسترسی دستگاه",
                                { onRevokeDevice(device.id) },
                                enabled = !busy, filled = false, accent = FamilyCyan,
                            )
                        }
                    }
                }
            }
            if (data.owner && data.invites.isNotEmpty()) {
                GanjGlassSurface(GanjGlassRole.Regular, accent = FamilyIndigo, shapeRadius = 14.dp) {
                    Text("دعوت‌های استفاده‌نشده", style = MaterialTheme.typography.titleMedium)
                    data.invites.forEach { invite ->
                        GanjGlassButton(
                            "لغو دعوت · " + invite.expiresAt.take(10),
                            { mutate { apiClient, id -> apiClient.revokeInvite(id, invite.id) } },
                            filled = false, enabled = !busy, accent = FamilyIndigo,
                        )
                    }
                }
            }
            GanjGlassButton("به‌روزرسانی اطلاعات", ::load, enabled = !busy, filled = false)
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
        GanjGlassSurface(role=GanjGlassRole.Dense,accent=FamilyIndigo,modifier=Modifier.fillMaxWidth().heightIn(max=600.dp).verticalScroll(rememberScrollState()),shapeRadius=18.dp,padding=PaddingValues(20.dp)) {
            Text("دعوت خصوصی به اشتراک",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
            Text("مالک اشتراک، دسترسی استفاده از سرویس را با شما به اشتراک می‌گذارد؛ مالک حساب و کیف پول شما نمی‌شود.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
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
                    },Modifier.fillMaxWidth(),enabled=!busy&&!invite.ownInvite,accent=FamilyIndigo,contentColor=Color(0xFF101427))
                }
                if(busy)Text("در حال بررسی…")
                error?.let {Text(it,color=MaterialTheme.colorScheme.error);GanjGlassButton("تلاش دوباره",{ previewRevision++ },enabled=!busy,filled=false)}
            }
            GanjGlassButton("رد و بستن",onDismiss,Modifier.fillMaxWidth(),enabled=!busy,filled=false)
        }
    }
}
