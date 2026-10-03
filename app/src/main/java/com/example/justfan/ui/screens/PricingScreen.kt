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
            "3 community requests per week" to true,
            "Ad-free viewing" to false,
            "Unlimited requests" to false,
            "Direct 4K ZIP Downloads" to false
        )
    ),
    PlanData(
        name = "Pro",
        price = "$5",
        period = "/month",
        description = "Unlimited requests & fast 4K downloads",
        popular = true,
        features = listOf(
            "Browse all public content" to true,
            "Search & filter galleries" to true,
            "Save bookmarks & favorites" to true,
            "Unlimited requests for 1 month" to true,
            "100% Ad-free browsing" to true,
            "Direct 4K ZIP Downloads" to true,
            "Priority request fulfillment" to true
        )
    ),
    PlanData(
        name = "Legendary",
        price = "$15",
        period = "one-time",
        description = "Lifetime unlimited VIP access forever",
        popular = false,
        features = listOf(
            "Browse all public content" to true,
            "Search & filter galleries" to true,
            "Save bookmarks & favorites" to true,
            "Unlimited requests forever" to true,
            "100% Ad-free browsing forever" to true,
            "Direct 4K Ultra HD Downloads" to true,
            "Top-tier priority VIP fulfillment" to true,
            "Creator supporter badge" to true
        )
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PricingScreen(
    currentPlan: String,
    onSelectPlan: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var upgradedPlanName by remember { mutableStateOf<String?>(null) }

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

                        Button(
                            onClick = {
                                onSelectPlan(plan.name)
                                upgradedPlanName = plan.name
                            },
                            enabled = !isCurrent,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (plan.name == "Legendary") GoldAccent else MaterialTheme.colorScheme.primary,
                                contentColor = if (plan.name == "Legendary") Color.Black else MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier
                                .testTag("btn_select_plan_${plan.name.lowercase()}")
                                .fillMaxWidth()
                        ) {
                            Text(
                                text = if (isCurrent) "Current Plan" else "Select ${plan.name}",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    if (upgradedPlanName != null) {
        AlertDialog(
            onDismissRequest = { upgradedPlanName = null },
            icon = { Icon(Icons.Default.Star, contentDescription = null, tint = GoldAccent) },
            title = { Text("Plan Updated!") },
            text = { Text("Your account membership was updated to $upgradedPlanName successfully!") },
            confirmButton = {
                Button(onClick = { upgradedPlanName = null }) {
                    Text("Awesome")
                }
            }
        )
    }
}
