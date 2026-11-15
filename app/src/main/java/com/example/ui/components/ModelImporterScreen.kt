package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.LocalAiModelSpec
import com.example.model.LocalModelRole
import com.example.service.LocalModelInspector
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AccentPurple

@Composable
fun ModelImporterScreen(
    importedModels: List<LocalAiModelSpec>,
    statusMessage: String?,
    onImportModelUri: (LocalModelRole, Uri, String) -> Unit,
    onActivateModel: (LocalAiModelSpec) -> Unit,
    onUpdateEndpoint: (String, String) -> Unit,
    onTestEndpoint: (String) -> Unit,
    onDeleteModel: (LocalAiModelSpec) -> Unit,
    onDismissStatus: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pendingRole by remember { mutableStateOf(LocalModelRole.LLM_ORCHESTRATOR) }

    val openModelFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val defaultLoopback = when (pendingRole) {
                LocalModelRole.LLM_ORCHESTRATOR -> "http://127.0.0.1:8080"
                LocalModelRole.IMAGE_GENERATOR -> "http://127.0.0.1:7860"
                LocalModelRole.VIDEO_ENGINE -> ""
            }
            onImportModelUri(pendingRole, uri, defaultLoopback)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 640.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Architecture Header Card
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = AccentPurple.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, AccentPurple.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Hub,
                            contentDescription = "Local AI Team Architecture",
                            tint = AccentPurple
                        )
                        Text(
                            text = "نظام فريق الذكاء الاصطناعي المحلي (100% على جهازك)",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                    Text(
                        text = "استورد ملفات النماذج من ذاكرة هاتفك (.gguf, .onnx, .safetensors, .tflite) عبر SAF بدون أذونات تخزين، واربطها مع جسر الخادم المحلي (127.0.0.1) أو اعمل مباشرة عبر المنسّق المدمج في الهاتف دون أي خوادم خارجية.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    )
                }
            }

            AnimatedVisibility(visible = !statusMessage.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AccentCyan.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("importer_status_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = null,
                            tint = AccentCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = statusMessage.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = onDismissStatus,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close Status Banner")
                        }
                    }
                }
            }

            // Three Role Cards (LLM Orchestrator, Image AI, Video Engine)
            LocalModelRole.entries.forEach { role ->
                val roleModels = importedModels.filter { it.role == role }
                val roleIcon = when (role) {
                    LocalModelRole.LLM_ORCHESTRATOR -> Icons.Default.Psychology
                    LocalModelRole.IMAGE_GENERATOR -> Icons.Default.Image
                    LocalModelRole.VIDEO_ENGINE -> Icons.Default.MovieCreation
                }
                val roleAccent = when (role) {
                    LocalModelRole.LLM_ORCHESTRATOR -> AccentPurple
                    LocalModelRole.IMAGE_GENERATOR -> AccentCyan
                    LocalModelRole.VIDEO_ENGINE -> AccentEmerald
                }

                RoleImporterCard(
                    role = role,
                    icon = roleIcon,
                    accentColor = roleAccent,
                    models = roleModels,
                    onPickFileClick = {
                        pendingRole = role
                        openModelFileLauncher.launch(arrayOf("*/*"))
                    },
                    onActivateModel = onActivateModel,
                    onUpdateEndpoint = onUpdateEndpoint,
                    onTestEndpoint = onTestEndpoint,
                    onDeleteModel = onDeleteModel
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun RoleImporterCard(
    role: LocalModelRole,
    icon: ImageVector,
    accentColor: Color,
    models: List<LocalAiModelSpec>,
    onPickFileClick: () -> Unit,
    onActivateModel: (LocalAiModelSpec) -> Unit,
    onUpdateEndpoint: (String, String) -> Unit,
    onTestEndpoint: (String) -> Unit,
    onDeleteModel: (LocalAiModelSpec) -> Unit
) {
    val activeModel = models.firstOrNull { it.isActive }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("role_card_${role.name}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = accentColor.copy(alpha = 0.18f),
                        modifier = Modifier.size(38.dp)
                    ) {
                        androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = icon,
                                contentDescription = role.titleEn,
                                tint = accentColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = role.titleAr,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = role.subtitleAr,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }

            Button(
                onClick = onPickFileClick,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor,
                    contentColor = Color(0xFF06101B)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("btn_import_${role.name}")
            ) {
                Icon(Icons.Default.AddCircleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "استيراد ملف (${role.supportedExtensions.joinToString(" / .", prefix = ".")})",
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (models.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Memory,
                            contentDescription = null,
                            tint = AccentEmerald,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (role == LocalModelRole.LLM_ORCHESTRATOR) {
                                "النشط حالياً: المنسّق الدلالي المدمج (Structured JSON Planner)"
                            } else if (role == LocalModelRole.IMAGE_GENERATOR) {
                                "النشط حالياً: مولّد الشخصيات والتصاميم البصرية المحلي (Offline)"
                            } else {
                                "النشط حالياً: محرك MediaCodec + Canvas GPU لتحريك الشخصيات"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            } else {
                models.forEach { model ->
                    ImportedModelItemRow(
                        model = model,
                        accentColor = accentColor,
                        onActivate = { onActivateModel(model) },
                        onSaveEndpoint = { url -> onUpdateEndpoint(model.id, url) },
                        onTestEndpoint = onTestEndpoint,
                        onDelete = { onDeleteModel(model) }
                    )
                }
            }

            if (activeModel != null) {
                Text(
                    text = "المسار المفعل بالفريق: ${activeModel.displayName}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = accentColor,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
        }
    }
}

@Composable
private fun ImportedModelItemRow(
    model: LocalAiModelSpec,
    accentColor: Color,
    onActivate: () -> Unit,
    onSaveEndpoint: (String) -> Unit,
    onTestEndpoint: (String) -> Unit,
    onDelete: () -> Unit
) {
    var endpointDraft by remember(model.id, model.localEndpointUrl) {
        mutableStateOf(model.localEndpointUrl)
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (model.isActive) accentColor.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(
            1.dp,
            if (model.isActive) accentColor.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = model.displayName,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${model.fileFormat} • ${LocalModelInspector.formatSize(model.fileSizeBytes)}",
                        style = MaterialTheme.typography.labelSmall.copy(color = accentColor)
                    )
                    Text(
                        text = model.binaryHeaderSummary,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                    )
                }

                if (!model.isActive) {
                    OutlinedButton(
                        onClick = onActivate,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(40.dp)
                    ) {
                        Text("تفعيل", fontSize = 12.sp)
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Active Model",
                        tint = accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Remove Model",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            if (model.role != LocalModelRole.VIDEO_ENGINE) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = endpointDraft,
                        onValueChange = { endpointDraft = it },
                        singleLine = true,
                        label = { Text("Local Bridge URI (127.0.0.1)", fontSize = 11.sp) },
                        leadingIcon = { Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentColor,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = {
                            onSaveEndpoint(endpointDraft)
                            if (endpointDraft.isNotBlank()) {
                                onTestEndpoint(endpointDraft)
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text("ربط وفحص", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
