package com.example.justfan.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.justfan.data.model.CollectionEntity
import com.example.justfan.data.model.PostEntity
import com.example.justfan.ui.components.ContentCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    favoritePosts: List<PostEntity>,
    collections: List<CollectionEntity>,
    onPostClick: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onCreateCollection: (String) -> Unit,
    onDeleteCollection: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var isCreateDialogOpen by remember { mutableStateOf(false) }
    var newCollectionName by remember { mutableStateOf("") }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Bookmarks & Collections", fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    if (selectedTab == 1) {
                        IconButton(
                            onClick = { isCreateDialogOpen = true },
                            modifier = Modifier.testTag("create_collection_button")
                        ) {
                            Icon(Icons.Default.CreateNewFolder, contentDescription = "New Collection")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Favorites (${favoritePosts.size})", fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.testTag("tab_favorites")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Collections (${collections.size})", fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.testTag("tab_collections")
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (selectedTab == 0) {
                if (favoritePosts.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No saved favorites yet.\nTap the heart on any gallery to bookmark it!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 160.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 80.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(favoritePosts, key = { it.id }) { post ->
                            ContentCard(
                                post = post,
                                isFavorite = true,
                                onPostClick = { onPostClick(post.id) },
                                onToggleFavorite = { onToggleFavorite(post.id) },
                                onDownloadClick = { onPostClick(post.id) }
                            )
                        }
                    }
                }
            } else {
                if (collections.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("No custom collections created.")
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(onClick = { isCreateDialogOpen = true }) {
                                Text("Create First Collection")
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 80.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(collections, key = { it.id }) { col ->
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                modifier = Modifier
                                    .testTag("collection_item_${col.id}")
                                    .fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Folder,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(28.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = col.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "Saved creator galleries collection",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    IconButton(onClick = { onDeleteCollection(col.id) }) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (isCreateDialogOpen) {
        AlertDialog(
            onDismissRequest = { isCreateDialogOpen = false },
            title = { Text("Create New Collection") },
            text = {
                OutlinedTextField(
                    value = newCollectionName,
                    onValueChange = { newCollectionName = it },
                    label = { Text("Collection Name") },
                    placeholder = { Text("e.g. Cosplay Best, Wallpapers") },
                    singleLine = true,
                    modifier = Modifier.testTag("collection_name_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newCollectionName.isNotBlank()) {
                            onCreateCollection(newCollectionName.trim())
                            newCollectionName = ""
                            isCreateDialogOpen = false
                        }
                    },
                    modifier = Modifier.testTag("confirm_create_collection")
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { isCreateDialogOpen = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
