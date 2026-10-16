package com.ganj.vpn.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.key
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.remember
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.BackHandler
import androidx.compose.ui.graphics.Brush
import com.ganj.vpn.core.controlapi.CurrentAccount
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ganj.vpn.R
import com.ganj.vpn.enterprise.BugReportInput
import com.ganj.vpn.enterprise.EnterpriseUiState
import com.ganj.vpn.presentation.ContentState
import com.ganj.vpn.presentation.GanjUiState
import com.ganj.vpn.presentation.ServiceUiModel
import com.ganj.vpn.presentation.UiTier

private val ProfileGold = GanjReference.Gold
private val ProfileGoldBright = Color(0xFFF0CD70)
private val ProfileEmerald = Color(0xFF72FCB6)

@Composable
internal fun StitchProfileScreen(
    state: GanjUiState,
    enterpriseState: EnterpriseUiState,
    onSelectService: (String) -> Unit,
    onConnect: () -> Unit,
    onBuy: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSubscriptions: () -> Unit,
    onRetry: () -> Unit,
    onEnterpriseRefresh: () -> Unit,
    onSubmitBug: (BugReportInput) -> Unit,
    onSubmitDiagnostics: (Boolean) -> Unit,
    onClearBug: () -> Unit,
    onClearDiagnostic: () -> Unit,
    modifier: Modifier = Modifier,
    headerContent: @Composable () -> Unit = {},
    currentAccount: CurrentAccount? = null,
    linked: Boolean = false,
    onOpenWallet: (() -> Unit)? = null,
    onOpenTransactions: (() -> Unit)? = null,
    botStoreApi: GanjBotStoreApi? = null,
    onOpenDevices: (() -> Unit)? = null,
    onOpenFamily: (() -> Unit)? = null,
    onOpenNotifications: (() -> Unit)? = null,
    onOpenPrivacy: (() -> Unit)? = null,
    onOpenSupport: (() -> Unit)? = null,
    onOpenDetails: (() -> Unit)? = null,
    onOpenVip: (() -> Unit)? = null,
    onTelegramConnect: (() -> Unit)? = null,
) {
    val activeServices = remember(state.services) { state.serviceItems.filter { it.isActive } }
    val premium = activeServices.any { it.tier != UiTier.FREE }
    val selected = state.selectedService
    var panel by rememberSaveable { mutableStateOf("overview") }
    BackHandler(panel != "overview") { panel = "overview" }
    val ink = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val name = telegramIdentityDisplayName(currentAccount) ?: if(linked) "حساب گنج" else "به گنج خوش آمدید"
    val username = telegramIdentityUsername(currentAccount)
    val telegramAvatar = rememberGanjTelegramAvatar(currentAccount?.takeIf { linked }, botStoreApi)
    val wallet = if (linked && botStoreApi != null) rememberGanjProfileWallet(botStoreApi, currentAccount?.id) else null
    LazyColumn(modifier.fillMaxSize().then(profileRevealModifier(panel)).testTag("reference-profile"),
        contentPadding = PaddingValues(start=responsiveHorizontalPadding(), end=responsiveHorizontalPadding(), top=20.dp,
            bottom=GanjFloatingNavContentClearance),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(key="profile-header") {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                if(panel!="overview") AccountBackAction({panel="overview"})
                Column(Modifier.weight(1f)) {
                    Text(when(panel){"account"->"مدیریت حساب";"services"->"اشتراک‌های من";"support"->"گزارش و عیب‌یابی";else->"پروفایل"},
                        style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.SemiBold,color=ink)
                    Text("حساب شما، همه‌چیز در یک نگاه",style=MaterialTheme.typography.labelSmall,color=muted)
                }
                onOpenNotifications?.let { GanjReferenceNotificationButton(it) }
            }
        }
        when(panel) {
            "account" -> item(key="account-actions") { headerContent() }
            "services" -> {
                when(val services=state.services) {
                    ContentState.Loading -> item{ LoadingCard("در حال دریافت سرویس‌ها") }
                    ContentState.Empty -> item{ ProfileEmptyServices(onBuy) }
                    ContentState.AuthRequired -> item{ AuthCard(onRetry) }
                    is ContentState.Error -> item{ ErrorCard(services.failure,onRetry) }
                    is ContentState.Ready -> items(services.items,key={it.entitlementId}) { service ->
                        ProfileServiceCard(service,service.entitlementId==state.selectedEntitlementId,{onSelectService(service.entitlementId)},onConnect)
                    }
                }
            }
            "support" -> {
                item { EnterpriseStatusCard(enterpriseState.availability,onEnterpriseRefresh) }
                if(enterpriseState.features.bugReportsEnabled) item { BugReportPanel(enterpriseState.bugReport,onSubmitBug,onClearBug) }
                if(enterpriseState.features.diagnosticsEnabled) item { DiagnosticPanel(enterpriseState.diagnostic,onSubmitDiagnostics,onClearDiagnostic) }
            }
            else -> {
                item(key="profile-identity") {
                    GanjProfileIdentity(name, username, linked,
                        if (state.services is ContentState.Ready) premium else null,
                        onManage = { panel = "account" }, onOpenVip = onOpenVip ?: onBuy,
                        avatar = telegramAvatar, onConnectTelegram = onTelegramConnect ?: { panel = "account" })
                }
                if (linked && onOpenWallet != null) item(key="wallet-summary") {
                    GanjProfileWalletCard(wallet?.balance, wallet?.busy ?: false, wallet?.message,
                        onOpenWallet, onOpenTransactions ?: onOpenWallet,
                        onRefresh = { wallet?.refresh() })
                }
                if (state.servicesStale) item(key="profile-services-stale") {
                    GanjInlineStatusPanel(
                        "اطلاعات اشتراک‌ها نیاز به به‌روزرسانی دارد",
                        "آخرین اطلاعات معتبر نمایش داده شده؛ وضعیت فعلی هنگام اتصال بررسی می‌شود.",
                        accent=MaterialTheme.colorScheme.onSurfaceVariant, tone=GanjStatusTone.Neutral,
                        actionText="تلاش دوباره", onAction=onRetry,
                    )
                }
                item(key="profile-summary") {
                    GanjGlassSurface(GanjGlassRole.OpaqueFallback,modifier=Modifier.fillMaxWidth(),shapeRadius=14.dp,padding=PaddingValues(15.dp)) {
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                            Row(Modifier.weight(1f), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                GanjProfileIcon(GanjProfileSymbol.Subscription, MaterialTheme.colorScheme.primary, Modifier.size(18.dp))
                                Text("اشتراک انتخاب‌شده",style=MaterialTheme.typography.labelMedium,color=muted)
                            }
                            if(selected!=null) GanjStatusPill(profileServiceStatus(selected),if(selected.isActive)GanjStatusTone.Positive else GanjStatusTone.Neutral)
                        }
                        Text(selected?.let { subscriptionUsername(it) } ?: "اشتراکی انتخاب نشده است",style=MaterialTheme.typography.titleMedium,color=ink,fontWeight=FontWeight.Medium)
                        selected?.let { service ->
                            if(service.trafficUsageAvailable && service.usageFraction != null) {
                                Text(profileTraffic(service.trafficUsedBytes)+" از "+profileTraffic(service.trafficLimitBytes)+" مصرف شده",style=MaterialTheme.typography.bodyMedium,color=ink)
                                GanjSlimProgress(service.usageFraction!!)
                            }
                            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(12.dp), verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                Text(if(service.trafficUsageAvailable) profileTraffic(service.remainingBytes)+" باقی‌مانده" else "اطلاعات مصرف در دسترس نیست",style=MaterialTheme.typography.labelSmall,color=muted)
                                Text(profileExpiry(service.expiresAt),style=MaterialTheme.typography.labelSmall,color=muted)
                            }
                        }
                        GanjGlassButton(if(selected==null)"انتخاب اشتراک" else "جزئیات اشتراک",{if(selected==null)onOpenSubscriptions() else onOpenDetails?.invoke() ?: run{panel="services"}},filled=false,modifier=Modifier.fillMaxWidth())
                    }
                }
                item(key="account-services") {
                    ProfileSectionTitle("حساب و سرویس‌ها")
                    ProfileMenuGroup {
                        ProfileMenuAction("اشتراک‌های من",
                            if (state.services is ContentState.Ready) activeServices.size.toPersianDigits() + " سرویس فعال" else "بررسی و انتخاب اشتراک",
                            GanjProfileSymbol.Subscription, GanjProfileAccent.Emerald,
                            Modifier.testTag("profile-to-subscriptions"), onOpenSubscriptions)
                        if (linked) {
                            onOpenFamily?.let {
                                ProfileMenuDivider()
                                ProfileMenuAction("اعضا و ظرفیت اشتراک", "دعوت و مدیریت اعضا",
                                    GanjProfileSymbol.Family, GanjProfileAccent.Iris, onClick = it)
                            }
                            onOpenDevices?.let {
                                ProfileMenuDivider()
                                ProfileMenuAction("دستگاه‌های من", "مدیریت دسترسی دستگاه‌ها",
                                    GanjProfileSymbol.Devices, GanjProfileAccent.Sky, onClick = it)
                            }
                        }
                    }
                }
                item(key="profile-upgrade") {
                    GanjSignatureBanner("گنج، یک سطح بالاتر", "مشاهدهٔ پلن‌های VIP", onOpenVip ?: onBuy,
                        Modifier.testTag("profile-to-vip"))
                }
                item(key="settings-support") {
                    ProfileSectionTitle("تنظیمات و پشتیبانی")
                    ProfileMenuGroup {
                        ProfileMenuAction("ظاهر و دسترس‌پذیری", "رنگ، حرکت و شفافیت",
                            GanjProfileSymbol.Appearance, GanjProfileAccent.Iris,
                            Modifier.testTag("profile-to-settings"), onOpenSettings)
                        ProfileMenuDivider()
                        ProfileMenuAction("حریم خصوصی و امنیت", "تنظیمات حساب و دسترسی‌ها",
                            GanjProfileSymbol.Shield, GanjProfileAccent.Emerald, onClick = onOpenPrivacy ?: onOpenSettings)
                        ProfileMenuDivider()
                        ProfileMenuAction("پشتیبانی و گزارش مشکل", "ارتباط با تیم گنج",
                            GanjProfileSymbol.Support, GanjProfileAccent.Sky, onClick = onOpenSupport ?: { panel = "support" })
                        ProfileMenuDivider()
                        ProfileMenuAction("عیب‌یابی اتصال", "بررسی و گزارش وضعیت اتصال",
                            GanjProfileSymbol.Diagnostics, GanjProfileAccent.Gold, onClick = { panel = "support" })
                    }
                }
                item(key="account-session") {
                    if (linked) GanjGlassButton("مدیریت نشست و خروج از حساب",{panel="account"},filled=false,modifier=Modifier.fillMaxWidth())
                    Text("GANJ VPN · "+com.ganj.vpn.BuildConfig.VERSION_NAME,style=MaterialTheme.typography.labelSmall,color=muted,modifier=Modifier.fillMaxWidth(),textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun ProfileMenuGroup(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surface)
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f), shape), content = content)
}

@Composable
private fun ProfileMenuAction(
    title: String, subtitle: String, symbol: GanjProfileSymbol, tone: GanjProfileAccent,
    modifier: Modifier = Modifier, onClick: () -> Unit,
) = GanjReferenceMenuRow(title, subtitle, modifier, accent = profileAccent(tone), grouped = true,
    leading = { GanjProfileIconTile(symbol, tone) }, onClick = onClick)

@Composable
private fun ProfileMenuDivider() {
    HorizontalDivider(Modifier.padding(start = 68.dp, end = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f), thickness = .5.dp)
}

@Composable
private fun ProfileServiceCard(
    service: ServiceUiModel,
    selected: Boolean,
    onSelect: () -> Unit,
    onConnect: () -> Unit,
) {
    val premium = service.tier != UiTier.FREE
    val accent = when {
        !service.isActive -> MaterialTheme.colorScheme.error
        premium -> ProfileGold
        else -> MaterialTheme.colorScheme.primary
    }

    GanjGlassSurface(
        role = if (selected) GanjGlassRole.Prominent else GanjGlassRole.Dense,
        accent = accent,
        modifier = Modifier.fillMaxWidth(),
        shapeRadius = 22.dp,
        padding = PaddingValues(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = service.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${profileTier(service.tier)} • ${profileServiceStatus(service)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selected) {
                ProfileBadge(text = "انتخاب‌شده", gold = premium)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ProfileInfoChip(
                label = "ترافیک",
                value = if (service.trafficUsageAvailable) profileTraffic(service.remainingBytes)
                    else androidx.compose.ui.res.stringResource(com.ganj.vpn.R.string.subscription_usage_unavailable),
                modifier = Modifier.weight(1f),
            )
            ProfileInfoChip(
                label = "دستگاه",
                value = service.deviceLimit.toPersianDigits(),
                modifier = Modifier.weight(1f),
                gold = premium,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ProfileInfoChip(
                label = "انقضا",
                value = profileExpiry(service.expiresAt),
                modifier = Modifier.weight(1f),
            )
            ProfileInfoChip(
                label = "پروتکل",
                value = profileProtocols(service.allowedProtocols),
                modifier = Modifier.weight(1f),
                gold = premium,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ProfileSecondaryAction(
                text = if (selected) "انتخاب شده" else "انتخاب سرویس",
                enabled = service.isActive,
                onClick = onSelect,
                modifier = Modifier.weight(1f),
            )
            GanjLiquidAction(
                onClick = onConnect,
                enabled = service.isActive && selected,
                accent = MaterialTheme.colorScheme.primary,
                shapeRadius = 999.dp,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = "اتصال",
                    modifier = Modifier.align(Alignment.Center),
                    color = if (service.isActive && selected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun ProfileStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    gold: Boolean = false,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.46f))
            .border(
                1.dp,
                if (gold) ProfileGold.copy(alpha = 0.24f)
                else MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                RoundedCornerShape(18.dp),
            )
            .padding(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun ProfileInfoChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    gold: Boolean = false,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.44f))
            .padding(horizontal = 11.dp, vertical = 9.dp),
    ) {
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (gold) ProfileGoldBright else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ProfileSecondaryAction(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GanjGlassButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        accent = MaterialTheme.colorScheme.outline,
        filled = false,
    )
}

@Composable
private fun ProfileBadge(text: String, gold: Boolean = false) {
    val accent = if (gold) ProfileGold else MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(accent.copy(alpha = 0.14f))
            .border(1.dp, accent.copy(alpha = 0.34f), RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ProfileSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
    )
}

@Composable
private fun ProfileEmptyServices(onBuy: () -> Unit) {
    GanjGlassSurface(
        role = GanjGlassRole.Dense,
        accent = MaterialTheme.colorScheme.outline,
        modifier = Modifier.fillMaxWidth(),
        shapeRadius = 22.dp,
        padding = PaddingValues(16.dp),
    ) {
        Text(
            text = "سرویس فعالی ندارید",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "از فروشگاه یک سرویس انتخاب کنید تا در این بخش نمایش داده شود.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ProfileSecondaryAction(
            text = "رفتن به فروشگاه",
            enabled = true,
            onClick = onBuy,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun profileTier(tier: UiTier): String = when (tier) {
    UiTier.FREE -> "رایگان"
    UiTier.PREMIUM -> "پریمیوم"
    UiTier.VIP -> "وی‌آی‌پی"
}

private fun profileServiceStatus(service: ServiceUiModel): String = when {
    service.isActive -> "فعال"
    else -> when (service.status.name) {
        "PENDING" -> "در انتظار"
        "DISABLED" -> "غیرفعال"
        "EXPIRED" -> "منقضی"
        "REVOKED" -> "لغوشده"
        else -> "نامشخص"
    }
}

private fun profileTraffic(bytes: Long?): String = when {
    bytes == null -> "نامحدود"
    bytes >= 1_000_000_000L -> "${(bytes / 1_000_000_000L).toPersianDigits()} گیگابایت"
    else -> "${(bytes / 1_000_000L).toPersianDigits()} مگابایت"
}

private fun profileExpiry(expiresAt: String?): String = when {
    expiresAt.isNullOrBlank() -> "بدون تاریخ مشخص"
    else -> isolateTechnicalLtr(expiresAt.take(10).toPersianDigits())
}

private fun profileProtocols(protocols: Set<String>): String = when {
    protocols.isEmpty() -> "—"
    protocols.size == 1 -> isolateTechnicalLtr(protocols.first())
    else -> "${protocols.size.toPersianDigits()} پروتکل"
}
