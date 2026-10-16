package com.ganj.vpn.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

/** Uses the existing bot support records and Telegram admin reply handler. */
@Composable
internal fun GanjBotSupportScreen(api: GanjBotStoreApi,onBack: ()->Unit) {
    val scope=rememberCoroutineScope()
    val uri=LocalUriHandler.current
    var username by remember {mutableStateOf<String?>(null)}
    var departments by remember {mutableStateOf<List<JSONObject>>(emptyList())}
    var tickets by remember {mutableStateOf<List<JSONObject>>(emptyList())}
    var department by remember {mutableStateOf<String?>(null)}
    var selected by remember {mutableStateOf<JSONObject?>(null)}
    var draft by remember {mutableStateOf("")}
    var busy by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf<String?>(null)}
    var feedback by remember {mutableStateOf<String?>(null)}
    suspend fun load() = coroutineScope {
        val contact=async {api.request("support").optJSONObject("data")?.optString("username")}
        val deps=async {api.request("support_departments").optJSONArray("data")}
        val history=async {api.request("support_tickets").optJSONArray("data")}
        username=contact.await()?.removePrefix("@")?.takeIf {it.matches(Regex("[A-Za-z0-9_]{5,32}"))}
        val depRows=deps.await();departments=(0 until (depRows?.length() ?: 0)).mapNotNull {depRows?.optJSONObject(it)}
        val ticketRows=history.await();tickets=(0 until (ticketRows?.length() ?: 0)).mapNotNull {ticketRows?.optJSONObject(it)}
        if(department==null)department=departments.firstOrNull()?.optString("id")
    }
    fun refresh() {if(busy)return;busy=true;error=null;scope.launch {try {load()}catch(c:CancellationException){throw c}catch(e:Exception){error=ganjBotStoreError(e)}finally{busy=false}}}
    LaunchedEffect(api) {refresh()}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=responsiveHorizontalPadding(),vertical=18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        AccountBackAction(onBack)
        Text("پشتیبانی گنج",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
        Text("پیام شما در همان بخش پشتیبانی ربات ثبت می‌شود؛ پاسخ در حساب خودتان قابل پیگیری است.",color=MaterialTheme.colorScheme.onSurfaceVariant)
        username?.let {id -> GanjGlassButton("تلگرام پشتیبانی · @$id",{uri.openUri("https://t.me/$id")},Modifier.fillMaxWidth(),filled=false)}
        if(busy)Text("در حال دریافت…")
        error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
        feedback?.let {Text(it,color=MaterialTheme.colorScheme.primary)}
        selected?.let {ticket ->
            Text("تیکت ${ticket.optString("Tracking")}",style=MaterialTheme.typography.titleMedium)
            Text(ticket.optString("text"))
            ticket.optString("result").takeIf {it.isNotBlank()&&it!="null"}?.let {Text("پاسخ پشتیبانی: $it")}
            GanjGlassButton("پیام جدید",{selected=null;draft=""},enabled=!busy,filled=false)
        } ?: run {
            Text("موضوع پیام",style=MaterialTheme.typography.titleMedium)
            departments.forEach {dep -> val id=dep.optString("id");GanjChoicePill(dep.optString("name_departman"),department==id,{department=id},Modifier.fillMaxWidth(),enabled=!busy)}
        }
        GanjLiquidTextField(draft,{draft=it.take(1500)},"متن پیام",Modifier.fillMaxWidth(),enabled=!busy,singleLine=false,minLines=4)
        GanjGlassButton("ارسال به پشتیبانی",{
            if(!busy) {busy=true;error=null;feedback=null;scope.launch {try {
                val base=selected
                val result=if(base==null)api.request("support_ticket_create",mapOf("department" to department.orEmpty(),"text" to draft))
                    else api.request("support_ticket_reply",mapOf("tracking" to base.optString("Tracking"),"text" to draft))
                feedback="پیام ثبت شد · کد پیگیری: "+result.getJSONObject("data").getString("tracking")
                draft="";selected=null;load()
            }catch(c:CancellationException){throw c}catch(e:Exception){error=ganjBotStoreError(e)}finally{busy=false}}}
        },Modifier.fillMaxWidth(),enabled=!busy&&draft.trim().length>=2&&(selected!=null||department!=null))
        GanjGlassButton("به‌روزرسانی پیام‌ها",::refresh,Modifier.fillMaxWidth(),enabled=!busy,filled=false)
        Text("پیام‌های قبلی",style=MaterialTheme.typography.titleMedium)
        if(tickets.isEmpty()&&!busy)Text("هنوز پیامی ثبت نشده است.")
        tickets.forEach {ticket ->
            GanjGlassButton(ticket.optString("name_departman")+" · "+ticket.optString("Tracking"),{selected=ticket;draft=""},Modifier.fillMaxWidth(),enabled=!busy,filled=false)
            Text(ticket.optString("text").take(160),style=MaterialTheme.typography.bodySmall)
        }
    }
}
