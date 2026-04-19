package com.example.mdworkspace.ui.workspace

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mdworkspace.MdWorkspaceApplication
import com.example.mdworkspace.R
import com.example.mdworkspace.data.local.FavoriteEntity
import com.example.mdworkspace.data.local.RecentDocumentEntity
import com.example.mdworkspace.data.local.RepoEntryEntity

data class TreeNode(
    val entry: RepoEntryEntity,
    val depth: Int
)

@Composable
fun WorkspaceScreen(
    path: String,
    onOpenDirectory: (String) -> Unit,
    onOpenDocument: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit
) {
    val app = LocalContext.current.applicationContext as MdWorkspaceApplication
    val viewModel: WorkspaceViewModel = viewModel(
        key = "workspace-$path",
        factory = WorkspaceViewModel.factory(path, app.container.repository)
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val treeNodes = flattenTree(
        entriesByParent = state.entriesByParent,
        expandedDirectories = state.expandedDirectories,
        parent = path,
        depth = 0
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.config.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${state.config.branch} / ${path.ifBlank { "." }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = viewModel::refreshExpandedTree) {
                Icon(
                    painter = painterResource(R.drawable.ic_tabler_refresh),
                    contentDescription = "刷新",
                    tint = Color(0xFF1F2421)
                )
            }
            IconButton(onClick = onOpenSettings) {
                Icon(
                    painter = painterResource(R.drawable.ic_tabler_settings),
                    contentDescription = "设置",
                    tint = Color(0xFF1F2421)
                )
            }
        }

        state.message?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.tertiary,
                style = MaterialTheme.typography.bodySmall
            )
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (state.favorites.isNotEmpty() && path == state.config.normalizedRootPath) {
                item { SectionTitle("收藏") }
                items(state.favorites, key = { "${it.projectCode}/${it.docId}" }) { favorite ->
                    FavoriteRow(favorite = favorite) {
                        viewModel.openFavorite(favorite, onOpenDocument)
                    }
                }
                item { Spacer(modifier = Modifier.height(8.dp)) }
            }

            if (state.recentDocuments.isNotEmpty() && path == state.config.normalizedRootPath) {
                item { SectionTitle("最近打开") }
                items(state.recentDocuments, key = { "${it.projectCode}/${it.docId}/${it.docVersion}" }) { recent ->
                    RecentRow(recent = recent) { onOpenDocument(recent.path) }
                }
                item { Spacer(modifier = Modifier.height(8.dp)) }
            }

            item { SectionTitle("文件树") }
            if (treeNodes.isEmpty() && !state.isLoading) {
                item {
                    Text(
                        text = "这里还没有可显示的文件夹或 Markdown 文档。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            }
            items(treeNodes, key = { it.entry.path }) { node ->
                RepoEntryRow(
                    node = node,
                    expanded = node.entry.path in state.expandedDirectories,
                    onClick = {
                        if (node.entry.type == "dir") {
                            viewModel.toggleDirectory(node.entry)
                        } else {
                            onOpenDocument(node.entry.path)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun FileTreePanel(
    rootPath: String,
    onOpenDocument: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val app = LocalContext.current.applicationContext as MdWorkspaceApplication
    val viewModel: WorkspaceViewModel = viewModel(
        key = "tree-$rootPath",
        factory = WorkspaceViewModel.factory(rootPath, app.container.repository)
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val treeNodes = flattenTree(
        entriesByParent = state.entriesByParent,
        expandedDirectories = state.expandedDirectories,
        parent = rootPath,
        depth = 0
    )

    Column(modifier = modifier.padding(14.dp)) {
        Text(
            text = "文件树",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = state.config.displayName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        TextButton(onClick = viewModel::refreshExpandedTree) {
            Icon(
                painter = painterResource(R.drawable.ic_tabler_refresh),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = Color(0xFF1F2421)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("刷新")
        }
        LazyColumn {
            items(treeNodes, key = { it.entry.path }) { node ->
                RepoEntryRow(
                    node = node,
                    expanded = node.entry.path in state.expandedDirectories,
                    onClick = {
                        if (node.entry.type == "dir") {
                            viewModel.toggleDirectory(node.entry)
                        } else {
                            onOpenDocument(node.entry.path)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)
    )
}

@Composable
private fun RepoEntryRow(node: TreeNode, expanded: Boolean, onClick: () -> Unit) {
    val entry = node.entry
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = (node.depth * 18).dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (entry.type == "dir") {
            Icon(
                painter = painterResource(
                    if (expanded) R.drawable.ic_tabler_chevron_down else R.drawable.ic_tabler_chevron_right
                ),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = Color(0xFF1F2421)
            )
            Spacer(modifier = Modifier.width(6.dp))
        } else {
            Spacer(modifier = Modifier.width(24.dp))
        }
        Icon(
            painter = painterResource(
                when {
                    entry.type != "dir" -> R.drawable.ic_tabler_file_text
                    expanded -> R.drawable.ic_tabler_folder_open
                    else -> R.drawable.ic_tabler_folder
                }
            ),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = Color(0xFF1F2421)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (node.depth == 0) {
                Text(
                    text = entry.path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
    HorizontalDivider()
}

@Composable
private fun FavoriteRow(favorite: FavoriteEntity, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_tabler_star_filled),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = Color(0xFF1F2421)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = favorite.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            Text(
                text = "${favorite.docId}  ${favorite.lastKnownVersion.orEmpty()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RecentRow(recent: RecentDocumentEntity, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_tabler_note),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = Color(0xFF1F2421)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = recent.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            Text(
                text = "${recent.docId}@${recent.docVersion}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun flattenTree(
    entriesByParent: Map<String, List<RepoEntryEntity>>,
    expandedDirectories: Set<String>,
    parent: String,
    depth: Int
): List<TreeNode> {
    val entries = entriesByParent[parent].orEmpty()
    return buildList {
        entries.forEach { entry ->
            add(TreeNode(entry = entry, depth = depth))
            if (entry.type == "dir" && entry.path in expandedDirectories) {
                addAll(
                    flattenTree(
                        entriesByParent = entriesByParent,
                        expandedDirectories = expandedDirectories,
                        parent = entry.path,
                        depth = depth + 1
                    )
                )
            }
        }
    }
}
