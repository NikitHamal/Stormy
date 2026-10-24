package com.oro.app.data.ai.agent

/**
 * Whether a request to build something new is too vague to build well
 * without a few answers from the user -- decided before the first model call,
 * deterministically, so weak models get the same judgement strong ones have.
 *
 * Told "a website for my cafe", most models build a generic page around a
 * made-up name; the user then spends several turns steering it back. A good
 * product person would first ask two or three things that change the result:
 * the name, what the site is mainly for, the look. The check scores the
 * request for the facts that matter ([Facet]) and, only when a new build is
 * missing most of them, adds a short app note to that turn telling the model
 * to ask first in one card ([note]); otherwise it says nothing.
 *
 * High precision on purpose -- asking too much is its own failure, and vibe
 * coders leave when they are quizzed. Nothing is added for: a project that
 * already has work in it, an edit or a fix, a question, a detailed request,
 * a self-explanatory kind of app ("a snake game"), or a user who said "just
 * build it" / "you decide".
 */
object BriefCheck {

    /** What a build needs to know; each is something one question can settle. */
    enum class Facet(val phrase: String) {
        NAME("its name or brand"),
        PURPOSE("what it is mainly for"),
        AUDIENCE("who it is for"),
        SCOPE("the must-have pages or features"),
        LOOK("the look and feel"),
        DATA("what it must save (accounts, data)")
    }

    enum class Kind { SITE, APP }

    data class Verdict(
        val vague: Boolean,
        /** The open facts that matter most for this kind of build, most important first (at most 3). */
        val missing: List<Facet> = emptyList(),
        val kind: Kind? = null,
        /** Why, for logs and tests. */
        val reason: String
    )

    /** Past this many words a request has said enough to build from. */
    private const val DETAILED_WORDS = 40

    /**
     * @param request what the user typed.
     * @param projectFresh true when the project has nothing of the user's yet:
     *   no files, or only the files it was created with, untouched.
     */
    fun check(request: String, projectFresh: Boolean): Verdict {
        val text = request.trim()
        if (text.isEmpty()) return Verdict(false, reason = "empty")
        if (!projectFresh) return Verdict(false, reason = "the project already has work in it")
        val lower = text.lowercase()
        if (OPT_OUT.containsMatchIn(lower)) return Verdict(false, reason = "the user asked not to be asked")
        val words = WORD.findAll(text).count()
        if (words > DETAILED_WORDS) return Verdict(false, reason = "a detailed request")
        if (isQuestion(lower)) return Verdict(false, reason = "a question, not a request to build")
        if (EDIT.containsMatchIn(lower) && !STRONG_BUILD.containsMatchIn(lower)) {
            return Verdict(false, reason = "an edit, a fix or an addition")
        }
        if (SELF_EXPLANATORY.containsMatchIn(lower) && (BUILD_VERB.containsMatchIn(lower) || words <= 8)) {
            return Verdict(false, reason = "a self-explanatory kind of app")
        }
        val kind = kindOf(lower) ?: return Verdict(false, reason = "not a request to build something new")

        val present = facetsIn(text, lower)
        // A long request with a fact or two is enough to start from; a short one is not.
        val vague = when (present.size) {
            0 -> true
            1 -> words <= 25
            2 -> words <= 12
            else -> false
        }
        if (!vague) return Verdict(false, kind = kind, reason = "says enough: ${present.joinToString { it.name.lowercase() }}")
        val order = if (kind == Kind.SITE) {
            listOf(Facet.NAME, Facet.PURPOSE, Facet.LOOK, Facet.SCOPE, Facet.AUDIENCE)
        } else {
            listOf(Facet.PURPOSE, Facet.SCOPE, Facet.DATA, Facet.LOOK, Facet.AUDIENCE)
        }
        val missing = order.filter { it !in present }.take(3)
        return Verdict(true, missing, kind, "vague: $words words, facts given: ${present.joinToString { it.name.lowercase() }.ifEmpty { "none" }}")
    }

    /**
     * The app note for the user's turn, or null when the request needs none.
     * [ask] is the user's "Ask before building" setting; when it is off, a
     * vague request gets a note to decide and say so instead. [textProtocol]
     * adds a worked ask_user call in the text tool format.
     */
    fun note(verdict: Verdict, ask: Boolean, textProtocol: Boolean): String? {
        if (!verdict.vague) return null
        if (!ask) {
            return "[Oro: This request is brief and the user prefers not to be asked. Decide the details yourself, " +
                "keep them realistic and specific, and list your main choices in one line at the end.]"
        }
        val facts = verdict.missing.map { it.phrase }.ifEmpty { listOf(Facet.PURPOSE.phrase, Facet.LOOK.phrase) }
        val list = when (facts.size) {
            1 -> facts[0]
            else -> facts.dropLast(1).joinToString(", ") + " and " + facts.last()
        }
        return buildString {
            append("[Oro: This request leaves open what would change the result most -- most likely ")
            append(list).append(". Before building anything, ask the user ONE ask_user call with 2-3 short ")
            append("questions about that, written for this request (nothing generic, nothing the request already says). ")
            append("Give each question 2-4 one-tap options, the one you recommend first with recommended: true; ")
            append("the user can also type or tap \"You decide\". When the answers come, build without asking again; ")
            append("if the user lets you decide, use your recommended options and list them in one line at the end.")
            if (textProtocol) {
                append("\nThe call, for example (a different request):\n")
                append(EXAMPLE_CALL)
            }
            append(']')
        }
    }

    /** A worked call in the text tool format, about a request unlike the user's so it is not copied. */
    private const val EXAMPLE_CALL = "<tool_call>\n<function=ask_user>\n<parameter=questions>" +
        "[{\"header\": \"Name\", \"question\": \"What is the studio called?\", \"options\": " +
        "[{\"label\": \"I'll type it\"}, {\"label\": \"Make one up\", \"recommended\": true}]}, " +
        "{\"header\": \"Focus\", \"question\": \"What should visitors do first?\", \"options\": " +
        "[{\"label\": \"Book a class\", \"recommended\": true}, {\"label\": \"See the timetable\"}, {\"label\": \"Buy a pass\"}]}, " +
        "{\"header\": \"Look\", \"question\": \"Which feel fits the studio?\", \"options\": " +
        "[{\"label\": \"Calm and airy\", \"recommended\": true}, {\"label\": \"Bold and energetic\"}, {\"label\": \"Earthy and warm\"}]}]" +
        "</parameter>\n</function>\n</tool_call>"

    // ------------------------------------------------------------ signals

    private val WORD = Regex("""[\p{L}\p{N}][\p{L}\p{N}'’-]*""")

    private val OPT_OUT = Regex(
        """\b(just (build|make|do|create|start|go)|surprise me|you decide|your call|up to you|dealer'?s choice|""" +
            """whatever you (think|like|want|prefer)|(don'?t|do not|no need to) ask|no questions|without (asking|questions)|""" +
            """be creative|go wild|anything (is fine|works)|your choice)\b"""
    )

    /** Verbs that start something new; "want" and "need" only say what the user is after. */
    private val STRONG_BUILD = Regex("""\b(build|make|create|design|generate|develop|code|craft|set ?up|launch|spin up)\b""")

    private val BUILD_VERB = Regex(
        """\b(build|make|create|design|generate|develop|code|write|craft|start|set ?up|launch|spin up|need|want|""" +
            """give me|i'?d like|looking for)\b"""
    )

    /** Work on something that exists: edits, fixes, and additions to it. */
    private val EDIT = Regex(
        """\b(fix|bug|error|broken|crash(es|ing)?|not working|doesn'?t work|change|update|rename|move|replace|remove|""" +
            """delete|tweak|adjust|increase|decrease|align|center|centre|resize|refactor|translate|edit|add(ing)?|""" +
            """include|insert|put)\b"""
    )

    private val QUESTION_START = Regex(
        """^(how|what|why|which|when|where|who|is|are|does|do|did|should|could i|can i|explain|tell me)\b"""
    )

    /** "Can you make me…?" is a request in question form. */
    private val REQUEST_FORM = Regex("""^(can|could|would|will) you\b|\bplease\b""")

    private fun isQuestion(lower: String): Boolean =
        lower.endsWith("?") && QUESTION_START.containsMatchIn(lower) && !REQUEST_FORM.containsMatchIn(lower)

    private val SITE_NOUN = Regex(
        """\b(web ?sites?|sites?|web ?pages?|landing( pages?)?|home ?pages?|portfolios?|blogs?|online stores?|""" +
            """stores?|shops?|e-?commerce|storefronts?|resumes?|cv|link ?in ?bio|wedding page|event page|menu page)\b"""
    )

    private val APP_NOUN = Regex(
        """\b(apps?|web ?apps?|applications?|dashboards?|trackers?|planners?|platforms?|tools?|crm|marketplaces?|""" +
            """social (network|media)|admin panel|saas|booking system|inventory( system)?|portal|games?|chat ?bot|chat app|""" +
            """organi[sz]er|manager)\b"""
    )

    /** Apps whose behaviour the name already says; asking about them is quizzing. */
    private val SELF_EXPLANATORY = Regex(
        """\b(calculator|stop ?watch|timer|clock|countdown|snake|tetris|tic[- ]?tac[- ]?toe|2048|pong|flappy|sudoku|""" +
            """minesweeper|hangman|wordle|memory (game|match)|rock[, -]+paper[, -]+scissors|dice|coin (flip|toss)|""" +
            """colou?r picker|unit converter|currency converter|bmi|qr ?code|password generator|counter|to-?do( list)?)\b"""
    )

    private fun kindOf(lower: String): Kind? {
        val site = SITE_NOUN.find(lower)
        val app = APP_NOUN.find(lower)
        val noun = listOfNotNull(site, app).minByOrNull { it.range.first } ?: return null
        val kind = if (noun === site) Kind.SITE else Kind.APP
        // "a website for my cafe" needs no verb, nor does a message that opens with
        // what it wants; otherwise a message without a build verb is usually not a
        // build request ("I saw a site with a nice gallery").
        val shortNounPhrase = WORD.findAll(lower).count() <= 8
        return if (BUILD_VERB.containsMatchIn(lower) || shortNounPhrase || NOUN_LEAD.containsMatchIn(lower)) kind else null
    }

    private val NOUN_LEAD = Regex(
        """^(an? |my |our |new |simple |nice )?(\w+ )?(web ?site|site|landing page|portfolio|blog|store|shop|app|web app|""" +
            """game|dashboard)\b(?: \w+)? (for|that|to|where|with|about)\b"""
    )

    private fun facetsIn(text: String, lower: String): Set<Facet> {
        val found = mutableSetOf<Facet>()
        if (QUOTED.containsMatchIn(text) || NAMED.containsMatchIn(lower) || hasProperNoun(text)) found += Facet.NAME
        if (PURPOSE.containsMatchIn(lower)) found += Facet.PURPOSE
        if (AUDIENCE.containsMatchIn(lower)) found += Facet.AUDIENCE
        if (scopeIn(text, lower)) found += Facet.SCOPE
        if (LOOK.containsMatchIn(lower) || HEX.containsMatchIn(lower) || URL.containsMatchIn(lower)) found += Facet.LOOK
        if (DATA.containsMatchIn(lower)) found += Facet.DATA
        return found
    }

    private val QUOTED = Regex("""["“”«»]([^"“”«»]{2,40})["“”«»]""")
    private val NAMED = Regex("""\b(called|named|name is|brand(ed)? as|titled)\s+\S+""")

    private val PLACE_PREPOSITIONS = setOf("in", "at", "from", "near", "around", "across", "to", "of")

    /** Words a capital letter does not make a name. */
    private val NOT_NAMES = setOf(
        "i", "i'm", "i'd", "app", "website", "site", "web", "page", "html", "css", "js", "javascript", "typescript",
        "react", "vue", "svelte", "tailwind", "bootstrap", "oro", "orobi", "android", "ios", "iphone", "google", "youtube",
        "instagram", "facebook", "tiktok", "whatsapp", "api", "ui", "ux", "seo", "pwa", "make", "build", "create", "please",
        "hi", "hey", "hello", "can", "could", "a", "an", "the", "my", "for", "and", "with"
    )

    /**
     * A capitalised word that is not the first word of a sentence and not a
     * tech term: "a site for Brew Haven" names the business. Phone keyboards
     * capitalise only sentence starts, so this rarely misfires.
     */
    private fun hasProperNoun(text: String): Boolean {
        val sentences = text.split(Regex("""(?<=[.!?\n])\s+"""))
        return sentences.any { sentence ->
            val words = WORD.findAll(sentence).map { it.value }.toList()
            words.withIndex().drop(1).any { (i, w) ->
                // "my cafe in Thamel": a place, not the name of the thing.
                val after = words[i - 1].lowercase()
                w.first().isUpperCase() && w.length >= 2 && after !in PLACE_PREPOSITIONS &&
                    w.lowercase() !in NOT_NAMES && w.lowercase().trimEnd('\'', '’', 's') !in NOT_NAMES
            }
        }
    }

    private val PURPOSE = Regex(
        """\b((to|for) (sell|selling|book|booking|order|ordering|showcase|show off|track|tracking|manage|managing|collect|""" +
            """share|teach|learn|promote|advertise|announce|find|compare|plan|planning|organi[sz]e|list|display|present|get|""" +
            """attract|recruit|raise|accept)|where (people|users|customers|clients|visitors|i|we) can|""" +
            """(lets?|allows?|helps?) (me|users|people|customers|clients|visitors|us)|so (that )?(people|users|customers|i|we)|""" +
            """online (ordering|orders|booking|bookings)|reservations?|waitlist|sign-?ups?|donations?|preorders?)\b"""
    )

    private val AUDIENCE = Regex(
        """\b(for (my |our )?(students?|kids|children|parents|teens|teenagers|developers|designers|clients|customers|tourists|""" +
            """travell?ers|beginners|seniors|women|men|families|gamers|small businesses|businesses|startups|employees|staff|""" +
            """members|patients|readers|fans|investors|recruiters|employers|freelancers|creators|locals)|audience|aimed at|""" +
            """targeted at|target(ing)? (users|customers|people))\b"""
    )

    private val SCOPE_NOUN = Regex(
        """\b(hero|about( us)?|services|pricing|plans|testimonials|reviews|faq|contact( form)?|gallery|menu|team|""" +
            """projects|case studies|cart|checkout|log ?in|sign ?up|search|filters?|charts?|calendar|map|newsletter|""" +
            """footer|nav(bar|igation)?|pages?|sections?|features?|categories|profiles?|comments|notifications|""" +
            """leaderboard|levels?|scores?|timeline|schedule|bookings?|orders?)\b"""
    )

    /** At least two distinct page/feature nouns, or a list of three or more things. */
    private fun scopeIn(text: String, lower: String): Boolean {
        if (SCOPE_NOUN.findAll(lower).map { it.value.trimEnd('s') }.toSet().size >= 2) return true
        if (text.lines().count { BULLET.containsMatchIn(it) } >= 2) return true
        return LIST.containsMatchIn(lower)
    }

    private val BULLET = Regex("""^\s*([-*•]|\d+[.)])\s+\S""")
    private val LIST = Regex("""\b\w+(?: \w+)?, \w+(?: \w+)?,? (and|&|plus) \w+""")

    private val LOOK = Regex(
        """\b(red|blue|green|yellow|orange|purple|violet|pink|black|white|gr[ae]y|gold(en)?|silver|beige|brown|teal|cyan|""" +
            """navy|maroon|pastel|neon|monochrome|earthy|minimal(ist|istic)?|modern|clean|sleek|dark|light theme|playful|""" +
            """elegant|luxur(y|ious)|premium|bold|brutalis[mt]|retro|vintage|futuristic|corporate|professional|cozy|cosy|""" +
            """warm|rustic|glass(morphism)?|gradient|flat|cartoon(y|ish)?|cute|colou?rful|vibrant|aesthetic|editorial|""" +
            """magazine|theme|font|typography|palette|vibe|styled?|inspired by|looks? like|similar to|like apple|like stripe)\b"""
    )
    private val HEX = Regex("""#[0-9a-f]{3,8}\b""")
    private val URL = Regex("""https?://""")

    private val DATA = Regex(
        """\b(save|saves|saving|store data|persist|database|db|backend|back-end|api|log ?ins?|sign ?ins?|accounts?|""" +
            """users? (can|log)|admin|cms|uploads?|payments?|stripe|paypal|subscriptions?|sync|offline|crud)\b"""
    )
}
