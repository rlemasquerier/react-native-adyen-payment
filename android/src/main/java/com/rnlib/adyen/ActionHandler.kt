/*
 * Copyright (c) 2019 Adyen N.V.
 *
 * This file is open source and available under the MIT license. See the LICENSE file for more info.
 *
 * Created by caiof on 23/5/2019.
 */

package com.rnlib.adyen

import androidx.lifecycle.Observer
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.adyen.checkout.adyen3ds2.Adyen3DS2Component
import com.adyen.checkout.adyen3ds2.Adyen3DS2Configuration
import com.adyen.checkout.components.ActionComponentData
import com.adyen.checkout.components.model.payments.response.Action
import com.adyen.checkout.core.api.Environment
import com.adyen.checkout.core.log.LogUtil
import com.adyen.checkout.core.log.Logger
import com.adyen.checkout.redirect.RedirectComponent
import com.adyen.checkout.redirect.RedirectConfiguration
import com.adyen.checkout.wechatpay.WeChatPayActionComponent

class ActionHandler(activity: FragmentActivity, private val callback: DetailsRequestedInterface) : Observer<ActionComponentData> {

    companion object {
        val TAG = LogUtil.getTag()
        const val UNKNOWN_ACTION = "UNKNOWN ACTION"
    }

    // TODO client key handling
    // TODO handle environment correctly
    private val redirectConfiguration = RedirectConfiguration.Builder(activity, "TODO")
        .setEnvironment(Environment.TEST)
        .build()
    private val adyen3DS2Configuration = Adyen3DS2Configuration.Builder(activity, "TODO")
        .setEnvironment(Environment.TEST)
        .build()

    private val redirectComponent = RedirectComponent.PROVIDER.get(activity, activity.application, redirectConfiguration)
    private val adyen3DS2Component = Adyen3DS2Component.PROVIDER.get(activity, activity.application, adyen3DS2Configuration)

    init {
        redirectComponent.observe(activity, this)
        adyen3DS2Component.observe(activity, this)

        redirectComponent.observeErrors(activity, Observer {
            callback.onError(it?.errorMessage ?: "Redirect Error.")
        })

        adyen3DS2Component.observeErrors(activity, Observer {
            callback.onError(it?.errorMessage ?: "3DS2 Error.")
        })
    }

    override fun onChanged(componentData: ActionComponentData?) {
        if (componentData != null) {
            callback.requestDetailsCall(componentData)
        }
    }

    fun saveState(bundle: Bundle?) {
        redirectComponent.saveState(bundle)
        adyen3DS2Component.saveState(bundle)
    }

    fun restoreState(bundle: Bundle?) {
        redirectComponent.restoreState(bundle)
        adyen3DS2Component.restoreState(bundle)
    }

    fun handleAction(activity: FragmentActivity, action: Action, sendResult: (String) -> Unit) {
        when {
            redirectComponent.canHandleAction(action) -> {
                redirectComponent.handleAction(activity, action)
            }
            adyen3DS2Component.canHandleAction(action) -> {
                adyen3DS2Component.handleAction(activity, action)
            }
            else -> {
                Logger.e(TAG, "Unknown Action - ${action.type}")
                sendResult("$UNKNOWN_ACTION.${action.type}")
            }
        }
    }

    fun handleRedirectResponse(intent: Intent) {
        redirectComponent.handleIntent(intent)
    }

    interface DetailsRequestedInterface {
        fun requestDetailsCall(actionComponentData: ActionComponentData)
        fun onError(errorMessage: String)
    }
}
