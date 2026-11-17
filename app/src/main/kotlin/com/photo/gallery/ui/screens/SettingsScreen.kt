package com.photo.gallery.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photo.gallery.data.local.AutoLockDelay
import com.photo.gallery.data.local.SecuritySettings
import com.photo.gallery.ui.theme.BentoAccent
import com.photo.gallery.ui.theme.BentoAccentContainer
import com.photo.gallery.ui.theme.BentoAccentSubtle
import com.photo.gallery.ui.theme.BentoAmber
import com.photo.gallery.ui.theme.BentoBg
import com.photo.gallery.ui.theme.BentoBorder
import com.photo.gallery.ui.theme.BentoGreen
import com.photo.gallery.ui.theme.BentoSurface
import com.photo.gallery.ui.theme.BentoSurfaceCard
import com.photo.gallery.ui.theme.BentoSurfaceElevated
import com.photo.gallery.ui.theme.BentoTextPrimary
import com.photo.gallery.ui.theme.BentoTextSecondary
import com.photo.gallery.ui.theme.BentoTextTertiary

@Composable
fun SettingsScreen(
    securitySettings: SecuritySettings,
    isBiometricAvailable: Boolean,
    onSetPinClick: () -> Unit,
    onToggleBiometrics: (Boolean) -> Unit,
    onToggleDeletionLock: (Boolean) -> Unit,
    onRequestChangeVaultCode: () -> Unit,
    onToggleSecureModeAppWide: (Boolean) -> Unit = {},
    onSelectAutoLockDelay: (AutoLockDelay) -> Unit = {}
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BentoBg)
            .verticalScroll(rememberScrollState())
            .testTag("settings_screen")
    ) {
        // Header Subtitle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(BentoAccentSubtle),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = BentoAccent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Security & Privacy",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = BentoTextPrimary
                )
                Text(
                    text = "VaultCore Protection & Deletion Lock",
                    style = MaterialTheme.typography.bodySmall,
                    color = BentoTextSecondary
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Master PIN Section (Problem 2 & 11)
            BentoSettingsCard(title = "Authentication Credentials") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Password,
                            contentDescription = null,
                            tint = BentoAccent,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Master Security PIN",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = BentoTextPrimary
                            )
                            val pinCopy = if (securitySettings.hasPinSet) {
                                if (securitySettings.isPinUpgradeAvailable) {
                                    "Configured (4-digit • Upgrade to 6+ available)"
                                } else {
                                    "Configured (PBKDF2-HMAC-SHA256, 100k rounds + Keystore Hardware-bound)"
                                }
                            } else {
                                "Not Configured"
                            }
                            Text(
                                text = pinCopy,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (securitySettings.hasPinSet) BentoGreen else BentoAmber
                            )
                        }
                    }

                    Button(
                        onClick = onSetPinClick,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BentoAccent,
                            contentColor = BentoAccentContainer
                        ),
                        modifier = Modifier.testTag("change_pin_button")
                    ) {
                        Text(
                            text = if (securitySettings.hasPinSet) "Change" else "Set PIN",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }

                // Biometrics Toggle
                if (isBiometricAvailable) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = null,
                                tint = BentoAccent,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Biometric Authentication",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = BentoTextPrimary
                                )
                                Text(
                                    text = "Unlock with hardware-bound fingerprint/face key",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = BentoTextSecondary
                                )
                            }
                        }

                        Switch(
                            checked = securitySettings.biometricsEnabled,
                            onCheckedChange = onToggleBiometrics,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = BentoAccentContainer,
                                checkedTrackColor = BentoAccent,
                                uncheckedThumbColor = BentoTextTertiary,
                                uncheckedTrackColor = BentoSurfaceElevated
                            ),
                            modifier = Modifier.testTag("biometric_switch")
                        )
                    }
                }

                // Vault Access Code (Problem 9 - never show in plaintext)
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = BentoAccent,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Vault Access Code",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = BentoTextPrimary
                            )
                            Text(
                                text = if (securitySettings.hasSecretCodeSet) "Secret phrase configured (hashed)" else "Not set",
                                style = MaterialTheme.typography.bodySmall,
                                color = BentoTextSecondary
                            )
                        }
                    }

                    Button(
                        onClick = onRequestChangeVaultCode,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BentoAccent,
                            contentColor = BentoAccentContainer
                        ),
                        modifier = Modifier.testTag("change_vault_code_button")
                    ) {
                        Text(text = "Change", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            // Flagship Feature: Deletion Lock (Problem 11 clarification)
            BentoSettingsCard(title = "Flagship Protection") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = BentoAccent,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Deletion Lock Engine",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = BentoTextPrimary
                            )
                            Text(
                                text = "Requires authentication to delete locked items inside this app only. (Cannot prevent deletion from other apps or file managers).",
                                style = MaterialTheme.typography.bodySmall,
                                color = BentoTextSecondary
                            )
                        }
                    }

                    Switch(
                        checked = securitySettings.deletionLockEnabled,
                        onCheckedChange = onToggleDeletionLock,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = BentoAccentContainer,
                            checkedTrackColor = BentoAccent,
                            uncheckedThumbColor = BentoTextTertiary,
                            uncheckedTrackColor = BentoSurfaceElevated
                        ),
                        modifier = Modifier.testTag("deletion_lock_switch")
                    )
                }
            }

            // Screen & Auto-Lock Security (Problem 4 & 5)
            BentoSettingsCard(title = "Vault Privacy & Session") {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // FLAG_SECURE App-wide Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "App-wide Screenshot & Recents Protection",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = BentoTextPrimary
                            )
                            Text(
                                text = "Blocks screenshots and masks app preview in Android Recents app-wide. (Vault is always protected automatically).",
                                style = MaterialTheme.typography.bodySmall,
                                color = BentoTextSecondary
                            )
                        }
                        Switch(
                            checked = securitySettings.secureModeAppWide,
                            onCheckedChange = onToggleSecureModeAppWide,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = BentoAccentContainer,
                                checkedTrackColor = BentoAccent,
                                uncheckedThumbColor = BentoTextTertiary,
                                uncheckedTrackColor = BentoSurfaceElevated
                            )
                        )
                    }

                    // Auto-Lock Background Delay
                    Column {
                        Text(
                            text = "Auto-Lock Delay in Background",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = BentoTextPrimary
                        )
                        Text(
                            text = "How quickly the vault locks and clears key material when leaving the app:",
                            style = MaterialTheme.typography.bodySmall,
                            color = BentoTextSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AutoLockDelay.values().forEach { delay ->
                                val selected = securitySettings.autoLockDelay == delay
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (selected) BentoAccent else BentoSurfaceElevated)
                                        .clickable { onSelectAutoLockDelay(delay) }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = delay.displayName,
                                        color = if (selected) BentoAccentContainer else BentoTextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Architecture & Privacy Details Card (Problem 1, 6, 7, 8)
            BentoSettingsCard(title = "VaultCore Architecture") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FeatureDetailRow(
                        icon = Icons.Default.Lock,
                        title = "Tink Streaming AEAD Encryption",
                        desc = "AES-256-GCM HKDF streaming encryption with hardware-backed Keystore key wrapping. Decrypted on the fly; zero plaintext on disk."
                    )
                    FeatureDetailRow(
                        icon = Icons.Default.Security,
                        title = "Zero Disk/Cache Decryption",
                        desc = "Video streams and thumbnails decode directly into memory. Keystore master key zeroed on session lock."
                    )
                    FeatureDetailRow(
                        icon = Icons.Default.Shield,
                        title = "Internal Storage Only",
                        desc = "Vault resides strictly in private app filesDir. All external storage directories are excluded."
                    )
                    FeatureDetailRow(
                        icon = Icons.Default.Info,
                        title = "Zero Cloud Leakage",
                        desc = "Android cloud and local backups are completely disabled across all storage domains."
                    )
                }
            }

            Spacer(modifier = Modifier.height(110.dp))
        }
    }
}

@Composable
private fun BentoSettingsCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = BentoSurfaceCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, BentoBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = BentoAccent,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun FeatureDetailRow(
    icon: ImageVector,
    title: String,
    desc: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = BentoAccent,
            modifier = Modifier
                .size(18.dp)
                .padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = BentoTextPrimary
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.labelSmall,
                color = BentoTextSecondary,
                fontSize = 12.sp
            )
        }
    }
}

/**
 * Lets user set a new secret phrase that triggers the hidden vault from the search bar.
 * Never displays previous code in plaintext (Problem 9).
 */
@Composable
fun VaultCodeEditDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BentoSurface,
        title = {
            Text(text = "Change Vault Access Code", color = BentoTextPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text(
                    text = "Enter a new secret phrase. Typing this phrase into the search bar will open the Hidden Vault.",
                    color = BentoTextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    placeholder = { Text("Enter secret phrase...", color = BentoTextTertiary) },
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = BentoTextPrimary,
                        unfocusedTextColor = BentoTextPrimary,
                        focusedIndicatorColor = BentoAccent,
                        unfocusedIndicatorColor = BentoTextTertiary,
                        cursorColor = BentoAccent
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("vault_code_input")
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmed = text.trim()
                    if (trimmed.isNotEmpty()) onConfirm(trimmed) else onDismiss()
                },
                modifier = Modifier.testTag("vault_code_save_button")
            ) {
                Text(text = "Save", color = BentoAccent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel", color = BentoTextSecondary)
            }
        }
    )
}
