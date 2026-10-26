package com.tulip.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.tulip.app.ui.TulipRoot
import com.tulip.app.ui.TulipTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    /** Diálogos del sistema de Android para modificar/borrar archivos de otras apps. */
    private val consentLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        vm.onConsentResult(it.resultCode == RESULT_OK)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.consentRequests.collect { consentLauncher.launch(IntentSenderRequest.Builder(it).build()) }
            }
        }
        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            TulipTheme(settings) { TulipRoot(vm) }
        }
    }
}
