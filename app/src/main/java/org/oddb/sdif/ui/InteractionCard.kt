package org.oddb.sdif.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.oddb.sdif.data.severityColor

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InteractionCard(
    severityScore: Int,
    drugLabel: String,
    severityIndicator: String,
    severityLabel: String,
    interactionType: String?,
    explanation: String?,
    description: String,
    source: String,
    fiHint: String?,
    comboHint: String?
) {
    var isExpanded by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val copyableText = buildString {
        appendLine(drugLabel)
        if (source.isNotEmpty()) appendLine("Quelle: $source")
        appendLine("Einstufung: $severityLabel")
        if (interactionType != null) appendLine("Typ: $interactionType")
        if (!explanation.isNullOrEmpty()) appendLine(explanation)
        appendLine(description)
        if (!fiHint.isNullOrEmpty()) appendLine(fiHint)
        if (!comboHint.isNullOrEmpty()) appendLine(comboHint)
    }.trim()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (description.length > 150) isExpanded = !isExpanded },
                onLongClick = { showMenu = true }
            ),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // Severity color bar
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(severityScore.severityColor)
            )

            Column(
                modifier = Modifier
                    .padding(12.dp)
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        drugLabel,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        if (source.isNotEmpty()) {
                            val badgeColor = if (source == "EPha") Color(0xFFE91E63) else Color(0xFF2196F3)
                            Text(
                                source,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = badgeColor,
                                modifier = Modifier
                                    .background(badgeColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            severityLabel,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            modifier = Modifier
                                .background(severityScore.severityColor, CircleShape)
                                .padding(horizontal = 10.dp, vertical = 3.dp)
                        )
                    }
                }

                // Interaction type
                if (interactionType != null) {
                    val typeLabel = when (interactionType) {
                        "substance" -> "Substanz-Match"
                        "class-level" -> "ATC-Klasse"
                        "epha" -> "EPha"
                        "CYP" -> "CYP-Enzym"
                        else -> interactionType
                    }
                    Text(typeLabel, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                // Explanation
                if (!explanation.isNullOrEmpty()) {
                    Text(
                        explanation,
                        fontSize = 12.sp,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Description
                Text(
                    description,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    maxLines = if (isExpanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.animateContentSize()
                )

                if (description.length > 150) {
                    Text(
                        if (isExpanded) "weniger" else "mehr anzeigen",
                        fontSize = 12.sp,
                        color = Color(0xFF2196F3),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                // FI quality hint
                if (!fiHint.isNullOrEmpty()) {
                    Text(
                        fiHint,
                        fontSize = 11.sp,
                        color = Color(0xFFFF9800),
                        modifier = Modifier
                            .background(Color(0xFFFFF9C4).copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                // Combo hint
                if (!comboHint.isNullOrEmpty()) {
                    Text(
                        comboHint,
                        fontSize = 11.sp,
                        color = Color(0xFF4CAF50),
                        modifier = Modifier
                            .background(Color(0xFF4CAF50).copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }

        // Context menu
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("Alles kopieren") },
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("interaction", copyableText))
                    Toast.makeText(context, "Kopiert", Toast.LENGTH_SHORT).show()
                    showMenu = false
                }
            )
            DropdownMenuItem(
                text = { Text("Feedback") },
                onClick = {
                    val subject = Uri.encode(drugLabel)
                    val body = Uri.encode(copyableText)
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:zdavatz@ywesee.com?subject=$subject&body=$body")
                    }
                    context.startActivity(intent)
                    showMenu = false
                }
            )
        }
    }
}
