/*
 * Icon from Heroicons (heroicons).
 * License: MIT
 * 
 * MIT License
 * 
 * Copyright (c) Tailwind Labs, Inc.
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.avdbuddy.iconography

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Icons.DevicePhoneMobile: ImageVector
    get() {
        if (_DevicePhoneMobile != null) return _DevicePhoneMobile!!
        
        _DevicePhoneMobile = ImageVector.Builder(
            name = "device-phone-mobile",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(10.5f, 1.5f)
                horizontalLineTo(8.25f)
                arcTo(2.25f, 2.25f, 0f, false, false, 6f, 3.75f)
                verticalLineToRelative(16.5f)
                arcToRelative(2.25f, 2.25f, 0f, false, false, 2.25f, 2.25f)
                horizontalLineToRelative(7.5f)
                arcTo(2.25f, 2.25f, 0f, false, false, 18f, 20.25f)
                verticalLineTo(3.75f)
                arcToRelative(2.25f, 2.25f, 0f, false, false, -2.25f, -2.25f)
                horizontalLineTo(13.5f)
                moveToRelative(-3f, 0f)
                verticalLineTo(3f)
                horizontalLineToRelative(3f)
                verticalLineTo(1.5f)
                moveToRelative(-3f, 0f)
                horizontalLineToRelative(3f)
                moveToRelative(-3f, 18.75f)
                horizontalLineToRelative(3f)
            }
        }.build()
        
        return _DevicePhoneMobile!!
    }

private var _DevicePhoneMobile: ImageVector? = null

