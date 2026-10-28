package com.hcwebhook.app.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hcwebhook.app.HCWebhookApplication
import com.hcwebhook.app.HealthDataType
import com.hcwebhook.app.PreferencesManager
import com.hcwebhook.app.WebhookConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

@Composable
fun FamilyLoginScreen(
    onLoginSuccess: () -> Unit
) {
    val context = LocalContext.current
    val preferencesManager = remember { PreferencesManager(context) }
    val scope = rememberCoroutineScope()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val httpClient = remember {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    Scaffold(
        contentWindowInsets = WindowInsets.systemBars
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Family Fitness Sync",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Sign in with the same email & password you use on the Kota Family Fitness portal to automatically sync your daily steps.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    OutlinedTextField(
                        value = email,
                        onValueChange = {
                            email = it
                            errorMessage = null
                        },
                        label = { Text("Email address") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !loading
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            errorMessage = null
                        },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = if (passwordVisible) "Hide password" else "Show password"
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !loading
                    )

                    if (errorMessage != null) {
                        Text(
                            text = errorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Button(
                        onClick = {
                            val trimmedEmail = email.trim()
                            if (trimmedEmail.isEmpty() || password.isEmpty()) {
                                errorMessage = "Please enter your email and password."
                                return@Button
                            }
                            loading = true
                            errorMessage = null
                            scope.launch {
                                val result = authenticateWithBackend(
                                    httpClient = httpClient,
                                    baseUrl = PreferencesManager.DEFAULT_BACKEND_URL,
                                    email = trimmedEmail,
                                    password = password
                                )
                                loading = false
                                result.onSuccess { authData ->
                                    preferencesManager.setWebhookConfigs(
                                        listOf(
                                            WebhookConfig(
                                                url = authData.webhookUrl,
                                                name = "Family Fitness Tracker",
                                                isEnabled = true
                                            )
                                        )
                                    )
                                    preferencesManager.setConnectedUserName(authData.userName)
                                    preferencesManager.setConnectedUserEmail(authData.userEmail)
                                    if (preferencesManager.getEnabledDataTypes().isEmpty()) {
                                        preferencesManager.setEnabledDataTypes(setOf(HealthDataType.STEPS))
                                    }
                                    preferencesManager.setHasSeenOnboarding()
                                    (context.applicationContext as? HCWebhookApplication)?.scheduleSyncWork()
                                    onLoginSuccess()
                                }.onFailure { err ->
                                    errorMessage = err.message ?: "Could not connect to server."
                                }
                            }
                        },
                        enabled = !loading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        if (loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Connecting...")
                        } else {
                            Text("Sign In & Connect")
                        }
                    }
                }
            }
        }
    }
}

private data class MobileAuthResponse(
    val userName: String,
    val userEmail: String,
    val webhookUrl: String
)

private suspend fun authenticateWithBackend(
    httpClient: OkHttpClient,
    baseUrl: String,
    email: String,
    password: String
): Result<MobileAuthResponse> = withContext(Dispatchers.IO) {
    try {
        val jsonBody = buildJsonObject {
            put("email", email)
            put("password", password)
        }.toString()

        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/api/auth/mobile-token")
            .post(jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        httpClient.newCall(request).execute().use { response ->
            val rawBody = response.body?.string().orEmpty()
            val parsed = runCatching { Json.parseToJsonElement(rawBody).jsonObject }.getOrNull()

            if (!response.isSuccessful) {
                val serverError = parsed?.get("error")?.jsonPrimitive?.content
                return@withContext Result.failure(
                    Exception(serverError ?: "Sign in failed (HTTP ${response.code})")
                )
            }

            val webhookUrl = parsed?.get("webhookUrl")?.jsonPrimitive?.content
            val userName = parsed?.get("userName")?.jsonPrimitive?.content ?: email
            val userEmail = parsed?.get("userEmail")?.jsonPrimitive?.content ?: email

            if (webhookUrl.isNullOrBlank()) {
                return@withContext Result.failure(Exception("Server did not return a webhook URL."))
            }

            Result.success(
                MobileAuthResponse(
                    userName = userName,
                    userEmail = userEmail,
                    webhookUrl = webhookUrl
                )
            )
        }
    } catch (e: Exception) {
        Result.failure(Exception("Connection error: ${e.localizedMessage ?: "Please check your internet connection."}"))
    }
}
