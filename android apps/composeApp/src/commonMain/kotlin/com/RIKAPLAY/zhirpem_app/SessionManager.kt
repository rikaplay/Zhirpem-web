package com.RIKAPLAY.zhirpem_app

expect class SessionManager() {
    var isLoggedIn: Boolean
    var username: String?
    var name: String?
    
    fun logout()
}
