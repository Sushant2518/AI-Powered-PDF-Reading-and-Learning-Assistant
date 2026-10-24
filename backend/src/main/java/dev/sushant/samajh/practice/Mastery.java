package dev.sushant.samajh.practice;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Mastery of one concept, computed from practice history (TRD §6):
 * <pre>
 * practice = weighted average of the last 10 attempt scores (newer weigh more)
 * flash    = % "Got it" in the last 10 flashcard reviews
 * raw      = 0.7 * practice + 0.3 * flash   (just practice if never reviewed)
 * score    = raw * (0.6 + 0.4 * exp(-days since last practice / 45))   — knowledge fades without revision
 * </pre>
 */
public record Mastery(int score, Stage stage, int attempts) {

    public enum Stage { READ, UNDERSTOOD, PRACTISED, MASTERED }

    public record Attempt(int score, boolean explainBack, Instant at) {}

    public record Review(boolean gotIt, Instant at) {}

    public static Mastery of(List<Attempt> attempts, List<Review> reviews, Instant now) {
        if (attempts.isEmpty() && reviews.isEmpty()) return new Mastery(0, Stage.READ, 0);

        List<Attempt> recent = attempts.stream().sorted(Comparator.comparing(Attempt::at)).toList();
        recent = recent.subList(Math.max(0, recent.size() - 10), recent.size());
        double weighted = 0, weights = 0;
        for (int i = 0; i < recent.size(); i++) {
            weighted += recent.get(i).score() * (i + 1);
            weights += i + 1;
        }
        Double practice = recent.isEmpty() ? null : weighted / weights;

        List<Review> lastReviews = reviews.stream().sorted(Comparator.comparing(Review::at)).toList();
        lastReviews = lastReviews.subList(Math.max(0, lastReviews.size() - 10), lastReviews.size());
        Double flash = lastReviews.isEmpty() ? null : 100.0 * lastReviews.stream().filter(Review::gotIt).count() / lastReviews.size();

        double raw = practice == null ? flash : flash == null ? practice : 0.7 * practice + 0.3 * flash;

        Instant last = Stream.concat(attempts.stream().map(Attempt::at), reviews.stream().map(Review::at))
                .max(Comparator.naturalOrder()).orElseThrow();
        double days = Math.max(0, Duration.between(last, now).toHours() / 24.0);
        int score = (int) Math.round(raw * (0.6 + 0.4 * Math.exp(-days / 45)));

        boolean goodExplainBack = attempts.stream().anyMatch(a -> a.explainBack() && a.score() >= 70);
        Stage stage = score >= 80 && goodExplainBack ? Stage.MASTERED
                : attempts.size() >= 3 ? Stage.PRACTISED
                : Stage.UNDERSTOOD;
        return new Mastery(score, stage, attempts.size());
    }
}
