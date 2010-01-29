/*
 * Copyright (C) 2010 The Android Open Source Project
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

package android.app;

import org.xmlpull.v1.XmlPullParserException;

import android.annotation.SdkConstant;
import android.annotation.SdkConstant.SdkConstantType;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.RemoteCallback;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

import java.io.IOException;
import java.util.List;

/**
 * Public interface for managing policies enforced on a device.  Most clients
 * of this class must have published a {@link DeviceAdmin} that the user
 * has currently enabled.
 */
public class DevicePolicyManager {
    private static String TAG = "DevicePolicyManager";
    private static boolean DEBUG = false;
    private static boolean localLOGV = DEBUG || android.util.Config.LOGV;

    private final Context mContext;
    private final IDevicePolicyManager mService;
    
    private final Handler mHandler;

    /*package*/ DevicePolicyManager(Context context, Handler handler) {
        mContext = context;
        mHandler = handler;
        mService = IDevicePolicyManager.Stub.asInterface(
                ServiceManager.getService(Context.DEVICE_POLICY_SERVICE));
    }

    /**
     * Activity action: ask the user to add a new device administrator to the system.
     * The desired policy is the ComponentName of the policy in the
     * {@link #EXTRA_DEVICE_ADMIN} extra field.  This will invoke a UI to
     * bring the user through adding the device administrator to the system (or
     * allowing them to reject it).
     * 
     * <p>You can optionally include the {@link #EXTRA_ADD_EXPLANATION}
     * field to provide the user with additional explanation (in addition
     * to your component's description) about what is being added.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_ADD_DEVICE_ADMIN
            = "android.app.action.ADD_DEVICE_ADMIN";
    
    /**
     * The ComponentName of the administrator component.
     *
     * @see #ACTION_ADD_DEVICE_ADMIN
     */
    public static final String EXTRA_DEVICE_ADMIN = "android.app.extra.DEVICE_ADMIN";
    
    /**
     * An optional CharSequence providing additional explanation for why the
     * admin is being added.
     *
     * @see #ACTION_ADD_DEVICE_ADMIN
     */
    public static final String EXTRA_ADD_EXPLANATION = "android.app.extra.ADD_EXPLANATION";
    
    /**
     * Activity action: have the user enter a new password.  This activity
     * should be launched after using {@link #setPasswordMode(ComponentName, int)}
     * or {@link #setPasswordMinimumLength(ComponentName, int)} to have the
     * user enter a new password that meets the current requirements.  You can
     * use {@link #isActivePasswordSufficient()} to determine whether you need
     * to have the user select a new password in order to meet the current
     * constraints.  Upon being resumed from this activity,
     * you can check the new password characteristics to see if they are
     * sufficient.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_SET_NEW_PASSWORD
            = "android.app.action.SET_NEW_PASSWORD";
    
    /**
     * Return true if the given administrator component is currently
     * active (enabled) in the system.
     */
    public boolean isAdminActive(ComponentName who) {
        if (mService != null) {
            try {
                return mService.isAdminActive(who);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed talking with device policy service", e);
            }
        }
        return false;
    }
    
    /**
     * Return a list of all currently active device administrator's component
     * names.  Note that if there are no administrators than null may be
     * returned.
     */
    public List<ComponentName> getActiveAdmins() {
        if (mService != null) {
            try {
                return mService.getActiveAdmins();
            } catch (RemoteException e) {
                Log.w(TAG, "Failed talking with device policy service", e);
            }
        }
        return null;
    }
    
    /**
     * Remove a current administration component.  This can only be called
     * by the application that owns the administration component; if you
     * try to remove someone else's component, a security exception will be
     * thrown.
     */
    public void removeActiveAdmin(ComponentName who) {
        if (mService != null) {
            try {
                mService.removeActiveAdmin(who);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed talking with device policy service", e);
            }
        }
    }
    
    /**
     * Constant for {@link #setPasswordMode}: the policy has no requirements
     * for the password.  Note that mode constants are ordered so that higher
     * values are more restrictive.
     */
    public static final int PASSWORD_MODE_UNSPECIFIED = 0;
    
    /**
     * Constant for {@link #setPasswordMode}: the policy requires some kind
     * of password, but doesn't care what it is.  Note that mode constants
     * are ordered so that higher values are more restrictive.
     */
    public static final int PASSWORD_MODE_SOMETHING = 1000;
    
    /**
     * Constant for {@link #setPasswordMode}: the user must have at least a
     * numeric password.  Note that mode constants are ordered so that higher
     * values are more restrictive.
     */
    public static final int PASSWORD_MODE_NUMERIC = 2000;
    
    /**
     * Constant for {@link #setPasswordMode}: the user must have at least an
     * alphanumeric password.  Note that mode constants are ordered so that higher
     * values are more restrictive.
     */
    public static final int PASSWORD_MODE_ALPHANUMERIC = 3000;
    
    /**
     * Called by an application that is administering the device to set the
     * password restrictions it is imposing.  After setting this, the user
     * will not be able to enter a new password that is not at least as
     * restrictive as what has been set.  Note that the current password
     * will remain until the user has set a new one, so the change does not
     * take place immediately.  To prompt the user for a new password, use
     * {@link #ACTION_SET_NEW_PASSWORD} after setting this value.
     * 
     * <p>Mode constants are ordered so that higher values are more restrictive;
     * thus the highest requested mode constant (between the policy set here,
     * the user's preference, and any other considerations) is the one that
     * is in effect.
     * 
     * <p>The calling device admin must have requested
     * {@link DeviceAdminInfo#USES_POLICY_LIMIT_PASSWORD} to be able to call
     * this method; if it has not, a security exception will be thrown.
     * 
     * @param admin Which {@link DeviceAdmin} this request is associated with.
     * @param mode The new desired mode.  One of
     * {@link #PASSWORD_MODE_UNSPECIFIED}, {@link #PASSWORD_MODE_SOMETHING},
     * {@link #PASSWORD_MODE_NUMERIC}, or {@link #PASSWORD_MODE_ALPHANUMERIC}.
     */
    public void setPasswordMode(ComponentName admin, int mode) {
        if (mService != null) {
            try {
                mService.setPasswordMode(admin, mode);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed talking with device policy service", e);
            }
        }
    }
    
    /**
     * Retrieve the current minimum password mode for all admins
     * or a particular one.
     * @param admin The name of the admin component to check, or null to aggregate
     * all admins.
     */
    public int getPasswordMode(ComponentName admin) {
        if (mService != null) {
            try {
                return mService.getPasswordMode(admin);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed talking with device policy service", e);
            }
        }
        return PASSWORD_MODE_UNSPECIFIED;
    }
    
    /**
     * Called by an application that is administering the device to set the
     * minimum allowed password length.  After setting this, the user
     * will not be able to enter a new password that is not at least as
     * restrictive as what has been set.  Note that the current password
     * will remain until the user has set a new one, so the change does not
     * take place immediately.  To prompt the user for a new password, use
     * {@link #ACTION_SET_NEW_PASSWORD} after setting this value.  This
     * constraint is only imposed if the administrator has also requested either
     * {@link #PASSWORD_MODE_NUMERIC} or {@link #PASSWORD_MODE_ALPHANUMERIC}
     * with {@link #setPasswordMode}.
     * 
     * <p>The calling device admin must have requested
     * {@link DeviceAdminInfo#USES_POLICY_LIMIT_PASSWORD} to be able to call
     * this method; if it has not, a security exception will be thrown.
     * 
     * @param admin Which {@link DeviceAdmin} this request is associated with.
     * @param length The new desired minimum password length.  A value of 0
     * means there is no restriction.
     */
    public void setPasswordMinimumLength(ComponentName admin, int length) {
        if (mService != null) {
            try {
                mService.setPasswordMinimumLength(admin, length);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed talking with device policy service", e);
            }
        }
    }
    
    /**
     * Retrieve the current minimum password length for all admins
     * or a particular one.
     * @param admin The name of the admin component to check, or null to aggregate
     * all admins.
     */
    public int getPasswordMinimumLength(ComponentName admin) {
        if (mService != null) {
            try {
                return mService.getPasswordMinimumLength(admin);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed talking with device policy service", e);
            }
        }
        return 0;
    }
    
    /**
     * Return the maximum password length that the device supports for a
     * particular password mode.
     * @param mode The mode being interrogated.
     * @return Returns the maximum length that the user can enter.
     */
    public int getPasswordMaximumLength(int mode) {
        // Kind-of arbitrary.
        return 16;
    }
    
    /**
     * Determine whether the current password the user has set is sufficient
     * to meet the policy requirements (mode, minimum length) that have been
     * requested.
     * 
     * <p>The calling device admin must have requested
     * {@link DeviceAdminInfo#USES_POLICY_LIMIT_PASSWORD} to be able to call
     * this method; if it has not, a security exception will be thrown.
     * 
     * @return Returns true if the password meets the current requirements,
     * else false.
     */
    public boolean isActivePasswordSufficient() {
        if (mService != null) {
            try {
                return mService.isActivePasswordSufficient();
            } catch (RemoteException e) {
                Log.w(TAG, "Failed talking with device policy service", e);
            }
        }
        return false;
    }
    
    /**
     * Retrieve the number of times the user has failed at entering a
     * password since that last successful password entry.
     * 
     * <p>The calling device admin must have requested
     * {@link DeviceAdminInfo#USES_POLICY_WATCH_LOGIN} to be able to call
     * this method; if it has not, a security exception will be thrown.
     */
    public int getCurrentFailedPasswordAttempts() {
        if (mService != null) {
            try {
                return mService.getCurrentFailedPasswordAttempts();
            } catch (RemoteException e) {
                Log.w(TAG, "Failed talking with device policy service", e);
            }
        }
        return -1;
    }

    /**
     * Set the maximum number of failed password attempts that are allowed
     * before the device wipes its data.  This is convenience for implementing
     * the corresponding functionality with a combination of watching failed
     * password attempts and calling {@link #wipeData} upon reaching a certain
     * count, and as such requires that you request both
     * {@link DeviceAdminInfo#USES_POLICY_WATCH_LOGIN} and
     * {@link DeviceAdminInfo#USES_POLICY_WIPE_DATA}}.
     * 
     * @param admin Which {@link DeviceAdmin} this request is associated with.
     * @param num The number of failed password attempts at which point the
     * device will wipe its data.
     */
    public void setMaximumFailedPasswordsForWipe(ComponentName admin, int num) {
        if (mService != null) {
            try {
                mService.setMaximumFailedPasswordsForWipe(admin, num);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed talking with device policy service", e);
            }
        }
    }
    
    /**
     * Retrieve the current maximum number of login attempts that are allowed
     * before the device wipes itself, for all admins
     * or a particular one.
     * @param admin The name of the admin component to check, or null to aggregate
     * all admins.
     */
    public int getMaximumFailedPasswordsForWipe(ComponentName admin) {
        if (mService != null) {
            try {
                return mService.getMaximumFailedPasswordsForWipe(admin);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed talking with device policy service", e);
            }
        }
        return 0;
    }
    
    /**
     * Force a new password on the user.  This takes effect immediately.  The
     * given password must meet the current password minimum length constraint
     * or it will be rejected.  The given password will be accepted regardless
     * of the current password mode, automatically adjusting the password mode
     * higher if needed to meet the requirements of all active administrators.
     * (The string you give here is acceptable for any mode;
     * if it contains only digits, that is still an acceptable alphanumeric
     * password.)
     * 
     * <p>The calling device admin must have requested
     * {@link DeviceAdminInfo#USES_POLICY_RESET_PASSWORD} to be able to call
     * this method; if it has not, a security exception will be thrown.
     * 
     * @param password The new password for the user.
     * @return Returns true if the password was applied, or false if it is
     * not acceptable for the current constraints.
     */
    public boolean resetPassword(String password) {
        if (mService != null) {
            try {
                return mService.resetPassword(password);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed talking with device policy service", e);
            }
        }
        return false;
    }
    
    /**
     * Called by an application that is administering the device to set the
     * maximum time for user activity until the device will lock.  This limits
     * the length that the user can set.  It takes effect immediately.
     * 
     * <p>The calling device admin must have requested
     * {@link DeviceAdminInfo#USES_POLICY_LIMIT_UNLOCK} to be able to call
     * this method; if it has not, a security exception will be thrown.
     * 
     * @param admin Which {@link DeviceAdmin} this request is associated with.
     * @param timeMs The new desired maximum time to lock in milliseconds.
     * A value of 0 means there is no restriction.
     */
    public void setMaximumTimeToLock(ComponentName admin, long timeMs) {
        if (mService != null) {
            try {
                mService.setMaximumTimeToLock(admin, timeMs);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed talking with device policy service", e);
            }
        }
    }
    
    /**
     * Retrieve the current maximum time to unlock for all admins
     * or a particular one.
     * @param admin The name of the admin component to check, or null to aggregate
     * all admins.
     */
    public long getMaximumTimeToLock(ComponentName admin) {
        if (mService != null) {
            try {
                return mService.getMaximumTimeToLock(admin);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed talking with device policy service", e);
            }
        }
        return 0;
    }
    
    /**
     * Make the device lock immediately, as if the lock screen timeout has
     * expired at the point of this call.
     * 
     * <p>The calling device admin must have requested
     * {@link DeviceAdminInfo#USES_POLICY_FORCE_LOCK} to be able to call
     * this method; if it has not, a security exception will be thrown.
     */
    public void lockNow() {
        if (mService != null) {
            try {
                mService.lockNow();
            } catch (RemoteException e) {
                Log.w(TAG, "Failed talking with device policy service", e);
            }
        }
    }
    
    /**
     * Ask the user date be wiped.  This will cause the device to reboot,
     * erasing all user data while next booting up.  External storage such
     * as SD cards will not be erased.
     * 
     * <p>The calling device admin must have requested
     * {@link DeviceAdminInfo#USES_POLICY_WIPE_DATA} to be able to call
     * this method; if it has not, a security exception will be thrown.
     * 
     * @param flags Bit mask of additional options: currently must be 0.
     */
    public void wipeData(int flags) {
        if (mService != null) {
            try {
                mService.wipeData(flags);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed talking with device policy service", e);
            }
        }
    }
    
    /**
     * @hide
     */
    public void setActiveAdmin(ComponentName policyReceiver) {
        if (mService != null) {
            try {
                mService.setActiveAdmin(policyReceiver);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed talking with device policy service", e);
            }
        }
    }
    
    /**
     * @hide
     */
    public DeviceAdminInfo getAdminInfo(ComponentName cn) {
        ActivityInfo ai;
        try {
            ai = mContext.getPackageManager().getReceiverInfo(cn,
                    PackageManager.GET_META_DATA);
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Unable to retrieve device policy " + cn, e);
            return null;
        }
        
        ResolveInfo ri = new ResolveInfo();
        ri.activityInfo = ai;
        
        try {
            return new DeviceAdminInfo(mContext, ri);
        } catch (XmlPullParserException e) {
            Log.w(TAG, "Unable to parse device policy " + cn, e);
            return null;
        } catch (IOException e) {
            Log.w(TAG, "Unable to parse device policy " + cn, e);
            return null;
        }
    }
    
    /**
     * @hide
     */
    public void getRemoveWarning(ComponentName admin, RemoteCallback result) {
        if (mService != null) {
            try {
                mService.getRemoveWarning(admin, result);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed talking with device policy service", e);
            }
        }
    }

    /**
     * @hide
     */
    public void setActivePasswordState(int mode, int length) {
        if (mService != null) {
            try {
                mService.setActivePasswordState(mode, length);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed talking with device policy service", e);
            }
        }
    }
    
    /**
     * @hide
     */
    public void reportFailedPasswordAttempt() {
        if (mService != null) {
            try {
                mService.reportFailedPasswordAttempt();
            } catch (RemoteException e) {
                Log.w(TAG, "Failed talking with device policy service", e);
            }
        }
    }
    
    /**
     * @hide
     */
    public void reportSuccessfulPasswordAttempt() {
        if (mService != null) {
            try {
                mService.reportSuccessfulPasswordAttempt();
            } catch (RemoteException e) {
                Log.w(TAG, "Failed talking with device policy service", e);
            }
        }
    }
}
