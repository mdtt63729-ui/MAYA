package com.aistudio.mj.wxyt.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aistudio.mj.wxyt.domain.chat.AppDatabase
import com.aistudio.mj.wxyt.domain.chat.ConversationEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).chatDao()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val conversations: StateFlow<List<ConversationEntity>> = _searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) {
                dao.getAllConversations()
            } else {
                dao.searchConversations(query.trim())
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            dao.deleteConversation(id)
        }
    }
}
