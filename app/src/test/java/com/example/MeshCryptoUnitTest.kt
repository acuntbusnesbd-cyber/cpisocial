package com.example

import com.example.mesh.CryptoService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MeshCryptoUnitTest {

    @Test
    fun `e2ee encryption allows recipient decryption and blocks relay snooping`() {
        val crypto = CryptoService()
        val aliceKeys = crypto.generateIdentityKeyPair()
        val bobKeys = crypto.generateIdentityKeyPair()
        val relayKeys = crypto.generateIdentityKeyPair()

        val bobPubB64 = crypto.encodePublicKey(bobKeys.public)
        val aad = "pkt-001|1|2|TEXT|0/1"
        val secretMessage = "Meet at ridge camp offline via 3-hop BLE mesh!"

        val envelope = crypto.encryptTextForRecipient(
            text = secretMessage,
            senderPrivateKey = aliceKeys.private,
            senderPublicKey = aliceKeys.public,
            recipientPublicKeyB64 = bobPubB64,
            aadContext = aad
        )

        // Relay node attempts to decrypt payload -> MUST fail
        val relaySnooped = crypto.attemptRelaySnooping(
            envelope = envelope,
            relayPrivateKey = relayKeys.private,
            aadContext = aad
        )
        assertFalse("Intermediate relay must never decrypt E2EE payload", relaySnooped)

        // Recipient decrypts using matching Private Key -> MUST succeed
        val decrypted = crypto.decryptTextFromSender(
            envelope = envelope,
            recipientPrivateKey = bobKeys.private,
            aadContext = aad
        )
        assertEquals(secretMessage, decrypted)
        assertNotEquals(secretMessage, envelope.ciphertextB64)
    }

    @Test
    fun `safety numbers are symmetric and deterministic`() {
        val crypto = CryptoService()
        val a = crypto.encodePublicKey(crypto.generateIdentityKeyPair().public)
        val b = crypto.encodePublicKey(crypto.generateIdentityKeyPair().public)

        val ab = crypto.computeSafetyNumber(a, b)
        val ba = crypto.computeSafetyNumber(b, a)
        assertEquals(ab, ba)
        assertTrue(ab.split(" ").size == 6)
    }
}
