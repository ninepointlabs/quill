package com.ninepointlabs.quill

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Main : NavKey
@Serializable data class ThreadView(val rootEventId: String) : NavKey
