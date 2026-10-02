package app.edroid.ui.search

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Abc
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Castle
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.ConfirmationNumber
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Emergency
import androidx.compose.material.icons.rounded.EnhancedEncryption
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.Games
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.KeyboardVoice
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LocalLibrary
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.MoreTime
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PermMedia
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.RestaurantMenu
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.automirrored.rounded.ScreenShare
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material.icons.rounded.ShareLocation
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.automirrored.rounded.StickyNote2
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.TheaterComedy
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.TipsAndUpdates
import androidx.compose.material.icons.rounded.Toys
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.VideoCall
import androidx.compose.material.icons.rounded.VideogameAsset
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Icon for an F-Droid category id (the untranslated key of `repo.categories`, e.g. "Puzzle Game").
 * Covers the official repo's catalogue plus the legacy pre-2024 ids that third-party repos still
 * use; anything else falls back to a generic glyph so new categories still render.
 */
fun categoryIcon(id: String): ImageVector = CategoryIcons[id] ?: Icons.Rounded.Category

/**
 * Badge color for a category: picked from [CategoryColors] by hashing the id, so it looks random
 * but each category keeps the same color across launches. Mid-tone so white icons read on it in
 * both light and dark themes.
 */
fun categoryColor(id: String): Color = CategoryColors[Math.floorMod(id.hashCode(), CategoryColors.size)]

private val CategoryColors = listOf(
    Color(0xFF6A52A8), // purple
    Color(0xFF10B37D), // emerald
    Color(0xFFA34E63), // rose
    Color(0xFFA3A055), // olive
    Color(0xFF4F6DC3), // blue
    Color(0xFF51A37A), // sea green
    Color(0xFFD9822B), // orange
    Color(0xFF2A9DA8), // teal
    Color(0xFFC2508F), // pink
    Color(0xFF8D6E63), // brown
    Color(0xFF5C6BC0), // indigo
    Color(0xFFD45B4F), // red
)

private val CategoryIcons: Map<String, ImageVector> = mapOf(
    "Action Game" to Icons.Rounded.SportsEsports,
    "AI Chat" to Icons.Rounded.SmartToy,
    "Alarm Clock" to Icons.Rounded.Alarm,
    "Ambient Sound" to Icons.Rounded.Waves,
    "App Manager" to Icons.Rounded.Apps,
    "App Store & Updater" to Icons.Rounded.Storefront,
    "Audiobook" to Icons.Rounded.Headphones,
    "Battery" to Icons.Rounded.BatteryFull,
    "Board Game" to Icons.Rounded.GridOn,
    "Bookmark" to Icons.Rounded.Bookmarks,
    "Browser" to Icons.Rounded.Language,
    "Calculator" to Icons.Rounded.Calculate,
    "Calendar & Agenda" to Icons.Rounded.CalendarMonth,
    "Camera" to Icons.Rounded.PhotoCamera,
    "Card Game" to Icons.Rounded.Style,
    "Cast" to Icons.Rounded.Cast,
    "Casual Game" to Icons.Rounded.Toys,
    "Clock" to Icons.Rounded.AccessTime,
    "Cloud Storage & File Sync" to Icons.Rounded.CloudSync,
    "Code & Forge" to Icons.Rounded.Code,
    "Connectivity" to Icons.Rounded.Wifi,
    "Contact" to Icons.Rounded.Contacts,
    "Development" to Icons.Rounded.Terminal,
    "Dice" to Icons.Rounded.Casino,
    "Diet" to Icons.Rounded.Restaurant,
    "DNS & Hosts" to Icons.Rounded.Dns,
    "Download" to Icons.Rounded.Download,
    "Draw" to Icons.Rounded.Brush,
    "Ebook Reader" to Icons.Rounded.AutoStories,
    "Educational Game" to Icons.Rounded.Quiz,
    "Email" to Icons.Rounded.Email,
    "Emergency Action" to Icons.Rounded.Emergency,
    "Emulator" to Icons.Rounded.Gamepad,
    "File Encryption & Vault" to Icons.Rounded.Lock,
    "File Manager" to Icons.Rounded.Folder,
    "File Transfer" to Icons.Rounded.SwapHoriz,
    "Finance Manager" to Icons.Rounded.AccountBalance,
    "Firewall" to Icons.Rounded.Shield,
    "Flashlight" to Icons.Rounded.FlashlightOn,
    "Forum" to Icons.Rounded.Forum,
    "Gallery" to Icons.Rounded.PhotoLibrary,
    "Game Helper" to Icons.Rounded.TipsAndUpdates,
    "Graphics" to Icons.Rounded.Palette,
    "Habit Tracker" to Icons.Rounded.TaskAlt,
    "Health Manager" to Icons.Rounded.MonitorHeart,
    "Icon Pack" to Icons.Rounded.Widgets,
    "Internet" to Icons.Rounded.Public,
    "Inventory" to Icons.Rounded.Inventory2,
    "Keyboard & IME" to Icons.Rounded.Keyboard,
    "Launcher" to Icons.Rounded.Home,
    "Local Media Player" to Icons.Rounded.PlayCircle,
    "Location Tracker & Sharer" to Icons.Rounded.ShareLocation,
    "Lyrics" to Icons.Rounded.Lyrics,
    "Market & Price" to Icons.Rounded.Sell,
    "Medication" to Icons.Rounded.Medication,
    "Meditation" to Icons.Rounded.SelfImprovement,
    "Mental Health" to Icons.Rounded.Psychology,
    "Messaging" to Icons.AutoMirrored.Rounded.Chat,
    "Multimedia" to Icons.Rounded.PermMedia,
    "Music Practice Tool" to Icons.Rounded.MusicNote,
    "Navigation" to Icons.Rounded.Map,
    "Network Analyzer" to Icons.Rounded.NetworkCheck,
    "News" to Icons.Rounded.Newspaper,
    "Note" to Icons.AutoMirrored.Rounded.StickyNote2,
    "Notification" to Icons.Rounded.Notifications,
    "OCR" to Icons.Rounded.DocumentScanner,
    "Online Media Player" to Icons.Rounded.SmartDisplay,
    "Party Game" to Icons.Rounded.Celebration,
    "Pass Wallet" to Icons.Rounded.ConfirmationNumber,
    "Password & 2FA" to Icons.Rounded.Password,
    "Phone & SMS" to Icons.Rounded.Phone,
    "Platformer Game" to Icons.Rounded.VideogameAsset,
    "Podcast" to Icons.Rounded.Podcasts,
    "Public Transport" to Icons.Rounded.DirectionsBus,
    "Push" to Icons.Rounded.NotificationsActive,
    "Puzzle Game" to Icons.Rounded.Extension,
    "Radio" to Icons.Rounded.Radio,
    "Reading" to Icons.Rounded.LocalLibrary,
    "Recipe Manager" to Icons.Rounded.RestaurantMenu,
    "Recorder" to Icons.Rounded.Mic,
    "Religion" to Icons.Rounded.VolunteerActivism,
    "Role-Playing Game" to Icons.Rounded.Castle,
    "Remote Access" to Icons.AutoMirrored.Rounded.ScreenShare,
    "Remote Controller" to Icons.Rounded.SettingsRemote,
    "Schedule" to Icons.Rounded.Event,
    "Science & Education" to Icons.Rounded.Science,
    "Security" to Icons.Rounded.Security,
    "Shooter Game" to Icons.Rounded.GpsFixed,
    "Strategy Game" to Icons.Rounded.Flag,
    "Shopping List" to Icons.Rounded.ShoppingCart,
    "Social Network" to Icons.Rounded.Groups,
    "Speech Recognizer" to Icons.Rounded.KeyboardVoice,
    "Sport Game" to Icons.Rounded.SportsSoccer,
    "Sports & Health" to Icons.AutoMirrored.Rounded.DirectionsRun,
    "Stopwatch" to Icons.Rounded.Timer,
    "System" to Icons.Rounded.Settings,
    "Task" to Icons.Rounded.Checklist,
    "Text Encryption" to Icons.Rounded.EnhancedEncryption,
    "Text Editor" to Icons.Rounded.EditNote,
    "Text to Speech" to Icons.Rounded.RecordVoiceOver,
    "Theming" to Icons.Rounded.ColorLens,
    "Time Tracker" to Icons.Rounded.MoreTime,
    "Timer" to Icons.Rounded.HourglassBottom,
    "Translation & Dictionary" to Icons.Rounded.Translate,
    "Unit Convertor" to Icons.Rounded.Straighten,
    "Visual Novel" to Icons.Rounded.TheaterComedy,
    "Voice & Video Chat" to Icons.Rounded.VideoCall,
    "Volume" to Icons.AutoMirrored.Rounded.VolumeUp,
    "VPN & Proxy" to Icons.Rounded.VpnKey,
    "Wallet" to Icons.Rounded.AccountBalanceWallet,
    "Wallpaper" to Icons.Rounded.Wallpaper,
    "Weather" to Icons.Rounded.WbSunny,
    "Word Game" to Icons.Rounded.Abc,
    "Workout" to Icons.Rounded.FitnessCenter,
    "Writing" to Icons.Rounded.Edit,
    // Legacy ids not covered above.
    "Games" to Icons.Rounded.Games,
    "Money" to Icons.Rounded.Payments,
    "Time" to Icons.Rounded.AccessTime,
)
