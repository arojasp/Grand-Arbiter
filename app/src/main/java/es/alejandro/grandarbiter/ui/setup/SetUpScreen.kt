package es.alejandro.grandarbiter.ui.setup

import android.content.res.Configuration
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.alejandro.grandarbiter.R
import es.alejandro.grandarbiter.ui.theme.GrandArbiterTheme
import org.koin.androidx.compose.koinViewModel
import kotlin.math.roundToInt

@Composable
fun SetUpScreen(
    viewModel: SetUpViewModel = koinViewModel(),
    onSetUpComplete: () -> Unit
) {
    val uiState = viewModel.uiState

    SetUpScreenContent(
        uiState = uiState,
        onDownloadNow = { viewModel.downloadNow() },
        onComplete = { onSetUpComplete() }
    )
}

@Composable
private fun SetUpScreenContent(
    uiState: SetUpUiState,
    onDownloadNow: () -> Unit,
    onComplete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App Title
            Text(
                text = stringResource(id = R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(48.dp))

            SetupContent(
                state = uiState as SetUpUiState.Loading,
                onDownloadNow = onDownloadNow,
                onComplete = onComplete
            )
        }
    }
}


@Composable
private fun SetupContent(
    state: SetUpUiState.Loading,
    onDownloadNow: () -> Unit,
    onComplete: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(id = R.string.setup_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(id = R.string.setup_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Steps List Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                items(state.steps) { step ->
                    SetupStepItem(step)
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Bottom Progress Section
        SetupFooter(
            progress = state.progress,
            onDownloadNow = onDownloadNow,
            onComplete = onComplete
        )
    }
}

@Composable
private fun SetupStepItem(step: SetupStep) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center
        ) {
            when (step.status) {
                SetupStepStatus.COMPLETED -> {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_check_circle),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                SetupStepStatus.IN_PROGRESS -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                SetupStepStatus.PENDING -> {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_pending_circle),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                SetupStepStatus.ERROR -> {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(
                text = stringResource(step.type.titleRes),
                style = MaterialTheme.typography.titleMedium,
                color = if (step.status == SetupStepStatus.IN_PROGRESS) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (step.status == SetupStepStatus.IN_PROGRESS) FontWeight.Bold else FontWeight.Medium,
                lineHeight = 22.sp
            )
            Text(
                text = stringResource(step.type.subtitleRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun SetupFooter(
    progress: Float,
    onDownloadNow: () -> Unit,
    onComplete: () -> Unit
) {

    Column(modifier = Modifier.fillMaxWidth()) {
        when {
            progress == 0f -> {
                Button(
                    onClick = onDownloadNow,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(text = "Download now")
                }
            }

            progress < 100 -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${progress.roundToInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                val progressAnimDuration = 500
                val progressAnimation by animateFloatAsState(
                    targetValue = progress / 100f,
                    animationSpec = tween(durationMillis = progressAnimDuration, easing = FastOutSlowInEasing),
                )
                LinearProgressIndicator(
                    progress = { progressAnimation },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeCap = StrokeCap.Round
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_info),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(id = R.string.setup_footer_note),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            else -> {
                Button(
                    onClick = onComplete,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(text = "Complete")
                }
            }
        }

    }
}

@Preview(showSystemUi = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun SetUpScreenStartLoadingPreview() {
    val steps = listOf(
        SetupStep(SetupStepType.CONNECTIVITY_CHECK, SetupStepStatus.PENDING),
        SetupStep(SetupStepType.CR_DOWNLOAD, SetupStepStatus.PENDING),
        SetupStep(SetupStepType.CR_STORE, SetupStepStatus.PENDING),
        SetupStep(SetupStepType.CR_STORE, SetupStepStatus.PENDING)

    )
    GrandArbiterTheme {
        SetUpScreenContent(
            uiState = SetUpUiState.Loading(
                steps = steps,
                currentStepType = SetupStepType.CR_STORE,
                0f
            ),
            onDownloadNow = {},
            onComplete = {}
        )
    }
}

@Preview(showSystemUi = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun SetUpScreenLoadingPreview() {
    val steps = listOf(
        SetupStep(SetupStepType.CONNECTIVITY_CHECK, SetupStepStatus.PENDING),
        SetupStep(SetupStepType.CR_DOWNLOAD, SetupStepStatus.IN_PROGRESS),
        SetupStep(SetupStepType.CR_STORE, SetupStepStatus.COMPLETED),
        SetupStep(SetupStepType.CR_STORE, SetupStepStatus.ERROR)

    )
    GrandArbiterTheme {
        SetUpScreenContent(
            uiState = SetUpUiState.Loading(
                steps = steps,
                currentStepType = SetupStepType.CR_STORE,
                64f
            ),
            onDownloadNow = {},
            onComplete = {}
        )
    }
}

@Preview(showSystemUi = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun SetUpScreenSuccessPreview() {
    val steps = listOf(
        SetupStep(SetupStepType.CONNECTIVITY_CHECK, SetupStepStatus.COMPLETED),
        SetupStep(SetupStepType.CR_DOWNLOAD, SetupStepStatus.COMPLETED),
        SetupStep(SetupStepType.CR_STORE, SetupStepStatus.COMPLETED),
        SetupStep(SetupStepType.CR_STORE, SetupStepStatus.COMPLETED)

    )
    GrandArbiterTheme {
        SetUpScreenContent(
            uiState = SetUpUiState.Loading(
                steps = steps,
                currentStepType = SetupStepType.CR_STORE,
                100f
            ),
            onDownloadNow = {},
            onComplete = {}
        )
    }
}

