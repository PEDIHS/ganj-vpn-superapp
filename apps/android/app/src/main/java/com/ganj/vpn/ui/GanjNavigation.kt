package com.ganj.vpn.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ganj.vpn.R

internal enum class GanjDestination(@StringRes val labelRes: Int) {
    Home(R.string.nav_home),
    Servers(R.string.nav_servers),
    Connect(R.string.nav_connect),
    Store(R.string.nav_store),
    Account(R.string.nav_account),
}

@Composable
internal fun GanjLiquidBottomNavigation(
    selectedDestination: GanjDestination,
    onDestinationSelected: (GanjDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fontScale = LocalDensity.current.fontScale
    val windowWidthDp = LocalConfiguration.current.screenWidthDp
    val showAllLabels = GanjResponsivePolicy.shouldShowAllNavigationLabels(
        widthDp = windowWidthDp,
        fontScale = fontScale,
    )
    val glass = LocalGanjGlassPalette.current
    val navShape = RoundedCornerShape(30.dp)

    Box(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .fillMaxWidth()
            .height(92.dp),
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(72.dp)
                .clip(navShape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            glass.highlight.copy(alpha = 0.08f),
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                        ),
                    ),
                )
                .border(1.dp, glass.borderSoft.copy(alpha = 0.72f), navShape)
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GanjDestination.entries.forEach { destination ->
                if (destination == GanjDestination.Connect) {
                    Spacer(Modifier.weight(1f))
                } else {
                    GanjNavigationItem(
                        destination = destination,
                        selected = destination == selectedDestination,
                        showLabel = showAllLabels || destination == selectedDestination,
                        onClick = { onDestinationSelected(destination) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        GanjCenterConnectDestination(
            selected = selectedDestination == GanjDestination.Connect,
            onClick = { onDestinationSelected(GanjDestination.Connect) },
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

@Composable
private fun GanjNavigationItem(
    destination: GanjDestination,
    selected: Boolean,
    showLabel: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = persianDestinationLabel(destination)
    val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val description = UiAccessibilityPolicy.destinationDescription(
        label = label,
        selected = selected,
        selectedSuffix = stringResource(R.string.a11y_selected),
    )

    Box(
        modifier = modifier
            .heightIn(min = 58.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else Color.Transparent,
            )
            .semantics {
                this.selected = selected
                contentDescription = description
            }
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            GanjNavigationIcon(
                destination = destination,
                tint = tint,
                modifier = Modifier.size(21.dp),
            )
            if (showLabel) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = label,
                    color = tint,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun GanjCenterConnectDestination(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = if (selected) Color(0xFF72FCB6) else MaterialTheme.colorScheme.primary
    val description = UiAccessibilityPolicy.destinationDescription(
        label = "اتصال",
        selected = selected,
        selectedSuffix = stringResource(R.string.a11y_selected),
    )

    Column(
        modifier = modifier.offset(y = 1.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(
                            accent.copy(alpha = 0.32f),
                            Color(0xFF063E2F),
                            Color(0xFF07110D),
                        ),
                    ),
                )
                .border(
                    width = 2.dp,
                    color = if (selected) Color(0xFF72FCB6) else MaterialTheme.colorScheme.primary.copy(alpha = 0.78f),
                    shape = CircleShape,
                )
                .semantics {
                    this.selected = selected
                    contentDescription = description
                }
                .clickable(role = Role.Tab, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            GanjNavigationIcon(
                destination = GanjDestination.Connect,
                tint = accent,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = "اتصال",
            color = accent,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

private fun persianDestinationLabel(destination: GanjDestination): String = when (destination) {
    GanjDestination.Home -> "خانه"
    GanjDestination.Servers -> "سرورها"
    GanjDestination.Connect -> "اتصال"
    GanjDestination.Store -> "فروشگاه"
    GanjDestination.Account -> "پروفایل"
}
