package app.revanced.integrations.music.settings.preference;

import static app.revanced.integrations.music.settings.Settings.CHANGE_START_PAGE;
import static app.revanced.integrations.music.settings.Settings.CUSTOM_FILTER_STRINGS;
import static app.revanced.integrations.music.settings.Settings.CUSTOM_PLAYBACK_SPEEDS;
import static app.revanced.integrations.music.settings.Settings.EXTERNAL_DOWNLOADER_PACKAGE_NAME;
import static app.revanced.integrations.music.settings.Settings.HIDE_ACCOUNT_MENU_FILTER_STRINGS;
import static app.revanced.integrations.music.settings.Settings.OPTIONAL_SPONSOR_BLOCK_SETTINGS_PREFIX;
import static app.revanced.integrations.music.settings.Settings.SB_API_URL;
import static app.revanced.integrations.music.settings.Settings.SETTINGS_IMPORT_EXPORT;
import static app.revanced.integrations.music.settings.Settings.SPOOF_APP_VERSION_TARGET;
import static app.revanced.integrations.music.utils.ExtendedUtils.getDialogBuilder;
import static app.revanced.integrations.music.utils.ExtendedUtils.getLayoutParams;
import static app.revanced.integrations.music.utils.RestartUtils.showRestartDialog;
import static app.revanced.integrations.shared.settings.Setting.getSettingFromPath;
import static app.revanced.integrations.shared.utils.ResourceUtils.getStringArray;
import static app.revanced.integrations.shared.utils.StringRef.str;
import static app.revanced.integrations.shared.utils.Utils.showToastShort;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.icu.text.SimpleDateFormat;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.text.InputType;
import android.util.TypedValue;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import com.google.android.material.textfield.TextInputLayout;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;
import java.util.Objects;

import app.revanced.integrations.music.patches.utils.ReturnYouTubeDislikePatch;
import app.revanced.integrations.music.returnyoutubedislike.ReturnYouTubeDislike;
import app.revanced.integrations.music.settings.ActivityHook;
import app.revanced.integrations.music.settings.Settings;
import app.revanced.integrations.music.utils.ExtendedUtils;
import app.revanced.integrations.shared.settings.BooleanSetting;
import app.revanced.integrations.shared.settings.Setting;
import app.revanced.integrations.shared.utils.Logger;
import app.revanced.integrations.shared.utils.Utils;

/**
 * @noinspection ALL
 */
public class ReVancedPreferenceFragment extends PreferenceFragment {

    private static final String IMPORT_EXPORT_SETTINGS_ENTRY_KEY = "revanced_extended_settings_import_export_entries";
    private static final int READ_REQUEST_CODE = 42;
    private static final int WRITE_REQUEST_CODE = 43;

    private static String existingSettings;


    public ReVancedPreferenceFragment() {
    }

    /**
     * Injection point.
     */
    public static void onPreferenceChanged(@Nullable String key, boolean newValue) {
        if (key == null || key.isEmpty())
            return;

        for (Setting<?> setting : Setting.allLoadedSettings()) {
            if (setting.equals(Settings.RESTORE_OLD_PLAYER_LAYOUT)) {
                Settings.RESTORE_OLD_PLAYER_BACKGROUND.save(newValue);
            } else if (setting.equals(Settings.RESTORE_OLD_PLAYER_BACKGROUND) && !newValue) {
                Settings.RESTORE_OLD_PLAYER_LAYOUT.save(newValue);
            } else if (setting.equals(Settings.RYD_ENABLED)) {
                ReturnYouTubeDislikePatch.onRYDStatusChange(newValue);
            } else if (setting.equals(Settings.RYD_DISLIKE_PERCENTAGE) || setting.equals(Settings.RYD_COMPACT_LAYOUT)) {
                ReturnYouTubeDislike.clearAllUICaches();
            }

            if (key.equals(setting.key)) {
                ((BooleanSetting) setting).save(newValue);
                if (setting.rebootApp) {
                    showRebootDialog();
                }
                break;
            }
        }
    }

    public static void showRebootDialog() {
        final Activity activity = ActivityHook.getActivity();
        if (activity == null)
            return;

        showRestartDialog(activity);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            final Activity baseActivity = this.getActivity();
            final Activity mActivity = ActivityHook.getActivity();
            final Intent savedInstanceStateIntent = baseActivity.getIntent();
            if (savedInstanceStateIntent == null)
                return;

            final String dataString = savedInstanceStateIntent.getDataString();
            if (dataString == null || dataString.isEmpty())
                return;

            if (dataString.startsWith(OPTIONAL_SPONSOR_BLOCK_SETTINGS_PREFIX)) {
                SponsorBlockCategoryPreference.showDialog(baseActivity, dataString.replaceAll(OPTIONAL_SPONSOR_BLOCK_SETTINGS_PREFIX, ""));
                return;
            }

            final Setting<?> settings = getSettingFromPath(dataString);
            final Setting<String> setting = (Setting<String>) settings;

            if (settings.equals(CHANGE_START_PAGE)) {
                ResettableListPreference.showDialog(mActivity, setting, 2);
            } else if (settings.equals(CUSTOM_FILTER_STRINGS)
                    || settings.equals(HIDE_ACCOUNT_MENU_FILTER_STRINGS)
                    || settings.equals(CUSTOM_PLAYBACK_SPEEDS)) {
                ResettableEditTextPreference.showDialog(mActivity, setting);
            } else if (settings.equals(EXTERNAL_DOWNLOADER_PACKAGE_NAME)) {
                ExternalDownloaderPreference.showDialog(mActivity);
            } else if (settings.equals(SB_API_URL)) {
                SponsorBlockApiUrlPreference.showDialog(mActivity);
            } else if (settings.equals(SETTINGS_IMPORT_EXPORT)) {
                importExportListDialogBuilder();
            } else if (settings.equals(SPOOF_APP_VERSION_TARGET)) {
                ResettableListPreference.showDialog(mActivity, setting, 1);
            } else {
                Logger.printDebug(() -> "Failed to find the right value: " + dataString);
            }
        } catch (Exception ex) {
            Logger.printException(() -> "onCreate failure", ex);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    /**
     * Build a ListDialog for Import / Export settings
     * When importing/exporting as file, {@link #onActivityResult} is used, so declare it here.
     */
    private void importExportListDialogBuilder() {
        try {
            final Activity activity = getActivity();
            final String[] mEntries = getStringArray(IMPORT_EXPORT_SETTINGS_ENTRY_KEY);

            getDialogBuilder(activity)
                    .setTitle(str("revanced_extended_settings_import_export_title"))
                    .setItems(mEntries, (dialog, index) -> {
                        switch (index) {
                            case 0 -> exportActivity();
                            case 1 -> importActivity();
                            case 2 -> importExportEditTextDialogBuilder();
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        } catch (Exception ex) {
            Logger.printException(() -> "importExportListDialogBuilder failure", ex);
        }
    }

    /**
     * Build a EditTextDialog for Import / Export settings
     */
    private void importExportEditTextDialogBuilder() {
        try {
            final Activity activity = getActivity();
            final EditText textView = new EditText(activity);
            existingSettings = Setting.exportToJson(null);
            textView.setText(existingSettings);
            textView.setInputType(textView.getInputType() | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_PT, 8); // Use a smaller font to reduce text wrap.

            TextInputLayout textInputLayout = new TextInputLayout(activity);
            textInputLayout.setLayoutParams(getLayoutParams());
            textInputLayout.addView(textView);

            FrameLayout container = new FrameLayout(activity);
            container.addView(textInputLayout);

            getDialogBuilder(activity)
                    .setTitle(str("revanced_extended_settings_import_export_title"))
                    .setView(container)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setNeutralButton(str("revanced_extended_settings_import_copy"), (dialog, which) -> Utils.setClipboard(textView.getText().toString(), str("revanced_share_copy_settings_success")))
                    .setPositiveButton(str("revanced_extended_settings_import"), (dialog, which) -> importSettings(textView.getText().toString()))
                    .show();
        } catch (Exception ex) {
            Logger.printException(() -> "importExportEditTextDialogBuilder failure", ex);
        }
    }

    /**
     * Invoke the SAF(Storage Access Framework) to export settings
     */
    private void exportActivity() {
        @SuppressLint("SimpleDateFormat")
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

        var appName = ExtendedUtils.getApplicationLabel();
        var versionName = ExtendedUtils.getVersionName();
        var formatDate = dateFormat.format(new Date(System.currentTimeMillis()));
        var fileName = String.format("%s_v%s_%s.txt", appName, versionName, formatDate);

        var intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, fileName);
        startActivityForResult(intent, WRITE_REQUEST_CODE);
    }

    /**
     * Invoke the SAF(Storage Access Framework) to import settings
     */
    private void importActivity() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(Build.VERSION.SDK_INT <= 28 ? "*/*" : "text/plain");
        startActivityForResult(intent, READ_REQUEST_CODE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == WRITE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            exportText(data.getData());
        } else if (requestCode == READ_REQUEST_CODE && resultCode == Activity.RESULT_OK && data != null) {
            importText(data.getData());
        }
    }

    private void exportText(Uri uri) {
        try {
            final Context context = this.getContext();

            @SuppressLint("Recycle")
            FileWriter jsonFileWriter =
                    new FileWriter(
                            Objects.requireNonNull(context.getApplicationContext()
                                            .getContentResolver()
                                            .openFileDescriptor(uri, "w"))
                                    .getFileDescriptor()
                    );
            PrintWriter printWriter = new PrintWriter(jsonFileWriter);
            printWriter.write(Setting.exportToJson(null));
            printWriter.close();
            jsonFileWriter.close();

            showToastShort(str("revanced_extended_settings_export_success"));
        } catch (IOException e) {
            showToastShort(str("revanced_extended_settings_export_failed"));
        }
    }

    private void importText(Uri uri) {
        final Context context = this.getContext();
        StringBuilder sb = new StringBuilder();
        String line;

        try {
            @SuppressLint("Recycle")
            FileReader fileReader =
                    new FileReader(
                            Objects.requireNonNull(context.getApplicationContext()
                                            .getContentResolver()
                                            .openFileDescriptor(uri, "r"))
                                    .getFileDescriptor()
                    );
            BufferedReader bufferedReader = new BufferedReader(fileReader);
            while ((line = bufferedReader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            bufferedReader.close();
            fileReader.close();

            final boolean restartNeeded = Setting.importFromJSON(sb.toString(), false);
            if (restartNeeded) {
                ReVancedPreferenceFragment.showRebootDialog();
            }
        } catch (IOException e) {
            showToastShort(str("revanced_extended_settings_import_failed"));
            throw new RuntimeException(e);
        }
    }

    private void importSettings(String replacementSettings) {
        try {
            existingSettings = Setting.exportToJson(null);
            if (replacementSettings.equals(existingSettings)) {
                return;
            }
            final boolean restartNeeded = Setting.importFromJSON(replacementSettings, false);
            if (restartNeeded) {
                ReVancedPreferenceFragment.showRebootDialog();
            }
        } catch (Exception ex) {
            Logger.printException(() -> "importSettings failure", ex);
        }
    }
}