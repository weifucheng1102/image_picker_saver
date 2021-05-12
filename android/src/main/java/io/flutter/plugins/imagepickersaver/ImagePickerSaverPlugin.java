// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.imagepickersaver;

import android.app.Activity;
import android.app.Application;
import android.os.Environment;

import java.io.File;
import java.io.IOException;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.PluginRegistry;


public class ImagePickerSaverPlugin implements MethodChannel.MethodCallHandler, FlutterPlugin, ActivityAware {
    private static final String CHANNEL = "plugins.flutter.io/image_picker_saver";

    private static final int SOURCE_CAMERA = 0;
    private static final int SOURCE_GALLERY = 1;

    private ImagePickerDelegate delegate;

    private MethodChannel channel;
    private FlutterPluginBinding pluginBinding;
    private ActivityPluginBinding activityBinding;
    private Application application;
    private Activity activity;

    @SuppressWarnings("deprecation")
    public static void registerWith(io.flutter.plugin.common.PluginRegistry.Registrar registrar) {
        if (registrar.activity() == null) {
            // If a background flutter view tries to register the plugin, there will be no activity from the registrar,
            // we stop the registering process immediately because the ImagePicker requires an activity.
            return;
        }
        Activity activity = registrar.activity();
        Application application = null;
        if (registrar.context() != null) {
            application = (Application) (registrar.context().getApplicationContext());
        }
        ImagePickerSaverPlugin plugin = new ImagePickerSaverPlugin();
        plugin.setup(registrar.messenger(), application, activity, registrar, null);
    }

    public ImagePickerSaverPlugin() {
    }

    @Override
    public void onAttachedToEngine(FlutterPluginBinding binding) {
        pluginBinding = binding;


    }

    @Override
    public void onDetachedFromEngine(FlutterPluginBinding binding) {
        pluginBinding = null;
    }

    @Override
    public void onAttachedToActivity(ActivityPluginBinding binding) {
        activityBinding = binding;
        setup(
                pluginBinding.getBinaryMessenger(),
                (Application) pluginBinding.getApplicationContext(),
                activityBinding.getActivity(),
                null,
                activityBinding);
    }

    @Override
    public void onDetachedFromActivity() {
        tearDown();
    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {
        onDetachedFromActivity();
    }

    @Override
    public void onReattachedToActivityForConfigChanges(ActivityPluginBinding binding) {
        onAttachedToActivity(binding);
    }

    private void setup(
            final BinaryMessenger messenger,
            final Application application,
            final Activity activity,
            final PluginRegistry.Registrar registrar,
            final ActivityPluginBinding activityBinding) {
        this.activity = activity;
        this.application = application;
        this.delegate = constructDelegate(activity);
        channel = new MethodChannel(messenger, CHANNEL);
        channel.setMethodCallHandler(this);
        if (registrar != null) {
            // V1 embedding setup for activity listeners.
            registrar.addActivityResultListener(delegate);
            registrar.addRequestPermissionsResultListener(delegate);
        } else {
            // V2 embedding setup for activity listeners.
            activityBinding.addActivityResultListener(delegate);
            activityBinding.addRequestPermissionsResultListener(delegate);
        }
    }

    private final ImagePickerDelegate constructDelegate(final Activity setupActivity) {

        final File externalFilesDirectory =
                setupActivity.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        final ExifDataCopier exifDataCopier = new ExifDataCopier();
        final ImageResizer imageResizer = new ImageResizer(externalFilesDirectory, exifDataCopier);
        return new ImagePickerDelegate(setupActivity, externalFilesDirectory, imageResizer);
    }

    private void tearDown() {
        activityBinding.removeActivityResultListener(delegate);
        activityBinding.removeRequestPermissionsResultListener(delegate);
        activityBinding = null;
        delegate = null;
        channel.setMethodCallHandler(null);
        channel = null;
        application = null;
    }

    @Override
    public void onMethodCall(MethodCall call, MethodChannel.Result result) {
        if (activity == null) {
            result.error("no_context", "image_picker plugin requires a foreground activity.", null);
            return;
        }
        if (call.method.equals("pickImage")) {
            int imageSource = call.argument("source");
            switch (imageSource) {
                case SOURCE_GALLERY:
                    delegate.chooseImageFromGallery(call, result);
                    break;
                case SOURCE_CAMERA:
                    delegate.takeImageWithCamera(call, result);
                    break;
                default:
                    throw new IllegalArgumentException("Invalid image source: " + imageSource);
            }
        } else if (call.method.equals("pickVideo")) {
            int imageSource = call.argument("source");
            switch (imageSource) {
                case SOURCE_GALLERY:
                    delegate.chooseVideoFromGallery(call, result);
                    break;
                case SOURCE_CAMERA:
                    delegate.takeVideoWithCamera(call, result);
                    break;
                default:
                    throw new IllegalArgumentException("Invalid video source: " + imageSource);
            }
        } else if (call.method.equals("saveFile")) {


            try {
                delegate.saveImageToGallery(call, result);
            } catch (IOException e) {
                e.printStackTrace();
                throw new IllegalArgumentException(e);
            }


        } else {
            throw new IllegalArgumentException("Unknown method " + call.method);
        }
    }
}

