package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.firestore.model.FirestoreUser

@Composable
fun TeamAccessScreen(
    currentUser: FirestoreUser?,
    teamMembers: List<FirestoreUser>,
    onUpdateRole: (targetUserId: String, targetEmail: String, newRole: String) -> Unit,
    onSignOutClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    var userToChangeRole by remember { mutableStateOf<FirestoreUser?>(null) }
    var targetRoleToSet by remember { mutableStateOf("STAFF") }
    var showSignOutConfirm by remember { mutableStateOf(false) }

    val isAdmin = currentUser?.isAdmin == true

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))

            // Current User Session Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("current_user_profile_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        if (isAdmin) Color(0xFFF59E0B) else Color(0xFF2563EB),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    if (isAdmin) Icons.Default.AdminPanelSettings else Icons.Default.Person,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = currentUser?.displayName ?: "Online User",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = currentUser?.email ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Role Badge
                        Text(
                            text = if (isAdmin) "ADMIN" else "STAFF",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp,
                            color = Color.White,
                            modifier = Modifier
                                .background(
                                    if (isAdmin) Color(0xFFF59E0B) else Color(0xFF2563EB),
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(
                            onClick = { showSignOutConfirm = true },
                            modifier = Modifier.testTag("sign_out_button")
                        ) {
                            Icon(Icons.Default.ExitToApp, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sign Out")
                        }
                    }
                }
            }
        }

        // Permissions Overview Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Access Permissions Matrix",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Admin Privileges", fontWeight = FontWeight.Bold, color = Color(0xFFD97706), fontSize = 12.sp)
                            Text("• Manage user & staff roles", fontSize = 11.sp)
                            Text("• Add, edit & delete products", fontSize = 11.sp)
                            Text("• Adjust inventory & audit counts", fontSize = 11.sp)
                            Text("• Full cost & profit analytics", fontSize = 11.sp)
                            Text("• Cancel / delete orders", fontSize = 11.sp)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Staff Privileges", fontWeight = FontWeight.Bold, color = Color(0xFF2563EB), fontSize = 12.sp)
                            Text("• Real-time stock lookup", fontSize = 11.sp)
                            Text("• Barcode scanning & identification", fontSize = 11.sp)
                            Text("• Process customer orders & sales", fontSize = 11.sp)
                            Text("• Restock incoming inventory", fontSize = 11.sp)
                            Text("• View order receipts & history", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Team Members List Section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Shop Team Members (${teamMembers.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (isAdmin) {
                    Text(
                        text = "Admin Role Editor",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFD97706),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        items(teamMembers, key = { it.userId.ifBlank { it.id } }) { member ->
            val isMemberAdmin = member.isAdmin
            val isCurrentAccount = member.userId == currentUser?.userId

            Card(
                modifier = Modifier.fillMaxWidth().testTag("team_card_${member.userId}"),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(
                                    if (isMemberAdmin) Color(0xFFFEF3C7) else Color(0xFFDBEAFE),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = member.displayName.take(1).uppercase(),
                                fontWeight = FontWeight.Bold,
                                color = if (isMemberAdmin) Color(0xFFD97706) else Color(0xFF2563EB)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = member.displayName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                if (isCurrentAccount) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "(You)",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Text(
                                text = member.email,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isMemberAdmin) "ADMIN" else "STAFF",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isMemberAdmin) Color(0xFFD97706) else Color(0xFF2563EB),
                            modifier = Modifier
                                .background(
                                    if (isMemberAdmin) Color(0xFFFEF3C7) else Color(0xFFDBEAFE),
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )

                        // Role toggle action available exclusively to Admins (cannot demote self)
                        if (isAdmin && !isCurrentAccount) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    userToChangeRole = member
                                    targetRoleToSet = if (isMemberAdmin) "STAFF" else "ADMIN"
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isMemberAdmin) MaterialTheme.colorScheme.surfaceVariant else Color(0xFFFEF3C7),
                                    contentColor = if (isMemberAdmin) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFF92400E)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(34.dp).testTag("toggle_role_${member.userId}")
                            ) {
                                Text(
                                    text = if (isMemberAdmin) "Make Staff" else "Make Admin",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(72.dp))
        }
    }

    // Role Modification Confirmation Dialog
    userToChangeRole?.let { member ->
        AlertDialog(
            onDismissRequest = { userToChangeRole = null },
            title = { Text("Change User Access Role?") },
            text = {
                Text(
                    text = "Are you sure you want to change '${member.displayName}' to $targetRoleToSet role?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateRole(member.userId, member.email, targetRoleToSet)
                        userToChangeRole = null
                    }
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { userToChangeRole = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Sign Out Confirmation Dialog
    if (showSignOutConfirm) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirm = false },
            title = { Text("Sign Out?") },
            text = { Text("Are you sure you want to sign out of ShopTrack?") },
            confirmButton = {
                Button(
                    onClick = {
                        showSignOutConfirm = false
                        onSignOutClicked()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Sign Out")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
