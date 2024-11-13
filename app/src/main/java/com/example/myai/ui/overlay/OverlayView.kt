package com.example.myai.ui.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.myai.R
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items

class OverlayView(private val context: Context) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null
    private val currentText = mutableStateOf("Iniciando reconhecimento de voz...")
    private var isExpanded = true
    private val animatedScale = Animatable(1f)
    private val animatedAlpha = Animatable(1f)
    private val animatedOffset = Animatable(0f)

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            toggleVisibility()
            return true
        }
    })

    private inner class OverlayLifecycleOwner : LifecycleOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)

        override val lifecycle: Lifecycle
            get() = lifecycleRegistry

        fun setCurrentState(state: Lifecycle.State) {
            lifecycleRegistry.currentState = state
        }
    }

    private inner class OverlaySavedStateRegistryOwner : SavedStateRegistryOwner {
        private val controller = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle = lifecycleOwner.lifecycle
        override val savedStateRegistry: SavedStateRegistry = controller.savedStateRegistry

        init {
            controller.performRestore(Bundle())
        }
    }

    private val lifecycleOwner = OverlayLifecycleOwner()
    private val savedStateRegistryOwner = OverlaySavedStateRegistryOwner()

    fun toggleVisibility() {
        isExpanded = !isExpanded
        overlayView?.let { view ->
            (view as ComposeView).setContent {
                MaterialTheme {
                    if (isExpanded) {
                        ExpandedOverlay()
                    } else {
                        MinimizedOverlay()
                    }
                }
            }
        }
    }

    private fun Dp.toPx(context: Context): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            this.value,
            context.resources.displayMetrics
        ).toInt()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (overlayView != null) return

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0  // Centralizado horizontalmente
            y = 100.dp.toPx(context)  // Distância do topo
        }

        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(savedStateRegistryOwner)

            setContent {
                MaterialTheme {
                    if (isExpanded) {
                        ExpandedOverlay()
                    } else {
                        MinimizedOverlay()
                    }
                }
            }
        }

        overlayView = composeView
        windowManager.addView(composeView, params)
        lifecycleOwner.setCurrentState(Lifecycle.State.RESUMED)
    }

    @Composable
    private fun ExpandedOverlay() {
        val scale by animatedScale.asState()
        val alpha by animatedAlpha.asState()
        val offset by animatedOffset.asState()
        val scope = rememberCoroutineScope()

        Surface(
            modifier = Modifier
                .width(300.dp)
                .heightIn(min = 100.dp, max = 200.dp)
                .padding(horizontal = 16.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                    translationX = offset
                }
                .shadow(elevation = 8.dp, shape = RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Cabeçalho com botão minimizar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_assistant),
                            contentDescription = "Assistente",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Assistente",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    IconButton(
                        onClick = { scope.launch { animateToMinimized() } }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = "Minimizar",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Conteúdo com scroll
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        item {
                            Text(
                                text = currentText.value,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun MinimizedOverlay() {
        val scale by animatedScale.asState()
        val alpha by animatedAlpha.asState()
        val scope = rememberCoroutineScope()

        Box(
            modifier = Modifier
                .size(48.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                }
                .shadow(elevation = 4.dp, shape = CircleShape)
                .background(
                    MaterialTheme.colorScheme.primary,
                    CircleShape
                )
                .clickable {
                    scope.launch { animateToExpanded() }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_assistant),
                contentDescription = "Expandir Assistente",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
    }

    private suspend fun animateToMinimized() {
        coroutineScope {
            launch {
                animatedScale.animateTo(
                    targetValue = 0.8f,
                    animationSpec = spring(
                        dampingRatio = 0.8f,
                        stiffness = Spring.StiffnessLow
                    )
                )
            }
            launch {
                animatedAlpha.animateTo(
                    targetValue = 0.5f,
                    animationSpec = tween(300)
                )
            }
            launch {
                animatedOffset.animateTo(
                    targetValue = 100f,
                    animationSpec = tween(300)
                )
            }
        }.join()

        toggleVisibility()

        coroutineScope {
            launch { animatedScale.snapTo(1f) }
            launch { animatedAlpha.snapTo(1f) }
            launch { animatedOffset.snapTo(0f) }
        }
    }

    private suspend fun animateToExpanded() {
        toggleVisibility()
        coroutineScope {
            launch {
                animatedScale.animateTo(
                    targetValue = 0.8f,
                    animationSpec = spring(
                        dampingRatio = 0.8f,
                        stiffness = Spring.StiffnessLow
                    )
                )
            }
            launch {
                animatedAlpha.animateTo(
                    targetValue = 0.5f,
                    animationSpec = tween(300)
                )
            }
            launch {
                animatedOffset.animateTo(
                    targetValue = 100f,
                    animationSpec = tween(300)
                )
            }
        }.join()

        coroutineScope {
            launch {
                animatedScale.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = 0.8f,
                        stiffness = Spring.StiffnessLow
                    )
                )
            }
            launch {
                animatedAlpha.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(300)
                )
            }
            launch {
                animatedOffset.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(300)
                )
            }
        }
    }

    fun updateText(text: String) {
        currentText.value = text
    }

    fun hide() {
        overlayView?.let {
            lifecycleOwner.setCurrentState(Lifecycle.State.DESTROYED)
            windowManager.removeView(it)
            overlayView = null
        }
    }

    fun isVisible(): Boolean = isExpanded
}

@Composable
fun AppSelectionContent(apps: List<ResolveInfo>, onSelection: (Int) -> Unit,packageManager: PackageManager) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 300.dp)  // Altura máxima para o scroll
            .padding(16.dp)
    ) {
        items(apps.size) { index ->
            val app = apps[index]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${index + 1} - ${app.loadLabel(packageManager)}",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}