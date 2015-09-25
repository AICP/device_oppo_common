/*
 * Copyright (C) 2014 SlimRoms Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.slim.device;

import com.slim.device.util.FileUtils;

import java.io.File;

/*
 * Very ugly class which enables or disables for now
 * all gesture controls on kernel level.
 * We need to do it this way for now to do not break 3rd party kernel.
 * Kernel should have a better per gesture control but as long
 * this is not changed by the manufacture we would break gesture control on every
 * 3rd party kernel. Hence we do it this way for now.
 */

public final class KernelControl {

    private static String GESTURE_PATH = "/proc/touchpanel/";
    private static String GESTURE_DOUBLE_TAP       = GESTURE_PATH + "double_tap_enable";
    private static String GESTURE_CAMERA           = GESTURE_PATH + "camera_enable";
    private static String GESTURE_FLASHLIGHT       = GESTURE_PATH + "flashlight_enable";
    private static String GESTURE_MUSIC            = GESTURE_PATH + "music_enable";
    private static String GESTURE_SILENT_VIB_SOUND = GESTURE_PATH + "silent_vib_sound_enable";

    private static String[] GESTURE_CONTROL_NODES = {
            GESTURE_DOUBLE_TAP,
            GESTURE_CAMERA,
            GESTURE_FLASHLIGHT ,
            GESTURE_MUSIC,
            GESTURE_SILENT_VIB_SOUND};

    private KernelControl() {
        // this class is not supposed to be instantiated
    }

    /**
     * Enable or disable gesture control.
     */
    public static void enableGestures(boolean enable) {
        for (int i = 0; i < GESTURE_CONTROL_NODES.length; i++) {
            if (new File(GESTURE_CONTROL_NODES[i]).exists()) {
                FileUtils.writeLine(GESTURE_CONTROL_NODES[i], enable ? "1" : "0");
            }
        }
    }

    /**
     * Check if kernel supports arrow up gesture - slim specific.
     * As well ugly but needed for now.
     */
    public static boolean isArrowUpSupported() {
        return new File(GESTURE_SILENT_VIB_SOUND).exists();
    }

    /**
     * Do we have touch control at all?
     */
    public static boolean hasTouchscreenGestures() {
        return new File(GESTURE_DOUBLE_TAP).exists()
                && new File(GESTURE_CAMERA).exists()
                && new File(GESTURE_FLASHLIGHT).exists()
                && new File(GESTURE_MUSIC).exists();
    }

}
