package com.example.mediremind.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.mediremind.data.ai.ParsedMedicineUiModel
import com.example.mediremind.data.ai.PrescriptionParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import com.example.mediremind.data.userMessage
import java.io.File
import java.util.concurrent.Executors

@Composable
fun CameraCaptureScreen(
    onBackClick: () -> Unit,
    onParsedMedicines: (List<ParsedMedicineUiModel>) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val parser = remember { PrescriptionParser() }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var isParsing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var lastBitmap by remember { mutableStateOf<Bitmap?>(null) }
    fun scan(bitmap: Bitmap?) {
        if (isParsing) return
        lastBitmap = bitmap
        coroutineScope.launch {
            parseBitmap(bitmap, parser, { isParsing = it }, { errorMessage = it }, onParsedMedicines)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) {
            errorMessage = "Ứng dụng cần quyền Camera để chụp đơn thuốc."
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                scan(withContext(Dispatchers.IO) { loadBitmapFromUri(context, uri) })
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF101820))
    ) {
        if (hasCameraPermission) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { viewContext ->
                    PreviewView(viewContext).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                },
                update = { previewView ->
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                    cameraProviderFuture.addListener(
                        {
                            try {
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().apply {
                                setSurfaceProvider(previewView.surfaceProvider)
                            }
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageCapture
                            )
                            } catch (failure: Exception) {
                                errorMessage = "Không mở được camera. Thử mở lại hoặc chọn ảnh từ Thư viện."
                            }
                        },
                        ContextCompat.getMainExecutor(context)
                    )
                }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Quay lại",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Text(
                    text = "Chụp rõ toàn bộ đơn thuốc",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.size(48.dp))
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                errorMessage?.let { message ->
                    Text(
                        text = message,
                        color = Color(0xFFFFCDD2),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = Color(0xAA000000),
                                shape = RoundedCornerShape(14.dp)
                            )
                            .padding(12.dp)
                    )
                    if (lastBitmap != null) {
                        Button(onClick = { scan(lastBitmap) }, enabled = !isParsing) { Text("Thử quét lại ảnh") }
                    }
                    Button(onClick = onBackClick, enabled = !isParsing) { Text("Quay lại để nhập thuốc thủ công") }
                }
                if (!hasCameraPermission) {
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) { Text("Cấp quyền Camera") }
                    Button(onClick = {
                        runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${context.packageName}"))) }
                    }) { Text("Mở cài đặt quyền") }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { galleryLauncher.launch("image/*") },
                        enabled = !isParsing,
                        modifier = Modifier.height(58.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF455A64),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = null
                        )
                        Text("  Thư viện", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            val outputFile = File(
                                context.cacheDir,
                                "prescription_${System.currentTimeMillis()}.jpg"
                            )
                            val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()
                            imageCapture.takePicture(
                                outputOptions,
                                cameraExecutor,
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(
                                        outputFileResults: ImageCapture.OutputFileResults
                                    ) {
                                        coroutineScope.launch {
                                            scan(withContext(Dispatchers.IO) { BitmapFactory.decodeFile(outputFile.absolutePath) })
                                        }
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        coroutineScope.launch { errorMessage = "Không thể chụp ảnh. Hãy thử lại hoặc chọn ảnh từ Thư viện." }
                                    }
                                }
                            )
                        },
                        enabled = hasCameraPermission && !isParsing,
                        modifier = Modifier
                            .size(86.dp)
                            .clip(CircleShape),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFE53935),
                            contentColor = Color.White
                        ),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Chụp đơn thuốc",
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }
            }
        }

        if (isParsing) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xCC000000)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(color = Color.White)
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = "AI đang quét đơn thuốc...",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

private suspend fun parseBitmap(
    bitmap: Bitmap?,
    parser: PrescriptionParser,
    onLoadingChange: (Boolean) -> Unit,
    onError: (String?) -> Unit,
    onParsedMedicines: (List<ParsedMedicineUiModel>) -> Unit
) {
    if (bitmap == null) {
        onError("Không đọc được ảnh đơn thuốc.")
        return
    }

    onLoadingChange(true)
    onError(null)
    try {
    val result = withTimeout(45_000) { withContext(Dispatchers.IO) {
        parser.parsePrescription(bitmap)
    } }

    result.onSuccess { medicines ->
        if (medicines.isEmpty()) {
            onError("AI chưa đọc được thuốc nào từ ảnh. Vui lòng chụp lại rõ hơn hoặc nhập thủ công.")
        } else {
            onParsedMedicines(medicines)
        }
    }.onFailure { error ->
        val detail = error.message.orEmpty().lowercase()
        onError(when {
            detail.contains("chưa cấu hình") -> "Chưa cấu hình dịch vụ AI. Bạn có thể nhập thuốc thủ công."
            detail.contains("429") || detail.contains("quota") -> "Gemini đã hết hạn mức hoặc đang nhận quá nhiều yêu cầu. Chờ một lúc rồi thử lại hoặc nhập thủ công."
            detail.contains("api key") || detail.contains("403") || detail.contains("401") -> "Gemini từ chối truy cập. Cần kiểm tra khóa API; bạn vẫn có thể nhập thuốc thủ công."
            detail.contains("404") || detail.contains("not found") -> "Mô hình Gemini không khả dụng. Cần cập nhật cấu hình AI; bạn vẫn có thể nhập thuốc thủ công."
            error is java.io.IOException -> error.userMessage()
            else -> "Không đọc được đơn thuốc từ Gemini. Kiểm tra mạng, thử ảnh rõ hơn hoặc nhập thủ công."
        })
    }
    } catch (failure: Exception) { onError(failure.userMessage()) }
    finally { onLoadingChange(false) }
}

private fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
    return runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source)
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
    }.getOrNull()
}
