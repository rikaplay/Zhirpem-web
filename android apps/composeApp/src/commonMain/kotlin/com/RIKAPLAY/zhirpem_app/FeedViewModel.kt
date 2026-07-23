package com.RIKAPLAY.zhirpem_app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.firestore.Direction
import dev.gitlive.firebase.firestore.DocumentSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FeedViewModel : ViewModel() {
    private val db = Firebase.firestore
    
    private val _postsList = MutableStateFlow<List<Post>>(emptyList())
    val postsList: StateFlow<List<Post>> = _postsList.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var lastVisiblePost: DocumentSnapshot? = null
    private var isLastPage = false
    private val PAGE_SIZE = 10L

    init {
        fetchPosts(isRefresh = true)
    }

    fun fetchPosts(isRefresh: Boolean = false) {
        if (_isLoading.value || (isLastPage && !isRefresh)) return

        viewModelScope.launch {
            try {
                if (isRefresh) {
                    _isRefreshing.value = true
                    lastVisiblePost = null
                    isLastPage = false
                } else {
                    _isLoading.value = true
                }

                var query = db.collection("zhirpem_posts")
                    .orderBy("timestamp", Direction.DESCENDING)
                    .limit(PAGE_SIZE)

                lastVisiblePost?.let {
                    query = query.startAfter(it)
                }

                val snapshot = query.get()
                val newPosts = snapshot.documents.map { doc ->
                    doc.data<Post>().copy(id = doc.id)
                }

                if (isRefresh) {
                    _postsList.value = newPosts
                    _isRefreshing.value = false
                } else {
                    _postsList.value = _postsList.value + newPosts
                    _isLoading.value = false
                }

                if (snapshot.documents.isNotEmpty()) {
                    lastVisiblePost = snapshot.documents[snapshot.documents.size - 1]
                }
                
                if (snapshot.documents.size < PAGE_SIZE) {
                    isLastPage = true
                }
            } catch (e: Exception) {
                _errorMessage.value = "Ошибка загрузки: ${e.message}"
                _isLoading.value = false
                _isRefreshing.value = false
            }
        }
    }
}
