package com.example.jogostop

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

class AuthRepository {

    private val auth = FirebaseAuth.getInstance()

    suspend fun login(email: String, senha: String) {
        auth.signInWithEmailAndPassword(email, senha).await()
    }

    suspend fun register(email: String, senha: String) {
        auth.createUserWithEmailAndPassword(email, senha).await()
    }

    fun logout() {
        auth.signOut()
    }

    fun isLogged(): Boolean {
        return auth.currentUser != null
    }
}
