package com.membora.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.membora.app.R
import com.membora.app.domain.ActivityEvent
import com.membora.app.domain.Member
import com.membora.app.domain.MemberStatus
import com.membora.app.data.pretty
import coil3.compose.AsyncImage
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

val PagePadding = 20.dp
val CardShape = RoundedCornerShape(20.dp)

@Composable
fun BrandMark(modifier: Modifier = Modifier, small: Boolean = false) {
    Surface(
        modifier = modifier.size(if (small) 38.dp else 48.dp),
        shape = RoundedCornerShape(if (small) 12.dp else 15.dp),
        color = Color.White,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)),
        shadowElevation = if (small) 2.dp else 5.dp
    ) {
        Icon(
            painter = painterResource(R.drawable.membora_mark),
            contentDescription = "Membora",
            modifier = Modifier.padding(if (small) 4.dp else 5.dp),
            tint = Color.Unspecified
        )
    }
}

@Composable
fun BrandLockup(modifier: Modifier = Modifier, light: Boolean = false) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        BrandMark(small = true)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                "Membora",
                style = MaterialTheme.typography.titleLarge,
                color = if (light) Color.White else MaterialTheme.colorScheme.onSurface,
                letterSpacing = (-0.4).sp
            )
            Text(
                "MANAGE MEMBERS. GROW TOGETHER.",
                style = MaterialTheme.typography.labelMedium,
                color = if (light) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 8.sp,
                letterSpacing = 0.8.sp
            )
        }
    }
}

@Composable
fun EnterprisePageHeader(title: String, subtitle: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        if (action != null && maxWidth < 480.dp) {
            Column {
                Text(title, style = MaterialTheme.typography.displaySmall)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                action()
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.displaySmall)
                    Spacer(Modifier.height(2.dp))
                    Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
                action?.invoke()
            }
        }
    }
}

@Composable
fun MemberAvatar(
    member: Member,
    modifier: Modifier = Modifier,
    large: Boolean = false,
    onPhotoClick: (() -> Unit)? = null
) {
    val photoClickModifier = if (member.photoUrl.isNotBlank() && onPhotoClick != null) {
        Modifier.clickable(onClickLabel = "View profile photo", onClick = onPhotoClick)
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .size(if (large) 72.dp else 48.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .then(photoClickModifier),
        contentAlignment = Alignment.Center
    ) {
        if (member.photoUrl.isNotBlank()) {
            AsyncImage(
                model = member.photoUrl,
                contentDescription = "${member.fullName} profile photo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(member.initials, color = MaterialTheme.colorScheme.onPrimaryContainer, style = if (large) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun ProfilePhotoDialog(member: Member, onDismiss: () -> Unit) {
    if (member.photoUrl.isBlank()) return
    ZoomableImageDialog(
        title = member.fullName,
        subtitle = "Profile photo",
        imageModel = member.photoUrl,
        circular = true,
        onDismiss = onDismiss
    )
}

@Composable
fun ZoomableImageDialog(
    title: String,
    subtitle: String,
    imageModel: Any,
    circular: Boolean = false,
    contentScale: ContentScale = ContentScale.Crop,
    imageActionLabel: String? = null,
    imageActionEnabled: Boolean = true,
    onImageAction: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var scale by remember(imageModel) { mutableFloatStateOf(1f) }
    var offset by remember(imageModel) { mutableStateOf(Offset.Zero) }
    fun updateScale(value: Float) {
        scale = value.coerceIn(1f, 5f)
        if (scale == 1f) offset = Offset.Zero
    }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        updateScale(scale * zoomChange)
        if (scale > 1f) offset += panChange
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 12.dp
        ) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close profile photo")
                    }
                }
                Spacer(Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(if (circular) CircleShape else RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .pointerInput(imageModel) {
                            detectTapGestures(onDoubleTap = {
                                updateScale(if (scale > 1f) 1f else 2.5f)
                            })
                        }
                        .transformable(transformState)
                ) {
                    AsyncImage(
                        model = imageModel,
                        contentDescription = "$title enlarged image",
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y
                            ),
                        contentScale = contentScale
                    )
                    if (imageActionLabel != null && onImageAction != null) {
                        Button(
                            onClick = onImageAction,
                            enabled = imageActionEnabled,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(16.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(imageActionLabel)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    FilledTonalIconButton(onClick = { updateScale(scale - 0.5f) }, enabled = scale > 1f) {
                        Icon(Icons.Rounded.ZoomOut, contentDescription = "Zoom out")
                    }
                    TextButton(onClick = { updateScale(1f) }, Modifier.padding(horizontal = 8.dp)) {
                        Text("${(scale * 100).toInt()}%")
                    }
                    FilledTonalIconButton(onClick = { updateScale(scale + 0.5f) }, enabled = scale < 5f) {
                        Icon(Icons.Rounded.ZoomIn, contentDescription = "Zoom in")
                    }
                }
                Text(
                    "Pinch or double-tap to zoom • Drag to move",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun StatusPill(status: MemberStatus, modifier: Modifier = Modifier) {
    val (container, content) = when (status) {
        MemberStatus.ACTIVE -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        MemberStatus.EXPIRING_SOON -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        MemberStatus.EXPIRED -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        MemberStatus.UPCOMING -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        MemberStatus.SUSPENDED -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(modifier, shape = RoundedCornerShape(99.dp), color = container) {
        Text(status.pretty(), modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), color = content, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun AppCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val clickableModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Surface(
        modifier = modifier
            .shadow(8.dp, CardShape, ambientColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.035f), spotColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            .then(clickableModifier),
        shape = CardShape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))
    ) {
        Column(Modifier.padding(18.dp), content = content)
    }
}

@Composable
fun SectionHeading(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) TextButton(onClick = onAction) { Text(action, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
fun MetricCard(label: String, value: String, supporting: String, accent: Color, modifier: Modifier = Modifier) {
    AppCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
            Spacer(Modifier.width(7.dp))
            Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(14.dp))
        Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(3.dp))
        Text(supporting, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun MemberRow(member: Member, status: MemberStatus, subtitle: String, onClick: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stackStatus = maxWidth < 360.dp
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MemberAvatar(member)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(member.fullName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (stackStatus) {
                    Spacer(Modifier.height(6.dp))
                    StatusPill(status)
                }
            }
            if (!stackStatus) {
                Spacer(Modifier.width(8.dp))
                StatusPill(status)
            }
        }
    }
}

@Composable
fun SearchBox(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        trailingIcon = if (value.isNotEmpty()) ({ IconButton(onClick = { onValueChange("") }) { Icon(Icons.Rounded.Close, contentDescription = "Clear search") } }) else null,
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.72f)
        )
    )
}

@Composable
fun InitialIcon(label: String, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.primary) {
    Box(
        modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(tint.copy(alpha = 0.11f)),
        contentAlignment = Alignment.Center
    ) {
        Text(label.take(2).uppercase(), color = tint, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun TimelineRow(event: ActivityEvent, isLast: Boolean = false) {
    Row(Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
            if (!isLast) Box(Modifier.width(2.dp).height(54.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.padding(bottom = 16.dp).weight(1f)) {
            Text(event.title, style = MaterialTheme.typography.titleMedium)
            Text(event.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(event.occurredAt.toUiDateTime(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun EmptyState(title: String, message: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(58.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Text("M", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null && onAction != null) TextButton(onClick = onAction) { Text(action) }
    }
}

fun LocalDate.toUiDate(): String = format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
fun LocalDateTime.toUiDateTime(): String = format(DateTimeFormatter.ofPattern("dd MMM • hh:mm a"))
