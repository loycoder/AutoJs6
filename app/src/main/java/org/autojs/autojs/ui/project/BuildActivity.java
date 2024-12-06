package org.autojs.autojs.ui.project;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.util.Linkify;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.afollestad.materialdialogs.MaterialDialog;
import com.google.android.flexbox.FlexboxLayout;
import com.google.android.material.textfield.TextInputLayout;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import org.autojs.autojs.apkbuilder.ApkBuilder;
import org.autojs.autojs.core.pref.Language;
import org.autojs.autojs.model.explorer.Explorers;
import org.autojs.autojs.model.script.ScriptFile;
import org.autojs.autojs.project.ProjectConfig;
import org.autojs.autojs.runtime.ScriptRuntime;
import org.autojs.autojs.runtime.api.AppUtils;
import org.autojs.autojs.runtime.api.AppUtils.Companion.SimpleVersionInfo;
import org.autojs.autojs.ui.BaseActivity;
import org.autojs.autojs.ui.common.NotAskAgainDialog;
import org.autojs.autojs.ui.filechooser.FileChooserDialogBuilder;
import org.autojs.autojs.ui.shortcut.AppsIconSelectActivity;
import org.autojs.autojs.ui.widget.RoundCheckboxWithText;
import org.autojs.autojs.util.AndroidUtils;
import org.autojs.autojs.util.AndroidUtils.Abi;
import org.autojs.autojs.util.BitmapUtils;
import org.autojs.autojs.util.EnvironmentUtils;
import org.autojs.autojs.util.IntentUtils;
import org.autojs.autojs.util.ViewUtils;
import org.autojs.autojs.util.WorkingDirectoryUtils;
import org.autojs.autojs6.R;
import org.autojs.autojs6.databinding.ActivityBuildBinding;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.autojs.autojs.apkbuilder.ApkBuilder.TEMPLATE_APK_NAME;
import static org.autojs.autojs.util.StringUtils.key;

/**
 * Created by Stardust on Oct 22, 2017.
 * Modified by SuperMonster003 as of Dec 1, 2023.
 */
public class BuildActivity extends BaseActivity implements ApkBuilder.ProgressCallback {

    private static final int REQUEST_CODE = 44401;
    public static final String EXTRA_SOURCE = BuildActivity.class.getName() + ".extra_source_file";
    private static final String LOG_TAG = "BuildActivity";
    private static final Pattern REGEX_PACKAGE_NAME = Pattern.compile("^([A-Za-z][A-Za-z\\d_]*\\.)+([A-Za-z][A-Za-z\\d_]*)$");

    private static final ArrayList<String> SUPPORTED_ABIS = new ArrayList<>() {{
        add(Abi.ARM64_V8A);
        add(Abi.X86_64);
        add(Abi.ARMEABI_V7A);
        add(Abi.X86);
        add(Abi.ARMEABI);
    }};

    private static final Map<String, List<String>> ABI_ALIASES = new HashMap<>() {{
        put(Abi.ARM64_V8A, /* "arm64-v8a" */ List.of("arm64_v8a", "arm64_v8", "arm64_8", "arm_v8a", "v8a", "arm_v8", "v8", "arm_8", "8", "arm64-v8a", "arm64-v8", "arm64-8", "arm-v8a", "arm-v8", "arm-8", "arm64v8a", "64v8a", "arm64v8", "64v8", "arm648", "armv8a", "armv8", "arm8", "a64_v8a", "a64_v8", "a64_8", "a_v8a", "a_v8", "a_8", "a64-v8a", "a64-v8", "a64-8", "a-v8a", "a-v8", "a-8", "a64v8a", "a64v8", "a648", "av8a", "av8", "a8"));
        put(Abi.X86_64, /* x86_64 */ List.of("x86_64", "x8664", "86_64", "8664"));
        put(Abi.ARMEABI_V7A, /* armeabi-v7a */ List.of("armeabi_v7a", "armeabi_v7", "armeabi_7", "arme_v7a", "arme_v7", "arme_7", "arm_v7a", "v7a", "arm_v7", "v7", "arm_7", "7", "armeabi-v7a", "armeabi-v7", "armeabi-7", "arme-v7a", "arme-v7", "arme-7", "arm-v7a", "arm-v7", "arm-7", "armeabiv7a", "armeabiv7", "armeabi7", "armev7a", "armev7", "arme7", "armv7a", "armv7", "arm7", "a_v7a", "a_v7", "a_7", "a-v7a", "a-v7", "a-7", "av7a", "av7", "a7"));
        put(Abi.X86, /* x86 */ List.of("x86", "86"));
        put(Abi.ARMEABI, /* armeabi */ List.of("armeabi", "arme", "armv5te", "arm5te", "armv5", "v5", "arm5", "5"));
    }};

    private static final ArrayList<String> SUPPORTED_LIBS = new ArrayList<>() {{
        add(ApkBuilder.Constants.OPENCV);
        add(ApkBuilder.Constants.MLKIT_OCR);
        add(ApkBuilder.Constants.PADDLE_OCR);
        add(ApkBuilder.Constants.RAPID_OCR);
        add(ApkBuilder.Constants.OPENCC);
        add(ApkBuilder.Constants.MLKIT_BARCODE);
    }};

    private static final Map<String, List<String>> LIB_ALIASES = new HashMap<>() {{
        put(ApkBuilder.Constants.OPENCV, /* OpenCV */ List.of("cv"));
        put(ApkBuilder.Constants.MLKIT_OCR, /* MLKit OCR */ List.of("mlkit", "mlkitocr", "mlkit-ocr", "mlkit_ocr"));
        put(ApkBuilder.Constants.PADDLE_OCR, /* Paddle OCR */ List.of("paddle", "paddleocr", "paddle-ocr", "paddle_ocr"));
        put(ApkBuilder.Constants.RAPID_OCR, /* Rapid OCR */ List.of("rapid", "rapidocr", "rapid-ocr", "rapid_ocr"));
        put(ApkBuilder.Constants.OPENCC, /* OpenCC */ List.of("cc"));
        put(ApkBuilder.Constants.MLKIT_BARCODE, /* MLKit Barcode */ List.of("barcode", "mlkit-barcode", "mlkit_barcode"));
    }};

    EditText mSourcePath;
    View mSourcePathContainer;
    EditText mOutputPath;
    EditText mAppName;
    EditText mPackageName;
    EditText mVersionName;
    EditText mVersionCode;
    ImageView mIcon;
    LinearLayout mAppConfig;

    private ProjectConfig mProjectConfig;
    private MaterialDialog mProgressDialog;
    private String mSource;
    private boolean mIsDefaultIcon = true;
    private boolean mIsProjectLevelBuilding;
    private FlexboxLayout mFlexboxAbis;
    private FlexboxLayout mFlexboxLibs;

    private final ArrayList<String> mInvalidAbis = new ArrayList<>();
    private final ArrayList<String> mUnavailableAbis = new ArrayList<>();
    private final ArrayList<String> mUnavailableStandardAbis = new ArrayList<>();
    private final ArrayList<String> mInvalidLibs = new ArrayList<>();
    private final ArrayList<String> mUnavailableLibs = new ArrayList<>();

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActivityBuildBinding binding = ActivityBuildBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mSourcePath = binding.sourcePath;
        mSourcePath.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_ENTER) {
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    mOutputPath.requestFocus();
                }
                return true;
            }
            return false;
        });

        mSourcePathContainer = binding.sourcePathContainer;

        mOutputPath = binding.outputPath;
        mOutputPath.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_ENTER) {
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    TextView nextField = (TextView) mOutputPath.focusSearch(View.FOCUS_DOWN);
                    if (nextField != null) {
                        nextField.requestFocus();
                    }
                }
                return true;
            }
            return false;
        });

        mAppName = binding.appName;

        mPackageName = binding.packageName;
        mPackageName.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_ENTER) {
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    mVersionName.requestFocus();
                }
                return true;
            }
            return false;
        });

        mVersionName = binding.versionName;
        mVersionCode = binding.versionCode;

        mIcon = binding.appIcon;
        mIcon.setOnClickListener(v -> selectIcon());

        mAppConfig = binding.appConfig;

        mFlexboxAbis = binding.flexboxAbis;
        initAbisChildren();

        mFlexboxLibs = binding.flexboxLibraries;
        initLibsChildren();

        binding.fab.setOnClickListener(v -> buildApk());
        binding.selectSource.setOnClickListener(v -> selectSourceFilePath());
        binding.selectOutput.setOnClickListener(v -> selectOutputDirPath());
        binding.textAbis.setOnClickListener(v -> toggleAllFlexboxChildren(mFlexboxAbis));
        binding.textAbis.setOnLongClickListener(v -> {
            syncAbisCheckedStates();
            return true;
        });
        binding.textLibs.setOnClickListener(v -> toggleAllFlexboxChildren(mFlexboxLibs));

        setToolbarAsBack(R.string.text_build_apk);
        mSource = getIntent().getStringExtra(EXTRA_SOURCE);
        if (mSource != null) {
            setupWithSourceFile(new ScriptFile(mSource));
        }

        syncAbisCheckedStates();
        syncLibsCheckedStates();

        showHintDialogIfNeeded();
    }

    private void toggleAllFlexboxChildren(FlexboxLayout mFlexboxLibs) {
        boolean isAllChecked = true;
        for (int i = 0; i < mFlexboxLibs.getChildCount(); i += 1) {
            View child = mFlexboxLibs.getChildAt(i);
            if (child instanceof RoundCheckboxWithText) {
                if (!child.isEnabled()) {
                    continue;
                }
                if (!((RoundCheckboxWithText) child).isChecked()) {
                    isAllChecked = false;
                    break;
                }
            }
        }
        for (int i = 0; i < mFlexboxLibs.getChildCount(); i += 1) {
            View child = mFlexboxLibs.getChildAt(i);
            if (child instanceof RoundCheckboxWithText) {
                if (!child.isEnabled()) {
                    continue;
                }
                ((RoundCheckboxWithText) child).setChecked(!isAllChecked);
            }
        }
    }

    private void initAbisChildren() {
        SUPPORTED_ABIS.forEach((abiText) -> {
            RoundCheckboxWithText child = new RoundCheckboxWithText(this, null);
            child.setText(abiText);
            child.setChecked(false);
            child.setEnabled(false);
            child.setOnBeingUnavailableListener(this::promptForUnavailability);
            mFlexboxAbis.addView(child);
        });
    }

    private void promptForUnavailability(RoundCheckboxWithText view) {
        CharSequence abiText = view.getText();
        Context context = BuildActivity.this;
        String key = key(R.string.key_dialog_selected_abi_is_unavailable);
        NotAskAgainDialog.Builder builder = new NotAskAgainDialog.Builder(context, key);
        builder.title(R.string.text_prompt);
        builder.content(getString(R.string.text_unable_to_build_apk_as_autojs6_does_not_include_selected_abi, abiText) + "\n\n" +
                getString(R.string.text_the_following_solutions_can_be_referred_to) + ":\n\n" +
                "- " + getString(R.string.text_download_and_install_autojs6_including_above_abi, abiText) + "\n" +
                "- " + getString(R.string.text_download_and_install_autojs6_including_all_abis) + " [" + getString(R.string.text_recommended) + "]\n\n" +
                getString(R.string.text_download_link_for_autojs6) + ":\n" +
                getString(R.string.uri_autojs6_download_link));
        builder.positiveText(R.string.dialog_button_dismiss);
        MaterialDialog dialog = builder.show();
        if (dialog != null) {
            TextView contentView = dialog.getContentView();
            if (contentView != null) {
                Linkify.addLinks(contentView, Pattern.compile("https?://.*"), null);
            }
        } else {
            ViewUtils.showToast(context, getString(R.string.text_unavailable_abi_for, abiText));
        }
    }

    private void syncAbisCheckedStates() {
        if (mProjectConfig != null) {
            List<String> projectConfigAbis = mProjectConfig.getAbis();
            if (!projectConfigAbis.isEmpty()) {
                var candidates = new ArrayList<>(projectConfigAbis);
                syncAbisWithDefaultCheckedFilter(standardAbi -> isAliasMatching(ABI_ALIASES, standardAbi, candidates));
                mInvalidAbis.addAll(candidates);
                return;
            }
        }
        syncAbisWithDefaultCheckedFilter(standardAbi -> AndroidUtils.getDeviceFilteredAbiList().contains(standardAbi));
    }

    private void syncAbisWithDefaultCheckedFilter(Function<String, Boolean> filterForDefaultChecked) {
        List<String> appSupportedAbiList = AndroidUtils.getAppSupportedAbiList();
        for (int i = 0; i < mFlexboxAbis.getChildCount(); i += 1) {
            View child = mFlexboxAbis.getChildAt(i);
            if (child instanceof RoundCheckboxWithText) {
                CharSequence standardAbi = ((RoundCheckboxWithText) child).getText();
                if (standardAbi != null) {
                    boolean isEnabled = appSupportedAbiList.contains(standardAbi.toString());
                    boolean isDefaultChecked = filterForDefaultChecked.apply(standardAbi.toString());
                    child.setEnabled(isEnabled);
                    ((RoundCheckboxWithText) child).setChecked(isEnabled && isDefaultChecked);
                    if (isDefaultChecked && !isEnabled) {
                        mUnavailableStandardAbis.add(standardAbi.toString());
                    }
                }
            }
        }
    }

    private void initLibsChildren() {
        SUPPORTED_LIBS.forEach((text) -> {
            RoundCheckboxWithText child = new RoundCheckboxWithText(this, null);
            child.setText(text);
            child.setChecked(false);
            mFlexboxLibs.addView(child);
        });
    }

    private void syncLibsCheckedStates() {
        if (mProjectConfig == null) return;

        var configLibs = mProjectConfig.getLibs();
        if (configLibs.isEmpty()) return;

        // 创建一个新的副本
        var candidates = new ArrayList<>(configLibs);

        for (int i = 0; i < mFlexboxLibs.getChildCount(); i += 1) {
            View child = mFlexboxLibs.getChildAt(i);
            if (child instanceof RoundCheckboxWithText) {
                CharSequence standardLib = ((RoundCheckboxWithText) child).getText();
                if (standardLib != null) {
                    boolean shouldChecked = isAliasMatching(LIB_ALIASES, standardLib.toString(), candidates);
                    ((RoundCheckboxWithText) child).setChecked(shouldChecked);
                }
            }
        }

        mInvalidLibs.addAll(candidates);
    }

    private boolean isAliasMatching(Map<String, List<String>> aliases, String aliasKey, List<String> candidates) {
        AtomicBoolean result = new AtomicBoolean(false);
        var aliasList = aliases.getOrDefault(aliasKey, Collections.emptyList());
        ArrayList<String> aliasesToCheck = new ArrayList<>(aliasList);
        aliasesToCheck.add(aliasKey);
        aliasesToCheck.forEach(alias -> {
            if (containsIgnoreCase(candidates, alias)) {
                candidates.remove(alias);
                result.set(true);
            }
        });
        return result.get();
    }

    private boolean containsIgnoreCase(List<String> list, String s) {
        return list.stream().anyMatch(item -> item.equalsIgnoreCase(s));
    }

    private void setupWithSourceFile(ScriptFile file) {
        String dir = file.getParent();
        if (dir != null && dir.startsWith(getFilesDir().getPath())) {
            dir = WorkingDirectoryUtils.getPath();
        }
        mOutputPath.setText(dir);
        mAppName.setText(file.getSimplifiedName());

        String packageName = getString(R.string.format_default_package_name, generatePackageNameSuffix(file));
        mPackageName.setText(packageName);

        SimpleVersionInfo nextVersionInfo = AppUtils.generateNextVersionInfo(packageName);
        if (nextVersionInfo != null) {
            mVersionName.setText(nextVersionInfo.versionName);
            mVersionCode.setText(nextVersionInfo.versionCodeString);
        }

        Drawable iconDrawable = AppUtils.getInstalledAppIcon(packageName);
        if (iconDrawable != null) {
            mIcon.setImageDrawable(iconDrawable);
            mIsDefaultIcon = false;
        }

        setSource(file);
    }

    private static String generatePackageNameSuffix(ScriptFile file) {
        String name = file.getSimplifiedName()
                .replaceAll("\\W+", "_");
        if (name.matches("^\\d.*")) {
            name = "app_" + name;
        }
        return name.toLowerCase(Language.getPrefLanguage().getLocale());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
    }

    void selectSourceFilePath() {
        String initialDir = new File(mSourcePath.getText().toString()).getParent();
        new FileChooserDialogBuilder(this)
                .title(R.string.text_source_file_path)
                .dir(EnvironmentUtils.getExternalStoragePath(),
                        initialDir == null ? WorkingDirectoryUtils.getPath() : initialDir)
                .singleChoice(this::setSource)
                .show();
    }

    private void setSource(File file) {
        if (!file.isDirectory()) {
            mSourcePath.setText(file.getPath());
            return;
        }
        mProjectConfig = ProjectConfig.fromProjectDir(file.getPath());
        if (mProjectConfig == null) {
            return;
        }
        mIsProjectLevelBuilding = true;
        mOutputPath.setText(new File(mSource, mProjectConfig.getBuildDir()).getPath());
        mAppConfig.setVisibility(View.GONE);
        mSourcePathContainer.setVisibility(View.GONE);
    }

    void selectOutputDirPath() {
        String initialDir = new File(mOutputPath.getText().toString()).exists()
                ? mOutputPath.getText().toString()
                : WorkingDirectoryUtils.getPath();
        new FileChooserDialogBuilder(this)
                .title(R.string.text_output_apk_path)
                .dir(initialDir)
                .chooseDir()
                .singleChoice(dir -> mOutputPath.setText(dir.getPath()))
                .show();
    }

    void selectIcon() {
        AppsIconSelectActivity.launchForResult(this, REQUEST_CODE);
    }

    void buildApk() {
        if (!checkInputs()) {
            ViewUtils.showToast(this, getString(R.string.error_input_fields_check_failed));
            return;
        }
        if (!checkAbis()) {
            ViewUtils.showToast(this, getString(R.string.error_at_least_one_abi_needs_to_be_selected));
            return;
        }
        doBuildingApk();
    }

    private boolean checkInputs() {
        if (mIsProjectLevelBuilding) {
            return checkNotEmpty(mOutputPath);
        }
        return checkNotEmpty(mSourcePath)
                & checkNotEmpty(mOutputPath)
                & checkNotEmpty(mAppName)
                & checkNotEmpty(mVersionCode)
                & checkNotEmpty(mVersionName)
                & checkPackageNameValid(mPackageName);
    }

    private boolean checkAbis() {
        for (int i = 0; i < mFlexboxAbis.getChildCount(); i += 1) {
            View child = mFlexboxAbis.getChildAt(i);
            if (child instanceof RoundCheckboxWithText) {
                if (((RoundCheckboxWithText) child).isChecked() && child.isEnabled()) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean checkPackageNameValid(EditText editText) {
        Editable text = editText.getText();
        String hint = Objects.requireNonNull(((TextInputLayout) editText.getParent().getParent()).getHint()).toString();
        if (TextUtils.isEmpty(text)) {
            editText.setError(hint + getString(R.string.text_should_not_be_empty));
            return false;
        }
        if (!REGEX_PACKAGE_NAME.matcher(text).matches()) {
            editText.setError(getString(R.string.text_invalid_package_name));
            return false;
        }
        return true;
    }

    private boolean checkNotEmpty(EditText editText) {
        if (!TextUtils.isEmpty(editText.getText()) || !editText.isShown())
            return true;
        // TODO by Stardust on Dec 8, 2017.
        //  ! More beautiful ways?
        //  ! zh-CN (translated by SuperMonster003 on Jul 29, 2024):
        //  ! 更优雅的方式?
        String hint = Objects.requireNonNull(((TextInputLayout) editText.getParent().getParent()).getHint()).toString();
        editText.setError(hint + getString(R.string.text_should_not_be_empty));
        return false;
    }

    private void showHintDialogIfNeeded() {
        ArrayList<ArrayList<Map<Integer, List<String>>>> info = new ArrayList<>();

        if (!mUnavailableStandardAbis.isEmpty() && mProjectConfig != null) {
            var projectConfigAbis = mProjectConfig.getAbis();
            mUnavailableStandardAbis.forEach(unavailableStandardAbi -> {
                var unavailableAbiAliasList = ABI_ALIASES.getOrDefault(unavailableStandardAbi, Collections.emptyList());
                ArrayList<String> unavailableAbisToCheck = new ArrayList<>(unavailableAbiAliasList);
                unavailableAbisToCheck.add(unavailableStandardAbi);
                projectConfigAbis.forEach(projectConfigAbi -> {
                    if (containsIgnoreCase(unavailableAbisToCheck, projectConfigAbi)) {
                        mUnavailableAbis.add(projectConfigAbi);
                    }
                });
            });
        }

        int splitLineLength = 0;

        if (!mInvalidAbis.isEmpty()) {
            info.add(new ArrayList<>() {{
                add(Map.of(R.string.config_abi_options_contains_invalid, mInvalidAbis));
            }});
        }
        if (!mUnavailableAbis.isEmpty()) {
            info.add(new ArrayList<>() {{
                add(Map.of(R.string.config_abi_options_contains_unavailable, mUnavailableAbis));
                add(Map.of(R.string.current_available_abi_options, AndroidUtils.getAppSupportedAbiList()));
            }});
        }
        if (!mInvalidLibs.isEmpty()) {
            info.add(new ArrayList<>() {{
                add(Map.of(R.string.config_lib_options_contains_invalid, mInvalidLibs));
            }});
        }
        if (!mUnavailableLibs.isEmpty()) {
            info.add(new ArrayList<>() {{
                add(Map.of(R.string.config_lib_options_contains_unavailable, mUnavailableLibs));
            }});
        }

        if (info.isEmpty()) return;

        // 动态计算 splitLineLength, 考虑广范围的双宽字符
        for (ArrayList<Map<Integer, List<String>>> mapsList : info) {
            for (Map<Integer, List<String>> map : mapsList) {
                for (Map.Entry<Integer, List<String>> entry : map.entrySet()) {
                    String text = getString(entry.getKey());
                    int length = 0;
                    for (char c : text.toCharArray()) {
                        length += isWideCharacter(c) ? 2 : 1;
                    }
                    splitLineLength = Math.max(splitLineLength, length);
                }
            }
        }

        final int finalSplitLineLength = splitLineLength;

        // 生成对话框内容
        String content = info.stream()
                .map(mapsList -> mapsList.stream()
                        .map(map -> map.entrySet().stream()
                                .map(entry -> getString(entry.getKey()) + ":\n[ " + String.join(", ", entry.getValue()) + " ]")
                                .collect(Collectors.joining("\n")))
                        .collect(Collectors.joining("\n")))
                .collect(Collectors.joining("\n" + "-".repeat(finalSplitLineLength) + "\n"));

        new MaterialDialog.Builder(this)
                .title(R.string.text_prompt)
                .content(content)
                .positiveText(R.string.dialog_button_dismiss)
                .cancelable(false)
                .show();
    }

    // 判断字符是否是广范围双宽字符
    private boolean isWideCharacter(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
                block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
                block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B ||
                block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS ||
                block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS_SUPPLEMENT ||
                block == Character.UnicodeBlock.HIRAGANA ||
                block == Character.UnicodeBlock.KATAKANA ||
                block == Character.UnicodeBlock.HANGUL_SYLLABLES ||
                block == Character.UnicodeBlock.HANGUL_JAMO ||
                block == Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO ||
                block == Character.UnicodeBlock.ENCLOSED_CJK_LETTERS_AND_MONTHS ||
                block == Character.UnicodeBlock.CJK_COMPATIBILITY_FORMS ||
                block == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @SuppressLint("CheckResult")
    private void doBuildingApk() {
        ApkBuilder.AppConfig appConfig = createAppConfig();
        File tmpDir = new File(getCacheDir(), "build/");
        File outApk = new File(mOutputPath.getText().toString(),
                String.format("%s_v%s.apk", appConfig.getAppName(), appConfig.getVersionName()));
        showProgressDialog();
        Observable.fromCallable(() -> callApkBuilder(tmpDir, outApk, appConfig))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(apkBuilder -> {
                    if (apkBuilder != null) {
                        onBuildSuccessful(outApk);
                    } else {
                        onBuildFailed(new FileNotFoundException(TEMPLATE_APK_NAME));
                    }
                }, this::onBuildFailed);
    }

    private ApkBuilder.AppConfig createAppConfig() {
        ArrayList<String> abis = collectCheckedItems(mFlexboxAbis);
        ArrayList<String> libs = collectCheckedItems(mFlexboxLibs);

        ApkBuilder.AppConfig appConfig = mProjectConfig != null
                ? ApkBuilder.AppConfig.fromProjectConfig(mSource, mProjectConfig)
                : new ApkBuilder.AppConfig()
                .setAppName(mAppName.getText().toString())
                .setSourcePath(mSourcePath.getText().toString())
                .setPackageName(mPackageName.getText().toString())
                .setVersionName(mVersionName.getText().toString())
                .setVersionCode(Integer.parseInt(mVersionCode.getText().toString()))
                .setIcon(mIsDefaultIcon ? null : () -> BitmapUtils.drawableToBitmap(mIcon.getDrawable()));

        appConfig.setAbis(abis);
        appConfig.setLibs(libs);

        return appConfig;
    }

    @NotNull
    private ArrayList<String> collectCheckedItems(FlexboxLayout flexboxLayout) {
        ArrayList<String> libs = new ArrayList<>();

        for (int i = 0; i < flexboxLayout.getChildCount(); i += 1) {
            View child = flexboxLayout.getChildAt(i);
            if (child instanceof RoundCheckboxWithText) {
                if (((RoundCheckboxWithText) child).isChecked()) {
                    CharSequence charSequence = ((RoundCheckboxWithText) child).getText();
                    if (charSequence != null) {
                        libs.add(charSequence.toString());
                    }
                }
            }
        }
        return libs;
    }

    private ApkBuilder callApkBuilder(File tmpDir, File outApk, ApkBuilder.AppConfig appConfig) throws Exception {
        InputStream templateApk = getAssets().open(TEMPLATE_APK_NAME);
        return new ApkBuilder(templateApk, outApk, tmpDir.getPath())
                .setProgressCallback(BuildActivity.this)
                .prepare()
                .withConfig(appConfig)
                .build()
                .sign()
                .cleanWorkspace();
    }

    private void showProgressDialog() {
        mProgressDialog = new MaterialDialog.Builder(this)
                .progress(true, 100)
                .content(R.string.text_in_progress)
                .cancelable(false)
                .show();
    }

    private void onBuildFailed(Throwable error) {
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }
        String message = getString(R.string.text_failed_to_build) + "\n" + error.getMessage();
        ViewUtils.showToast(this, message, true);
        ScriptRuntime.popException(message);
        Log.e(LOG_TAG, "Failed to build", error);
    }

    private void onBuildSuccessful(File outApk) {
        Explorers.workspace().refreshAll();
        mProgressDialog.dismiss();
        mProgressDialog = null;
        new MaterialDialog.Builder(this)
                .title(R.string.text_build_succeeded)
                .content(getString(R.string.format_build_succeeded, outApk.getPath()))
                .positiveText(R.string.text_install)
                .negativeText(R.string.text_cancel)
                .onPositive((dialog, which) -> IntentUtils.installApk(BuildActivity.this, outApk.getPath()))
                .show();
    }

    @Override
    public void onPrepare(@NonNull ApkBuilder builder) {
        mProgressDialog.setContent(R.string.apk_builder_prepare);
    }

    @Override
    public void onBuild(@NonNull ApkBuilder builder) {
        mProgressDialog.setContent(R.string.apk_builder_build);
    }

    @Override
    public void onSign(@NonNull ApkBuilder builder) {
        mProgressDialog.setContent(R.string.apk_builder_package);
    }

    @Override
    public void onClean(@NonNull ApkBuilder builder) {
        mProgressDialog.setContent(R.string.apk_builder_clean);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @SuppressLint({"CheckResult", "MissingSuperCall"})
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            AppsIconSelectActivity.getDrawableFromIntent(getApplicationContext(), data)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(drawable -> {
                        mIcon.setImageDrawable(drawable);
                        mIsDefaultIcon = false;
                    }, Throwable::printStackTrace);
        }
    }

    public static void launch(Context context, String extraSource) {
        Intent intent = new Intent(context, BuildActivity.class)
                .putExtra(BuildActivity.EXTRA_SOURCE, extraSource);
        context.startActivity(intent);
    }

}
