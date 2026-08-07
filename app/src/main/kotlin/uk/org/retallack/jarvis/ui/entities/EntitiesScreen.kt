package uk.org.retallack.jarvis.ui.entities

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import uk.org.retallack.jarvis.data.db.entity.HaEntityDb

@Composable
fun EntitiesScreen(
    onEntityClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EntitiesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    Column(modifier = modifier.fillMaxSize()) {
        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.setSearchQuery(it) },
            placeholder = { Text("Search entities...") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            singleLine = true,
            label = { Text("Search") },
        )

        // Domain filters
        val domains = state.entities.map { it.domain }.distinct().sorted()
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                FilterChip(
                    selected = state.selectedDomain == null,
                    onClick = { viewModel.setDomainFilter(null) },
                    label = { Text("All") },
                )
            }
            items(domains) { domain ->
                FilterChip(
                    selected = state.selectedDomain == domain,
                    onClick = { viewModel.setDomainFilter(domain) },
                    label = { Text(domain) },
                )
            }
        }

        // Sync button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${state.entities.size} entities",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.isSyncing) {
                CircularProgressIndicator(modifier = Modifier.padding(8.dp))
            } else {
                IconButton(onClick = { viewModel.syncFromHa() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Sync from HA")
                }
            }
        }

        // Entity list
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(state.entities, key = { it.entityId }) { entity ->
                EntityListItem(
                    entity = entity,
                    onClick = { onEntityClick(entity.entityId) },
                    onFavouriteToggle = {
                        viewModel.toggleFavourite(entity.entityId, entity.isFavourite)
                    },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun EntityListItem(
    entity: HaEntityDb,
    onClick: () -> Unit,
    onFavouriteToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        modifier = modifier.clickable(onClick = onClick),
        headlineContent = {
            Text(entity.friendlyName ?: entity.entityId)
        },
        supportingContent = {
            Text(
                text = "${entity.domain} • ${entity.state}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            IconButton(onClick = onFavouriteToggle) {
                Icon(
                    imageVector = if (entity.isFavourite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = if (entity.isFavourite) "Remove favourite" else "Add favourite",
                    tint = if (entity.isFavourite) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        },
    )
}
