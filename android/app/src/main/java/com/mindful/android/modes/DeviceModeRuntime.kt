// Copyright 2026 Rafail Andreou. SPDX-License-Identifier: GPL-2.0-only
package com.mindful.android.modes

import android.content.Context
import com.mindful.android.remote.RemoteEvaluation

object DeviceModeRuntime {
    @Synchronized fun reconcile(c: Context,e: RemoteEvaluation) {
        DeviceModeStore.audit(c,e)
        DeviceModeActions.reconcile(c,e)
    }
}
