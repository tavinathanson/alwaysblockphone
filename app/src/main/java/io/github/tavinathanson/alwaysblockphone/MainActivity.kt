package io.github.tavinathanson.alwaysblockphone

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.tavinathanson.alwaysblockphone.policy.AppRule
import io.github.tavinathanson.alwaysblockphone.policy.Status
import kotlinx.coroutines.delay

/** Status screen, and the screen the blocker shows in place of a blocked app. */
class MainActivity : ComponentActivity() {
    private var blockedId by mutableStateOf<String?>(null)
    private var serviceEnabled by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        blockedId = intent.getStringExtra(EXTRA_BLOCKED)
        val store = Store(this)
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
                    StatusScreen(
                        store = store,
                        blockedId = blockedId,
                        serviceEnabled = serviceEnabled,
                        openAccessibilitySettings = {
                            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        blockedId = intent.getStringExtra(EXTRA_BLOCKED)
    }

    override fun onResume() {
        super.onResume()
        serviceEnabled = getSystemService(AccessibilityManager::class.java)
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo.serviceInfo.packageName == packageName }
    }

    companion object {
        private const val EXTRA_BLOCKED = "blocked"

        fun blockedIntent(context: Context, ruleId: String): Intent =
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_BLOCKED, ruleId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}

@Composable
private fun StatusScreen(
    store: Store,
    blockedId: String?,
    serviceEnabled: Boolean,
    openAccessibilitySettings: () -> Unit,
) {
    // Ticks once a second; every status below is recomputed from stored timestamps.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }
    val refresh = { now = System.currentTimeMillis() }

    Column(
        Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("AlwaysBlockPhone", style = MaterialTheme.typography.headlineSmall)

        if (!serviceEnabled) {
            Notice("Blocking is off. Turn on the AlwaysBlockPhone accessibility service to enforce your policy.") {
                Button(onClick = openAccessibilitySettings) { Text("Open Accessibility settings") }
            }
        }

        val policy = store.policy.getOrElse {
            Notice("Policy error, nothing is blocked: ${it.message}")
            return@Column
        }

        policy.rule(blockedId ?: "")?.let { Notice("${it.displayName} is blocked. Request access below if you really mean it.") }

        policy.frictionRules.forEach { rule ->
            RuleCard(rule, store.status(rule, now), onRequest = { store.request(rule); refresh() }, onCancel = { store.cancel(rule); refresh() }, now)
        }

        Text(
            "Rules are built into the app from policy/policy.conf. Absent and normal rules are checked by bin/verify.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun RuleCard(rule: AppRule, status: Status, onRequest: () -> Unit, onCancel: () -> Unit, now: Long) {
    val timing = rule.timing ?: return
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(rule.displayName, style = MaterialTheme.typography.titleMedium)
            Text(
                "wait ${minutes(timing.waitMs)} · access ${minutes(timing.durationMs)} · cooldown ${minutes(timing.cooldownMs)}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                when (status) {
                    Status.Blocked -> "Blocked"
                    is Status.Waiting -> "Waiting: opens in ${clock(status.until - now)}"
                    is Status.Active -> "Open for ${clock(status.until - now)}. Launch it normally."
                    is Status.Cooldown -> "Cooldown: can request again in ${clock(status.until - now)}"
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (status) {
                    Status.Blocked -> Button(onClick = onRequest) { Text("Request access") }
                    is Status.Waiting -> OutlinedButton(onClick = onCancel) { Text("Cancel request") }
                    is Status.Active -> OutlinedButton(onClick = onCancel) { Text("End now") }
                    is Status.Cooldown -> Unit
                }
            }
        }
    }
}

@Composable
private fun Notice(text: String, action: @Composable () -> Unit = {}) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text)
            action()
        }
    }
}

private fun minutes(ms: Long): String {
    val m = ms / 60_000.0
    return if (m == Math.floor(m)) "${m.toLong()}m" else "%.1fm".format(m)
}

private fun clock(ms: Long): String {
    val total = (ms.coerceAtLeast(0) + 999) / 1000
    return if (total >= 3600) "%d:%02d:%02d".format(total / 3600, total / 60 % 60, total % 60)
    else "%d:%02d".format(total / 60, total % 60)
}
