package com.ninepointlabs.quill.ui.thread

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ninepointlabs.quill.data.DataRepository
import com.ninepointlabs.quill.network.SignedEvent
import com.ninepointlabs.quill.ui.main.NoteCard
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadScreen(
    rootEventId: String,
    onBack: () -> Unit,
    onThreadClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val json = remember { Json { ignoreUnknownKeys = true } }
    
    // Use State flows to keep things simple
    val rootNoteStr by remember(rootEventId) {
        DataRepository.getNoteById(rootEventId)
    }.collectAsStateWithLifecycle(initialValue = "[]")

    val threadNotesStr by remember(rootEventId) {
        DataRepository.getThread(rootEventId)
    }.collectAsStateWithLifecycle(initialValue = "[]")

    val profiles by DataRepository.profiles.collectAsStateWithLifecycle()

    var rootNote by remember { mutableStateOf<SignedEvent?>(null) }
    var replies by remember { mutableStateOf<List<SignedEvent>>(emptyList()) }

    LaunchedEffect(rootEventId) {
        DataRepository.subscribeToThread(rootEventId)
        DataRepository.fetchNoteFromRelays(rootEventId)
    }

    LaunchedEffect(rootNoteStr) {
        if (rootNoteStr != "[]") {
            try {
                val notes = json.decodeFromString<List<SignedEvent>>(rootNoteStr)
                if (notes.isNotEmpty()) {
                    rootNote = notes[0]
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    LaunchedEffect(threadNotesStr) {
        if (threadNotesStr != "[]") {
            try {
                val notes = json.decodeFromString<List<SignedEvent>>(threadNotesStr)
                // Filter out the root note itself just in case
                replies = notes.filter { it.id != rootEventId }.sortedBy { it.created_at }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { 
                    Text("Thread", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            if (rootNote == null && rootNoteStr == "[]") {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    rootNote?.let { note ->
                        item {
                            NoteCard(
                                note = note,
                                profile = profiles[note.pubkey],
                                onNoteClick = null
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outline,
                                thickness = 2.dp
                            )
                        }
                    }
                    
                    if (replies.isNotEmpty()) {
                        item {
                            Text(
                                "Replies",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                        items(replies, key = { it.id }) { reply ->
                            NoteCard(
                                note = reply,
                                profile = profiles[reply.pubkey],
                                onNoteClick = onThreadClick
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outline,
                                thickness = 1.dp
                            )
                        }
                    } else if (rootNote != null) {
                        item {
                            Text(
                                "No replies yet.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
