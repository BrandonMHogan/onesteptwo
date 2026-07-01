package com.onesteptwo.android.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.onesteptwo.data.ChildrenRepository
import com.onesteptwo.db.Children
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Scoped above the 4-tab NavHost (constructed once in AppNavigation) so Home/History/Progress
 * share one notion of "the active child" (REQ-031). Single-child families never see a switcher
 * affordance, but this still resolves the implicit active child for them.
 */
class ChildSelectionViewModel(private val childrenRepository: ChildrenRepository) : ViewModel() {

    private val _children = MutableStateFlow<List<Children>>(emptyList())
    val children: StateFlow<List<Children>> = _children.asStateFlow()

    private val _activeChild = MutableStateFlow<Children?>(null)
    val activeChild: StateFlow<Children?> = _activeChild.asStateFlow()

    init {
        viewModelScope.launch {
            childrenRepository.observeAll().collect { list ->
                _children.value = list
                val current = _activeChild.value
                _activeChild.value = when {
                    current != null && list.any { it.id == current.id } -> current
                    else -> list.firstOrNull()
                }
            }
        }
    }

    fun selectChild(child: Children) {
        _activeChild.value = child
    }
}

class ChildSelectionViewModelFactory(
    private val childrenRepository: ChildrenRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ChildSelectionViewModel(childrenRepository) as T
    }
}
