package com.redboxsa.controller;

import android.app.admin.DeviceAdminReceiver;

/**
 * Device owner component of the Android 11 lockers (set once with `dpm set-device-owner`).
 * Never rename or move it: the device owner is bound to this class name, and losing it means re-provisioning every locker by hand.
 */
public class DeviceOwnerReceiver extends DeviceAdminReceiver {
}
