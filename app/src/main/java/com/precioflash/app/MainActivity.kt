
package com.precioflash.app

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.text.NumberFormat
import java.util.*
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val launcher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) setContent { App() }
        }
        launcher.launch(Manifest.permission.CAMERA)
    }
}

@Composable
fun App() {
    val prices = remember { mutableStateListOf<Double>() }
    val format = NumberFormat.getCurrencyInstance(Locale("es","DO"))

    Column {
        CameraPreview { price ->
            if (!prices.contains(price)) prices.add(price)
        }

        Column(Modifier.padding(16.dp)) {
            prices.forEach { Text(format.format(it)) }

            Spacer(Modifier.height(12.dp))

            Text("TOTAL: " + format.format(prices.sum()), style = MaterialTheme.typography.headlineMedium)

            Button(onClick = { prices.clear() }) { Text("Reiniciar") }
        }
    }
}

@Composable
fun CameraPreview(onDetected:(Double)->Unit) {
    val context = LocalContext.current
    val previewView = remember { PreviewView(context) }

    AndroidView(factory = { previewView }) {
        val providerFuture = ProcessCameraProvider.getInstance(context)

        providerFuture.addListener({
            val provider = providerFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val executor = Executors.newSingleThreadExecutor()

            val analysis = ImageAnalysis.Builder().build().also {
                it.setAnalyzer(executor) { proxy ->
                    val image = proxy.image ?: return@setAnalyzer proxy.close()

                    val input = InputImage.fromMediaImage(image, proxy.imageInfo.rotationDegrees)

                    recognizer.process(input).addOnSuccessListener { result ->
                        val regex = Regex("(\\d+[.,]\\d{2})")
                        regex.find(result.text)?.value?.let {
                            val clean = it.replace(",", ".")
                            onDetected(clean.toDouble())
                        }
                    }.addOnCompleteListener { proxy.close() }
                }
            }

            provider.unbindAll()
            provider.bindToLifecycle(context as ComponentActivity, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
        }, ContextCompat.getMainExecutor(context))
    }
}
