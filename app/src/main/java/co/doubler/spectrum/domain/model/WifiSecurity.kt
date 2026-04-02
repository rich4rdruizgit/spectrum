package co.doubler.spectrum.domain.model

enum class WifiSecurityLevel { OPEN, WEP, WEAK, GOOD, STRONG }

object WifiSecurity {
    fun parse(capabilities: String): WifiSecurityLevel {
        val caps = capabilities.uppercase()
        return when {
            caps.contains("SAE") || caps.contains("WPA3") -> WifiSecurityLevel.STRONG
            caps.contains("WPA2") && caps.contains("CCMP") && !caps.contains("TKIP") -> WifiSecurityLevel.GOOD
            (caps.contains("WPA2") || caps.contains("WPA")) && caps.contains("TKIP") -> WifiSecurityLevel.WEAK
            caps.contains("WEP") -> WifiSecurityLevel.WEP
            else -> WifiSecurityLevel.OPEN
        }
    }
}
