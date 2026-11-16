package com.RIKAPLAY.zhirpem_app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot

/**
 * Хранит подписки Firestore и отключает их, когда экран закрывается.
 * Раньше подписки, созданные внутри LaunchedEffect, жили до конца работы приложения
 * и продолжали читать базу (и тратить батарею, трафик и квоту) после ухода с экрана.
 */
class ListenerBag {
    private val items = mutableListOf<ListenerRegistration>()

    fun listen(
        ref: DocumentReference,
        block: (DocumentSnapshot?, FirebaseFirestoreException?) -> Unit
    ) {
        items += ref.addSnapshotListener { snapshot, error -> block(snapshot, error) }
    }

    fun listen(
        query: Query,
        block: (QuerySnapshot?, FirebaseFirestoreException?) -> Unit
    ) {
        items += query.addSnapshotListener { snapshot, error -> block(snapshot, error) }
    }

    /** Отключает все подписки (вызывается и при смене ключа эффекта, и при закрытии экрана). */
    fun clear() {
        items.forEach { it.remove() }
        items.clear()
    }
}

@Composable
fun rememberListenerBag(): ListenerBag {
    val bag = remember { ListenerBag() }
    DisposableEffect(bag) {
        onDispose { bag.clear() }
    }
    return bag
}
