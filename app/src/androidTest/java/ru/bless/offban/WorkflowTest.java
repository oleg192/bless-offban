package ru.bless.offban;

import android.content.ClipboardManager;
import android.content.Context;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import androidx.test.espresso.Espresso;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static org.hamcrest.Matchers.anything;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class WorkflowTest {
    @Before public void reset() {
        Context context = ApplicationProvider.getApplicationContext();
        assertTrue(context.getSharedPreferences("offban_v1", Context.MODE_PRIVATE).edit().clear().commit());
    }

    private void input(String text) {
        onView(withId(R.id.entry_input)).inRoot(isDialog()).perform(replaceText(text), closeSoftKeyboard());
    }

    private void assertClipboard(ActivityScenario<MainActivity> scenario, String expected) {
        scenario.onActivity(activity -> {
            ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
            assertNotNull(clipboard);
            assertNotNull(clipboard.getPrimaryClip());
            assertEquals(expected, clipboard.getPrimaryClip().getItemAt(0).coerceToText(activity).toString());
        });
    }

    @Test public void wizardRestoresDraftAndRemovesCopiedCommandsInOrder() {
        final String reason = "Неуважение к администрации";
        final String first = "/offban Ivan_Ivanov 0 " + reason;
        final String second = "/offban Petr_Petrov 0 " + reason;
        final String third = "/offban Aster_Noir 0 Игнорирование требований";
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.add_group)).perform(click());
            input(reason);
            onView(withText("Далее")).inRoot(isDialog()).perform(click());
            input("Ivan_Ivanov");
            onView(withText("Добавить ещё")).inRoot(isDialog()).perform(click());
            onView(withId(R.id.entry_input)).inRoot(isDialog()).check(matches(withText("")));
            input("ivan_ivanov");
            onView(withText("Добавить ещё")).inRoot(isDialog()).perform(click());
            onView(withId(R.id.entry_input)).inRoot(isDialog()).check(matches(hasErrorText("Этот ник уже добавлен")));
            input("Petr_Petrov");
            scenario.recreate();
            onView(withId(R.id.entry_input)).inRoot(isDialog()).check(matches(withText("Petr_Petrov")));
            Espresso.closeSoftKeyboard();
            onView(withText("Готово")).inRoot(isDialog()).perform(click());
            onView(withId(R.id.command_count)).check(matches(withText("Команд в списке: 2")));
            onData(anything()).inAdapterView(withId(R.id.command_list)).atPosition(1)
                .onChildView(withId(R.id.command)).check(matches(withText(second)));

            onView(withId(R.id.add_group)).perform(click());
            input("Игнорирование требований");
            onView(withText("Далее")).inRoot(isDialog()).perform(click());
            input("Aster_Noir");
            onView(withText("Готово")).inRoot(isDialog()).perform(click());
            scenario.recreate();
            onView(withId(R.id.command_count)).check(matches(withText("Команд в списке: 3")));
            onData(anything()).inAdapterView(withId(R.id.command_list)).atPosition(0)
                .onChildView(withContentDescription("Копировать команду для Ivan_Ivanov")).perform(click());
            assertClipboard(scenario, first);
            onView(withId(R.id.command_count)).check(matches(withText("Команд в списке: 2")));
            onView(withText(first)).check(doesNotExist());
            onData(anything()).inAdapterView(withId(R.id.command_list)).atPosition(0)
                .onChildView(withId(R.id.command)).check(matches(withText(second)));
            onData(anything()).inAdapterView(withId(R.id.command_list)).atPosition(0)
                .onChildView(withText("1.  Petr_Petrov")).check(matches(isDisplayed()));
            onData(anything()).inAdapterView(withId(R.id.command_list)).atPosition(1)
                .onChildView(withText("2.  Aster_Noir")).check(matches(isDisplayed()));
        }
        try (ActivityScenario<MainActivity> reopened = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.command_count)).check(matches(withText("Команд в списке: 2")));
            onData(anything()).inAdapterView(withId(R.id.command_list)).atPosition(1)
                .onChildView(withId(R.id.command)).check(matches(withText(third)));
            onData(anything()).inAdapterView(withId(R.id.command_list)).atPosition(0)
                .onChildView(withContentDescription("Копировать команду для Petr_Petrov")).perform(click());
            assertClipboard(reopened, second);
            onView(withId(R.id.command_count)).check(matches(withText("Команд в списке: 1")));
            onData(anything()).inAdapterView(withId(R.id.command_list)).atPosition(0)
                .onChildView(withId(R.id.command)).check(matches(withText(third)));
            onData(anything()).inAdapterView(withId(R.id.command_list)).atPosition(0)
                .onChildView(withText("1.  Aster_Noir")).check(matches(isDisplayed()));
            onData(anything()).inAdapterView(withId(R.id.command_list)).atPosition(0)
                .onChildView(withContentDescription("Копировать команду для Aster_Noir")).perform(click());
            assertClipboard(reopened, third);
            onView(withId(R.id.command_count)).check(matches(withText("Команд в списке: 0")));
            onView(withId(R.id.command_list)).check(matches(withEffectiveVisibility(Visibility.GONE)));
            reopened.recreate();
            onView(withId(R.id.command_count)).check(matches(withText("Команд в списке: 0")));
        }
    }

    @Test public void emptyFieldsAndMultiLineNickAreRejected() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.add_group)).perform(click());
            input("   ");
            onView(withText("Далее")).inRoot(isDialog()).perform(click());
            onView(withId(R.id.entry_input)).inRoot(isDialog()).check(matches(hasErrorText("Введите причину")));
            input("Причина");
            onView(withText("Далее")).inRoot(isDialog()).perform(click());
            Espresso.closeSoftKeyboard();
            onView(withText("Готово")).inRoot(isDialog()).perform(click());
            onView(withId(R.id.entry_input)).inRoot(isDialog()).check(matches(hasErrorText("Добавьте хотя бы один ник")));
            input("Ivan\nPetr\nAster");
            onView(withText("Добавить ещё")).inRoot(isDialog()).perform(click());
            onView(withId(R.id.entry_input)).inRoot(isDialog()).check(matches(hasErrorText("Введите один ник без пробелов и слешей")));
            input("Ivan_Ivanov");
            onView(withText("Добавить ещё")).inRoot(isDialog()).perform(click());
            onView(withText("Готово")).inRoot(isDialog()).perform(click());
            onView(withId(R.id.command_count)).check(matches(withText("Команд в списке: 1")));
        }
    }
}
