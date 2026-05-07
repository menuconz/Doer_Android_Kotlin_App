package nz.co.doer.ui.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ShareLocation
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import nz.co.doer.R
import nz.co.doer.data.local.PreferencesManager

private val DrawerBackground = Color(0xFF667685)

data class DrawerMenuItem(
    val title: String,
    val icon: ImageVector,
    val route: String,
    val requiresAdmin: Boolean = false,
    val requiresManager: Boolean = false
)

@Composable
fun DrawerContent(
    preferencesManager: PreferencesManager,
    currentRoute: String,
    onNavigate: (String) -> Unit,
    onLogout: () -> Unit,
    boardConfigViewModel: BoardConfigViewModel = hiltViewModel()
) {
    val isAdmin by preferencesManager.isAdmin.collectAsState(initial = false)
    val isManager by preferencesManager.isManager.collectAsState(initial = false)
    val fullName by preferencesManager.fullName.collectAsState(initial = "")
    val email by preferencesManager.email.collectAsState(initial = "")
    val activeBoard by boardConfigViewModel.activeBoard.collectAsState()
    val boardName = activeBoard?.name ?: "NZ Mahi 2026"

    val menuItems = buildList {
        add(DrawerMenuItem("Home", Icons.Default.CalendarMonth, "calendar"))
        add(DrawerMenuItem(boardName, Icons.Default.Work, "main_leads_jobs"))
        if (isAdmin) {
            add(DrawerMenuItem("New Leads", Icons.Default.Leaderboard, "new_leads", requiresAdmin = true))
            add(DrawerMenuItem("Quoted Leads", Icons.Default.Leaderboard, "quoted_leads", requiresAdmin = true))
            add(DrawerMenuItem("Contacted Leads", Icons.Default.Leaderboard, "contacted_leads", requiresAdmin = true))
            add(DrawerMenuItem("Clients", Icons.Default.People, "clients", requiresAdmin = true))
            add(DrawerMenuItem("FiloKreto Team", Icons.Default.Group, "filo_kreto_team", requiresAdmin = true))
        }
        if (isAdmin || isManager) {
            add(DrawerMenuItem("Live Tracking", Icons.Default.ShareLocation, "live_tracking", requiresManager = true))
            add(DrawerMenuItem("Time Tracking", Icons.Default.Schedule, "time_tracking", requiresManager = true))
            add(DrawerMenuItem("Contractors", Icons.Default.Group, "all_contractors", requiresManager = true))
        }
        if (isAdmin || isManager) {
            add(DrawerMenuItem("Board Settings", Icons.Default.Tune, "board_settings", requiresManager = true))
            add(DrawerMenuItem("Activity Log", Icons.Default.History, "activity_log", requiresManager = true))
        }
        add(DrawerMenuItem("Profile", Icons.Default.Person, "profile"))
    }

    ModalDrawerSheet(
        modifier = Modifier.width(280.dp),
        drawerContainerColor = DrawerBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(id = R.drawable.doerlogoheader),
                    contentDescription = "Doer",
                    modifier = Modifier.height(60.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = fullName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = email,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.3f))

            // Menu items
            menuItems.forEach { item ->
                DrawerItem(
                    item = item,
                    isSelected = currentRoute == item.route,
                    onClick = { onNavigate(item.route) }
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            HorizontalDivider(color = Color.White.copy(alpha = 0.3f))

            // Logout
            DrawerItem(
                item = DrawerMenuItem("Logout", Icons.AutoMirrored.Filled.Logout, "logout"),
                isSelected = false,
                onClick = onLogout
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DrawerItem(
    item: DrawerMenuItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (isSelected) Color.White.copy(alpha = 0.15f) else Color.Transparent)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White
        )
    }
}
