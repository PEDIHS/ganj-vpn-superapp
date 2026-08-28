<?php
declare(strict_types=1);

/**
 * Ganj App Bot Approval bridge.
 *
 * This file is installed beside the existing bot and deliberately contains no bot token,
 * database credential, VPN configuration or Control API bearer.
 */

function ganjAppBridgeConfig(): array
{
    static $config;
    if (is_array($config)) return $config;
    $path = __DIR__ . '/config.php';
    if (!is_file($path)) throw new RuntimeException('Ganj App bridge is not configured.');
    $loaded = require $path;
    if (!is_array($loaded)) throw new RuntimeException('Ganj App bridge configuration is invalid.');
    return $config = $loaded;
}

function ganjAppBridgeTruncate(string $value, int $limit): string
{
    if ($limit < 1 || preg_match('//u', $value) !== 1) return '';
    if (function_exists('mb_substr')) return mb_substr($value, 0, $limit, 'UTF-8');
    if (preg_match('/^.{0,' . $limit . '}/us', $value, $match) !== 1) return '';
    return $match[0];
}

function ganjAppBridgeSecret(string $path): string
{
    $real = realpath($path);
    if ($real === false || !is_file($real) || is_link($real)) {
        throw new RuntimeException('Ganj App bridge secret file is unavailable.');
    }
    $mode = @fileperms($real);
    if (is_int($mode) && (($mode & 0077) !== 0)) {
        throw new RuntimeException('Ganj App bridge secret file permissions are too broad.');
    }
    $value = trim((string)file_get_contents($real));
    if (strlen($value) < 32 || strlen($value) > 4096) {
        throw new RuntimeException('Ganj App bridge secret is invalid.');
    }
    return $value;
}

function ganjAppBridgeRequest(string $action, string $requestToken): bool
{
    $config = ganjAppBridgeConfig();
    $base = rtrim((string)($config['control_api_url'] ?? ''), '/');
    $parts = parse_url($base);
    if (!is_array($parts) || ($parts['scheme'] ?? '') !== 'https' || empty($parts['host'])
        || isset($parts['user']) || isset($parts['pass']) || isset($parts['query']) || isset($parts['fragment'])
        || !empty($parts['path'])) {
        throw new RuntimeException('Control API URL is invalid.');
    }
    if (!extension_loaded('curl')) throw new RuntimeException('PHP cURL extension is required.');

    $fromId = trim((string)($GLOBALS['from_id'] ?? ''));
    if (!preg_match('/^[1-9][0-9]{0,19}$/D', $fromId)) {
        throw new RuntimeException('Telegram identity is invalid.');
    }
    $username = trim((string)($GLOBALS['username'] ?? ''));
    if (!preg_match('/^[A-Za-z0-9_]{5,32}$/D', $username)) $username = null;
    $displayName = trim(strip_tags((string)($GLOBALS['first_name'] ?? '')));
    if ($displayName === '') $displayName = null;
    if ($displayName !== null) $displayName = ganjAppBridgeTruncate($displayName, 160) ?: null;

    $payload = [
        'request_token' => $requestToken,
        'action' => $action,
        'telegram_user_id' => $fromId,
        'username' => $username,
        'display_name' => $displayName,
    ];
    $encoded = json_encode($payload, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_THROW_ON_ERROR);
    $token = ganjAppBridgeSecret((string)($config['approval_token_file'] ?? ''));

    $handle = curl_init($base . '/v1/internal/telegram/bot/approval');
    if ($handle === false) return false;
    curl_setopt_array($handle, [
        CURLOPT_POST => true,
        CURLOPT_POSTFIELDS => $encoded,
        CURLOPT_HTTPHEADER => [
            'Accept: application/json',
            'Content-Type: application/json',
            'Authorization: Bearer ' . $token,
        ],
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_FOLLOWLOCATION => false,
        CURLOPT_CONNECTTIMEOUT => 4,
        CURLOPT_TIMEOUT => 8,
        CURLOPT_SSL_VERIFYPEER => true,
        CURLOPT_SSL_VERIFYHOST => 2,
        CURLOPT_PROTOCOLS => CURLPROTO_HTTPS,
        CURLOPT_REDIR_PROTOCOLS => CURLPROTO_HTTPS,
        CURLOPT_USERAGENT => 'ganj-bot-app-bridge/1.0',
    ]);
    try {
        $response = curl_exec($handle);
        $status = (int)curl_getinfo($handle, CURLINFO_RESPONSE_CODE);
        if ($response === false || $status < 200 || $status >= 300) return false;
        $body = json_decode((string)$response, true, 32, JSON_THROW_ON_ERROR);
        return is_array($body) && empty($body['error']);
    } catch (Throwable $error) {
        return false;
    } finally {
        curl_close($handle);
        if (isset($token)) $token = str_repeat("\0", strlen($token));
    }
}

function ganjAppBridgeHandle(): bool
{
    if (($GLOBALS['Chat_type'] ?? 'private') !== 'private') return false;
    $text = trim((string)($GLOBALS['text'] ?? ''));
    $callback = (string)($GLOBALS['datain'] ?? '');

    if (preg_match('/^\/start\s+APP-([A-Za-z0-9_-]{43})$/D', $text, $match)) {
        $token = $match[1];
        $keyboard = json_encode([
            'inline_keyboard' => [
                [['text' => '✅ تأیید ورود به اپ گنج', 'callback_data' => 'ga_a_' . $token]],
                [['text' => '❌ لغو درخواست', 'callback_data' => 'ga_c_' . $token]],
            ],
        ], JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
        sendmessage(
            $GLOBALS['from_id'],
            "📱 <b>ورود به اپلیکیشن Ganj VPN</b>\n\nاین درخواست، حساب فعلی تلگرام شما را به همان کاربر داخل اپ متصل می‌کند. موجودی، خریدها و سرویس‌ها مشترک می‌مانند.\n\n<b>اگر خودتان این درخواست را شروع نکرده‌اید، لغو را بزنید.</b>",
            $keyboard,
            'HTML'
        );
        return true;
    }

    if (!preg_match('/^ga_([ac])_([A-Za-z0-9_-]{43})$/D', $callback, $match)) return false;
    $action = $match[1] === 'a' ? 'approve' : 'cancel';
    try {
        $ok = ganjAppBridgeRequest($action, $match[2]);
    } catch (Throwable $error) {
        $ok = false;
    }
    if (!empty($GLOBALS['callback_query_id'])) {
        telegram('answerCallbackQuery', [
            'callback_query_id' => $GLOBALS['callback_query_id'],
            'text' => $ok
                ? ($action === 'approve' ? 'ورود تأیید شد.' : 'درخواست لغو شد.')
                : 'درخواست معتبر نیست یا سرویس موقتاً در دسترس نیست.',
            'show_alert' => !$ok,
        ]);
    }
    $message = $ok
        ? ($action === 'approve'
            ? "✅ <b>ورود به اپ تأیید شد</b>\n\nبه اپ برگردید؛ حساب و سرویس‌های شما به‌صورت خودکار همگام می‌شوند."
            : "درخواست ورود به اپ لغو شد.")
        : "❌ <b>تأیید ورود انجام نشد</b>\n\nممکن است درخواست منقضی شده باشد. از داخل اپ دوباره تلاش کنید.";
    Editmessagetext(
        $GLOBALS['from_id'],
        $GLOBALS['message_id'],
        $message,
        json_encode(['inline_keyboard' => [[['text' => '🏠 بازگشت به خانه', 'callback_data' => 'start']]]], JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES),
        'HTML'
    );
    return true;
}
