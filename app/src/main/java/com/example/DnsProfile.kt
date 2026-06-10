package com.example

data class DnsProfile(
    val name: String,
    val primaryDns: String,
    val secondaryDns: String,
    val dohHost: String = "",
    val description: String,
    val logoAccent: Long, // Hex color Long representation for visual color representation
    val primaryDnsIpv6: String = "",
    val secondaryDnsIpv6: String = ""
) {
    companion object {
        val Cloudflare = DnsProfile(
            name = "Cloudflare DNS",
            primaryDns = "1.1.1.1",
            secondaryDns = "1.0.0.1",
            dohHost = "cloudflare-dns.com",
            description = "Focado em velocidade máxima e alta privacidade. Um dos melhores DNS mundiais.",
            logoAccent = 0xFFF38020L, // Orange
            primaryDnsIpv6 = "2606:4700:4700::1111",
            secondaryDnsIpv6 = "2606:4700:4700::1001"
        )

        val Google = DnsProfile(
            name = "Google Public DNS",
            primaryDns = "8.8.8.8",
            secondaryDns = "8.8.4.4",
            dohHost = "dns.google",
            description = "Seguro, rápido, excelente estabilidade e altamente compatível com streaming.",
            logoAccent = 0xFF4285F4L, // Blue
            primaryDnsIpv6 = "2001:4860:4860::8888",
            secondaryDnsIpv6 = "2001:4860:4860::8844"
        )

        val AdGuard = DnsProfile(
            name = "AdGuard DNS (Anti-Anúncios)",
            primaryDns = "94.140.14.14",
            secondaryDns = "94.140.15.15",
            dohHost = "dns.adguard-dns.com",
            description = "Bloqueia anúncios, spams, rastreadores e scripts maliciosos de forma automática em toda a TV.",
            logoAccent = 0xFF2CA05AL, // Green
            primaryDnsIpv6 = "2a10:50c0::ad1:feed",
            secondaryDnsIpv6 = "2a10:50c0::ad2:feed"
        )

        val CleanBrowsing = DnsProfile(
            name = "CleanBrowsing (Filtro Familiar)",
            primaryDns = "185.228.168.168",
            secondaryDns = "185.228.169.168",
            dohHost = "family-filter.cleanbrowsing.org",
            description = "Bloqueia conteúdo adulto, obsceno e links criminosos para navegação infantil segura.",
            logoAccent = 0xFF9B59B6L, // Purple
            primaryDnsIpv6 = "2a0d:5600:191::",
            secondaryDnsIpv6 = "2a0d:5600:191::1"
        )
        
        val Quad9 = DnsProfile(
            name = "Quad9 (Foco Segurança)",
            primaryDns = "9.9.9.9",
            secondaryDns = "149.112.112.112",
            dohHost = "dns.quad9.net",
            description = "Filtra ameaças cibernéticas em tempo real, impedindo acessos a servidores de malware infectados.",
            logoAccent = 0xFFE74C3CL, // Red
            primaryDnsIpv6 = "2620:fe::fe",
            secondaryDnsIpv6 = "2620:fe::9"
        )

        val Custom = DnsProfile(
            name = "Personalizado (Manual)",
            primaryDns = "",
            secondaryDns = "",
            dohHost = "",
            description = "Configure manualmente...",
            logoAccent = 0xFF7F8C8DL,
            primaryDnsIpv6 = "",
            secondaryDnsIpv6 = ""
        )

        val presets = listOf(Cloudflare, Google, AdGuard, CleanBrowsing, Quad9, Custom)
    }
}
