package com.ganj.vpn.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val DeepNavy = Color(0xFF06111F)
private val SurfaceNavy = Color(0xFF0C1C30)
private val IosBlue = Color(0xFF0A84FF)
private val Emerald = Color(0xFF30D158)
private val Muted = Color(0xFF92A4B8)

private val GanjDarkScheme = darkColorScheme(
    primary = IosBlue,
    secondary = Emerald,
    background = DeepNavy,
    surface = SurfaceNavy,
    onBackground = Color.White,
    onSurface = Color.White,
)

private enum class AppTab(val title: String) {
    HOME("Home"),
    SERVERS("Servers"),
    CONNECT("Connect"),
    STORE("Store"),
    ACCOUNT("Account"),
}

@Composable
fun GanjVpnApp() {
    MaterialTheme(colorScheme = GanjDarkScheme) {
        var selected by remember { mutableStateOf(AppTab.CONNECT) }
        var connected by remember { mutableStateOf(false) }

        Scaffold(
            containerColor = DeepNavy,
            bottomBar = {
                GanjBottomBar(selected = selected, onSelected = { selected = it })
            },
        ) { padding ->
            ConnectionDashboard(
                connected = connected,
                onToggleConnection = { connected = !connected },
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun ConnectionDashboard(
    connected: Boolean,
    onToggleConnection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val connectionColor by animateColorAsState(
        targetValue = if (connected) Emerald else IosBlue,
        label = "connectionColor",
    )
    val buttonScale by animateFloatAsState(
        targetValue = if (connected) 1.03f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 260f),
        label = "buttonScale",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(connectionColor.copy(alpha = 0.18f), DeepNavy),
                    radius = 980f,
                ),
            ),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(260.dp)
                .blur(64.dp)
                .background(connectionColor.copy(alpha = 0.18f), CircleShape),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("GANJ VPN", fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Text("Secure. Fast. Yours.", color = Muted, fontSize = 12.sp)
                }
                Surface(
                    color = Color(0x33FFFFFF),
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Text("SMART CONNECT", modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), fontSize = 11.sp)
                }
            }

            Spacer(Modifier.height(72.dp))
            Text(
                text = if (connected) "Protected" else "Ready to connect",
                fontSize = 28.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (connected) "Germany • Frankfurt VIP" else "Fastest available server",
                color = Muted,
                modifier = Modifier.padding(top = 8.dp),
            )

            Spacer(Modifier.height(42.dp))
            Box(
                modifier = Modifier
                    .scale(buttonScale)
                    .size(180.dp)
                    .background(Color(0x18FFFFFF), CircleShape)
                    .border(1.dp, Color(0x3DFFFFFF), CircleShape)
                    .padding(16.dp)
                    .background(
                        Brush.linearGradient(
                            listOf(connectionColor.copy(alpha = 0.95f), connectionColor.copy(alpha = 0.62f)),
                        ),
                        CircleShape,
                    )
                    .clickable(onClick = onToggleConnection),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (connected) "ON" else "GO", fontSize = 36.sp, fontWeight = FontWeight.Bold)
                    Text(if (connected) "Tap to disconnect" else "Tap to connect", fontSize = 11.sp)
                }
            }

            Spacer(Modifier.height(48.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x12FFFFFF), RoundedCornerShape(24.dp))
                    .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(24.dp))
                    .padding(vertical = 18.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Metric("PING", if (connected) "42 ms" else "—")
                Metric("DOWNLOAD", if (connected) "84 Mbps" else "—")
                Metric("UPLOAD", if (connected) "21 Mbps" else "—")
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Text(label, color = Muted, fontSize = 9.sp, letterSpacing = 0.8.sp)
    }
}

@Composable
private fun GanjBottomBar(selected: AppTab, onSelected: (AppTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xF20A1727))
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppTab.entries.forEach { tab ->
            val isSelected = tab == selected
            val central = tab == AppTab.CONNECT
            Column(
                modifier = Modifier
                    .size(if (central) 64.dp else 58.dp)
                    .background(
                        color = when {
                            central -> IosBlue
                            isSelected -> Color(0x22FFFFFF)
                            else -> Color.Transparent
                        },
                        shape = CircleShape,
                    )
                    .clickable { onSelected(tab) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = if (central) "G" else tab.title.take(1),
                    fontWeight = FontWeight.Bold,
                    color = if (central || isSelected) Color.White else Muted,
                )
                if (!central) {
                    Text(
                        text = tab.title,
                        fontSize = 9.sp,
                        color = if (isSelected) Color.White else Muted,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
