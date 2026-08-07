package uk.org.retallack.jarvis.ui.entities

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun EntityDetailScreen(
    entityId: String,
    modifier: Modifier = Modifier,
    viewModel: EntityDetailViewModel = hiltViewModel(),
) {
    val entity by viewModel.entity.collectAsState()
    val aliases by viewModel.aliases.collectAsState()
    var newAlias by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        entity?.let { ent ->
            // Entity info card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = ent.friendlyName ?: ent.entityId,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Entity ID: ${ent.entityId}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Domain: ${ent.domain}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "State: ${ent.state}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (ent.areaId != null) {
                        Text(
                            text = "Area: ${ent.areaId}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Aliases section
            Text(
                text = "Voice Shortcuts (Aliases)",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Add alias input
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = newAlias,
                    onValueChange = { newAlias = it },
                    label = { Text("New alias") },
                    placeholder = { Text("e.g. \"lounge light\"") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                IconButton(
                    onClick = {
                        if (newAlias.isNotBlank()) {
                            viewModel.addAlias(newAlias.trim())
                            newAlias = ""
                        }
                    },
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add alias")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Alias list
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(aliases, key = { it.id }) { alias ->
                    ListItem(
                        headlineContent = { Text(alias.alias) },
                        trailingContent = {
                            IconButton(onClick = { viewModel.removeAlias(alias.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Remove alias")
                            }
                        },
                    )
                    HorizontalDivider()
                }
            }

            // Push to HA button
            if (aliases.isNotEmpty()) {
                Button(
                    onClick = { viewModel.pushAliasesToHa() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Push Aliases to Home Assistant")
                }
            }
        } ?: run {
            Text("Loading...")
        }
    }
}
