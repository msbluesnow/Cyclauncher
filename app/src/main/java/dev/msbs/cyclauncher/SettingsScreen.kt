package dev.msbs.cyclauncher

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    viewModel: LauncherViewModel,
    onBack: () -> Unit
) {
    val handSide by viewModel.handSide.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "SETTINGS",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.Cyan
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Preferred Hand:", color = Color.White, style = MaterialTheme.typography.bodyLarge)
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                HandOption("Left", handSide == HandSide.LEFT) {
                    viewModel.setHandSide(HandSide.LEFT)
                }
                Spacer(modifier = Modifier.width(16.dp))
                HandOption("Right", handSide == HandSide.RIGHT) {
                    viewModel.setHandSide(HandSide.RIGHT)
                }
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        Button(
            onClick = onBack,
            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
        ) {
            Text("Back to Home", color = Color.White)
        }
    }
}

@Composable
private fun HandOption(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable { onClick() }
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = Color.Cyan,
                unselectedColor = Color.Gray
            )
        )
        Text(label, color = if (isSelected) Color.Cyan else Color.White)
    }
}
