package com.oro.app.data.ai.eval

/**
 * The live eval's tasks: what a user would actually type on a phone, one per
 * kind of work Oro is for (a page that must look designed, an app that must
 * persist and handle its states, something interactive that must feel alive).
 */
internal enum class EvalTask(val projectName: String, val prompt: String) {
    LANDING(
        "Himalayan Brew",
        "Build a polished one-page website for \"Himalayan Brew\", a specialty coffee shop and small-batch roastery " +
            "in Thamel, Kathmandu (Jyatha Marg, open 7am-9pm every day). Include a hero, the story of their single-origin " +
            "Nepali beans from Gulmi and Ilam, a menu with real drinks and pastries priced in NPR, opening hours and " +
            "location, a few customer reviews, and a table-reservation form. Make it feel premium and modern, mobile-first."
    ),
    APP(
        "Habit tracker",
        "Make a habit tracker web app: I can add habits (a name, an emoji and daily or weekly), mark a habit done for " +
            "today, see each habit's streak, and edit or delete habits. Save everything with Oro's built-in backend " +
            "(/api) so it persists across reloads. Show proper loading, empty and error states. Clean, modern design " +
            "that works great on a phone."
    ),
    GAME(
        "Snake",
        "Build a Snake mini-game that plays well on a phone: swipe and on-screen arrow controls (keyboard arrows on " +
            "desktop too), score and best score (saved), speed that increases as you grow, pause and restart, a game " +
            "over screen, and a polished neon-arcade look with smooth animations."
    );

    companion object {
        fun parse(v: String): EvalTask = valueOf(v.trim().uppercase())
    }
}
