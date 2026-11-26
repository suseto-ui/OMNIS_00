package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.auth.OmnisAuthService
import com.example.auth.GoogleAuthManager
import com.example.auth.UserRole
import com.example.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Vstupní přihlašovací rozhraní O.M.N.I.S. s podporou předvyplnění testovacích účtů,
 * RBAC autorizace, biometrie a cyber-tech designu.
 */
@Composable
fun OmnisLoginScreen(
    onLoginSuccess: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val authNotice by OmnisAuthService.authNotice.collectAsStateWithLifecycle()
    val currentSession by OmnisAuthService.currentSession.collectAsStateWithLifecycle()

    var isRegisterMode by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf(currentSession?.username ?: "") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var emailInput by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    var pendingApprovalInfo by remember { mutableStateOf<PendingApprovalInfo?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(currentSession) {
        if (username.isBlank() && !currentSession?.username.isNullOrBlank()) {
            username = currentSession?.username.orEmpty()
        }
    }

    pendingApprovalInfo?.let { pendingInfo ->
        PendingApprovalScreen(
            info = pendingInfo,
            onBackToLogin = {
                pendingApprovalInfo = null
                isRegisterMode = false
                errorMessage = null
                successMessage = "Jakmile administrátor účet schválí, můžete se přihlásit."
            }
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OmnisBgDark)
            .imePadding()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp)
                .testTag("omnis_login_card"),
            shape = RoundedCornerShape(20.dp),
            color = OmnisPanelDark,
            border = androidx.compose.foundation.BorderStroke(1.dp, OmnisBorderDark),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // System Status Indicator Pill
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = OmnisBgDark,
                    border = androidx.compose.foundation.BorderStroke(1.dp, OmnisEmerald.copy(alpha = 0.5f)),
                    modifier = Modifier.padding(bottom = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(OmnisEmerald)
                        )
                        Text(
                            text = "SYSTÉM ONLINE • AKTIVNÍ ŘÍZENÍ PŘÍSTUPU",
                            color = OmnisEmerald,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                // Header Logo and Title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Brush.linearGradient(listOf(OmnisCyan, OmnisViolet))),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Ω", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 26.sp)
                    }

                    Column {
                        Text(
                            text = "O.M.N.I.S. OPERATING SYSTEM",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            letterSpacing = 1.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = if (isRegisterMode) "Nová registrace • Zabezpečené přihlášení" else "Kognitivní architektura • Bezpečnostní brána",
                            color = OmnisCyan,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Banner pro vynucené odhlášení z AuthInterceptoru
                authNotice?.let { notice ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0x33FF5252),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF5252)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_notice_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = Color(0xFFFF5252),
                                modifier = Modifier.size(18.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "RELACE UKONČENA BEZPEČNOSTNÍ OCHRANOU",
                                    color = Color(0xFFFF5252),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = notice,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }
                            IconButton(
                                onClick = { OmnisAuthService.clearAuthNotice() },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Text("✕", color = OmnisTextMuted, fontSize = 12.sp)
                            }
                        }
                    }
                }

                // Login vs Register Switcher
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(OmnisBgDark, RoundedCornerShape(10.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp)),
                        color = if (!isRegisterMode) OmnisCyan.copy(alpha = 0.2f) else Color.Transparent,
                        border = if (!isRegisterMode) androidx.compose.foundation.BorderStroke(1.dp, OmnisCyan) else null,
                        onClick = {
                            isRegisterMode = false
                            errorMessage = null
                            successMessage = null
                        }
                    ) {
                        Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = "PŘIHLÁŠENÍ",
                                color = if (!isRegisterMode) Color.White else OmnisTextMuted,
                                fontSize = 12.sp,
                                fontWeight = if (!isRegisterMode) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp)),
                        color = if (isRegisterMode) OmnisViolet.copy(alpha = 0.25f) else Color.Transparent,
                        border = if (isRegisterMode) androidx.compose.foundation.BorderStroke(1.dp, OmnisViolet) else null,
                        onClick = {
                            isRegisterMode = true
                            errorMessage = null
                            successMessage = null
                        }
                    ) {
                        Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = "REGISTRACE",
                                color = if (isRegisterMode) Color.White else OmnisTextMuted,
                                fontSize = 12.sp,
                                fontWeight = if (isRegisterMode) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                if (isRegisterMode) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = OmnisCyan.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, OmnisCyan.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = OmnisCyan, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Nová registrace podléhá schválení administrátorem. Účet bude aktivován po ověření.",
                                color = Color.White,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }

                // Form Fields
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it; errorMessage = null },
                    label = { Text("Uživatelské jméno") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = OmnisCyan) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = OmnisCyan,
                        unfocusedBorderColor = OmnisBorderDark,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = OmnisCyan,
                        unfocusedLabelColor = OmnisTextMuted
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_username_field")
                )

                if (isRegisterMode) {
                    OutlinedTextField(
                        value = emailInput,
                        onValueChange = { emailInput = it; errorMessage = null },
                        label = { Text("E-mailová adresa") },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = OmnisViolet) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = OmnisViolet,
                            unfocusedBorderColor = OmnisBorderDark,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedLabelColor = OmnisViolet,
                            unfocusedLabelColor = OmnisTextMuted
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_email_field")
                    )
                }

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; errorMessage = null },
                    label = { Text(if (isRegisterMode) "Nové heslo (min. 6 znaků)" else "Heslo") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = if (isRegisterMode) OmnisViolet else OmnisCyan) },
                    trailingIcon = {
                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                            Icon(
                                imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (isPasswordVisible) "Skrýt heslo" else "Zobrazit heslo",
                                tint = OmnisTextMuted
                            )
                        }
                    },
                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = if (isRegisterMode) OmnisViolet else OmnisCyan,
                        unfocusedBorderColor = OmnisBorderDark,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = if (isRegisterMode) OmnisViolet else OmnisCyan,
                        unfocusedLabelColor = OmnisTextMuted
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_password_field")
                )

                if (isRegisterMode) {
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it; errorMessage = null },
                        label = { Text("Potvrzení hesla") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = OmnisViolet) },
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = OmnisViolet,
                            unfocusedBorderColor = OmnisBorderDark,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedLabelColor = OmnisViolet,
                            unfocusedLabelColor = OmnisTextMuted
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_reg_confirm_password_input")
                    )
                }

                // Error and Success Feedback Messages
                errorMessage?.let { msg ->
                    Text(
                        text = msg,
                        color = Color(0xFFFF5252),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }

                successMessage?.let { msg ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = OmnisEmerald.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, OmnisEmerald)
                    ) {
                        Text(
                            text = msg,
                            color = OmnisEmerald,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                if (isRegisterMode) {
                    Button(
                        onClick = {
                            if (username.isBlank() || password.length < 6 || password != confirmPassword) {
                                errorMessage = "Ověřte prosím zadaná data (heslo min. 6 znaků)."
                                return@Button
                            }
                            val targetEmail = if (emailInput.contains("@") && emailInput.contains(".")) {
                                emailInput.trim()
                            } else {
                                "${username.trim()}@omnis.ai"
                            }
                            isLoading = true
                            coroutineScope.launch {
                                when (val res = OmnisAuthService.register(context, username.trim(), password, targetEmail)) {
                                    is com.example.auth.RegistrationResult.Success -> onLoginSuccess()
                                    is com.example.auth.RegistrationResult.Pending -> {
                                        pendingApprovalInfo = PendingApprovalInfo(
                                            username = username.trim(),
                                            email = targetEmail,
                                            message = res.message
                                        )
                                        password = ""
                                        confirmPassword = ""
                                    }
                                    is com.example.auth.RegistrationResult.Failure -> {
                                        errorMessage = res.reason
                                    }
                                }
                                isLoading = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("auth_register_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = OmnisViolet),
                        enabled = !isLoading
                    ) {
                        Text("ZAREGISTROVAT SE")
                    }
                } else {
                    Button(
                        onClick = {
                            isLoading = true
                            errorMessage = null
                            successMessage = null
                            coroutineScope.launch {
                                when (val res = OmnisAuthService.loginWithDb(context, username.trim(), password)) {
                                    is com.example.auth.AuthResult.Success -> onLoginSuccess()
                                    is com.example.auth.AuthResult.PendingApproval -> {
                                        pendingApprovalInfo = PendingApprovalInfo(
                                            username = username.trim(),
                                            email = res.email.orEmpty(),
                                            message = res.message
                                        )
                                    }
                                    is com.example.auth.AuthResult.Failure -> {
                                        errorMessage = res.reason
                                    }
                                }
                                isLoading = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("auth_login_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = OmnisCyan),
                        enabled = !isLoading
                    ) {
                        Text("PŘIHLÁSIT SE", color = Color.Black)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = OmnisBorderDark)

                // Google Login Button
                OutlinedButton(
                    onClick = {
                        val activity = context.findActivity()
                        if (activity == null) {
                            errorMessage = "Chyba aktivního okna pro Google přihlášení."
                            return@OutlinedButton
                        }
                        isLoading = true
                        errorMessage = null
                        successMessage = null

                        GoogleAuthManager.signInWithGoogle(
                            activity = activity,
                            scope = coroutineScope,
                            onSuccess = { googleAccount, googleIdToken ->
                                coroutineScope.launch {
                                    when (val backendResult = OmnisAuthService.loginWithGoogle(context, googleIdToken, googleAccount)) {
                                        is com.example.auth.AuthResult.Success -> onLoginSuccess()
                                        is com.example.auth.AuthResult.PendingApproval -> {
                                            pendingApprovalInfo = PendingApprovalInfo(
                                                username = googleAccount.displayName.orEmpty().ifBlank { googleAccount.email.orEmpty() },
                                                email = googleAccount.email.orEmpty(),
                                                message = backendResult.message
                                            )
                                        }
                                        is com.example.auth.AuthResult.Failure -> {
                                            errorMessage = backendResult.reason
                                        }
                                    }
                                    isLoading = false
                                }
                            },
                            onError = { err ->
                                errorMessage = err
                                isLoading = false
                            },
                            onCancel = {
                                isLoading = false
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("google_signin_button"),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
                    enabled = !isLoading
                ) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("PŘIHLÁŠIT SE PŘES GOOGLE", color = Color.White)
                }
            }
        }
    }
}

private fun android.content.Context.findActivity(): android.app.Activity? {
    var context = this
    while (context is android.content.ContextWrapper) {
        if (context is android.app.Activity) return context
        context = context.baseContext
    }
    return null
}
