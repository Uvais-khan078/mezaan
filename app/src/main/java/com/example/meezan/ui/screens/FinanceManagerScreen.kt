package com.example.meezan.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.meezan.MeezanApplication
import com.example.meezan.data.entities.*
import com.example.meezan.ui.components.MeezanErrorDialog
import com.example.meezan.ui.components.MeezanSnackbarErrorEffect
import com.example.meezan.ui.viewmodel.FinanceViewModel
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun FinanceManagerScreen(
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val appContainer = (context.applicationContext as MeezanApplication).container
    val viewModel: FinanceViewModel = viewModel(
        factory = FinanceViewModel.provideFactory(appContainer.financeRepository, appContainer.userPreferencesRepository),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val snackbarError by viewModel.snackbarError.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var showAddBudgetDialog by remember { mutableStateOf(value = false) }

    MeezanSnackbarErrorEffect(
        snackbarError = snackbarError,
        snackbarHostState = snackbarHostState,
        onClearError = viewModel::clearSnackbarError,
    )
    var showWithdrawDialog by remember { mutableStateOf(value = false) }
    var showSavingsDialog by remember { mutableStateOf(value = false) }

    val pagerState = rememberPagerState { 3 }
    val coroutineScope = rememberCoroutineScope()

    // Hierarchical Back Navigation: Status/Lending -> Ledger
    BackHandler(enabled = pagerState.currentPage != 0) {
        coroutineScope.launch {
            pagerState.animateScrollToPage(0)
        }
    }

    Scaffold(
        topBar = {
            MeezanTopBar(
                title = "Finance",
                onOpenSettings = onOpenSettings
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            SecondaryTabRow(selectedTabIndex = pagerState.currentPage) {
                Tab(
                    selected = pagerState.currentPage == 0, 
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } }, 
                    text = { Text("Ledger") }
                )
                Tab(
                    selected = pagerState.currentPage == 1, 
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } }, 
                    text = { Text("Lending") }
                )
                Tab(
                    selected = pagerState.currentPage == 2, 
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(2) } }, 
                    text = { Text("Status") }
                )
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
            ) { page ->
                when (page) {
                    0 -> LedgerPage(
                        state.transactions,
                        onAdd = { showAddBudgetDialog = true },
                        onWithdraw = { showWithdrawDialog = true },
                    )
                    1 -> LendingPage(state.lending, onClear = viewModel::clearLending)
                    2 -> StatusPage(state.profile, state.todaysSavings, onSavingsAction = { showSavingsDialog = true })
                }
            }
        }

        if (showAddBudgetDialog) {
            AddBudgetDialog(
                onDismiss = { showAddBudgetDialog = false },
                onAdd = { amount, source ->
                    viewModel.addBudget(amount, source)
                    showAddBudgetDialog = false
                }
            )
        }

        if (showWithdrawDialog) {
            WithdrawDialog(
                savedNames = state.savedNames,
                onDismiss = { showWithdrawDialog = false },
                onConfirm = { amount, reason, isSplit, isDebt, people ->
                    viewModel.withdraw(amount, reason, isSplit, isDebt, people)
                    showWithdrawDialog = false
                }
            )
        }
        
        if (showSavingsDialog) {
            SavingsActionDialog(
                currentSavings = state.profile?.totalSavings ?: 0.0,
                onDismiss = { showSavingsDialog = false },
                onDeposit = viewModel::addToSavings,
                onWithdraw = viewModel::transferFromSavings
            )
        }

        error?.let { appError ->
            MeezanErrorDialog(
                error = appError,
                onDismiss = viewModel::clearError,
                onAction = { viewModel.clearError() }
            )
        }
    }
}

@Composable
fun LedgerPage(transactions: List<FinanceTransaction>, onAdd: () -> Unit, onWithdraw: () -> Unit) {
    var selectedTx by remember { mutableStateOf<FinanceTransaction?>(null) }
    
    Box(modifier = Modifier.fillMaxSize()) {
        if (transactions.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                MeezanEmptyState(
                    message = "No transactions yet. Add your first budget or withdrawal to start tracking.",
                    icon = Icons.Default.AccountBalanceWallet
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(transactions, key = { it.id }) { tx ->
                    Box(Modifier.animateItem()) {
                        TransactionItem(tx, onClick = { selectedTx = tx })
                    }
                }
            }
        }
        
        Column(
            modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            FloatingActionButton(onClick = onAdd, containerColor = Color(0xFF4CAF50), contentColor = Color.White) {
                Icon(Icons.Default.Add, contentDescription = "Add Budget")
            }
            FloatingActionButton(onClick = onWithdraw, containerColor = MaterialTheme.colorScheme.error, contentColor = Color.White) {
                Icon(Icons.Default.Remove, contentDescription = "Withdraw")
            }
        }
        
        selectedTx?.let { tx ->
            AlertDialog(
                onDismissRequest = { selectedTx = null },
                title = { Text("Details") },
                text = {
                    Column {
                        Text("Amount: ₹${tx.amount.toInt()}", fontWeight = FontWeight.Bold)
                        Text("Type: ${tx.type}")
                        Text("Reason: ${tx.sourceOrReason}")
                        val sdf = java.text.SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())
                        Text("Date: ${sdf.format(tx.date)}")
                    }
                },
                confirmButton = { TextButton(onClick = { selectedTx = null }) { Text("Close") } }
            )
        }
    }
}

@Composable
fun TransactionItem(tx: FinanceTransaction, onClick: () -> Unit) {
    val color = when (tx.type) {
        TransactionType.INCOME -> Color(0xFF4CAF50)
        TransactionType.WITHDRAWAL -> Color.Red
        TransactionType.SAVINGS_WITHDRAWAL -> MaterialTheme.colorScheme.tertiary
        TransactionType.SAVINGS_DEPOSIT -> MaterialTheme.colorScheme.secondary
        TransactionType.LENDING -> MaterialTheme.colorScheme.primary
    }
    
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(tx.sourceOrReason, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                if (tx.isFromSavings) {
                    Text("From Savings", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                }
            }
            Text(
                text = "₹${tx.amount.toInt()}",
                color = color,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
fun LendingPage(records: List<LendingRecord>, onClear: (LendingRecord) -> Unit) {
    var expandedRecordId by remember { mutableLongStateOf(-1L) }
    
    if (records.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            MeezanEmptyState(
                message = "No active splits or debts. All clear!",
                icon = Icons.Default.CheckCircle
            )
        }
    } else {
        val totalOwed = remember(records) { records.sumOf { it.amount } }
        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                ) {
                    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Total Money Owed to You", style = MaterialTheme.typography.labelSmall)
                        Text("₹${totalOwed.toInt()}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
            
            items(records, key = { it.id }) { record ->
                Box(Modifier.animateItem()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { expandedRecordId = if(expandedRecordId == record.id) -1L else record.id }
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(record.personName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("₹${record.amount.toInt()}", color = MaterialTheme.colorScheme.primary)
                            }
                            Text(record.type.toString(), style = MaterialTheme.typography.labelSmall)
                            
                            if (expandedRecordId == record.id) {
                                Spacer(Modifier.height(8.dp))
                                Text("Reason: ${record.reason}", style = MaterialTheme.typography.bodySmall)
                                Text("Date: ${java.text.SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(record.date)}", style = MaterialTheme.typography.labelSmall)
                                Spacer(Modifier.height(12.dp))
                                Button(onClick = { onClear(record) }, modifier = Modifier.fillMaxWidth()) {
                                    Text("Clear ${record.type}")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusPage(profile: FinanceProfile?, todaysSavings: Double, onSavingsAction: () -> Unit) {
    val data = profile ?: FinanceProfile()
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatusCard(
                    label = "Monthly Budget", 
                    value = "₹${data.totalMonthlyBudget.toInt()}",
                    modifier = Modifier.weight(1f)
                )
                StatusCard(
                    label = "Daily Limit", 
                    value = "₹${data.dailyLimit.toInt()}",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatusCard(
                    label = "Today's Saving", 
                    value = "₹${todaysSavings.toInt()}",
                    color = Color(0xFF4CAF50),
                    modifier = Modifier.weight(1f)
                )
                StatusCard(
                    label = "Total Savings", 
                    value = "₹${data.totalSavings.toInt()}",
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item {
            Button(
                onClick = onSavingsAction, 
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Default.AccountBalanceWallet, null)
                Spacer(Modifier.width(8.dp))
                Text("Manage Savings")
            }
        }
    }
}

@Composable
fun StatusCard(
    label: String, 
    value: String, 
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Card(
        modifier = modifier, 
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.8f))
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = color)
        }
    }
}

@Composable
fun AddBudgetDialog(onDismiss: () -> Unit, onAdd: (Double, String) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var source by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Budget") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Amount") })
                OutlinedTextField(value = source, onValueChange = { source = it }, label = { Text("Source") })
            }
        },
        confirmButton = {
            Button(onClick = { onAdd(amount.toDoubleOrNull() ?: 0.0, source) }) { Text("Submit") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WithdrawDialog(
    savedNames: List<String>,
    onDismiss: () -> Unit, 
    onConfirm: (Double, String, Boolean, Boolean, List<String>) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var isSplit by remember { mutableStateOf(false) }
    var isDebt by remember { mutableStateOf(false) }
    var personName by remember { mutableStateOf("") }
    var selectedPeople by remember { mutableStateOf(setOf<String>()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Withdrawal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Amount") })
                OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("Reason") })
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PulseCheckbox(checked = isSplit, onCheckedChange = { isSplit = it; if(it) isDebt = false })
                    Text("Split Bill", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(16.dp))
                    PulseCheckbox(checked = isDebt, onCheckedChange = { isDebt = it; if(it) isSplit = false })
                    Text("Give as Debt", style = MaterialTheme.typography.bodyMedium)
                }
                
                if (isSplit || isDebt) {
                    Text("People:", style = MaterialTheme.typography.labelSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        savedNames.forEach { name ->
                            FilterChip(
                                selected = selectedPeople.contains(name),
                                onClick = {
                                    selectedPeople = if(selectedPeople.contains(name)) selectedPeople - name else selectedPeople + name
                                },
                                label = { Text(name) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = personName, 
                        onValueChange = { personName = it }, 
                        label = { Text("New Name") },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    if (personName.isNotBlank()) {
                                        selectedPeople += personName
                                        personName = ""
                                    }
                                },
                            ) {
                                Icon(Icons.Default.Add, null)
                            }
                        }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { 
                    onConfirm(amount.toDoubleOrNull() ?: 0.0, reason, isSplit, isDebt, selectedPeople.toList()) 
                },
                enabled = (amount.toDoubleOrNull() != null) && reason.isNotBlank(),
            ) { Text("Confirm") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun SavingsActionDialog(currentSavings: Double, onDismiss: () -> Unit, onDeposit: (Double) -> Unit, onWithdraw: (Double) -> Unit) {
    var amount by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage Savings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Total Savings: ₹${currentSavings.toInt()}", color = MaterialTheme.colorScheme.primary)
                OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Amount") })
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onWithdraw(amount.toDoubleOrNull() ?: 0.0); onDismiss() }) { Text("Move to Budget") }
                Button(onClick = { onDeposit(amount.toDoubleOrNull() ?: 0.0); onDismiss() }) { Text("Add to Savings") }
            }
        }
    )
}
