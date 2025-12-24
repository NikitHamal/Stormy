package com.codex.stormy.ui.screens.editor.assets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessAlarm
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountBox
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.AddShoppingCart
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Anchor
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Announcement
import androidx.compose.material.icons.outlined.Api
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowCircleDown
import androidx.compose.material.icons.outlined.ArrowCircleUp
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Backspace
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.BrightnessMedium
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Camera
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Colorize
import androidx.compose.material.icons.outlined.Comment
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DesignServices
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Drafts
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Feed
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.GetApp
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Help
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Inventory
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Laptop
import androidx.compose.material.icons.outlined.Launch
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Leaderboard
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Login
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Message
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Money
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.Payment
import androidx.compose.material.icons.outlined.Pending
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Pin
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Reply
import androidx.compose.material.icons.outlined.Report
import androidx.compose.material.icons.outlined.Rocket
import androidx.compose.material.icons.outlined.RssFeed
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Shop
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Store
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.Support
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.Task
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.outlined.VideoCall
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.WatchLater
import androidx.compose.material.icons.outlined.Web
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SearchBar
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codex.stormy.ui.theme.PoppinsFontFamily

/**
 * Icon category for grouping icons
 */
enum class IconCategory(val displayName: String) {
    ALL("All"),
    ACTION("Action"),
    NAVIGATION("Navigation"),
    COMMUNICATION("Communication"),
    CONTENT("Content"),
    SOCIAL("Social"),
    MEDIA("Media"),
    DEVICE("Device"),
    FILE("File"),
    ALERT("Alert"),
    AV("Audio/Video"),
    EDITOR("Editor"),
    HARDWARE("Hardware"),
    MAPS("Maps"),
    TOGGLE("Toggle")
}

/**
 * Icon library type
 */
enum class IconLibrary(val displayName: String) {
    MATERIAL("Material Icons"),
    FONT_AWESOME("Font Awesome")
}

/**
 * Icon item data class
 */
data class IconItem(
    val name: String,
    val icon: ImageVector? = null, // For Material icons
    val fontAwesomeClass: String? = null, // For Font Awesome
    val category: IconCategory,
    val keywords: List<String> = emptyList()
)

/**
 * Icon Library Dialog for browsing and inserting icons
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconLibraryDialog(
    onDismiss: () -> Unit,
    onIconSelected: (IconItem, String) -> Unit // Icon and code to insert
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val clipboardManager = LocalClipboardManager.current

    // State
    var selectedLibrary by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(IconCategory.ALL) }
    var selectedIcon by remember { mutableStateOf<IconItem?>(null) }

    // Get icons for selected library
    val icons = remember(selectedLibrary) {
        if (selectedLibrary == 0) getMaterialIcons() else getFontAwesomeIcons()
    }

    // Filtered icons based on search and category
    val filteredIcons by remember(icons, searchQuery, selectedCategory) {
        derivedStateOf {
            icons.filter { icon ->
                val matchesSearch = searchQuery.isEmpty() ||
                        icon.name.contains(searchQuery, ignoreCase = true) ||
                        icon.keywords.any { it.contains(searchQuery, ignoreCase = true) }
                val matchesCategory = selectedCategory == IconCategory.ALL || icon.category == selectedCategory

                matchesSearch && matchesCategory
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Icon Library",
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Close"
                    )
                }
            }

            // Library tabs
            TabRow(
                selectedTabIndex = selectedLibrary,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconLibrary.entries.forEachIndexed { index, library ->
                    Tab(
                        selected = selectedLibrary == index,
                        onClick = { selectedLibrary = index },
                        text = { Text(library.displayName) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                placeholder = { Text("Search icons...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Clear"
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Category filter chips (scrollable row)
            androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(IconCategory.entries.size) { index ->
                    val category = IconCategory.entries[index]
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        label = { Text(category.displayName) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            HorizontalDivider()

            // Icon grid
            if (filteredIcons.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No icons found",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(350.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredIcons) { iconItem ->
                        IconGridItem(
                            iconItem = iconItem,
                            isSelected = selectedIcon == iconItem,
                            onClick = {
                                selectedIcon = iconItem
                            }
                        )
                    }
                }
            }

            // Selected icon details and actions
            selectedIcon?.let { icon ->
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                IconDetailSection(
                    icon = icon,
                    library = if (selectedLibrary == 0) IconLibrary.MATERIAL else IconLibrary.FONT_AWESOME,
                    onCopyCode = { code ->
                        clipboardManager.setText(AnnotatedString(code))
                        onIconSelected(icon, code)
                    }
                )
            }
        }
    }
}

@Composable
private fun IconGridItem(
    iconItem: IconItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        shape = RoundedCornerShape(8.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            iconItem.icon?.let { imageVector ->
                Icon(
                    imageVector = imageVector,
                    contentDescription = iconItem.name,
                    modifier = Modifier.size(24.dp),
                    tint = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            } ?: run {
                // Font Awesome icon placeholder
                Text(
                    text = iconItem.name.take(2).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

@Composable
private fun IconDetailSection(
    icon: IconItem,
    library: IconLibrary,
    onCopyCode: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    icon.icon?.let { imageVector ->
                        Icon(
                            imageVector = imageVector,
                            contentDescription = icon.name,
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = icon.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${library.displayName} • ${icon.category.displayName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Code snippets
        Text(
            text = "Copy as:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // HTML code
            CopyCodeChip(
                label = "HTML",
                code = if (library == IconLibrary.MATERIAL) {
                    "<span class=\"material-icons\">${icon.name.lowercase().replace(" ", "_")}</span>"
                } else {
                    "<i class=\"${icon.fontAwesomeClass}\"></i>"
                },
                onClick = onCopyCode,
                modifier = Modifier.weight(1f)
            )

            // CSS/Class code
            CopyCodeChip(
                label = "Class",
                code = if (library == IconLibrary.MATERIAL) {
                    "material-icons"
                } else {
                    icon.fontAwesomeClass ?: ""
                },
                onClick = onCopyCode,
                modifier = Modifier.weight(1f)
            )

            // Icon name
            CopyCodeChip(
                label = "Name",
                code = icon.name.lowercase().replace(" ", "_"),
                onClick = onCopyCode,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun CopyCodeChip(
    label: String,
    code: String,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = { onClick(code) },
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Icon(
                imageVector = Icons.Outlined.ContentCopy,
                contentDescription = "Copy",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * Get Material Icons list
 */
private fun getMaterialIcons(): List<IconItem> {
    return listOf(
        // Action icons
        IconItem("Add", Icons.Outlined.Add, null, IconCategory.ACTION, listOf("plus", "new", "create")),
        IconItem("Add Circle", Icons.Outlined.AddCircle, null, IconCategory.ACTION, listOf("plus", "new")),
        IconItem("Delete", Icons.Outlined.Delete, null, IconCategory.ACTION, listOf("remove", "trash", "bin")),
        IconItem("Edit", Icons.Outlined.Edit, null, IconCategory.ACTION, listOf("modify", "pen", "pencil")),
        IconItem("Save", Icons.Outlined.Save, null, IconCategory.ACTION, listOf("disk", "store")),
        IconItem("Search", Icons.Outlined.Search, null, IconCategory.ACTION, listOf("find", "magnify")),
        IconItem("Settings", Icons.Outlined.Settings, null, IconCategory.ACTION, listOf("gear", "cog", "preferences")),
        IconItem("Share", Icons.Outlined.Share, null, IconCategory.ACTION, listOf("social", "send")),
        IconItem("Download", Icons.Outlined.Download, null, IconCategory.ACTION, listOf("get", "save")),
        IconItem("Upload", Icons.Outlined.Upload, null, IconCategory.ACTION, listOf("send", "export")),
        IconItem("Refresh", Icons.Outlined.Refresh, null, IconCategory.ACTION, listOf("reload", "update")),
        IconItem("Print", Icons.Outlined.Print, null, IconCategory.ACTION, listOf("printer")),
        IconItem("Bookmark", Icons.Outlined.Bookmark, null, IconCategory.ACTION, listOf("save", "favorite")),
        IconItem("Favorite", Icons.Outlined.Favorite, null, IconCategory.ACTION, listOf("heart", "love", "like")),
        IconItem("Star", Icons.Outlined.Star, null, IconCategory.ACTION, listOf("rate", "favorite")),
        IconItem("Check", Icons.Outlined.Check, null, IconCategory.ACTION, listOf("done", "complete", "tick")),
        IconItem("Check Circle", Icons.Outlined.CheckCircle, null, IconCategory.ACTION, listOf("done", "complete")),
        IconItem("Close", Icons.Outlined.Close, null, IconCategory.ACTION, listOf("x", "cancel", "dismiss")),
        IconItem("Cancel", Icons.Outlined.Cancel, null, IconCategory.ACTION, listOf("close", "stop")),
        IconItem("Done", Icons.Outlined.Done, null, IconCategory.ACTION, listOf("check", "complete")),
        IconItem("Info", Icons.Outlined.Info, null, IconCategory.ACTION, listOf("about", "help")),
        IconItem("Help", Icons.Outlined.Help, null, IconCategory.ACTION, listOf("question", "support")),
        IconItem("Warning", Icons.Outlined.Warning, null, IconCategory.ALERT, listOf("caution", "alert")),
        IconItem("Error", Icons.Outlined.Error, null, IconCategory.ALERT, listOf("problem", "issue")),
        IconItem("Launch", Icons.Outlined.Launch, null, IconCategory.ACTION, listOf("open", "external")),
        IconItem("Send", Icons.Outlined.Send, null, IconCategory.ACTION, listOf("submit", "arrow")),
        IconItem("Copy", Icons.Outlined.ContentCopy, null, IconCategory.ACTION, listOf("duplicate", "clone")),
        IconItem("Paste", Icons.Outlined.ContentPaste, null, IconCategory.ACTION, listOf("clipboard")),
        IconItem("Sync", Icons.Outlined.Sync, null, IconCategory.ACTION, listOf("refresh", "update")),
        IconItem("Backup", Icons.Outlined.Backup, null, IconCategory.ACTION, listOf("cloud", "save")),
        IconItem("Archive", Icons.Outlined.Archive, null, IconCategory.ACTION, listOf("box", "storage")),
        IconItem("Login", Icons.Outlined.Login, null, IconCategory.ACTION, listOf("signin", "enter")),
        IconItem("Logout", Icons.Outlined.Logout, null, IconCategory.ACTION, listOf("signout", "exit")),

        // Navigation icons
        IconItem("Home", Icons.Outlined.Home, null, IconCategory.NAVIGATION, listOf("house", "main")),
        IconItem("Menu", Icons.Outlined.Menu, null, IconCategory.NAVIGATION, listOf("hamburger", "nav")),
        IconItem("Arrow Back", Icons.Outlined.ArrowBack, null, IconCategory.NAVIGATION, listOf("left", "previous")),
        IconItem("Arrow Forward", Icons.Outlined.ArrowForward, null, IconCategory.NAVIGATION, listOf("right", "next")),
        IconItem("Arrow Upward", Icons.Outlined.ArrowUpward, null, IconCategory.NAVIGATION, listOf("up")),
        IconItem("Arrow Downward", Icons.Outlined.ArrowDownward, null, IconCategory.NAVIGATION, listOf("down")),
        IconItem("Expand More", Icons.Outlined.ExpandMore, null, IconCategory.NAVIGATION, listOf("chevron down")),
        IconItem("Expand Less", Icons.Outlined.ExpandLess, null, IconCategory.NAVIGATION, listOf("chevron up")),
        IconItem("Chevron Left", Icons.Outlined.ChevronLeft, null, IconCategory.NAVIGATION, listOf("back")),
        IconItem("Chevron Right", Icons.Outlined.ChevronRight, null, IconCategory.NAVIGATION, listOf("forward")),
        IconItem("More Vert", Icons.Outlined.MoreVert, null, IconCategory.NAVIGATION, listOf("dots", "options")),
        IconItem("More Horiz", Icons.Outlined.MoreHoriz, null, IconCategory.NAVIGATION, listOf("dots", "options")),
        IconItem("Apps", Icons.Outlined.Apps, null, IconCategory.NAVIGATION, listOf("grid", "menu")),
        IconItem("Dashboard", Icons.Outlined.Dashboard, null, IconCategory.NAVIGATION, listOf("home", "overview")),
        IconItem("Explore", Icons.Outlined.Explore, null, IconCategory.NAVIGATION, listOf("compass", "discover")),

        // Communication icons
        IconItem("Email", Icons.Outlined.Email, null, IconCategory.COMMUNICATION, listOf("mail", "message")),
        IconItem("Mail", Icons.Outlined.Mail, null, IconCategory.COMMUNICATION, listOf("email", "letter")),
        IconItem("Chat", Icons.Outlined.Chat, null, IconCategory.COMMUNICATION, listOf("message", "conversation")),
        IconItem("Message", Icons.Outlined.Message, null, IconCategory.COMMUNICATION, listOf("chat", "sms")),
        IconItem("Call", Icons.Outlined.Call, null, IconCategory.COMMUNICATION, listOf("phone", "dial")),
        IconItem("Phone", Icons.Outlined.Phone, null, IconCategory.COMMUNICATION, listOf("call", "mobile")),
        IconItem("Video Call", Icons.Outlined.VideoCall, null, IconCategory.COMMUNICATION, listOf("camera")),
        IconItem("Forum", Icons.Outlined.Forum, null, IconCategory.COMMUNICATION, listOf("discussion", "chat")),
        IconItem("Comment", Icons.Outlined.Comment, null, IconCategory.COMMUNICATION, listOf("feedback", "message")),
        IconItem("Notifications", Icons.Outlined.Notifications, null, IconCategory.COMMUNICATION, listOf("bell", "alert")),
        IconItem("Campaign", Icons.Outlined.Campaign, null, IconCategory.COMMUNICATION, listOf("megaphone", "announce")),
        IconItem("Announcement", Icons.Outlined.Announcement, null, IconCategory.COMMUNICATION, listOf("news", "notice")),

        // Content icons
        IconItem("Article", Icons.Outlined.Article, null, IconCategory.CONTENT, listOf("post", "document")),
        IconItem("Book", Icons.Outlined.Book, null, IconCategory.CONTENT, listOf("read", "manual")),
        IconItem("Description", Icons.Outlined.Description, null, IconCategory.CONTENT, listOf("document", "file")),
        IconItem("Feed", Icons.Outlined.Feed, null, IconCategory.CONTENT, listOf("news", "timeline")),
        IconItem("Flag", Icons.Outlined.Flag, null, IconCategory.CONTENT, listOf("report", "mark")),
        IconItem("Label", Icons.Outlined.Label, null, IconCategory.CONTENT, listOf("tag", "category")),
        IconItem("Tag", Icons.Outlined.Tag, null, IconCategory.CONTENT, listOf("label", "hashtag")),
        IconItem("Link", Icons.Outlined.Link, null, IconCategory.CONTENT, listOf("url", "chain")),
        IconItem("Drafts", Icons.Outlined.Drafts, null, IconCategory.CONTENT, listOf("edit", "unsent")),
        IconItem("Report", Icons.Outlined.Report, null, IconCategory.CONTENT, listOf("flag", "issue")),
        IconItem("Reply", Icons.Outlined.Reply, null, IconCategory.CONTENT, listOf("respond", "answer")),

        // Social icons
        IconItem("Person", Icons.Outlined.Person, null, IconCategory.SOCIAL, listOf("user", "account")),
        IconItem("People", Icons.Outlined.People, null, IconCategory.SOCIAL, listOf("users", "group")),
        IconItem("Group", Icons.Outlined.Group, null, IconCategory.SOCIAL, listOf("team", "people")),
        IconItem("Account Circle", Icons.Outlined.AccountCircle, null, IconCategory.SOCIAL, listOf("user", "avatar")),
        IconItem("Person Add", Icons.Outlined.PersonAdd, null, IconCategory.SOCIAL, listOf("add user", "invite")),
        IconItem("Face", Icons.Outlined.Face, null, IconCategory.SOCIAL, listOf("emoji", "smile")),
        IconItem("Public", Icons.Outlined.Public, null, IconCategory.SOCIAL, listOf("globe", "world")),
        IconItem("Thumb Up", Icons.Outlined.ThumbUp, null, IconCategory.SOCIAL, listOf("like", "approve")),
        IconItem("Thumb Down", Icons.Outlined.ThumbDown, null, IconCategory.SOCIAL, listOf("dislike", "reject")),

        // Media icons
        IconItem("Image", Icons.Outlined.Image, null, IconCategory.MEDIA, listOf("photo", "picture")),
        IconItem("Photo", Icons.Outlined.Photo, null, IconCategory.MEDIA, listOf("image", "picture")),
        IconItem("Camera", Icons.Outlined.Camera, null, IconCategory.MEDIA, listOf("photo", "capture")),
        IconItem("Photo Camera", Icons.Outlined.PhotoCamera, null, IconCategory.MEDIA, listOf("camera", "capture")),
        IconItem("Videocam", Icons.Outlined.Videocam, null, IconCategory.MEDIA, listOf("video", "record")),
        IconItem("Movie", Icons.Outlined.Movie, null, IconCategory.MEDIA, listOf("film", "video")),
        IconItem("Music Note", Icons.Outlined.MusicNote, null, IconCategory.MEDIA, listOf("audio", "song")),
        IconItem("Mic", Icons.Outlined.Mic, null, IconCategory.MEDIA, listOf("microphone", "audio")),
        IconItem("Volume Up", Icons.Outlined.VolumeUp, null, IconCategory.MEDIA, listOf("speaker", "sound")),
        IconItem("Volume Off", Icons.Outlined.VolumeOff, null, IconCategory.MEDIA, listOf("mute", "silent")),

        // AV icons
        IconItem("Play", Icons.Outlined.PlayArrow, null, IconCategory.AV, listOf("start", "begin")),
        IconItem("Play Circle", Icons.Outlined.PlayCircle, null, IconCategory.AV, listOf("start", "video")),
        IconItem("Pause", Icons.Outlined.Pause, null, IconCategory.AV, listOf("hold", "wait")),
        IconItem("Stop", Icons.Outlined.Stop, null, IconCategory.AV, listOf("end", "halt")),

        // Device icons
        IconItem("Computer", Icons.Outlined.Computer, null, IconCategory.DEVICE, listOf("desktop", "pc")),
        IconItem("Laptop", Icons.Outlined.Laptop, null, IconCategory.DEVICE, listOf("notebook", "computer")),
        IconItem("Phone", Icons.Outlined.Phone, null, IconCategory.DEVICE, listOf("mobile", "smartphone")),
        IconItem("Devices", Icons.Outlined.Devices, null, IconCategory.DEVICE, listOf("responsive", "multi")),
        IconItem("Keyboard", Icons.Outlined.Keyboard, null, IconCategory.DEVICE, listOf("type", "input")),
        IconItem("Storage", Icons.Outlined.Storage, null, IconCategory.DEVICE, listOf("database", "server")),
        IconItem("Cloud", Icons.Outlined.Cloud, null, IconCategory.DEVICE, listOf("storage", "online")),
        IconItem("Cloud Download", Icons.Outlined.CloudDownload, null, IconCategory.DEVICE, listOf("download")),
        IconItem("Cloud Upload", Icons.Outlined.CloudUpload, null, IconCategory.DEVICE, listOf("upload")),
        IconItem("Wifi", Icons.Outlined.Wifi, null, IconCategory.DEVICE, listOf("internet", "network")),

        // File icons
        IconItem("Folder", Icons.Outlined.Folder, null, IconCategory.FILE, listOf("directory", "container")),
        IconItem("Attach File", Icons.Outlined.AttachFile, null, IconCategory.FILE, listOf("attachment", "clip")),

        // Editor icons
        IconItem("Code", Icons.Outlined.Code, null, IconCategory.EDITOR, listOf("programming", "development")),
        IconItem("Terminal", Icons.Outlined.Terminal, null, IconCategory.EDITOR, listOf("console", "command")),
        IconItem("Brush", Icons.Outlined.Brush, null, IconCategory.EDITOR, listOf("paint", "design")),
        IconItem("Colorize", Icons.Outlined.Colorize, null, IconCategory.EDITOR, listOf("picker", "dropper")),
        IconItem("Palette", Icons.Outlined.Palette, null, IconCategory.EDITOR, listOf("colors", "theme")),
        IconItem("Text Fields", Icons.Outlined.TextFields, null, IconCategory.EDITOR, listOf("typography", "font")),
        IconItem("Tune", Icons.Outlined.Tune, null, IconCategory.EDITOR, listOf("adjust", "settings")),
        IconItem("Layers", Icons.Outlined.Layers, null, IconCategory.EDITOR, listOf("stack", "overlap")),
        IconItem("Design Services", Icons.Outlined.DesignServices, null, IconCategory.EDITOR, listOf("creative")),
        IconItem("Style", Icons.Outlined.Style, null, IconCategory.EDITOR, listOf("format", "theme")),

        // Maps icons
        IconItem("Location", Icons.Outlined.LocationOn, null, IconCategory.MAPS, listOf("place", "pin")),
        IconItem("Place", Icons.Outlined.Place, null, IconCategory.MAPS, listOf("location", "marker")),
        IconItem("Map", Icons.Outlined.Map, null, IconCategory.MAPS, listOf("navigation", "directions")),
        IconItem("Language", Icons.Outlined.Language, null, IconCategory.MAPS, listOf("globe", "translate")),
        IconItem("Translate", Icons.Outlined.Translate, null, IconCategory.MAPS, listOf("language", "convert")),

        // Toggle icons
        IconItem("Visibility", Icons.Outlined.Visibility, null, IconCategory.TOGGLE, listOf("show", "eye")),
        IconItem("Visibility Off", Icons.Outlined.VisibilityOff, null, IconCategory.TOGGLE, listOf("hide", "eye")),
        IconItem("Lock", Icons.Outlined.Lock, null, IconCategory.TOGGLE, listOf("secure", "private")),
        IconItem("Lock Open", Icons.Outlined.LockOpen, null, IconCategory.TOGGLE, listOf("unlock", "open")),
        IconItem("Light Mode", Icons.Outlined.LightMode, null, IconCategory.TOGGLE, listOf("sun", "day")),
        IconItem("Dark Mode", Icons.Outlined.DarkMode, null, IconCategory.TOGGLE, listOf("moon", "night")),
        IconItem("Brightness", Icons.Outlined.BrightnessMedium, null, IconCategory.TOGGLE, listOf("contrast")),

        // More icons
        IconItem("Schedule", Icons.Outlined.Schedule, null, IconCategory.ACTION, listOf("time", "clock")),
        IconItem("Timer", Icons.Outlined.Timer, null, IconCategory.ACTION, listOf("stopwatch", "countdown")),
        IconItem("Alarm", Icons.Outlined.Alarm, null, IconCategory.ACTION, listOf("clock", "alert")),
        IconItem("Access Time", Icons.Outlined.AccessTime, null, IconCategory.ACTION, listOf("clock")),
        IconItem("Calendar", Icons.Outlined.CalendarToday, null, IconCategory.ACTION, listOf("date", "schedule")),
        IconItem("Calendar Month", Icons.Outlined.CalendarMonth, null, IconCategory.ACTION, listOf("date")),
        IconItem("Event", Icons.Outlined.Event, null, IconCategory.ACTION, listOf("calendar", "date")),
        IconItem("Today", Icons.Outlined.Today, null, IconCategory.ACTION, listOf("date", "calendar")),
        IconItem("Date Range", Icons.Outlined.DateRange, null, IconCategory.ACTION, listOf("calendar")),
        IconItem("History", Icons.Outlined.History, null, IconCategory.ACTION, listOf("past", "recent")),
        IconItem("Watch Later", Icons.Outlined.WatchLater, null, IconCategory.ACTION, listOf("clock", "save")),
        IconItem("Security", Icons.Outlined.Security, null, IconCategory.ACTION, listOf("shield", "protect")),
        IconItem("Shield", Icons.Outlined.Shield, null, IconCategory.ACTION, listOf("security", "protect")),
        IconItem("Key", Icons.Outlined.Key, null, IconCategory.ACTION, listOf("password", "auth")),
        IconItem("Verified", Icons.Outlined.Verified, null, IconCategory.ACTION, listOf("check", "approved")),
        IconItem("Badge", Icons.Outlined.Badge, null, IconCategory.ACTION, listOf("id", "identity")),
        IconItem("Support", Icons.Outlined.Support, null, IconCategory.ACTION, listOf("help", "assist")),
        IconItem("Psychology", Icons.Outlined.Psychology, null, IconCategory.ACTION, listOf("brain", "ai")),
        IconItem("Science", Icons.Outlined.Science, null, IconCategory.ACTION, listOf("lab", "experiment")),
        IconItem("School", Icons.Outlined.School, null, IconCategory.ACTION, listOf("education", "learn")),
        IconItem("Work", Icons.Outlined.Work, null, IconCategory.ACTION, listOf("job", "briefcase")),
        IconItem("Store", Icons.Outlined.Store, null, IconCategory.ACTION, listOf("shop", "retail")),
        IconItem("Shop", Icons.Outlined.Shop, null, IconCategory.ACTION, listOf("store", "buy")),
        IconItem("Shopping Cart", Icons.Outlined.ShoppingCart, null, IconCategory.ACTION, listOf("cart", "buy")),
        IconItem("Shopping Bag", Icons.Outlined.ShoppingBag, null, IconCategory.ACTION, listOf("purchase")),
        IconItem("Add Shopping Cart", Icons.Outlined.AddShoppingCart, null, IconCategory.ACTION, listOf("buy")),
        IconItem("Payment", Icons.Outlined.Payment, null, IconCategory.ACTION, listOf("card", "pay")),
        IconItem("Credit Card", Icons.Outlined.CreditCard, null, IconCategory.ACTION, listOf("payment")),
        IconItem("Money", Icons.Outlined.Money, null, IconCategory.ACTION, listOf("cash", "currency")),
        IconItem("Attach Money", Icons.Outlined.AttachMoney, null, IconCategory.ACTION, listOf("dollar", "price")),
        IconItem("Receipt", Icons.Outlined.Receipt, null, IconCategory.ACTION, listOf("invoice", "bill")),
        IconItem("Local Offer", Icons.Outlined.LocalOffer, null, IconCategory.ACTION, listOf("tag", "discount")),
        IconItem("QR Code", Icons.Outlined.QrCode, null, IconCategory.ACTION, listOf("scan", "barcode")),
        IconItem("Analytics", Icons.Outlined.Analytics, null, IconCategory.ACTION, listOf("stats", "data")),
        IconItem("Assessment", Icons.Outlined.Assessment, null, IconCategory.ACTION, listOf("report", "stats")),
        IconItem("Bar Chart", Icons.Outlined.BarChart, null, IconCategory.ACTION, listOf("graph", "stats")),
        IconItem("Pie Chart", Icons.Outlined.PieChart, null, IconCategory.ACTION, listOf("graph", "data")),
        IconItem("Leaderboard", Icons.Outlined.Leaderboard, null, IconCategory.ACTION, listOf("ranking", "stats")),
        IconItem("Data Usage", Icons.Outlined.DataUsage, null, IconCategory.ACTION, listOf("chart", "stats")),
        IconItem("Filter List", Icons.Outlined.FilterList, null, IconCategory.ACTION, listOf("sort", "organize")),
        IconItem("List", Icons.Outlined.List, null, IconCategory.ACTION, listOf("menu", "items")),
        IconItem("Inventory", Icons.Outlined.Inventory, null, IconCategory.ACTION, listOf("box", "stock")),
        IconItem("Task", Icons.Outlined.Task, null, IconCategory.ACTION, listOf("todo", "check")),
        IconItem("Assignment", Icons.Outlined.Assignment, null, IconCategory.ACTION, listOf("task", "document")),
        IconItem("Pending", Icons.Outlined.Pending, null, IconCategory.ACTION, listOf("waiting", "process")),
        IconItem("Quiz", Icons.Outlined.Quiz, null, IconCategory.ACTION, listOf("question", "test")),
        IconItem("Extension", Icons.Outlined.Extension, null, IconCategory.ACTION, listOf("plugin", "addon")),
        IconItem("Api", Icons.Outlined.Api, null, IconCategory.ACTION, listOf("integration", "connect")),
        IconItem("Dns", Icons.Outlined.Dns, null, IconCategory.ACTION, listOf("server", "network")),
        IconItem("Web", Icons.Outlined.Web, null, IconCategory.ACTION, listOf("browser", "internet")),
        IconItem("RSS Feed", Icons.Outlined.RssFeed, null, IconCategory.ACTION, listOf("blog", "news")),
        IconItem("Subscriptions", Icons.Outlined.Subscriptions, null, IconCategory.ACTION, listOf("follow")),
        IconItem("Speed", Icons.Outlined.Speed, null, IconCategory.ACTION, listOf("fast", "performance")),
        IconItem("Bolt", Icons.Outlined.Bolt, null, IconCategory.ACTION, listOf("lightning", "fast")),
        IconItem("Rocket", Icons.Outlined.Rocket, null, IconCategory.ACTION, listOf("launch", "fast")),
        IconItem("Autorenew", Icons.Outlined.Autorenew, null, IconCategory.ACTION, listOf("refresh", "rotate")),
        IconItem("Bug Report", Icons.Outlined.BugReport, null, IconCategory.ACTION, listOf("issue", "debug")),
        IconItem("Build", Icons.Outlined.Build, null, IconCategory.ACTION, listOf("tools", "wrench")),
        IconItem("Account Balance", Icons.Outlined.AccountBalance, null, IconCategory.ACTION, listOf("bank")),
        IconItem("Account Box", Icons.Outlined.AccountBox, null, IconCategory.SOCIAL, listOf("user", "profile")),
        IconItem("Pin", Icons.Outlined.Pin, null, IconCategory.ACTION, listOf("mark", "location")),
        IconItem("Push Pin", Icons.Outlined.PushPin, null, IconCategory.ACTION, listOf("pin", "attach")),
        IconItem("Android", Icons.Outlined.Android, null, IconCategory.DEVICE, listOf("robot", "google")),
        IconItem("Anchor", Icons.Outlined.Anchor, null, IconCategory.CONTENT, listOf("link", "hold")),
        IconItem("Zoom In", Icons.Outlined.ZoomIn, null, IconCategory.ACTION, listOf("magnify", "enlarge")),
        IconItem("Zoom Out", Icons.Outlined.ZoomOut, null, IconCategory.ACTION, listOf("magnify", "reduce")),
        IconItem("Remove", Icons.Outlined.Remove, null, IconCategory.ACTION, listOf("minus", "subtract")),
        IconItem("Backspace", Icons.Outlined.Backspace, null, IconCategory.ACTION, listOf("delete", "erase")),
        IconItem("Arrow Drop Down", Icons.Outlined.ArrowDropDown, null, IconCategory.NAVIGATION, listOf("expand")),
        IconItem("Arrow Circle Up", Icons.Outlined.ArrowCircleUp, null, IconCategory.NAVIGATION, listOf("up")),
        IconItem("Arrow Circle Down", Icons.Outlined.ArrowCircleDown, null, IconCategory.NAVIGATION, listOf("down")),
        IconItem("Bookmarks", Icons.Outlined.Bookmarks, null, IconCategory.ACTION, listOf("saved", "favorites")),
        IconItem("Get App", Icons.Outlined.GetApp, null, IconCategory.ACTION, listOf("download")),
        IconItem("Star Border", Icons.Outlined.StarBorder, null, IconCategory.ACTION, listOf("rate", "outline")),
        IconItem("Favorite Border", Icons.Outlined.FavoriteBorder, null, IconCategory.ACTION, listOf("heart outline")),
        IconItem("Hand", Icons.Outlined.PanTool, null, IconCategory.ACTION, listOf("stop", "wave"))
    )
}

/**
 * Get Font Awesome icons list (subset of popular icons)
 */
private fun getFontAwesomeIcons(): List<IconItem> {
    return listOf(
        // Brand icons
        IconItem("Facebook", null, "fab fa-facebook", IconCategory.SOCIAL, listOf("social", "brand")),
        IconItem("Twitter/X", null, "fab fa-x-twitter", IconCategory.SOCIAL, listOf("social", "brand")),
        IconItem("Instagram", null, "fab fa-instagram", IconCategory.SOCIAL, listOf("social", "brand")),
        IconItem("LinkedIn", null, "fab fa-linkedin", IconCategory.SOCIAL, listOf("social", "brand")),
        IconItem("YouTube", null, "fab fa-youtube", IconCategory.SOCIAL, listOf("video", "brand")),
        IconItem("TikTok", null, "fab fa-tiktok", IconCategory.SOCIAL, listOf("video", "brand")),
        IconItem("GitHub", null, "fab fa-github", IconCategory.SOCIAL, listOf("code", "brand")),
        IconItem("GitLab", null, "fab fa-gitlab", IconCategory.SOCIAL, listOf("code", "brand")),
        IconItem("Discord", null, "fab fa-discord", IconCategory.SOCIAL, listOf("chat", "brand")),
        IconItem("Slack", null, "fab fa-slack", IconCategory.SOCIAL, listOf("chat", "brand")),
        IconItem("WhatsApp", null, "fab fa-whatsapp", IconCategory.SOCIAL, listOf("chat", "brand")),
        IconItem("Telegram", null, "fab fa-telegram", IconCategory.SOCIAL, listOf("chat", "brand")),
        IconItem("Reddit", null, "fab fa-reddit", IconCategory.SOCIAL, listOf("social", "brand")),
        IconItem("Pinterest", null, "fab fa-pinterest", IconCategory.SOCIAL, listOf("social", "brand")),
        IconItem("Snapchat", null, "fab fa-snapchat", IconCategory.SOCIAL, listOf("social", "brand")),
        IconItem("Twitch", null, "fab fa-twitch", IconCategory.SOCIAL, listOf("streaming", "brand")),
        IconItem("Spotify", null, "fab fa-spotify", IconCategory.SOCIAL, listOf("music", "brand")),
        IconItem("Apple", null, "fab fa-apple", IconCategory.SOCIAL, listOf("brand")),
        IconItem("Google", null, "fab fa-google", IconCategory.SOCIAL, listOf("brand")),
        IconItem("Microsoft", null, "fab fa-microsoft", IconCategory.SOCIAL, listOf("brand")),
        IconItem("Amazon", null, "fab fa-amazon", IconCategory.SOCIAL, listOf("brand")),
        IconItem("Stripe", null, "fab fa-stripe", IconCategory.SOCIAL, listOf("payment", "brand")),
        IconItem("PayPal", null, "fab fa-paypal", IconCategory.SOCIAL, listOf("payment", "brand")),
        IconItem("Bitcoin", null, "fab fa-bitcoin", IconCategory.SOCIAL, listOf("crypto", "brand")),
        IconItem("Ethereum", null, "fab fa-ethereum", IconCategory.SOCIAL, listOf("crypto", "brand")),
        IconItem("WordPress", null, "fab fa-wordpress", IconCategory.SOCIAL, listOf("cms", "brand")),
        IconItem("Shopify", null, "fab fa-shopify", IconCategory.SOCIAL, listOf("ecommerce", "brand")),
        IconItem("Figma", null, "fab fa-figma", IconCategory.SOCIAL, listOf("design", "brand")),
        IconItem("Sketch", null, "fab fa-sketch", IconCategory.SOCIAL, listOf("design", "brand")),
        IconItem("Dribbble", null, "fab fa-dribbble", IconCategory.SOCIAL, listOf("design", "brand")),
        IconItem("Behance", null, "fab fa-behance", IconCategory.SOCIAL, listOf("design", "brand")),
        IconItem("npm", null, "fab fa-npm", IconCategory.SOCIAL, listOf("code", "brand")),
        IconItem("Node.js", null, "fab fa-node-js", IconCategory.SOCIAL, listOf("code", "brand")),
        IconItem("React", null, "fab fa-react", IconCategory.SOCIAL, listOf("code", "brand")),
        IconItem("Vue.js", null, "fab fa-vuejs", IconCategory.SOCIAL, listOf("code", "brand")),
        IconItem("Angular", null, "fab fa-angular", IconCategory.SOCIAL, listOf("code", "brand")),
        IconItem("Python", null, "fab fa-python", IconCategory.SOCIAL, listOf("code", "brand")),
        IconItem("Java", null, "fab fa-java", IconCategory.SOCIAL, listOf("code", "brand")),
        IconItem("PHP", null, "fab fa-php", IconCategory.SOCIAL, listOf("code", "brand")),
        IconItem("Laravel", null, "fab fa-laravel", IconCategory.SOCIAL, listOf("code", "brand")),
        IconItem("Docker", null, "fab fa-docker", IconCategory.SOCIAL, listOf("devops", "brand")),
        IconItem("AWS", null, "fab fa-aws", IconCategory.SOCIAL, listOf("cloud", "brand")),
        IconItem("Digital Ocean", null, "fab fa-digital-ocean", IconCategory.SOCIAL, listOf("cloud", "brand")),
        IconItem("Stack Overflow", null, "fab fa-stack-overflow", IconCategory.SOCIAL, listOf("code", "brand")),
        IconItem("Medium", null, "fab fa-medium", IconCategory.SOCIAL, listOf("blog", "brand")),
        IconItem("Dev.to", null, "fab fa-dev", IconCategory.SOCIAL, listOf("blog", "brand")),

        // Solid icons (common)
        IconItem("Heart", null, "fas fa-heart", IconCategory.ACTION, listOf("love", "like")),
        IconItem("Star", null, "fas fa-star", IconCategory.ACTION, listOf("favorite", "rate")),
        IconItem("User", null, "fas fa-user", IconCategory.SOCIAL, listOf("person", "account")),
        IconItem("Users", null, "fas fa-users", IconCategory.SOCIAL, listOf("people", "group")),
        IconItem("Home", null, "fas fa-house", IconCategory.NAVIGATION, listOf("main")),
        IconItem("Search", null, "fas fa-magnifying-glass", IconCategory.ACTION, listOf("find")),
        IconItem("Settings", null, "fas fa-gear", IconCategory.ACTION, listOf("cog")),
        IconItem("Menu", null, "fas fa-bars", IconCategory.NAVIGATION, listOf("hamburger")),
        IconItem("Close", null, "fas fa-xmark", IconCategory.ACTION, listOf("x", "cancel")),
        IconItem("Check", null, "fas fa-check", IconCategory.ACTION, listOf("done", "tick")),
        IconItem("Plus", null, "fas fa-plus", IconCategory.ACTION, listOf("add", "new")),
        IconItem("Minus", null, "fas fa-minus", IconCategory.ACTION, listOf("remove")),
        IconItem("Arrow Right", null, "fas fa-arrow-right", IconCategory.NAVIGATION, listOf("next")),
        IconItem("Arrow Left", null, "fas fa-arrow-left", IconCategory.NAVIGATION, listOf("back")),
        IconItem("Arrow Up", null, "fas fa-arrow-up", IconCategory.NAVIGATION, listOf("up")),
        IconItem("Arrow Down", null, "fas fa-arrow-down", IconCategory.NAVIGATION, listOf("down")),
        IconItem("Chevron Right", null, "fas fa-chevron-right", IconCategory.NAVIGATION, listOf("next")),
        IconItem("Chevron Left", null, "fas fa-chevron-left", IconCategory.NAVIGATION, listOf("back")),
        IconItem("Envelope", null, "fas fa-envelope", IconCategory.COMMUNICATION, listOf("email", "mail")),
        IconItem("Phone", null, "fas fa-phone", IconCategory.COMMUNICATION, listOf("call")),
        IconItem("Comment", null, "fas fa-comment", IconCategory.COMMUNICATION, listOf("chat")),
        IconItem("Bell", null, "fas fa-bell", IconCategory.COMMUNICATION, listOf("notification")),
        IconItem("Calendar", null, "fas fa-calendar", IconCategory.ACTION, listOf("date")),
        IconItem("Clock", null, "fas fa-clock", IconCategory.ACTION, listOf("time")),
        IconItem("Location", null, "fas fa-location-dot", IconCategory.MAPS, listOf("pin", "place")),
        IconItem("Globe", null, "fas fa-globe", IconCategory.MAPS, listOf("world", "web")),
        IconItem("Link", null, "fas fa-link", IconCategory.CONTENT, listOf("url", "chain")),
        IconItem("Image", null, "fas fa-image", IconCategory.MEDIA, listOf("photo")),
        IconItem("Camera", null, "fas fa-camera", IconCategory.MEDIA, listOf("photo")),
        IconItem("Video", null, "fas fa-video", IconCategory.MEDIA, listOf("film")),
        IconItem("Music", null, "fas fa-music", IconCategory.MEDIA, listOf("audio")),
        IconItem("Microphone", null, "fas fa-microphone", IconCategory.MEDIA, listOf("audio")),
        IconItem("Play", null, "fas fa-play", IconCategory.AV, listOf("start")),
        IconItem("Pause", null, "fas fa-pause", IconCategory.AV, listOf("hold")),
        IconItem("Stop", null, "fas fa-stop", IconCategory.AV, listOf("end")),
        IconItem("Volume High", null, "fas fa-volume-high", IconCategory.AV, listOf("speaker")),
        IconItem("Volume Mute", null, "fas fa-volume-xmark", IconCategory.AV, listOf("silent")),
        IconItem("Download", null, "fas fa-download", IconCategory.ACTION, listOf("save")),
        IconItem("Upload", null, "fas fa-upload", IconCategory.ACTION, listOf("send")),
        IconItem("Share", null, "fas fa-share-nodes", IconCategory.ACTION, listOf("social")),
        IconItem("Copy", null, "fas fa-copy", IconCategory.ACTION, listOf("duplicate")),
        IconItem("Trash", null, "fas fa-trash", IconCategory.ACTION, listOf("delete")),
        IconItem("Edit", null, "fas fa-pen", IconCategory.ACTION, listOf("modify")),
        IconItem("Save", null, "fas fa-floppy-disk", IconCategory.ACTION, listOf("store")),
        IconItem("Print", null, "fas fa-print", IconCategory.ACTION, listOf("printer")),
        IconItem("Lock", null, "fas fa-lock", IconCategory.TOGGLE, listOf("secure")),
        IconItem("Unlock", null, "fas fa-lock-open", IconCategory.TOGGLE, listOf("open")),
        IconItem("Eye", null, "fas fa-eye", IconCategory.TOGGLE, listOf("view", "show")),
        IconItem("Eye Slash", null, "fas fa-eye-slash", IconCategory.TOGGLE, listOf("hide")),
        IconItem("Sun", null, "fas fa-sun", IconCategory.TOGGLE, listOf("light", "day")),
        IconItem("Moon", null, "fas fa-moon", IconCategory.TOGGLE, listOf("dark", "night")),
        IconItem("Code", null, "fas fa-code", IconCategory.EDITOR, listOf("programming")),
        IconItem("Terminal", null, "fas fa-terminal", IconCategory.EDITOR, listOf("console")),
        IconItem("Database", null, "fas fa-database", IconCategory.DEVICE, listOf("storage")),
        IconItem("Server", null, "fas fa-server", IconCategory.DEVICE, listOf("hosting")),
        IconItem("Cloud", null, "fas fa-cloud", IconCategory.DEVICE, listOf("storage")),
        IconItem("Laptop", null, "fas fa-laptop", IconCategory.DEVICE, listOf("computer")),
        IconItem("Mobile", null, "fas fa-mobile", IconCategory.DEVICE, listOf("phone")),
        IconItem("Desktop", null, "fas fa-desktop", IconCategory.DEVICE, listOf("computer")),
        IconItem("Wifi", null, "fas fa-wifi", IconCategory.DEVICE, listOf("network")),
        IconItem("Folder", null, "fas fa-folder", IconCategory.FILE, listOf("directory")),
        IconItem("File", null, "fas fa-file", IconCategory.FILE, listOf("document")),
        IconItem("Shopping Cart", null, "fas fa-cart-shopping", IconCategory.ACTION, listOf("buy")),
        IconItem("Credit Card", null, "fas fa-credit-card", IconCategory.ACTION, listOf("payment")),
        IconItem("Dollar Sign", null, "fas fa-dollar-sign", IconCategory.ACTION, listOf("money")),
        IconItem("Tag", null, "fas fa-tag", IconCategory.CONTENT, listOf("label")),
        IconItem("Gift", null, "fas fa-gift", IconCategory.ACTION, listOf("present")),
        IconItem("Trophy", null, "fas fa-trophy", IconCategory.ACTION, listOf("award", "winner")),
        IconItem("Chart Line", null, "fas fa-chart-line", IconCategory.ACTION, listOf("graph", "stats")),
        IconItem("Chart Bar", null, "fas fa-chart-bar", IconCategory.ACTION, listOf("graph", "stats")),
        IconItem("Chart Pie", null, "fas fa-chart-pie", IconCategory.ACTION, listOf("graph", "stats")),
        IconItem("Fire", null, "fas fa-fire", IconCategory.ACTION, listOf("hot", "trending")),
        IconItem("Bolt", null, "fas fa-bolt", IconCategory.ACTION, listOf("lightning", "fast")),
        IconItem("Rocket", null, "fas fa-rocket", IconCategory.ACTION, listOf("launch")),
        IconItem("Shield", null, "fas fa-shield", IconCategory.ACTION, listOf("security")),
        IconItem("Key", null, "fas fa-key", IconCategory.ACTION, listOf("password")),
        IconItem("Bug", null, "fas fa-bug", IconCategory.ACTION, listOf("issue", "debug")),
        IconItem("Circle Info", null, "fas fa-circle-info", IconCategory.ALERT, listOf("information")),
        IconItem("Circle Check", null, "fas fa-circle-check", IconCategory.ALERT, listOf("success")),
        IconItem("Circle Xmark", null, "fas fa-circle-xmark", IconCategory.ALERT, listOf("error")),
        IconItem("Triangle Exclamation", null, "fas fa-triangle-exclamation", IconCategory.ALERT, listOf("warning")),
        IconItem("Question", null, "fas fa-circle-question", IconCategory.ALERT, listOf("help")),
        IconItem("Spinner", null, "fas fa-spinner", IconCategory.ACTION, listOf("loading")),
        IconItem("Circle Notch", null, "fas fa-circle-notch", IconCategory.ACTION, listOf("loading")),
        IconItem("Thumbs Up", null, "fas fa-thumbs-up", IconCategory.SOCIAL, listOf("like")),
        IconItem("Thumbs Down", null, "fas fa-thumbs-down", IconCategory.SOCIAL, listOf("dislike")),
        IconItem("Smile", null, "fas fa-face-smile", IconCategory.SOCIAL, listOf("happy")),
        IconItem("Frown", null, "fas fa-face-frown", IconCategory.SOCIAL, listOf("sad")),
        IconItem("Meh", null, "fas fa-face-meh", IconCategory.SOCIAL, listOf("neutral")),
        IconItem("Graduation Cap", null, "fas fa-graduation-cap", IconCategory.ACTION, listOf("education")),
        IconItem("Book", null, "fas fa-book", IconCategory.CONTENT, listOf("read")),
        IconItem("Briefcase", null, "fas fa-briefcase", IconCategory.ACTION, listOf("work")),
        IconItem("Building", null, "fas fa-building", IconCategory.ACTION, listOf("office", "company")),
        IconItem("Industry", null, "fas fa-industry", IconCategory.ACTION, listOf("factory")),
        IconItem("Handshake", null, "fas fa-handshake", IconCategory.ACTION, listOf("deal", "partnership"))
    )
}
