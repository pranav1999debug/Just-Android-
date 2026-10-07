package com.example.justfan.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.justfan.data.model.UserProfile
import com.example.justfan.data.remote.ImgchestUploader
import com.example.justfan.data.remote.PayPalClient
import com.example.justfan.ui.theme.GoldAccent
import com.example.justfan.ui.theme.SuccessGreen
import com.example.justfan.util.QrCodeGenerator
import kotlinx.coroutines.launch

data class PlanData(
    val name: String,
    val price: String,
    val inrPrice: String,
    val period: String,
    val description: String,
    val popular: Boolean,
    val features: List<Pair<String, Boolean>>
)

val PLANS = listOf(
    PlanData(
        name = "Free",
        price = "$0",
        inrPrice = "₹0",
        period = "forever",
        description = "Browse and discover trending creator sets",
        popular = false,
        features = listOf(
            "Browse all public content" to true,
            "Search & filter galleries" to true,
            "Save bookmarks & favorites" to true,
            "3 community requests total" to true,
            "Ad-free viewing" to false,
            "Unlimited requests" to false,
            "Direct 4K ZIP Downloads" to false
        )
    ),
    PlanData(
        name = "Pro",
        price = "$5",
        inrPrice = "₹150",
        period = "/month",
        description = "Unlimited requests for 1 month & fast 4K downloads",
        popular = true,
        features = listOf(
            "Browse all public content" to true,
            "Search & filter galleries" to true,
            "Save bookmarks & favorites" to true,
            "Unlimited requests for 1 month" to true,
            "Custom themes & background wallpaper" to true,
            "100% Ad-free browsing" to true,
            "Direct 4K ZIP Downloads" to true
        )
    ),
    PlanData(
        name = "Legendary",
        price = "$15",
        inrPrice = "₹500",
        period = "one-time",
        description = "Unlimited requests for unlimited time (Lifetime VIP)",
        popular = false,
        features = listOf(
            "Browse all public content" to true,
            "Search & filter galleries" to true,
            "Save bookmarks & favorites" to true,
            "Unlimited requests for unlimited time" to true,
            "Custom themes & background wallpaper" to true,
            "Direct 4K Ultra HD Downloads" to true,
            "Lifetime VIP supporter status" to true
        )
    )
)

const val UPI_ID = "12spranavranjan-3@okhdfcbank"
const val UPI_NAME = "pranav singh"
const val UPI_BANK_DETAILS = "Kotak Mahindra Bank 8423"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PricingScreen(
    currentPlan: String,
    isAdmin: Boolean = false,
    userProfile: UserProfile? = null,
    onSelectPlan: (String) -> Unit = {},
    onSubmitPaymentProof: (tier: String, method: String, amount: String, proofImageUrl: String, note: String) -> Unit = { _, _, _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var upgradedPlanName by remember { mutableStateOf<String?>(null) }
    var checkoutPlan by remember { mutableStateOf<PlanData?>(null) }
    var showGooglePayDialog by remember { mutableStateOf<PlanData?>(null) }
    var showPayPalDialog by remember { mutableStateOf<PlanData?>(null) }

    // PayPal payment states
    var isProcessingPayPal by remember { mutableStateOf(false) }
    var payPalApprovalUrl by remember { mutableStateOf<String?>(null) }
    var payPalOrderId by remember { mutableStateOf<String?>(null) }
    var payPalStatusMessage by remember { mutableStateOf<String?>(null) }

    // Google Pay / UPI states
    var selectedProofUri by remember { mutableStateOf<Uri?>(null) }
    var uploadedImgchestUrl by remember { mutableStateOf<String?>(null) }
    var isUploadingToImgchest by remember { mutableStateOf(false) }
    var uploadErrorMessage by remember { mutableStateOf<String?>(null) }
    var userPaymentNote by remember { mutableStateOf("") }
    var proofSubmittedSuccessfully by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedProofUri = uri
            uploadErrorMessage = null
            isUploadingToImgchest = true
            coroutineScope.launch {
                val uploadResult = ImgchestUploader.uploadImage(
                    context = context,
                    imageUri = uri,
                    title = "JUSTFAN Payment Proof - ${showGooglePayDialog?.name ?: "Plan"}"
                )
                isUploadingToImgchest = false
                if (uploadResult.isSuccess) {
                    uploadedImgchestUrl = uploadResult.getOrNull()
                    Toast.makeText(context, "Screenshot uploaded to Imgchest!", Toast.LENGTH_SHORT).show()
                } else {
                    uploadErrorMessage = uploadResult.exceptionOrNull()?.message ?: "Failed to upload to Imgchest"
                    Toast.makeText(context, "Imgchest upload failed: $uploadErrorMessage", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Diamond,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Membership Plans", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            item {
                Text(
                    text = "Upgrade your JUSTFAN membership for unlimited community requests, ad-free downloads, and 4K uncompressed archives.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Quick Payment methods banner
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Payment, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Accepted Payment Methods", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text("⚡ PayPal", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                                    Text("Instant automatic account upgrade", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Surface(
                                color = SuccessGreen.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text("🇮🇳 Google Pay / UPI", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = SuccessGreen)
                                    Text("Pro: Rs 150 • Leg: Rs 500\nManual Admin Verification", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            items(PLANS.size) { i ->
                val plan = PLANS[i]
                val isCurrent = currentPlan.equals(plan.name, ignoreCase = true)

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier
                        .testTag("pricing_card_${plan.name.lowercase()}")
                        .fillMaxWidth()
                        .then(
                            if (plan.popular) Modifier.border(
                                width = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(16.dp)
                            ) else if (plan.name == "Legendary") Modifier.border(
                                width = 2.dp,
                                color = GoldAccent,
                                shape = RoundedCornerShape(16.dp)
                            ) else Modifier
                        )
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = plan.name,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = when (plan.name) {
                                    "Legendary" -> GoldAccent
                                    "Pro" -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                            )

                            if (plan.popular) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Text(
                                        text = "MOST POPULAR",
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            } else if (plan.name == "Legendary") {
                                Surface(
                                    color = GoldAccent,
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Text(
                                        text = "🔥 BEST VALUE",
                                        color = Color.Black,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = plan.price,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (plan.inrPrice != "₹0") {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "(${plan.inrPrice})",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SuccessGreen
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = plan.period,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }

                        Text(
                            text = plan.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(modifier = Modifier.height(16.dp))

                        plan.features.forEach { (feat, included) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = if (included) Icons.Default.Check else Icons.Default.Close,
                                    contentDescription = null,
                                    tint = if (included) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = feat,
                                    fontSize = 13.sp,
                                    color = if (included) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        if (isCurrent) {
                            FilledTonalButton(
                                onClick = {},
                                enabled = false,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Current Active Tier", fontWeight = FontWeight.Bold)
                            }
                        } else if (isAdmin) {
                            Button(
                                onClick = {
                                    onSelectPlan(plan.name)
                                    upgradedPlanName = plan.name
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (plan.name == "Legendary") GoldAccent else MaterialTheme.colorScheme.primary,
                                    contentColor = if (plan.name == "Legendary") Color.Black else MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier
                                    .testTag("btn_select_plan_${plan.name.lowercase()}")
                                    .fillMaxWidth()
                            ) {
                                Text("Set Tier: ${plan.name} (Admin Override)", fontWeight = FontWeight.Bold)
                            }
                        } else if (plan.name != "Free") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { checkoutPlan = plan },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (plan.name == "Legendary") GoldAccent else MaterialTheme.colorScheme.primary,
                                        contentColor = if (plan.name == "Legendary") Color.Black else MaterialTheme.colorScheme.onPrimary
                                    ),
                                    modifier = Modifier
                                        .testTag("btn_pay_plan_${plan.name.lowercase()}")
                                        .fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.Diamond, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Upgrade to ${plan.name} (${plan.price} / ${plan.inrPrice})", fontWeight = FontWeight.Bold)
                                    }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal: Choose Payment Method (PayPal vs Google Pay UPI)
    if (checkoutPlan != null) {
        val target = checkoutPlan!!
        AlertDialog(
            onDismissRequest = { checkoutPlan = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.Diamond,
                    contentDescription = null,
                    tint = if (target.name == "Legendary") GoldAccent else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text("Upgrade to ${target.name}", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Choose your preferred payment method below:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Option 1: PayPal (Automatic)
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val selected = checkoutPlan
                                checkoutPlan = null
                                showPayPalDialog = selected
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Payment, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("PayPal", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = SuccessGreen.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text("AUTOMATIC", color = SuccessGreen, fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                    }
                                }
                                Text("Amount: ${target.price} USD", fontWeight = FontWeight.Medium, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                Text("Automatic instant account upgrade via PayPal checkout", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    // Option 2: Google Pay / UPI (Manual Upgrade via Imgchest Screenshot)
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, SuccessGreen),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val selected = checkoutPlan
                                checkoutPlan = null
                                showGooglePayDialog = selected
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = SuccessGreen.copy(alpha = 0.15f),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = SuccessGreen)
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Google Pay / UPI QR", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = GoldAccent.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text("SCAN & PAY", color = GoldAccent, fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                    }
                                }
                                Text("Amount: ${target.inrPrice} INR (${if (target.name == "Pro") "Rs 150" else "Rs 500"})", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = SuccessGreen)
                                Text("Pay using GPay QR / UPI and upload screenshot for manual Admin upgrade", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { checkoutPlan = null }) {
                    Text("Close")
                }
            }
        )
    }

    // Dialog: PayPal Automatic Account Payment
    if (showPayPalDialog != null) {
        val target = showPayPalDialog!!
        val amountClean = target.price.removePrefix("$").trim()

        AlertDialog(
            onDismissRequest = {
                if (!isProcessingPayPal) showPayPalDialog = null
            },
            icon = {
                Icon(Icons.Default.Payment, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
            },
            title = {
                Text("PayPal Automatic Payment", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Upgrade to ${target.name} Membership for ${target.price} USD.\nPayPal provides instant, automatic account upgrade upon payment.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Membership:", fontSize = 13.sp)
                                Text(target.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Total Amount:", fontSize = 13.sp)
                                Text("${target.price} USD", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    if (isProcessingPayPal) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Connecting to PayPal...", fontSize = 12.sp)
                        }
                    }

                    if (payPalStatusMessage != null) {
                        Text(
                            text = payPalStatusMessage!!,
                            fontSize = 12.sp,
                            color = SuccessGreen,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isProcessingPayPal = true
                        coroutineScope.launch {
                            val result = PayPalClient.createOrder(target.name, amountClean)
                            isProcessingPayPal = false
                            when (result) {
                                is PayPalClient.PayPalOrderResult.Created -> {
                                    payPalOrderId = result.orderId
                                    payPalApprovalUrl = result.approvalUrl
                                    // Open PayPal web checkout in browser
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(result.approvalUrl))
                                        context.startActivity(intent)
                                        payPalStatusMessage = "Opened PayPal checkout in browser. Complete payment and tap 'I Have Paid' below."
                                    } catch (_: Exception) {
                                        Toast.makeText(context, "Cannot open browser. URL: ${result.approvalUrl}", Toast.LENGTH_LONG).show()
                                    }
                                }
                                is PayPalClient.PayPalOrderResult.DirectUrl -> {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(result.checkoutUrl))
                                        context.startActivity(intent)
                                        payPalStatusMessage = "Opened PayPal checkout. After payment, your tier activates."
                                    } catch (_: Exception) {}
                                }
                                is PayPalClient.PayPalOrderResult.Error -> {
                                    Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    },
                    enabled = !isProcessingPayPal,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Proceed to PayPal")
                }
            },
            dismissButton = {
                Row {
                    if (payPalOrderId != null || payPalApprovalUrl != null) {
                        TextButton(
                            onClick = {
                                // Automatic activation confirmation
                                onSelectPlan(target.name)
                                upgradedPlanName = target.name
                                showPayPalDialog = null
                                payPalOrderId = null
                                payPalApprovalUrl = null
                                payPalStatusMessage = null
                            }
                        ) {
                            Text("I Have Paid (Verify & Activate)", color = SuccessGreen, fontWeight = FontWeight.Bold)
                        }
                    }
                    TextButton(
                        onClick = {
                            showPayPalDialog = null
                            payPalOrderId = null
                            payPalApprovalUrl = null
                            payPalStatusMessage = null
                        }
                    ) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    // Dialog: Google Pay / UPI QR Code & Imgchest Screenshot Upload
    if (showGooglePayDialog != null) {
        val target = showGooglePayDialog!!
        val inrAmount = if (target.name == "Pro") "150" else "500"
        val upiUri = "upi://pay?pa=$UPI_ID&pn=${Uri.encode(UPI_NAME)}&am=$inrAmount&cu=INR&tn=${Uri.encode("JUSTFAN ${target.name} Membership")}"

        val qrBitmap = remember(upiUri) {
            QrCodeGenerator.generateQrBitmap(upiUri, size = 512)
        }

        AlertDialog(
            onDismissRequest = {
                if (!isUploadingToImgchest) {
                    showGooglePayDialog = null
                    selectedProofUri = null
                    uploadedImgchestUrl = null
                    uploadErrorMessage = null
                    userPaymentNote = ""
                }
            },
            icon = {
                Icon(Icons.Default.QrCode2, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(36.dp))
            },
            title = {
                Text("Google Pay / UPI Payment", fontWeight = FontWeight.Bold)
            },
            text = {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp)
                ) {
                    item {
                        Surface(
                            color = SuccessGreen.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Amount Due:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("₹$inrAmount (Rs $inrAmount)", fontWeight = FontWeight.Black, fontSize = 16.sp, color = SuccessGreen)
                            }
                        }
                    }

                    // UPI QR Code
                    item {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (qrBitmap != null) {
                                Surface(
                                    color = Color.White,
                                    shape = RoundedCornerShape(12.dp),
                                    border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFE0E0E0)),
                                    modifier = Modifier.size(210.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(8.dp)) {
                                        Image(
                                            bitmap = qrBitmap.asImageBitmap(),
                                            contentDescription = "Google Pay UPI QR Code",
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Scan to pay with any UPI app (GPay, PhonePe, Paytm)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    // UPI ID Box with Copy Button
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("UPI ID: $UPI_ID", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text("Name: $UPI_NAME • $UPI_BANK_DETAILS", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("UPI ID", UPI_ID)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "UPI ID copied to clipboard!", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy UPI ID", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }

                    // Quick Pay Intent button (opens Google Pay or installed UPI app directly)
                    item {
                        OutlinedButton(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(upiUri))
                                    context.startActivity(Intent.createChooser(intent, "Pay ₹$inrAmount with UPI"))
                                } catch (_: Exception) {
                                    Toast.makeText(context, "No UPI app installed. Please copy UPI ID or scan QR.", Toast.LENGTH_LONG).show()
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open Google Pay / UPI App Directly", fontSize = 12.sp)
                        }
                    }

                    // Upload Screenshot Section (Imgchest)
                    item {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Text(
                            text = "📸 Upload Payment Screenshot (Imgchest):",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "After completing the payment in Google Pay, take a screenshot and upload it here so the administrator can verify and upgrade your account.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    item {
                        if (uploadedImgchestUrl != null) {
                            Card(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = uploadedImgchestUrl,
                                        contentDescription = "Uploaded Screenshot",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(54.dp).clip(RoundedCornerShape(8.dp))
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Uploaded to Imgchest", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = SuccessGreen)
                                        }
                                        Text(uploadedImgchestUrl!!, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                                    }
                                    IconButton(
                                        onClick = {
                                            uploadedImgchestUrl = null
                                            selectedProofUri = null
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        } else {
                            Button(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                enabled = !isUploadingToImgchest,
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.primary),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (isUploadingToImgchest) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Uploading to Imgchest...", fontSize = 12.sp)
                                } else {
                                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Choose Screenshot from Gallery", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        uploadErrorMessage?.let { errMsg ->
                            Text(errMsg, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                        }
                    }

                    item {
                        OutlinedTextField(
                            value = userPaymentNote,
                            onValueChange = { userPaymentNote = it },
                            label = { Text("Transaction Reference / UTR (Optional)") },
                            placeholder = { Text("e.g. UTR / UPI Ref: 1234567890") },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val proofUrl = uploadedImgchestUrl.orEmpty()
                        onSubmitPaymentProof(
                            target.name,
                            "Google Pay UPI",
                            "Rs $inrAmount",
                            proofUrl,
                            userPaymentNote.trim()
                        )
                        showGooglePayDialog = null
                        selectedProofUri = null
                        uploadedImgchestUrl = null
                        proofSubmittedSuccessfully = true
                    },
                    enabled = uploadedImgchestUrl != null && !isUploadingToImgchest,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                ) {
                    Text("Submit for Admin Verification")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showGooglePayDialog = null
                        selectedProofUri = null
                        uploadedImgchestUrl = null
                        uploadErrorMessage = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Success confirmation dialog for Google Pay screenshot
    if (proofSubmittedSuccessfully) {
        AlertDialog(
            onDismissRequest = { proofSubmittedSuccessfully = false },
            icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(36.dp)) },
            title = { Text("Payment Proof Submitted!", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "Your payment screenshot has been uploaded to Imgchest and submitted to the administrator.\n\nThe administrator will verify the transaction on Google Pay and upgrade your account to your selected tier shortly.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(onClick = { proofSubmittedSuccessfully = false }) {
                    Text("Done")
                }
            }
        )
    }

    if (upgradedPlanName != null) {
        AlertDialog(
            onDismissRequest = { upgradedPlanName = null },
            icon = { Icon(Icons.Default.Star, contentDescription = null, tint = GoldAccent) },
            title = { Text("🎉 Membership Upgraded!") },
            text = {
                Text("Your payment was processed successfully and your account has been upgraded to $upgradedPlanName.\n\nDirect Download mirrors and unthrottled downloads are now fully unlocked!")
            },
            confirmButton = {
                Button(onClick = { upgradedPlanName = null }) {
                    Text("Start Downloading")
                }
            }
        )
    }
}
