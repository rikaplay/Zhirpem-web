package com.RIKAPLAY.zhirpem_app

import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FeedViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    
    private val _postsList = MutableStateFlow<List<Post>>(emptyList())
    val postsList: StateFlow<List<Post>> = _postsList.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var lastVisiblePost: com.google.firebase.firestore.DocumentSnapshot? = null
    private var isLastPage = false
    private val PAGE_SIZE = 10L

    init {
        fetchPosts(isRefresh = true)
    }

    fun fetchPosts(isRefresh: Boolean = false) {
        if (_isLoading.value || (isLastPage && !isRefresh)) return

        if (isRefresh) {
            _isRefreshing.value = true
            lastVisiblePost = null
            isLastPage = false
        } else {
            _isLoading.value = true
        }

        var query = db.collection("zhirpem_posts")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(PAGE_SIZE)

        lastVisiblePost?.let {
            query = query.startAfter(it)
        }

        query.get()
            .addOnSuccessListener { snapshot ->
                val newPosts = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(Post::class.java)?.copy(id = doc.id)
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
                
                if (snapshot.size() < PAGE_SIZE) {
                    isLastPage = true
                }
            }
            .addOnFailureListener { error ->
                _errorMessage.value = "Ошибка загрузки: ${error.message}"
                _isLoading.value = false
                _isRefreshing.value = false
            }
    }
}
