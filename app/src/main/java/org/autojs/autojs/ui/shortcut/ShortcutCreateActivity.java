package org.autojs.autojs.ui.shortcut;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import com.afollestad.materialdialogs.MaterialDialog;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import org.autojs.autojs.external.ScriptIntents;
import org.autojs.autojs.external.shortcut.Shortcut;
import org.autojs.autojs.external.shortcut.ShortcutActivity;
import org.autojs.autojs.model.script.ScriptFile;
import org.autojs.autojs.util.BitmapUtils;
import org.autojs.autojs.util.ShortcutUtils;
import org.autojs.autojs6.R;
import org.autojs.autojs6.databinding.ShortcutCreateDialogBinding;

/**
 * Created by Stardust on Oct 25, 2017.
 */
public class ShortcutCreateActivity extends AppCompatActivity {

    public static final String EXTRA_FILE = "file";
    private static final String LOG_TAG = "ShortcutCreateActivity";
    private ScriptFile mScriptFile;
    private boolean mIsDefaultIcon = true;

    TextView mName;
    ImageView mIcon;
    CheckBox mUseAndroidNShortcut;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        View view = View.inflate(this, R.layout.shortcut_create_dialog, null);
        ShortcutCreateDialogBinding binding = ShortcutCreateDialogBinding.bind(view);

        mName = binding.name;
        mIcon = binding.icon;
        mUseAndroidNShortcut = binding.useAndroidNShortcut;

        mIcon.setOnClickListener(v -> AppsIconSelectActivity.launchForResult(this, 21209));

        mScriptFile = (ScriptFile) getIntent().getSerializableExtra(EXTRA_FILE);
        showDialog(view);
    }

    private void showDialog(View view) {
        mUseAndroidNShortcut.setVisibility(Build.VERSION.SDK_INT == Build.VERSION_CODES.N_MR1
                ? View.VISIBLE : View.GONE);
        mName.setText(mScriptFile.getSimplifiedName());
        new MaterialDialog.Builder(this)
                .customView(view, false)
                .title(R.string.text_send_shortcut)
                .positiveText(R.string.text_ok)
                .onPositive((dialog, which) -> {
                    createShortcut();
                    finish();
                })
                .cancelListener(dialog -> finish())
                .show();
    }

    private void createShortcut() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ||
            Build.VERSION.SDK_INT == Build.VERSION_CODES.N_MR1 && mUseAndroidNShortcut.isChecked()
        ) {
            createShortcutByShortcutManager();
            return;
        }
        Shortcut shortcut = new Shortcut(this);
        if (mIsDefaultIcon) {
            shortcut.iconRes(R.drawable.ic_node_js_black);
        } else {
            Bitmap bitmap = BitmapUtils.drawableToBitmap(mIcon.getDrawable());
            shortcut.icon(bitmap);
        }
        shortcut.name(mName.getText().toString())
                .targetClass(ShortcutActivity.class)
                .extras(new Intent().putExtra(ScriptIntents.EXTRA_KEY_PATH, mScriptFile.getPath()))
                .send();
    }

    @RequiresApi(api = Build.VERSION_CODES.N_MR1)
    private void createShortcutByShortcutManager() {
        CharSequence name = mName.getText();
        /* To make each script be able to have its own individual icon. */
        String id = ShortcutActivity.class.getName() + "$" + name + "@" + System.currentTimeMillis();
        Intent intent = new Intent(this, ShortcutActivity.class)
                .putExtra(ScriptIntents.EXTRA_KEY_PATH, mScriptFile.getPath())
                .setAction(Intent.ACTION_MAIN);
        if (mIsDefaultIcon) {
            Icon icon = Icon.createWithResource(this, R.drawable.ic_file_type_js_dark_green);
            ShortcutUtils.requestPinShortcut(this, id, intent, name, name, icon);
        } else {
            Bitmap bitmap = BitmapUtils.drawableToBitmap(mIcon.getDrawable());
            ShortcutUtils.requestPinShortcut(this, id, intent, name, name, bitmap);
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @SuppressLint({"CheckResult", "MissingSuperCall"})
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != RESULT_OK) {
            return;
        }
        String packageName = data.getStringExtra(AppsIconSelectActivity.EXTRA_PACKAGE_NAME);
        if (packageName != null) {
            try {
                mIcon.setImageDrawable(getPackageManager().getApplicationIcon(packageName));
                mIsDefaultIcon = false;
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
            return;
        }
        Uri uri = data.getData();
        if (uri == null) {
            return;
        }
        Observable.fromCallable(() -> BitmapFactory.decodeStream(getContentResolver().openInputStream(uri)))
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((bitmap -> {
                    mIcon.setImageBitmap(bitmap);
                    mIsDefaultIcon = false;
                }), error -> Log.e(LOG_TAG, "decode stream", error));

    }
}
