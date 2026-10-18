# GANJ bot → Control API → Android push integration

**Staged code only. Not installed on production and no bot payments modified.**

1. Provision the Firebase Android app identifiers to Gradle and a server-side
   service account to the Control API using private 0600 secret files.
2. Apply Control API migrations 024-026 with backup, compatibility review, and rollback plan.
3. Install the optional MariaDB outbox schema into the actual GANJ Bot database.
4. Add ganjAppEventQueue() to existing *verified* payment state transitions
   and subscription notification status transitions **inside the same InnoDB
   transaction**. Never fire confirmation from a user-uploaded receipt alone.
   Use the correct Telegram subject and stable bot invoice/cycle IDs.
5. Call ganjAppEventFlush() once from the existing NoticationsService/cron
   dispatcher, limiting the batch to 20. No second cron and no scan of all users.
6. Test with separate Telegram accounts: payment approved/rejected, usage 80%,
   48h and expired, duplicate events, offline for 2 days, reconnect/replay,
   expired tokens, Firebase token rotation, DND and Android 13 permission denial.
7. Launch behind a disabled-by-default feature switch, starting with test
   accounts. Verify no cross-account notification leakage.

**Important:** Existing bot warnings are sent by NoticationsService.php and
other payment handlers. This adapter does not auto-hook them: the actual
mutation call sites must be audited before any live integration. The server
replay journal remains the source of recovery for missed FCM messages. The
Android OS—not this app—controls whether a HIGH channel becomes a heads-up
pop-up, especially under Do Not Disturb.
