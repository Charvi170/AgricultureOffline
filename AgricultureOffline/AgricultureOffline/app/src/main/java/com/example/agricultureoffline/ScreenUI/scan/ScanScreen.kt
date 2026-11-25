package com.example.agricultureoffline.ui.scan

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.agricultureoffline.R
import com.example.agricultureoffline.ai.LeafImageValidator
import com.example.agricultureoffline.ui.components.AnalysisLoadingOverlay
import com.example.agricultureoffline.ui.components.FieldBackground
import com.example.agricultureoffline.ui.components.GlassCard
import com.example.agricultureoffline.ui.theme.AgriColors
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ScanScreen(
    onBackClick: () -> Unit,
    onAnalyzeClick: (String) -> Unit = {},
    isAnalyzing: Boolean = false,
    modifier: Modifier = Modifier
) {
    val tag = "ScanScreen"
    val context = LocalContext.current
    Log.d(tag, "ScanScreen initial composition started")

    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var capturedImage by remember { mutableStateOf<Bitmap?>(null) }
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }
    var isProcessingImage by remember { mutableStateOf(false) }
    var showInvalidImage by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val registryOwner = LocalActivityResultRegistryOwner.current
    Log.d(tag, "LocalActivityResultRegistryOwner resolved: $registryOwner")

    // Camera capture launcher using ActivityResultContracts.TakePicture
    val cameraLauncher = if (registryOwner != null) {
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.TakePicture()
        ) { success: Boolean ->
            Log.d(tag, "Camera capture finished, success: $success")
            if (success && tempCameraUri != null) {
                selectedImageUri = tempCameraUri
                capturedImage = null
                Log.d(tag, "Displayed captured photo in preview: $tempCameraUri")
            } else {
                Log.d(tag, "Camera capture cancelled or failed")
            }
        }
    } else {
        Log.w(tag, "LocalActivityResultRegistryOwner is null; deferring cameraLauncher")
        null
    }

    // Helper to create and launch camera
    val launchCameraIntent = {
        try {
            val uri = createTempImageUri(context)
            if (uri != null) {
                tempCameraUri = uri
                Log.d(tag, "Launching camera with FileProvider URI: $uri")
                cameraLauncher?.launch(uri)
            } else {
                Log.e(tag, "createTempImageUri returned null")
                Toast.makeText(context, "Unable to create camera file", Toast.LENGTH_SHORT).show()
            }
        } catch (t: Throwable) {
            Log.e(tag, "Camera launch error: ${t.javaClass.simpleName}: ${t.message}", t)
            Toast.makeText(context, "Camera error: ${t.javaClass.simpleName}", Toast.LENGTH_SHORT).show()
        }
    }

    // CAMERA runtime permission launcher
    val cameraPermissionLauncher = if (registryOwner != null) {
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            Log.d(tag, "CAMERA permission result: $isGranted")
            if (isGranted) {
                launchCameraIntent()
            } else {
                Log.w(tag, "User denied CAMERA permission")
                Toast.makeText(context, "Camera permission is required to capture photos", Toast.LENGTH_SHORT).show()
            }
        }
    } else {
        Log.w(tag, "LocalActivityResultRegistryOwner is null; deferring cameraPermissionLauncher")
        null
    }

    // Gallery picker launcher
    val galleryLauncher = if (registryOwner != null) {
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            Log.d(tag, "Image selected from gallery: $uri")
            if (uri != null) {
                selectedImageUri = uri
                capturedImage = null
            }
        }
    } else {
        Log.w(tag, "LocalActivityResultRegistryOwner is null; deferring galleryLauncher")
        null
    }

    Log.d(tag, "Rendering ScanScreen UI components")

    FieldBackground(modifier = modifier) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                RowHeader(onBackClick = onBackClick)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.check_plant_health),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = AgriColors.TextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.take_clear_photo),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AgriColors.TextSecondary
                )
                Spacer(modifier = Modifier.height(20.dp))

                GlassCard(modifier = Modifier.fillMaxWidth().height(280.dp)) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            capturedImage != null -> {
                                Image(
                                    bitmap = capturedImage!!.asImageBitmap(),
                                    contentDescription = stringResource(R.string.captured_leaf),
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            selectedImageUri != null -> {
                                AsyncImage(
                                    model = selectedImageUri,
                                    contentDescription = stringResource(R.string.selected_leaf),
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            else -> {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.PhotoLibrary,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint = AgriColors.Mint
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = stringResource(R.string.no_image_selected),
                                        color = AgriColors.TextPrimary
                                    )
                                    Text(
                                        text = stringResource(R.string.choose_photo_analyze),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = AgriColors.TextMuted
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                // Take Photo button
                Button(
                    onClick = {
                        try {
                            val hasPermission = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.CAMERA
                            ) == PackageManager.PERMISSION_GRANTED

                            if (hasPermission) {
                                Log.d(tag, "CAMERA permission already granted; launching camera")
                                launchCameraIntent()
                            } else {
                                Log.d(tag, "Requesting CAMERA runtime permission")
                                cameraPermissionLauncher?.launch(Manifest.permission.CAMERA)
                            }
                        } catch (t: Throwable) {
                            Log.e(tag, "Error triggering camera button: ${t.javaClass.simpleName}: ${t.message}", t)
                            Toast.makeText(context, "Camera error: ${t.javaClass.simpleName}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = (cameraLauncher != null),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AgriColors.LeafBright,
                        disabledContainerColor = AgriColors.LeafBright.copy(alpha = 0.5f),
                        disabledContentColor = Color.White.copy(alpha = 0.7f)
                    )
                ) {
                    Icon(imageVector = Icons.Default.CameraAlt, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.take_photo), fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Gallery / Upload Image button (working)
                OutlinedButton(
                    onClick = {
                        try {
                            if (galleryLauncher != null) {
                                Log.d(tag, "Launching gallery picker")
                                galleryLauncher.launch("image/*")
                            } else {
                                Log.w(tag, "Gallery launcher is unavailable")
                            }
                        } catch (e: Exception) {
                            Log.e(tag, "Gallery launch exception", e)
                        }
                    },
                    enabled = (galleryLauncher != null),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = AgriColors.Mint,
                        disabledContentColor = AgriColors.Mint.copy(alpha = 0.4f)
                    )
                ) {
                    Icon(imageVector = Icons.Default.PhotoLibrary, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.choose_from_gallery), fontWeight = FontWeight.Medium)
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Analyze Leaf button
                Button(
                    onClick = {
                        if (isAnalyzing || isProcessingImage) return@Button
                        val uri = selectedImageUri
                        val bmp = capturedImage
                        if (uri == null && bmp == null) {
                            Toast.makeText(context, R.string.scan_photo_required, Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        isProcessingImage = true
                        coroutineScope.launch {
                            var pendingFile: File? = null
                            var keepImage = false
                            try {
                                val isLeaf = withContext(Dispatchers.IO) {
                                    val scansDir = File(context.filesDir, "scans").apply { mkdirs() }
                                    val destFile = File.createTempFile("scan_", ".jpg", scansDir)
                                    pendingFile = destFile
                                    if (uri != null) {
                                        val input = context.contentResolver.openInputStream(uri)
                                            ?: error("Selected image could not be opened")
                                        input.use {
                                            destFile.outputStream().use { output ->
                                                it.copyTo(output)
                                            }
                                        }
                                    } else if (bmp != null) {
                                        destFile.outputStream().use { output ->
                                            check(bmp.compress(Bitmap.CompressFormat.JPEG, 92, output))
                                        }
                                    }
                                    LeafImageValidator.isLeaf(context, destFile)
                                }
                                if (uri == selectedImageUri && bmp === capturedImage) {
                                    if (isLeaf) {
                                        onAnalyzeClick(checkNotNull(pendingFile).absolutePath)
                                        keepImage = true
                                    } else {
                                        showInvalidImage = true
                                    }
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Log.e(tag, "Failed to prepare or validate image", e)
                                Toast.makeText(context, R.string.scan_processing_error, Toast.LENGTH_LONG).show()
                            } finally {
                                try {
                                    if (!keepImage) withContext(NonCancellable + Dispatchers.IO) {
                                        pendingFile?.delete()
                                    }
                                } finally {
                                    isProcessingImage = false
                                }
                            }
                        }
                    },
                    enabled = !isAnalyzing && !isProcessingImage,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AgriColors.Leaf,
                        disabledContainerColor = Color(0x66D8DDD8),
                        disabledContentColor = AgriColors.TextMuted
                    )
                ) {
                    Text(text = stringResource(R.string.analyze_leaf), fontWeight = FontWeight.Bold)
                }
            }

            if (showInvalidImage) {
                AlertDialog(
                    onDismissRequest = { showInvalidImage = false },
                    title = { Text(stringResource(R.string.scan_invalid_image_title)) },
                    text = { Text(stringResource(R.string.scan_invalid_image_message)) },
                    confirmButton = {
                        TextButton(onClick = { showInvalidImage = false }) {
                            Text(stringResource(R.string.scan_invalid_image_ok))
                        }
                    }
                )
            }

            if (isAnalyzing) {
                AnalysisLoadingOverlay()
            }
        }
    }
}

private fun createTempImageUri(context: Context): Uri? {
    return try {
        val appContext = context.applicationContext
        val authority = "${appContext.packageName}.fileprovider"
        val imageDir = File(appContext.cacheDir, "camera_images").apply { mkdirs() }
        val file = File(imageDir, "leaf_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(appContext, authority, file)
        Log.d("ScanScreen", "createTempImageUri success: $uri (path=${file.absolutePath})")
        uri
    } catch (t: Throwable) {
        Log.e("ScanScreen", "createTempImageUri exception: ${t.javaClass.simpleName}: ${t.message}", t)
        null
    }
}

@Composable
private fun RowHeader(onBackClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.CenterStart) {
        IconButton(onClick = onBackClick) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = AgriColors.TextPrimary
            )
        }
        Text(
            text = stringResource(R.string.scan_a_leaf),
            modifier = Modifier.align(Alignment.Center),
            fontWeight = FontWeight.Bold,
            color = AgriColors.TextPrimary
        )
    }
}
