package com.vova9110.bloodbath;

import androidx.annotation.Nullable;

import java.util.logging.Logger;

public class MyLogger extends Logger {
    protected MyLogger(@Nullable String name, @Nullable String resourceBundleName) {
        super(name, resourceBundleName);
    }
}
