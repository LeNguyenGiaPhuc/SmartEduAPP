package hcmute.com.smarteduapp.ui.main;

import android.app.Activity;
import android.view.View;

import hcmute.com.smarteduapp.R;

/**
 * Handles the home sidebar animation.
 * MainActivity only decides where each menu button navigates.
 */
public class HomeMenuController {
    private final Activity activity;

    public HomeMenuController(Activity activity) {
        this.activity = activity;
    }

    public void show() {
        View backdrop = activity.findViewById(R.id.homeMenuBackdrop);
        View menu = activity.findViewById(R.id.homeSideMenu);
        if (backdrop == null || menu == null) {
            return;
        }

        backdrop.setAlpha(0f);
        backdrop.setVisibility(View.VISIBLE);
        menu.setVisibility(View.VISIBLE);
        menu.post(() -> {
            menu.setTranslationX(-menu.getWidth());
            menu.animate().translationX(0f).setDuration(220).start();
            backdrop.animate().alpha(1f).setDuration(180).start();
        });
    }

    public void hide() {
        View backdrop = activity.findViewById(R.id.homeMenuBackdrop);
        View menu = activity.findViewById(R.id.homeSideMenu);
        if (backdrop == null || menu == null || menu.getVisibility() != View.VISIBLE) {
            return;
        }

        menu.animate()
                .translationX(-menu.getWidth())
                .setDuration(180)
                .withEndAction(() -> {
                    menu.setVisibility(View.GONE);
                    menu.setTranslationX(0f);
                })
                .start();

        backdrop.animate()
                .alpha(0f)
                .setDuration(160)
                .withEndAction(() -> backdrop.setVisibility(View.GONE))
                .start();
    }
}
