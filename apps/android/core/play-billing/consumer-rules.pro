# The Play Billing public SDK already ships its own rules. Keep only the entry points that may be
# created by application dependency injection/reflection; no purchase payload model is retained.
-keep public class com.ganj.vpn.core.playbilling.GooglePlayBillingAdapter$Factory { public *; }
-keep public class com.ganj.vpn.core.playbilling.AndroidKeystorePurchaseProofVault { public *; }

# Preserve generic signatures used by Kotlin callers while allowing implementation shrinking.
-keepattributes Signature,InnerClasses,EnclosingMethod
