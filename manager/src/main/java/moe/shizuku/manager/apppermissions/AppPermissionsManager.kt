package moe.shizuku.manager.apppermissions

import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku

/**
 * Manages fine-grained AppOps permissions for installed apps via Shizuku shell access.
 * Uses `cmd appops get/set` under the hood, which requires ADB/root-level privilege.
 */
object AppPermissionsManager {

    /**
     * Well-known AppOps operations that can be toggled by this manager.
     * Each entry maps a human-readable label to the `cmd appops` operation name.
     */
    val MANAGED_OPS: List<AppOp> = listOf(
        AppOp("READ_CLIPBOARD",         "Read Clipboard",         "Read content from the clipboard"),
        AppOp("WRITE_CLIPBOARD",        "Write Clipboard",        "Write content to the clipboard"),
        AppOp("CAMERA",                 "Camera",                 "Access the device camera"),
        AppOp("RECORD_AUDIO",           "Microphone",             "Record audio / microphone access"),
        AppOp("FINE_LOCATION",          "Precise Location",       "Access precise GPS location"),
        AppOp("COARSE_LOCATION",        "Approximate Location",   "Access approximate (network) location"),
        AppOp("READ_CONTACTS",          "Read Contacts",          "Read contacts from the address book"),
        AppOp("READ_CALL_LOG",          "Read Call Log",          "Read incoming/outgoing call history"),
        AppOp("READ_PHONE_STATE",       "Read Phone State",       "Access phone state and identity"),
        AppOp("POST_NOTIFICATION",      "Post Notifications",     "Show system notifications"),
        AppOp("SYSTEM_ALERT_WINDOW",    "Draw Over Other Apps",   "Display overlays on top of other apps"),
        AppOp("GET_USAGE_STATS",        "Usage Stats",            "Access app usage statistics"),
        AppOp("RUN_IN_BACKGROUND",      "Run in Background",      "Continue running when not in foreground"),
        AppOp("READ_EXTERNAL_STORAGE",  "Read Storage",           "Read files from shared/external storage"),
        AppOp("WRITE_EXTERNAL_STORAGE", "Write Storage",          "Write files to shared/external storage"),
    )

    data class AppOp(
        val opName: String,
        val label: String,
        val description: String,
    )

    enum class OpMode(val adbValue: String, val displayName: String) {
        ALLOW("allow", "Allowed"),
        IGNORE("ignore", "Blocked"),
        DENY("deny", "Denied"),
        DEFAULT("default", "Default"),
        FOREGROUND("foreground", "Foreground only");

        companion object {
            fun fromOutput(raw: String): OpMode {
                return when {
                    raw.contains("allow", ignoreCase = true) && !raw.contains("foreground", ignoreCase = true) -> ALLOW
                    raw.contains("foreground", ignoreCase = true) -> FOREGROUND
                    raw.contains("ignore", ignoreCase = true) -> IGNORE
                    raw.contains("deny", ignoreCase = true) -> DENY
                    else -> DEFAULT
                }
            }
        }
    }

    /**
     * Returns the current AppOps mode for the given package and op name.
     * Runs `cmd appops get <packageName> <opName>` via Shizuku.
     */
    fun getMode(packageName: String, opName: String): OpMode {
        return try {
            val output = runShell("cmd", "appops", "get", packageName, opName)
            OpMode.fromOutput(output)
        } catch (e: Exception) {
            OpMode.DEFAULT
        }
    }

    /**
     * Sets the AppOps mode for the given package and op name.
     * Runs `cmd appops set <packageName> <opName> <mode>` via Shizuku.
     */
    fun setMode(packageName: String, opName: String, mode: OpMode) {
        runShell("cmd", "appops", "set", packageName, opName, mode.adbValue)
    }

    /**
     * Resets all AppOps for the given package to defaults.
     */
    fun resetAll(packageName: String) {
        runShell("cmd", "appops", "reset", packageName)
    }

    private fun runShell(vararg command: String): String {
        val service = IShizukuService.Stub.asInterface(Shizuku.getBinder())
        val process = service.newProcess(command, null, null)
        val output = process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor()
        return output
    }
}
