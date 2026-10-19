package io.github.auroralucas365.pausecart

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import io.github.auroralucas365.pausecart.core.SharedText
import io.github.auroralucas365.pausecart.ui.PauseCartApp

data class ImportDraft(val title: String = "", val cents: Long? = null, val link: String = "", val source: String = "手动记录", val nonce: Long = System.nanoTime())
class MainActivity : ComponentActivity() {
    private var incoming by mutableStateOf<ImportDraft?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        if(savedInstanceState == null) readIntent(intent)
        setContent { PauseCartApp(incoming) { incoming=null } }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); readIntent(intent) }
    private fun readIntent(intent: Intent?) {
        when {
            intent?.action == Intent.ACTION_SEND && intent.type == "text/plain" -> {
                val text=intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty().take(8000)
                incoming=ImportDraft(SharedText.title(text),link=SharedText.link(text).orEmpty(),source="分享导入")
            }
            intent?.hasExtra("capture_source") == true -> incoming=ImportDraft(
                cents=intent.getLongExtra("capture_cents",-1).takeIf { it>0 },
                source=intent.getStringExtra("capture_source").orEmpty().take(30))
        }
        intent?.removeExtra("capture_source"); intent?.removeExtra(Intent.EXTRA_TEXT)
    }
}
