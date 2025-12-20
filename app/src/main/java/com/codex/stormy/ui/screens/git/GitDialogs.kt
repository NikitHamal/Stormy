package com.codex.stormy.ui.screens.git

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.codex.stormy.ui.theme.PoppinsFontFamily

/**
 * Dialog for cloning a Git repository
 */
@Composable
fun GitCloneDialog(
    onDismiss: () -> Unit,
    onClone: (url: String, name: String, branch: String?, shallow: Boolean) -> Unit,
    isLoading: Boolean = false
) {
    var url by remember { mutableStateOf("") }
    var projectName by remember { mutableStateOf("") }
    var branch by remember { mutableStateOf("") }
    var shallowClone by remember { mutableStateOf(false) }

    // Extract project name from URL
    fun extractNameFromUrl(repoUrl: String): String {
        return repoUrl
            .trimEnd('/')
            .substringAfterLast('/')
            .removeSuffix(".git")
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Clone Repository",
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Repository URL
                OutlinedTextField(
                    value = url,
                    onValueChange = { newUrl ->
                        url = newUrl
                        if (projectName.isEmpty() || projectName == extractNameFromUrl(url)) {
                            projectName = extractNameFromUrl(newUrl)
                        }
                    },
                    label = { Text("Repository URL", fontFamily = PoppinsFontFamily) },
                    placeholder = { Text("https://github.com/user/repo.git") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Link,
                            contentDescription = null
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Project name
                OutlinedTextField(
                    value = projectName,
                    onValueChange = { projectName = it },
                    label = { Text("Project Name", fontFamily = PoppinsFontFamily) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Folder,
                            contentDescription = null
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Branch (optional)
                OutlinedTextField(
                    value = branch,
                    onValueChange = { branch = it },
                    label = { Text("Branch (optional)", fontFamily = PoppinsFontFamily) },
                    placeholder = { Text("main") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Shallow clone option
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = shallowClone,
                        onCheckedChange = { shallowClone = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Shallow clone",
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = PoppinsFontFamily
                        )
                        Text(
                            text = "Faster download, limited history",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss, enabled = !isLoading) {
                        Text("Cancel", fontFamily = PoppinsFontFamily)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onClone(
                                url,
                                projectName,
                                branch.takeIf { it.isNotBlank() },
                                shallowClone
                            )
                        },
                        enabled = url.isNotBlank() && projectName.isNotBlank() && !isLoading
                    ) {
                        Text("Clone", fontFamily = PoppinsFontFamily)
                    }
                }
            }
        }
    }
}

/**
 * Dialog for creating a new branch
 */
@Composable
fun CreateBranchDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, checkout: Boolean) -> Unit,
    currentBranch: String = "main"
) {
    var branchName by remember { mutableStateOf("") }
    var checkoutAfterCreate by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Create Branch",
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Create a new branch from '$currentBranch'",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = branchName,
                    onValueChange = { branchName = it.replace(" ", "-") },
                    label = { Text("Branch name", fontFamily = PoppinsFontFamily) },
                    placeholder = { Text("feature/my-feature") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Switch(
                        checked = checkoutAfterCreate,
                        onCheckedChange = { checkoutAfterCreate = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Switch to branch after creating",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = PoppinsFontFamily
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(branchName, checkoutAfterCreate) },
                enabled = branchName.isNotBlank()
            ) {
                Text("Create", fontFamily = PoppinsFontFamily)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", fontFamily = PoppinsFontFamily)
            }
        }
    )
}

/**
 * Dialog for Git credentials/settings
 */
@Composable
fun GitSettingsDialog(
    userName: String,
    userEmail: String,
    onSaveIdentity: (name: String, email: String) -> Unit,
    onSaveCredentials: (host: String, username: String, password: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(userName) }
    var email by remember { mutableStateOf(userEmail) }

    var credHost by remember { mutableStateOf("github.com") }
    var credUsername by remember { mutableStateOf("") }
    var credPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Git Settings",
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Identity Section
                Text(
                    text = "Git Identity",
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name", fontFamily = PoppinsFontFamily) },
                    placeholder = { Text("John Doe") },
                    leadingIcon = {
                        Icon(Icons.Outlined.Person, contentDescription = null)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email", fontFamily = PoppinsFontFamily) },
                    placeholder = { Text("john@example.com") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )

                Button(
                    onClick = { onSaveIdentity(name, email) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = name.isNotBlank() && email.isNotBlank()
                ) {
                    Text("Save Identity", fontFamily = PoppinsFontFamily)
                }

                HorizontalDivider()

                // Credentials Section
                Text(
                    text = "Repository Credentials",
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "Store credentials for private repositories",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = credHost,
                    onValueChange = { credHost = it },
                    label = { Text("Host", fontFamily = PoppinsFontFamily) },
                    placeholder = { Text("github.com") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = credUsername,
                    onValueChange = { credUsername = it },
                    label = { Text("Username", fontFamily = PoppinsFontFamily) },
                    leadingIcon = {
                        Icon(Icons.Outlined.Person, contentDescription = null)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = credPassword,
                    onValueChange = { credPassword = it },
                    label = { Text("Password / Token", fontFamily = PoppinsFontFamily) },
                    leadingIcon = {
                        Icon(Icons.Outlined.Lock, contentDescription = null)
                    },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Outlined.VisibilityOff
                                else Icons.Outlined.Visibility,
                                contentDescription = if (showPassword) "Hide" else "Show"
                            )
                        }
                    },
                    visualTransformation = if (showPassword) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Text(
                    text = "For GitHub, use a Personal Access Token instead of your password",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = {
                        onSaveCredentials(credHost, credUsername, credPassword)
                        credPassword = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = credUsername.isNotBlank() && credPassword.isNotBlank()
                ) {
                    Text("Save Credentials", fontFamily = PoppinsFontFamily)
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Close button
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Close", fontFamily = PoppinsFontFamily)
                }
            }
        }
    }
}

/**
 * Confirmation dialog for destructive actions
 */
@Composable
fun GitConfirmDialog(
    title: String,
    message: String,
    confirmText: String = "Confirm",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isDestructive: Boolean = false
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = PoppinsFontFamily
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = if (isDestructive) {
                    androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                } else {
                    androidx.compose.material3.ButtonDefaults.buttonColors()
                }
            ) {
                Text(confirmText, fontFamily = PoppinsFontFamily)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", fontFamily = PoppinsFontFamily)
            }
        }
    )
}

/**
 * Dialog for adding a remote
 */
@Composable
fun AddRemoteDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, url: String) -> Unit
) {
    var remoteName by remember { mutableStateOf("origin") }
    var remoteUrl by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Add Remote",
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = remoteName,
                    onValueChange = { remoteName = it },
                    label = { Text("Remote name", fontFamily = PoppinsFontFamily) },
                    placeholder = { Text("origin") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = remoteUrl,
                    onValueChange = { remoteUrl = it },
                    label = { Text("URL", fontFamily = PoppinsFontFamily) },
                    placeholder = { Text("https://github.com/user/repo.git") },
                    leadingIcon = {
                        Icon(Icons.Outlined.Link, contentDescription = null)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(remoteName, remoteUrl) },
                enabled = remoteName.isNotBlank() && remoteUrl.isNotBlank()
            ) {
                Text("Add", fontFamily = PoppinsFontFamily)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", fontFamily = PoppinsFontFamily)
            }
        }
    )
}
