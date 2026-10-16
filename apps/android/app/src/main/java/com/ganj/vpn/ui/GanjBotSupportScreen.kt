package com.ganj.vpn.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Customer support stays native and uses the established GANJ surface/typography system. */
@Composable
internal fun GanjBotSupportScreen(api: GanjBotStoreApi, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    var username by remember { mutableStateOf<String?>(null) }
    var departments by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var tickets by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var department by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<JSONObject?>(null) }
    var draft by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var feedback by remember { mutableStateOf<String?>(null) }

    suspend fun load() = coroutineScope {
        val contact = async { api.request("support").optJSONObject("data")?.optString("username") }
        val deps = async { api.request("support_departments").optJSONArray("data") }
        val history = async { api.request("support_tickets").optJSONArray("data") }
        username = contact.await()?.removePrefix("@")?.takeIf { it.matches(Regex("[A-Za-z0-9_]{5,32}")) }
        val depRows = deps.await()
        departments = (0 until (depRows?.length() ?: 0)).mapNotNull { depRows?.optJSONObject(it) }
        val ticketRows = history.await()
        tickets = (0 until (ticketRows?.length() ?: 0)).mapNotNull { ticketRows?.optJSONObject(it) }
        if (department == null) department = departments.firstOrNull()?.optString("id")
    }

    fun refresh() {
        if (busy) return
        busy = true; error = null
        scope.launch {
            try { load() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (exception: Exception) { error = ganjBotStoreError(exception) }
            finally { busy = false }
        }
    }
    LaunchedEffect(api) { refresh() }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = responsiveHorizontalPadding(), end = responsiveHorizontalPadding(),
            top = 18.dp, bottom = GanjFloatingNavContentClearance,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            AccountBackAction(onBack)
            GanjSectionHeader(
                "پشتیبانی گنج",
                "درخواست‌ها و پاسخ‌ها با همان حساب تلگرام در ربات و اپلیکیشن همگام می‌شوند.",
            )
        }
        if (username != null) item {
            GanjGlassButton(
                "ارتباط در تلگرام · @${username}",
                { uri.openUri("https://t.me/${username}") },
                Modifier.fillMaxWidth(), filled = false,
            )
        }
        if (busy) item {
            GanjGlassSurface(GanjGlassRole.Regular, modifier = Modifier.fillMaxWidth(), shapeRadius = 14.dp) {
                Text("در حال همگام‌سازی با پشتیبانی…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        error?.let { message -> item {
            GanjInlineStatusPanel(
                "ارتباط با پشتیبانی برقرار نشد", message,
                accent = MaterialTheme.colorScheme.error, tone = GanjStatusTone.Danger,
                actionText = "تلاش دوباره", onAction = ::refresh,
            )
        } }
        feedback?.let { message -> item {
            GanjGlassSurface(GanjGlassRole.Regular, accent = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth(), shapeRadius = 14.dp) {
                GanjStatusPill("ثبت موفق", GanjStatusTone.Positive)
                Text(message, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface)
            }
        } }

        selected?.let { ticket -> item {
            GanjGlassSurface(GanjGlassRole.Dense, accent = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.fillMaxWidth(), shapeRadius = 14.dp) {
                Text("تیکت ${ticket.optString("Tracking")}", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text(ticket.optString("text"), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                ticket.optString("result").takeIf { it.isNotBlank() && it != "null" }?.let {
                    GanjStatusPill("پاسخ پشتیبانی", GanjStatusTone.Positive)
                    Text(it, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface)
                }
                GanjGlassButton("ثبت درخواست جدید", { selected = null; draft = "" },
                    Modifier.fillMaxWidth(), enabled = !busy, filled = false)
            }
        } } ?: run {
            item {
                GanjGlassSurface(GanjGlassRole.Dense, accent = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(), shapeRadius = 14.dp) {
                    GanjSectionHeader("موضوع پیام", "بخشی که درخواست شما به آن مربوط است را انتخاب کنید.")
                    departments.forEach { dep ->
                        val id = dep.optString("id")
                        GanjChoicePill(dep.optString("name_departman"), department == id,
                            { department = id }, Modifier.fillMaxWidth(), enabled = !busy)
                    }
                    if (departments.isEmpty() && !busy)
                        Text("موضوع‌های پشتیبانی دریافت نشدند؛ دوباره تلاش کنید.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            GanjGlassSurface(GanjGlassRole.Regular, accent = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth(), shapeRadius = 14.dp) {
                Text(if (selected == null) "متن درخواست" else "پاسخ شما",
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                GanjLiquidTextField(draft, { draft = it.take(1500) }, "متن پیام",
                    Modifier.fillMaxWidth(), enabled = !busy, singleLine = false, minLines = 4)
                GanjGlassButton("ارسال به پشتیبانی", {
                    if (!busy) {
                        busy = true; error = null; feedback = null
                        scope.launch {
                            try {
                                val base = selected
                                val result = if (base == null) api.request(
                                    "support_ticket_create",
                                    mapOf("department" to department.orEmpty(), "text" to draft),
                                ) else api.request(
                                    "support_ticket_reply",
                                    mapOf("tracking" to base.optString("Tracking"), "text" to draft),
                                )
                                feedback = "پیام ثبت شد · کد پیگیری: " +
                                    result.getJSONObject("data").getString("tracking")
                                draft = ""; selected = null; load()
                            } catch (cancelled: CancellationException) { throw cancelled }
                            catch (exception: Exception) { error = ganjBotStoreError(exception) }
                            finally { busy = false }
                        }
                    }
                }, Modifier.fillMaxWidth(),
                    enabled = !busy && draft.trim().length >= 2 &&
                        (selected != null || department != null))
            }
        }
        item {
            GanjSectionHeader("پیام‌های قبلی", "وضعیت درخواست‌ها و پاسخ‌های پشتیبانی")
            GanjGlassButton("به‌روزرسانی پیام‌ها", ::refresh, Modifier.fillMaxWidth(),
                enabled = !busy, filled = false)
        }
        if (tickets.isEmpty() && !busy) item {
            GanjGlassSurface(GanjGlassRole.Regular, modifier = Modifier.fillMaxWidth(), shapeRadius = 14.dp) {
                Text("هنوز پیامی ثبت نشده است.", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(tickets.size) { index ->
            val ticket = tickets[index]
            GanjReferenceMenuRow(
                title = ticket.optString("name_departman").ifBlank { "درخواست پشتیبانی" },
                subtitle = ticket.optString("text").take(110),
                accent = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth(),
                onClick = { if (!busy) { selected = ticket; draft = "" } },
            )
        }
    }
}
