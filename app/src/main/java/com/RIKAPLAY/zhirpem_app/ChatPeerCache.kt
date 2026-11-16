package com.RIKAPLAY.zhirpem_app

import com.RIKAPLAY.zhirpem_app.webrtc.peerOfChat
import java.util.concurrent.ConcurrentHashMap

/**
 * Определяет собеседника в чате.
 *
 * Обычные чаты называются «userA_userB», и собеседника можно получить из названия.
 * Но часть чатов (созданных кнопкой «Написать» в профиле старыми версиями) получила случайный
 * идентификатор вида «v4wqLjHiD3GPNVnPMQBf». Из него собеседника не вычислить, поэтому
 * для таких чатов берём участников из самого документа чата и запоминаем здесь.
 */
object ChatPeerCache {
    private val peers = ConcurrentHashMap<String, String>()

    /** Запоминает собеседника по списку участников чата. */
    fun remember(chatId: String, me: String, participants: List<String>?) {
        if (participants.isNullOrEmpty()) return
        val peer = participants.firstOrNull { it != me } ?: me // чат с самим собой
        if (peer.isNotBlank()) peers[chatId] = peer
    }

    fun rememberPeer(chatId: String, peer: String) {
        if (peer.isNotBlank()) peers[chatId] = peer
    }

    /** Собеседник: из кеша (по участникам чата), иначе из названия чата. */
    fun peerOf(chatId: String, me: String): String = peers[chatId] ?: peerOfChat(chatId, me)
}
