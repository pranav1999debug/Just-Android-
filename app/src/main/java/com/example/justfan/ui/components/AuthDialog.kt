package com.example.justfan.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.justfan.data.model.UserProfile
import com.example.justfan.ui.theme.GoldAccent
import com.example.justfan.ui.theme.SuccessGreen
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AuthDialog(
    userProfile: UserProfile,
    isOpen: Boolean,
    onDismiss: () -> Unit,
    onSignInWithGoogle: (email: String, name: String) -> Unit,
    onSignInWithPasskey: (name: String) -> Unit,
    onSignInWithEmail: (email: String, pass: String) -> Result<UserProfile>,
    onSignOut: () -> Unit,
    onUpgradeClick: () -> Unit
) {
    if (!isOpen) return

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header with close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = CircleShape,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (userProfile.isSignedIn) Icons.Default.AccountCircle else Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (userProfile.isSignedIn) "Your Account" else "Sign In to JUSTFAN",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (userProfile.isSignedIn) "Tier: ${userProfile.tier}" else "Choose your sign-in method",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (userProfile.isSignedIn) {
                    // Signed In Account Profile View
                    SignedInProfileView(
                        userProfile = userProfile,
                        onSignOut = {
                            onSignOut()
                            onDismiss()
                        },
                        onUpgradeClick = {
                            onDismiss()
                            onUpgradeClick()
                        }
                    )
                } else {
                    // Sign In Options
                    SignInFormView(
                        onSignInWithGoogle = { email, name ->
                            onSignInWithGoogle(email, name)
                            onDismiss()
                        },
                        onSignInWithPasskey = { name ->
                            onSignInWithPasskey(name)
                            onDismiss()
                        },
                        onSignInWithEmail = { email, pass ->
                            val res = onSignInWithEmail(email, pass)
                            if (res.isSuccess) {
                                onDismiss()
                            }
                            res
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SignedInProfileView(
    userProfile: UserProfile,
    onSignOut: () -> Unit,
    onUpgradeClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Avatar + Tier Badge
        Box(contentAlignment = Alignment.BottomEnd) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = userProfile.username.take(2).uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Surface(
                color = when (userProfile.tier) {
                    "Legendary" -> GoldAccent
                    "Pro" -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
                shape = RoundedCornerShape(6.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.Black.copy(alpha = 0.3f))
            ) {
                Text(
                    text = userProfile.tier.uppercase(),
                    color = if (userProfile.tier == "Legendary") Color.Black else Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = userProfile.username,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = userProfile.email,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Subscription Tier & Request Quota Card
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Membership Status",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (userProfile.isAdmin) {
                        Surface(
                            color = Color(0xFFEF4444),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "ADMIN",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (userProfile.tier == "Legendary") Icons.Default.Star else Icons.Default.Diamond,
                        contentDescription = null,
                        tint = if (userProfile.tier == "Legendary") GoldAccent else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${userProfile.tier} Tier",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Requests Allowance Text
                val requestText = when (userProfile.tier) {
                    "Legendary" -> "✨ Unlimited requests forever (Lifetime VIP)"
                    "Pro" -> {
                        val expiresFormatted = if (userProfile.proExpiresAt > 0L && userProfile.proExpiresAt != Long.MAX_VALUE) {
                            val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                            "Active until ${sdf.format(Date(userProfile.proExpiresAt))}"
                        } else {
                            "1 Month Unlimited"
                        }
                        "⚡ Unlimited requests ($expiresFormatted)"
                    }
                    else -> "📝 3 requests allowed (${userProfile.remainingRequests} remaining)"
                }

                Text(
                    text = requestText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )

                if (userProfile.tier == "Free") {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { (userProfile.requestsCount.toFloat() / 3f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surface
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Upgrade / Change Tier CTA
        if (userProfile.tier != "Legendary") {
            Button(
                onClick = onUpgradeClick,
                colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = Color.Black),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.WorkspacePremium, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (userProfile.tier == "Free") "Upgrade to Pro / Legendary" else "Upgrade to Legendary",
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Switch Account Button
        Button(
            onClick = onSignOut,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.AccountCircle, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Switch Account / Sign In with Google")
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Sign Out Button
        OutlinedButton(
            onClick = onSignOut,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.ExitToApp, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Sign Out")
        }
    }
}

@Composable
private fun SignInFormView(
    onSignInWithGoogle: (email: String, name: String) -> Unit,
    onSignInWithPasskey: (name: String) -> Unit,
    onSignInWithEmail: (email: String, pass: String) -> Result<UserProfile>
) {
    var selectedMethod by remember { mutableIntStateOf(0) } // 0: Google, 1: Passkey, 2: Email/Pass

    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Method Selector Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf("Google", "Passkey", "Email").forEachIndexed { idx, label ->
                val isSelected = selectedMethod == idx
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable {
                            selectedMethod = idx
                            errorMessage = null
                        }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (selectedMethod) {
            0 -> {
                // Google Sign In
                var customGoogleEmail by remember { mutableStateOf("") }
                var isCustomInputVisible by remember { mutableStateOf(false) }
                val context = androidx.compose.ui.platform.LocalContext.current

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Sign in with your Google account to unlock theme settings, custom uploaded background wallpapers, and requests.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(12.dp),
                        shadowElevation = 2.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSignInWithGoogle("reytherapper12@gmail.com", "Rey (Admin)")
                            }
                            .testTag("btn_google_signin")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = "Google",
                                tint = Color(0xFF4285F4),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Continue as reytherapper12@gmail.com",
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "Platform Administrator • Legendary Tier",
                                    color = Color(0xFF16A34A),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (!isCustomInputVisible) {
                        OutlinedButton(
                            onClick = { isCustomInputVisible = true },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Mail, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sign In with Another Google Email")
                        }
                    } else {
                        OutlinedTextField(
                            value = customGoogleEmail,
                            onValueChange = { customGoogleEmail = it },
                            label = { Text("Enter Google/Gmail Address") },
                            placeholder = { Text("e.g. yourname@gmail.com") },
                            singleLine = true,
                            leadingIcon = {
                                Icon(Icons.Default.AccountCircle, contentDescription = null, tint = Color(0xFF4285F4))
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                val email = customGoogleEmail.trim()
                                if (email.contains("@")) {
                                    val name = email.substringBefore("@").replaceFirstChar { it.uppercase() }
                                    onSignInWithGoogle(email, name)
                                }
                            },
                            enabled = customGoogleEmail.trim().contains("@"),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Sign In as $customGoogleEmail", fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = {
                            try {
                                val oauthUrl = "https://zlboyxbqppoimhhbvrax.supabase.co/auth/v1/authorize?provider=google&redirect_to=justfan://auth"
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(oauthUrl))
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open Supabase Google OAuth (Browser)")
                    }
                }
            }
            1 -> {
                // Passkey Sign In
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = "Passkey",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Device Passkey & Biometrics",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Use your device fingerprint, face unlock, or screen lock passkey to sign in without passwords.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            onSignInWithPasskey("Device Passkey Verified")
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth().testTag("btn_passkey_signin")
                    ) {
                        Icon(Icons.Default.Key, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Verify with Device Passkey", fontWeight = FontWeight.Bold)
                    }
                }
            }
            2 -> {
                // Email & Password
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = emailInput,
                        onValueChange = {
                            emailInput = it
                            errorMessage = null
                        },
                        label = { Text("Email Address") },
                        placeholder = { Text("e.g. reytherapper12@gmail.com") },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().testTag("auth_email_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = {
                            passwordInput = it
                            errorMessage = null
                        },
                        label = { Text("Password") },
                        placeholder = { Text("Enter your password") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle password"
                                )
                            }
                        },
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().testTag("auth_password_input")
                    )

                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = errorMessage ?: "",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Quick Fill Admin button
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                emailInput = "reytherapper12@gmail.com"
                                passwordInput = "Pranav19ranjan97"
                                errorMessage = null
                            }
                            .padding(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Fill Admin (reytherapper12@gmail.com)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            if (emailInput.isBlank()) {
                                errorMessage = "Please enter an email address."
                                return@Button
                            }
                            val res = onSignInWithEmail(emailInput, passwordInput)
                            if (res.isFailure) {
                                errorMessage = res.exceptionOrNull()?.message ?: "Sign-in failed."
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().testTag("btn_email_signin")
                    ) {
                        Text("Sign In / Sign Up", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
