package com.chao.peakmusic.base;

import static org.junit.Assert.*;
import android.content.Intent;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import com.chao.peakmusic.activity.GuideActivity;
import com.chao.peakmusic.MainActivity;

@RunWith(org.robolectric.RobolectricTestRunner.class)
@Config(sdk = 28)
public class LaunchTest {
    @Test public void launcherOpensHomeImmediatelyWithoutWaitingForTimer() {
        try (org.robolectric.android.controller.ActivityController<GuideActivity> controller =
                Robolectric.buildActivity(GuideActivity.class).create()) {
            GuideActivity activity = controller.get();
            Intent intent = Shadows.shadowOf(activity).getNextStartedActivity();
            assertNotNull(intent);
            assertEquals(com.chao.peakmusic.MainActivity.class.getName(), intent.getComponent().getClassName());
            assertTrue(activity.isFinishing());
        }
    }
}
