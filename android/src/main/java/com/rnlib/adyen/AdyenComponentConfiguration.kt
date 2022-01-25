package com.rnlib.adyen

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Parcel
import android.os.Parcelable
import com.adyen.checkout.components.base.Configuration
import com.adyen.checkout.components.model.payments.Amount
import com.adyen.checkout.components.util.CheckoutCurrency
import com.adyen.checkout.components.util.PaymentMethodTypes
import com.adyen.checkout.bcmc.BcmcConfiguration
import com.adyen.checkout.card.CardConfiguration
import com.adyen.checkout.core.api.Environment
import com.adyen.checkout.core.exception.CheckoutException
import com.adyen.checkout.core.log.LogUtil
import com.adyen.checkout.core.model.JsonUtils
import com.adyen.checkout.core.util.LocaleUtil
import com.adyen.checkout.dotpay.DotpayConfiguration

import com.rnlib.adyen.AdyenComponentConfiguration.Builder

import com.adyen.checkout.entercash.EntercashConfiguration
import com.adyen.checkout.eps.EPSConfiguration
import com.adyen.checkout.googlepay.GooglePayConfiguration
import com.adyen.checkout.ideal.IdealConfiguration
import com.adyen.checkout.molpay.MolpayConfiguration
import com.adyen.checkout.openbanking.OpenBankingConfiguration
import com.adyen.checkout.sepa.SepaConfiguration
import java.util.Locale

/**
 * This is the base configuration for the Drop-In solution. You need to use the [Builder] to instantiate this class.
 * There you will find specific methods to add configurations for each specific PaymentComponent, to be able to customize their behavior.
 * If you don't specify anything, a default configuration will be used.
 */
@SuppressWarnings("TooManyFunctions")
class AdyenComponentConfiguration : Configuration, Parcelable {

    val availableConfigs: Map<String, Configuration>
    val serviceComponentName: ComponentName
    val resultHandlerIntent: Intent
    val amount: Amount

    companion object {
        @JvmField
        val CREATOR = object : Parcelable.Creator<AdyenComponentConfiguration> {
            override fun createFromParcel(parcel: Parcel) = AdyenComponentConfiguration(parcel)
            override fun newArray(size: Int) = arrayOfNulls<AdyenComponentConfiguration>(size)
        }
    }

    constructor(
        shopperLocale: Locale,
        environment: Environment,
        availableConfigs: Map<String, Configuration>,
        serviceComponentName: ComponentName,
        resultHandlerIntent: Intent,
        amount: Amount,
        clientKey: String
    ) : super(shopperLocale, environment, clientKey) {
        this.availableConfigs = availableConfigs
        this.serviceComponentName = serviceComponentName
        this.resultHandlerIntent = resultHandlerIntent
        this.amount = amount
    }

    constructor(parcel: Parcel) : super(parcel) {
        @Suppress("UNCHECKED_CAST")
        availableConfigs = parcel.readHashMap(Configuration::class.java.classLoader) as Map<String, Configuration>
        serviceComponentName = parcel.readParcelable(ComponentName::class.java.classLoader)!!
        resultHandlerIntent = parcel.readParcelable(Intent::class.java.classLoader)!!
        amount = Amount.CREATOR.createFromParcel(parcel)
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        super.writeToParcel(dest, flags)
        dest.writeMap(availableConfigs)
        dest.writeParcelable(serviceComponentName, flags)
        dest.writeParcelable(resultHandlerIntent, flags)
        JsonUtils.writeToParcel(dest, Amount.SERIALIZER.serialize(amount))
    }

    override fun describeContents(): Int {
        return Parcelable.CONTENTS_FILE_DESCRIPTOR;
    }

    fun <T : Configuration> getConfigurationFor(paymentMethod: String, context: Context): T {
        return if (PaymentMethodTypes.SUPPORTED_PAYMENT_METHODS.contains(paymentMethod) && availableConfigs.containsKey(paymentMethod)) {
            @Suppress("UNCHECKED_CAST")
            availableConfigs[paymentMethod] as T
        } else {
            getDefaultConfigFor(paymentMethod, context, this)
        }
    }

    /**
     * Builder for creating a [DropInConfiguration] where you can set specific Configurations for a Payment Method
     */
    class Builder {

        companion object {
            val TAG = LogUtil.getTag()
        }

        private val availableConfigs = HashMap<String, Configuration>()

        private var serviceComponentName: ComponentName
        private var shopperLocale: Locale
        private var clientKey: String
        private var resultHandlerIntent: Intent
        private var environment: Environment = Environment.EUROPE
        private var amount: Amount = Amount.EMPTY

        private val packageName: String
        private val serviceClassName: String

        @Deprecated("You need to pass resultHandlerIntent to drop-in configuration")
        constructor(context: Context, serviceClass: Class<out Any?>) : this(context, Intent(), serviceClass)

        /**
         * @param context
         * @param resultHandlerIntent The Intent used with [Activity.startActivity] that will contain the payment result extra with key [RESULT_KEY].
         * @param serviceClass Service that extended from [DropInService] that would handle network requests.
         */
        constructor(context: Context, resultHandlerIntent: Intent, serviceClass: Class<out Any?>) {
            this.packageName = context.packageName
            this.serviceClassName = serviceClass.name

            this.resultHandlerIntent = resultHandlerIntent
            this.serviceComponentName = ComponentName(packageName, serviceClassName)
            this.shopperLocale = LocaleUtil.getLocale(context)
            // TODO
            this.clientKey = "to do"
        }

        /**
         * Create a Builder with the same values of an existing Configuration object.
         */
        constructor(adyenComponentConfiguration: AdyenComponentConfiguration) {
            this.packageName = adyenComponentConfiguration.serviceComponentName.packageName
            this.serviceClassName = adyenComponentConfiguration.serviceComponentName.className

            this.serviceComponentName = adyenComponentConfiguration.serviceComponentName
            this.shopperLocale = adyenComponentConfiguration.shopperLocale
            this.clientKey = adyenComponentConfiguration.clientKey
            this.environment = adyenComponentConfiguration.environment
            this.resultHandlerIntent = adyenComponentConfiguration.resultHandlerIntent
            this.amount = adyenComponentConfiguration.amount
        }

        fun setServiceComponentName(serviceComponentName: ComponentName): Builder {
            this.serviceComponentName = serviceComponentName
            return this
        }

        fun setShopperLocale(shopperLocale: Locale): Builder {
            this.shopperLocale = shopperLocale
            return this
        }

        fun setResultHandlerIntent(resultHandlerIntent: Intent): Builder {
            this.resultHandlerIntent = resultHandlerIntent
            return this
        }

        fun setEnvironment(environment: Environment): Builder {
            this.environment = environment
            return this
        }

        fun setAmount(amount: Amount): Builder {
            if (!CheckoutCurrency.isSupported(amount.currency) || amount.value < 0) {
                throw CheckoutException("Currency is not valid.")
            }
            this.amount = amount
            return this
        }

        /**
         * Add configuration for Credit Card payment method.
         */
        fun addCardConfiguration(cardConfiguration: CardConfiguration): Builder {
            availableConfigs[PaymentMethodTypes.SCHEME] = cardConfiguration
            return this
        }

        /**
         * Add configuration for iDeal payment method.
         */
        fun addIdealConfiguration(idealConfiguration: IdealConfiguration): Builder {
            availableConfigs[PaymentMethodTypes.IDEAL] = idealConfiguration
            return this
        }

        /**
         * Add configuration for MolPay Thailand payment method.
         */
        fun addMolpayThailandConfiguration(molpayConfiguration: MolpayConfiguration): Builder {
            availableConfigs[PaymentMethodTypes.MOLPAY_THAILAND] = molpayConfiguration
            return this
        }

        /**
         * Add configuration for MolPay Malasya payment method.
         */
        fun addMolpayMalasyaConfiguration(molpayConfiguration: MolpayConfiguration): Builder {
            availableConfigs[PaymentMethodTypes.MOLPAY_MALAYSIA] = molpayConfiguration
            return this
        }

        /**
         * Add configuration for MolPay Vietnam payment method.
         */
        fun addMolpayVietnamConfiguration(molpayConfiguration: MolpayConfiguration): Builder {
            availableConfigs[PaymentMethodTypes.MOLPAY_VIETNAM] = molpayConfiguration
            return this
        }

        /**
         * Add configuration for DotPay payment method.
         */
        fun addDotpayConfiguration(dotpayConfiguration: DotpayConfiguration): Builder {
            availableConfigs[PaymentMethodTypes.DOTPAY] = dotpayConfiguration
            return this
        }

        /**
         * Add configuration for EPS payment method.
         */
        fun addEpsConfiguration(epsConfiguration: EPSConfiguration): Builder {
            availableConfigs[PaymentMethodTypes.EPS] = epsConfiguration
            return this
        }

        /**
         * Add configuration for EnterCash payment method.
         */
        fun addEntercashConfiguration(entercashConfiguration: EntercashConfiguration): Builder {
            availableConfigs[PaymentMethodTypes.ENTERCASH] = entercashConfiguration
            return this
        }

        /**
         * Add configuration for Open Banking payment method.
         */
        fun addOpenBankingConfiguration(openBankingConfiguration: OpenBankingConfiguration): Builder {
            availableConfigs[PaymentMethodTypes.OPEN_BANKING] = openBankingConfiguration
            return this
        }

        /**
         * Add configuration for Google Pay payment method.
         */
        fun addGooglePayConfiguration(googlePayConfiguration: GooglePayConfiguration): Builder {
            availableConfigs[PaymentMethodTypes.GOOGLE_PAY] = googlePayConfiguration
            return this
        }

        /**
         * Add configuration for Sepa payment method.
         */
        fun addSepaConfiguration(sepaConfiguration: SepaConfiguration): Builder {
            availableConfigs[PaymentMethodTypes.SEPA] = sepaConfiguration
            return this
        }

        /**
         * Add configuration for Sepa payment method.
         */
        fun addBcmcConfiguration(bcmcConfiguration: BcmcConfiguration): Builder {
            availableConfigs[PaymentMethodTypes.BCMC] = bcmcConfiguration
            return this
        }

        /**
         * Create the [DropInConfiguration] instance.
         */
        fun build(): AdyenComponentConfiguration {
            return AdyenComponentConfiguration(
                    shopperLocale,
                    environment,
                    availableConfigs,
                    serviceComponentName,
                    resultHandlerIntent,
                    amount,
                    clientKey
            )
        }
    }
}
