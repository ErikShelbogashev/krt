package krt.app.scene

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import krt.app.shell.KrtIcons
import krt.app.shell.SectionHeader
import krt.domain.scene.model.ObjectGroup
import krt.domain.scene.model.ObjectSummary
import krt.domain.scene.model.SceneCatalog
import krt.domain.scene.model.Traceable
import krt.platform.compose.toComposeColor
import krt.platform.math.Point3

/**
 * Scene tab of the inspector: the objects, large and named ones one by one, the rest in folded groups,
 * and step buttons that move the selected object (the first one until another is picked) by [speed] along an axis.
 */
@Composable
fun SceneTab(objects: List<Traceable>, speed: Double, onMove: (String, Point3) -> Unit, modifier: Modifier = Modifier) {
    val catalog = remember(objects) { SceneCatalog.of(objects) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    val selected = catalog.find(selectedId) ?: catalog.featured.firstOrNull()
    var openGroups by remember { mutableStateOf(emptySet<String>()) }
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader("Objects · ${catalog.size}")
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            objectRows(catalog.featured, selected?.id, onSelect = { selectedId = it })
            catalog.groups.forEach { group ->
                val open = group.title in openGroups
                item(key = "group:${group.title}") {
                    GroupRow(group, open, onToggle = { openGroups = openGroups.toggled(group.title) })
                }
                if (open) objectRows(group.objects, selected?.id, onSelect = { selectedId = it })
            }
        }
        selected?.let { MoveCard(it, speed, onMove) }
    }
}

private fun LazyListScope.objectRows(objects: List<ObjectSummary>, selectedId: String?, onSelect: (String) -> Unit) {
    items(objects, key = { it.id }) { summary ->
        ObjectRow(summary, selected = summary.id == selectedId, onClick = { onSelect(summary.id) })
    }
}

@Composable
private fun ObjectRow(summary: ObjectSummary, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val background = if (selected) colors.secondaryContainer else colors.surfaceContainerLow
    Row(
        Modifier
            .fillMaxWidth()
            .background(background, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(14.dp)
                .background(summary.color.toComposeColor(), CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
        )
        Text(
            summary.name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            summary.position.short(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GroupRow(group: ObjectGroup, open: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (open) KrtIcons.ExpandLess else KrtIcons.ExpandMore,
            contentDescription = if (open) "Fold" else "Unfold",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text("${group.title} · ${group.objects.size}", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.weight(1f))
    }
}

private fun Set<String>.toggled(item: String): Set<String> = if (item in this) this - item else this + item

private fun Point3.short(): String = "%.1f, %.1f, %.1f".format(java.util.Locale.ROOT, x, y, z)
