package com.photo.gallery.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.FragmentActivity
import com.photo.gallery.security.BiometricAuthManager
import com.photo.gallery.ui.theme.BentoAccent
import com.photo.gallery.ui.theme.BentoAccentContainer
import com.photo.gallery.ui.theme.BentoAccentSubtle
import com.photo.gallery.ui.theme.BentoAmber
import com.photo.gallery.ui.theme.BentoBorder
import com.photo.gallery.ui.theme.BentoRose
import com.photo.gallery.ui.theme.BentoSurface
import com.photo.gallery.ui.theme.BentoSurfaceCard
import com.photo.gallery.ui.theme.BentoSurfaceElevated
import com.photo.gallery.ui.theme.BentoSurfaceHighlight
import com.photo.gallery.ui.theme.BentoTextPrimary
import com.photo.gallery.ui.theme.BentoTextSecondary
import com.photo.gallery.ui.viewmodel.AuthReason

/**
 * Bento Grid styled Authentication Dialog providing PIN entry and Biometrics.
 * Used for Deletion Lock verification, Vault unlocking, and PIN setup.
 */
@Composable
fun AuthenticationDialog(
    reason: AuthReason,
    title: String,
    description: String,
    hasPinSet: Boolean,
    isBiometricAvailable: Boolean,
    biometricsEnabled: Boolean,
    biometricAuthManager: BiometricAuthManager,
    onVerifyPin: (String, (Boolean) -> Unit) -> Unit,
    onSetPin: (String) -> Unit,
    onSuccess: () -> Unit,
    onDismiss: () -> Unit
) {
    var enteredPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var isConfirmingSetup by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    // Trigger biometrics automatically if available and user is verifying
    LaunchedEffect(Unit) {
        if (hasPinSet && isBiometricAvailable && biometricsEnabled && reason != AuthReason.SET_PIN) {
            val activity = context as? FragmentActivity
            if (activity != null) {
                biometricAuthManager.promptBiometric(
                    activity = activity,
                    title = title,
                    subtitle = description,
                    onSuccess = { onSuccess() },
                    onError = { /* fallback silently to PIN pad */ },
                    onFallbackToPin = { /* keep PIN pad visible */ }
                )
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(16.dp)
                .testTag("auth_dialog_card"),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = BentoSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, BentoBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Icon
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(
                            if (reason == AuthReason.DELETION_LOCK) BentoAccentSubtle
                            else BentoAccentSubtle
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (reason == AuthReason.DELETION_LOCK) Icons.Default.Shield else Icons.Default.Lock,
                        contentDescription = "Security Authentication",
                        tint = BentoAccent,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (isConfirmingSetup) "Confirm Your PIN" else title,
                    style = MaterialTheme.typography.titleLarge,
                    color = BentoTextPrimary,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (isConfirmingSetup) "Re-enter the 4-digit PIN to confirm" else description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = BentoTextSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                // PIN Dots Display (4 digits)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val activePin = if (isConfirmingSetup) confirmPin else enteredPin
                    for (i in 0 until 4) {
                        val isFilled = i < activePin.length
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isFilled) BentoAccent
                                    else BentoSurfaceHighlight
                                )
                                .border(
                                    1.dp,
                                    if (isFilled) BentoAccent else BentoBorder,
                                    CircleShape
                                )
                        )
                    }
                }

                // Error message
                AnimatedVisibility(
                    visible = errorMessage != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Text(
                        text = errorMessage ?: "",
                        color = BentoRose,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Number Keypad (1 - 9, Biometric/Empty, 0, Backspace)
                val onNumberClick: (String) -> Unit = { num ->
                    errorMessage = null
                    if (isConfirmingSetup) {
                        if (confirmPin.length < 4) {
                            val newPin = confirmPin + num
                            confirmPin = newPin
                            if (newPin.length == 4) {
                                if (newPin == enteredPin) {
                                    onSetPin(newPin)
                                    onSuccess()
                                } else {
                                    errorMessage = "PINs do not match. Try again."
                                    confirmPin = ""
                                }
                            }
                        }
                    } else {
                        if (enteredPin.length < 4) {
                            val newPin = enteredPin + num
                            enteredPin = newPin
                            if (newPin.length == 4) {
                                if (reason == AuthReason.SET_PIN) {
                                    isConfirmingSetup = true
                                } else {
                                    onVerifyPin(newPin) { isValid ->
                                        if (isValid) {
                                            onSuccess()
                                        } else {
                                            errorMessage = "Incorrect PIN. Try again."
                                            enteredPin = ""
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                val onBackspace: () -> Unit = {
                    errorMessage = null
                    if (isConfirmingSetup) {
                        if (confirmPin.isNotEmpty()) confirmPin = confirmPin.dropLast(1)
                    } else {
                        if (enteredPin.isNotEmpty()) enteredPin = enteredPin.dropLast(1)
                    }
                }

                KeypadGrid(
                    isBiometricAvailable = isBiometricAvailable && hasPinSet && reason != AuthReason.SET_PIN,
                    onNumberClick = onNumberClick,
                    onBackspace = onBackspace,
                    onBiometricClick = {
                        val activity = context as? FragmentActivity
                        if (activity != null) {
                            biometricAuthManager.promptBiometric(
                                activity = activity,
                                title = title,
                                subtitle = description,
                                onSuccess = { onSuccess() },
                                onError = { err -> errorMessage = err },
                                onFallbackToPin = { /* focus stays on pin */ }
                            )
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_cancel_button"),
                    shape = CircleShape,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = BentoTextSecondary)
                ) {
                    Text("Cancel", fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun KeypadGrid(
    isBiometricAvailable: Boolean,
    onNumberClick: (String) -> Unit,
    onBackspace: () -> Unit,
    onBiometricClick: () -> Unit
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("BIO", "0", "DEL")
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        for (row in rows) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (key in row) {
                    when (key) {
                        "BIO" -> {
                            if (isBiometricAvailable) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(BentoAccentSubtle)
                                        .clickable { onBiometricClick() }
                                        .testTag("keypad_biometric_button"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Fingerprint,
                                        contentDescription = "Biometric authentication",
                                        tint = BentoAccent,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.size(64.dp))
                            }
                        }
                        "DEL" -> {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(BentoSurfaceElevated)
                                    .clickable { onBackspace() }
                                    .testTag("keypad_backspace_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Backspace,
                                    contentDescription = "Backspace",
                                    tint = BentoTextSecondary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        else -> {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(BentoSurfaceCard)
                                    .border(1.dp, BentoBorder, CircleShape)
                                    .clickable { onNumberClick(key) }
                                    .testTag("keypad_num_$key"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = key,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = BentoTextPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
