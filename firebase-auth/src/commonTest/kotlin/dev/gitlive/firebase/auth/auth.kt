/*
 * Copyright (c) 2020 GitLive Ltd.  Use of this source code is governed by the Apache 2.0 license.
 */

package dev.gitlive.firebase.auth

import dev.gitlive.firebase.*
import kotlin.random.Random
import kotlin.test.*

expect val context: Any
expect fun runTest(test: suspend () -> Unit)

class FirebaseAuthTest {

    @BeforeTest
    fun initializeFirebase() {
        Firebase
            .takeIf { Firebase.apps(context).isEmpty() }
            ?.initialize(
                context,
                FirebaseOptions(
                    applicationId = "1:846484016111:ios:dd1f6688bad7af768c841a",
                    apiKey = "AIzaSyCK87dcMFhzCz_kJVs2cT2AVlqOTLuyWV0",
                    databaseUrl = "https://fir-kotlin-sdk.firebaseio.com",
                    storageBucket = "fir-kotlin-sdk.appspot.com",
                    projectId = "fir-kotlin-sdk"
                )
            )
    }

    @Test
    fun testSignInWithUsernameAndPassword() = runTest {
        val result = Firebase.auth.signInWithEmailAndPassword("test@test.com", "test123")
        assertEquals("mn8kgIFnxLO7il8GpTa5g0ObP6I2", result.user!!.uid)
    }

    @Test
    fun testCreateUserWithEmailAndPassword() = runTest {
        val email = "test+${Random.nextInt(100000)}@test.com"
        val createResult = Firebase.auth.createUserWithEmailAndPassword(email, "test123")
        assertNotEquals(null, createResult.user?.uid)
        assertEquals(null, createResult.user?.displayName)
        assertEquals(null, createResult.user?.phoneNumber)
        assertEquals(email, createResult.user?.email)

        val signInResult = Firebase.auth.signInWithEmailAndPassword(email, "test123")
        assertEquals(createResult.user?.uid, signInResult.user?.uid)

        signInResult.user!!.delete()
    }

    @Test
    fun testFetchSignInMethods() = runTest {
        val email = "test+${Random.nextInt(100000)}@test.com"
        var signInMethodResult = Firebase.auth.fetchSignInMethodsForEmail(email)
        assertEquals(emptyList(), signInMethodResult.signInMethods)
        Firebase.auth.createUserWithEmailAndPassword(email, "test123")
        signInMethodResult = Firebase.auth.fetchSignInMethodsForEmail(email)
        assertEquals(listOf("password"), signInMethodResult.signInMethods)

        Firebase.auth.signInWithEmailAndPassword(email, "test123").user!!.delete()
    }

    @Test
    fun testSendEmailVerification() = runTest {
        val email = "test+${Random.nextInt(100000)}@test.com"
        val createResult = Firebase.auth.createUserWithEmailAndPassword(email, "test123")
        assertNotEquals(null, createResult.user?.uid)
        createResult.user!!.sendEmailVerification()

        createResult.user!!.delete()
    }

    @Test
    fun sendPasswordResetEmail() = runTest {
        val email = "test+${Random.nextInt(100000)}@test.com"
        val createResult = Firebase.auth.createUserWithEmailAndPassword(email, "test123")
        assertNotEquals(null, createResult.user?.uid)

        Firebase.auth.sendPasswordResetEmail(email)

        createResult.user!!.delete()
    }

    @Test
    fun testSignInWithCredential() = runTest {
        val credential = EmailAuthProvider.credentialWithEmail("test@test.com", "test123")
        val result = Firebase.auth.signInWithCredential(credential)
        assertEquals("mn8kgIFnxLO7il8GpTa5g0ObP6I2", result.user!!.uid)

    }
}