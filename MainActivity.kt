package com.example.ainotification
import android.app.Notification
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.ainotification.MatchData.awayScore
import com.example.ainotification.MatchData.awayTeam
import com.example.ainotification.MatchData.extras
import com.example.ainotification.MatchData.homeScore
import com.example.ainotification.MatchData.homeTeam
import com.kieronquinn.app.smartspacer.sdk.model.SmartspaceTarget
import com.kieronquinn.app.smartspacer.sdk.model.uitemplatedata.Icon
import com.kieronquinn.app.smartspacer.sdk.model.uitemplatedata.Text
import com.kieronquinn.app.smartspacer.sdk.provider.SmartspacerTargetProvider
import com.kieronquinn.app.smartspacer.sdk.utils.TargetTemplate

object MatchData {
    var title: String = ""
    var subtitle: String = ""
    var homeTeam: String=""
    var awayTeam: String=""
    var homeScore: String=""
    var awayScore: String=""
    var extras: String=""
    var scorers: String = ""

}
var flag=false

class MainActivity : AppCompatActivity() {
    private lateinit var textView: TextView
    private lateinit var flashScoreReceiver: BroadcastReceiver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        textView = TextView(this).apply {
            textSize = 16f
            setPadding(16, 16, 16, 16)
        }
        setContentView(textView)

        flashScoreReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val title = intent?.getStringExtra("title") ?: ""
                val subtitle = intent?.getStringExtra("subtitle") ?: ""

                MatchData.title = title
                MatchData.subtitle = subtitle

                textView.text = """
                    Title: ${MatchData.title}
                    Subtitle: ${MatchData.subtitle}
                       home:${homeTeam}
            away:${awayTeam}
            score:${awayScore}
            home:${homeScore}
                """.trimIndent()
            }
        }

        val filter = IntentFilter("eu.livesport.FlashScore_com")

        // ✅ Android 13+ compliant receiver registration
        ContextCompat.registerReceiver(
            this,
            flashScoreReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        // Optional fallback text
        textView.text = """
            Title: ${MatchData.title}
            Subtitle: ${MatchData.subtitle}
            home:${homeTeam}
            away:${awayTeam}
            score:${awayScore}
            home:${homeScore}
            Scorers: ${MatchData.scorers}

        """.trimIndent()
        SmartspacerTargetProvider.notifyChange(this,Target::class.java, smartspacerId = "notify")
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(flashScoreReceiver)
    }
}




class FlashScoreNotificationService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {

        sbn?.let {
            val packageName = it.packageName
            if (packageName == "eu.livesport.FlashScore_com") {
                val extras = it.notification.extras
                val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
                val subtitle = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

                MatchData.title = title
                MatchData.subtitle = subtitle

                Log.d("FlashScoreNotif", "Title: $title")
                Log.d("FlashScoreNotif", "Subtitle: $subtitle")

                // Send broadcast to MainActivity
                val intent = Intent("")
                intent.putExtra("title", title)
                intent.putExtra("subtitle", subtitle)
                sendBroadcast(intent)
                parseFlashScoreNotification(this,title,subtitle)

            }
        }
    }
}

fun parseFlashScoreNotification(context: Context,title: String, subtitle: String) {
    // Extract team names
    val teams = title.split(" - ")
    if (teams.size == 2) {
        MatchData.homeTeam = teams[0].trim()
        MatchData.awayTeam = teams[1].trim()
        flag=true
        SmartspacerTargetProvider.notifyChange(context,Target::class.java, smartspacerId = "notify")

    }

    // Find first score line (e.g., "Goal! 1 - [2]")
    val lines = subtitle.lines()
    val scoreLine = subtitle.lines().firstOrNull {
        it.contains("goal", ignoreCase = true) && it.contains("[")
    }

    scoreLine?.let {
        val scoreRegex = Regex("""Goal!\s*\[(\d+)]""", RegexOption.IGNORE_CASE)
        val match = scoreRegex.find(it)

        if (match != null) {
            val newScore = match.groupValues[1].toInt()
            var lastHomeScore = MatchData.homeScore?.toIntOrNull() ?: 0
            var lastAwayScore = MatchData.awayScore?.toIntOrNull() ?: 0
            // Heuristic: if newScore > current home score → home team scored
            // You can improve this if you extract team/player name
            if (newScore > lastHomeScore) {
                MatchData.homeScore = newScore.toString()
            } else if (newScore > lastAwayScore) {
                MatchData.awayScore = newScore.toString()
            }
        }
    }

    handleFlashScoreNotification(title,subtitle,context)
}
fun handleFlashScoreNotification(title: String?, subtitle: String?,context: Context) {
    val titleText = title ?: ""
    val subtitleText = subtitle ?: ""
    val content = "$titleText\n$subtitleText".lowercase()

    when {

        "half-time" in content -> half(context)
        "finished" in content -> handleFullTime(titleText, subtitleText,context)
        "lineups are available" in content->start(context)
        "goal" in content-> extractGoalScorers(subtitleText,context)

    }
}
fun start(context: Context){
    extras="Match starts soon"
    SmartspacerTargetProvider.notifyChange(context,Target::class.java, smartspacerId = "notify")
}
fun half(context: Context){
    extras="Half time"
    SmartspacerTargetProvider.notifyChange(context,Target::class.java, smartspacerId = "notify")
}
fun handleFullTime(title: String, subtitle: String,context: Context){
    extras="Finished"
    SmartspacerTargetProvider.notifyChange(context,Target::class.java, smartspacerId = "notify")
    Handler(Looper.getMainLooper()).postDelayed({
        flag=false
        SmartspacerTargetProvider.notifyChange(context,Target::class.java, smartspacerId = "notify")
    },30 * 60 * 1000L)
    SmartspacerTargetProvider.notifyChange(context,Target::class.java, smartspacerId = "notify")

}
fun extractGoalScorers(subtitle: String, context: Context) {
    val lines = subtitle.lines()
    val names = mutableListOf<String>()
    var currentHomeScore = MatchData.homeScore.toIntOrNull() ?: 0
    var currentAwayScore = MatchData.awayScore.toIntOrNull() ?: 0

    val scoreRegex = Regex("""Goal!\s+(\[\d+]|[\d+])\s*-\s*(\[\d+]|[\d+])""")
    val scorerRegex = Regex("""⚽\s*\d+(?:\+\d+)?'\s+Goal!\s+(\[\d+]|[\d+])\s*-\s*(\[\d+]|[\d+])\s+(.+)""")

    for (line in lines) {
        // Always extract scores
        val scoreMatch = scoreRegex.find(line)
        if (scoreMatch != null) {
            val left = scoreMatch.groupValues[1]
            val right = scoreMatch.groupValues[2]

            if (left.startsWith("[")) {
                currentHomeScore = left.filter { it.isDigit() }.toInt()
            }
            if (right.startsWith("[")) {
                currentAwayScore = right.filter { it.isDigit() }.toInt()
            }
        }

        // Try to extract scorer if present
        val fullMatch = scorerRegex.find(line)
        if (fullMatch != null) {
            val scorerName = fullMatch.groupValues[3].trim()
            names.add(scorerName)
        }
    }

    MatchData.homeScore = currentHomeScore.toString()
    MatchData.awayScore = currentAwayScore.toString()
    MatchData.scorers = names.joinToString(", ").ifBlank { "Scorer not available" }
    extras = MatchData.scorers

    SmartspacerTargetProvider.notifyChange(context, Target::class.java, smartspacerId = "notify")
    play(context)
}


fun play(context: Context){

    Handler(Looper.getMainLooper()).postDelayed({
        extras="In-Play"
        SmartspacerTargetProvider.notifyChange(context,Target::class.java, smartspacerId = "notify")

    },5*60*1000L)

}

class Target: SmartspacerTargetProvider(){
    override fun getSmartspaceTargets(smartspacerId: String): List<SmartspaceTarget> {
        val targets = mutableListOf<SmartspaceTarget>()
        targets.add(
            TargetTemplate.Basic(
                id="notify",
                componentName = ComponentName(context!!, Target::class.java),
                title = Text("$homeTeam $homeScore - $awayScore $awayTeam"),
                subtitle = Text(extras),
                icon =Icon(android.graphics.drawable.Icon.createWithResource(context, R.drawable.soccer)),
            ).create()
        )
        if(flag==false){
            return emptyList()
            notifyChange()
        }
        return targets
    }

    override fun getConfig(smartspacerId: String?): Config {
        return Config(
            label = "notifications",
            description = "notifications",
            icon =android.graphics.drawable.Icon.createWithResource(context,R.drawable.soccer),

        )
    }

    override fun onDismiss(smartspacerId: String, targetId: String): Boolean {
        flag=false
        notifyChange()
        return true

    }
}
