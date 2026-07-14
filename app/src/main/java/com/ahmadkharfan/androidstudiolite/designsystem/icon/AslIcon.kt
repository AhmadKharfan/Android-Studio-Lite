package com.ahmadkharfan.androidstudiolite.designsystem.icon
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.FormatAlignLeft
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.automirrored.outlined.ViewSidebar
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Api
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Coffee
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Cable
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Dock
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.Input
import androidx.compose.material.icons.outlined.Redo
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.SettingsSuggest
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material.icons.outlined.ToggleOff
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Commit
import androidx.compose.material.icons.outlined.Construction
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Diamond
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Functions
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Monitor
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.HorizontalRule
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.material3.Icon as M3Icon
private val iconMap: Map<String, ImageVector> = mapOf(
    "settings" to Icons.Filled.Settings,
    "sliders-horizontal" to Icons.Outlined.Tune,
    "menu" to Icons.Filled.Menu,
    "chevron-right" to Icons.Outlined.ChevronRight,
    "chevron-left" to Icons.Outlined.ChevronLeft,
    "chevron-down" to Icons.Outlined.ExpandMore,
    "chevron-up" to Icons.Outlined.ExpandLess,
    "arrow-left" to Icons.AutoMirrored.Filled.ArrowBack,
    "arrow-right" to Icons.AutoMirrored.Filled.ArrowForward,
    "more-vertical" to Icons.Filled.MoreVert,
    "more-horizontal" to Icons.Outlined.MoreHoriz,
    "x" to Icons.Filled.Close,
    "search" to Icons.Filled.Search,
    "plus" to Icons.Filled.Add,
    "check" to Icons.Filled.Check,
    "check-circle" to Icons.Filled.CheckCircle,
    "minus" to Icons.Outlined.HorizontalRule,
    "circle-check" to Icons.Outlined.CheckCircleOutline,
    "alert-circle" to Icons.Outlined.ErrorOutline,
    "alert-triangle" to Icons.Filled.Warning,
    "triangle-alert" to Icons.Filled.Warning,
    "octagon-alert" to Icons.Outlined.ErrorOutline,
    "check-circle-2" to Icons.Filled.CheckCircle,
    "info" to Icons.Filled.Info,
    "download" to Icons.Outlined.Download,
    "upload" to Icons.Outlined.Upload,
    "refresh-cw" to Icons.Filled.Refresh,
    "play" to Icons.Filled.PlayArrow,
    "square" to Icons.Outlined.Stop,
    "send" to Icons.AutoMirrored.Filled.Send,
    "copy" to Icons.Outlined.ContentCopy,
    "trash-2" to Icons.Outlined.DeleteOutline,
    "edit-2" to Icons.Filled.Edit,
    "save" to Icons.Outlined.Save,
    "undo-2" to Icons.AutoMirrored.Outlined.Undo,
    "sync" to Icons.Outlined.Sync,
    "power" to Icons.Outlined.PowerSettingsNew,
    "restart" to Icons.Outlined.RestartAlt,
    "folder" to Icons.Outlined.Folder,
    "folder-open" to Icons.Outlined.FolderOpen,
    "folder-lock" to Icons.Filled.Lock,
    "file" to Icons.AutoMirrored.Outlined.InsertDriveFile,
    "file-code" to Icons.Outlined.DataObject,
    "file-text" to Icons.Outlined.Description,
    "file-cog" to Icons.AutoMirrored.Outlined.Article,
    "file-plus-2" to Icons.AutoMirrored.Outlined.NoteAdd,
    "scroll-text" to Icons.Outlined.Description,
    "stethoscope" to Icons.Outlined.BugReport,
    "sparkles" to Icons.Outlined.AutoAwesome,
    "image" to Icons.Outlined.Image,
    "type" to Icons.Outlined.TextFields,
    "gem" to Icons.Outlined.Diamond,
    "github" to Icons.Outlined.Code,
    "arrow-up-right" to Icons.Outlined.NorthEast,
    "mail" to Icons.Outlined.Mail,
    "align-left" to Icons.AutoMirrored.Outlined.FormatAlignLeft,
    "pencil" to Icons.Outlined.DriveFileRenameOutline,
    "git-branch" to Icons.Outlined.AccountTree,
    "git-commit" to Icons.Outlined.Commit,
    "history" to Icons.Outlined.History,
    "hammer" to Icons.Outlined.Build,
    "wrench" to Icons.Outlined.Construction,
    "braces" to Icons.Outlined.DataObject,
    "code" to Icons.Outlined.Code,
    "terminal" to Icons.Outlined.Terminal,
    "cpu" to Icons.Outlined.Memory,
    "database" to Icons.Outlined.Storage,
    "hard-drive" to Icons.Outlined.Storage,
    "server" to Icons.Outlined.Storage,
    "package" to Icons.Outlined.Inventory2,
    "coffee" to Icons.Outlined.Coffee,
    "chart-no-axes-column" to Icons.Outlined.BarChart,
    "layers" to Icons.Outlined.Layers,
    "grid" to Icons.Outlined.GridView,
    "layout" to Icons.Outlined.Dashboard,
    "layout-template" to Icons.Outlined.Dashboard,
    "panel-left" to Icons.AutoMirrored.Outlined.ViewSidebar,
    "dock" to Icons.Outlined.Dock,
    "folder-search" to Icons.Outlined.FolderOpen,
    "search-x" to Icons.Outlined.SearchOff,
    "zap" to Icons.Outlined.Bolt,
    "gauge" to Icons.Outlined.Speed,
    "cable" to Icons.Outlined.Cable,
    "bot" to Icons.Outlined.SmartToy,
    "message-square" to Icons.AutoMirrored.Outlined.Chat,
    "key" to Icons.Outlined.Key,
    "fingerprint" to Icons.Outlined.Fingerprint,
    "shield" to Icons.Outlined.Shield,
    "shield-check" to Icons.Outlined.VerifiedUser,
    "globe" to Icons.Outlined.Language,
    "api" to Icons.Outlined.Api,
    "wifi" to Icons.Outlined.Wifi,
    "wifi-off" to Icons.Outlined.WifiOff,
    "server-cog" to Icons.Outlined.Dns,
    "blocks" to Icons.Outlined.Widgets,
    "settings-2" to Icons.Outlined.SettingsSuggest,
    "redo-2" to Icons.Outlined.Redo,
    "undo-2" to Icons.Outlined.Undo,
    "text-cursor-input" to Icons.Outlined.Input,
    "toggle-left" to Icons.Outlined.ToggleOff,
    "grip-vertical" to Icons.Outlined.DragIndicator,
    "life-buoy" to Icons.Outlined.SupportAgent,
    "rotate-ccw" to Icons.Outlined.Replay,
    "memory-stick" to Icons.Outlined.Memory,
    "loader" to Icons.Outlined.Autorenew,
    "circle-play" to Icons.Outlined.PlayCircleOutline,
    "square-function" to Icons.Outlined.Functions,
    "box" to Icons.Outlined.Inventory2,
    "shapes" to Icons.Outlined.Category,
    "key-round" to Icons.Outlined.Key,
    "user" to Icons.Outlined.PersonOutline,
    "users" to Icons.Outlined.People,
    "bell" to Icons.Outlined.NotificationsNone,
    "bell-filled" to Icons.Filled.Notifications,
    "moon" to Icons.Outlined.DarkMode,
    "sun" to Icons.Outlined.LightMode,
    "monitor" to Icons.Outlined.Monitor,
    "smartphone" to Icons.Outlined.Smartphone,
    "link" to Icons.Outlined.Link,
    "external-link" to Icons.AutoMirrored.Outlined.OpenInNew,
    "clock" to Icons.Outlined.Schedule,
    "calendar" to Icons.Outlined.CalendarToday,
    "book-open" to Icons.AutoMirrored.Outlined.MenuBook,
    "star" to Icons.Filled.Star,
    "list" to Icons.AutoMirrored.Filled.List,
    "eye" to Icons.Outlined.Visibility,
    "eye-off" to Icons.Outlined.VisibilityOff,
    "circle" to Icons.Outlined.Circle,
    "lock" to Icons.Filled.Lock,
    "home" to Icons.Filled.Home,
)
private val fallbackIcon: ImageVector get() = Icons.Outlined.Circle
fun aslIconFor(name: String): ImageVector = iconMap[name] ?: fallbackIcon
@Composable
fun AslIcon(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp? = null,
    tint: Color = LocalContentColor.current,
    contentDescription: String? = null,
) {
    val vector = aslIconFor(name)
    M3Icon(
        imageVector = vector,
        contentDescription = contentDescription,
        modifier = if (size != null) modifier.size(size) else modifier,
        tint = tint,
    )
}
