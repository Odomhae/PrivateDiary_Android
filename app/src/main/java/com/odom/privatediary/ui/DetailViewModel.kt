package com.odom.privatediary.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.odom.privatediary.data.DiaryDatabase
import com.odom.privatediary.data.DiaryRepository
import com.odom.privatediary.data.EntryEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DetailViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = DiaryRepository(DiaryDatabase.get(app).entryDao())

    private val _entry = MutableStateFlow<EntryEntity?>(null)
    val entry: StateFlow<EntryEntity?> = _entry.asStateFlow()

    fun load(id: String) {
        viewModelScope.launch { _entry.value = repo.entry(id) }
    }

    fun saveTranscript(id: String, transcript: String) {
        viewModelScope.launch {
            repo.updateTranscript(id, transcript)
            _entry.value = repo.entry(id)
        }
    }

    fun delete(id: String, onDeleted: () -> Unit) {
        viewModelScope.launch {
            repo.deleteEntry(id)
            onDeleted()
        }
    }
}
