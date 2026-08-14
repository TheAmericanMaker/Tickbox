// SPDX-FileCopyrightText: 2026 James Sesler
// SPDX-License-Identifier: GPL-3.0-or-later

package com.theamericanmaker.tickbox.ui.edit

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import java.io.File

private const val MAX_IMAGES = 5

@Composable
fun ImageAttachmentRow(
    images: List<NoteImageUiState>,
    getImageFile: (String) -> File,
    onAddImage: () -> Unit,
    onRemoveImage: (Int) -> Unit,
    onImageClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onTakePhoto: (() -> Unit)? = null,
    showOcrBadge: Boolean = false,
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        images.forEachIndexed { index, image ->
            Box {
                val file = getImageFile(image.filePath)
                val bitmap = remember(image.filePath) {
                    if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Attached image ${index + 1}",
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onImageClick(index) },
                        contentScale = ContentScale.Crop,
                    )
                }
                // Only advertise text extraction when this build can actually do it.
                if (showOcrBadge) {
                    Icon(
                        imageVector = Icons.Filled.DocumentScanner,
                        contentDescription = "Tap to extract text",
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(4.dp)
                            .size(16.dp)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), CircleShape),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(
                    onClick = { onRemoveImage(index) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(20.dp)
                        .background(MaterialTheme.colorScheme.errorContainer, CircleShape),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Remove image ${index + 1}",
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }

        if (images.size < MAX_IMAGES) {
            AttachmentTile(
                icon = Icons.Filled.AddPhotoAlternate,
                label = "Gallery",
                contentDescription = "Add from gallery",
                onClick = onAddImage,
            )
            if (onTakePhoto != null) {
                AttachmentTile(
                    icon = Icons.Filled.CameraAlt,
                    label = "Camera",
                    contentDescription = "Take photo",
                    onClick = onTakePhoto,
                )
            }
        }
    }
}

@Composable
private fun AttachmentTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
) {
    OutlinedCard(onClick = onClick, modifier = Modifier.size(80.dp)) {
        Box(modifier = Modifier.size(80.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
