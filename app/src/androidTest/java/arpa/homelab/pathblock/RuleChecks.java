package arpa.homelab.pathblock;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;

public final class RuleChecks extends Instrumentation {
    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            check("exact case-insensitive path", pathMatches("https://reddit.com/R/UnitedKingdom"));
            check("descendant", pathMatches("https://reddit.com/r/unitedkingdom/comments/123?sort=new"));
            check("encoded path", pathMatches("https://reddit.com/r/%75nitedkingdom"));
            check("dot segments", pathMatches("https://reddit.com/r/other/../unitedkingdom"));
            check("sibling", !pathMatches("https://reddit.com/r/unitedkingdom2"));
            check("unrelated subreddit", !pathMatches("https://reddit.com/r/android"));
            check("unrelated host", !pathMatches("https://notreddit.com/r/unitedkingdom"));
            check("whole domain", matches("reddit.com", "https://old.reddit.com/r/android"));
            check("false subdomain", !matches("reddit.com", "https://reddit.com.example.org/"));
            check("explicit port", !matches("example.org:8080/path", "https://example.org:8081/path"));
            check("unsupported scheme", Rule.parse("file:///tmp/page") == null);
            Rule paused = new Rule(Rule.parse("reddit.com"), 500);
            check("paused until deadline", !paused.matches(Rule.parse("reddit.com"), 499));
            check("resumes at deadline", paused.matches(Rule.parse("reddit.com"), 500));
            result.putString("stream", "PASS: 13 rule checks\n");
            finish(Activity.RESULT_OK, result);
        } catch (AssertionError | RuntimeException failure) {
            result.putString("stream", "FAIL: " + failure + "\n");
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private static boolean pathMatches(String url) {
        return matches("https://www.reddit.com/r/unitedkingdom/", url);
    }

    private static boolean matches(String rule, String url) {
        return new Rule(Rule.parse(rule), 0).matches(Rule.parse(url), 0);
    }

    private static void check(String name, boolean condition) {
        if (!condition) throw new AssertionError(name);
    }
}
