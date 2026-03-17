package nz.co.doer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme

import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nz.co.doer.data.remote.ApiResult
import nz.co.doer.data.repository.ShiftRepository

import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import nz.co.doer.data.local.PreferencesManager
import java.net.URLEncoder
import nz.co.doer.data.local.SecureStorageManager
import nz.co.doer.ui.auth.ForgotPasswordScreen
import nz.co.doer.ui.auth.GenerateOTPScreen
import nz.co.doer.ui.auth.LoadingScreen
import nz.co.doer.ui.auth.LoginScreen
import nz.co.doer.ui.auth.RegisterContractorScreen
import nz.co.doer.ui.auth.RegisterManagerScreen
import nz.co.doer.ui.calendar.AddShiftScreen
import nz.co.doer.ui.calendar.CalendarScreen
import nz.co.doer.ui.calendar.DayDetailScreen
import nz.co.doer.ui.calendar.DayTimelineScreen
import nz.co.doer.ui.calendar.ShiftDetailsScreen
import nz.co.doer.ui.clients.AddNewClientScreen
import nz.co.doer.ui.clients.ClientsScreen
import nz.co.doer.ui.contractors.AllContractorsScreen
import nz.co.doer.ui.contractors.ContractorDetailsScreen
import nz.co.doer.ui.feedback.ReviewsScreen
import nz.co.doer.ui.feedback.SendFeedbackScreen
import nz.co.doer.ui.files.ShiftFilesScreen
import nz.co.doer.ui.files.SubItemFilesScreen
import nz.co.doer.ui.files.ViewDocumentScreen
import nz.co.doer.ui.leads.AddNewLeadScreen
import nz.co.doer.ui.leads.ContactedLeadsScreen
import nz.co.doer.ui.leads.NewLeadsScreen
import nz.co.doer.ui.leads.QuotedLeadsScreen
import nz.co.doer.ui.leads.ViewLeadScreen
import nz.co.doer.ui.main.DrawerContent
import nz.co.doer.ui.mainleads.MainLeadsJobsScreen
import nz.co.doer.ui.messages.MessagesScreen
import nz.co.doer.ui.messages.SubItemMessagesScreen
import nz.co.doer.ui.messages.ViewEmailDocumentScreen
import nz.co.doer.ui.navigation.Routes
import nz.co.doer.ui.notifications.NotificationsScreen
import nz.co.doer.ui.profile.EditProfileScreen
import nz.co.doer.ui.profile.ProfileScreen
import nz.co.doer.ui.quotation.SendQuoteScreen
import nz.co.doer.ui.quotation.ViewQuotationsScreen
import nz.co.doer.ui.team.FiloKretoTeamScreen

// Routes that show the drawer hamburger menu
private val DRAWER_ROUTES = setOf(
    Routes.CALENDAR,
    Routes.MAIN_LEADS_JOBS,
    Routes.NEW_LEADS,
    Routes.QUOTED_LEADS,
    Routes.CONTACTED_LEADS,
    Routes.CLIENTS,
    Routes.ALL_CONTRACTORS,
    Routes.PROFILE,
    Routes.FILO_KRETO_TEAM,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoerNavHost(
    secureStorageManager: SecureStorageManager,
    preferencesManager: PreferencesManager,
    shiftRepository: ShiftRepository,
    activity: MainActivity? = null
) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: ""

    val isDrawerRoute = DRAWER_ROUTES.contains(currentRoute)

    // Close drawer when navigating away from drawer routes
    LaunchedEffect(currentRoute) {
        if (!isDrawerRoute && drawerState.isOpen) {
            drawerState.close()
        }
    }

    // Matching MAUI: NotificationTapped → navigate to NotificationView
    // Handle pending navigation from notification tap
    if (activity != null) {
        val pendingNav by activity.pendingNavigation.collectAsState()
        LaunchedEffect(pendingNav) {
            if (pendingNav == "notifications") {
                // Only navigate if user is logged in and not already on notifications
                if (secureStorageManager.isLoggedIn && currentRoute != Routes.NOTIFICATIONS) {
                    navController.navigate(Routes.NOTIFICATIONS)
                }
                activity.clearPendingNavigation()
            }
        }
    }

    // Helper for logout logic
    val doLogout: () -> Unit = {
        scope.launch {
            secureStorageManager.isLoggedIn = false
            secureStorageManager.clear()
            preferencesManager.clearSession()
            navController.navigate(Routes.LOGIN) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = isDrawerRoute,
        drawerContent = {
            DrawerContent(
                preferencesManager = preferencesManager,
                currentRoute = currentRoute,
                onNavigate = { route ->
                    scope.launch { drawerState.close() }
                    navController.navigate(route) { launchSingleTop = true }
                },
                onLogout = {
                    scope.launch { drawerState.close() }
                    doLogout()
                }
            )
        }
    ) {
        NavHost(
            navController = navController,
            startDestination = Routes.LOADING
        ) {
            // ===================== AUTH ROUTES =====================

            composable(Routes.LOADING) {
                LoadingScreen(
                    secureStorageManager = secureStorageManager,
                    onLoggedIn = {
                        navController.navigate(Routes.CALENDAR) {
                            popUpTo(Routes.LOADING) { inclusive = true }
                        }
                    },
                    onNotLoggedIn = {
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(Routes.LOADING) { inclusive = true }
                        }
                    }
                )
            }

            composable(Routes.LOGIN) {
                LoginScreen(
                    onLoginSuccess = {
                        navController.navigate(Routes.CALENDAR) {
                            popUpTo(Routes.LOGIN) { inclusive = true }
                        }
                    },
                    onForgotPassword = { navController.navigate(Routes.FORGOT_PASSWORD) },
                    onRegisterContractor = { navController.navigate(Routes.REGISTER_CONTRACTOR) },
                    onRegisterManager = { navController.navigate(Routes.REGISTER_REST_HOME) }
                )
            }

            composable(Routes.REGISTER_CONTRACTOR) {
                RegisterContractorScreen(
                    onBack = { navController.popBackStack() },
                    onSuccess = { navController.popBackStack(Routes.LOGIN, inclusive = false) }
                )
            }

            composable(Routes.REGISTER_REST_HOME) {
                RegisterManagerScreen(
                    onBack = { navController.popBackStack() },
                    onSuccess = { navController.popBackStack(Routes.LOGIN, inclusive = false) }
                )
            }

            composable(Routes.FORGOT_PASSWORD) {
                ForgotPasswordScreen(
                    onBack = { navController.popBackStack() },
                    onResetSuccess = { navController.popBackStack(Routes.LOGIN, inclusive = false) }
                )
            }

            composable(Routes.GENERATE_OTP) {
                GenerateOTPScreen(
                    onBack = { navController.popBackStack() },
                    onSuccess = { navController.navigate(Routes.FORGOT_PASSWORD) }
                )
            }

            // ===================== CALENDAR & SHIFTS =====================

            composable(Routes.CALENDAR) {
                // Fetch unread notification count
                val unreadCount = remember { mutableIntStateOf(0) }
                LaunchedEffect(Unit) {
                    val userId = preferencesManager.getUserId()
                    when (val result = shiftRepository.getUserAllNotificationsById(userId)) {
                        is ApiResult.Success -> {
                            unreadCount.intValue = result.data.count { !it.isRead }
                        }
                        else -> {}
                    }
                }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Calendar", color = Color.White) },
                            navigationIcon = {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color.White)
                                }
                            },
                            actions = {
                                IconButton(onClick = {
                                    navController.navigate(Routes.NOTIFICATIONS)
                                }) {
                                    BadgedBox(
                                        badge = {
                                            if (unreadCount.intValue > 0) {
                                                Badge(
                                                    containerColor = Color(0xFFFF3B30)
                                                ) {
                                                    Text(
                                                        text = if (unreadCount.intValue > 99) "99+" else unreadCount.intValue.toString(),
                                                        color = Color.White,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            Icons.Default.Notifications,
                                            contentDescription = "Notifications",
                                            tint = Color.White
                                        )
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                ) { padding ->
                    CalendarScreen(
                        modifier = Modifier.padding(padding),
                        onDayTapped = { dateStr ->
                            navController.navigate("${Routes.DAY_TIMELINE}/$dateStr")
                        },
                        onDayLongPressed = { dateStr ->
                            navController.navigate("${Routes.ADD_SHIFT}/$dateStr")
                        }
                    )
                }
            }

            composable("${Routes.DAY_TIMELINE}/{date}") {
                DayTimelineScreen(
                    onBack = { navController.popBackStack() },
                    onShiftTapped = { dateStr, shiftId ->
                        navController.navigate("${Routes.DAY_DETAIL}/$dateStr?shiftId=$shiftId")
                    },
                    onViewAll = { dateStr ->
                        navController.navigate("${Routes.DAY_DETAIL}/$dateStr")
                    },
                    onAddShift = { dateStr, hour ->
                        val route = if (hour != null) {
                            "${Routes.ADD_SHIFT}/$dateStr?hour=$hour"
                        } else {
                            "${Routes.ADD_SHIFT}/$dateStr"
                        }
                        navController.navigate(route)
                    }
                )
            }

            composable("${Routes.DAY_DETAIL}/{date}?shiftId={shiftId}") {
                DayDetailScreen(
                    onBack = { navController.popBackStack() },
                    onViewDetails = { shiftId ->
                        navController.navigate("${Routes.SHIFT_DETAILS}/$shiftId")
                    },
                    onViewFiles = { shiftId ->
                        navController.navigate("${Routes.SHIFT_FILES}/$shiftId")
                    },
                    onViewQuotations = { shiftId ->
                        navController.navigate("${Routes.VIEW_QUOTATIONS}/$shiftId")
                    },
                    onViewMessages = { shiftId ->
                        navController.navigate("${Routes.EMAIL_MESSAGES}/$shiftId")
                    },
                    onViewSubItemMessages = { shiftId, subItemId ->
                        navController.navigate("${Routes.SUB_ITEM_MESSAGES}/$shiftId/$subItemId")
                    },
                    onViewSubItemFiles = { shiftId, subItemId ->
                        navController.navigate("${Routes.SUB_ITEM_FILES}/$shiftId/$subItemId")
                    }
                )
            }

            composable("${Routes.ADD_SHIFT}/{date}?hour={hour}") {
                AddShiftScreen(
                    onBack = { navController.popBackStack() },
                    onSuccess = { navController.popBackStack(Routes.CALENDAR, inclusive = false) }
                )
            }

            composable("${Routes.SHIFT_DETAILS}/{shiftId}") {
                ShiftDetailsScreen(
                    onBack = { navController.popBackStack() },
                    onEdit = { shiftId ->
                        navController.navigate("${Routes.EDIT_SHIFT}/$shiftId")
                    },
                    onSendQuote = { shiftId ->
                        navController.navigate("${Routes.SEND_QUOTE}/$shiftId")
                    },
                    onViewQuotations = { shiftId ->
                        navController.navigate("${Routes.VIEW_QUOTATIONS}/$shiftId")
                    },
                    onSendFeedback = { shiftId ->
                        navController.navigate("${Routes.SEND_FEEDBACK}/$shiftId")
                    }
                )
            }

            composable("${Routes.SEND_QUOTE}/{shiftId}") {
                SendQuoteScreen(onBack = { navController.popBackStack() })
            }

            composable("${Routes.VIEW_QUOTATIONS}/{shiftId}") {
                ViewQuotationsScreen(onBack = { navController.popBackStack() })
            }

            // ===================== FILES & DOCUMENTS =====================

            composable("${Routes.SHIFT_FILES}/{shiftId}") {
                ShiftFilesScreen(
                    onBack = { navController.popBackStack() },
                    onViewDocument = { fileUrl, isImage ->
                        val encoded = URLEncoder.encode(fileUrl, "UTF-8")
                        navController.navigate("${Routes.VIEW_DOCUMENT}?fileUrl=$encoded&isImage=$isImage")
                    }
                )
            }

            composable("${Routes.SUB_ITEM_FILES}/{shiftId}/{subItemId}") {
                SubItemFilesScreen(
                    onBack = { navController.popBackStack() },
                    onViewDocument = { fileUrl, isImage ->
                        val encoded = URLEncoder.encode(fileUrl, "UTF-8")
                        navController.navigate("${Routes.VIEW_DOCUMENT}?fileUrl=$encoded&isImage=$isImage")
                    }
                )
            }

            composable("${Routes.VIEW_DOCUMENT}?fileUrl={fileUrl}&isImage={isImage}") {
                ViewDocumentScreen(onBack = { navController.popBackStack() })
            }

            // ===================== MESSAGES =====================

            composable("${Routes.EMAIL_MESSAGES}/{shiftId}") {
                MessagesScreen(
                    onBack = { navController.popBackStack() },
                    onViewAttachment = { fileUrl ->
                        val encoded = URLEncoder.encode(fileUrl, "UTF-8")
                        navController.navigate("${Routes.VIEW_EMAIL_DOCUMENT}?fileUrl=$encoded")
                    }
                )
            }

            composable("${Routes.SUB_ITEM_MESSAGES}/{shiftId}/{subItemId}") {
                SubItemMessagesScreen(
                    onBack = { navController.popBackStack() },
                    onViewAttachment = { fileUrl ->
                        val encoded = URLEncoder.encode(fileUrl, "UTF-8")
                        navController.navigate("${Routes.VIEW_EMAIL_DOCUMENT}?fileUrl=$encoded")
                    }
                )
            }

            composable("${Routes.VIEW_EMAIL_DOCUMENT}?fileUrl={fileUrl}") {
                ViewEmailDocumentScreen(onBack = { navController.popBackStack() })
            }

            // ===================== FEEDBACK & REVIEWS =====================

            composable("${Routes.SEND_FEEDBACK}/{shiftId}") {
                SendFeedbackScreen(
                    onBack = { navController.popBackStack() },
                    onSuccess = { navController.popBackStack() }
                )
            }

            composable("${Routes.REVIEWS}/{shiftId}") {
                ReviewsScreen(
                    onBack = { navController.popBackStack() },
                    onSuccess = { navController.popBackStack() }
                )
            }

            // ===================== NOTIFICATIONS =====================

            composable(Routes.NOTIFICATIONS) {
                NotificationsScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToShift = { dateStr, shiftId ->
                        navController.navigate("${Routes.DAY_DETAIL}/$dateStr?shiftId=$shiftId")
                    },
                    onNavigateToMessages = { shiftId ->
                        navController.navigate("${Routes.EMAIL_MESSAGES}/$shiftId")
                    }
                )
            }

            // ===================== PROFILE =====================

            composable(Routes.PROFILE) { backStackEntry ->
                val viewModel: nz.co.doer.ui.profile.ProfileViewModel = androidx.hilt.navigation.compose.hiltViewModel()
                val profileUpdated by backStackEntry.savedStateHandle.getStateFlow("profileUpdated", false).collectAsState()
                val profileSuccessMsg by backStackEntry.savedStateHandle.getStateFlow<String?>("profileSuccessMsg", null).collectAsState()
                LaunchedEffect(profileUpdated) {
                    if (profileUpdated) {
                        viewModel.refresh()
                        backStackEntry.savedStateHandle["profileUpdated"] = false
                    }
                }
                ProfileScreen(
                    viewModel = viewModel,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onEditProfile = { navController.navigate(Routes.EDIT_PROFILE) },
                    onDeletedAccount = {
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onViewDocument = { fileUrl, isImage ->
                        val encoded = URLEncoder.encode(fileUrl, "UTF-8")
                        navController.navigate("${Routes.VIEW_DOCUMENT}?fileUrl=$encoded&isImage=$isImage")
                    },
                    successMessage = profileSuccessMsg,
                    onSuccessMessageShown = {
                        backStackEntry.savedStateHandle["profileSuccessMsg"] = null
                    }
                )
            }

            composable(Routes.EDIT_PROFILE) {
                EditProfileScreen(
                    onBack = { navController.popBackStack() },
                    onSuccess = { message ->
                        navController.previousBackStackEntry?.savedStateHandle?.set("profileUpdated", true)
                        navController.previousBackStackEntry?.savedStateHandle?.set("profileSuccessMsg", message)
                        navController.popBackStack()
                    },
                    onViewDocument = { fileUrl, isImage ->
                        val encoded = URLEncoder.encode(fileUrl, "UTF-8")
                        navController.navigate("${Routes.VIEW_DOCUMENT}?fileUrl=$encoded&isImage=$isImage")
                    }
                )
            }

            // ===================== LEADS =====================

            composable(Routes.NEW_LEADS) { backStackEntry ->
                val viewModel: nz.co.doer.ui.leads.NewLeadsViewModel = androidx.hilt.navigation.compose.hiltViewModel()
                val leadsUpdated by backStackEntry.savedStateHandle.getStateFlow("leadsUpdated", false).collectAsState()
                LaunchedEffect(leadsUpdated) {
                    if (leadsUpdated) {
                        viewModel.refresh()
                        backStackEntry.savedStateHandle["leadsUpdated"] = false
                    }
                }
                NewLeadsScreen(
                    viewModel = viewModel,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onAddLead = { navController.navigate(Routes.ADD_NEW_LEAD) },
                    onViewLead = { leadId ->
                        navController.navigate("${Routes.LEAD_DETAIL}/$leadId")
                    }
                )
            }

            composable(Routes.QUOTED_LEADS) {
                QuotedLeadsScreen(
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onViewLead = { leadId ->
                        navController.navigate("${Routes.LEAD_DETAIL}/$leadId")
                    }
                )
            }

            composable(Routes.CONTACTED_LEADS) {
                ContactedLeadsScreen(
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onViewLead = { leadId ->
                        navController.navigate("${Routes.LEAD_DETAIL}/$leadId")
                    }
                )
            }

            composable(Routes.ADD_NEW_LEAD) {
                AddNewLeadScreen(
                    onBack = { navController.popBackStack() },
                    onSuccess = {
                        navController.previousBackStackEntry?.savedStateHandle?.set("leadsUpdated", true)
                        navController.popBackStack()
                    }
                )
            }

            composable("${Routes.LEAD_DETAIL}/{leadId}") {
                ViewLeadScreen(onBack = { navController.popBackStack() })
            }

            // ===================== CLIENTS =====================

            composable(Routes.CLIENTS) { backStackEntry ->
                val viewModel: nz.co.doer.ui.clients.ClientsViewModel = androidx.hilt.navigation.compose.hiltViewModel()
                val clientsUpdated by backStackEntry.savedStateHandle.getStateFlow("clientsUpdated", false).collectAsState()
                LaunchedEffect(clientsUpdated) {
                    if (clientsUpdated) {
                        viewModel.loadClients()
                        backStackEntry.savedStateHandle["clientsUpdated"] = false
                    }
                }
                ClientsScreen(
                    viewModel = viewModel,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onAddClient = { navController.navigate(Routes.ADD_NEW_CLIENT) }
                )
            }

            composable(Routes.ADD_NEW_CLIENT) {
                AddNewClientScreen(
                    onBack = { navController.popBackStack() },
                    onSuccess = {
                        navController.previousBackStackEntry?.savedStateHandle?.set("clientsUpdated", true)
                        navController.popBackStack()
                    }
                )
            }

            // ===================== CONTRACTORS =====================

            composable(Routes.ALL_CONTRACTORS) {
                AllContractorsScreen(
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onViewContractorDetail = { contractorId ->
                        navController.navigate("${Routes.CONTRACTOR_DETAILS}/$contractorId")
                    }
                )
            }

            composable("${Routes.CONTRACTOR_DETAILS}/{contractorId}") {
                ContractorDetailsScreen(
                    onBack = { navController.popBackStack() },
                    onViewDocument = { fileUrl, isImage ->
                        val encoded = URLEncoder.encode(fileUrl, "UTF-8")
                        navController.navigate("${Routes.VIEW_DOCUMENT}?fileUrl=$encoded&isImage=$isImage")
                    }
                )
            }

            // ===================== MAIN LEADS JOBS =====================

            composable(Routes.MAIN_LEADS_JOBS) {
                MainLeadsJobsScreen(
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onShiftDetails = { shiftId ->
                        navController.navigate("${Routes.SHIFT_DETAILS}/$shiftId")
                    },
                    onViewQuotations = { shiftId ->
                        navController.navigate("${Routes.VIEW_QUOTATIONS}/$shiftId")
                    },
                    onViewMessages = { shiftId ->
                        navController.navigate("${Routes.EMAIL_MESSAGES}/$shiftId")
                    },
                    onViewFiles = { shiftId ->
                        navController.navigate("${Routes.SHIFT_FILES}/$shiftId")
                    },
                    onViewSubItemMessages = { shiftId, subItemId ->
                        navController.navigate("${Routes.SUB_ITEM_MESSAGES}/$shiftId/$subItemId")
                    },
                    onViewSubItemFiles = { shiftId, subItemId ->
                        navController.navigate("${Routes.SUB_ITEM_FILES}/$shiftId/$subItemId")
                    }
                )
            }

            // ===================== FILO KRETO TEAM =====================

            composable(Routes.FILO_KRETO_TEAM) {
                FiloKretoTeamScreen(onOpenDrawer = { scope.launch { drawerState.open() } })
            }
        }
    }
}
