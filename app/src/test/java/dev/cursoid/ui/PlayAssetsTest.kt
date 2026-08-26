package dev.cursoid.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import dev.cursoid.R
import dev.cursoid.ui.theme.CursoidTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the Play Store graphics at their exact required pixel sizes. The qualifiers pin the
 * density to mdpi so one dp is one pixel.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w1280dp-h800dp-mdpi")
class PlayAssetsTest {

    @get:Rule
    val compose = createComposeRule()

    private val ink = Color(0xFF0B0B0F)

    @Test
    fun appIcon() {
        compose.setContent {
            CursoidTheme(darkTheme = true) {
                Box(
                    modifier = Modifier
                        .testTag(TAG)
                        .size(512.dp)
                        .background(ink),
                    contentAlignment = Alignment.Center,
                ) {
                    Mark(size = 512.dp)
                }
            }
        }
        compose.onNodeWithTag(TAG).captureRoboImage("build/play/icon-512.png")
    }

    @Test
    fun featureGraphic() {
        compose.setContent {
            CursoidTheme(darkTheme = true) {
                Row(
                    modifier = Modifier
                        .testTag(TAG)
                        .size(width = 1024.dp, height = 500.dp)
                        .background(ink)
                        .padding(horizontal = 88.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Mark(size = 232.dp)
                    Spacer(Modifier.width(48.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(
                            text = "Cursoid",
                            color = Color(0xFFF4F3F8),
                            fontSize = 88.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Cursor cloud agents, on Android",
                            color = Color(0xFF8B6CFF),
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = "Launch, watch, review — from your phone.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 26.sp,
                        )
                    }
                }
            }
        }
        compose.onNodeWithTag(TAG).captureRoboImage("build/play/feature-graphic.png")
    }

    /**
     * The launcher vector keeps its art inside the adaptive-icon safe zone, which leaves too much
     * padding for a store icon, so it is scaled up and cropped to fill the frame.
     */
    @Composable
    private fun Mark(size: androidx.compose.ui.unit.Dp) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RectangleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier
                    .requiredSize(size * 1.5f)
                    .offset(y = -(size * 0.02f)),
            )
        }
    }

    private companion object {
        const val TAG = "play-asset"
    }
}
