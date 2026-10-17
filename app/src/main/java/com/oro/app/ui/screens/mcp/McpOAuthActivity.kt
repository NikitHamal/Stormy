package com.oro.app.ui.screens.mcp

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import com.oro.app.OroApplication
import com.oro.app.data.ai.mcp.McpOAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Receives `oro-mcp://callback?code=...&state=...` from the browser after an
 * MCP server sign-in, exchanges the code and stores the tokens on the server.
 */
class McpOAuthActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent?.data?.toString()
        if (uri == null) { finish(); return }
        val app = OroApplication.getInstance()
        app.applicationScope.launch {
            val message = McpOAuth.complete(uri).fold(
                onSuccess = { (serverId, tokens) ->
                    app.preferencesRepository.updateMcpSettings { s ->
                        s.copy(servers = s.servers.map { if (it.id == serverId) it.copy(oauth = tokens) else it })
                    }
                    "Signed in. Orobi can use this server now."
                },
                onFailure = { it.message ?: "Sign-in failed" }
            )
            withContext(Dispatchers.Main) { Toast.makeText(app, message, Toast.LENGTH_LONG).show() }
        }
        // Back to wherever the user was (the MCP screen sits under the browser).
        packageManager.getLaunchIntentForPackage(packageName)?.let {
            it.addFlags(android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startActivity(it)
        }
        finish()
    }
}
