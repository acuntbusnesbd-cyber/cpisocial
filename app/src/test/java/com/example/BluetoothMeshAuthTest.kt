package com.example

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.EchoRepository
import com.example.data.User
import com.example.mesh.CryptoService
import com.example.ui.EchoViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BluetoothMeshAuthTest {

    private fun drainLooperAndIo(rounds: Int = 12) {
        repeat(rounds) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
            Shadows.shadowOf(Looper.getMainLooper()).idle()
        }
    }

    @Test
    fun `no demo accounts exist and cold launch does not auto login`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = EchoViewModel(context)

        drainLooperAndIo()

        assertNull("Cold start must NOT auto-login", viewModel.currentUser.value)
        assertTrue("No demo users should be seeded", viewModel.allUsers.value.isEmpty())
        assertTrue("No demo posts should be seeded", viewModel.feedPosts.value.isEmpty())
        assertTrue("No local accounts should exist initially", viewModel.localAccounts.value.isEmpty())
    }

    @Test
    fun `purgeLegacyDemoAccounts deletes legacy demo users`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = EchoRepository(db)

        repo.insertUser(User(username = "sky_wanderer", isLocalAccount = true))
        repo.insertUser(User(username = "pixel_artisan", isLocalAccount = false))
        repo.insertUser(User(username = "real_node", isLocalAccount = true, nodeId = "node-abc"))

        repo.purgeLegacyDemoAccounts()

        val remaining = repo.getAllUsers().first()
        assertEquals(1, remaining.size)
        assertEquals("real_node", remaining.first().username)
        db.close()
    }

    @Test
    fun `bluetooth gate blocks signup when bluetooth is off and succeeds when enabled`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val shadowApp = Shadows.shadowOf(app)
        shadowApp.grantPermissions(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.RECORD_AUDIO
        )

        val btManager = app.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter: BluetoothAdapter? = btManager.adapter
        if (adapter != null) {
            Shadows.shadowOf(adapter).setEnabled(false)
        }

        val viewModel = EchoViewModel(app)
        viewModel.refreshBlePermissionsAndRadio()
        drainLooperAndIo()

        var rejectedMessage = ""
        viewModel.createBluetoothAccount(
            username = "alpha_node",
            pinCode = "1234",
            bio = "Mesh relay",
            onSuccess = {},
            onError = { rejectedMessage = it }
        )
        drainLooperAndIo()
        assertTrue(
            "Signup should require Bluetooth radio to be ON",
            rejectedMessage.contains("Bluetooth", ignoreCase = true)
        )
        assertNull(viewModel.currentUser.value)

        if (adapter != null) {
            Shadows.shadowOf(adapter).setEnabled(true)
            viewModel.refreshBlePermissionsAndRadio()
            drainLooperAndIo()

            var created = false
            var createErr = ""
            viewModel.createBluetoothAccount(
                username = "alpha_node",
                pinCode = "1234",
                bio = "Mesh relay",
                onSuccess = { created = true },
                onError = { createErr = it }
            )
            drainLooperAndIo()
            assertTrue("Expected signup success when BT is ON, got error: $createErr", created)
            assertNotNull(viewModel.currentUser.value)
            assertEquals("alpha_node", viewModel.currentUser.value?.username)
            assertTrue(viewModel.currentUser.value?.isLocalAccount == true)

            viewModel.logout()
            drainLooperAndIo()
            assertNull(viewModel.currentUser.value)

            // Wrong PIN must fail
            var pinErr = ""
            viewModel.loginWithBluetoothNode(
                usernameOrNodeId = "alpha_node",
                pinCode = "9999",
                onSuccess = {},
                onError = { pinErr = it }
            )
            drainLooperAndIo()
            assertTrue(pinErr.contains("Incorrect PIN", ignoreCase = true))

            // Correct PIN logs back in
            var loggedIn = false
            viewModel.loginWithBluetoothNode(
                usernameOrNodeId = "alpha_node",
                pinCode = "1234",
                onSuccess = { loggedIn = true },
                onError = {}
            )
            drainLooperAndIo()
            assertTrue(loggedIn)
            assertEquals("alpha_node", viewModel.currentUser.value?.username)
        }
    }

    @Test
    fun `private key round trip preserves ECDH decryption capability`() {
        val crypto = CryptoService()
        val sender = crypto.generateIdentityKeyPair()
        val recipient = crypto.generateIdentityKeyPair()

        val encodedPriv = crypto.encodePrivateKey(recipient.private)
        val restoredPriv = crypto.decodePrivateKey(encodedPriv)

        val aad = "pkt-rt|n1|n2|TEXT|0/1"
        val env = crypto.encryptTextForRecipient(
            text = "Hello over real Bluetooth mesh",
            senderPrivateKey = sender.private,
            senderPublicKey = sender.public,
            recipientPublicKeyB64 = crypto.encodePublicKey(recipient.public),
            aadContext = aad
        )

        val decrypted = crypto.decryptTextFromSender(env, restoredPriv, aad)
        assertEquals("Hello over real Bluetooth mesh", decrypted)
    }
}
