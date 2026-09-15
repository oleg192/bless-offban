package ru.bless.skystrike;

import android.content.Context;
import android.content.pm.PackageManager;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class OfflineGameTest {
    private String js(ActivityScenario<MainActivity> scenario, String script) throws Exception {
        AtomicReference<String> result = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        scenario.onActivity(a -> a.gameView().evaluateJavascript(script, value -> { result.set(value); done.countDown(); }));
        assertTrue("JavaScript callback timed out", done.await(15, TimeUnit.SECONDS));
        return result.get();
    }
    private void waitFor(ActivityScenario<MainActivity> scenario, String expression) throws Exception {
        long until = System.currentTimeMillis()+45000;
        while (System.currentTimeMillis()<until) {
            if ("true".equals(js(scenario, expression))) return;
            Thread.sleep(180);
        }
        fail("Game did not reach expected state: " + expression + " / " + js(scenario,"document.getElementById('fatal-text').textContent"));
    }
    @Test public void offlineGameRendersStartsPausesAndLoadsThreeMaps() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        String[] permissions = context.getPackageManager().getPackageInfo(context.getPackageName(), PackageManager.GET_PERMISSIONS).requestedPermissions;
        if (permissions != null) for (String permission : permissions) assertNotEquals("android.permission.INTERNET", permission);
        for (String clip : new String[]{"ready","altitude","lock","missile","target","damage","win"})
            try (java.io.InputStream stream = context.getAssets().open("game/audio/"+clip+".wav")) { assertTrue(stream.available()>1000); }
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            waitFor(scenario,"!!window.__gameReady && !!window.Game.status.webgl");
            for (int map=0;map<3;map++) {
                js(scenario,"window.Game.menu(); document.querySelector('[data-map=\""+map+"\"]').click(); document.getElementById('start').click();");
                waitFor(scenario,"window.Game.playing && !window.Game.paused && window.Game.status.map==="+map);
                assertEquals("true",js(scenario,"window.Game.sim.entities.length>=12 && window.Game.sim.altitude>100 && !window.Game.world.renderer.getContext().isContextLost()"));
                js(scenario,"document.getElementById('pause').click()");
                waitFor(scenario,"window.Game.paused");
                assertEquals("true",js(scenario,"!!localStorage.getItem('sky-strike-flight-v1')"));
                js(scenario,"document.getElementById('continue').click()");
                waitFor(scenario,"!window.Game.paused");
            }
        }
    }
}
