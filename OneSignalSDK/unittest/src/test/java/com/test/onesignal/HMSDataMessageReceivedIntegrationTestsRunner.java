package com.test.onesignal;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;

import com.huawei.hms.push.RemoteMessage;
import com.onesignal.MockOSTimeImpl;
import com.onesignal.OneSignalPackagePrivateHelper.NotificationPayloadProcessorHMS;
import com.onesignal.ShadowBadgeCountUpdater;
import com.onesignal.ShadowGenerateNotification;
import com.onesignal.ShadowHmsRemoteMessage;
import com.onesignal.ShadowNotificationManagerCompat;
import com.onesignal.ShadowOSUtils;
import com.onesignal.ShadowRoboNotificationManager;
import com.onesignal.StaticResetHelper;
import com.onesignal.example.BlankActivity;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLog;

import java.util.UUID;

import static com.onesignal.OneSignalPackagePrivateHelper.HMSEventBridge_onMessageReceive;
import static com.onesignal.OneSignalPackagePrivateHelper.HMSProcessor_processDataMessageReceived;
import static com.onesignal.OneSignalPackagePrivateHelper.OSNotificationFormatHelper.PAYLOAD_OS_NOTIFICATION_ID;
import static com.onesignal.OneSignalPackagePrivateHelper.OSNotificationFormatHelper.PAYLOAD_OS_ROOT_CUSTOM;
import static com.onesignal.OneSignalPackagePrivateHelper.OneSignal_setTime;
import static com.test.onesignal.TestHelpers.threadAndTaskWait;
import static junit.framework.Assert.assertEquals;

@Config(
    packageName = "com.onesignal.example",
    shadows = {
        ShadowRoboNotificationManager.class,
        ShadowNotificationManagerCompat.class
    },
    sdk = 26
)
@RunWith(RobolectricTestRunner.class)
public class HMSDataMessageReceivedIntegrationTestsRunner {
    @SuppressLint("StaticFieldLeak")
    private static Activity blankActivity;
    private static ActivityController<BlankActivity> blankActivityController;

    private static final String ALERT_TEST_MESSAGE_BODY = "Test Message body";

    private MockOSTimeImpl time;

    @BeforeClass // Runs only once, before any tests
    public static void setUpClass() throws Exception {
        ShadowLog.stream = System.out;
        TestHelpers.beforeTestSuite();
        StaticResetHelper.saveStaticValues();
    }

    @Before
    public void beforeEachTest() throws Exception {
        TestHelpers.beforeTestInitAndCleanup();

        ShadowOSUtils.supportsHMS(true);

        time = new MockOSTimeImpl();
        OneSignal_setTime(time);

        blankActivityController = Robolectric.buildActivity(BlankActivity.class).create();
        blankActivity = blankActivityController.get();
    }

    @AfterClass
    public static void afterEverything() throws Exception {
        TestHelpers.beforeTestInitAndCleanup();
    }

    @After
    public void afterEachTest() throws Exception {
        TestHelpers.afterTestCleanup();
    }

    private static @NonNull String helperBasicOSPayload() throws JSONException {
        return new JSONObject() {{
            put(PAYLOAD_OS_ROOT_CUSTOM, new JSONObject() {{
                put(PAYLOAD_OS_NOTIFICATION_ID, UUID.randomUUID().toString());
            }});
            put("alert", ALERT_TEST_MESSAGE_BODY);
        }}.toString();
    }

    @Test
    public void nullData_shouldNotThrow() {
        NotificationPayloadProcessorHMS.processDataMessageReceived(blankActivity, null);
    }

    @Test
    public void blankData_shouldNotThrow() {
        NotificationPayloadProcessorHMS.processDataMessageReceived(blankActivity, "");
    }

    @Test
    @Config(shadows = { ShadowGenerateNotification.class })
    public void basicPayload_shouldDisplayNotification() throws Exception {
        blankActivityController.pause();
        HMSProcessor_processDataMessageReceived(blankActivity, helperBasicOSPayload());
        threadAndTaskWait();

        assertEquals(ALERT_TEST_MESSAGE_BODY, ShadowRoboNotificationManager.getLastShadowNotif().getBigText());
    }

    @Test
    @Config(shadows = { ShadowGenerateNotification.class, ShadowHmsRemoteMessage.class, ShadowBadgeCountUpdater.class })
    public void ttl_shouldNotDisplayNotification() throws Exception {
        blankActivityController.pause();

        long sentTime = 1_635_971_895_940L;
        int ttl = 60;

        time.setMockedCurrentThreadTimeMillis(sentTime * 1_000);

        ShadowHmsRemoteMessage.data = helperBasicOSPayload();
        ShadowHmsRemoteMessage.ttl = ttl;
        ShadowHmsRemoteMessage.sentTime = sentTime;

        HMSEventBridge_onMessageReceive(blankActivity, new RemoteMessage(new Bundle()));
        threadAndTaskWait();

        assertEquals(0, ShadowBadgeCountUpdater.lastCount);
    }

    // NOTE: More tests can be added but they would be duplicated with GenerateNotificationRunner
    //       In 4.0.0 or later these should be written in a reusable way between HMS, FCM, and ADM
}
