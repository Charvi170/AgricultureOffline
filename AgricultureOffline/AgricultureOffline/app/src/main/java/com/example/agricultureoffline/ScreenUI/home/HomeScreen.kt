package com.example.agricultureoffline.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.agricultureoffline.R
import com.example.agricultureoffline.data.local.Scan
import com.example.agricultureoffline.data.local.SessionStore
import com.example.agricultureoffline.domain.ScanInsights
import com.example.agricultureoffline.domain.ScanStatusKey
import com.example.agricultureoffline.i18n.DiseaseTranslator
import com.example.agricultureoffline.i18n.LocalAppLanguage
import com.example.agricultureoffline.i18n.LocalSetAppLanguage
import com.example.agricultureoffline.ui.components.AgriBottomBar
import com.example.agricultureoffline.ui.components.FieldBackground
import com.example.agricultureoffline.ui.components.GlassCard
import com.example.agricultureoffline.ui.components.HomeTab
import com.example.agricultureoffline.ui.components.LanguageChips
import com.example.agricultureoffline.ui.theme.AgriColors
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    farmerName: String,
    farmerId: Int,
    scans: List<Scan> = emptyList(),
    onScanClick: () -> Unit,
    onProfileClick: () -> Unit = {},
    onLogoutClick: () -> Unit = {},
    onOpenScan: (Int) -> Unit = {},
    onDeleteScan: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var scanToDelete by remember { mutableStateOf<Scan?>(null) }
    var showProfileDialog by remember { mutableStateOf(false) }
    var showLogoutConfirmation by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(HomeTab.Home) }
    val context = LocalContext.current
    val language = LocalAppLanguage.current
    val setLanguage = LocalSetAppLanguage.current
    val locale = remember(language) { Locale(language) }
    val dateLabel = remember(language) {
        SimpleDateFormat("EEEE d MMM yyyy", locale).format(Date())
    }
    val latestScan = scans.firstOrNull()
    val healthyScans = scans.count {
        ScanInsights.keyFor(
            rawStatus = it.status,
            predictedClass = it.predictedClass,
            diseaseName = it.diseaseName,
            confidence = it.confidence,
            lowConfidenceThreshold = 0.50f
        ) == ScanStatusKey.Healthy
    }

    if (scanToDelete != null) {
        val target = scanToDelete!!
        AlertDialog(
            onDismissRequest = { scanToDelete = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(text = stringResource(R.string.delete_scan_title), fontWeight = FontWeight.Bold)
            },
            text = {
                Text(text = stringResource(R.string.delete_scan_message, target.diseaseName))
            },
            confirmButton = {
                Button(
                    onClick = {
                        val id = target.id
                        scanToDelete = null
                        onDeleteScan(id)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { scanToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showProfileDialog) {
        val phoneNumber = remember { SessionStore.getSession(context).phoneNumber }
        AlertDialog(
            onDismissRequest = { showProfileDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = null,
                    tint = AgriColors.Mint,
                    modifier = Modifier.size(48.dp)
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.profile),
                    fontWeight = FontWeight.Bold,
                    color = AgriColors.TextPrimary
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0x1FFFFFFF),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = stringResource(R.string.name),
                                style = MaterialTheme.typography.labelSmall,
                                color = AgriColors.TextMuted
                            )
                            Text(
                                text = if (farmerName.isNotBlank()) farmerName else stringResource(R.string.farmer_fallback),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = AgriColors.TextPrimary
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0x1FFFFFFF),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Farmer ID",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AgriColors.TextMuted
                                )
                                Text(
                                    text = "F${farmerId.toString().padStart(3, '0')}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = AgriColors.Mint
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0x1FFFFFFF),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = stringResource(R.string.nav_history),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AgriColors.TextMuted
                                )
                                Text(
                                    text = "${scans.size} ${if (scans.size == 1) "scan" else "scans"}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = AgriColors.TextPrimary
                                )
                            }
                        }
                    }

                    if (phoneNumber.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0x1FFFFFFF),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = stringResource(R.string.phone_number),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AgriColors.TextMuted
                                )
                                Text(
                                    text = phoneNumber,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = AgriColors.TextPrimary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Button(
                        onClick = {
                            showLogoutConfirmation = true
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.logout),
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            confirmButton = {
                OutlinedButton(onClick = { showProfileDialog = false }) {
                    Text(stringResource(R.string.back))
                }
            }
        )
    }

    if (showLogoutConfirmation) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirmation = false },
            title = {
                Text(
                    text = stringResource(R.string.logout_dialog_title),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(text = stringResource(R.string.logout_dialog_message))
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutConfirmation = false
                        showProfileDialog = false
                        onLogoutClick()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.logout))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showLogoutConfirmation = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    FieldBackground(modifier = modifier) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp),
                contentPadding = PaddingValues(bottom = 96.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.brand_name),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = AgriColors.TextPrimary
                            )
                            Text(
                                text = stringResource(
                                    R.string.farmer_id_label,
                                    farmerId.toString().padStart(3, '0')
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = AgriColors.TextMuted
                            )
                        }
                        IconButton(
                            onClick = { showProfileDialog = true },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0x33FFFFFF))
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = stringResource(R.string.profile),
                                tint = AgriColors.Mint,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    LanguageChips(selected = language, onSelect = setLanguage)
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = dateLabel,
                        color = AgriColors.TextSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.welcome_back),
                        style = MaterialTheme.typography.bodyLarge,
                        color = AgriColors.TextSecondary
                    )
                    Text(
                        text = if (farmerName.isNotBlank()) farmerName else stringResource(R.string.farmer_fallback),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = AgriColors.TextPrimary
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                }

                if (tab != HomeTab.History) {
                    item {
                        GlassCard(onClick = onScanClick, modifier = Modifier.fillMaxWidth()) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(R.string.plant_health_diagnosis),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = AgriColors.TextPrimary
                                    )
                                    Surface(
                                        shape = CircleShape,
                                        color = Color(0x30C9FFD1)
                                    ) {
                                        Text(
                                            text = if (scans.size == 1) {
                                                stringResource(R.string.scan_count_one, scans.size)
                                            } else {
                                                stringResource(R.string.scan_count_many, scans.size)
                                            },
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = AgriColors.Mint
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(74.dp)
                                            .clip(RoundedCornerShape(24.dp))
                                            .background(Color(0x2EFFFFFF)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CameraAlt,
                                            contentDescription = null,
                                            tint = AgriColors.Mint,
                                            modifier = Modifier.size(38.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(18.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.scan_a_leaf),
                                            style = MaterialTheme.typography.headlineSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = AgriColors.TextPrimary
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = stringResource(R.string.scan_leaf_subtitle),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = AgriColors.TextSecondary
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = stringResource(R.string.take_photo_gallery),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = AgriColors.Mint,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Surface(
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(18.dp),
                                        color = Color(0x1FFFFFFF)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                text = stringResource(R.string.nav_history),
                                                color = AgriColors.TextMuted,
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = scans.size.toString(),
                                                color = AgriColors.TextPrimary,
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Surface(
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(18.dp),
                                        color = Color(0x1FFFFFFF)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                text = stringResource(R.string.status_healthy),
                                                color = AgriColors.TextMuted,
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = healthyScans.toString(),
                                                color = AgriColors.TextPrimary,
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                                latestScan?.let { last ->
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Text(
                                        text = DiseaseTranslator.diseaseName(
                                            context = LocalContext.current,
                                            className = last.predictedClass,
                                            fallback = last.diseaseName.ifBlank { last.predictedClass },
                                            language = language
                                        ),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = AgriColors.TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = AgriColors.Mint,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.scan_history),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = AgriColors.TextPrimary
                            )
                        }
                        if (scans.isNotEmpty()) {
                            Text(
                                text = if (scans.size == 1) {
                                    stringResource(R.string.scan_count_one, scans.size)
                                } else {
                                    stringResource(R.string.scan_count_many, scans.size)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = AgriColors.TextMuted
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (scans.isEmpty()) {
                    item {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(text = "🌿", style = MaterialTheme.typography.headlineMedium)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.no_scan_history),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = AgriColors.TextPrimary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.no_scan_history_hint),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = AgriColors.TextSecondary
                                )
                            }
                        }
                    }
                } else {
                    items(items = scans, key = { it.id }) { scan ->
                        ScanHistoryItem(
                            scan = scan,
                            onClick = { onOpenScan(scan.id) },
                            onDeleteClick = { scanToDelete = scan }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }
            }

            AgriBottomBar(
                selected = tab,
                onSelect = { selected ->
                    when (selected) {
                        HomeTab.Scan -> onScanClick()
                        HomeTab.Profile -> showProfileDialog = true
                        else -> tab = selected
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
fun ScanHistoryItem(
    scan: Scan,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val context = LocalContext.current
    val language = LocalAppLanguage.current
    val fallbackName = scan.diseaseName.ifBlank { scan.predictedClass.replace('_', ' ').trim() }
    val displayName = DiseaseTranslator.diseaseName(
        context = context,
        className = scan.predictedClass,
        fallback = fallbackName,
        language = language
    )
    val statusKey = ScanInsights.keyFor(
        rawStatus = scan.status,
        predictedClass = scan.predictedClass,
        diseaseName = scan.diseaseName,
        confidence = scan.confidence,
        lowConfidenceThreshold = 0.50f
    )
    val isLowConfidence = statusKey == ScanStatusKey.LowConfidence
    val confidencePercent = (scan.confidence * 100).toInt()
    val statusLabel = when (statusKey) {
        ScanStatusKey.LowConfidence -> stringResource(R.string.status_low_confidence)
        ScanStatusKey.Uncertain -> stringResource(R.string.status_uncertain)
        ScanStatusKey.Healthy -> stringResource(R.string.status_healthy)
        ScanStatusKey.Confirmed -> stringResource(R.string.status_confirmed)
    }

    GlassCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0x33FFFFFF)),
                contentAlignment = Alignment.Center
            ) {
                val imageFile = File(scan.imagePath)
                if (imageFile.exists()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(imageFile)
                            .crossfade(true)
                            .build(),
                        contentDescription = stringResource(R.string.scan_thumbnail),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Eco,
                        contentDescription = null,
                        tint = AgriColors.Mint,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = AgriColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = if (isLowConfidence) Color(0x55E65100) else Color(0x5532A852)
                    ) {
                        Text(
                            text = "$confidencePercent%",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = if (isLowConfidence) AgriColors.Warning else AgriColors.Mint
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = CircleShape,
                        color = Color(0x1FFFFFFF)
                    ) {
                        Text(
                            text = statusLabel,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp,
                            color = AgriColors.TextSecondary
                        )
                    }
                    if (scan.scannedAt.isNotBlank()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = scan.scannedAt,
                            style = MaterialTheme.typography.bodySmall,
                            color = AgriColors.TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            }
            IconButton(onClick = onDeleteClick, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = stringResource(R.string.delete_scan),
                    tint = AgriColors.TextMuted,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
