// Modified by Rafail Andreou, 2026-10-26, for FocusRelay. Original notices retained.
// SPDX-License-Identifier: GPL-2.0-only
package com.mindful.android.remote

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.mindful.android.helpers.device.ImpSystemAppsHelper
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object RemoteStore {
    private const val PREFS = "mindful_remote_v1"
    private const val KEY = "mindful_remote_credentials_v1"
    private var cachedApps: List<Map<String, String>>? = null
    private var appsCachedAt = 0L
    private val stateLock = Any()
    private var snapshotText: String? = null
    private var parsedSnapshot: RemoteSnapshot? = null
    private var cachedEvaluation: RemoteEvaluation? = null
    private var evaluatedAt = 0L
    private var evaluatedAllowed = emptySet<String>()
    fun invalidateEvaluation() = synchronized(stateLock) { cachedEvaluation = null }
    fun schedulerReady(context: Context) = prefs(context).getLong("scheduler_revision", -2) == revision(context) && prefs(context).getString("scheduler_mode", "error") != "error"
    fun invalidateScheduler(context: Context) { prefs(context).edit().putLong("scheduler_revision", -2).apply() }
    fun schedulerMode(context: Context) = prefs(context).getString("scheduler_mode", "none") ?: "none"
    fun schedulerError(context: Context, message: String) { prefs(context).edit().putString("scheduler_error", message.take(200)).putString("scheduler_mode", "error").putLong("scheduler_revision", -2).apply() }
    fun snapshotError(context: Context, message: String) { prefs(context).edit().putString("snapshot_error", message.take(200)).apply() }
    fun scheduleErrors(context: Context): List<String> = listOf("snapshot_error", "scheduler_error").mapNotNull { prefs(context).getString(it, null)?.takeIf { value -> value.isNotEmpty() } }
    fun enforcementErrors(context: Context): List<String> {
        val result = prefs(context).getString("overlay_result", "") ?: ""
        return if (result.startsWith("Blocking overlay failed:") && activeBlocks(context).isNotEmpty()) listOf(result.take(200)) else emptyList()
    }
    fun clockSkew(context: Context) = prefs(context).getLong("clock_skew", 0)
    fun schedulerPrepared(context: Context, mode: String, revision: Long) {
        check(prefs(context).edit().putLong("scheduler_revision", revision).putString("scheduler_mode", mode).remove("scheduler_error").commit()) { "Cannot persist scheduler readiness" }
    }
    private fun storedState(context: Context): JSONObject {
        prefs(context).getString("desired_state", null)?.let { return JSONObject(it) }
        // Migrate the existing personal preview in place; never clear its timed blocks.
        return JSONObject().put("revision", maxOf(0, revision(context))).put("serverTimeMs", System.currentTimeMillis())
            .put("blocks", JSONArray(prefs(context).getString("blocks", "[]")))
    }
    fun modeJson(context: Context): JSONObject = storedState(context).optJSONObject("deviceModes") ?: JSONObject().put("schemaVersion",1).put("templates",JSONArray()).put("instances",JSONArray()).put("exceptions",JSONArray())
    fun evaluation(context: Context, time: Long = System.currentTimeMillis()): RemoteEvaluation = synchronized(stateLock) {
        if (!enabled(context)) return@synchronized RemoteEvaluation(emptyMap(), emptyList(), null)
        val allowed = allowedPackages(context)
        val modeEligible = com.mindful.android.modes.DeviceModeStore.eligible(context)
        val modesEnabled = com.mindful.android.modes.DeviceModeStore.enabled(context)
        val modeStopped = com.mindful.android.modes.DeviceModeStore.stopped(context)
        val currentText = prefs(context).getString("desired_state", null) ?: storedState(context).toString()
        if (snapshotText != currentText || parsedSnapshot == null) {
            // Local opt-out takes precedence immediately. Stored rules may still reference
            // removed apps until the relay's next allowlist-normalized revision arrives.
            val source = JSONObject(currentText)
            val allPackages = mutableSetOf<String>()
            val blocks = source.getJSONArray("blocks")
            for (i in 0 until blocks.length()) allPackages.add(blocks.getJSONObject(i).getString("packageName"))
            for (key in listOf("schedules", "presets")) source.optJSONArray(key)?.let { array ->
                for (i in 0 until array.length()) array.getJSONObject(i).getJSONArray("packages").let { packages ->
                    for (j in 0 until packages.length()) allPackages.add(packages.getString(j))
                }
            }
            source.optJSONObject("deviceModes")?.let { modes ->
                for(key in listOf("templates","instances"))modes.optJSONArray(key)?.let { a ->
                    for(n in 0 until a.length())a.getJSONObject(n).optJSONArray("blockedPackages")?.let { pkgs -> for(j in 0 until pkgs.length())allPackages.add(pkgs.getString(j)) }
                }
                modes.optJSONArray("exceptions")?.let { a -> for(n in 0 until a.length())allPackages.add(a.getJSONObject(n).getString("packageName")) }
            }
            parsedSnapshot = RemoteSnapshotParser.parse(source, allPackages)
            snapshotText = currentText; cachedEvaluation = null
        }
        val cached = cachedEvaluation
        if (!sourceHasModes(currentText) && cached != null && allowed == evaluatedAllowed && time >= evaluatedAt && time - evaluatedAt < 60000 && (cached.nextTransitionMs == null || time < cached.nextTransitionMs)) return@synchronized cached
        RemoteRuleEngine.evaluate(parsedSnapshot!!, allowed, time, modeEligible, modesEnabled, modeStopped).also { cachedEvaluation = it; evaluatedAt = time; evaluatedAllowed = allowed }
    }
    private fun sourceHasModes(text: String) = text.contains("\"deviceModes\"")
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun enabled(context: Context) = prefs(context).getBoolean("enabled", false)
    fun setEnabled(context: Context, value: Boolean) { prefs(context).edit().putBoolean("enabled", value).commit() }
    fun baseUrl(context: Context) = prefs(context).getString("url", "") ?: ""
    fun revision(context: Context) = prefs(context).getLong("revision", -1)
    fun updateMessage(context: Context, message: String) { prefs(context).edit().putString("message", message).apply() }
    fun updateEnforcement(context: Context, message: String) { prefs(context).edit().putString("enforcement", message).apply() }
    fun updateOverlayResult(context: Context, message: String) { prefs(context).edit().putString("overlay_result", message).apply() }
    fun lastSync(context: Context) = prefs(context).getLong("last_sync", 0)
    fun synced(context: Context) { prefs(context).edit().putLong("last_sync", System.currentTimeMillis()).putString("message", "Connected").apply() }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(KEY, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun configure(context: Context, url: String, token: String) {
        val uri = URI(url.trim())
        require(uri.scheme == "https" && uri.host != null && uri.userInfo == null && uri.query == null && uri.fragment == null && (uri.path.isNullOrEmpty() || uri.path == "/")) { "Enter the HTTPS relay address without a path" }
        require(token.matches(Regex("[A-Za-z0-9_-]{32,256}"))) { "Device token must contain at least 32 letters, numbers, - or _" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val ciphertext = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        prefs(context).edit().putString("url", "https://${uri.rawAuthority}")
            .putString("token", Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putLong("revision", -1).commit()
    }
    fun token(context: Context): String {
        val p = prefs(context)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(p.getString("iv", ""), Base64.NO_WRAP)))
        }
        return String(cipher.doFinal(Base64.decode(p.getString("token", ""), Base64.NO_WRAP)), Charsets.UTF_8)
    }
    @Synchronized
    fun availableApps(context: Context, refresh: Boolean = false): List<Map<String, String>> {
        val elapsed = SystemClock.elapsedRealtime()
        cachedApps?.let { if (!refresh && elapsed - appsCachedAt < 5 * 60 * 1000L) return it }
        val pm = context.packageManager
        val protected = com.mindful.android.modes.ModeSafety.essential(context)
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(intent, 0).mapNotNull { info ->
            val app = info.activityInfo.applicationInfo
            // Preinstalled entertainment apps can be selected; essential controls cannot.
            if (app.packageName in protected) null
            else mapOf("packageName" to app.packageName, "label" to app.loadLabel(pm).toString().take(120))
        }.distinctBy { it["packageName"] }.sortedBy { it["label"]?.lowercase() }
        cachedApps = apps
        appsCachedAt = elapsed
        return apps
    }
    fun allowedPackages(context: Context): Set<String> = (prefs(context).getStringSet("allowed", emptySet())?.toSet() ?: emptySet()) - com.mindful.android.modes.ModeSafety.essential(context)
    @Synchronized
    fun setAllowed(context: Context, packages: Set<String>) {
        require(packages.isNotEmpty()) { "Select at least one app" }
        val available = availableApps(context).map { it["packageName"] }.toSet()
        require(packages.all { it in available }) { "An app cannot be remotely controlled" }
        check(prefs(context).edit().putStringSet("allowed", packages.toSet()).putLong("revision", -1).putLong("scheduler_revision", -2).commit()) { "Cannot save app access" }
        invalidateEvaluation()
    }
    fun sharedApps(context: Context): List<Map<String, String>> {
        val allowed = allowedPackages(context)
        return availableApps(context).filter { it["packageName"] in allowed }
    }
    fun activeBlocks(context: Context): Map<String, Long> = evaluation(context).activeBlocks
    @Synchronized
    fun applyState(context: Context, json: JSONObject): Boolean {
        if (!enabled(context)) return false
        require(json.toString().toByteArray(Charsets.UTF_8).size <= 131072) { "Desired snapshot too large" }
        val previous = prefs(context).getString("desired_state", null)?.let { JSONObject(it) }
        if(previous?.optInt("schemaVersion")==3)require(json.optInt("schemaVersion")==3) { "Refusing mode schema downgrade" }
        var changed = false
        val wasUnready = !schedulerReady(context) || scheduleErrors(context).isNotEmpty()
        RemoteSnapshotParser.validateAndPersist(json, allowedPackages(context), revision(context), previous?.has("schemaVersion") == true) { parsed ->
            val edit = prefs(context).edit().remove("snapshot_error")
                .putLong("clock_skew", System.currentTimeMillis() - json.getLong("serverTimeMs"))
            // Re-delivery keeps absolute timestamps; it never restarts a duration.
            if (parsed.revision != revision(context) || previous == null) {
                changed = true
                edit.putString("desired_state", json.toString()).putString("blocks", json.getJSONArray("blocks").toString())
                    .putLong("revision", parsed.revision).putLong("scheduler_revision", -2)
            }
            edit.commit()
        }
        if (changed) invalidateEvaluation()
        return changed || wasUnready
    }
    @Synchronized
    fun disconnect(context: Context) {
        prefs(context).edit().putBoolean("enabled", false).remove("token").remove("iv").remove("url")
            .putLong("revision", -1).putString("blocks", "[]").remove("desired_state").remove("snapshot_error").remove("scheduler_error").putLong("scheduler_revision", -2).putString("message", "Disconnected; local remote blocks cleared").commit()
        invalidateEvaluation()
    }
    private fun value(v: Any?): Any? = when(v) {
        null, JSONObject.NULL -> null
        is JSONObject -> v.keys().asSequence().associateWith { value(v.get(it)) }
        is JSONArray -> (0 until v.length()).map { value(v.get(it)) }
        else -> v
    }
    fun status(context: Context): Map<String, Any?> {
        val evaluation = evaluation(context)
        val state = storedState(context)
        val phases = evaluation.states.associateBy { it.id }
        val schedules = state.optJSONArray("schedules") ?: JSONArray()
        for(i in 0 until schedules.length()) {
            val rule = schedules.getJSONObject(i); val phase = phases[rule.getString("id")]
            rule.put("state", phase?.phase ?: "inactive")
            phase?.nextWindow?.let { rule.put("nextWindow", JSONObject().put("startAtMs", it.startAtMs).put("endAtMs", it.endAtMs)) }
        }
        return mapOf(
        "enabled" to enabled(context), "url" to baseUrl(context), "revision" to revision(context),
        "lastSync" to lastSync(context), "message" to (prefs(context).getString("message", "Not connected") ?: "Not connected"),
        "enforcement" to (prefs(context).getString("enforcement", "No blocking check yet") ?: "No blocking check yet"),
        "overlayResult" to (prefs(context).getString("overlay_result", "No remote overlay attempted yet") ?: "No remote overlay attempted yet"),
        "deviceModes" to value(modeJson(context)), "modeStatus" to value(com.mindful.android.modes.DeviceModeStore.report(context,evaluation)),
        "schedules" to value(schedules), "presets" to value(state.optJSONArray("presets") ?: JSONArray()),
        "nextTransitionMs" to evaluation.nextTransitionMs, "schedulerMode" to schedulerMode(context),
        "schedulerReady" to schedulerReady(context), "scheduleErrors" to scheduleErrors(context),
        "clockSkewMs" to clockSkew(context), "timeZone" to java.util.TimeZone.getDefault().id,
        "allowed" to allowedPackages(context).toList(), "blocks" to activeBlocks(context).map { mapOf("packageName" to it.key, "untilMs" to it.value) },
    )
    }
}

