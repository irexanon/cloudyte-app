package com.cloudyte.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.cloudyte.app.R
import com.cloudyte.app.data.EvaluateExclusions
import com.cloudyte.app.data.ExclusionSizeUnit
import com.cloudyte.app.data.ExclusionValidationResult
import com.cloudyte.app.data.formatStorageSize
import com.cloudyte.app.db.AppDatabase
import com.cloudyte.app.db.ExclusionRuleType
import com.cloudyte.app.db.LocalExclusion
import com.cloudyte.app.sync.AutoSyncManager
import com.cloudyte.app.ui.components.ConfirmDialog
import com.cloudyte.app.ui.components.EmptyState
import com.cloudyte.app.ui.components.GroupedList
import com.cloudyte.app.ui.components.GroupedListItem
import com.cloudyte.app.ui.components.LocalBottomBarInset
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExclusionsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }
    val exclusionDao = db.exclusionDao()

    val exclusions by exclusionDao.observeAll().collectAsState(initial = emptyList())
    val snackbarHostState = remember { SnackbarHostState() }

    var showAddDialog by remember { mutableStateOf(false) }
    var selectedType by remember { mutableStateOf(ExclusionRuleType.PATH_PATTERN) }
    var newPattern by remember { mutableStateOf("") }
    var newIsRegex by remember { mutableStateOf(false) }
    var newSizeValue by remember { mutableStateOf("") }
    var newSizeUnit by remember { mutableStateOf(ExclusionSizeUnit.MB) }
    var pickedSourceUri by remember { mutableStateOf<String?>(null) }
    var validationError by remember { mutableStateOf<String?>(null) }
    var rulePendingDelete by remember { mutableStateOf<LocalExclusion?>(null) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) {
            validationError = "Folder selection was cancelled."
            return@rememberLauncherForActivityResult
        }
        val doc = runCatching { DocumentFile.fromTreeUri(context, uri) }.getOrNull()
        val display = doc?.name?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast(':')?.takeIf { it.isNotBlank() }
        if (display.isNullOrBlank()) {
            validationError = "Selected folder could not be read."
            return@rememberLauncherForActivityResult
        }
        pickedSourceUri = uri.toString()
        newPattern = display
        validationError = null
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            validationError = "File selection was cancelled."
            return@rememberLauncherForActivityResult
        }
        val doc = runCatching { DocumentFile.fromSingleUri(context, uri) }.getOrNull()
        val display = doc?.name?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast(':')?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
        if (display.isNullOrBlank()) {
            validationError = "Selected file could not be read."
            return@rememberLauncherForActivityResult
        }
        pickedSourceUri = uri.toString()
        newPattern = display
        validationError = null
    }

    fun resetDialogState() {
        selectedType = ExclusionRuleType.PATH_PATTERN
        newPattern = ""
        newIsRegex = false
        newSizeValue = ""
        newSizeUnit = ExclusionSizeUnit.MB
        pickedSourceUri = null
        validationError = null
    }

    val scrollState = rememberScrollState()
    val isScrolled = scrollState.value > 0
    val topBarContainerColor = if (isScrolled) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surface

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.exclusions_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("exclusions_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = topBarContainerColor)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    resetDialogState()
                    showAddDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .padding(bottom = LocalBottomBarInset.current)
                    .testTag("exclusions_add_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_add_rule))
            }
        },
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = LocalBottomBarInset.current)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
        ) {
            Text(
                text = "Enabled rules are evaluated during backup scanning before files enter the upload queue.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )

            if (exclusions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyState(title = stringResource(R.string.exclusions_empty))
                }
            } else {
                val items = exclusions.map { rule ->
                    val ruleType = rule.effectiveRuleType()
                    val titleText = when (ruleType) {
                        ExclusionRuleType.HIDDEN -> "Hidden files & folders (.*)"
                        ExclusionRuleType.MAX_SIZE_BYTES -> {
                            val bytesLabel = rule.sizeLimitBytes?.let(::formatStorageSize) ?: rule.pattern
                            "Larger than $bytesLabel"
                        }
                        else -> rule.pattern
                    }
                    val subtitleText = buildString {
                        append(ruleType.displayName)
                        if (ruleType == ExclusionRuleType.PATH_PATTERN) {
                            append(if (rule.isRegex) " • Regex" else " • Glob pattern")
                        }
                        if (!rule.sourceUri.isNullOrBlank()) {
                            append(" • Picked from device")
                        }
                    }

                    GroupedListItem(key = rule.id.toString()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = titleText,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = subtitleText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = rule.enabled,
                                onCheckedChange = { isChecked ->
                                    scope.launch {
                                        runCatching {
                                            exclusionDao.update(rule.copy(enabled = isChecked))
                                            AutoSyncManager.requestReschedule(context)
                                        }.onFailure { err ->
                                            snackbarHostState.showSnackbar("Failed to update rule: ${err.message ?: "Database error"}")
                                        }
                                    }
                                },
                                modifier = Modifier.testTag("exclusion_switch_${rule.id}")
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            IconButton(
                                onClick = { rulePendingDelete = rule },
                                modifier = Modifier.testTag("exclusion_delete_${rule.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.action_delete),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                GroupedList(items = items, modifier = Modifier.padding(top = 4.dp))
                Spacer(modifier = Modifier.height(80.dp + LocalBottomBarInset.current))
            }
        }
    }

    rulePendingDelete?.let { rule ->
        ConfirmDialog(
            title = stringResource(R.string.exclusions_delete_title),
            message = stringResource(R.string.exclusions_delete_msg, rule.pattern),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                scope.launch {
                    runCatching {
                        exclusionDao.delete(rule)
                        AutoSyncManager.requestReschedule(context)
                    }.onFailure { err ->
                        snackbarHostState.showSnackbar("Failed to delete rule: ${err.message ?: "Database error"}")
                    }
                }
            },
            onDismiss = { rulePendingDelete = null }
        )
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text(stringResource(R.string.exclusions_add_dialog_title), fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = "Rule type",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ExclusionRuleType.entries.forEach { type ->
                            FilterChip(
                                selected = selectedType == type,
                                onClick = {
                                    selectedType = type
                                    pickedSourceUri = null
                                    validationError = null
                                    if (type != ExclusionRuleType.PATH_PATTERN) newIsRegex = false
                                },
                                label = { Text(type.displayName) },
                                modifier = Modifier.testTag("exclusion_type_${type.name.lowercase()}")
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    when (selectedType) {
                        ExclusionRuleType.HIDDEN -> {
                            Text(
                                text = "Excludes files and directories whose names begin with '.' (for example .nomedia, .thumbnails, .git).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        ExclusionRuleType.MAX_SIZE_BYTES -> {
                            OutlinedTextField(
                                value = newSizeValue,
                                onValueChange = {
                                    newSizeValue = it
                                    validationError = null
                                },
                                label = { Text("Skip files larger than") },
                                placeholder = { Text("e.g. 250") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("exclusion_size_input")
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Units use binary powers (1 KB = 1,024 B, 1 MB = 1,024 KB, 1 GB = 1,024 MB):",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ExclusionSizeUnit.entries.forEach { unit ->
                                    FilterChip(
                                        selected = newSizeUnit == unit,
                                        onClick = { newSizeUnit = unit },
                                        label = { Text(unit.name) },
                                        modifier = Modifier.testTag("exclusion_unit_${unit.name.lowercase()}")
                                    )
                                }
                            }
                        }
                        ExclusionRuleType.FOLDER_PATH, ExclusionRuleType.FILE_PATH -> {
                            val isFolder = selectedType == ExclusionRuleType.FOLDER_PATH
                            OutlinedButton(
                                onClick = {
                                    if (isFolder) folderPickerLauncher.launch(null)
                                    else filePickerLauncher.launch(arrayOf("*/*"))
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag(if (isFolder) "exclusion_pick_folder_btn" else "exclusion_pick_file_btn")
                            ) {
                                Icon(
                                    imageVector = if (isFolder) Icons.Default.FolderOpen else Icons.Default.InsertDriveFile,
                                    contentDescription = null
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (isFolder) "Pick Folder" else "Pick File")
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = newPattern,
                                onValueChange = {
                                    newPattern = it
                                    pickedSourceUri = null
                                    validationError = null
                                },
                                label = {
                                    Text(if (isFolder) "Folder name or relative path" else "File name or relative path")
                                },
                                placeholder = {
                                    Text(if (isFolder) "e.g. WhatsApp/Media or cache" else "e.g. Documents/secrets.kdbx")
                                },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("exclusion_pattern_input")
                            )
                        }
                        else -> {
                            val (label, hint) = when (selectedType) {
                                ExclusionRuleType.EXTENSION -> "Extensions (comma separated)" to "e.g. tmp, log, bak"
                                ExclusionRuleType.MIME_TYPE -> "MIME type or wildcard" to "e.g. video/* or application/zip"
                                else -> stringResource(R.string.exclusions_pattern_hint) to "e.g. *.tmp, cache/*"
                            }
                            OutlinedTextField(
                                value = newPattern,
                                onValueChange = {
                                    newPattern = it
                                    validationError = null
                                },
                                label = { Text(label) },
                                placeholder = { Text(hint) },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("exclusion_pattern_input")
                            )
                            if (selectedType == ExclusionRuleType.PATH_PATTERN) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .clickable {
                                            newIsRegex = !newIsRegex
                                            validationError = null
                                        }
                                ) {
                                    Checkbox(
                                        checked = newIsRegex,
                                        onCheckedChange = {
                                            newIsRegex = it
                                            validationError = null
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.exclusions_is_regex), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }

                    validationError?.let { err ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = err,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("exclusion_validation_error")
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        when (
                            val validation = EvaluateExclusions.validateRuleInput(
                                ruleType = selectedType,
                                pattern = newPattern,
                                isRegex = newIsRegex,
                                sizeValueText = newSizeValue,
                                sizeUnit = newSizeUnit,
                                sourceUri = pickedSourceUri
                            )
                        ) {
                            is ExclusionValidationResult.Invalid -> {
                                validationError = validation.message
                            }
                            is ExclusionValidationResult.Valid -> {
                                val entity = LocalExclusion(
                                    pattern = validation.normalizedPattern,
                                    isRegex = selectedType == ExclusionRuleType.PATH_PATTERN && newIsRegex,
                                    enabled = true,
                                    ruleType = selectedType.name,
                                    sizeLimitBytes = validation.sizeLimitBytes,
                                    sourceUri = pickedSourceUri
                                )
                                scope.launch {
                                    runCatching {
                                        exclusionDao.insert(entity)
                                        AutoSyncManager.requestReschedule(context)
                                    }.onSuccess {
                                        showAddDialog = false
                                        resetDialogState()
                                    }.onFailure { err ->
                                        validationError = "Failed to save rule: ${err.message ?: "Database error"}"
                                    }
                                }
                            }
                        }
                    },
                    modifier = Modifier.testTag("exclusion_save_button")
                ) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}
