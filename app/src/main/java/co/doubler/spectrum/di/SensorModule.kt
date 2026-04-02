package co.doubler.spectrum.di

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.hardware.SensorManager
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import co.doubler.spectrum.data.fake.DemoAwareBleRepository
import co.doubler.spectrum.data.fake.DemoAwareMagneticRepository
import co.doubler.spectrum.data.fake.DemoAwareWifiRepository
import co.doubler.spectrum.data.repository.BluetoothRepositoryImpl
import co.doubler.spectrum.data.repository.MagneticFieldRepositoryImpl
import co.doubler.spectrum.data.repository.WifiRepositoryImpl
import co.doubler.spectrum.data.sensor.BluetoothScanner
import co.doubler.spectrum.data.sensor.BluetoothScannerImpl
import co.doubler.spectrum.data.sensor.MagnetometerReader
import co.doubler.spectrum.data.sensor.MagnetometerReaderImpl
import co.doubler.spectrum.data.sensor.WifiScanner
import co.doubler.spectrum.data.sensor.WifiScannerImpl
import co.doubler.spectrum.domain.repository.BluetoothRepository
import co.doubler.spectrum.domain.repository.MagneticFieldRepository
import co.doubler.spectrum.domain.repository.WifiRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SensorModule {

    // ── Scanner interface bindings ───────────────────────────────

    @Binds
    @Singleton
    abstract fun bindWifiScanner(impl: WifiScannerImpl): WifiScanner

    @Binds
    @Singleton
    abstract fun bindBluetoothScanner(impl: BluetoothScannerImpl): BluetoothScanner

    @Binds
    @Singleton
    abstract fun bindMagnetometerReader(impl: MagnetometerReaderImpl): MagnetometerReader

    // ── Repository interface bindings ────────────────────────────

    @Binds
    @Singleton
    @RealImpl
    abstract fun bindWifiRepository(impl: WifiRepositoryImpl): WifiRepository

    @Binds
    @Singleton
    abstract fun bindDemoAwareWifiRepository(impl: DemoAwareWifiRepository): WifiRepository

    @Binds
    @Singleton
    @RealImpl
    abstract fun bindBluetoothRepository(impl: BluetoothRepositoryImpl): BluetoothRepository

    @Binds
    @Singleton
    abstract fun bindDemoAwareBleRepository(impl: DemoAwareBleRepository): BluetoothRepository

    @Binds
    @Singleton
    @RealImpl
    abstract fun bindMagneticFieldRepository(impl: MagneticFieldRepositoryImpl): MagneticFieldRepository

    @Binds
    @Singleton
    abstract fun bindDemoAwareMagneticRepository(impl: DemoAwareMagneticRepository): MagneticFieldRepository

    companion object {

        // ── System service providers ─────────────────────────────

        @Provides
        @Singleton
        fun provideWifiManager(@ApplicationContext context: Context): WifiManager =
            context.getSystemService(Context.WIFI_SERVICE) as WifiManager

        @Provides
        @Singleton
        fun provideConnectivityManager(@ApplicationContext context: Context): ConnectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        @Provides
        @Singleton
        fun provideBluetoothAdapter(@ApplicationContext context: Context): BluetoothAdapter {
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            return manager.adapter
        }

        @Provides
        @Singleton
        fun provideSensorManager(@ApplicationContext context: Context): SensorManager =
            context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

        // ── Coroutine scope for shareIn at repository level ──────

        @Provides
        @Singleton
        @SensorScope
        fun provideSensorCoroutineScope(): CoroutineScope =
            CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
