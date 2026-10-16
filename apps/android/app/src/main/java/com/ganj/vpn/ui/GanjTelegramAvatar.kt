package com.ganj.vpn.ui

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.ganj.vpn.core.controlapi.CurrentAccount
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Shows only the authenticated Telegram account's photo returned by the Ganj Bot.
 * No Telegram CDN token, file path, user ID or guessed username is sent in the URL.
 * A changed/removed account resets the image immediately; missing photos use the brand icon.
 */
@Composable
internal fun rememberGanjTelegramAvatar(
    account: CurrentAccount?,
    botApi: GanjBotStoreApi?,
): ImageBitmap? {
    val accountId = account?.takeIf { it.telegramLinked }?.id
    var avatar by remember(accountId, botApi) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(accountId, botApi) {
        if (accountId == null || botApi == null) return@LaunchedEffect
        val loaded = try {
            withContext(Dispatchers.IO) {
                val data = botApi.request("profile_avatar").optJSONObject("data")
                if (data?.optString("mime") != "image/jpeg") return@withContext null
                val raw = data.optString("image_base64")
                if (raw.length !in 500..400000) return@withContext null
                val bytes = Base64.decode(raw, Base64.NO_WRAP)
                if (bytes.size !in 500..250000) return@withContext null
                val bounds = BitmapFactory.Options().also { it.inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth !in 1..512 || bounds.outHeight !in 1..512) return@withContext null
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null // Offline or no accessible Telegram profile photo: show the existing icon.
        }
        avatar = loaded
    }
    return avatar
}
