package com.avdbuddy.iconography

import androidx.compose.ui.graphics.vector.ImageVector
import com.avdbuddy.DeviceType

val DeviceType.icon: ImageVector
    get() = when (this) {
        DeviceType.PHONE, DeviceType.FOLDABLE -> Icons.DevicePhoneMobile
        DeviceType.TABLET -> Icons.DeviceTablet
        DeviceType.WEAR -> Icons.Clock
        DeviceType.DESKTOP -> Icons.ComputerDesktop
        DeviceType.TV -> Icons.Tv
        DeviceType.CAR -> Icons.Truck
        DeviceType.XR -> Icons.ViewfinderCircle
    }
