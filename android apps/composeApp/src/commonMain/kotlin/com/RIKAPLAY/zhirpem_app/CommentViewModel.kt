package com.RIKAPLAY.zhirpem_app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.firestore.Direction
import dev.gitlive.firebase.firestore.FieldValue
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CommentViewModel : ViewModel() {

    private val db = Firebase.firestore
    private var commentsJob: Job? = null
    
    private val _comments = MutableStateFlow<List<Comment>>(emptyList())
    val comments: StateFlow<List<Comment>> = _comments.asStateFlow()

    fun listenToComments(postId: String) {
        if (postId.isEmpty()) return
        
        commentsJob?.cancel()
        
        commentsJob = viewModelScope.launch {
            try {
                db.collection("comments")
                    .whereEqualTo("postId", postId)
                    .orderBy("timestamp", Direction.ASCENDING)
                    .snapshots
                    .collect { snapshot ->
                        val commentList = snapshot.documents.map { doc ->
                            doc.data<Comment>().copy(id = doc.id)
                        }
                        _comments.value = commentList
                    }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun toggleLikeComment(commentId: String, currentUserId: String) {
        if (commentId.isEmpty() || currentUserId.isEmpty()) return
        
        val commentRef = db.collection("comments").document(commentId)
        val currentComment = _comments.value.find { it.id == commentId } ?: return
        val isAlreadyLiked = currentComment.likedBy.contains(currentUserId)

        viewModelScope.launch {
            try {
                if (isAlreadyLiked) {
                    commentRef.update(
                        "likesCount" to FieldValue.increment(-1),
                        "likedBy" to FieldValue.arrayRemove(currentUserId)
                    )
                } else {
                    commentRef.update(
                        "likesCount" to FieldValue.increment(1),
                        "likedBy" to FieldValue.arrayUnion(currentUserId)
                    )
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        commentsJob?.cancel()
    }
}
