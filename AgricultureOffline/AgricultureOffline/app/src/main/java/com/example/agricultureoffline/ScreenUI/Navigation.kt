package com.example.agricultureoffline.ScreenUI

import android.widget.Toast
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.agricultureoffline.R
import com.example.agricultureoffline.ScreenUI.farmer.FarmerScreen
import com.example.agricultureoffline.ai.PlantDiseaseClassifier
import com.example.agricultureoffline.data.local.AppDatabase
import com.example.agricultureoffline.data.local.DiseaseSeeder
import com.example.agricultureoffline.data.local.SessionStore
import com.example.agricultureoffline.data.repository.AnalysisRepository
import com.example.agricultureoffline.data.repository.DiseaseRepository
import com.example.agricultureoffline.data.repository.FarmerRepository
import com.example.agricultureoffline.data.repository.ScanRepository
import com.example.agricultureoffline.sms.SmsQueueManager
import com.example.agricultureoffline.ui.home.HomeScreen
import com.example.agricultureoffline.ui.result.ResultScreen
import com.example.agricultureoffline.ui.scan.ScanScreen
import com.example.agricultureoffline.viewmodel.AnalysisViewModel
import com.example.agricultureoffline.viewmodel.AnalysisViewModelFactory
import com.example.agricultureoffline.viewmodel.FarmerViewModel
import com.example.agricultureoffline.viewmodel.FarmerViewModelFactory
import com.example.agricultureoffline.viewmodel.ScanHistoryViewModel
import com.example.agricultureoffline.viewmodel.ScanHistoryViewModelFactory

@Composable
fun Navigation() {

    val context = LocalContext.current

    val database = AppDatabase.getDatabase(context)

    LaunchedEffect(Unit) {
        DiseaseSeeder.seed(context, database)
    }

    val farmerRepository = FarmerRepository(database.farmerDao())
    val diseaseRepository = DiseaseRepository(database.diseaseDao())
    val scanRepository = ScanRepository(database.scanDao())
    val analysisRepository = AnalysisRepository(diseaseRepository, scanRepository)

    val farmerViewModel: FarmerViewModel = viewModel(
        factory = FarmerViewModelFactory(farmerRepository)
    )

    val classifier = remember {
        PlantDiseaseClassifier(context.applicationContext)
    }

    val analysisViewModel: AnalysisViewModel = viewModel(
        factory = AnalysisViewModelFactory(analysisRepository, classifier)
    )

    val scanHistoryViewModel: ScanHistoryViewModel = viewModel(
        factory = ScanHistoryViewModelFactory(scanRepository)
    )

    val otpViewModel: com.example.agricultureoffline.viewmodel.OtpViewModel = viewModel()

    val navController = rememberNavController()

    val session = remember { SessionStore.getSession(context) }
    var currentFarmerId by rememberSaveable { mutableIntStateOf(session.farmerId) }
    var currentFarmerName by rememberSaveable { mutableStateOf(session.farmerName) }
    val startDestination = remember {
        if (session.isLoggedIn && session.farmerId > 0) "home" else "farmer"
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { fadeIn() + slideInHorizontally { it / 8 } },
        exitTransition = { fadeOut() + slideOutHorizontally { -it / 8 } },
        popEnterTransition = { fadeIn() },
        popExitTransition = { fadeOut() }
    ) {
        composable("farmer") {
            FarmerScreen(
                modifier = Modifier,
                onCreateProfile = { name, dialCode, phone ->
                    val fullPhone = if (phone.startsWith("+")) phone else "$dialCode$phone"
                    farmerViewModel.saveFarmer(name, fullPhone) { id ->
                        val fId = id.toInt()
                        currentFarmerId = fId
                        currentFarmerName = name
                        SessionStore.saveSession(context, fId, name, fullPhone)
                        scanHistoryViewModel.loadScans(currentFarmerId)

                        Toast.makeText(
                            context,
                            context.getString(R.string.login_success),
                            Toast.LENGTH_SHORT
                        ).show()

                        // Queue welcome SMS offline-first (persists, syncs when network available, prevents duplicates)
                        SmsQueueManager.enqueueWelcomeSms(context, dialCode, phone)

                        navController.navigate("home") {
                            popUpTo("farmer") {
                                inclusive = true
                            }
                        }
                    }
                }
            )
        }

        composable("home") {
            LaunchedEffect(currentFarmerId) {
                if (currentFarmerId > 0) {
                    scanHistoryViewModel.loadScans(currentFarmerId)
                }
            }

            HomeScreen(
                farmerName = currentFarmerName,
                farmerId = currentFarmerId,
                scans = scanHistoryViewModel.scans,
                onScanClick = {
                    navController.navigate("scan")
                },
                onProfileClick = {},
                onLogoutClick = {
                    SessionStore.clearSession(context)
                    currentFarmerId = 0
                    currentFarmerName = ""
                    navController.navigate("farmer") {
                        popUpTo(0) {
                            inclusive = true
                        }
                    }
                },
                onOpenScan = { scanId ->
                    analysisViewModel.loadScan(scanId) { success ->
                        if (success) {
                            navController.navigate("result")
                        } else {
                            Toast.makeText(
                                context,
                                context.getString(R.string.could_not_open_scan),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                },
                onDeleteScan = { scanId ->
                    scanHistoryViewModel.deleteScan(scanId, currentFarmerId) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.scan_deleted),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            )
        }

        composable("scan") {
            ScanScreen(
                isAnalyzing = analysisViewModel.isAnalyzing,
                onBackClick = {
                    navController.popBackStack()
                },
                onAnalyzeClick = { imagePath ->
                    analysisViewModel.analyze(
                        farmerId = currentFarmerId,
                        imagePath = imagePath
                    ) { success ->
                        if (success) {
                            scanHistoryViewModel.loadScans(currentFarmerId)
                            navController.navigate("result")
                        } else {
                            Toast.makeText(
                                context,
                                context.getString(R.string.unable_to_analyze),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            )
        }

        composable("result") {
            val result = analysisViewModel.scanResult

            if (result != null) {
                val scan = result.first
                val disease = result.second

                ResultScreen(
                    disease = disease.diseaseName.ifBlank { disease.className },
                    plantName = disease.plantName,
                    confidence = (scan.confidence * 100).toInt(),
                    status = scan.status,
                    symptoms = disease.symptoms,
                    treatment = disease.treatment,
                    prevention = disease.prevention,
                    scannedAt = scan.scannedAt,
                    imagePath = scan.imagePath,
                    className = disease.className.ifBlank { scan.predictedClass },
                    onBackClick = {
                        scanHistoryViewModel.loadScans(currentFarmerId)
                        navController.popBackStack()
                    },
                    onHomeClick = {
                        scanHistoryViewModel.loadScans(currentFarmerId)
                        navController.navigate("home") {
                            popUpTo("home") {
                                inclusive = false
                            }
                        }
                    }
                )
            }
        }
    }
}
