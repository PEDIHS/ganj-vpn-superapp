package com.ganj.vpn.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ganj.vpn.composition.SupportCompositionRegistry
import com.ganj.vpn.core.controlapi.ApiError
import com.ganj.vpn.core.controlapi.ApiResult
import com.ganj.vpn.core.controlapi.SupportSenderRole
import com.ganj.vpn.core.controlapi.SupportStatus
import com.ganj.vpn.core.controlapi.SupportTicketDetail
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface SupportDeepLinkState {
    data object Loading : SupportDeepLinkState
    data class Ready(val detail: SupportTicketDetail) : SupportDeepLinkState
    data class Error(val message: String, val retryable: Boolean) : SupportDeepLinkState
}

internal fun canonicalSupportTicketIdOrNull(value: String?): String? = try {
    value?.takeIf { it.length == 36 && UUID.fromString(it).toString() == it.lowercase() }
} catch (_: IllegalArgumentException) {
    null
}

@Composable
internal fun StitchSupportDeepLinkTarget(
    ticketId: String,
    onBack: () -> Unit,
    onOpenSupportCenter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val api = remember { SupportCompositionRegistry.currentApi() }
    val scope = rememberCoroutineScope()
    val canonicalTicketId = remember(ticketId) { canonicalSupportTicketIdOrNull(ticketId) }
    var state by remember(ticketId) { mutableStateOf<SupportDeepLinkState>(SupportDeepLinkState.Loading) }

    fun mapError(error: ApiError): SupportDeepLinkState.Error = when (error) {
        is ApiError.AuthenticationRequired,
        is ApiError.AuthenticationExpired -> SupportDeepLinkState.Error("نشست حساب منقضی شده است؛ دوباره وارد شوید.", false)
        is ApiError.NotFound -> SupportDeepLinkState.Error("این درخواست پشتیبانی دیگر در دسترس نیست.", false)
        is ApiError.Network -> SupportDeepLinkState.Error("اتصال اینترنت را بررسی کنید و دوباره تلاش کنید.", true)
        is ApiError.RateLimited -> SupportDeepLinkState.Error("درخواست‌های زیادی ارسال شده است؛ کمی بعد دوباره تلاش کنید.", true)
        is ApiError.Server -> SupportDeepLinkState.Error("سرویس پشتیبانی موقتاً در دسترس نیست.", error.retryable)
        else -> SupportDeepLinkState.Error("جزئیات درخواست پشتیبانی دریافت نشد.", true)
    }

    fun load() {
        val id = canonicalTicketId ?: run {
            state = SupportDeepLinkState.Error("شناسه درخواست پشتیبانی معتبر نیست.", false)
            return
        }
        val active = api ?: run {
            state = SupportDeepLinkState.Error("سرویس پشتیبانی در این نسخه فعال نیست.", false)
            return
        }
        state = SupportDeepLinkState.Loading
        scope.launch {
            state = when (val result = withContext(Dispatchers.IO) { active.ticket(id) }) {
                is ApiResult.Success -> SupportDeepLinkState.Ready(result.value)
                is ApiResult.Failure -> mapError(result.error)
            }
        }
    }

    LaunchedEffect(ticketId) { load() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = responsiveHorizontalPadding(), vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AppHeader("پاسخ پشتیبانی", "این صفحه مستقیماً از اعلان به همان درخواست باز شده است", ::load)
        AccountBackAction(onBack)

        when (val current = state) {
            SupportDeepLinkState.Loading -> LoadingCard("در حال دریافت گفت‌وگوی پشتیبانی")
            is SupportDeepLinkState.Error -> GanjGlassSurface(
                role = GanjGlassRole.Dense,
                accent = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth(),
                shapeRadius = 22.dp,
                padding = PaddingValues(16.dp),
            ) {
                Text(current.message, color = MaterialTheme.colorScheme.onSurface)
                if (current.retryable) {
                    GanjLiquidAction(onClick = ::load, modifier = Modifier.fillMaxWidth()) {
                        Text("تلاش دوباره", modifier = Modifier.align(Alignment.Center), fontWeight = FontWeight.Bold)
                    }
                }
                GanjLiquidAction(onClick = onOpenSupportCenter, modifier = Modifier.fillMaxWidth()) {
                    Text("مرکز پشتیبانی", modifier = Modifier.align(Alignment.Center), fontWeight = FontWeight.Bold)
                }
            }
            is SupportDeepLinkState.Ready -> {
                val ticket = current.detail.ticket
                GanjGlassSurface(
                    role = GanjGlassRole.Prominent,
                    accent = when (ticket.status) {
                        SupportStatus.WAITING_USER -> GanjGold
                        SupportStatus.RESOLVED -> MaterialTheme.colorScheme.primary
                        SupportStatus.CLOSED -> MaterialTheme.colorScheme.outline
                        else -> MaterialTheme.colorScheme.secondary
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shapeRadius = 24.dp,
                    padding = PaddingValues(17.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(ticket.subject, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(isolateTechnicalLtr(ticket.publicCode), style = MaterialTheme.typography.labelSmall)
                        }
                        GanjStatusPill(
                            text = when (ticket.status) {
                                SupportStatus.OPEN -> "باز"
                                SupportStatus.WAITING_USER -> "منتظر شما"
                                SupportStatus.WAITING_SUPPORT -> "منتظر پشتیبانی"
                                SupportStatus.RESOLVED -> "حل‌شده"
                                SupportStatus.CLOSED -> "بسته"
                            },
                            tone = when (ticket.status) {
                                SupportStatus.WAITING_USER -> GanjStatusTone.Warning
                                SupportStatus.RESOLVED -> GanjStatusTone.Positive
                                SupportStatus.CLOSED -> GanjStatusTone.Neutral
                                else -> GanjStatusTone.Premium
                            },
                        )
                    }
                }

                current.detail.messages.forEach { message ->
                    val fromSupport = message.senderRole == SupportSenderRole.SUPPORT
                    GanjGlassSurface(
                        role = GanjGlassRole.Regular,
                        accent = if (fromSupport) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.fillMaxWidth(),
                        shapeRadius = 20.dp,
                        padding = PaddingValues(14.dp),
                    ) {
                        Text(
                            if (fromSupport) "پشتیبانی گنج" else if (message.senderRole == SupportSenderRole.SYSTEM) "سیستم" else "شما",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (fromSupport) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(message.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            isolateTechnicalLtr(message.createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                GanjLiquidAction(onClick = onOpenSupportCenter, modifier = Modifier.fillMaxWidth()) {
                    Text("ادامه گفتگو در مرکز پشتیبانی", modifier = Modifier.align(Alignment.Center), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
