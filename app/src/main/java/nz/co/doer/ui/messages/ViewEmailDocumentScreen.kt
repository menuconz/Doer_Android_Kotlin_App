package nz.co.doer.ui.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter

// Matching MAUI ViewEmailDocument.xaml:
// Title="{Binding Document.FileName}"
// PinchToZoomContainer with Image, Aspect="AspectFit"
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewEmailDocumentScreen(
    onBack: () -> Unit,
    viewModel: ViewEmailDocumentViewModel = hiltViewModel()
) {
    val fileUrl = viewModel.fileUrl
    val fileName = viewModel.fileName

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Scaffold(
        topBar = {
            // Matching MAUI: Title="{Binding Document.FileName}"
            TopAppBar(
                title = {
                    Text(
                        text = fileName.ifBlank { "Attachment" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            // Matching MAUI: PinchToZoomContainer > Image Aspect="AspectFit"
            val painter = rememberAsyncImagePainter(model = fileUrl)
            val isLoading = painter.state is AsyncImagePainter.State.Loading

            androidx.compose.foundation.Image(
                painter = painter,
                contentDescription = fileName.ifBlank { "Email attachment" },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.5f, 5f)
                            offset = Offset(
                                x = offset.x + pan.x,
                                y = offset.y + pan.y
                            )
                        }
                    },
                contentScale = ContentScale.Fit
            )

            if (isLoading) {
                CircularProgressIndicator(color = Color.White)
            }
        }
    }
}
