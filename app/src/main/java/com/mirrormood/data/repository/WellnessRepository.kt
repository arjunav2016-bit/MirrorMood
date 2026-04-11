package com.mirrormood.data.repository

import com.mirrormood.data.WellnessRecommendation

object WellnessRepository {

    fun getRecommendations(mood: String): List<WellnessRecommendation> {
        val tips = when (mood) {
            "Stressed" -> stressedTips
            "Tired" -> tiredTips
            "Bored" -> boredTips
            "Happy" -> happyTips
            "Focused" -> focusedTips
            else -> neutralTips
        }
        return tips.shuffled()
    }

    fun getQuickTip(mood: String): WellnessRecommendation {
        return getRecommendations(mood).first()
    }

    // ── Stressed ────────────────────────────────────────────────
    private val stressedTips = listOf(
        WellnessRecommendation(
            emoji = "🫧",
            title = "Box Breathing",
            description = "Inhale for 4 seconds → Hold for 4 seconds → Exhale for 4 seconds → Hold for 4 seconds. Repeat 4 times. This activates your parasympathetic nervous system.",
            category = "Breathing"
        ),
        WellnessRecommendation(
            emoji = "🌊",
            title = "4-7-8 Calm Breath",
            description = "Breathe in through your nose for 4 seconds, hold for 7 seconds, then exhale slowly through your mouth for 8 seconds. A natural tranquilizer for your nervous system.",
            category = "Breathing"
        ),
        WellnessRecommendation(
            emoji = "🚶",
            title = "5-Minute Walk",
            description = "Step away from what you're doing and take a short walk. Even 5 minutes of movement lowers cortisol levels and clears your mind.",
            category = "Activity"
        ),
        WellnessRecommendation(
            emoji = "✍️",
            title = "Brain Dump",
            description = "Grab a piece of paper and write down everything on your mind for 3 minutes straight. Don't filter — just dump. It externalizes worry and reduces mental load.",
            category = "Mindset"
        ),
        WellnessRecommendation(
            emoji = "🧊",
            title = "Cold Water Reset",
            description = "Splash cold water on your face or hold an ice cube. The cold triggers your dive reflex, slowing your heart rate and calming your body almost instantly.",
            category = "Self-Care"
        ),
        WellnessRecommendation(
            emoji = "🎵",
            title = "Listen to Calm Music",
            description = "Put on instrumental music at 60 BPM — classical piano, lo-fi beats, or nature sounds. Music at this tempo synchronizes with your resting heart rate.",
            category = "Activity"
        ),
        WellnessRecommendation(
            emoji = "💆",
            title = "Progressive Muscle Relaxation",
            description = "Tense each muscle group for 5 seconds, then release. Start from your toes and work up to your forehead. Notice the contrast between tension and relaxation.",
            category = "Self-Care"
        )
    )

    // ── Tired ───────────────────────────────────────────────────
    private val tiredTips = listOf(
        WellnessRecommendation(
            emoji = "💧",
            title = "Hydrate Now",
            description = "Dehydration is one of the top causes of fatigue. Drink a full glass of water right now. Add lemon for a vitamin C boost.",
            category = "Self-Care"
        ),
        WellnessRecommendation(
            emoji = "☀️",
            title = "Get Some Sunlight",
            description = "Step outside or sit near a window for 5 minutes. Natural light suppresses melatonin and signals your body to be alert.",
            category = "Activity"
        ),
        WellnessRecommendation(
            emoji = "🧘",
            title = "Energizing Breath",
            description = "Try Breath of Fire: quick, rhythmic inhales and exhales through your nose, 1-2 per second, for 30 seconds. It boosts oxygen flow and alertness.",
            category = "Breathing"
        ),
        WellnessRecommendation(
            emoji = "🍎",
            title = "Smart Snack",
            description = "Eat something with protein and complex carbs — an apple with peanut butter, or a handful of nuts. Avoid sugar crashes from candy or soda.",
            category = "Self-Care"
        ),
        WellnessRecommendation(
            emoji = "🏃",
            title = "Quick Stretch",
            description = "Stand up and do 60 seconds of stretching: reach for the sky, touch your toes, roll your shoulders. Movement increases blood flow to your brain.",
            category = "Activity"
        ),
        WellnessRecommendation(
            emoji = "⏰",
            title = "Power Nap Window",
            description = "If you can, take a 10-20 minute nap. Set an alarm! Naps longer than 30 minutes cause grogginess. The sweet spot is 15 minutes.",
            category = "Self-Care"
        ),
        WellnessRecommendation(
            emoji = "🎶",
            title = "Upbeat Playlist",
            description = "Put on energetic music with a tempo above 120 BPM. Upbeat music has been shown to increase energy levels and improve mood within minutes.",
            category = "Activity"
        )
    )

    // ── Bored ───────────────────────────────────────────────────
    private val boredTips = listOf(
        WellnessRecommendation(
            emoji = "🎨",
            title = "Try Something Creative",
            description = "Doodle, write a haiku, or take a photo of something interesting nearby. Creativity activates different brain pathways and defeats monotony.",
            category = "Activity"
        ),
        WellnessRecommendation(
            emoji = "📚",
            title = "Learn One New Thing",
            description = "Watch a 5-minute educational video or read an interesting article. Curiosity is the antidote to boredom — feed it with something new.",
            category = "Mindset"
        ),
        WellnessRecommendation(
            emoji = "🤝",
            title = "Connect With Someone",
            description = "Send a message to a friend you haven't talked to in a while. Social connection boosts dopamine and gives your day unexpected energy.",
            category = "Activity"
        ),
        WellnessRecommendation(
            emoji = "🧩",
            title = "Micro-Challenge",
            description = "Set a 5-minute timer and challenge yourself: solve a riddle, organize your desk, or learn 5 words in a new language. Small wins build momentum.",
            category = "Mindset"
        ),
        WellnessRecommendation(
            emoji = "🌿",
            title = "Nature Moment",
            description = "Step outside and observe nature for 3 minutes. Count how many different bird sounds you hear, or find 5 different shades of green. Mindful observation refreshes attention.",
            category = "Self-Care"
        ),
        WellnessRecommendation(
            emoji = "🎯",
            title = "Set a Tiny Goal",
            description = "Pick one small thing you've been putting off and do it right now. Completing even a minor task releases dopamine and breaks the boredom cycle.",
            category = "Mindset"
        )
    )

    // ── Happy ───────────────────────────────────────────────────
    private val happyTips = listOf(
        WellnessRecommendation(
            emoji = "📝",
            title = "Capture This Moment",
            description = "Write down 3 things that contributed to your good mood right now. Documenting positive moments makes them easier to recreate later.",
            category = "Mindset"
        ),
        WellnessRecommendation(
            emoji = "💛",
            title = "Share the Joy",
            description = "Send a kind message to someone — compliment a colleague, thank a friend, or call a family member. Sharing happiness multiplies it.",
            category = "Activity"
        ),
        WellnessRecommendation(
            emoji = "🎉",
            title = "Celebrate Yourself",
            description = "You're in a great mood! Acknowledge what you did to get here. Did you sleep well? Exercise? Spend time with loved ones? Take note for future reference.",
            category = "Mindset"
        ),
        WellnessRecommendation(
            emoji = "🌟",
            title = "Tackle Something Big",
            description = "Positive mood boosts cognitive flexibility and creativity. This is the perfect time to work on a challenging project or make important decisions.",
            category = "Activity"
        ),
        WellnessRecommendation(
            emoji = "🧘",
            title = "Gratitude Breathing",
            description = "Close your eyes and take 5 deep breaths. On each exhale, think of something you're grateful for. This anchors your positive state deeper into memory.",
            category = "Breathing"
        ),
        WellnessRecommendation(
            emoji = "🎵",
            title = "Save This Vibe",
            description = "Add the songs you're listening to right now to a 'Happy' playlist. Music anchors emotions — you can replay this playlist when you need a mood boost.",
            category = "Self-Care"
        )
    )

    // ── Focused ─────────────────────────────────────────────────
    private val focusedTips = listOf(
        WellnessRecommendation(
            emoji = "⏱️",
            title = "Ride the Wave",
            description = "You're in a flow state! Set a 25-minute timer (Pomodoro) and dive deep into your most important task. Protect this focus — it's precious.",
            category = "Activity"
        ),
        WellnessRecommendation(
            emoji = "📵",
            title = "Go Distraction-Free",
            description = "Silence notifications, close social media tabs, and put your phone face down. Your current focus is a superpower — don't let interruptions steal it.",
            category = "Mindset"
        ),
        WellnessRecommendation(
            emoji = "💧",
            title = "Stay Hydrated",
            description = "Keep water within arm's reach. Even mild dehydration (1-2%) impairs concentration. Sip regularly to maintain your sharp focus.",
            category = "Self-Care"
        ),
        WellnessRecommendation(
            emoji = "🫁",
            title = "Focus Breathing",
            description = "Take 3 slow, deep breaths: inhale for 4 counts, exhale for 6 counts. The longer exhale keeps your nervous system in a calm-alert state, perfect for deep work.",
            category = "Breathing"
        ),
        WellnessRecommendation(
            emoji = "📋",
            title = "Capture Side Thoughts",
            description = "Keep a notepad nearby. When unrelated thoughts pop up, jot them down and return to your task. This 'parking lot' technique prevents context switching.",
            category = "Mindset"
        ),
        WellnessRecommendation(
            emoji = "🍵",
            title = "Strategic Caffeine",
            description = "If it's before 2 PM, a cup of green tea gives you calm energy via L-theanine + caffeine. After 2 PM, skip it to protect tonight's sleep.",
            category = "Self-Care"
        )
    )

    // ── Neutral ─────────────────────────────────────────────────
    private val neutralTips = listOf(
        WellnessRecommendation(
            emoji = "🧠",
            title = "Mood Check-In",
            description = "Take a moment to scan your body. How does your chest feel? Your shoulders? Your jaw? Sometimes we carry emotions physically before we notice them mentally.",
            category = "Mindset"
        ),
        WellnessRecommendation(
            emoji = "🌬️",
            title = "Mindful Breathing",
            description = "Breathe naturally and simply observe each breath for 1 minute. Don't change anything — just notice. This builds awareness and presence.",
            category = "Breathing"
        ),
        WellnessRecommendation(
            emoji = "🚶",
            title = "Movement Break",
            description = "Stand up and walk around for 2 minutes. Gentle movement transitions your body and mind into a more engaged state.",
            category = "Activity"
        ),
        WellnessRecommendation(
            emoji = "🎯",
            title = "Set an Intention",
            description = "Ask yourself: 'What do I want the rest of my day to feel like?' Setting a simple intention gives your neutral state direction and purpose.",
            category = "Mindset"
        ),
        WellnessRecommendation(
            emoji = "🌸",
            title = "Sensory Grounding",
            description = "Notice 5 things you can see, 4 you can touch, 3 you can hear, 2 you can smell, 1 you can taste. This 5-4-3-2-1 technique grounds you in the present.",
            category = "Self-Care"
        ),
        WellnessRecommendation(
            emoji = "☕",
            title = "Mindful Pause",
            description = "Make yourself a warm drink and sip it slowly. Pay attention to the warmth, the aroma, the taste. A mindful pause can shift neutral into content.",
            category = "Self-Care"
        )
    )
}
