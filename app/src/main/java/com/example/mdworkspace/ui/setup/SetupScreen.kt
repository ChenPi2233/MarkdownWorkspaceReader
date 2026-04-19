package com.example.mdworkspace.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mdworkspace.MdWorkspaceApplication

@Composable
fun SetupScreen(
    onConnected: (String) -> Unit
) {
    val app = LocalContext.current.applicationContext as MdWorkspaceApplication
    val viewModel: SetupViewModel = viewModel(
        factory = SetupViewModel.factory(app.container.repository)
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "绑定文档仓库",
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = "只连接一个 GitHub 仓库，用于阅读带 frontmatter 身份的 Markdown 文档。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = state.ownerRepo,
            onValueChange = viewModel::updateOwnerRepo,
            label = { Text("owner/repo") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None)
        )
        OutlinedTextField(
            value = state.branch,
            onValueChange = viewModel::updateBranch,
            label = { Text("branch") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        OutlinedTextField(
            value = state.rootPath,
            onValueChange = viewModel::updateRootPath,
            label = { Text("起始目录，可选，例如 docs") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        OutlinedTextField(
            value = state.token,
            onValueChange = viewModel::updateToken,
            label = { Text("Personal Access Token，可留空访问公开仓库") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation()
        )
        Button(
            onClick = { viewModel.connect(onConnected) },
            enabled = !state.isConnecting,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.isConnecting) {
                CircularProgressIndicator()
            } else {
                Text("连接")
            }
        }
        state.message?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.tertiary,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
