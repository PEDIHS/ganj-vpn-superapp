<?php
declare(strict_types=1);

if (PHP_SAPI !== 'cli') {
    http_response_code(404);
    exit;
}
if (PHP_VERSION_ID < 80100) {
    fwrite(STDERR, "PHP 8.1 or newer is required.\n");
    exit(1);
}
umask(0077);

function installerFail(string $message): never
{
    fwrite(STDERR, "ERROR: " . $message . "\n");
    exit(1);
}

function installerEnv(string $name): string
{
    $value = trim((string)getenv($name));
    if ($value === '') installerFail($name . ' is required.');
    return $value;
}

function installerInsideRoot(string $candidate, string $root): bool
{
    $rootPrefix = rtrim($root, DIRECTORY_SEPARATOR) . DIRECTORY_SEPARATOR;
    return $candidate === $root || str_starts_with($candidate . DIRECTORY_SEPARATOR, $rootPrefix);
}

function installerPrivateSecretFile(string $path, string $root): string
{
    $real = realpath($path);
    if ($real === false || !is_file($real) || is_link($path)) installerFail('Secret file is unavailable: ' . $path);
    if (installerInsideRoot($real, $root)) installerFail('Secret files must be outside the public bot root.');
    $mode = @fileperms($real);
    if (!is_int($mode) || (($mode & 0077) !== 0)) installerFail('Secret file permissions must be 0600 or stricter.');
    $value = trim((string)file_get_contents($real));
    if (strlen($value) < 32 || strlen($value) > 4096) installerFail('Secret file must contain 32 to 4096 characters.');
    $value = str_repeat("\0", strlen($value));
    return $real;
}

function installerPrivateDataFile(string $path, string $root): string
{
    $real = realpath($path);
    if ($real === false || !is_file($real) || is_link($path)) installerFail('Private data file is unavailable: ' . $path);
    if (installerInsideRoot($real, $root)) installerFail('Private data files must be outside the public bot root.');
    $mode = @fileperms($real);
    if (!is_int($mode) || (($mode & 0022) !== 0)) installerFail('Private data file must not be group/world writable.');
    return $real;
}

function installerPrivateDirectory(string $path, string $root): string
{
    $real = realpath($path);
    if ($real === false || !is_dir($real) || is_link($path)) installerFail('Backup directory must already exist.');
    if (installerInsideRoot($real, $root)) installerFail('Backup directory must be outside the public bot root.');
    $mode = @fileperms($real);
    if (!is_int($mode) || (($mode & 0077) !== 0)) installerFail('Backup directory permissions must be 0700 or stricter.');
    if (!is_writable($real)) installerFail('Backup directory is not writable.');
    return rtrim($real, DIRECTORY_SEPARATOR);
}

$options = getopt('', ['bot-root:']);
$source = __DIR__;
$rootInput = isset($options['bot-root']) ? (string)$options['bot-root'] : dirname(__DIR__);
$resolvedRoot = realpath($rootInput);
if ($resolvedRoot === false || !is_dir($resolvedRoot)) installerFail('Bot root does not exist.');
$root = rtrim($resolvedRoot, DIRECTORY_SEPARATOR);
$index = $root . '/index.php';
$botConfig = $root . '/config.php';
if (!is_file($index) || !is_file($botConfig)) installerFail('index.php and config.php were not found in the bot root.');
if (!is_writable($index)) installerFail('Bot index.php is not writable.');

$controlApi = rtrim(installerEnv('GANJ_CONTROL_API_URL'), '/');
$url = parse_url($controlApi);
if (!is_array($url) || ($url['scheme'] ?? '') !== 'https' || empty($url['host'])
    || isset($url['user']) || isset($url['pass']) || isset($url['query']) || isset($url['fragment'])
    || !empty($url['path'])) {
    installerFail('GANJ_CONTROL_API_URL must be a credential-free HTTPS origin.');
}
$approvalTokenFile = installerPrivateSecretFile(installerEnv('GANJ_BOT_APPROVAL_TOKEN_FILE'), $root);
$projectionTokenFile = installerPrivateSecretFile(installerEnv('GANJ_BOT_PROJECTION_TOKEN_FILE'), $root);
$connectorMapFile = installerPrivateDataFile(installerEnv('GANJ_PASARGUARD_CONNECTOR_MAP_FILE'), $root);
$backupDir = installerPrivateDirectory(installerEnv('GANJ_BOT_BRIDGE_BACKUP_DIR'), $root);
$connectorMap = json_decode((string)file_get_contents($connectorMapFile), true);
if (!is_array($connectorMap)) installerFail('PasarGuard connector map must be a JSON object.');
foreach ($connectorMap as $panel => $connector) {
    if (!is_string($panel) || $panel === '' || !is_string($connector)
        || !preg_match('/^[A-Za-z0-9._:-]{1,128}$/D', $connector)) {
        installerFail('PasarGuard connector map contains an invalid entry.');
    }
}

$original = (string)file_get_contents($index);
$needle = '$ganjStartCommand=trim((string)$text);';
$managed = "/* GANJ_APP_BRIDGE_V1 */";
if (!str_contains($original, $managed) && substr_count($original, $needle) !== 1) {
    installerFail('Supported Ganj 0.1.5.4 login hook marker was not found exactly once.');
}
foreach (['bootstrap.php', 'projection.php'] as $payload) {
    if (!is_file($source . '/' . $payload)) installerFail('Installer payload is missing: ' . $payload);
}

require_once $botConfig;
if (!isset($pdo) || !$pdo instanceof PDO) installerFail('The existing bot config did not expose a PDO connection.');
$pdo->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);

try {
    $pdo->exec(
        "CREATE TABLE IF NOT EXISTS ganj_app_plan_mapping (
            source_product_name VARCHAR(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci PRIMARY KEY,
            plan_code VARCHAR(128) NOT NULL,
            allowed_protocols LONGTEXT NOT NULL,
            device_limit INT NOT NULL DEFAULT 1,
            updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
    );
    $pdo->exec(
        "INSERT IGNORE INTO ganj_app_plan_mapping(source_product_name,plan_code,allowed_protocols,device_limit)
         SELECT name_product, MIN(code_product), '[\"vless\",\"vmess\",\"trojan\",\"shadowsocks\"]', 1
           FROM product
          WHERE TRIM(COALESCE(name_product,'')) <> '' AND TRIM(COALESCE(code_product,'')) <> ''
          GROUP BY name_product HAVING COUNT(DISTINCT code_product) = 1"
    );
    $pdo->exec(
        "CREATE TABLE IF NOT EXISTS ganj_app_ownership_journal (
            seq BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
            operation VARCHAR(16) NOT NULL,
            id_invoice VARCHAR(200) NOT NULL,
            id_user VARCHAR(200) NOT NULL,
            service_username VARCHAR(300) NULL,
            service_location VARCHAR(300) NULL,
            name_product VARCHAR(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
            status VARCHAR(200) NULL,
            time_sell VARCHAR(200) NULL,
            service_time VARCHAR(200) NULL,
            volume VARCHAR(200) NULL,
            created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
            KEY idx_ganj_app_journal_invoice (id_invoice, seq)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
    );

    foreach (['ganj_app_invoice_ai', 'ganj_app_invoice_au', 'ganj_app_invoice_ad'] as $trigger) {
        $pdo->exec('DROP TRIGGER IF EXISTS ' . $trigger);
    }
    $columns = '(operation,id_invoice,id_user,service_username,service_location,name_product,status,time_sell,service_time,volume)';
    $pdo->exec(
        "CREATE TRIGGER ganj_app_invoice_ai AFTER INSERT ON invoice FOR EACH ROW
         INSERT INTO ganj_app_ownership_journal " . $columns . "
         SELECT 'insert',NEW.id_invoice,NEW.id_user,NEW.username,NEW.Service_location,NEW.name_product,
                NEW.Status,NEW.time_sell,NEW.Service_time,NEW.Volume
          WHERE COALESCE(NEW.name_product,'') <> 'سرویس تست'
            AND LOWER(COALESCE(NEW.Status,'')) <> 'unpaid'"
    );
    $pdo->exec(
        "CREATE TRIGGER ganj_app_invoice_au AFTER UPDATE ON invoice FOR EACH ROW
         INSERT INTO ganj_app_ownership_journal " . $columns . "
         SELECT 'update',NEW.id_invoice,NEW.id_user,NEW.username,NEW.Service_location,NEW.name_product,
                NEW.Status,NEW.time_sell,NEW.Service_time,NEW.Volume
          WHERE COALESCE(NEW.name_product,'') <> 'سرویس تست'"
    );
    $pdo->exec(
        "CREATE TRIGGER ganj_app_invoice_ad AFTER DELETE ON invoice FOR EACH ROW
         INSERT INTO ganj_app_ownership_journal " . $columns . "
         SELECT 'delete',OLD.id_invoice,OLD.id_user,OLD.username,OLD.Service_location,OLD.name_product,
                'revoked',OLD.time_sell,OLD.Service_time,OLD.Volume
          WHERE COALESCE(OLD.name_product,'') <> 'سرویس تست'"
    );
    $count = (int)$pdo->query('SELECT COUNT(*) FROM ganj_app_ownership_journal')->fetchColumn();
    if ($count === 0) {
        $pdo->exec(
            "INSERT INTO ganj_app_ownership_journal " . $columns . "
             SELECT 'snapshot',id_invoice,id_user,username,Service_location,name_product,Status,time_sell,Service_time,Volume
               FROM invoice
              WHERE COALESCE(name_product,'') <> 'سرویس تست'
                AND LOWER(COALESCE(Status,'')) <> 'unpaid'"
        );
    }
} catch (Throwable $error) {
    installerFail('Database migration failed. TRIGGER privilege and current Ganj schema are required.');
}

$target = $root . '/.ganj-app-bridge';
if (!is_dir($target) && !mkdir($target, 0700, true) && !is_dir($target)) installerFail('Cannot create bridge directory.');
@chmod($target, 0700);
foreach (['bootstrap.php', 'projection.php'] as $payload) {
    $destination = $target . '/' . $payload;
    $temporary = $destination . '.tmp';
    if (file_put_contents($temporary, (string)file_get_contents($source . '/' . $payload), LOCK_EX) === false) {
        installerFail('Cannot write bridge payload.');
    }
    @chmod($temporary, 0640);
    if (!rename($temporary, $destination)) installerFail('Cannot publish bridge payload.');
}

$config = [
    'control_api_url' => $controlApi,
    'approval_token_file' => $approvalTokenFile,
    'projection_token_file' => $projectionTokenFile,
    'pasarguard_connector_map_file' => $connectorMapFile,
];
$configBody = "<?php\ndeclare(strict_types=1);\nif (realpath((string)(\$_SERVER['SCRIPT_FILENAME'] ?? '')) === __FILE__) { http_response_code(404); exit; }\nreturn "
    . var_export($config, true) . ";\n";
if (file_put_contents($target . '/config.php.tmp', $configBody, LOCK_EX) === false) installerFail('Cannot write bridge config.');
@chmod($target . '/config.php.tmp', 0640);
if (!rename($target . '/config.php.tmp', $target . '/config.php')) installerFail('Cannot publish bridge config.');

$access = "Options -Indexes\n<FilesMatch \"^(?!projection\\.php$).*$\">\n  Require all denied\n</FilesMatch>\n";
if (file_put_contents($target . '/.htaccess', $access, LOCK_EX) === false) installerFail('Cannot write bridge access policy.');
@chmod($target . '/.htaccess', 0640);

if (!str_contains($original, $managed)) {
    $hook = "/* GANJ_APP_BRIDGE_V1 */\n"
        . "require_once __DIR__ . '/.ganj-app-bridge/bootstrap.php';\n"
        . "if (ganjAppBridgeHandle()) return;\n\n"
        . $needle;
    $patched = str_replace($needle, $hook, $original, $replacements);
    if ($replacements !== 1) installerFail('Bot hook patch was not deterministic.');
    try {
        $backupSuffix = bin2hex(random_bytes(6));
    } catch (Throwable $error) {
        installerFail('Cannot create a unique backup name.');
    }
    $backup = $backupDir . '/index.php.' . gmdate('Ymd-His') . '.' . $backupSuffix . '.bak';
    if (file_put_contents($backup, $original, LOCK_EX) === false) installerFail('Cannot create bot index backup.');
    @chmod($backup, 0600);
    $temporaryIndex = $index . '.ganj-app.tmp';
    if (file_put_contents($temporaryIndex, $patched, LOCK_EX) === false) installerFail('Cannot write patched bot index.');
    @chmod($temporaryIndex, fileperms($index) & 0777);
    if (!rename($temporaryIndex, $index)) installerFail('Cannot publish patched bot index.');
}

fwrite(STDOUT, "Ganj App bridge installed successfully.\n");
fwrite(STDOUT, "Projection URL path: /.ganj-app-bridge/projection.php\n");
fwrite(STDOUT, "Private backup directory: " . $backupDir . "\n");
fwrite(STDOUT, "Remove the uploaded installer directory after verification; the managed bridge directory must remain.\n");
