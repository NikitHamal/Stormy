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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.sp
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
 * Icon item data class
 */
data class IconItem(
    val name: String,
    val icon: ImageVector,
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
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(IconCategory.ALL) }
    var selectedIcon by remember { mutableStateOf<IconItem?>(null) }

    // Get Material icons
    val icons = remember { getMaterialIcons() }

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
                    text = "Material Icons",
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

            Spacer(modifier = Modifier.height(12.dp))

            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                placeholder = { Text("Search icons...", fontSize = 14.sp) },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
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
            Icon(
                imageVector = iconItem.icon,
                contentDescription = iconItem.name,
                modifier = Modifier.size(24.dp),
                tint = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

@Composable
private fun IconDetailSection(
    icon: IconItem,
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
                    Icon(
                        imageVector = icon.icon,
                        contentDescription = icon.name,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
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
                    text = "Material Icons • ${icon.category.displayName}",
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
                code = "<span class=\"material-icons\">${icon.name.lowercase().replace(" ", "_")}</span>",
                onClick = onCopyCode,
                modifier = Modifier.weight(1f)
            )

            // CSS/Class code
            CopyCodeChip(
                label = "Class",
                code = "material-icons",
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
        IconItem("Add", Icons.Outlined.Add, IconCategory.ACTION, listOf("plus", "new", "create")),
        IconItem("Add Circle", Icons.Outlined.AddCircle, IconCategory.ACTION, listOf("plus", "new")),
        IconItem("Delete", Icons.Outlined.Delete, IconCategory.ACTION, listOf("remove", "trash", "bin")),
        IconItem("Edit", Icons.Outlined.Edit, IconCategory.ACTION, listOf("modify", "pen", "pencil")),
        IconItem("Save", Icons.Outlined.Save, IconCategory.ACTION, listOf("disk", "store")),
        IconItem("Search", Icons.Outlined.Search, IconCategory.ACTION, listOf("find", "magnify")),
        IconItem("Settings", Icons.Outlined.Settings, IconCategory.ACTION, listOf("gear", "cog", "preferences")),
        IconItem("Share", Icons.Outlined.Share, IconCategory.ACTION, listOf("social", "send")),
        IconItem("Download", Icons.Outlined.Download, IconCategory.ACTION, listOf("get", "save")),
        IconItem("Upload", Icons.Outlined.Upload, IconCategory.ACTION, listOf("send", "export")),
        IconItem("Refresh", Icons.Outlined.Refresh, IconCategory.ACTION, listOf("reload", "update")),
        IconItem("Print", Icons.Outlined.Print, IconCategory.ACTION, listOf("printer")),
        IconItem("Bookmark", Icons.Outlined.Bookmark, IconCategory.ACTION, listOf("save", "favorite")),
        IconItem("Favorite", Icons.Outlined.Favorite, IconCategory.ACTION, listOf("heart", "love", "like")),
        IconItem("Star", Icons.Outlined.Star, IconCategory.ACTION, listOf("rate", "favorite")),
        IconItem("Check", Icons.Outlined.Check, IconCategory.ACTION, listOf("done", "complete", "tick")),
        IconItem("Check Circle", Icons.Outlined.CheckCircle, IconCategory.ACTION, listOf("done", "complete")),
        IconItem("Close", Icons.Outlined.Close, IconCategory.ACTION, listOf("x", "cancel", "dismiss")),
        IconItem("Cancel", Icons.Outlined.Cancel, IconCategory.ACTION, listOf("close", "stop")),
        IconItem("Done", Icons.Outlined.Done, IconCategory.ACTION, listOf("check", "complete")),
        IconItem("Info", Icons.Outlined.Info, IconCategory.ACTION, listOf("about", "help")),
        IconItem("Help", Icons.Outlined.Help, IconCategory.ACTION, listOf("question", "support")),
        IconItem("Warning", Icons.Outlined.Warning, IconCategory.ALERT, listOf("caution", "alert")),
        IconItem("Error", Icons.Outlined.Error, IconCategory.ALERT, listOf("problem", "issue")),
        IconItem("Launch", Icons.Outlined.Launch, IconCategory.ACTION, listOf("open", "external")),
        IconItem("Send", Icons.Outlined.Send, IconCategory.ACTION, listOf("submit", "arrow")),
        IconItem("Copy", Icons.Outlined.ContentCopy, IconCategory.ACTION, listOf("duplicate", "clone")),
        IconItem("Paste", Icons.Outlined.ContentPaste, IconCategory.ACTION, listOf("clipboard")),
        IconItem("Sync", Icons.Outlined.Sync, IconCategory.ACTION, listOf("refresh", "update")),
        IconItem("Backup", Icons.Outlined.Backup, IconCategory.ACTION, listOf("cloud", "save")),
        IconItem("Archive", Icons.Outlined.Archive, IconCategory.ACTION, listOf("box", "storage")),
        IconItem("Login", Icons.Outlined.Login, IconCategory.ACTION, listOf("signin", "enter")),
        IconItem("Logout", Icons.Outlined.Logout, IconCategory.ACTION, listOf("signout", "exit")),

        // Navigation icons
        IconItem("Home", Icons.Outlined.Home, IconCategory.NAVIGATION, listOf("house", "main")),
        IconItem("Menu", Icons.Outlined.Menu, IconCategory.NAVIGATION, listOf("hamburger", "nav")),
        IconItem("Arrow Back", Icons.Outlined.ArrowBack, IconCategory.NAVIGATION, listOf("left", "previous")),
        IconItem("Arrow Forward", Icons.Outlined.ArrowForward, IconCategory.NAVIGATION, listOf("right", "next")),
        IconItem("Arrow Upward", Icons.Outlined.ArrowUpward, IconCategory.NAVIGATION, listOf("up")),
        IconItem("Arrow Downward", Icons.Outlined.ArrowDownward, IconCategory.NAVIGATION, listOf("down")),
        IconItem("Expand More", Icons.Outlined.ExpandMore, IconCategory.NAVIGATION, listOf("chevron down")),
        IconItem("Expand Less", Icons.Outlined.ExpandLess, IconCategory.NAVIGATION, listOf("chevron up")),
        IconItem("Chevron Left", Icons.Outlined.ChevronLeft, IconCategory.NAVIGATION, listOf("back")),
        IconItem("Chevron Right", Icons.Outlined.ChevronRight, IconCategory.NAVIGATION, listOf("forward")),
        IconItem("More Vert", Icons.Outlined.MoreVert, IconCategory.NAVIGATION, listOf("dots", "options")),
        IconItem("More Horiz", Icons.Outlined.MoreHoriz, IconCategory.NAVIGATION, listOf("dots", "options")),
        IconItem("Apps", Icons.Outlined.Apps, IconCategory.NAVIGATION, listOf("grid", "menu")),
        IconItem("Dashboard", Icons.Outlined.Dashboard, IconCategory.NAVIGATION, listOf("home", "overview")),
        IconItem("Explore", Icons.Outlined.Explore, IconCategory.NAVIGATION, listOf("compass", "discover")),

        // Communication icons
        IconItem("Email", Icons.Outlined.Email, IconCategory.COMMUNICATION, listOf("mail", "message")),
        IconItem("Mail", Icons.Outlined.Mail, IconCategory.COMMUNICATION, listOf("email", "letter")),
        IconItem("Chat", Icons.Outlined.Chat, IconCategory.COMMUNICATION, listOf("message", "conversation")),
        IconItem("Message", Icons.Outlined.Message, IconCategory.COMMUNICATION, listOf("chat", "sms")),
        IconItem("Call", Icons.Outlined.Call, IconCategory.COMMUNICATION, listOf("phone", "dial")),
        IconItem("Phone", Icons.Outlined.Phone, IconCategory.COMMUNICATION, listOf("call", "mobile")),
        IconItem("Video Call", Icons.Outlined.VideoCall, IconCategory.COMMUNICATION, listOf("camera")),
        IconItem("Forum", Icons.Outlined.Forum, IconCategory.COMMUNICATION, listOf("discussion", "chat")),
        IconItem("Comment", Icons.Outlined.Comment, IconCategory.COMMUNICATION, listOf("feedback", "message")),
        IconItem("Notifications", Icons.Outlined.Notifications, IconCategory.COMMUNICATION, listOf("bell", "alert")),
        IconItem("Campaign", Icons.Outlined.Campaign, IconCategory.COMMUNICATION, listOf("megaphone", "announce")),
        IconItem("Announcement", Icons.Outlined.Announcement, IconCategory.COMMUNICATION, listOf("news", "notice")),

        // Content icons
        IconItem("Article", Icons.Outlined.Article, IconCategory.CONTENT, listOf("post", "document")),
        IconItem("Book", Icons.Outlined.Book, IconCategory.CONTENT, listOf("read", "manual")),
        IconItem("Description", Icons.Outlined.Description, IconCategory.CONTENT, listOf("document", "file")),
        IconItem("Feed", Icons.Outlined.Feed, IconCategory.CONTENT, listOf("news", "timeline")),
        IconItem("Flag", Icons.Outlined.Flag, IconCategory.CONTENT, listOf("report", "mark")),
        IconItem("Label", Icons.Outlined.Label, IconCategory.CONTENT, listOf("tag", "category")),
        IconItem("Tag", Icons.Outlined.Tag, IconCategory.CONTENT, listOf("label", "hashtag")),
        IconItem("Link", Icons.Outlined.Link, IconCategory.CONTENT, listOf("url", "chain")),
        IconItem("Drafts", Icons.Outlined.Drafts, IconCategory.CONTENT, listOf("edit", "unsent")),
        IconItem("Report", Icons.Outlined.Report, IconCategory.CONTENT, listOf("flag", "issue")),
        IconItem("Reply", Icons.Outlined.Reply, IconCategory.CONTENT, listOf("respond", "answer")),

        // Social icons
        IconItem("Person", Icons.Outlined.Person, IconCategory.SOCIAL, listOf("user", "account")),
        IconItem("People", Icons.Outlined.People, IconCategory.SOCIAL, listOf("users", "group")),
        IconItem("Group", Icons.Outlined.Group, IconCategory.SOCIAL, listOf("team", "people")),
        IconItem("Account Circle", Icons.Outlined.AccountCircle, IconCategory.SOCIAL, listOf("user", "avatar")),
        IconItem("Person Add", Icons.Outlined.PersonAdd, IconCategory.SOCIAL, listOf("add user", "invite")),
        IconItem("Face", Icons.Outlined.Face, IconCategory.SOCIAL, listOf("emoji", "smile")),
        IconItem("Public", Icons.Outlined.Public, IconCategory.SOCIAL, listOf("globe", "world")),
        IconItem("Thumb Up", Icons.Outlined.ThumbUp, IconCategory.SOCIAL, listOf("like", "approve")),
        IconItem("Thumb Down", Icons.Outlined.ThumbDown, IconCategory.SOCIAL, listOf("dislike", "reject")),

        // Media icons
        IconItem("Image", Icons.Outlined.Image, IconCategory.MEDIA, listOf("photo", "picture")),
        IconItem("Photo", Icons.Outlined.Photo, IconCategory.MEDIA, listOf("image", "picture")),
        IconItem("Camera", Icons.Outlined.Camera, IconCategory.MEDIA, listOf("photo", "capture")),
        IconItem("Photo Camera", Icons.Outlined.PhotoCamera, IconCategory.MEDIA, listOf("camera", "capture")),
        IconItem("Videocam", Icons.Outlined.Videocam, IconCategory.MEDIA, listOf("video", "record")),
        IconItem("Movie", Icons.Outlined.Movie, IconCategory.MEDIA, listOf("film", "video")),
        IconItem("Music Note", Icons.Outlined.MusicNote, IconCategory.MEDIA, listOf("audio", "song")),
        IconItem("Mic", Icons.Outlined.Mic, IconCategory.MEDIA, listOf("microphone", "audio")),
        IconItem("Volume Up", Icons.Outlined.VolumeUp, IconCategory.MEDIA, listOf("speaker", "sound")),
        IconItem("Volume Off", Icons.Outlined.VolumeOff, IconCategory.MEDIA, listOf("mute", "silent")),

        // AV icons
        IconItem("Play", Icons.Outlined.PlayArrow, IconCategory.AV, listOf("start", "begin")),
        IconItem("Play Circle", Icons.Outlined.PlayCircle, IconCategory.AV, listOf("start", "video")),
        IconItem("Pause", Icons.Outlined.Pause, IconCategory.AV, listOf("hold", "wait")),
        IconItem("Stop", Icons.Outlined.Stop, IconCategory.AV, listOf("end", "halt")),

        // Device icons
        IconItem("Computer", Icons.Outlined.Computer, IconCategory.DEVICE, listOf("desktop", "pc")),
        IconItem("Laptop", Icons.Outlined.Laptop, IconCategory.DEVICE, listOf("notebook", "computer")),
        IconItem("Phone", Icons.Outlined.Phone, IconCategory.DEVICE, listOf("mobile", "smartphone")),
        IconItem("Devices", Icons.Outlined.Devices, IconCategory.DEVICE, listOf("responsive", "multi")),
        IconItem("Keyboard", Icons.Outlined.Keyboard, IconCategory.DEVICE, listOf("type", "input")),
        IconItem("Storage", Icons.Outlined.Storage, IconCategory.DEVICE, listOf("database", "server")),
        IconItem("Cloud", Icons.Outlined.Cloud, IconCategory.DEVICE, listOf("storage", "online")),
        IconItem("Cloud Download", Icons.Outlined.CloudDownload, IconCategory.DEVICE, listOf("download")),
        IconItem("Cloud Upload", Icons.Outlined.CloudUpload, IconCategory.DEVICE, listOf("upload")),
        IconItem("Wifi", Icons.Outlined.Wifi, IconCategory.DEVICE, listOf("internet", "network")),

        // File icons
        IconItem("Folder", Icons.Outlined.Folder, IconCategory.FILE, listOf("directory", "container")),
        IconItem("Attach File", Icons.Outlined.AttachFile, IconCategory.FILE, listOf("attachment", "clip")),

        // Editor icons
        IconItem("Code", Icons.Outlined.Code, IconCategory.EDITOR, listOf("programming", "development")),
        IconItem("Terminal", Icons.Outlined.Terminal, IconCategory.EDITOR, listOf("console", "command")),
        IconItem("Brush", Icons.Outlined.Brush, IconCategory.EDITOR, listOf("paint", "design")),
        IconItem("Colorize", Icons.Outlined.Colorize, IconCategory.EDITOR, listOf("picker", "dropper")),
        IconItem("Palette", Icons.Outlined.Palette, IconCategory.EDITOR, listOf("colors", "theme")),
        IconItem("Text Fields", Icons.Outlined.TextFields, IconCategory.EDITOR, listOf("typography", "font")),
        IconItem("Tune", Icons.Outlined.Tune, IconCategory.EDITOR, listOf("adjust", "settings")),
        IconItem("Layers", Icons.Outlined.Layers, IconCategory.EDITOR, listOf("stack", "overlap")),
        IconItem("Design Services", Icons.Outlined.DesignServices, IconCategory.EDITOR, listOf("creative")),
        IconItem("Style", Icons.Outlined.Style, IconCategory.EDITOR, listOf("format", "theme")),

        // Maps icons
        IconItem("Location", Icons.Outlined.LocationOn, IconCategory.MAPS, listOf("place", "pin")),
        IconItem("Place", Icons.Outlined.Place, IconCategory.MAPS, listOf("location", "marker")),
        IconItem("Map", Icons.Outlined.Map, IconCategory.MAPS, listOf("navigation", "directions")),
        IconItem("Language", Icons.Outlined.Language, IconCategory.MAPS, listOf("globe", "translate")),
        IconItem("Translate", Icons.Outlined.Translate, IconCategory.MAPS, listOf("language", "convert")),

        // Toggle icons
        IconItem("Visibility", Icons.Outlined.Visibility, IconCategory.TOGGLE, listOf("show", "eye")),
        IconItem("Visibility Off", Icons.Outlined.VisibilityOff, IconCategory.TOGGLE, listOf("hide", "eye")),
        IconItem("Lock", Icons.Outlined.Lock, IconCategory.TOGGLE, listOf("secure", "private")),
        IconItem("Lock Open", Icons.Outlined.LockOpen, IconCategory.TOGGLE, listOf("unlock", "open")),
        IconItem("Light Mode", Icons.Outlined.LightMode, IconCategory.TOGGLE, listOf("sun", "day")),
        IconItem("Dark Mode", Icons.Outlined.DarkMode, IconCategory.TOGGLE, listOf("moon", "night")),
        IconItem("Brightness", Icons.Outlined.BrightnessMedium, IconCategory.TOGGLE, listOf("contrast")),

        // More icons
        IconItem("Schedule", Icons.Outlined.Schedule, IconCategory.ACTION, listOf("time", "clock")),
        IconItem("Timer", Icons.Outlined.Timer, IconCategory.ACTION, listOf("stopwatch", "countdown")),
        IconItem("Alarm", Icons.Outlined.Alarm, IconCategory.ACTION, listOf("clock", "alert")),
        IconItem("Access Time", Icons.Outlined.AccessTime, IconCategory.ACTION, listOf("clock")),
        IconItem("Calendar", Icons.Outlined.CalendarToday, IconCategory.ACTION, listOf("date", "schedule")),
        IconItem("Calendar Month", Icons.Outlined.CalendarMonth, IconCategory.ACTION, listOf("date")),
        IconItem("Event", Icons.Outlined.Event, IconCategory.ACTION, listOf("calendar", "date")),
        IconItem("Today", Icons.Outlined.Today, IconCategory.ACTION, listOf("date", "calendar")),
        IconItem("Date Range", Icons.Outlined.DateRange, IconCategory.ACTION, listOf("calendar")),
        IconItem("History", Icons.Outlined.History, IconCategory.ACTION, listOf("past", "recent")),
        IconItem("Watch Later", Icons.Outlined.WatchLater, IconCategory.ACTION, listOf("clock", "save")),
        IconItem("Security", Icons.Outlined.Security, IconCategory.ACTION, listOf("shield", "protect")),
        IconItem("Shield", Icons.Outlined.Shield, IconCategory.ACTION, listOf("security", "protect")),
        IconItem("Key", Icons.Outlined.Key, IconCategory.ACTION, listOf("password", "auth")),
        IconItem("Verified", Icons.Outlined.Verified, IconCategory.ACTION, listOf("check", "approved")),
        IconItem("Badge", Icons.Outlined.Badge, IconCategory.ACTION, listOf("id", "identity")),
        IconItem("Support", Icons.Outlined.Support, IconCategory.ACTION, listOf("help", "assist")),
        IconItem("Psychology", Icons.Outlined.Psychology, IconCategory.ACTION, listOf("brain", "ai")),
        IconItem("Science", Icons.Outlined.Science, IconCategory.ACTION, listOf("lab", "experiment")),
        IconItem("School", Icons.Outlined.School, IconCategory.ACTION, listOf("education", "learn")),
        IconItem("Work", Icons.Outlined.Work, IconCategory.ACTION, listOf("job", "briefcase")),
        IconItem("Store", Icons.Outlined.Store, IconCategory.ACTION, listOf("shop", "retail")),
        IconItem("Shop", Icons.Outlined.Shop, IconCategory.ACTION, listOf("store", "buy")),
        IconItem("Shopping Cart", Icons.Outlined.ShoppingCart, IconCategory.ACTION, listOf("cart", "buy")),
        IconItem("Shopping Bag", Icons.Outlined.ShoppingBag, IconCategory.ACTION, listOf("purchase")),
        IconItem("Add Shopping Cart", Icons.Outlined.AddShoppingCart, IconCategory.ACTION, listOf("buy")),
        IconItem("Payment", Icons.Outlined.Payment, IconCategory.ACTION, listOf("card", "pay")),
        IconItem("Credit Card", Icons.Outlined.CreditCard, IconCategory.ACTION, listOf("payment")),
        IconItem("Money", Icons.Outlined.Money, IconCategory.ACTION, listOf("cash", "currency")),
        IconItem("Attach Money", Icons.Outlined.AttachMoney, IconCategory.ACTION, listOf("dollar", "price")),
        IconItem("Receipt", Icons.Outlined.Receipt, IconCategory.ACTION, listOf("invoice", "bill")),
        IconItem("Local Offer", Icons.Outlined.LocalOffer, IconCategory.ACTION, listOf("tag", "discount")),
        IconItem("QR Code", Icons.Outlined.QrCode, IconCategory.ACTION, listOf("scan", "barcode")),
        IconItem("Analytics", Icons.Outlined.Analytics, IconCategory.ACTION, listOf("stats", "data")),
        IconItem("Assessment", Icons.Outlined.Assessment, IconCategory.ACTION, listOf("report", "stats")),
        IconItem("Bar Chart", Icons.Outlined.BarChart, IconCategory.ACTION, listOf("graph", "stats")),
        IconItem("Pie Chart", Icons.Outlined.PieChart, IconCategory.ACTION, listOf("graph", "data")),
        IconItem("Leaderboard", Icons.Outlined.Leaderboard, IconCategory.ACTION, listOf("ranking", "stats")),
        IconItem("Data Usage", Icons.Outlined.DataUsage, IconCategory.ACTION, listOf("chart", "stats")),
        IconItem("Filter List", Icons.Outlined.FilterList, IconCategory.ACTION, listOf("sort", "organize")),
        IconItem("List", Icons.Outlined.List, IconCategory.ACTION, listOf("menu", "items")),
        IconItem("Inventory", Icons.Outlined.Inventory, IconCategory.ACTION, listOf("box", "stock")),
        IconItem("Task", Icons.Outlined.Task, IconCategory.ACTION, listOf("todo", "check")),
        IconItem("Assignment", Icons.Outlined.Assignment, IconCategory.ACTION, listOf("task", "document")),
        IconItem("Pending", Icons.Outlined.Pending, IconCategory.ACTION, listOf("waiting", "process")),
        IconItem("Quiz", Icons.Outlined.Quiz, IconCategory.ACTION, listOf("question", "test")),
        IconItem("Extension", Icons.Outlined.Extension, IconCategory.ACTION, listOf("plugin", "addon")),
        IconItem("Api", Icons.Outlined.Api, IconCategory.ACTION, listOf("integration", "connect")),
        IconItem("Dns", Icons.Outlined.Dns, IconCategory.ACTION, listOf("server", "network")),
        IconItem("Web", Icons.Outlined.Web, IconCategory.ACTION, listOf("browser", "internet")),
        IconItem("RSS Feed", Icons.Outlined.RssFeed, IconCategory.ACTION, listOf("blog", "news")),
        IconItem("Subscriptions", Icons.Outlined.Subscriptions, IconCategory.ACTION, listOf("follow")),
        IconItem("Speed", Icons.Outlined.Speed, IconCategory.ACTION, listOf("fast", "performance")),
        IconItem("Bolt", Icons.Outlined.Bolt, IconCategory.ACTION, listOf("lightning", "fast")),
        IconItem("Rocket", Icons.Outlined.Rocket, IconCategory.ACTION, listOf("launch", "fast")),
        IconItem("Autorenew", Icons.Outlined.Autorenew, IconCategory.ACTION, listOf("refresh", "rotate")),
        IconItem("Bug Report", Icons.Outlined.BugReport, IconCategory.ACTION, listOf("issue", "debug")),
        IconItem("Build", Icons.Outlined.Build, IconCategory.ACTION, listOf("tools", "wrench")),
        IconItem("Account Balance", Icons.Outlined.AccountBalance, IconCategory.ACTION, listOf("bank")),
        IconItem("Account Box", Icons.Outlined.AccountBox, IconCategory.SOCIAL, listOf("user", "profile")),
        IconItem("Pin", Icons.Outlined.Pin, IconCategory.ACTION, listOf("mark", "location")),
        IconItem("Push Pin", Icons.Outlined.PushPin, IconCategory.ACTION, listOf("pin", "attach")),
        IconItem("Android", Icons.Outlined.Android, IconCategory.DEVICE, listOf("robot", "google")),
        IconItem("Anchor", Icons.Outlined.Anchor, IconCategory.CONTENT, listOf("link", "hold")),
        IconItem("Zoom In", Icons.Outlined.ZoomIn, IconCategory.ACTION, listOf("magnify", "enlarge")),
        IconItem("Zoom Out", Icons.Outlined.ZoomOut, IconCategory.ACTION, listOf("magnify", "reduce")),
        IconItem("Remove", Icons.Outlined.Remove, IconCategory.ACTION, listOf("minus", "subtract")),
        IconItem("Backspace", Icons.Outlined.Backspace, IconCategory.ACTION, listOf("delete", "erase")),
        IconItem("Arrow Drop Down", Icons.Outlined.ArrowDropDown, IconCategory.NAVIGATION, listOf("expand")),
        IconItem("Arrow Circle Up", Icons.Outlined.ArrowCircleUp, IconCategory.NAVIGATION, listOf("up")),
        IconItem("Arrow Circle Down", Icons.Outlined.ArrowCircleDown, IconCategory.NAVIGATION, listOf("down")),
        IconItem("Bookmarks", Icons.Outlined.Bookmarks, IconCategory.ACTION, listOf("saved", "favorites")),
        IconItem("Get App", Icons.Outlined.GetApp, IconCategory.ACTION, listOf("download")),
        IconItem("Star Border", Icons.Outlined.StarBorder, IconCategory.ACTION, listOf("rate", "outline")),
        IconItem("Favorite Border", Icons.Outlined.FavoriteBorder, IconCategory.ACTION, listOf("heart outline")),
        IconItem("Hand", Icons.Outlined.PanTool, IconCategory.ACTION, listOf("stop", "wave"))
    )
}
