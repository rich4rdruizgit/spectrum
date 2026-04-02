package co.doubler.spectrum.di

import co.doubler.spectrum.data.repository.BluetoothRepositoryImpl
import co.doubler.spectrum.data.repository.MagneticFieldRepositoryImpl
import co.doubler.spectrum.data.repository.WifiRepositoryImpl
import co.doubler.spectrum.domain.repository.BluetoothRepository
import co.doubler.spectrum.domain.repository.MagneticFieldRepository
import co.doubler.spectrum.domain.repository.WifiRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SensorModule {

    @Binds
    @Singleton
    abstract fun bindWifiRepository(impl: WifiRepositoryImpl): WifiRepository

    @Binds
    @Singleton
    abstract fun bindBluetoothRepository(impl: BluetoothRepositoryImpl): BluetoothRepository

    @Binds
    @Singleton
    abstract fun bindMagneticFieldRepository(impl: MagneticFieldRepositoryImpl): MagneticFieldRepository
}
