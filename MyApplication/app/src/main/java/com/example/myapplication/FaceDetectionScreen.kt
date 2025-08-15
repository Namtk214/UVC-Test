package com.example.myapplication

import android.Manifest
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.mlkit.vision.face.Face
import java.util.concurrent.Executors

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun FaceDetectionScreen() {
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    
    if (cameraPermissionState.status.isGranted) {
        CameraPreviewWithFaceDetection()
    } else {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Camera permission is required for face detection",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { cameraPermissionState.launchPermissionRequest() }
            ) {
                Text("Grant Camera Permission")
            }
        }
    }
}

@Composable
private fun CameraPreviewWithFaceDetection() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var detectedFaces by remember { mutableStateOf<List<Face>>(emptyList()) }
    var previewView: PreviewView? by remember { mutableStateOf(null) }
    
    val faceDetectionManager = remember {
        FaceDetectionManager { faces ->
            detectedFaces = faces
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            faceDetectionManager.close()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    this.scaleType = PreviewView.ScaleType.FILL_CENTER
                    previewView = this
                    startCamera(ctx, lifecycleOwner, this, faceDetectionManager)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        
        previewView?.let { preview ->
            FaceOverlay(
                faces = detectedFaces,
                previewView = preview,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun FaceOverlay(
    faces: List<Face>,
    previewView: PreviewView,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        drawFaces(faces, previewView, this)
    }
}

private fun drawFaces(
    faces: List<Face>,
    previewView: PreviewView,
    drawScope: DrawScope
) {
    val scaleX = drawScope.size.width / previewView.width
    val scaleY = drawScope.size.height / previewView.height

    faces.forEach { face ->
        val boundingBox = face.boundingBox
        
        drawScope.drawRect(
            color = Color.Green,
            topLeft = androidx.compose.ui.geometry.Offset(
                x = boundingBox.left * scaleX,
                y = boundingBox.top * scaleY
            ),
            size = androidx.compose.ui.geometry.Size(
                width = boundingBox.width() * scaleX,
                height = boundingBox.height() * scaleY
            ),
            style = Stroke(width = 4.dp.toPx())
        )
    }
}

private fun startCamera(
    context: android.content.Context,
    lifecycleOwner: LifecycleOwner,
    previewView: PreviewView,
    faceDetectionManager: FaceDetectionManager
) {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    
    cameraProviderFuture.addListener({
        val cameraProvider = cameraProviderFuture.get()
        
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        
        val imageAnalyzer = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(Executors.newSingleThreadExecutor(), faceDetectionManager)
            }
        
        val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
        
        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalyzer
            )
        } catch (exc: Exception) {
            exc.printStackTrace()
        }
    }, ContextCompat.getMainExecutor(context))
}