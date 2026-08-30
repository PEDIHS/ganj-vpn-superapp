package com.ganj.vpn.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ganj.vpn.composition.SupportCompositionRegistry
import com.ganj.vpn.core.controlapi.ApiError
import com.ganj.vpn.core.controlapi.ApiResult
import com.ganj.vpn.core.controlapi.SupportCategory
import com.ganj.vpn.core.controlapi.SupportMessage
import com.ganj.vpn.core.controlapi.SupportPriority
import com.ganj.vpn.core.controlapi.SupportSenderRole
import com.ganj.vpn.core.controlapi.SupportStatus
import com.ganj.vpn.core.controlapi.SupportTicket
import com.ganj.vpn.core.controlapi.SupportTicketDetail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface SupportSurface {
    data object Center : SupportSurface
    data object NewTicket : SupportSurface
    data class Detail(val ticketId: String) : SupportSurface
}

private sealed interface SupportListState {
    data object Loading : SupportListState
    data class Ready(val tickets: List<SupportTicket>) : SupportListState
    data object AuthRequired : SupportListState
    data class Error(val message: String, val retryable: Boolean) : SupportListState
}

private sealed interface SupportDetailState {
    data object Loading : SupportDetailState
    data class Ready(val detail: SupportTicketDetail) : SupportDetailState
    data class Error(val message: String, val retryable: Boolean) : SupportDetailState
}

@Composable
internal fun StitchSupportHub(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val api = remember { SupportCompositionRegistry.currentApi() }
    val scope = rememberCoroutineScope()
    var surface by remember { mutableStateOf<SupportSurface>(SupportSurface.Center) }
    var listState by remember { mutableStateOf<SupportListState>(SupportListState.Loading) }
    var detailState by remember { mutableStateOf<SupportDetailState>(SupportDetailState.Loading) }
    var mutationBusy by remember { mutableStateOf(false) }
    var mutationError by remember { mutableStateOf<String?>(null) }

    fun mapError(error: ApiError): Pair<String, Boolean> = when (error) {
        is ApiError.AuthenticationRequired,
        is ApiError.AuthenticationExpired -> "نشست حساب منقضی شده است؛ دوباره وارد شوید." to false
        is ApiError.Network -> "اتصال اینترنت را بررسی کنید و دوباره تلاش کنید." to true
        is ApiError.RateLimited -> "درخواست‌های زیادی ارسال شده است؛ کمی بعد دوباره تلاش کنید." to true
        is ApiError.Forbidden -> "دسترسی این حساب به پشتیبانی محدود شده است." to false
        is ApiError.NotFound -> "این درخواست پشتیبانی دیگر در دسترس نیست." to false
        is ApiError.Conflict -> when (error.code) {
            "support_ticket_closed" -> "این درخواست بسته شده است؛ برای ارسال پیام ابتدا آن را دوباره باز کنید." to false
            "idempotency_conflict" -> "این پیام قبلاً با محتوای دیگری ثبت شده است. صفحه را به‌روزرسانی کنید." to true
            else -> "وضعیت درخواست تغییر کرده است؛ دوباره به‌روزرسانی کنید." to true
        }
        is ApiError.Validation -> when (error.reason) {
            "sensitive_content_rejected" -> "برای امنیت، کانفیگ VPN، توکن، کلید خصوصی یا اطلاعات محرمانه را داخل پیام ارسال نکنید." to false
            else -> "متن یا اطلاعات درخواست معتبر نیست." to false
        }
        is ApiError.Server -> "سرویس پشتیبانی موقتاً در دسترس نیست." to error.retryable
        else -> "پشتیبانی در حال حاضر در دسترس نیست." to true
    }

    fun refreshTickets() {
        val active = api ?: run {
            listState = SupportListState.Error("سرویس پشتیبانی در این نسخه فعال نیست.", false)
            return
        }
        listState = SupportListState.Loading
        scope.launch {
            listState = when (val result = withContext(Dispatchers.IO) { active.tickets() }) {
                is ApiResult.Success -> SupportListState.Ready(result.value)
                is ApiResult.Failure -> {
                    val (message, retryable) = mapError(result.error)
                    if (result.error is ApiError.AuthenticationRequired || result.error is ApiError.AuthenticationExpired) {
                        SupportListState.AuthRequired
                    } else {
                        SupportListState.Error(message, retryable)
                    }
                }
            }
        }
    }

    fun openTicket(ticketId: String) {
        val active = api ?: return
        surface = SupportSurface.Detail(ticketId)
        detailState = SupportDetailState.Loading
        mutationError = null
        scope.launch {
            detailState = when (val result = withContext(Dispatchers.IO) { active.ticket(ticketId) }) {
                is ApiResult.Success -> SupportDetailState.Ready(result.value)
                is ApiResult.Failure -> {
                    val (message, retryable) = mapError(result.error)
                    SupportDetailState.Error(message, retryable)
                }
            }
        }
    }

    fun createTicket(category: SupportCategory, priority: SupportPriority, subject: String, body: String) {
        val active = api ?: return
        if (mutationBusy) return
        mutationBusy = true
        mutationError = null
        scope.launch {
            when (val result = withContext(Dispatchers.IO) { active.createTicket(category, priority, subject, body) }) {
                is ApiResult.Success -> {
                    refreshTickets()
                    openTicket(result.value.id)
                }
                is ApiResult.Failure -> mutationError = mapError(result.error).first
            }
            mutationBusy = false
        }
    }

    fun reply(ticketId: String, body: String) {
        val active = api ?: return
        if (mutationBusy) return
        mutationBusy = true
        mutationError = null
        scope.launch {
            when (val result = withContext(Dispatchers.IO) { active.reply(ticketId, body) }) {
                is ApiResult.Success -> {
                    when (val refreshed = withContext(Dispatchers.IO) { active.ticket(ticketId) }) {
                        is ApiResult.Success -> detailState = SupportDetailState.Ready(refreshed.value)
                        is ApiResult.Failure -> {
                            val (message, retryable) = mapError(refreshed.error)
                            detailState = SupportDetailState.Error(message, retryable)
                        }
                    }
                    refreshTickets()
                }
                is ApiResult.Failure -> mutationError = mapError(result.error).first
            }
            mutationBusy = false
        }
    }

    fun reopen(ticketId: String) {
        val active = api ?: return
        if (mutationBusy) return
        mutationBusy = true
        mutationError = null
        scope.launch {
            when (val result = withContext(Dispatchers.IO) { active.reopen(ticketId) }) {
                is ApiResult.Success -> {
                    when (val refreshed = withContext(Dispatchers.IO) { active.ticket(ticketId) }) {
                        is ApiResult.Success -> detailState = SupportDetailState.Ready(refreshed.value)
                        is ApiResult.Failure -> {
                            val (message, retryable) = mapError(refreshed.error)
                            detailState = SupportDetailState.Error(message, retryable)
                        }
                    }
                    refreshTickets()
                }
                is ApiResult.Failure -> mutationError = mapError(result.error).first
            }
            mutationBusy = false
        }
    }

    BackHandler(enabled = surface != SupportSurface.Center) {
        surface = SupportSurface.Center
        mutationError = null
    }

    LaunchedEffect(Unit) { refreshTickets() }

    when (val current = surface) {
        SupportSurface.Center -> SupportCenterScreen(
            state = listState,
            onBack = onBack,
            onRefresh = ::refreshTickets,
            onNewTicket = {
                mutationError = null
                surface = SupportSurface.NewTicket
            },
            onOpenTicket = ::openTicket,
            modifier = modifier,
        )
        SupportSurface.NewTicket -> SupportNewTicketScreen(
            busy = mutationBusy,
            errorMessage = mutationError,
            onBack = {
                mutationError = null
                surface = SupportSurface.Center
            },
            onCreate = ::createTicket,
            modifier = modifier,
        )
        is SupportSurface.Detail -> SupportTicketDetailScreen(
            state = detailState,
            busy = mutationBusy,
            mutationError = mutationError,
            onBack = {
                mutationError = null
                surface = SupportSurface.Center
            },
            onRefresh = { openTicket(current.ticketId) },
            onReply = { reply(current.ticketId, it) },
            onReopen = { reopen(current.ticketId) },
            modifier = modifier,
        )
    }
}

@Composable
private fun SupportCenterScreen(
    state: SupportListState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onNewTicket: () -> Unit,
    onOpenTicket: (String) -> Unit,
    modifier: Modifier,
) {
    Page(modifier.fillMaxSize()) {
        AppHeader("پشتیبانی", "درخواست‌ها و پاسخ‌های واقعی تیم پشتیبانی", onRefresh)
        AccountBackAction(onBack)
        GanjLiquidAction(onClick = onNewTicket, modifier = Modifier.fillMaxWidth()) {
            Text("درخواست جدید", modifier = Modifier.align(Alignment.Center), fontWeight = FontWeight.Bold)
        }
        when (state) {
            SupportListState.Loading -> LoadingCard("در حال دریافت درخواست‌های پشتیبانی")
            SupportListState.AuthRequired -> AuthCard(onRefresh)
            is SupportListState.Error -> ContentCard(MaterialTheme.colorScheme.error) {
                Text(state.message)
                if (state.retryable) {
                    GanjLiquidAction(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                        Text("تلاش دوباره", modifier = Modifier.align(Alignment.Center), fontWeight = FontWeight.Bold)
                    }
                }
            }
            is SupportListState.Ready -> if (state.tickets.isEmpty()) {
                EmptyCard(
                    "درخواست پشتیبانی ندارید",
                    "اگر مشکلی دارید یک درخواست جدید ثبت کنید. کانفیگ، توکن یا کلید خصوصی را ارسال نکنید.",
                    onNewTicket,
                )
            } else {
                state.tickets.forEach { ticket -> SupportTicketRow(ticket, onOpenTicket) }
            }
        }
    }
}

@Composable
private fun SupportTicketRow(ticket: SupportTicket, onOpenTicket: (String) -> Unit) {
    GanjGlassSurface(
        role = GanjGlassRole.Regular,
        accent = supportStatusAccent(ticket.status),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { onOpenTicket(ticket.id) },
        shapeRadius = 22.dp,
        padding = PaddingValues(15.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(ticket.subject, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${supportCategoryLabel(ticket.category)} · ${supportPriorityLabel(ticket.priority)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            GanjStatusPill(supportStatusLabel(ticket.status), supportStatusTone(ticket.status))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(isolateTechnicalLtr(ticket.publicCode), style = MaterialTheme.typography.labelSmall)
            Text(isolateTechnicalLtr(ticket.updatedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SupportNewTicketScreen(
    busy: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    onCreate: (SupportCategory, SupportPriority, String, String) -> Unit,
    modifier: Modifier,
) {
    var category by rememberSaveable { mutableStateOf(SupportCategory.CONNECTION.name) }
    var priority by rememberSaveable { mutableStateOf(SupportPriority.NORMAL.name) }
    var subject by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    val selectedCategory = SupportCategory.valueOf(category)
    val selectedPriority = SupportPriority.valueOf(priority)
    val canSubmit = !busy && subject.trim().length in 3..160 && body.trim().length in 10..5000

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = responsiveHorizontalPadding(), vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AppHeader("درخواست جدید", "مشکل را بدون اطلاعات محرمانه توضیح دهید", null)
        AccountBackAction(onBack)
        Text("دسته‌بندی", fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SupportCategory.entries.forEach { option ->
                SupportChoiceChip(
                    text = supportCategoryLabel(option),
                    selected = selectedCategory == option,
                    enabled = !busy,
                    onClick = { category = option.name },
                )
            }
        }
        Text("اولویت", fontWeight = FontWeight.Bold)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SupportPriority.entries.forEach { option ->
                SupportChoiceChip(
                    text = supportPriorityLabel(option),
                    selected = selectedPriority == option,
                    enabled = !busy,
                    onClick = { priority = option.name },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        SupportTextField(
            value = subject,
            onValueChange = { subject = it.take(160) },
            enabled = !busy,
            label = "عنوان کوتاه",
            singleLine = true,
        )
        SupportTextField(
            value = body,
            onValueChange = { body = it.take(5000) },
            enabled = !busy,
            label = "شرح مشکل",
            minLines = 5,
        )
        ContentCard(MaterialTheme.colorScheme.secondary) {
            Text("حریم خصوصی", fontWeight = FontWeight.Bold)
            Text(
                "URI یا کانفیگ VPN، توکن، رمز عبور و کلید خصوصی را داخل تیکت قرار ندهید. سیستم محتوای حساس شناخته‌شده را رد می‌کند.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        errorMessage?.let {
            ContentCard(MaterialTheme.colorScheme.error) { Text(it) }
        }
        GanjLiquidAction(
            onClick = { onCreate(selectedCategory, selectedPriority, subject.trim(), body.trim()) },
            enabled = canSubmit,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (busy) "در حال ثبت…" else "ثبت درخواست",
                modifier = Modifier.align(Alignment.Center),
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun SupportTicketDetailScreen(
    state: SupportDetailState,
    busy: Boolean,
    mutationError: String?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onReply: (String) -> Unit,
    onReopen: () -> Unit,
    modifier: Modifier,
) {
    var replyText by rememberSaveable { mutableStateOf("") }
    Page(modifier.fillMaxSize()) {
        AppHeader("گفت‌وگوی پشتیبانی", "پیام‌های ثبت‌شده روی همین درخواست", onRefresh)
        AccountBackAction(onBack)
        when (state) {
            SupportDetailState.Loading -> LoadingCard("در حال دریافت گفت‌وگو")
            is SupportDetailState.Error -> ContentCard(MaterialTheme.colorScheme.error) {
                Text(state.message)
                if (state.retryable) {
                    GanjLiquidAction(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                        Text("تلاش دوباره", modifier = Modifier.align(Alignment.Center), fontWeight = FontWeight.Bold)
                    }
                }
            }
            is SupportDetailState.Ready -> {
                val ticket = state.detail.ticket
                ContentCard(supportStatusAccent(ticket.status)) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(ticket.subject, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${supportCategoryLabel(ticket.category)} · ${supportPriorityLabel(ticket.priority)} · ${isolateTechnicalLtr(ticket.publicCode)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        GanjStatusPill(supportStatusLabel(ticket.status), supportStatusTone(ticket.status))
                    }
                }
                if (state.detail.messages.isEmpty()) {
                    EmptyCard("پیامی وجود ندارد", "گفت‌وگوی این درخواست خالی است.", onRefresh)
                } else {
                    state.detail.messages.forEach(::SupportMessageCard)
                }
                mutationError?.let { ContentCard(MaterialTheme.colorScheme.error) { Text(it) } }
                if (ticket.status == SupportStatus.CLOSED || ticket.status == SupportStatus.RESOLVED) {
                    GanjLiquidAction(onClick = onReopen, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Text(if (busy) "در حال بازکردن…" else "باز کردن دوباره درخواست", modifier = Modifier.align(Alignment.Center), fontWeight = FontWeight.Bold)
                    }
                } else {
                    SupportTextField(
                        value = replyText,
                        onValueChange = { replyText = it.take(8000) },
                        enabled = !busy,
                        label = "پاسخ شما",
                        minLines = 3,
                    )
                    GanjLiquidAction(
                        onClick = {
                            val value = replyText.trim()
                            if (value.isNotEmpty()) {
                                onReply(value)
                                replyText = ""
                            }
                        },
                        enabled = !busy && replyText.trim().isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (busy) "در حال ارسال…" else "ارسال پاسخ", modifier = Modifier.align(Alignment.Center), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SupportMessageCard(message: SupportMessage) {
    val fromUser = message.senderRole == SupportSenderRole.USER
    val accent = when (message.senderRole) {
        SupportSenderRole.USER -> MaterialTheme.colorScheme.primary
        SupportSenderRole.SUPPORT -> GanjGold
        SupportSenderRole.SYSTEM -> MaterialTheme.colorScheme.outline
    }
    GanjGlassSurface(
        role = if (fromUser) GanjGlassRole.Regular else GanjGlassRole.Dense,
        accent = accent,
        modifier = Modifier.fillMaxWidth(),
        shapeRadius = 20.dp,
        padding = PaddingValues(14.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                when (message.senderRole) {
                    SupportSenderRole.USER -> "شما"
                    SupportSenderRole.SUPPORT -> "پشتیبانی گنج"
                    SupportSenderRole.SYSTEM -> "سیستم"
                },
                fontWeight = FontWeight.Bold,
                color = accent,
            )
            Text(isolateTechnicalLtr(message.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(message.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun SupportChoiceChip(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(accent.copy(alpha = if (selected) 0.16f else 0.05f))
            .border(1.dp, accent.copy(alpha = if (selected) 0.52f else 0.20f), shape)
            .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }
}

@Composable
private fun SupportTextField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    label: String,
    singleLine: Boolean = false,
    minLines: Int = 1,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = minLines,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.48f),
            disabledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.28f),
            focusedIndicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.56f),
            unfocusedIndicatorColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f),
            disabledIndicatorColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.10f),
        ),
    )
}

@Composable
private fun supportStatusAccent(status: SupportStatus) = when (status) {
    SupportStatus.OPEN -> MaterialTheme.colorScheme.primary
    SupportStatus.WAITING_USER -> GanjWarning
    SupportStatus.WAITING_SUPPORT -> MaterialTheme.colorScheme.secondary
    SupportStatus.RESOLVED -> MaterialTheme.colorScheme.primary
    SupportStatus.CLOSED -> MaterialTheme.colorScheme.outline
}

private fun supportStatusTone(status: SupportStatus): GanjStatusTone = when (status) {
    SupportStatus.OPEN -> GanjStatusTone.Positive
    SupportStatus.WAITING_USER -> GanjStatusTone.Warning
    SupportStatus.WAITING_SUPPORT -> GanjStatusTone.Premium
    SupportStatus.RESOLVED -> GanjStatusTone.Positive
    SupportStatus.CLOSED -> GanjStatusTone.Neutral
}

private fun supportStatusLabel(status: SupportStatus): String = when (status) {
    SupportStatus.OPEN -> "باز"
    SupportStatus.WAITING_USER -> "منتظر پاسخ شما"
    SupportStatus.WAITING_SUPPORT -> "منتظر پشتیبانی"
    SupportStatus.RESOLVED -> "حل‌شده"
    SupportStatus.CLOSED -> "بسته"
}

private fun supportCategoryLabel(category: SupportCategory): String = when (category) {
    SupportCategory.CONNECTION -> "اتصال"
    SupportCategory.BILLING -> "پرداخت"
    SupportCategory.ACCOUNT -> "حساب"
    SupportCategory.SECURITY -> "امنیت"
    SupportCategory.FEEDBACK -> "پیشنهاد"
    SupportCategory.OTHER -> "سایر"
}

private fun supportPriorityLabel(priority: SupportPriority): String = when (priority) {
    SupportPriority.URGENT -> "فوری"
    SupportPriority.HIGH -> "زیاد"
    SupportPriority.NORMAL -> "عادی"
}
