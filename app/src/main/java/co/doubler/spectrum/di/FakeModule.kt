package co.doubler.spectrum.di

import co.doubler.spectrum.data.fake.FakeBluetoothRepository
import co.doubler.spectrum.data.fake.FakeMagneticFieldRepository
import co.doubler.spectrum.data.fake.FakeWifiRepository
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
abstract class FakeModule {

    @Binds
    @Singleton
    @DemoImpl
    abstract fun bindFakeWifiRepository(impl: FakeWifiRepository): WifiRepository

    @Binds
    @Singleton
    @DemoImpl
    abstract fun bindFakeBleRepository(impl: FakeBluetoothRepository): BluetoothRepository

    @Binds
    @Singleton
    @DemoImpl
    abstract fun bindFakeMagneticRepository(impl: FakeMagneticFieldRepository): MagneticFieldRepository
}
