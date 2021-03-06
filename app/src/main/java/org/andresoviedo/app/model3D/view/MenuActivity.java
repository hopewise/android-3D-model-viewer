package org.andresoviedo.app.model3D.view;

import android.app.ListActivity;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import org.andresoviedo.app.model3D.services.wavefront.WavefrontLoader;
import org.andresoviedo.app.util.android.AndroidUtils;
import org.andresoviedo.app.util.android.AssetUtils;
import org.andresoviedo.app.util.android.ContentUtils;
import org.andresoviedo.app.util.android.FileUtils;
import org.andresoviedo.app.util.view.TextActivity;
import org.andresoviedo.dddmodel2.R;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class MenuActivity extends ListActivity {

    private static final int REQUEST_READ_EXTERNAL_STORAGE = 1000;
    private static final int REQUEST_CODE_OPEN_FILE = 1101;
    private static final int REQUEST_CODE_OPEN_MATERIAL = 1102;
    private static final int REQUEST_CODE_OPEN_TEXTURE = 1103;
    private static final String SUPPORTED_FILE_TYPES_REGEX = "(?i).*\\.(obj|stl|dae)";


    private enum Action {
        LOAD_MODEL, GITHUB, SETTINGS, HELP, ABOUT, EXIT, UNKNOWN, DEMO
    }

    /**
     * Load file user data
     */
    private Map<String, Object> loadModelParameters = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_menu);
        setListAdapter(new ArrayAdapter<>(this, R.layout.activity_menu_item,
                getResources().getStringArray(R.array.menu_items)));
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        String selectedItem = (String) getListView().getItemAtPosition(position);
        // Toast.makeText(getApplicationContext(), "Click ListItem '" + selectedItem + "'", Toast.LENGTH_LONG).show();
        String selectedAction = selectedItem.replace(' ', '_').toUpperCase(Locale.getDefault());
        Action action = Action.UNKNOWN;
        try {
            action = Action.valueOf(selectedAction);
        } catch (IllegalArgumentException ex) {
            Toast.makeText(getApplicationContext(), ex.getMessage(), Toast.LENGTH_LONG).show();
        }
        try {
            switch (action) {
                case DEMO:
                    Intent demoIntent = new Intent(MenuActivity.this.getApplicationContext(), ModelActivity.class);
                    demoIntent.putExtra("immersiveMode", "true");
                    demoIntent.putExtra("backgroundColor", "0 0 0 1");
                    MenuActivity.this.startActivity(demoIntent);
                    break;
                case GITHUB:
                    AndroidUtils.openUrl(this, "https://github.com/andresoviedo/android-3D-model-viewer");
                    break;
                case LOAD_MODEL:
                    loadModel();
                    break;
                case ABOUT:
                    Intent aboutIntent = new Intent(MenuActivity.this.getApplicationContext(), TextActivity.class);
                    aboutIntent.putExtra("title", selectedItem);
                    aboutIntent.putExtra("text", getResources().getString(R.string.about_text));
                    MenuActivity.this.startActivity(aboutIntent);
                    break;
                case HELP:
                    Intent helpIntent = new Intent(MenuActivity.this.getApplicationContext(), TextActivity.class);
                    helpIntent.putExtra("title", selectedItem);
                    helpIntent.putExtra("text", getResources().getString(R.string.help_text));
                    MenuActivity.this.startActivity(helpIntent);
                    break;
                case SETTINGS:
                    break;
                case EXIT:
                    MenuActivity.this.finish();
                    break;
                case UNKNOWN:
                    Toast.makeText(getApplicationContext(), "Unrecognized action '" + selectedAction + "'",
                            Toast.LENGTH_LONG).show();
                    break;
            }
        } catch (Exception ex) {
            Toast.makeText(getApplicationContext(), ex.getMessage(), Toast.LENGTH_LONG).show();
        }

    }

    private void loadModel() {
        ContentUtils.showListDialog(this, "File Provider", new String[]{"Embedded Models", "External Storage ",
                "Content Provider"}, (DialogInterface dialog, int which) -> {
            if (which == 0) {
                loadModelFromAssets();
            } else if (which == 1) {
                loadModelFromSdCard();
            } else {
                loadModelFromContentProvider();
            }
        });

    }

    private void loadModelFromAssets() {
        AssetUtils.createChooserDialog(this, "Select file", null, "models", "(?i).*\\.(obj|stl|dae)",
                (String file) -> {
                    if (file != null) {
                        ContentUtils.provideAssets(this);
                        launchModelRendererActivity(Uri.parse("assets://" + getPackageName() + "/" + file));
                    }
                });
    }

    private void loadModelFromSdCard() {
        // check permission starting from android API 23 - Marshmallow
        if (!FileUtils.checkReadExternalStoragePermissionOrRequest(this, REQUEST_READ_EXTERNAL_STORAGE)) {
            return;
        }
        FileUtils.createChooserDialog(this, "Select file", null, null, SUPPORTED_FILE_TYPES_REGEX,
                (File file) -> {
                    if (file != null) {
                        ContentUtils.setCurrentDir(file.getParentFile());
                        launchModelRendererActivity(Uri.parse("file://" + file.getAbsolutePath()));
                    }
                });
    }

    private void loadModelFromContentProvider() {
        loadModelParameters.clear();
        ContentUtils.clearDocumentsProvided();
        askForFile(REQUEST_CODE_OPEN_FILE, "*/*");
    }

    private void askForFile(int requestCode, String mimeType) {
        Intent target = ContentUtils.createGetContentIntent(mimeType);
        Intent intent = Intent.createChooser(target, "Select file");
        try {
            startActivityForResult(intent, requestCode);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Error. Please install a file content provider", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        ContentUtils.setThreadActivity(this);
        switch (requestCode) {
            case REQUEST_READ_EXTERNAL_STORAGE:
                loadModelFromSdCard();
                break;
            case REQUEST_CODE_OPEN_FILE:
                if (resultCode != RESULT_OK) {
                    return;
                }
                final Uri uri = data.getData();
                if (uri == null) {
                    return;
                }

                // save user selected model
                loadModelParameters.put("model", uri);

                // detect model type
                if (uri.toString().toLowerCase().endsWith(".obj")) {
                    askForRelatedFiles(0);
                } else if (uri.toString().toLowerCase().endsWith(".stl")) {
                    askForRelatedFiles(1);
                } else if (uri.toString().toLowerCase().endsWith(".dae")) {
                    askForRelatedFiles(2);
                } else {
                    // no model type from filename, ask user...
                    ContentUtils.showListDialog(this, "Select type", new String[]{"Wavefront (*.obj)", "Stereolithography (*" +
                            ".stl)", "Collada (*.dae)"}, (dialog, which) -> askForRelatedFiles(which));
                }
                break;
            case REQUEST_CODE_OPEN_MATERIAL:
                if (resultCode != RESULT_OK || data.getData() == null) {
                    launchModelRendererActivity(getUserSelectedModel());
                    break;
                }
                String filename = (String) loadModelParameters.get("file");
                ContentUtils.addUri(filename, data.getData());
                // check if material references texture file
                String textureFile = WavefrontLoader.getTextureFile(data.getData());
                if (textureFile == null) {
                    launchModelRendererActivity(getUserSelectedModel());
                    break;
                }
                ContentUtils.showDialog(this, "Select texture file", "This model references a " +
                                "texture file (" + textureFile + "). Please select it", "OK",
                        "Cancel", (DialogInterface dialog, int which) -> {
                            switch (which) {
                                case DialogInterface.BUTTON_NEGATIVE:
                                    launchModelRendererActivity(getUserSelectedModel());
                                    break;
                                case DialogInterface.BUTTON_POSITIVE:
                                    loadModelParameters.put("file", textureFile);
                                    askForFile(REQUEST_CODE_OPEN_TEXTURE, "image/*");
                            }
                        });
                break;
            case REQUEST_CODE_OPEN_TEXTURE:
                if (resultCode != RESULT_OK || data.getData() == null) {
                    launchModelRendererActivity(getUserSelectedModel());
                    break;
                }
                String textureFilename = (String) loadModelParameters.get("file");
                ContentUtils.addUri(textureFilename, data.getData());
                launchModelRendererActivity(getUserSelectedModel());
        }
    }

    private Uri getUserSelectedModel() {
        return (Uri) loadModelParameters.get("model");
    }

    private void askForRelatedFiles(int modelType) {
        loadModelParameters.put("type", modelType);
        switch (modelType) {
            case 0: // obj
                // check if model references material file
                String materialFile = WavefrontLoader.getMaterialLib(getUserSelectedModel());
                if (materialFile == null) {
                    launchModelRendererActivity(getUserSelectedModel());
                    break;
                }
                ContentUtils.showDialog(this, "Select material file", "This model references a " +
                                "material file (" + materialFile + "). Please select it", "OK",
                        "Cancel", (DialogInterface dialog, int which) -> {
                            switch (which) {
                                case DialogInterface.BUTTON_NEGATIVE:
                                    launchModelRendererActivity(getUserSelectedModel());
                                    break;
                                case DialogInterface.BUTTON_POSITIVE:
                                    loadModelParameters.put("file", materialFile);
                                    askForFile(REQUEST_CODE_OPEN_MATERIAL, "*/*");
                            }
                        });
                break;
            case 1: // stl
                launchModelRendererActivity(getUserSelectedModel());
                break;
            case 2: // dae
                // TODO: pre-process file to ask for referenced textures
                // XmlParser.parse(colladaFile)
                launchModelRendererActivity(getUserSelectedModel());
                break;
        }
    }

    private void launchModelRendererActivity(Uri uri) {
        Log.i("Menu", "Launching renderer for '" + uri + "'");
        Intent intent = new Intent(getApplicationContext(), ModelActivity.class);
        intent.putExtra("uri", uri.toString());
        intent.putExtra("immersiveMode", "true");

        // content provider case
        if (!loadModelParameters.isEmpty()) {
            intent.putExtra("type", loadModelParameters.get("type").toString());
            loadModelParameters.clear();
        }

        startActivity(intent);
    }
}
