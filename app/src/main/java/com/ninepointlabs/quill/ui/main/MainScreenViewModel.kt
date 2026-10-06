package com.ninepointlabs.quill.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ninepointlabs.quill.data.DataRepository
import com.ninepointlabs.quill.network.SignedEvent
import com.ninepointlabs.quill.network.OmostrichConnectionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.coroutines.isActive

sealed interface FeedUiState {
    object Loading : FeedUiState
    data class Error(val message: String) : FeedUiState
    data class Loaded(val notes: List<SignedEvent>) : FeedUiState
}

class MainScreenViewModel : ViewModel() {
    private val json = Json { ignoreUnknownKeys = true }

    private val _uiState = MutableStateFlow<FeedUiState>(FeedUiState.Loading)
    val uiState: StateFlow<FeedUiState> = _uiState

    val connectionState: StateFlow<OmostrichConnectionState> = DataRepository.connectionState
    val feedError: StateFlow<String?> = DataRepository.feedError
    
    val profiles: StateFlow<Map<String, com.ninepointlabs.quill.network.Profile>> = DataRepository.profiles

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _isPublishing = MutableStateFlow(false)
    val isPublishing: StateFlow<Boolean> = _isPublishing

    private val _publishError = MutableStateFlow<String?>(null)
    val publishError: StateFlow<String?> = _publishError

    init {
        startPolling()
    }

    private fun startPolling() {
        viewModelScope.launch {
            pollFeed()
            DataRepository.newEvents.collect {
                pollFeed()
            }
        }
    }

    private suspend fun pollFeed() {
        try {
            DataRepository.getFeed(50).collect { jsonString ->
                val events = try {
                    json.decodeFromString<List<SignedEvent>>(jsonString)
                } catch (e: Exception) {
                    emptyList()
                }
                // Only update to Loaded if we actually have something, or if it's already Loaded
                if (events.isNotEmpty() || _uiState.value is FeedUiState.Loaded) {
                    _uiState.update { FeedUiState.Loaded(events) }
                    DataRepository.fetchMissingProfiles(events.map { it.pubkey })
                }
            }
        } catch (e: Exception) {
            _uiState.update { FeedUiState.Error(e.message ?: "Unknown error") }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            pollFeed()
            _isRefreshing.value = false
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
                    pollFeed() // Load the newly published note
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
