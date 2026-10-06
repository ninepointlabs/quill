package com.ninepointlabs.quill.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ninepointlabs.quill.data.DataRepository
import com.ninepointlabs.quill.network.SignedEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

sealed interface FeedUiState {
    object Loading : FeedUiState
    data class Error(val throwable: Throwable) : FeedUiState
    data class Loaded(val notes: List<SignedEvent>) : FeedUiState
}

class MainScreenViewModel : ViewModel() {
    private val json = Json { ignoreUnknownKeys = true }

    private val _uiState = MutableStateFlow<FeedUiState>(FeedUiState.Loading)
    val uiState: StateFlow<FeedUiState> = _uiState

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _isPublishing = MutableStateFlow(false)
    val isPublishing: StateFlow<Boolean> = _isPublishing

    private val _publishError = MutableStateFlow<String?>(null)
    val publishError: StateFlow<String?> = _publishError

    init {
        // Subscribe to a known popular pubkey or feed to get some data
        DataRepository.subscribeToFollowFeed("npub1sg6plzptd64u62a878hep2kev88swjh3tw00gjsfl8f237lmu63q0uf63m")
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                // Give the DB a slight delay to ingest initial subscription events
                delay(300)

                DataRepository.getFeed(50).collect { jsonString ->
                    val events = try {
                        json.decodeFromString<List<SignedEvent>>(jsonString)
                    } catch (e: Exception) {
                        emptyList()
                    }
                    _uiState.update { FeedUiState.Loaded(events) }
                }
            } catch (e: Exception) {
                _uiState.update { FeedUiState.Error(e) }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun loadMore() {
        // Pagination logic here (stub)
    }

    fun publishNote(content: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isPublishing.value = true
            _publishError.value = null
            try {
                val result = DataRepository.publishNote(content)
                if (result != null) {
                    onSuccess()
                    refresh() // Load the newly published note
                } else {
                    _publishError.value = "Failed to publish"
                }
            } catch (e: Exception) {
                _publishError.value = e.message ?: "Unknown error"
            } finally {
                _isPublishing.value = false
            }
        }
    }

    fun clearError() {
        _publishError.value = null
    }
}
