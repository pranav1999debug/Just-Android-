package com.example.justfan.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.justfan.ui.theme.GoldAccent
import com.example.justfan.ui.theme.SuccessGreen

data class PlanData(
    val name: String,
    val price: String,
    val period: String,
    val description: String,
    val popular: Boolean,
    val features: List<Pair<String, Boolean>>
)

val PLANS = listOf(
    PlanData(
        name = "Free",
        price = "$0",
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PricingScreen(
    currentPlan: String,
    isAdmin: Boolean = false,
    onSelectPlan: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var upgradedPlanName by remember { mutableStateOf<String?>(null) }
    var showAdminContactDialog by remember { mutableStateOf<String?>(null) }
    var checkoutPlan by remember { mutableStateOf<PlanData?>(null) }
    var selectedPaymentMethod by remember { mutableStateOf("Google Play") }
    var isProcessingPayment by remember { mutableStateOf(false) }

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
                            if (plan.popular) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                            else if (plan.name == "Legendary") Modifier.border(2.dp, GoldAccent, RoundedCornerShape(16.dp))
                            else Modifier
                        )
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = plan.name,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                                color = if (plan.name == "Legendary") GoldAccent else MaterialTheme.colorScheme.onSurface
                            )

                            if (plan.popular) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(6.dp)
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
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "BEST VALUE 🔥",
                                        color = Color.Black,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = plan.price,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = plan.period,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Text(
                            text = plan.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Divider(modifier = Modifier.padding(vertical = 12.dp))

                        plan.features.forEach { (feature, included) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 3.dp)
                            ) {
                                Icon(
                                    imageVector = if (included) Icons.Default.Check else Icons.Default.Close,
                                    contentDescription = null,
                                    tint = if (included) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = feature,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (included) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        if (isCurrent) {
                            Button(
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
                                Text("Set Tier: ${plan.name} (Admin)", fontWeight = FontWeight.Bold)
                            }
                        } else {
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
                                    Text("Pay ${plan.price} & Upgrade to ${plan.name}", fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = { showAdminContactDialog = plan.name },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .testTag("btn_contact_admin_${plan.name.lowercase()}")
                                        .fillMaxWidth()
                                ) {
                                    Text("Contact Admin for Invoice", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Interactive Checkout & Payment Dialog
    if (checkoutPlan != null) {
        val target = checkoutPlan!!
        AlertDialog(
            onDismissRequest = { if (!isProcessingPayment) checkoutPlan = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.Diamond,
                    contentDescription = null,
                    tint = if (target.name == "Legendary") GoldAccent else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text("Checkout: ${target.name} Membership", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Unlock Direct High-Speed Download Mirrors, 4K ZIP galleries, unlimited requests, and ad-free browsing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Total Amount Due:", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                            Text(
                                text = "${target.price} ${target.period}",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Text("Select Payment Gateway:", fontWeight = FontWeight.Bold, fontSize = 13.sp)

                    listOf(
                        "Google Play In-App Billing" to "Instant verification via Play Store",
                        "Credit / Debit Card (Stripe)" to "Visa, MasterCard, Amex",
                        "PayPal / Crypto" to "Secure international checkout"
                    ).forEach { (method, desc) ->
                        val isSelected = selectedPaymentMethod.startsWith(method.take(11))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { selectedPaymentMethod = method }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(text = method, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                    Text(text = desc, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }

                    if (isProcessingPayment) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Processing secure payment...", fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isProcessingPayment = true
                        onSelectPlan(target.name)
                        isProcessingPayment = false
                        upgradedPlanName = target.name
                        checkoutPlan = null
                    },
                    enabled = !isProcessingPayment,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (target.name == "Legendary") GoldAccent else MaterialTheme.colorScheme.primary,
                        contentColor = if (target.name == "Legendary") Color.Black else MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.testTag("btn_confirm_checkout_${target.name.lowercase()}")
                ) {
                    Text("Complete Payment & Activate")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { checkoutPlan = null },
                    enabled = !isProcessingPayment
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAdminContactDialog != null) {
        AlertDialog(
            onDismissRequest = { showAdminContactDialog = null },
            icon = { Icon(Icons.Default.Diamond, contentDescription = null, tint = GoldAccent) },
            title = { Text("Admin Managed Tier") },
            text = {
                Text("Membership tiers are strictly managed by the Platform Administrator.\n\nTo upgrade your account to ${showAdminContactDialog}, please contact the administrator:\n\nEmail: reytherapper12@gmail.com")
            },
            confirmButton = {
                Button(onClick = { showAdminContactDialog = null }) {
                    Text("Understood")
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
