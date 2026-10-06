package com.ninepointlabs.quill.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.ninepointlabs.quill.network.SignedEvent
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    @Suppress("UNUSED_PARAMETER") onItemClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainScreenViewModel = viewModel { MainScreenViewModel() }
) {
    val feedState by viewModel.uiState.collectAsStateWithLifecycle()
    val myNotesState by viewModel.myNotesState.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val state = if (selectedTab == 2) myNotesState else feedState
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val isPublishing by viewModel.isPublishing.collectAsStateWithLifecycle()
    val publishError by viewModel.publishError.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val feedError by viewModel.feedError.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()

    var showBottomSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(publishError) {
        publishError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    val dotColor = when (connectionState) {
        com.ninepointlabs.quill.network.OmostrichConnectionState.CONNECTED_UNLOCKED -> androidx.compose.ui.graphics.Color(0xFF76A36B)
        com.ninepointlabs.quill.network.OmostrichConnectionState.CONNECTED_LOCKED -> androidx.compose.ui.graphics.Color(0xFFD9AE5B)
        com.ninepointlabs.quill.network.OmostrichConnectionState.DISCONNECTED -> androidx.compose.ui.graphics.Color(0xFFC9503F)
        com.ninepointlabs.quill.network.OmostrichConnectionState.CHECKING -> androidx.compose.ui.graphics.Color.Gray
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            Column {
                TopAppBar(
                    title = { 
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Quill", fontWeight = FontWeight.Light)
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(modifier = Modifier.size(8.dp).background(dotColor, CircleShape))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                    )
                )
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color(0xFF0F1715),
                    divider = {
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        )
                    },
                    indicator = { tabPositions ->
                        if (selectedTab < tabPositions.size) {
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                color = Color(0xFFD9AE5B)
                            )
                        }
                    }
                ) {
                    val tabs = listOf("Following", "Replies", "My Notes")
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { viewModel.selectTab(index) },
                            text = { 
                                Text(
                                    title, 
                                    color = if (selectedTab == index) Color(0xFFD9AE5B) else Color(0xFF5E6F67),
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                                ) 
                            }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showBottomSheet = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Compose Note")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            when (val s = state) {
                is FeedUiState.Loading -> {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                is FeedUiState.Error -> {
                    Text(
                        text = "Error: ${s.message}",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center).padding(16.dp)
                    )
                }
                is FeedUiState.Loaded -> {
                    val userPubkey = viewModel.userPubkeyHex.collectAsStateWithLifecycle().value
                    
                    val filteredNotes = remember(s.notes, selectedTab, userPubkey) {
                        when (selectedTab) {
                            1 -> s.notes.filter { note -> note.tags.any { it.isNotEmpty() && it[0] == "e" } }
                            else -> s.notes
                        }
                    }

                    if (filteredNotes.isEmpty()) {
                        val emptyText = when (selectedTab) {
                            1 -> "No replies yet."
                            2 -> "You haven't posted anything yet."
                            else -> feedError ?: "No notes in your feed yet."
                        }
                        Text(
                            text = emptyText,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Center).padding(16.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    } else {
                        val ptrState = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
                        PullToRefreshBox(
                            isRefreshing = isRefreshing,
                            onRefresh = { viewModel.refresh() },
                            state = ptrState,
                            indicator = {
                                androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator(
                                    modifier = Modifier.align(Alignment.TopCenter),
                                    isRefreshing = isRefreshing,
                                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                                    color = MaterialTheme.colorScheme.primary,
                                    state = ptrState
                                )
                            }
                        ) {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(filteredNotes, key = { it.id }) { note ->
                                    NoteCard(note, profiles[note.pubkey])
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outline,
                                        thickness = 1.dp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.onSurfaceVariant) }
        ) {
            ComposeNoteContent(
                isPublishing = isPublishing,
                onPublish = { content ->
                    viewModel.publishNote(content) {
                        coroutineScope.launch { sheetState.hide() }.invokeOnCompletion {
                            if (!sheetState.isVisible) {
                                showBottomSheet = false
                            }
                        }
                    }
                },
                onCancel = {
                    coroutineScope.launch { sheetState.hide() }.invokeOnCompletion {
                        if (!sheetState.isVisible) {
                            showBottomSheet = false
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun NoteCard(note: SignedEvent, profile: com.ninepointlabs.quill.network.Profile?) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val pictureUrl = profile?.picture
                    coil3.compose.AsyncImage(
                        model = pictureUrl,
                        imageLoader = com.ninepointlabs.quill.QuillApplication.imageLoader,
                        contentDescription = "Profile picture",
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0xFF26352F), CircleShape)
                            .clip(CircleShape),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        val bestName = profile?.bestName ?: (note.pubkey.take(6) + "..." + note.pubkey.takeLast(4))
                        Text(
                            text = bestName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (!profile?.nip05.isNullOrEmpty()) {
                            Text(
                                text = profile?.nip05 ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF5E6F67)
                            )
                        }
                    }
                }
                Text(
                    text = formatRelativeTime(note.created_at),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            val context = androidx.compose.ui.platform.LocalContext.current
            val parsedContent = remember(note.content, note.tags) { com.ninepointlabs.quill.utils.ContentParser.parse(note.content, note.tags) }
            val annotatedText = androidx.compose.ui.text.buildAnnotatedString {
                val text = parsedContent.text
                append(text)
                
                parsedContent.regularUrls.forEach { url ->
                    var index = text.indexOf(url)
                    while (index >= 0) {
                        addStyle(
                            style = androidx.compose.ui.text.SpanStyle(
                                color = MaterialTheme.colorScheme.primary,
                                textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                            ),
                            start = index,
                            end = index + url.length
                        )
                        addStringAnnotation(
                            tag = "url",
                            annotation = url,
                            start = index,
                            end = index + url.length
                        )
                        index = text.indexOf(url, index + 1)
                    }
                }
                
                parsedContent.nostrRefs.forEach { ref ->
                    var index = text.indexOf(ref)
                    while (index >= 0) {
                        addStyle(
                            style = androidx.compose.ui.text.SpanStyle(
                                color = Color(0xFFD4AF37),
                                textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                            ),
                            start = index,
                            end = index + ref.length
                        )
                        addStringAnnotation(
                            tag = "nostr",
                            annotation = ref,
                            start = index,
                            end = index + ref.length
                        )
                        index = text.indexOf(ref, index + 1)
                    }
                }
            }
            androidx.compose.foundation.text.ClickableText(
                text = annotatedText,
                style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                onClick = { offset ->
                    annotatedText.getStringAnnotations("nostr", offset, offset)
                        .firstOrNull()?.let { annotation ->
                            val url = "https://njump.me/${annotation.item.removePrefix("nostr:")}"
                            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                        }
                        ?: annotatedText.getStringAnnotations("url", offset, offset)
                            .firstOrNull()?.let { annotation ->
                                context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(annotation.item)))
                            }
                }
            )
            
            parsedContent.imageUrls.forEach { imageUrl ->
                Spacer(modifier = Modifier.height(12.dp))
                coil3.compose.SubcomposeAsyncImage(
                    model = imageUrl,
                    imageLoader = com.ninepointlabs.quill.QuillApplication.imageLoader,
                    contentDescription = "Attached image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    loading = {
                        Box(modifier = Modifier.fillMaxWidth().height(200.dp).background(Color(0xFF26352F)))
                    },
                    error = {
                        Box(modifier = Modifier.fillMaxWidth().height(200.dp).background(Color(0xFF26352F)))
                    }
                )
            }
            
            parsedContent.blossomMedia.filter { it.isImage }.forEach { media ->
                Spacer(modifier = Modifier.height(12.dp))
                coil3.compose.SubcomposeAsyncImage(
                    model = media.url,
                    imageLoader = com.ninepointlabs.quill.QuillApplication.imageLoader,
                    contentDescription = "Blossom image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    loading = {
                        Box(modifier = Modifier.fillMaxWidth().height(200.dp).background(Color(0xFF26352F)))
                    },
                    error = {
                        Box(modifier = Modifier.fillMaxWidth().height(200.dp).background(Color(0xFF26352F)))
                    }
                )
            }
            
            parsedContent.blossomMedia.filter { it.isVideo }.forEach { media ->
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clickable {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(media.url))
                            context.startActivity(intent)
                        },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF18231F))
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play Video",
                            modifier = Modifier.size(48.dp),
                            tint = Color(0xFFD9AE5B)
                        )
                    }
                }
            }
            
            parsedContent.linkUrl?.let { linkUrl ->
                Spacer(modifier = Modifier.height(12.dp))
                LinkPreviewCard(url = linkUrl)
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IconButton(onClick = { /* stub */ }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Reply", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { /* stub */ }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Share, contentDescription = "Repost", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { /* stub */ }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.FavoriteBorder, contentDescription = "Like", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { /* stub */ }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Star, contentDescription = "Zap", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

fun formatRelativeTime(timestampSeconds: Long): String {
    val now = System.currentTimeMillis() / 1000
    val diff = now - timestampSeconds
    return when {
        diff < 60 -> "${diff}s"
        diff < 3600 -> "${diff / 60}m"
        diff < 86400 -> "${diff / 3600}h"
        else -> "${diff / 86400}d"
    }
}

@Composable
fun ComposeNoteContent(
    isPublishing: Boolean,
    onPublish: (String) -> Unit,
    onCancel: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    val maxChars = 280

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onCancel,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
            ) {
                Text("Cancel")
            }
            Button(
                onClick = { onPublish(text) },
                enabled = text.isNotBlank() && text.length <= maxChars && !isPublishing,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.outline,
                    contentColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    disabledContentColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )
            ) {
                if (isPublishing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text("Post")
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = text,
            onValueChange = { if (it.length <= maxChars) text = it },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 150.dp),
            placeholder = { Text("What's happening?", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.background,
                unfocusedContainerColor = MaterialTheme.colorScheme.background,
                focusedTextColor = MaterialTheme.colorScheme.onBackground,
                unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                focusedBorderColor = MaterialTheme.colorScheme.outline,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            ),
            supportingText = {
                Text(
                    text = "${text.length} / $maxChars",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    color = if (text.length >= 250) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )
    }
}

@Composable
fun LinkPreviewCard(url: String) {
    var preview by remember { mutableStateOf<com.ninepointlabs.quill.utils.LinkPreview?>(null) }
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current

    LaunchedEffect(url) {
        preview = com.ninepointlabs.quill.utils.LinkPreviewFetcher.fetch(url)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { uriHandler.openUri(url) },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF18231F)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF26352F)),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val imageUrl = preview?.imageUrl
            if (imageUrl != null) {
                coil3.compose.SubcomposeAsyncImage(
                    model = imageUrl,
                    imageLoader = com.ninepointlabs.quill.QuillApplication.imageLoader,
                    contentDescription = "Preview Image",
                    modifier = Modifier
                        .size(60.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp)),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    loading = {
                        Box(modifier = Modifier.size(60.dp).background(Color(0xFF26352F)))
                    },
                    error = {
                        Box(modifier = Modifier.size(60.dp).background(Color(0xFF26352F)))
                    }
                )
                Spacer(modifier = Modifier.width(12.dp))
            } else if (preview == null) {
                Box(modifier = Modifier.size(60.dp).background(Color(0xFF26352F)).clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp)))
                Spacer(modifier = Modifier.width(12.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                if (preview == null) {
                    Box(modifier = Modifier.fillMaxWidth(0.7f).height(16.dp).background(Color(0xFF26352F)))
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(modifier = Modifier.fillMaxWidth(0.4f).height(12.dp).background(Color(0xFF26352F)))
                } else {
                    Text(
                        text = preview?.title ?: url,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFFECE4D0),
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    
                    val desc = preview?.description
                    if (!desc.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF5E6F67),
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = preview?.domain ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF5E6F67)
                    )
                }
            }
        }
    }
}
