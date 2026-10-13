package es.alejandro.grandarbiter.domain.draft

/**
 * Draft formats the timer knows about. Only [available] ones can be started; the rest are shown
 * as "coming soon" (the app is in beta). All of them are in the MTR:
 * - Booster Draft: 7.7 + Appendix B "Booster Draft Timing".
 * - Two-Headed Giant Booster Draft: 9.7 + Appendix B "Two-Headed Giant Draft Timing".
 * - Rochester Draft: 8.5 (Team Rochester) + Appendix B "Rochester Draft Timing".
 */
enum class DraftFormat(val available: Boolean) {
    BOOSTER(available = true),
    TWO_HEADED_GIANT(available = false),
    ROCHESTER(available = false),
}

/** Where the pack goes after a pick. MTR 7.7: first pack left, reversed for each pack. */
enum class PassDirection {
    LEFT, RIGHT;

    companion object {
        /** Pack 1 left, pack 2 right, pack 3 left... */
        fun forPack(pack: Int): PassDirection = if (pack % 2 == 1) LEFT else RIGHT
    }
}
