package com.example.jogostop

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

/**
 * Repositório simples para autenticação no Firebase.
 * - register: cria usuário
 * - login: entra com usuário existente
 * - logout: sai
 * - isLogged: verifica sessão atual
 */
class AuthRepository {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    suspend fun login(email: String, senha: String) {
        auth.signInWithEmailAndPassword(email, senha).await()
    }

    suspend fun register(email: String, senha: String) {
        auth.createUserWithEmailAndPassword(email, senha).await()
    }

    fun logout() {
        auth.signOut()
    }

    fun isLogged(): Boolean = auth.currentUser != null
}
