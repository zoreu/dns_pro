package com.example.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.DnsProfile
import com.example.DnsViewModel

@Composable
fun DnsProApp(viewModel: DnsViewModel) {
    val context = LocalContext.current
    val vpnActive = viewModel.isVpnActive
    val selectedProfile = viewModel.selectedProfile

    // Launcher for handling standard Android system VPN authorization dialog
    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.toggleDns(
                onVpnPermissionRequired = { },
                onStartRejected = { message -> Toast.makeText(context, message, Toast.LENGTH_LONG).show() }
            )
        } else {
            Toast.makeText(context, "A permissão da VPN é necessária para aplicar o DNS.", Toast.LENGTH_LONG).show()
        }
    }

    // Main dark theme wrapper
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF121416) // Eyesafe cinema dark background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1A1C20),
                            Color(0xFF101113)
                        )
                    )
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(28.dp)
            ) {
                // 1. LEFT SIDEBAR: Status Display, Quick Info, Start/Stop toggle button (40% width)
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(0.42f)
                        .background(Color(0xFF1E2124), RoundedCornerShape(16.dp))
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Logo and Title Area
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Text(
                            text = "DNS",
                            color = Color(0xFF00ADB5),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "PRO",
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black
                        )
                    }

                    // Main Circular Connectivity Status Gauge
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(140.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = if (vpnActive) {
                                        listOf(Color(0x3300E676), Color(0x0500E676))
                                    } else {
                                        listOf(Color(0x11CCCCCC), Color(0x00CCCCCC))
                                    }
                                )
                            )
                            .border(
                                2.dp,
                                if (vpnActive) Color(0xFF00E676) else Color(0xFF7F8C8D),
                                CircleShape
                            )
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = if (vpnActive) Icons.Default.Lock else Icons.Default.Info,
                                contentDescription = null,
                                tint = if (vpnActive) Color(0xFF00E676) else Color(0xFF7F8C8D),
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (vpnActive) "ATIVO" else "PARADO",
                                color = if (vpnActive) Color(0xFF00E676) else Color(0xFF7F8C8D),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }

                    // Info description panel
                    Text(
                        text = if (vpnActive) {
                            "Servidor ativo: ${selectedProfile.name}\n${if (selectedProfile.dohHost.isNotEmpty()) selectedProfile.dohHost else viewModel.customPrimary}"
                        } else {
                            "Status: O DNS PRO está inativo. Conexão padrão de sua TV Box ativa."
                        },
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    // Big Start / Stop Interactive Button (Highly highlighted DPad Focusable)
                    TvFocusableCard(
                        onClick = {
                            val vpnIntent = VpnService.prepare(context)
                            if (vpnIntent != null) {
                                vpnPermissionLauncher.launch(vpnIntent)
                            } else {
                                viewModel.toggleDns(
                                    onVpnPermissionRequired = { },
                                    onStartRejected = { message -> Toast.makeText(context, message, Toast.LENGTH_LONG).show() }
                                )
                            }
                        },
                        accentColor = if (vpnActive) Color(0xFFFF5252) else Color(0xFF00ADB5),
                        focusedScale = 1.04f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        colors = if (vpnActive) {
                                            listOf(Color(0xFFE53935), Color(0xFFC62828))
                                        } else {
                                            listOf(Color(0xFF00ADB5), Color(0xFF00898F))
                                        }
                                    )
                                )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (vpnActive) Icons.Default.Close else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White
                                )
                                Text(
                                    text = if (vpnActive) "DESATIVAR DNS" else "ATIVAR DNS PRO",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }

                    // Network Latency Diagnostic Suite Button
                    TvFocusableCard(
                        onClick = {
                            viewModel.testAllLatencies()
                            Toast.makeText(context, "Verificando latência dos servidores...", Toast.LENGTH_SHORT).show()
                        },
                        accentColor = Color(0xFFFFC107),
                        focusedScale = 1.03f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF2C3236))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = Color(0xFFFFC107),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "TESTAR LATÊNCIAS (MS)",
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Brief informational footer
                    Text(
                        text = "Utiliza VPN Local no dispositivo sem rotear dados de internet externa, resultando em velocidade máxima de streaming.",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                }

                // 2. RIGHT PANEL: Split view between profile selection grid & custom parameters
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(0.58f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header for Selector Column
                    Text(
                        text = "Selecione o Servidor DNS Desejado:",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 4.dp)
                    )

                    // Profile selector LazyVerticalGrid - Fully remote-friendly scrollable
                    Box(modifier = Modifier.weight(0.55f)) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(DnsProfile.presets) { profile ->
                                val isSelected = selectedProfile.name == profile.name
                                val latency = viewModel.latencies[profile.name]
                                val isTesting = viewModel.testingStatus[profile.name] ?: false

                                TvFocusableCard(
                                    onClick = {
                                        viewModel.selectProfile(profile)
                                    },
                                    accentColor = Color(profile.logoAccent),
                                    focusedScale = 1.05f,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(96.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                if (isSelected) {
                                                    Color(profile.logoAccent).copy(alpha = 0.12f)
                                                } else {
                                                    Color(0xFF1E2124)
                                                }
                                            )
                                            .border(
                                                width = if (isSelected) 2.dp else 0.dp,
                                                color = if (isSelected) Color(profile.logoAccent) else Color.Transparent,
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = profile.name,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(0.7f)
                                            )

                                            // Small active check icon or color circle
                                            Box(
                                                modifier = Modifier
                                                    .size(12.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (isSelected) Color(profile.logoAccent) else Color.White.copy(
                                                            alpha = 0.2f
                                                        )
                                                    )
                                            )
                                        }

                                        // Render real live response ping latency indicator
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.Bottom
                                        ) {
                                            Text(
                                                text = if (profile == DnsProfile.Custom) "Config. Manual" else profile.primaryDns,
                                                color = Color.White.copy(alpha = 0.5f),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium
                                            )

                                            if (profile != DnsProfile.Custom) {
                                                if (isTesting) {
                                                    Text(
                                                        text = "Medindo...",
                                                        color = Color(0xFFFFC107),
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                } else if (latency != null) {
                                                    val (latencyText, latencyColor) = when {
                                                        latency < 0 -> "Insucesso" to Color(0xFFFF5252)
                                                        latency <= 35 -> "${latency}ms (Rápido)" to Color(0xFF00E676)
                                                        latency <= 75 -> "${latency}ms (Médio)" to Color(0xFFFFA000)
                                                        else -> "${latency}ms (Lento)" to Color(0xFFFF5252)
                                                    }
                                                    Text(
                                                        text = latencyText,
                                                        color = latencyColor,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Bottom info block: Details of current selection or Form fields for manual profiles
                    Box(
                        modifier = Modifier
                            .weight(0.45f)
                            .fillMaxWidth()
                            .background(Color(0xFF1E2124), RoundedCornerShape(16.dp))
                            .padding(16.dp)
                    ) {
                        if (selectedProfile == DnsProfile.Custom) {
                            // Custom editing Form
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "🔧 Parâmetros de Servidores Personalizados:",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF231E12), RoundedCornerShape(8.dp))
                                        .border(1.dp, Color(0xFFE5A93B).copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                        .padding(12.dp)
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = Color(0xFFE5A93B),
                                            modifier = Modifier.size(20.dp).align(Alignment.CenterVertically)
                                        )
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(
                                                text = "Aviso de DNS Privado do Android",
                                                color = Color(0xFFE5A93B),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                            Text(
                                                text = "Se a sua TV exibir 'DNS privado não pode ser acessado' ou perder a conexão, é porque a função 'DNS Privado' nativa do Android está ativada e recusando IPs normais.",
                                                color = Color.White.copy(alpha = 0.85f),
                                                fontSize = 11.sp,
                                                lineHeight = 15.sp
                                            )
                                            Text(
                                                text = "💡 Solução: Vá em Configurações da TV -> Rede e Internet -> DNS Privado (DNS Seguro) -> selecione 'Desativado' (Off). Assim, qualquer IP funcionará perfeitamente!",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                lineHeight = 15.sp
                                            )
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedTextField(
                                        value = viewModel.customPrimary,
                                        onValueChange = {
                                            viewModel.saveCustomConfig(
                                                primary = it,
                                                secondary = viewModel.customSecondary,
                                                doh = viewModel.customDohHost
                                            )
                                        },
                                        label = { Text("DNS Primário IPv4", fontSize = 11.sp) },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White,
                                            focusedLabelColor = Color(0xFF00ADB5),
                                            unfocusedLabelColor = Color.White.copy(alpha = 0.5f),
                                            focusedBorderColor = Color(0xFF00ADB5),
                                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f)
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )

                                    OutlinedTextField(
                                        value = viewModel.customSecondary,
                                        onValueChange = {
                                            viewModel.saveCustomConfig(
                                                primary = viewModel.customPrimary,
                                                secondary = it,
                                                doh = viewModel.customDohHost
                                            )
                                        },
                                        label = { Text("DNS Secundário IPv4", fontSize = 11.sp) },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White,
                                            focusedLabelColor = Color(0xFF00ADB5),
                                            unfocusedLabelColor = Color.White.copy(alpha = 0.5f),
                                            focusedBorderColor = Color(0xFF00ADB5),
                                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f)
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedTextField(
                                        value = viewModel.customPrimaryIpv6,
                                        onValueChange = {
                                            viewModel.saveCustomConfigIpv6(
                                                primaryIpv6 = it,
                                                secondaryIpv6 = viewModel.customSecondaryIpv6
                                            )
                                        },
                                        label = { Text("DNS Primário IPv6 (Opcional)", fontSize = 11.sp) },
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White,
                                            focusedLabelColor = Color(0xFF00ADB5),
                                            unfocusedLabelColor = Color.White.copy(alpha = 0.5f),
                                            focusedBorderColor = Color(0xFF00ADB5),
                                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f)
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )

                                    OutlinedTextField(
                                        value = viewModel.customSecondaryIpv6,
                                        onValueChange = {
                                            viewModel.saveCustomConfigIpv6(
                                                primaryIpv6 = viewModel.customPrimaryIpv6,
                                                secondaryIpv6 = it
                                            )
                                        },
                                        label = { Text("DNS Secundário IPv6 (Opcional)", fontSize = 11.sp) },
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White,
                                            focusedLabelColor = Color(0xFF00ADB5),
                                            unfocusedLabelColor = Color.White.copy(alpha = 0.5f),
                                            focusedBorderColor = Color(0xFF00ADB5),
                                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f)
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                OutlinedTextField(
                                    value = viewModel.customDohHost,
                                    onValueChange = {
                                        viewModel.saveCustomConfig(
                                            primary = viewModel.customPrimary,
                                            secondary = viewModel.customSecondary,
                                            doh = it
                                        )
                                    },
                                    label = { Text("Host DNS seguro (opcional)", fontSize = 11.sp) },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedLabelColor = Color(0xFF00ADB5),
                                        unfocusedLabelColor = Color.White.copy(alpha = 0.5f),
                                        focusedBorderColor = Color(0xFF00ADB5),
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.15f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(2.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "* Nota: o modo atual aplica DNS por IP via VPN local. O host seguro é salvo como referência do perfil, sem forçar DoH dentro do túnel.",
                                        color = Color.White.copy(alpha = 0.4f),
                                        fontSize = 10.sp,
                                        lineHeight = 13.sp,
                                        modifier = Modifier.weight(0.7f)
                                    )
                                    
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0xFF2C3236))
                                            .clickable {
                                                viewModel.saveCustomConfig(
                                                    viewModel.customPrimary,
                                                    viewModel.customSecondary,
                                                    viewModel.customDohHost
                                                )
                                                viewModel.saveCustomConfigIpv6(
                                                    viewModel.customPrimaryIpv6,
                                                    viewModel.customSecondaryIpv6
                                                )
                                                Toast.makeText(context, "Configurações personalizadas salvas!", Toast.LENGTH_SHORT).show()
                                            }
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = "SALVAR",
                                            color = Color(0xFF00ADB5),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        } else {
                            // Informative description card of selected presets
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = null,
                                            tint = Color(selectedProfile.logoAccent),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = selectedProfile.name,
                                            color = Color.White,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Text(
                                        text = selectedProfile.description,
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Visual specification list
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF17191C), RoundedCornerShape(8.dp))
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "DNS Primário:",
                                            color = Color.White.copy(alpha = 0.5f),
                                            fontSize = 11.sp
                                        )
                                        Text(
                                            text = selectedProfile.primaryDns,
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "DNS Secundário:",
                                            color = Color.White.copy(alpha = 0.5f),
                                            fontSize = 11.sp
                                        )
                                        Text(
                                            text = selectedProfile.secondaryDns,
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    if (selectedProfile.primaryDnsIpv6.isNotEmpty()) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "DNS IPv6 Primário:",
                                                color = Color.White.copy(alpha = 0.5f),
                                                fontSize = 11.sp
                                            )
                                            Text(
                                                text = selectedProfile.primaryDnsIpv6,
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    if (selectedProfile.secondaryDnsIpv6.isNotEmpty()) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "DNS IPv6 Secundário:",
                                                color = Color.White.copy(alpha = 0.5f),
                                                fontSize = 11.sp
                                            )
                                            Text(
                                                text = selectedProfile.secondaryDnsIpv6,
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    if (selectedProfile.dohHost.isNotEmpty()) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "Host seguro/ref.:",
                                                color = Color.White.copy(alpha = 0.5f),
                                                fontSize = 11.sp
                                            )
                                            Text(
                                                text = selectedProfile.dohHost,
                                                color = Color(0xFF00ADB5),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Custom apple-tv style glowing scale and colored border focusable card
 * This is crucial for optimal remote D-Pad navigation feel.
 */
@Composable
fun TvFocusableCard(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    focusedScale: Float = 1.05f,
    content: @Composable () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) focusedScale else 1f, label = "card_scale")
    val borderWidth = if (isFocused) 3.dp else 1.dp
    val borderColor = if (isFocused) accentColor else Color.White.copy(alpha = 0.05f)
    val cardBg = if (isFocused) Color(0xFF23272B) else Color(0xFF1E2124)

    Card(
        modifier = modifier
            .scale(scale)
            .onFocusChanged { isFocused = it.isFocused }
            .border(borderWidth, borderColor, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .focusable()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = cardBg
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        content()
    }
}
