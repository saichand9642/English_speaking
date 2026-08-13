package com.speak.app.ui.onboarding

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.speak.app.ui.components.BannerTone
import com.speak.app.ui.components.InfoBanner
import com.speak.app.ui.components.PrimaryAction
import com.speak.app.ui.components.SecondaryAction
import com.speak.app.ui.components.SectionCard

/**
 * Explains why the microphone is needed before asking for it.
 *
 * A bare system dialog on first launch, with no context, is the fastest way to get
 * a permanent denial. This screen states plainly what is recorded, where it goes,
 * and what leaves the phone -- which for this app is nothing.
 */
@Composable
fun PermissionGate(
    onGranted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var denied by remember { mutableStateOf(false) }
    var askedOnce by remember { mutableStateOf(false) }

    val alreadyGranted = remember {
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        askedOnce = true
        if (granted) onGranted() else denied = true
    }

    // Side effect, not a composition-time call: invoking the callback inline would
    // mutate state during composition.
    androidx.compose.runtime.LaunchedEffect(alreadyGranted) {
        if (alreadyGranted) onGranted()
    }
    if (alreadyGranted) return

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Speak", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(10.dp))
        Text(
            "A patient English tutor that listens to you talk and shows you what to fix.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(28.dp))

        SectionCard(title = "The microphone") {
            Text(
                "Speak needs to hear you in order to work. Your recordings are transcribed on " +
                    "this phone by a model inside the app, and the audio is never saved and never " +
                    "uploaded anywhere.",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "There is no account, no server and no analytics. The only time this app uses the " +
                    "internet is to download the tutor model once, and afterwards only if you " +
                    "choose to turn on the optional online mode.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(20.dp))

        if (denied) {
            InfoBanner(
                text = "Without the microphone there is nothing for Speak to listen to. " +
                    "You can turn it on in Android settings.",
                tone = BannerTone.WARNING,
                actionText = "Open settings",
                onAction = {
                    runCatching {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null)
                            )
                        )
                    }
                }
            )
            Spacer(Modifier.height(16.dp))
            SecondaryAction(
                text = "Ask again",
                onClick = { launcher.launch(Manifest.permission.RECORD_AUDIO) }
            )
        } else {
            PrimaryAction(
                text = "Allow the microphone",
                onClick = { launcher.launch(Manifest.permission.RECORD_AUDIO) }
            )
        }
    }
}
