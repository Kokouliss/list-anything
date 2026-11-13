package com.kokouliss.listanything.core.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.kokouliss.listanything.core.ui.R

/** Material Symbols (outlined) shipped as vector drawables in this module. */
object AppIcons {
    val Add = R.drawable.core_ui_ic_add
    val ArrowBack = R.drawable.core_ui_ic_arrow_back
    val Camera = R.drawable.core_ui_ic_photo_camera
    val Check = R.drawable.core_ui_ic_check
    val Checklist = R.drawable.core_ui_ic_checklist
    val Close = R.drawable.core_ui_ic_close
    val Delete = R.drawable.core_ui_ic_delete
    val DeleteSweep = R.drawable.core_ui_ic_delete_sweep
    val DragHandle = R.drawable.core_ui_ic_drag_handle
    val Draw = R.drawable.core_ui_ic_draw
    val Edit = R.drawable.core_ui_ic_edit
    val Event = R.drawable.core_ui_ic_event
    val Gift = R.drawable.core_ui_ic_gift
    val Group = R.drawable.core_ui_ic_group
    val GroupAdd = R.drawable.core_ui_ic_group_add
    val Image = R.drawable.core_ui_ic_image
    val Link = R.drawable.core_ui_ic_link
    val Location = R.drawable.core_ui_ic_location_on
    val Logout = R.drawable.core_ui_ic_logout
    val Mail = R.drawable.core_ui_ic_mail
    val Map = R.drawable.core_ui_ic_map
    val Mic = R.drawable.core_ui_ic_mic
    val MoreVert = R.drawable.core_ui_ic_more_vert
    val OpenInNew = R.drawable.core_ui_ic_open_in_new
    val PersonAdd = R.drawable.core_ui_ic_person_add
    val Play = R.drawable.core_ui_ic_play_arrow
    val QrCodeScanner = R.drawable.core_ui_ic_qr_code_scanner
    val Refresh = R.drawable.core_ui_ic_refresh
    val Remove = R.drawable.core_ui_ic_remove
    val Send = R.drawable.core_ui_ic_send
    val Share = R.drawable.core_ui_ic_share
    val Stop = R.drawable.core_ui_ic_stop
    val Undo = R.drawable.core_ui_ic_undo
}

/** Pass null as [contentDescription] only when the icon sits next to text that says the same. */
@Composable
fun AppIcon(
    @DrawableRes icon: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    Icon(painter = painterResource(icon), contentDescription = contentDescription, modifier = modifier, tint = tint)
}
